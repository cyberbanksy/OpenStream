import type { CandidateEvaluation, PlaybackCapabilities } from "../domain/capabilities";
import type { MediaItem } from "../domain/media";

export type ViewId =
  | "discover"
  | "library"
  | "live"
  | "connections"
  | "activity"
  | "settings"
  | "details"
  | "player"
  | "search";

/**
 * Views a kids policy hides — the single source of truth for navigation
 * guards, nav items, and rail items.
 */
export const kidsHiddenViews: readonly ViewId[] = ["connections", "activity"];

export type ToastMessage = {
  id: number;
  tone: "success" | "warning" | "neutral";
  title: string;
  detail: string;
};

export type PlaybackSession = {
  item: MediaItem;
  routes: CandidateEvaluation[];
  selectedRouteId?: string;
  capabilities: PlaybackCapabilities;
  status: "choosing" | "ready" | "playing" | "blocked";
};
