export const WATCHLIST_STORAGE_KEY = "openstream.watchlist.v1";

type WatchlistStore = Pick<Storage, "getItem" | "setItem">;

function readIds(store: WatchlistStore): string[] {
  try {
    const raw = store.getItem(WATCHLIST_STORAGE_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw) as unknown;
    return Array.isArray(parsed) ? parsed.filter((id): id is string => typeof id === "string") : [];
  } catch {
    return [];
  }
}

function writeIds(store: WatchlistStore, ids: string[]) {
  try {
    store.setItem(WATCHLIST_STORAGE_KEY, JSON.stringify(ids));
  } catch {
    // Storage can be unavailable (private mode, quota); the watchlist is best-effort.
  }
}

export function isInWatchlist(id: string, store: WatchlistStore = window.localStorage): boolean {
  return readIds(store).includes(id);
}

export function addToWatchlist(id: string, store: WatchlistStore = window.localStorage): void {
  const ids = readIds(store);
  if (!ids.includes(id)) writeIds(store, [...ids, id]);
}

export function removeFromWatchlist(id: string, store: WatchlistStore = window.localStorage): void {
  const ids = readIds(store);
  if (ids.includes(id)) writeIds(store, ids.filter((existing) => existing !== id));
}

/** Toggles membership and reports whether the id is now on the watchlist. */
export function toggleWatchlist(id: string, store: WatchlistStore = window.localStorage): boolean {
  if (isInWatchlist(id, store)) {
    removeFromWatchlist(id, store);
    return false;
  }
  addToWatchlist(id, store);
  return true;
}
