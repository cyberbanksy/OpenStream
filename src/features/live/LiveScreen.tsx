import { SignalHigh } from "lucide-react";
import { useMemo } from "react";
import { useApp } from "../../app/AppContext";
import { Artwork } from "../../components/Artwork";

export function LiveScreen() {
  const { catalog, navigate, beginPlayback } = useApp();
  // Live channels come from the same catalog the rest of the app reads (the
  // pattern DiscoverScreen uses); nothing is hardcoded. When configured
  // providers supply no live channels, we show an honest empty state and the
  // online count is derived from the real list rather than a fixed badge.
  const channels = useMemo(() => catalog.filter((item) => item.kind === "live"), [catalog]);

  return (
    <main className="screen workspace-screen live-screen">
      <section className="workspace-content full-width">
        <header className="section-header">
          <div><h1>Live</h1><p>Channels from your configured providers</p></div>
          <span className="live-state"><SignalHigh size={17} />{channels.length > 0 ? `${channels.length} online` : "No source"}</span>
        </header>
        {channels.length === 0 ? (
          <div className="cinema-empty">
            <h2>No live channels from your configured providers</h2>
            <p>Add an IPTV account, playlist, or individual channel from Sources.</p>
            <button className="action-button primary" data-focusable onClick={() => navigate("connections")}>Open Sources</button>
          </div>
        ) : (
          <div className="live-channel-grid">
            {channels.map((channel) => (
              <button
                key={channel.id}
                data-focusable
                className="live-channel-card"
                aria-label={`Watch ${channel.title}`}
                onClick={() => channel.candidates.length > 0 && beginPlayback(channel)}
              >
                <span className="live-channel-art"><Artwork cell={channel.artworkCell} className="live-channel-sprite" /></span>
                <span className="live-channel-copy">
                  <strong>{channel.title}</strong>
                  {/* Neutral placeholder, not a fabricated guide time. */}
                  <small>{channel.subtitle ?? "Live now"}</small>
                </span>
              </button>
            ))}
          </div>
        )}
      </section>
    </main>
  );
}
