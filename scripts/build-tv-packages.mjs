import { cp, mkdir, readFile, readdir, rm, writeFile } from "node:fs/promises";
import { execFileSync, spawnSync } from "node:child_process";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const outputRoot = path.join(root, "dist/tv");
const shared = path.join(root, "native/smart-tv/shared");
const webosStore = path.join(root, "native/smart-tv/platforms/webos/store");
const webosVersion = "1.0.0";
const sharedVersion = "0.1.0";

await rm(outputRoot, { recursive: true, force: true });
await mkdir(outputRoot, { recursive: true });

for (const platform of ["webos", "tizen", "vidaa"]) {
  const destination = path.join(outputRoot, platform);
  await mkdir(destination, { recursive: true });
  for (const name of ["index.html", "styles.css", "app.mjs", "core.mjs"]) {
    await cp(path.join(shared, name), path.join(destination, name));
  }
  if (platform === "webos") {
    for (const name of ["icon-80.png", "icon-130.png", "splash-1920x1080.png"]) {
      await cp(path.join(webosStore, name), path.join(destination, name));
    }
  } else {
    await cp(path.join(webosStore, "icon-400.png"), path.join(destination, "icon.png"));
  }

  const descriptor = platform === "webos" ? "appinfo.json" : platform === "tizen" ? "config.xml" : "app.json";
  await cp(path.join(root, `native/smart-tv/platforms/${platform}/${descriptor}`), path.join(destination, descriptor));
}

const rokuSource = path.join(root, "native/roku");
const rokuDestination = path.join(outputRoot, "roku");
await cp(rokuSource, rokuDestination, { recursive: true });
await mkdir(path.join(rokuDestination, "images"), { recursive: true });
for (const [name, width, height] of [
  ["mm_icon_focus_fhd.png", 540, 405],
  ["mm_icon_side_fhd.png", 108, 81],
  ["splash_screen_fhd.png", 1920, 1080],
]) {
  // Pre-sized assets are committed in native/roku/images and arrive via the
  // recursive rokuSource copy; assert the shipped dimensions like the webOS
  // path does so a wrong-size Roku asset errors here instead of shipping.
  const destination = path.join(rokuDestination, `images/${name}`);
  const dimensions = await pngDimensions(destination);
  if (dimensions.width !== width || dimensions.height !== height) {
    throw new Error(`${name} must be ${width}x${height}, got ${dimensions.width}x${dimensions.height}`);
  }
}

const webosDestination = path.join(outputRoot, "webos");
const webosAppinfo = JSON.parse(await readFile(path.join(webosDestination, "appinfo.json"), "utf8"));
for (const [name, width, height] of [
  [webosAppinfo.icon, 80, 80],
  [webosAppinfo.largeIcon, 130, 130],
  [webosAppinfo.splashBackground, 1920, 1080],
]) {
  const dimensions = await pngDimensions(path.join(webosDestination, name));
  if (dimensions.width !== width || dimensions.height !== height) {
    throw new Error(`${name} must be ${width}x${height}, got ${dimensions.width}x${dimensions.height}`);
  }
}

for (const platform of ["webos", "tizen", "vidaa"]) {
  const destination = path.join(outputRoot, platform);
  const entry = await readFile(path.join(destination, "index.html"), "utf8");
  const app = await readFile(path.join(destination, "app.mjs"), "utf8");
  const styles = await readFile(path.join(destination, "styles.css"), "utf8");
  for (const requirement of ["app.mjs", "M22 48.5h56", "Discover", "Sources"]) {
    if (!entry.includes(requirement)) throw new Error(`${platform} entry point is missing ${requirement}`);
  }
  if (!app.includes("performanceProfile") || !app.includes("selectSpatialTarget")) {
    throw new Error(`${platform} app is missing TV performance or D-pad policy`);
  }
  if (!styles.includes("--safe-x") || !styles.includes(".focusable:focus")) {
    throw new Error(`${platform} styles are missing TV safe-area or focus treatment`);
  }
}

const rokuManifest = await readFile(path.join(rokuDestination, "manifest"), "utf8");
for (const required of ["title=OpenStream", "ui_resolutions=fhd", "splash_screen_fhd="]) {
  if (!rokuManifest.includes(required)) throw new Error(`Roku manifest is missing ${required}`);
}
const rokuScene = await readFile(path.join(rokuDestination, "components/OpenStreamScene.xml"), "utf8");
if (!rokuScene.includes('extends="Scene"') || !rokuScene.includes("Nothing to show yet")) {
  throw new Error("Roku SceneGraph package is incomplete");
}

const artifactRoot = path.join(outputRoot, "artifacts");
await mkdir(artifactRoot, { recursive: true });
for (const platform of ["webos", "tizen", "vidaa", "roku"]) {
  const version = platform === "webos" ? webosVersion : sharedVersion;
  const suffix = platform === "roku" ? ".zip" : ".source.zip";
  const archive = path.join(artifactRoot, `openstream-${platform}-${version}${suffix}`);
  execFileSync("zip", ["-q", "-X", "-r", archive, "."], {
    cwd: path.join(outputRoot, platform),
    stdio: "ignore",
  });
}

let webosIpk = null;
const aresPackage = process.env.OPENSTREAM_ARES_PACKAGE || "ares-package";
if (spawnSync(aresPackage, ["--version"], { stdio: "ignore" }).status === 0) {
  execFileSync(aresPackage, ["-o", artifactRoot, webosDestination], { stdio: "inherit" });
  webosIpk = (await readdir(artifactRoot)).find((name) => name.endsWith(".ipk")) || null;
}

const manifest = {
  generatedAt: "deterministic",
  packages: ["webos", "tizen", "vidaa", "roku"],
  sourceVersion: sharedVersion,
  webos: {
    appId: webosAppinfo.id,
    version: webosVersion,
    ipk: webosIpk,
    sourceArchive: `openstream-webos-${webosVersion}.source.zip`,
    sellerIcon: "native/smart-tv/platforms/webos/store/icon-400.png",
  },
};
await writeFile(path.join(outputRoot, "manifest.json"), `${JSON.stringify(manifest, null, 2)}\n`);

async function pngDimensions(file) {
  const data = await readFile(file);
  const pngSignature = "89504e470d0a1a0a";
  if (data.subarray(0, 8).toString("hex") !== pngSignature) throw new Error(`${file} is not a PNG`);
  return { width: data.readUInt32BE(16), height: data.readUInt32BE(20) };
}
