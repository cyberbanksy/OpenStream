export type RatingSystem = "mpaa" | "tv" | "unknown";

export type NormalizedRating = {
  system: RatingSystem;
  label: string;
  /** Minimum viewer age the certification implies. */
  age: number;
};

export type KidsRatingCapId = "little-kids" | "big-kids" | "teens";

export type KidsRatingCap = {
  id: KidsRatingCapId;
  label: string;
  description: string;
  /** Highest normalized age this cap allows. */
  maxAge: number;
  examples: string;
};

export const kidsRatingCaps: KidsRatingCap[] = [
  {
    id: "little-kids",
    label: "Little kids",
    description: "Only all-ages titles.",
    maxAge: 6,
    examples: "G · TV-Y · TV-G",
  },
  {
    id: "big-kids",
    label: "Big kids",
    description: "All-ages titles plus parental-guidance titles.",
    maxAge: 9,
    examples: "Up to PG · TV-Y7 · TV-PG",
  },
  {
    id: "teens",
    label: "Teens",
    description: "Everything below restricted certifications.",
    maxAge: 14,
    examples: "Up to PG-13 · TV-14",
  },
];

export function kidsRatingCap(id: KidsRatingCapId): KidsRatingCap {
  return kidsRatingCaps.find((cap) => cap.id === id) ?? kidsRatingCaps[0];
}

const mpaaAges: Record<string, number> = {
  "G": 0,
  "PG": 8,
  "PG-13": 13,
  "PG13": 13,
  "PG-14": 14,
  "R": 17,
  "NC-17": 18,
  "NC17": 18,
  "X": 18,
};

const tvAges: Record<string, number> = {
  "TV-Y": 0,
  "TV-Y7": 7,
  "TV-Y7-FV": 7,
  "TV-G": 0,
  "TV-PG": 8,
  "TV-14": 14,
  "TV-MA": 17,
};

/**
 * Normalizes a certification label to a comparable minimum age.
 * Returns undefined for unrated or unrecognized labels; in Kids Mode the
 * caller must treat unknown certifications as blocked.
 */
export function normalizeRating(value: string | undefined | null): NormalizedRating | undefined {
  if (!value) return undefined;
  const label = value.trim().toUpperCase().replace(/\s+/g, "-");
  if (label in mpaaAges) return { system: "mpaa", label, age: mpaaAges[label] };
  if (label in tvAges) return { system: "tv", label, age: tvAges[label] };
  return undefined;
}

export function isRatingAllowed(value: string | undefined | null, maxAge: number): boolean {
  const normalized = normalizeRating(value);
  return normalized !== undefined && normalized.age <= maxAge;
}
