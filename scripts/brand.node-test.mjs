import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";

const projectRoot = join(dirname(fileURLToPath(import.meta.url)), "..");

test("canonical three-bar brand assets are accessible and use the current geometry", async () => {
  const [mark, icon, manifestSource] = await Promise.all([
    readFile(join(projectRoot, "brand/openstream-mark.svg"), "utf8"),
    readFile(join(projectRoot, "brand/openstream-app-icon.svg"), "utf8"),
    readFile(join(projectRoot, "brand/manifest.json"), "utf8"),
  ]);
  const manifest = JSON.parse(manifestSource);

  assert.match(mark, /viewBox="0 0 96 96"[^>]+role="img"/);
  assert.match(mark, /<title[^>]*>OpenStream<\/title>/);
  assert.match(mark, /<desc[^>]*>[^<]+<\/desc>/);
  assert.equal((mark.match(/<path\b/g) ?? []).length, 3);
  assert.doesNotMatch(mark, /<animate\b|cube|circle/i);
  assert.match(icon, /viewBox="0 0 400 400"[^>]+role="img"/);
  assert.equal((icon.match(/<path\b/g) ?? []).length, 3);
  assert.equal(manifest.mark, "openstream-mark.svg");
  assert.equal(manifest.appIcon, "openstream-app-icon.svg");
});
