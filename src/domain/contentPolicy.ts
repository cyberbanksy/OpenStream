import type { MediaItem } from "./media";
import { isRatingAllowed, kidsRatingCap, type KidsRatingCapId } from "./ratings";

export type StandardPolicy = { mode: "standard" };

export type KidsPolicy = {
  mode: "kids";
  capId: KidsRatingCapId;
  maxAge: number;
  /** Lowercased words or titles the guardian chose to block outright. */
  blockedTerms: string[];
  createdAt: string;
};

export type ContentPolicy = StandardPolicy | KidsPolicy;

export const standardPolicy: StandardPolicy = { mode: "standard" };

/** The fail-closed policy used when a kids-locked profile cannot be trusted. */
export const strictestKidsPolicy: KidsPolicy = {
  mode: "kids",
  capId: "little-kids",
  maxAge: kidsRatingCap("little-kids").maxAge,
  blockedTerms: [],
  createdAt: "unknown",
};

export function createKidsPolicy(capId: KidsRatingCapId, blockedTerms: string[]): KidsPolicy {
  return {
    mode: "kids",
    capId,
    maxAge: kidsRatingCap(capId).maxAge,
    blockedTerms: [...new Set(blockedTerms.map((term) => term.trim().toLowerCase()).filter(Boolean))].slice(0, 100),
    createdAt: new Date().toISOString(),
  };
}

function matchesBlockedTerm(item: MediaItem, blockedTerms: string[]): boolean {
  if (blockedTerms.length === 0) return false;
  const haystack = `${item.title} ${item.subtitle ?? ""} ${item.synopsis}`.toLowerCase();
  return blockedTerms.some((term) => haystack.includes(term));
}

/**
 * Whether a catalog item may be shown or played under the active policy.
 * Unrated and unrecognized certifications are blocked in Kids Mode.
 */
export function isItemAllowed(item: MediaItem, policy: ContentPolicy): boolean {
  if (policy.mode === "standard") return true;
  if (!isRatingAllowed(item.rating, policy.maxAge)) return false;
  return !matchesBlockedTerm(item, policy.blockedTerms);
}

export function filterCatalog(items: MediaItem[], policy: ContentPolicy): MediaItem[] {
  if (policy.mode === "standard") return items;
  return items.filter((item) => isItemAllowed(item, policy));
}
