import assert from "node:assert/strict";
import test from "node:test";
import { createTmdbMetadataService } from "./metadata.mjs";

function response(results) {
  return { ok: true, status: 200, json: async () => ({ results }) };
}

test("refresh reuses a v4 credential and deduplicates recent movie metadata", async () => {
  const requests = [];
  const service = createTmdbMetadataService({
    credential: "eyJheader.payload.signature",
    cacheFile: null,
    now: () => 1234,
    fetchImpl: async (url, options) => {
      requests.push({ url: String(url), options });
      return response([{ id: 7, title: "Northstar", release_date: "2026-01-02", poster_path: "/n.jpg" }]);
    },
  });

  const snapshot = await service.refresh();

  assert.equal(requests.length, 3);
  assert.equal(requests[0].options.headers.Authorization, "Bearer eyJheader.payload.signature");
  assert.equal(snapshot.movies.length, 1);
  assert.deepEqual(snapshot.movies[0], {
    id: 7,
    title: "Northstar",
    overview: "",
    releaseDate: "2026-01-02",
    voteAverage: 0,
    popularity: 0,
    posterPath: "/n.jpg",
    backdropPath: null,
    genreIds: [],
  });
  assert.equal(snapshot.refreshedAt, 1234);
});

test("v3 credentials use the api_key query and disabled service stays offline", async () => {
  const urls = [];
  const v3Credential = "a".repeat(32);
  const enabled = createTmdbMetadataService({
    credential: v3Credential,
    cacheFile: null,
    fetchImpl: async (url) => { urls.push(String(url)); return response([]); },
  });
  await enabled.refresh();
  assert.equal(new URL(urls[0]).searchParams.get("api_key"), v3Credential);

  let called = false;
  const disabled = createTmdbMetadataService({
    credential: "",
    cacheFile: null,
    fetchImpl: async () => { called = true; return response([]); },
  });
  assert.equal((await disabled.refresh()).enabled, false);
  assert.equal(called, false);
});
