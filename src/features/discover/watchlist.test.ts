import { describe, expect, it } from "vitest";
import {
  addToWatchlist,
  isInWatchlist,
  removeFromWatchlist,
  toggleWatchlist,
  WATCHLIST_STORAGE_KEY,
} from "./watchlist";

function memoryStore(initial?: string) {
  const data = new Map<string, string>();
  if (initial !== undefined) data.set(WATCHLIST_STORAGE_KEY, initial);
  return {
    getItem: (key: string) => data.get(key) ?? null,
    setItem: (key: string, value: string) => void data.set(key, value),
  };
}

describe("watchlist persistence", () => {
  it("adds, reports, and removes ids", () => {
    const store = memoryStore();
    expect(isInWatchlist("item-1", store)).toBe(false);
    addToWatchlist("item-1", store);
    addToWatchlist("item-2", store);
    addToWatchlist("item-1", store);
    expect(isInWatchlist("item-1", store)).toBe(true);
    expect(JSON.parse(store.getItem(WATCHLIST_STORAGE_KEY) ?? "[]")).toEqual(["item-1", "item-2"]);
    removeFromWatchlist("item-1", store);
    expect(isInWatchlist("item-1", store)).toBe(false);
    expect(isInWatchlist("item-2", store)).toBe(true);
  });

  it("toggles membership and reports the new state", () => {
    const store = memoryStore();
    expect(toggleWatchlist("item-1", store)).toBe(true);
    expect(isInWatchlist("item-1", store)).toBe(true);
    expect(toggleWatchlist("item-1", store)).toBe(false);
    expect(isInWatchlist("item-1", store)).toBe(false);
  });

  it("treats corrupted or foreign payloads as an empty list", () => {
    expect(isInWatchlist("item-1", memoryStore("not json"))).toBe(false);
    expect(isInWatchlist("item-1", memoryStore("{\"item-1\":true}"))).toBe(false);
    const mixed = memoryStore(JSON.stringify(["item-1", 7, null]));
    expect(isInWatchlist("item-1", mixed)).toBe(true);
    addToWatchlist("item-2", mixed);
    expect(JSON.parse(mixed.getItem(WATCHLIST_STORAGE_KEY) ?? "[]")).toEqual(["item-1", "item-2"]);
  });
});
