import { fileURLToPath } from "node:url";

const MAX_ATTEMPTS = 4;

export function retryDelayMs(attempt) {
  return 250 * (2 ** Math.max(0, Math.min(attempt, 3)));
}

export function shouldRetry(status, attempt) {
  return attempt < MAX_ATTEMPTS - 1 && (status === 408 || status === 429 || status >= 500);
}

export function resourceNames(manifest) {
  return [...new Set((manifest.resources ?? []).map((value) =>
    typeof value === "string" ? value : value?.name,
  ).filter(Boolean))];
}

async function jsonWithRetry(url, { fetchImpl = fetch, wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms)) } = {}) {
  for (let attempt = 0; attempt < MAX_ATTEMPTS; attempt += 1) {
    let response;
    try {
      response = await fetchImpl(url, {
        headers: { accept: "application/json", "user-agent": "OpenStream-validator/1" },
        signal: AbortSignal.timeout(15_000),
      });
    } catch {
      if (attempt >= MAX_ATTEMPTS - 1) throw new Error("network failure");
      await wait(retryDelayMs(attempt));
      continue;
    }
    if (response.ok) return response.json();
    if (!shouldRetry(response.status, attempt)) throw new Error(`HTTP ${response.status}`);
    await wait(retryDelayMs(attempt));
  }
  throw new Error("validation attempts exhausted");
}

function endpoint(base, resource, type, id) {
  return `${base.replace(/\/manifest\.json$/, "")}/${resource}/${encodeURIComponent(type)}/${encodeURIComponent(id)}.json`;
}

export async function validateManifest(manifestUrl, options = {}) {
  const manifest = await jsonWithRetry(manifestUrl, options);
  if (!manifest?.id || !manifest?.name) throw new Error("invalid manifest");
  const resources = resourceNames(manifest);
  const usableCatalog = (manifest.catalogs ?? []).find((catalog) =>
    catalog?.type && catalog?.id && !(catalog.extra ?? []).some((extra) => extra?.isRequired),
  );
  let catalogItems = null;
  if (resources.includes("catalog") && usableCatalog) {
    const payload = await jsonWithRetry(endpoint(manifestUrl, "catalog", usableCatalog.type, usableCatalog.id), options);
    catalogItems = Array.isArray(payload?.metas) ? payload.metas.length : 0;
  }
  let directStreams = null;
  if (resources.includes("stream")) {
    const payload = await jsonWithRetry(endpoint(manifestUrl, "stream", "movie", "tt0111161"), options);
    directStreams = Array.isArray(payload?.streams)
      ? payload.streams.filter((stream) => /^https?:\/\//.test(stream?.url ?? "")).length
      : 0;
  }
  // Deliberately exclude manifestUrl and stream URLs: configured paths may
  // contain viewer credentials.
  return { id: manifest.id, name: manifest.name, resources, catalogItems, directStreams };
}

export async function validateConfiguredAddons({ configuredManifestUrl, ...options }) {
  const urls = configuredManifestUrl ? [configuredManifestUrl] : [];
  const reports = [];
  for (const url of urls) {
    try {
      reports.push({ ok: true, ...(await validateManifest(url, options)) });
    } catch (error) {
      reports.push({ ok: false, error: error instanceof Error ? error.message.replace(/https?:\/\/\S+/g, "[redacted]") : "unknown error" });
    }
  }
  return reports;
}

async function main() {
  const reports = await validateConfiguredAddons({
    configuredManifestUrl: process.env.OPENSTREAM_STREAM_ADDON_MANIFEST_URL,
  });
  console.log(JSON.stringify(reports, null, 2));
  if (reports.some((report) => !report.ok)) process.exitCode = 1;
}

if (process.argv[1] === fileURLToPath(import.meta.url)) await main();
