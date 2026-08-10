import { mkdir, readFile, rename, writeFile } from "node:fs/promises";
import { dirname } from "node:path";

const OFFICIAL_COUNTRY_TSV = "https://www.netflix.com/tudum/top10/data/all-weeks-countries.tsv";
const DEFAULT_REFRESH_MS = 4 * 60 * 60_000;
const MIN_REFRESH_MS = 30 * 60_000;
const DAILY_STALE_AFTER_MS = 30 * 60 * 60_000;
const MAX_DAILY_BYTES = 5 * 1024 * 1024;
const MAX_OFFICIAL_BYTES = 40 * 1024 * 1024;

function normalizeCountry(value) {
  const country = String(value ?? "US").trim().toUpperCase();
  if (!/^[A-Z]{2}$/.test(country)) throw new Error("Netflix Top 10 country must be an ISO 3166-1 alpha-2 code.");
  return country;
}

function normalizeInterval(value) {
  const parsed = Number(value ?? DEFAULT_REFRESH_MS);
  return Number.isFinite(parsed) ? Math.max(MIN_REFRESH_MS, parsed) : DEFAULT_REFRESH_MS;
}

function decodeHtml(value) {
  return value.replace(/&(#x[0-9a-f]+|#\d+|amp|quot|apos|lt|gt);/gi, (_, entity) => {
    const normalized = entity.toLowerCase();
    if (normalized === "amp") return "&";
    if (normalized === "quot") return "\"";
    if (normalized === "apos") return "'";
    if (normalized === "lt") return "<";
    if (normalized === "gt") return ">";
    const radix = normalized.startsWith("#x") ? 16 : 10;
    const digits = normalized.replace(/^#x?/, "");
    const codePoint = Number.parseInt(digits, radix);
    return Number.isInteger(codePoint) ? String.fromCodePoint(codePoint) : "";
  });
}

function attribute(tag, name) {
  const match = tag.match(new RegExp(`\\b${name}=(?:"([^"]*)"|'([^']*)')`, "i"));
  return match ? decodeHtml(match[1] ?? match[2] ?? "") : "";
}

function publicTitleUrl(value, expectedId) {
  try {
    const url = new URL(value);
    if (url.protocol !== "https:" || url.hostname !== "www.netflix.com" || url.username || url.password) return null;
    const match = url.pathname.match(/^\/title\/(\d{5,12})\/?$/);
    if (!match || match[1] !== expectedId) return null;
    url.search = "";
    url.hash = "";
    return url.toString();
  } catch {
    return null;
  }
}

/** Parses the unauthenticated regional Trending Now row. No cookies or member API are used. */
export function parseNetflixTrendingHtml(html, country = "US") {
  normalizeCountry(country);
  if (typeof html !== "string") return [];
  const marker = html.search(/data-uia=(?:"trending-now"|'trending-now')/i);
  if (marker < 0) return [];
  const section = html.slice(marker, marker + 1_500_000);
  const unique = new Set();
  const titles = [];
  for (const match of section.matchAll(/<a\b[^>]*>/gi)) {
    const tag = match[0];
    const dataId = attribute(tag, "data-uia").match(/^titleId-(\d{5,12})$/i)?.[1];
    const hrefId = attribute(tag, "href").match(/^https:\/\/www\.netflix\.com\/title\/(\d{5,12})\/?(?:[?#].*)?$/i)?.[1];
    const netflixId = dataId ?? hrefId;
    if (!netflixId || unique.has(netflixId)) continue;
    const label = attribute(tag, "aria-label").replace(/^Go to\s+/i, "").trim();
    const url = publicTitleUrl(attribute(tag, "href"), netflixId);
    if (!label || label.length > 300 || !url) continue;
    unique.add(netflixId);
    titles.push({
      rank: titles.length + 1,
      netflixId,
      title: label,
      seasonTitle: null,
      url,
      category: "overall",
    });
    if (titles.length === 10) break;
  }
  return titles;
}

function officialTitle(row, category) {
  const rank = Number.parseInt(row.weekly_rank, 10);
  const title = row.show_title?.trim();
  if (!Number.isInteger(rank) || rank < 1 || rank > 10 || !title || title.length > 300) return null;
  const season = row.season_title?.trim();
  return {
    rank,
    netflixId: null,
    title,
    seasonTitle: season && season !== "N/A" ? season : null,
    url: null,
    category,
  };
}

/** Parses Netflix's official weekly country TSV without CSV/TSV dependencies. */
export function parseNetflixOfficialCountryTsv(tsv, country = "US") {
  const normalizedCountry = normalizeCountry(country);
  if (typeof tsv !== "string" || !tsv.trim()) return { week: null, movies: [], series: [] };
  const lines = tsv.split(/\r?\n/).filter(Boolean);
  const headings = lines.shift()?.split("\t") ?? [];
  const required = ["country_iso2", "week", "category", "weekly_rank", "show_title", "season_title"];
  if (required.some((heading) => !headings.includes(heading))) return { week: null, movies: [], series: [] };
  const rows = lines.map((line) => {
    const values = line.split("\t");
    return Object.fromEntries(headings.map((heading, index) => [heading, values[index] ?? ""]));
  }).filter((row) => row.country_iso2.toUpperCase() === normalizedCountry && /^\d{4}-\d{2}-\d{2}$/.test(row.week));
  const week = rows.reduce((latest, row) => row.week > latest ? row.week : latest, "");
  if (!week) return { week: null, movies: [], series: [] };
  const latest = rows.filter((row) => row.week === week);
  const collect = (sourceCategory, category) => latest
    .filter((row) => row.category === sourceCategory)
    .map((row) => officialTitle(row, category))
    .filter(Boolean)
    .sort((left, right) => left.rank - right.rank)
    .slice(0, 10);
  return {
    week,
    movies: collect("Films", "movie"),
    series: collect("TV", "series"),
  };
}

async function persistSnapshot(cacheFile, snapshot) {
  if (!cacheFile) return;
  await mkdir(dirname(cacheFile), { recursive: true });
  const temporary = `${cacheFile}.tmp`;
  await writeFile(temporary, JSON.stringify(snapshot), { encoding: "utf8", mode: 0o600 });
  await rename(temporary, cacheFile);
}

function validList(value, category) {
  if (!Array.isArray(value) || value.length > 10) return [];
  return value.filter((entry, index) =>
    entry && entry.rank === index + 1 && typeof entry.title === "string" && entry.title.length > 0 && entry.title.length <= 300 &&
    entry.category === category);
}

function validatedCache(value, country) {
  if (!value || value.country !== country || !Number.isFinite(value.refreshedAt) || !value.lists) return null;
  const overall = validList(value.lists.overall, "overall");
  const movies = validList(value.lists.movies, "movie");
  const series = validList(value.lists.series, "series");
  if (overall.length !== 10 && movies.length !== 10 && series.length !== 10) return null;
  return {
    country,
    source: value.source === "netflix-official-weekly" ? value.source : "netflix-public-trending",
    cadence: value.cadence === "weekly" ? "weekly" : "daily",
    refreshedAt: value.refreshedAt,
    lastAttemptAt: Number.isFinite(value.lastAttemptAt) ? value.lastAttemptAt : value.refreshedAt,
    fallbackWeek: typeof value.fallbackWeek === "string" ? value.fallbackWeek : null,
    lists: { overall, movies, series },
  };
}

function hasTitles(snapshot) {
  return snapshot.lists.overall.length === 10 || snapshot.lists.movies.length === 10 || snapshot.lists.series.length === 10;
}

async function responseText(fetchImpl, url, maximumBytes, headers) {
  const response = await fetchImpl(url, {
    headers,
    redirect: "follow",
    signal: AbortSignal.timeout(20_000),
  });
  if (!response.ok) throw new Error(`Netflix Top 10 source returned HTTP ${response.status}.`);
  const declaredLength = Number(response.headers?.get?.("content-length"));
  if (Number.isFinite(declaredLength) && declaredLength > maximumBytes) throw new Error("Netflix Top 10 source exceeded its size limit.");
  const text = await response.text();
  if (Buffer.byteLength(text) > maximumBytes) throw new Error("Netflix Top 10 source exceeded its size limit.");
  return text;
}

/** Four-hour public cache with a last-good snapshot and official weekly fallback. */
export function createNetflixTop10Service({
  country = process.env.OPENSTREAM_NETFLIX_TOP10_COUNTRY ?? "US",
  refreshIntervalMs = process.env.OPENSTREAM_NETFLIX_TOP10_REFRESH_MS,
  cacheFile = process.env.OPENSTREAM_NETFLIX_TOP10_CACHE_FILE ?? "logs/netflix-top10-cache.json",
  dailyPageUrl,
  officialTsvUrl = OFFICIAL_COUNTRY_TSV,
  fetchImpl = globalThis.fetch,
  now = Date.now,
  onError = () => {},
} = {}) {
  const normalizedCountry = normalizeCountry(country);
  const intervalMs = normalizeInterval(refreshIntervalMs);
  const publicPage = dailyPageUrl ?? `https://embed.beta.netflix.com/${normalizedCountry.toLowerCase()}/title/80057281`;
  let timer;
  let refreshing;
  let restored = false;
  let snapshot = {
    country: normalizedCountry,
    source: "unavailable",
    cadence: "daily",
    refreshedAt: null,
    lastAttemptAt: null,
    fallbackWeek: null,
    lists: { overall: [], movies: [], series: [] },
  };

  function publicSnapshot() {
    return {
      ...snapshot,
      stale: !Number.isFinite(snapshot.refreshedAt) || now() - snapshot.refreshedAt > DAILY_STALE_AFTER_MS,
    };
  }

  async function restore() {
    if (restored || !cacheFile) return;
    restored = true;
    try {
      const cached = validatedCache(JSON.parse(await readFile(cacheFile, "utf8")), normalizedCountry);
      if (cached) snapshot = cached;
    } catch (error) {
      if (error?.code !== "ENOENT") onError(new Error("Netflix Top 10 cache could not be restored."));
    }
  }

  async function performRefresh() {
    const attemptedAt = now();
    try {
      const html = await responseText(fetchImpl, publicPage, MAX_DAILY_BYTES, {
        Accept: "text/html,application/xhtml+xml",
        "Accept-Language": "en-US,en;q=0.9",
        "User-Agent": "Mozilla/5.0 (compatible; OpenStreamTop10/0.1; +https://github.com/cyberbanksy/OpenStream)",
      });
      const overall = parseNetflixTrendingHtml(html, normalizedCountry);
      if (overall.length !== 10) throw new Error("Netflix public Trending Now markup did not contain ten titles.");
      snapshot = {
        country: normalizedCountry,
        source: "netflix-public-trending",
        cadence: "daily",
        refreshedAt: attemptedAt,
        lastAttemptAt: attemptedAt,
        fallbackWeek: snapshot.fallbackWeek,
        lists: { overall, movies: snapshot.lists.movies, series: snapshot.lists.series },
      };
      await persistSnapshot(cacheFile, snapshot);
      return publicSnapshot();
    } catch (dailyError) {
      try {
        const tsv = await responseText(fetchImpl, officialTsvUrl, MAX_OFFICIAL_BYTES, {
          Accept: "text/tab-separated-values,text/plain;q=0.9,*/*;q=0.8",
          Referer: "https://www.netflix.com/tudum/top10/",
          "User-Agent": "Mozilla/5.0 (compatible; OpenStreamTop10/0.1; +https://github.com/cyberbanksy/OpenStream)",
        });
        const fallback = parseNetflixOfficialCountryTsv(tsv, normalizedCountry);
        if (fallback.movies.length !== 10 || fallback.series.length !== 10) {
          throw new Error("Netflix official country dataset did not contain complete Top 10 lists.");
        }
        snapshot = {
          country: normalizedCountry,
          source: "netflix-official-weekly",
          cadence: "weekly",
          refreshedAt: attemptedAt,
          lastAttemptAt: attemptedAt,
          fallbackWeek: fallback.week,
          lists: { overall: [], movies: fallback.movies, series: fallback.series },
        };
        await persistSnapshot(cacheFile, snapshot);
        return publicSnapshot();
      } catch (weeklyError) {
        const failure = new Error("Netflix daily and weekly Top 10 refresh failed.", { cause: weeklyError });
        onError(failure);
        snapshot = { ...snapshot, lastAttemptAt: attemptedAt };
        if (hasTitles(snapshot)) return publicSnapshot();
        throw failure;
      }
    }
  }

  return {
    get country() { return normalizedCountry; },
    getSnapshot: publicSnapshot,
    refresh() {
      if (!refreshing) refreshing = performRefresh().finally(() => { refreshing = undefined; });
      return refreshing;
    },
    async start() {
      await restore();
      if (!timer) {
        timer = setInterval(() => { void this.refresh().catch(() => {}); }, intervalMs);
        timer.unref?.();
      }
      try {
        return await this.refresh();
      } catch {
        return publicSnapshot();
      }
    },
    close() {
      if (timer) clearInterval(timer);
      timer = undefined;
    },
  };
}
