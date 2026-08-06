import { cp, mkdir, readFile, rm, writeFile } from "node:fs/promises";
import { execFileSync } from "node:child_process";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const outputRoot = path.join(root, "dist/tv");
const shared = path.join(root, "native/smart-tv/shared");
const demo = path.join(root, "native/android-xr/app/src/main/res/raw/openstream_demo.mp4");
const icon = path.join(root, "brand/previews/openstream-x-cube-mark.png");

await rm(outputRoot, { recursive: true, force: true });
await mkdir(outputRoot, { recursive: true });

for (const platform of ["webos", "tizen", "vidaa"]) {
  const destination = path.join(outputRoot, platform);
  await mkdir(destination, { recursive: true });
  for (const name of ["index.html", "styles.css", "app.mjs", "core.mjs"]) {
    await cp(path.join(shared, name), path.join(destination, name));
  }
  await cp(demo, path.join(destination, "openstream_demo.mp4"));
  await cp(icon, path.join(destination, "icon.png"));
  if (platform === "webos") await cp(icon, path.join(destination, "largeicon.png"));
  const descriptor = platform === "webos" ? "appinfo.json" : platform === "tizen" ? "config.xml" : "app.json";
  await cp(path.join(root, `native/smart-tv/platforms/${platform}/${descriptor}`), path.join(destination, descriptor));
}

const rokuSource = path.join(root, "native/roku");
const rokuDestination = path.join(outputRoot, "roku");
await cp(rokuSource, rokuDestination, { recursive: true });
await mkdir(path.join(rokuDestination, "media"), { recursive: true });
await mkdir(path.join(rokuDestination, "images"), { recursive: true });
await cp(demo, path.join(rokuDestination, "media/openstream_demo.mp4"));
for (const [name, width, height] of [
  ["mm_icon_focus_fhd.png", 540, 405],
  ["mm_icon_side_fhd.png", 108, 81],
  ["splash_screen_fhd.png", 1920, 1080],
]) {
  const destination = path.join(rokuDestination, `images/${name}`);
  const source = name === "splash_screen_fhd.png"
    ? path.join(root, "brand/previews/openstream-x-cube-preview.png")
    : icon;
  await cp(source, destination);
  if (process.platform === "darwin") {
    execFileSync("sips", ["-z", String(height), String(width), destination], { stdio: "ignore" });
  }
}

const manifest = {
  generatedAt: "deterministic",
  packages: ["webos", "tizen", "vidaa", "roku"],
  sourceVersion: "0.1.0",
};
await writeFile(path.join(outputRoot, "manifest.json"), `${JSON.stringify(manifest, null, 2)}\n`);

// Fail fast when a platform descriptor or shared entry point is accidentally incomplete.
for (const platform of ["webos", "tizen", "vidaa"]) {
  const entry = await readFile(path.join(outputRoot, platform, "index.html"), "utf8");
  if (!entry.includes("app.mjs")) throw new Error(`${platform} package has no entry script`);
}

const rokuManifest = await readFile(path.join(rokuDestination, "manifest"), "utf8");
for (const required of ["title=OpenStream", "ui_resolutions=fhd", "splash_screen_fhd="]) {
  if (!rokuManifest.includes(required)) throw new Error(`Roku manifest is missing ${required}`);
}
const rokuScene = await readFile(path.join(rokuDestination, "components/OpenStreamScene.xml"), "utf8");
if (!rokuScene.includes('extends="Scene"') || !rokuScene.includes('id="video"')) {
  throw new Error("Roku SceneGraph package is incomplete");
}

const artifactRoot = path.join(outputRoot, "artifacts");
await mkdir(artifactRoot, { recursive: true });
for (const platform of ["webos", "tizen", "vidaa", "roku"]) {
  const suffix = platform === "roku" ? ".zip" : ".source.zip";
  const archive = path.join(artifactRoot, `openstream-${platform}-0.1.0${suffix}`);
  execFileSync("zip", ["-q", "-r", archive, "."], {
    cwd: path.join(outputRoot, platform),
    stdio: "ignore",
  });
}
