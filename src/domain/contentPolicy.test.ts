import { describe, expect, it } from "vitest";
import { createKidsPolicy, filterCatalog, isItemAllowed, standardPolicy, strictestKidsPolicy } from "./contentPolicy";
import { isRatingAllowed, kidsRatingCap, normalizeRating } from "./ratings";
import type { MediaItem } from "./media";

const item = (overrides: Partial<MediaItem> = {}): MediaItem => ({
  id: "sample",
  externalIds: {},
  kind: "movie",
  title: "Sample Feature",
  synopsis: "A calm expedition documentary.",
  year: 2026,
  rating: "PG",
  runtimeMinutes: 100,
  artworkCell: [0, 0],
  candidates: [],
  ...overrides,
});

describe("normalizeRating", () => {
  it("maps MPAA and TV certifications to comparable ages", () => {
    expect(normalizeRating("G")?.age).toBe(0);
    expect(normalizeRating("PG-13")?.age).toBe(13);
    expect(normalizeRating("pg13")?.age).toBe(13);
    expect(normalizeRating("TV-14")?.age).toBe(14);
    expect(normalizeRating("TV-MA")?.age).toBe(17);
    expect(normalizeRating("R")?.age).toBe(17);
  });

  it("returns undefined for unrated or unknown labels", () => {
    expect(normalizeRating(undefined)).toBeUndefined();
    expect(normalizeRating("")).toBeUndefined();
    expect(normalizeRating("Not Rated")).toBeUndefined();
    expect(normalizeRating("18+")).toBeUndefined();
  });

  it("treats unknown ratings as blocked at any cap", () => {
    expect(isRatingAllowed("Unrated", 14)).toBe(false);
    expect(isRatingAllowed(undefined, 14)).toBe(false);
  });
});

describe("kids policy", () => {
  it("allows PG-13 and TV-14 but blocks R and TV-MA at the teens cap", () => {
    const policy = createKidsPolicy("teens", []);
    expect(isItemAllowed(item({ rating: "PG-13" }), policy)).toBe(true);
    expect(isItemAllowed(item({ rating: "TV-14" }), policy)).toBe(true);
    expect(isItemAllowed(item({ rating: "R" }), policy)).toBe(false);
    expect(isItemAllowed(item({ rating: "TV-MA" }), policy)).toBe(false);
    expect(isItemAllowed(item({ rating: "NC-17" }), policy)).toBe(false);
  });

  it("blocks unrated content in kids mode but allows it in standard mode", () => {
    const policy = createKidsPolicy("teens", []);
    expect(isItemAllowed(item({ rating: "" }), policy)).toBe(false);
    expect(isItemAllowed(item({ rating: "" }), standardPolicy)).toBe(true);
  });

  it("applies stricter caps for younger audiences", () => {
    const littleKids = createKidsPolicy("little-kids", []);
    expect(isItemAllowed(item({ rating: "G" }), littleKids)).toBe(true);
    expect(isItemAllowed(item({ rating: "PG" }), littleKids)).toBe(false);
    const bigKids = createKidsPolicy("big-kids", []);
    expect(isItemAllowed(item({ rating: "PG" }), bigKids)).toBe(true);
    expect(isItemAllowed(item({ rating: "PG-13" }), bigKids)).toBe(false);
  });

  it("blocks guardian-chosen terms in titles and synopses", () => {
    const policy = createKidsPolicy("teens", ["Redline", "haunted"]);
    expect(isItemAllowed(item({ title: "Redline", rating: "PG" }), policy)).toBe(false);
    expect(isItemAllowed(item({ synopsis: "A haunted lighthouse mystery.", rating: "PG" }), policy)).toBe(false);
    expect(isItemAllowed(item({ title: "Solar Drift", rating: "PG" }), policy)).toBe(true);
  });

  it("filters a catalog and keeps standard mode untouched", () => {
    const items = [
      item({ id: "a", rating: "G" }),
      item({ id: "b", rating: "R" }),
      item({ id: "c", rating: "TV-14" }),
      item({ id: "d", rating: "Not Rated" }),
    ];
    expect(filterCatalog(items, standardPolicy)).toHaveLength(4);
    expect(filterCatalog(items, createKidsPolicy("teens", [])).map((entry) => entry.id)).toEqual(["a", "c"]);
  });

  it("fails closed to the strictest cap", () => {
    expect(strictestKidsPolicy.maxAge).toBe(kidsRatingCap("little-kids").maxAge);
    expect(isItemAllowed(item({ rating: "PG-13" }), strictestKidsPolicy)).toBe(false);
  });
});
