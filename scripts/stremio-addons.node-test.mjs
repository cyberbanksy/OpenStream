import assert from "node:assert/strict";
import test from "node:test";
import { publicManifestUrls, validateConfiguredAddons, validateManifest } from "./stremio-addons.mjs";

function response(status, body) {
  return { ok: status >= 200 && status < 300, status, json: async () => body };
}

test("extracts only compiled HTTPS manifest constants", () => {
  const urls = publicManifestUrls('manifestUrl = "https://one.test/manifest.json"\nmanifestUrl = "http://bad.test/manifest.json"');
  assert.deepEqual(urls, ["https://one.test/manifest.json"]);
});

test("retries a transient manifest failure and returns a sanitized capability report", async () => {
  let calls = 0;
  const fetchImpl = async (url) => {
    calls += 1;
    if (calls === 1) return response(500, {});
    if (url.endsWith("manifest.json")) {
      return response(200, { id: "test", name: "Test", resources: ["stream"], catalogs: [] });
    }
    return response(200, { streams: [{ url: "https://video.test/file" }, { infoHash: "abc" }] });
  };
  const report = await validateManifest("https://addon.test/private-secret/manifest.json", {
    fetchImpl,
    wait: async () => {},
  });

  assert.equal(report.directStreams, 1);
  assert.equal(calls, 3);
  assert.equal(JSON.stringify(report).includes("private-secret"), false);
});

test("configured failures never reveal a credential-bearing URL", async () => {
  const reports = await validateConfiguredAddons({
    source: "",
    configuredManifestUrl: "https://addon.test/very-secret/manifest.json",
    fetchImpl: async () => response(401, {}),
    wait: async () => {},
  });

  assert.equal(reports[0].ok, false);
  assert.equal(JSON.stringify(reports).includes("very-secret"), false);
});
