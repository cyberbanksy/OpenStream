import assert from "node:assert/strict";
import test from "node:test";
import { lookupOmdb, resolveOmdbApiKey } from "./omdb-lookup.mjs";

test("OMDb credential resolver prefers the environment and uses macOS Keychain as a local fallback", () => {
  let keychainReads = 0;
  assert.equal(resolveOmdbApiKey({
    env: { OMDB_API_KEY: " environment-key " },
    platform: "darwin",
    keychainLookup: () => {
      keychainReads += 1;
      return "keychain-key";
    },
  }), "environment-key");
  assert.equal(keychainReads, 0);

  assert.equal(resolveOmdbApiKey({
    env: {},
    platform: "darwin",
    keychainLookup: () => " keychain-key ",
  }), "keychain-key");
  assert.equal(resolveOmdbApiKey({
    env: {},
    platform: "linux",
    keychainLookup: () => {
      throw new Error("must not read Keychain on another platform");
    },
  }), "");
});

test("OMDb lookup requires an IMDb id and credential", async () => {
  await assert.rejects(() => lookupOmdb("bad-id", { apiKey: "test-key" }), /IMDb ID/);
  await assert.rejects(() => lookupOmdb("tt3896198", { apiKey: "" }), /OMDB_API_KEY/);
});

test("OMDb lookup returns sanitized metadata without exposing its request credential", async () => {
  let requestedUrl = "";
  const result = await lookupOmdb("tt3896198", {
    apiKey: "test-only-key",
    fetchImpl: async (url) => {
      requestedUrl = url.toString();
      return new Response(JSON.stringify({
        Response: "True",
        imdbID: "tt3896198",
        Title: "Example",
        Year: "2025",
        Rated: "PG-13",
        Runtime: "120 min",
        Plot: "Plot",
        Ratings: [{ Source: "Rotten Tomatoes", Value: "90%" }],
      }), { status: 200, headers: { "Content-Type": "application/json" } });
    },
  });

  assert.match(requestedUrl, /^https:\/\/www\.omdbapi\.com\//);
  assert.match(requestedUrl, /apikey=test-only-key/);
  assert.deepEqual(result, {
    imdbId: "tt3896198",
    title: "Example",
    year: "2025",
    rated: "PG-13",
    runtime: "120 min",
    plot: "Plot",
    ratings: [{ source: "Rotten Tomatoes", value: "90%" }],
  });
  assert.doesNotMatch(JSON.stringify(result), /test-only-key/);
});
