import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const descriptorPath = new URL("../native/smart-tv/platforms/webos/appinfo.json", import.meta.url);
const appPath = new URL("../native/smart-tv/shared/app.mjs", import.meta.url);
const buildPath = new URL("./build-tv-packages.mjs", import.meta.url);

test("webOS store metadata uses supported FHD packaged-app fields", async () => {
  const appinfo = JSON.parse(await readFile(descriptorPath, "utf8"));

  assert.equal(appinfo.id, "com.cyberbanksy.app.openstream");
  assert.equal(appinfo.type, "web");
  assert.equal(appinfo.main, "index.html");
  assert.equal(appinfo.resolution, "1920x1080");
  assert.equal(appinfo.disableBackHistoryAPI, false);
  assert.equal(appinfo.icon, "icon-80.png");
  assert.equal(appinfo.largeIcon, "icon-130.png");
  assert.equal(appinfo.splashBackground, "splash-1920x1080.png");
  assert.ok(!("screenSaverProperties" in appinfo));
  assert.ok(appinfo.appDescription.length <= 60);
});

test("webOS runtime contains only configured video sources", async () => {
  const app = await readFile(appPath, "utf8");

  assert.doesNotMatch(app, /demoCatalog|Northstar|Atlas NAS|openstream_demo/i);
  assert.match(app, /OpenStreamTV/);
  assert.match(app, /performanceProfile/);
  assert.match(app, /selectSpatialTarget/);
  assert.match(app, /video\.src = item\.uri/);
  assert.match(app, /item\.height >= 2160/);
  assert.match(app, /4K UHD/);
});

test("TV packages do not bundle a catalog or sample video", async () => {
  const build = await readFile(buildPath, "utf8");

  assert.doesNotMatch(build, /openstream_demo|northstar|catalogArtwork|hero-northstar/i);
});
