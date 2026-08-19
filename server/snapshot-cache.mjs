import { mkdir, rename, writeFile } from "node:fs/promises";
import { dirname } from "node:path";

/**
 * Shared server-internal helpers for the periodic catalog snapshot caches
 * (TMDB metadata and Netflix Top 10). Both services clamp their configured
 * refresh interval the same way and persist their last-good snapshot with the
 * same atomic write, so the logic lives here instead of being duplicated.
 */

/** Clamps a configured refresh interval to `[minMs, ∞)`, falling back to `defaultMs`. */
export function normalizeInterval(value, { defaultMs, minMs }) {
  const parsed = Number(value ?? defaultMs);
  return Number.isFinite(parsed) ? Math.max(minMs, parsed) : defaultMs;
}

/** Atomically writes a snapshot to a private (0600) cache file via a temp-and-rename. */
export async function persistSnapshot(cacheFile, snapshot) {
  if (!cacheFile) return;
  await mkdir(dirname(cacheFile), { recursive: true });
  const temporary = `${cacheFile}.tmp`;
  await writeFile(temporary, JSON.stringify(snapshot), { encoding: "utf8", mode: 0o600 });
  await rename(temporary, cacheFile);
}
