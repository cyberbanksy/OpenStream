import { lazy, Suspense } from "react";
import { AppProvider } from "./app/AppContext";
import { AppShell } from "./app/AppShell";
import { Brand } from "./components/Brand";

const XrViewer = lazy(() => import("./features/xr/XrViewer").then((module) => ({ default: module.XrViewer })));

export default function App() {
  if (window.location.pathname === "/xr/viewer") {
    return <Suspense fallback={<main className="xr-loading" aria-label="Loading immersive viewer"><Brand /></main>}><XrViewer /></Suspense>;
  }
  return <AppProvider><AppShell /></AppProvider>;
}
