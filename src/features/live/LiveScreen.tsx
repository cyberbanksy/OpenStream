import { SignalHigh } from "lucide-react";
import { useApp } from "../../app/AppContext";

export function LiveScreen() {
  const { navigate } = useApp();
  return (
    <main className="screen workspace-screen live-screen">
      <section className="workspace-content full-width">
        <header className="section-header">
          <div><h1>Live</h1><p>Channels from your configured providers</p></div>
          <span className="live-state"><SignalHigh size={17} />No source</span>
        </header>
        <div className="cinema-empty">
          <h2>No Live TV source configured</h2>
          <p>Add an IPTV account, playlist, or individual channel from Sources.</p>
          <button className="action-button primary" data-focusable onClick={() => navigate("connections")}>Open Sources</button>
        </div>
      </section>
    </main>
  );
}
