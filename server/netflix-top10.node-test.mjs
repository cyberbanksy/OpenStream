import assert from "node:assert/strict";
import { mkdtemp, readFile, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";
import {
  createNetflixTop10Service,
  parseNetflixOfficialCountryTsv,
  parseNetflixTrendingHtml,
} from "./netflix-top10.mjs";

const dailyTitles = [
  ["81426967", "My Life With the Walter Boys"],
  ["81715790", "72 HOURS"],
  ["81914143", "The Last House"],
  ["81683870", "The Idaho Murders: College Nightmare"],
  ["81509368", "I Will Find You"],
  ["81923353", "A Toxic Love Story"],
  ["80237957", "Avatar: The Last Airbender"],
  ["81695311", "The Hawk"],
  ["81748089", "Little House on the Prairie"],
  ["81459107", "Ransom Canyon"],
];

function trendingHtml(titles = dailyTitles) {
  const anchors = titles.map(([id, title]) =>
    `<li><a href="https://www.netflix.com/title/${id}" data-uia="titleId-${id}" aria-label="Go to ${title}">${title}</a></li>`).join("");
  return `<html><a data-uia="titleId-99999" aria-label="Go to Not trending"></a><div data-uia="trending-now"><h2>Trending Now</h2><ul>${anchors}</ul></div></html>`;
}

function officialTsv() {
  const header = "country_name\tcountry_iso2\tweek\tcategory\tweekly_rank\tshow_title\tseason_title\tcumulative_weeks_in_top_10";
  const old = "United States\tUS\t2026-07-26\tFilms\t1\tOld Movie\tN/A\t1";
  const movies = Array.from({ length: 10 }, (_, index) =>
    `United States\tUS\t2026-08-02\tFilms\t${index + 1}\tMovie ${index + 1}\tN/A\t1`);
  const series = Array.from({ length: 10 }, (_, index) =>
    `United States\tUS\t2026-08-02\tTV\t${index + 1}\tSeries ${index + 1}\tSeason ${index + 1}\t1`);
  return [header, old, ...movies, ...series].join("\n");
}

function textResponse(text, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: new Headers({ "content-length": String(Buffer.byteLength(text)) }),
    text: async () => text,
  };
}

test("public Trending Now parser preserves displayed order and returns ten unique titles", () => {
  const html = trendingHtml([...dailyTitles.slice(0, 3), dailyTitles[1], ...dailyTitles.slice(3)]);
  const titles = parseNetflixTrendingHtml(html, "US");

  assert.equal(titles.length, 10);
  assert.deepEqual(titles.slice(0, 3).map(({ rank, netflixId, title }) => ({ rank, netflixId, title })), [
    { rank: 1, netflixId: "81426967", title: "My Life With the Walter Boys" },
    { rank: 2, netflixId: "81715790", title: "72 HOURS" },
    { rank: 3, netflixId: "81914143", title: "The Last House" },
  ]);
  assert.equal(titles.some((entry) => entry.title === "Not trending"), false);
});

test("official TSV parser selects the latest country week and keeps movie and series ranks separate", () => {
  const result = parseNetflixOfficialCountryTsv(officialTsv(), "us");

  assert.equal(result.week, "2026-08-02");
  assert.equal(result.movies.length, 10);
  assert.equal(result.series.length, 10);
  assert.equal(result.movies[0].title, "Movie 1");
  assert.equal(result.series[9].seasonTitle, "Season 10");
});

test("service caches the daily public list without cookies or account state", async () => {
  const directory = await mkdtemp(join(tmpdir(), "openstream-netflix-top10-"));
  const cacheFile = join(directory, "top10.json");
  const requests = [];
  const service = createNetflixTop10Service({
    country: "US",
    cacheFile,
    now: () => 1_786_291_200_000,
    fetchImpl: async (url, options) => {
      requests.push({ url: String(url), options });
      return textResponse(trendingHtml());
    },
  });

  try {
    const snapshot = await service.refresh();
    assert.equal(snapshot.source, "netflix-public-trending");
    assert.equal(snapshot.cadence, "daily");
    assert.equal(snapshot.lists.overall.length, 10);
    assert.equal(requests.length, 1);
    assert.equal("cookie" in Object.fromEntries(Object.entries(requests[0].options.headers).map(([key, value]) => [key.toLowerCase(), value])), false);
    const persisted = JSON.parse(await readFile(cacheFile, "utf8"));
    assert.equal(persisted.lists.overall[1].title, "72 HOURS");
  } finally {
    service.close();
    await rm(directory, { recursive: true, force: true });
  }
});

test("service falls back to Netflix official weekly country data when public daily markup changes", async () => {
  const service = createNetflixTop10Service({
    country: "US",
    cacheFile: null,
    now: () => 1_786_291_200_000,
    fetchImpl: async (url) => String(url).includes("all-weeks-countries.tsv")
      ? textResponse(officialTsv())
      : textResponse("<html>changed</html>"),
  });

  const snapshot = await service.refresh();
  assert.equal(snapshot.source, "netflix-official-weekly");
  assert.equal(snapshot.cadence, "weekly");
  assert.equal(snapshot.lists.overall.length, 0);
  assert.equal(snapshot.lists.movies.length, 10);
  assert.equal(snapshot.lists.series.length, 10);
});

test("a failed refresh restores and retains the last valid private cache", async () => {
  const directory = await mkdtemp(join(tmpdir(), "openstream-netflix-top10-restore-"));
  const cacheFile = join(directory, "top10.json");
  const first = createNetflixTop10Service({
    cacheFile,
    now: () => 1_786_291_200_000,
    fetchImpl: async () => textResponse(trendingHtml()),
  });
  await first.refresh();
  first.close();

  let observedError;
  const restored = createNetflixTop10Service({
    cacheFile,
    now: () => 1_786_294_800_000,
    fetchImpl: async () => { throw new Error("offline"); },
    onError: (error) => { observedError = error; },
  });
  try {
    const snapshot = await restored.start();
    assert.equal(snapshot.lists.overall.length, 10);
    assert.equal(snapshot.source, "netflix-public-trending");
    assert.match(observedError?.message ?? "", /daily and weekly/i);
  } finally {
    restored.close();
    await rm(directory, { recursive: true, force: true });
  }
});
