import assert from "node:assert/strict";
import test from "node:test";
import { exactTitle, normalizeTitle, searchUrl, TITLE_SEARCH_CASES } from "./title-search-audit.mjs";

test("search audit covers exactly 50 movies and 50 series", () => {
  assert.equal(TITLE_SEARCH_CASES.length, 100);
  assert.equal(TITLE_SEARCH_CASES.filter(({ type }) => type === "movie").length, 50);
  assert.equal(TITLE_SEARCH_CASES.filter(({ type }) => type === "series").length, 50);
});

test("punctuation-independent exact matching still rejects unrelated titles", () => {
  assert.equal(normalizeTitle("Spider-Man: No Way Home"), "spider man no way home");
  assert.equal(exactTitle([{ id: "right", name: "Spider-Man: No Way Home" }], "spider man no way home")?.id, "right");
  assert.equal(exactTitle([{ id: "wrong", name: "Spider-Man: Homecoming" }], "spider man no way home"), null);
});

test("catalog search path safely encodes query input", () => {
  assert.equal(
    searchUrl({ type: "series", query: "Band of Brothers" }, "https://catalog.test"),
    "https://catalog.test/catalog/series/top/search=Band%20of%20Brothers.json",
  );
});
