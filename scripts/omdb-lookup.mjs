import { execFileSync } from "node:child_process";
import { userInfo } from "node:os";
import { pathToFileURL } from "node:url";

const IMDB_ID = /^tt\d{5,12}$/;
export const OMDB_KEYCHAIN_SERVICE = "com.cyberbanksy.openstream.omdb";

function readMacKeychain() {
  return execFileSync("/usr/bin/security", [
    "find-generic-password",
    "-a",
    userInfo().username,
    "-s",
    OMDB_KEYCHAIN_SERVICE,
    "-w",
  ], {
    encoding: "utf8",
    stdio: ["ignore", "pipe", "ignore"],
  });
}

export function resolveOmdbApiKey({
  apiKey,
  env = process.env,
  platform = process.platform,
  keychainLookup = readMacKeychain,
} = {}) {
  if (apiKey !== undefined) return String(apiKey).trim();
  const environmentKey = env.OMDB_API_KEY?.trim() ?? "";
  if (environmentKey) return environmentKey;
  if (platform !== "darwin") return "";
  try {
    return keychainLookup().trim();
  } catch {
    return "";
  }
}

/**
 * Credential-safe OMDb adapter for local tools and agents. Callers provide an
 * IMDb id; the API key stays in the process environment and is never returned.
 */
export async function lookupOmdb(imdbId, {
  apiKey,
  fetchImpl = globalThis.fetch,
} = {}) {
  if (!IMDB_ID.test(imdbId)) throw new Error("A valid IMDb ID is required.");
  const resolvedApiKey = resolveOmdbApiKey({ apiKey });
  if (!resolvedApiKey) throw new Error("OMDB_API_KEY is not configured.");

  const url = new URL("https://www.omdbapi.com/");
  url.searchParams.set("i", imdbId);
  url.searchParams.set("apikey", resolvedApiKey);
  url.searchParams.set("tomatoes", "true");
  const response = await fetchImpl(url, {
    headers: { Accept: "application/json" },
    signal: AbortSignal.timeout(10_000),
  });
  if (!response.ok) throw new Error(`OMDb lookup failed with HTTP ${response.status}.`);
  const payload = await response.json();
  if (payload?.Response === "False") throw new Error(payload.Error || "OMDb could not find this title.");

  return {
    imdbId: typeof payload.imdbID === "string" ? payload.imdbID : imdbId,
    title: typeof payload.Title === "string" ? payload.Title : "",
    year: typeof payload.Year === "string" ? payload.Year : "",
    rated: typeof payload.Rated === "string" ? payload.Rated : "",
    runtime: typeof payload.Runtime === "string" ? payload.Runtime : "",
    plot: typeof payload.Plot === "string" ? payload.Plot : "",
    ratings: Array.isArray(payload.Ratings)
      ? payload.Ratings.map((entry) => ({
        source: typeof entry?.Source === "string" ? entry.Source : "",
        value: typeof entry?.Value === "string" ? entry.Value : "",
      })).filter((entry) => entry.source && entry.value)
      : [],
  };
}

async function main() {
  const imdbId = process.argv[2] ?? "";
  const result = await lookupOmdb(imdbId);
  process.stdout.write(`${JSON.stringify(result, null, 2)}\n`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch((error) => {
    // The URL and credential are intentionally never included in errors.
    process.stderr.write(`${error.message}\n`);
    process.exitCode = 1;
  });
}
