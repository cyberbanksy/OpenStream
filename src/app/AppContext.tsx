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
import { inferPlatform, probeBrowserCapabilities } from "../domain/platform";
import type { MediaItem } from "../domain/media";
import type { ConnectionSummary } from "../integrations/connection";
import { listGatewayConnections } from "../integrations/gateway";
import type { PlaybackSession, ToastMessage, ViewId } from "./types";

type AppContextValue = {
  view: ViewId;
  previousView: ViewId;
  selectedItem: MediaItem;
  catalog: MediaItem[];
  capabilities: PlaybackCapabilities;
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
  const [view, setView] = useState<ViewId>(initial.view);
  const [previousView, setPreviousView] = useState<ViewId>("discover");
  const [selectedItem, setSelectedItem] = useState<MediaItem>(initial.item);
  const [capabilities, setCapabilities] = useState<PlaybackCapabilities>(
    platformCapabilityHints[inferPlatform()],
  );
  const [playback, setPlayback] = useState<PlaybackSession>();
  const [connections, setConnections] = useState<ConnectionSummary[]>(initialConnections);
  const [connectionDialogOpen, setConnectionDialogOpen] = useState(false);
  const [toast, setToast] = useState<ToastMessage>();
  const toastId = useRef(0);

  const updateUrl = useCallback((nextView: ViewId, item?: MediaItem) => {
    const url = new URL(window.location.href);
    url.searchParams.set("screen", nextView);
    if (item && (nextView === "details" || nextView === "player")) url.searchParams.set("item", item.id);
    else url.searchParams.delete("item");
    window.history.pushState({}, "", url);
  }, []);

  const navigate = useCallback((next: ViewId) => {
    setView((current) => {
      if (current !== next) setPreviousView(current);
      return next;
    });
    updateUrl(next);
  }, [updateUrl]);

  const openItem = useCallback((item: MediaItem) => {
    setSelectedItem(item);
    setView((current) => {
      setPreviousView(current);
      return "details";
    });
    updateUrl("details", item);
  }, [updateUrl]);

  const beginPlayback = useCallback((item = selectedItem) => {
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
  }, [capabilities, selectedItem, updateUrl, view]);

  const selectPlaybackRoute = useCallback((routeId: string) => {
    setPlayback((current) => current ? { ...current, selectedRouteId: routeId, status: "ready" } : current);
  }, []);

  const setPlaybackStatus = useCallback((status: PlaybackSession["status"]) => {
    setPlayback((current) => current ? { ...current, status } : current);
  }, []);

  const goBack = useCallback(() => {
    const target = previousView === "player" ? "details" : previousView;
    setView(target);
    updateUrl(target, target === "details" ? selectedItem : undefined);
  }, [previousView, selectedItem, updateUrl]);

  const notify = useCallback((message: Omit<ToastMessage, "id">) => {
    setToast({ ...message, id: ++toastId.current });
  }, []);

  const upsertConnection = useCallback((connection: ConnectionSummary) => {
    setConnections((current) => {
      const index = current.findIndex((item) => item.id === connection.id);
      if (index === -1) return [connection, ...current];
      return current.map((item, itemIndex) => itemIndex === index ? connection : item);
    });
  }, []);

  useEffect(() => {
    let cancelled = false;
    void probeBrowserCapabilities().then((result) => {
      if (!cancelled) setCapabilities(result);
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
          const managedIds = new Set(managedConnections.map((connection) => connection.id));
          return [...managedConnections, ...current.filter((connection) => !connection.managed || !managedIds.has(connection.id))];
        });
      } catch {
        // The UI remains usable when the optional loopback gateway is not running.
      }
    };
    void refresh();
    const interval = window.setInterval(refresh, 10_000);
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
    const labels: Record<ViewId, string> = {
      discover: "Discover",
      library: "Library",
      live: "Live",
      connections: "Connections",
      activity: "Activity",
      settings: "Playback capabilities",
      details: selectedItem.title,
      player: `Playing ${selectedItem.title}`,
      search: "Search",
    };
    document.title = `${labels[view]} · OpenStream`;
  }, [selectedItem.title, view]);

  useEffect(() => {
    if (!toast) return;
    const timeout = window.setTimeout(() => setToast(undefined), 4200);
    return () => window.clearTimeout(timeout);
  }, [toast]);

  const value = useMemo<AppContextValue>(() => ({
    view,
    previousView,
    selectedItem,
    catalog,
    capabilities,
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
    openConnectionDialog: () => setConnectionDialogOpen(true),
    closeConnectionDialog: () => setConnectionDialogOpen(false),
    upsertConnection,
    notify,
    dismissToast: () => setToast(undefined),
  }), [
    beginPlayback,
    capabilities,
    connectionDialogOpen,
    connections,
    goBack,
    navigate,
    notify,
    openItem,
    playback,
    previousView,
    selectPlaybackRoute,
    selectedItem,
    setPlaybackStatus,
    toast,
    upsertConnection,
    view,
  ]);

  return <AppContext.Provider value={value}>{children}</AppContext.Provider>;
}

export function useApp() {
  const context = useContext(AppContext);
  if (!context) throw new Error("useApp must be used within AppProvider");
  return context;
}
