import { Activity, Baby, FolderOpen, Home, Radio, Search, Settings, UserCircle } from "lucide-react";
import { type CSSProperties, useEffect, useState } from "react";
import { useApp } from "./AppContext";
import type { ViewId } from "./types";
import { useSpatialNavigation } from "./useSpatialNavigation";
import { Brand } from "../components/Brand";
import { Toast } from "../components/Toast";
import { ActivityScreen } from "../features/activity/ActivityScreen";
import { ConnectionDialog } from "../features/connections/ConnectionDialog";
import { ConnectionsScreen } from "../features/connections/ConnectionsScreen";
import { DiscoverScreen } from "../features/discover/DiscoverScreen";
import { DetailsScreen } from "../features/library/DetailsScreen";
import { LibraryScreen } from "../features/library/LibraryScreen";
import { LiveScreen } from "../features/live/LiveScreen";
import { OnboardingScreen } from "../features/onboarding/OnboardingScreen";
import { PlayerScreen } from "../features/player/PlayerScreen";
import { SearchScreen } from "../features/search/SearchScreen";
import { SettingsScreen } from "../features/settings/SettingsScreen";

const nav: Array<{ id: ViewId; label: string; kidsHidden?: boolean }> = [
  { id: "discover", label: "Discover" },
  { id: "library", label: "Library" },
  { id: "live", label: "Live" },
  { id: "connections", label: "Connections", kidsHidden: true },
];

const rail: Array<{ id: ViewId; label: string; icon: typeof Home; kidsHidden?: boolean }> = [
  { id: "discover", label: "Discover", icon: Home },
  { id: "live", label: "Live", icon: Radio },
  { id: "library", label: "Library", icon: FolderOpen },
  { id: "activity", label: "Activity", icon: Activity, kidsHidden: true },
  { id: "settings", label: "Settings", icon: Settings },
];

function currentPrimaryView(view: ViewId) {
  if (view === "details" || view === "player") return "discover";
  return view;
}

export function AppShell() {
  const { view, navigate, connectionDialogOpen, policy, kidsMode } = useApp();
  const [scale, setScale] = useState(() => Math.min(window.innerWidth / 1920, window.innerHeight / 1080, 1));
  useSpatialNavigation();
  const onboarding = policy === undefined;
  const fullScreen = view === "player" || view === "search";
  const cinematicHome = view === "discover";
  const primaryView = currentPrimaryView(view);
  const navItems = nav.filter((item) => !kidsMode || !item.kidsHidden);
  const railItems = rail.filter((item) => !kidsMode || !item.kidsHidden);

  useEffect(() => {
    const focusTarget =
      document.querySelector<HTMLElement>(".screen [data-focusable][data-autofocus]") ??
      document.querySelector<HTMLElement>(".screen [data-focusable]") ??
      document.querySelector<HTMLElement>("[data-focusable]");
    window.requestAnimationFrame(() => focusTarget?.focus({ preventScroll: true }));
  }, [view]);

  useEffect(() => {
    const updateScale = () => setScale(Math.min(window.innerWidth / 1920, window.innerHeight / 1080, 1));
    window.addEventListener("resize", updateScale);
    return () => window.removeEventListener("resize", updateScale);
  }, []);

  let screen = <DiscoverScreen />;
  if (view === "library") screen = <LibraryScreen />;
  else if (view === "live") screen = <LiveScreen />;
  else if (view === "connections") screen = <ConnectionsScreen />;
  else if (view === "activity") screen = <ActivityScreen />;
  else if (view === "settings") screen = <SettingsScreen />;
  else if (view === "details") screen = <DetailsScreen />;
  else if (view === "player") screen = <PlayerScreen />;
  else if (view === "search") screen = <SearchScreen />;

  if (onboarding) {
    return (
      <div className="viewport-shell">
        <div className="app-shell" style={{ "--stage-scale": scale } as CSSProperties}>
          <OnboardingScreen />
          <Toast />
        </div>
      </div>
    );
  }

  return (
    <div className="viewport-shell">
      <div className="app-shell" style={{ "--stage-scale": scale } as CSSProperties}>
        {fullScreen || cinematicHome ? null : (
          <>
            <header className="topbar"><button className="brand-button" data-focusable aria-label="OpenStream home" onClick={() => navigate("discover")}><Brand /></button><nav aria-label="Primary navigation">{navItems.map((item) => <button key={item.id} data-focusable className={primaryView === item.id ? "topnav-button selected" : "topnav-button"} onClick={() => navigate(item.id)}>{item.label}</button>)}</nav><div className="top-actions">{kidsMode ? <span className="kids-badge" title="Kids Mode is permanently on"><Baby size={17} />Kids</span> : null}<button data-focusable aria-label="Search" onClick={() => navigate("search")}><Search size={23} /></button><button data-focusable aria-label="Profile"><UserCircle size={27} /></button></div></header>
            <aside className="quick-rail" aria-label="Quick navigation">{railItems.map(({ id, label, icon: Icon }) => <button key={id} data-focusable className={primaryView === id ? "selected" : ""} aria-label={label} title={label} onClick={() => navigate(id)}><Icon size={21} /></button>)}</aside>
          </>
        )}
        {screen}
        {connectionDialogOpen && !kidsMode ? <ConnectionDialog /> : null}
        <Toast />
      </div>
    </div>
  );
}
