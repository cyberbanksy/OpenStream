import { readFile } from "node:fs/promises";
import { normalizeInterval, persistSnapshot } from "./snapshot-cache.mjs";

const TMDB_BASE = "https://api.themoviedb.org/3";
const DEFAULT_REFRESH_MS = 6 * 60 * 60_000;
const MIN_REFRESH_MS = 15 * 60_000;

function movie(value) {
  return {
    id: Number(value.id),
    title: typeof value.title === "string" ? value.title : "",
    overview: typeof value.overview === "string" ? value.overview : "",
    releaseDate: typeof value.release_date === "string" ? value.release_date : "",
    voteAverage: Number.isFinite(value.vote_average) ? value.vote_average : 0,
    popularity: Number.isFinite(value.popularity) ? value.popularity : 0,
    posterPath: typeof value.poster_path === "string" ? value.poster_path : null,
    backdropPath: typeof value.backdrop_path === "string" ? value.backdrop_path : null,
    genreIds: Array.isArray(value.genre_ids) ? value.genre_ids.filter(Number.isInteger) : [],
  };
}

function restoredMovie(entry) {
  if (!entry || typeof entry !== "object" || !Number.isInteger(entry.id)) return null;
  return {
    id: entry.id,
    title: typeof entry.title === "string" ? entry.title : "",
    overview: typeof entry.overview === "string" ? entry.overview : "",
    releaseDate: typeof entry.releaseDate === "string" ? entry.releaseDate : "",
    voteAverage: Number.isFinite(entry.voteAverage) ? entry.voteAverage : 0,
    popularity: Number.isFinite(entry.popularity) ? entry.popularity : 0,
    posterPath: typeof entry.posterPath === "string" ? entry.posterPath : null,
    backdropPath: typeof entry.backdropPath === "string" ? entry.backdropPath : null,
    genreIds: Array.isArray(entry.genreIds) ? entry.genreIds.filter(Number.isInteger) : [],
  };
}

/** Validates a persisted metadata snapshot before it is trusted at startup. */
function validatedCache(value) {
  if (!value || typeof value !== "object" || !Array.isArray(value.movies)) return null;
  return {
    refreshedAt: Number.isFinite(value.refreshedAt) ? value.refreshedAt : null,
    movies: value.movies.map(restoredMovie).filter(Boolean),
  };
}

/** Periodically caches public TMDB catalog metadata; it never downloads media files. */
export function createTmdbMetadataService({
  credential = process.env.TMDB_READ_TOKEN ?? process.env.TMDB_API_KEY ?? "",
  refreshIntervalMs = process.env.OPENSTREAM_METADATA_REFRESH_MS,
  cacheFile = process.env.OPENSTREAM_METADATA_CACHE_FILE ?? "logs/metadata-cache.json",
  fetchImpl = globalThis.fetch,
  now = Date.now,
  onError = () => {},
} = {}) {
  const reusableCredential = credential.trim();
  const bearer = reusableCredential.startsWith("eyJ") ? reusableCredential : null;
  const intervalMs = normalizeInterval(refreshIntervalMs, { defaultMs: DEFAULT_REFRESH_MS, minMs: MIN_REFRESH_MS });
  let timer;
  let refreshing;
  let restored = false;
  let snapshot = { enabled: Boolean(reusableCredential), refreshedAt: null, movies: [] };

  async function restore() {
    if (restored || !cacheFile) return;
    restored = true;
    try {
      const cached = validatedCache(JSON.parse(await readFile(cacheFile, "utf8")));
      if (cached) snapshot = { enabled: Boolean(reusableCredential), ...cached };
    } catch (error) {
      if (error?.code !== "ENOENT") onError(new Error("TMDB metadata cache could not be restored."));
    }
  }

  async function page(path) {
    const url = new URL(`${TMDB_BASE}${path}`);
    if (!bearer) url.searchParams.set("api_key", reusableCredential);
    const response = await fetchImpl(url, {
      headers: {
        Accept: "application/json",
        ...(bearer ? { Authorization: `Bearer ${bearer}` } : {}),
      },
      signal: AbortSignal.timeout(10_000),
    });
    if (!response.ok) throw new Error(`TMDB metadata refresh failed with HTTP ${response.status}.`);
    const payload = await response.json();
    return Array.isArray(payload.results) ? payload.results : [];
  }

  async function performRefresh() {
    if (!reusableCredential) return snapshot;
    const pages = await Promise.all([
      page("/movie/now_playing?region=US"),
      page("/movie/popular?region=US"),
      page("/trending/movie/week"),
    ]);
    const unique = new Map();
    pages.flat().forEach((entry) => {
      if (Number.isInteger(entry?.id) && !unique.has(entry.id)) unique.set(entry.id, movie(entry));
    });
    snapshot = {
      enabled: true,
      refreshedAt: now(),
      movies: [...unique.values()].sort((left, right) =>
        right.popularity - left.popularity || right.voteAverage - left.voteAverage || left.title.localeCompare(right.title)),
    };
    await persistSnapshot(cacheFile, snapshot);
    return snapshot;
  }

  return {
    get enabled() { return Boolean(reusableCredential); },
    getSnapshot() { return snapshot; },
    refresh() {
      if (!refreshing) refreshing = performRefresh().finally(() => { refreshing = undefined; });
      return refreshing;
    },
    async start() {
      await restore();
      if (!reusableCredential || timer) return snapshot;
      timer = setInterval(() => { void this.refresh().catch(onError); }, intervalMs);
      timer.unref?.();
      return this.refresh();
    },
    close() {
      if (timer) clearInterval(timer);
      timer = undefined;
    },
  };
}
