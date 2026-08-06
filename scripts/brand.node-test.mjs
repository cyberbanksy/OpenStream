import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";

const projectRoot = join(dirname(fileURLToPath(import.meta.url)), "..");

test("X-to-cube brand asset is accessible, animated every three seconds, and motion-safe", async () => {
  const [svg, manifestSource] = await Promise.all([
    readFile(join(projectRoot, "brand/openstream-x-cube.svg"), "utf8"),
    readFile(join(projectRoot, "brand/manifest.json"), "utf8"),
  ]);
  const manifest = JSON.parse(manifestSource);

  assert.match(svg, /<svg[^>]+viewBox="0 0 512 512"[^>]+role="img"/);
  assert.match(svg, /<title[^>]*>OpenStream X to cube animated mark<\/title>/);
  assert.match(svg, /<desc[^>]*>[^<]+<\/desc>/);
  assert.match(svg, /@media \(prefers-reduced-motion: reduce\)/);
  assert.match(svg, /\.animated \{ display: none; \}/);
  assert.match(svg, /\.reduced \{ display: inline; \}/);

  const animations = [...svg.matchAll(/<animate\b[^>]*>/g)].map(([animation]) => animation);
  assert.ok(animations.length >= 8, "the X and cube transition should animate multiple paths and faces");
  for (const animation of animations) {
    assert.match(animation, /dur="3s"/);
    assert.match(animation, /repeatCount="indefinite"/);
  }

  assert.equal(manifest.source, "openstream-x-cube.svg");
  assert.equal(manifest.loopDurationSeconds, 3);
  assert.equal(manifest.reducedMotionFallback, "static X");
});
