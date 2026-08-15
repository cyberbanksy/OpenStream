import assert from "node:assert/strict";
import { access, readFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";

const projectRoot = join(dirname(fileURLToPath(import.meta.url)), "..");

const obsoleteRuntimeFiles = [
  "brand/openstream-x-cube.svg",
  "native/android-xr/app/src/main/java/com/orgista/openstream/data/BuiltInStremioAddons.kt",
  "native/android-xr/app/src/main/java/com/orgista/openstream/data/DemoCatalog.kt",
  "native/android-xr/app/src/main/java/com/orgista/openstream/iptv/IptvCatalogCache.kt",
  "native/android-xr/app/src/main/res/raw/openstream_demo.mp4",
  "native/android-xr/app/src/main/res/drawable-nodpi/northstar_hero.png",
  "native/apple/Sources/OpenStreamApple/Resources/northstar-hero.png",
  "public/demo/openstream-demo.mp4",
  "src/assets/northstar-hero.png",
  "src/assets/openstream-mark.png",
];

const productionFiles = [
  "src/data/catalog.ts",
  "src/data/connections.ts",
  "native/android-xr/app/src/main/java/com/orgista/openstream/data/NasStore.kt",
  "native/android-xr/app/src/main/java/com/orgista/openstream/iptv/IptvStore.kt",
  "native/smart-tv/shared/app.mjs",
  "native/roku/components/OpenStreamScene.brs",
];

test("production packages contain no obsolete demo or compiled-source assets", async () => {
  for (const relativePath of obsoleteRuntimeFiles) {
    await assert.rejects(access(join(projectRoot, relativePath)), undefined, relativePath);
  }
});

test("fresh-install source definitions stay empty and provider-neutral", async () => {
  const sources = await Promise.all(productionFiles.map((path) => readFile(join(projectRoot, path), "utf8")));
  const combined = sources.join("\n");

  assert.match(sources[0], /export const catalog: MediaItem\[\] = \[\];/);
  assert.match(sources[1], /export const initialConnections: ConnectionSummary\[\] = \[\];/);
  assert.doesNotMatch(combined, /v3-cinemeta|streaming-catalogs|torrentio|opensubtitles-v3|watch-next/i);
  assert.doesNotMatch(combined, /raw\.githubusercontent\.com\/Free-TV|iptv-org\.github\.io\/iptv/i);
  assert.doesNotMatch(combined, /Northstar|Last Signal|Atlas NAS/i);
});
