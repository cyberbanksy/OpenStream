import {
  createContext,
  type PropsWithChildren,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import { catalog, catalogById } from "../data/catalog";
import { initialConnections } from "../data/connections";
import { platformCapabilityHints, rankCandidates, type PlaybackCapabilities } from "../domain/capabilities";
import {
  type ContentPolicy,
  filterCatalog,
  isItemAllowed,
  type KidsPolicy,
  standardPolicy,
} from "../domain/contentPolicy";
import { inferPlatform, probeBrowserCapabilities } from "../domain/platform";
import type { MediaItem } from "../domain/media";
import { connectionListsEqual, type ConnectionSummary, mergeGatewayConnections } from "../integrations/connection";
import { getTranscoderCapabilities, listGatewayConnections, type TranscoderCapabilities } from "../integrations/gateway";
import { completeStandardOnboarding, enableKidsMode, loadActivePolicy } from "./appProfile";
import { kidsHiddenViews, type PlaybackSession, type ToastMessage, type ViewId } from "./types";

type AppContextValue = {
  view: ViewId;
  previousView: ViewId;
  selectedItem?: MediaItem;
  catalog: MediaItem[];
  policy?: ContentPolicy;
  kidsMode: boolean;
  completeOnboarding: (mode: "standard" | "kids", kidsPolicy?: KidsPolicy) => void;
  capabilities: PlaybackCapabilities;
  transcoder?: TranscoderCapabilities;
  playback?: PlaybackSession;
  connections: ConnectionSummary[];
  connectionDialogOpen: boolean;
  toast?: ToastMessage;
  navigate: (view: ViewId) => void;
  openItem: (item: MediaItem) => void;
  beginPlayback: (item?: MediaItem) => void;
  selectPlaybackRoute: (routeId: string) => void;
  setPlaybackStatus: (status: PlaybackSession["status"]) => void;
  goBack: () => void;
  openConnectionDialog: () => void;
  closeConnectionDialog: () => void;
  upsertConnection: (connection: ConnectionSummary) => void;
  notify: (message: Omit<ToastMessage, "id">) => void;
  dismissToast: () => void;
};

const AppContext = createContext<AppContextValue | undefined>(undefined);

/** How often the loopback gateway is polled for connection changes. */
const CONNECTION_POLL_INTERVAL_MS = 10_000;
/** How long a toast stays visible before it dismisses itself. */
const TOAST_DISMISS_MS = 4200;

function readRoute() {
  const params = new URLSearchParams(window.location.search);
  const route = params.get("screen") as ViewId | null;
  const allowed: ViewId[] = [
    "discover",
    "library",
    "live",
    "connections",
    "activity",
    "settings",
    "details",
    "player",
    "search",
  ];
  const itemId = params.get("item");
  return {
    view: route && allowed.includes(route) ? route : "discover",
    item: (itemId && catalogById.get(itemId)) || catalog[0],
  };
}

export function AppProvider({ children }: PropsWithChildren) {
  const initial = useMemo(readRoute, []);
  const [policy, setPolicy] = useState<ContentPolicy | undefined>(() => loadActivePolicy());
  const [view, setView] = useState<ViewId>(initial.view);
  const [previousView, setPreviousView] = useState<ViewId>("discover");
  const [selectedItem, setSelectedItem] = useState<MediaItem | undefined>(initial.item);
  const [capabilities, setCapabilities] = useState<PlaybackCapabilities>(
    platformCapabilityHints[inferPlatform()],
  );
  const [playback, setPlayback] = useState<PlaybackSession>();
  const [connections, setConnections] = useState<ConnectionSummary[]>(initialConnections);
  const [connectionDialogOpen, setConnectionDialogOpen] = useState(false);
  const [toast, setToast] = useState<ToastMessage>();
  const [transcoder, setTranscoder] = useState<TranscoderCapabilities>();
  const toastId = useRef(0);
  const kidsMode = policy?.mode === "kids";
  const visibleCatalog = useMemo(() => filterCatalog(catalog, policy ?? standardPolicy), [policy]);

  const updateUrl = useCallback((nextView: ViewId, item?: MediaItem, history: "push" | "replace" = "push") => {
    const url = new URL(window.location.href);
    url.searchParams.set("screen", nextView);
    if (item && (nextView === "details" || nextView === "player")) url.searchParams.set("item", item.id);
    else url.searchParams.delete("item");
    if (history === "replace") window.history.replaceState({}, "", url);
    else window.history.pushState({}, "", url);
  }, []);

  const notify = useCallback((message: Omit<ToastMessage, "id">) => {
    setToast({ ...message, id: ++toastId.current });
  }, []);

  const navigate = useCallback((next: ViewId) => {
    if (kidsMode && kidsHiddenViews.includes(next)) {
      notify({ tone: "neutral", title: "Not available in Kids Mode", detail: "Connections and activity stay hidden on this device." });
      return;
    }
    setView((current) => {
      if (current !== next) setPreviousView(current);
      return next;
    });
    updateUrl(next);
  }, [kidsMode, notify, updateUrl]);

  const openItem = useCallback((item: MediaItem) => {
    if (!isItemAllowed(item, policy ?? standardPolicy)) {
      notify({ tone: "warning", title: "Not available in Kids Mode", detail: `${item.title} is outside this device's allowed ratings.` });
      return;
    }
    setSelectedItem(item);
    setView((current) => {
      setPreviousView(current);
      return "details";
    });
    updateUrl("details", item);
  }, [notify, policy, updateUrl]);

  const beginPlayback = useCallback((item = selectedItem) => {
    if (!item) {
      notify({ tone: "neutral", title: "Nothing selected", detail: "Add a source, then choose a title to play." });
      return;
    }
    if (!isItemAllowed(item, policy ?? standardPolicy)) {
      notify({ tone: "warning", title: "Not available in Kids Mode", detail: `${item.title} is outside this device's allowed ratings.` });
      return;
    }
    const routes = rankCandidates(item.candidates, capabilities);
    const firstPlayable = routes.find((route) => route.playable);
    setSelectedItem(item);
    setPlayback({
      item,
      routes,
      selectedRouteId: firstPlayable?.candidate.id,
      capabilities,
      status: firstPlayable ? "choosing" : "blocked",
    });
    setPreviousView(view);
    setView("player");
    updateUrl("player", item);
  }, [capabilities, notify, policy, selectedItem, updateUrl, view]);

  const selectPlaybackRoute = useCallback((routeId: string) => {
    setPlayback((current) => current ? { ...current, selectedRouteId: routeId, status: "ready" } : current);
  }, []);

  const setPlaybackStatus = useCallback((status: PlaybackSession["status"]) => {
    setPlayback((current) => current ? { ...current, status } : current);
  }, []);

  const goBack = useCallback(() => {
    const target = previousView === "player" ? "details" : previousView;
    setView(target);
    // Replace instead of push so in-app Back does not grow the history stack
    // and make the browser Back button ping-pong.
    updateUrl(target, target === "details" ? selectedItem : undefined, "replace");
  }, [previousView, selectedItem, updateUrl]);

  const completeOnboarding = useCallback((mode: "standard" | "kids", kidsPolicy?: KidsPolicy) => {
    if (mode === "kids" && kidsPolicy) {
      setPolicy(enableKidsMode(kidsPolicy));
      notify({ tone: "success", title: "Kids Mode is on", detail: "Only age-appropriate titles will appear or play on this device." });
      return;
    }
    setPolicy(completeStandardOnboarding());
    notify({ tone: "success", title: "OpenStream is ready", detail: "The full library is available." });
  }, [notify]);

  const upsertConnection = useCallback((connection: ConnectionSummary) => {
    setConnections((current) => {
      const index = current.findIndex((item) => item.id === connection.id);
      if (index === -1) return [connection, ...current];
      return current.map((item, itemIndex) => itemIndex === index ? connection : item);
    });
  }, []);

  const openConnectionDialog = useCallback(() => setConnectionDialogOpen(true), []);
  const closeConnectionDialog = useCallback(() => setConnectionDialogOpen(false), []);
  const dismissToast = useCallback(() => setToast(undefined), []);

  useEffect(() => {
    let cancelled = false;
    void probeBrowserCapabilities().then((result) => {
      if (!cancelled) setCapabilities(result);
    });
    getTranscoderCapabilities()
      .then((result) => { if (!cancelled) setTranscoder(result); })
      .catch(() => {
        // Transcoding is an optional gateway capability; absence is fine.
      });
    return () => { cancelled = true; };
  }, []);

  useEffect(() => {
    let cancelled = false;
    const refresh = async () => {
      try {
        const managedConnections = await listGatewayConnections();
        if (cancelled) return;
        setConnections((current) => {
          const next = mergeGatewayConnections(current, managedConnections);
          // Keep the current reference when nothing changed so a poll tick
          // does not re-render the whole app.
          return connectionListsEqual(current, next) ? current : next;
        });
      } catch {
        // The UI remains usable when the optional loopback gateway is not running.
      }
    };
    void refresh();
    const interval = window.setInterval(refresh, CONNECTION_POLL_INTERVAL_MS);
    const handleVisibility = () => document.visibilityState === "visible" && void refresh();
    document.addEventListener("visibilitychange", handleVisibility);
    return () => {
      cancelled = true;
      window.clearInterval(interval);
      document.removeEventListener("visibilitychange", handleVisibility);
    };
  }, []);

  useEffect(() => {
    const handler = () => {
      const route = readRoute();
      setView(route.view);
      setSelectedItem(route.item);
    };
    window.addEventListener("popstate", handler);
    return () => window.removeEventListener("popstate", handler);
  }, []);

  useEffect(() => {
    if (!kidsMode) return;
    if (kidsHiddenViews.includes(view)) setView("discover");
    if (selectedItem && !isItemAllowed(selectedItem, policy ?? standardPolicy)) {
      const fallback = visibleCatalog[0];
      if (fallback) setSelectedItem(fallback);
      setPlayback((current) => current && !isItemAllowed(current.item, policy ?? standardPolicy) ? undefined : current);
      if (view === "details" || view === "player") setView("discover");
    }
  }, [kidsMode, policy, selectedItem, view, visibleCatalog]);

  useEffect(() => {
    const labels: Record<ViewId, string> = {
      discover: "Discover",
      library: "Library",
      live: "Live",
      connections: "Connections",
      activity: "Activity",
      settings: "Playback capabilities",
      details: selectedItem?.title ?? "Details",
      player: selectedItem ? `Playing ${selectedItem.title}` : "Player",
      search: "Search",
    };
    document.title = `${labels[view]} · OpenStream`;
  }, [selectedItem, view]);

  useEffect(() => {
    if (!toast) return;
    const timeout = window.setTimeout(() => setToast(undefined), TOAST_DISMISS_MS);
    return () => window.clearTimeout(timeout);
  }, [toast]);

  const value = useMemo<AppContextValue>(() => ({
    view,
    previousView,
    selectedItem,
    catalog: visibleCatalog,
    policy,
    kidsMode,
    completeOnboarding,
    capabilities,
    transcoder,
    playback,
    connections,
    connectionDialogOpen,
    toast,
    navigate,
    openItem,
    beginPlayback,
    selectPlaybackRoute,
    setPlaybackStatus,
    goBack,
    openConnectionDialog,
    closeConnectionDialog,
    upsertConnection,
    notify,
    dismissToast,
  }), [
    beginPlayback,
    capabilities,
    closeConnectionDialog,
    completeOnboarding,
    connectionDialogOpen,
    connections,
    dismissToast,
    goBack,
    kidsMode,
    navigate,
    notify,
    openConnectionDialog,
    openItem,
    playback,
    policy,
    previousView,
    selectPlaybackRoute,
    selectedItem,
    setPlaybackStatus,
    toast,
    transcoder,
    upsertConnection,
    view,
    visibleCatalog,
  ]);

  return <AppContext.Provider value={value}>{children}</AppContext.Provider>;
}

export function useApp() {
  const context = useContext(AppContext);
  if (!context) throw new Error("useApp must be used within AppProvider");
  return context;
}
