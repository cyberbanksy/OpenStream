import {
  Bookmark,
  CircleUserRound,
  Clock3,
  Compass,
  Database,
  Play,
  Search,
  Settings,
} from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { useApp } from "../../app/AppContext";
import { Artwork } from "../../components/Artwork";
import { Brand } from "../../components/Brand";
import {
  dynamicRangeLabel,
  formatResolution,
  type MediaCandidate,
  type MediaItem,
} from "../../domain/media";
import { isInWatchlist, toggleWatchlist } from "./watchlist";

function formatRuntime(minutes: number) {
  const hours = Math.floor(minutes / 60);
  const remainder = minutes % 60;
  if (hours === 0) return `${remainder}m`;
  return `${hours}h ${String(remainder).padStart(2, "0")}m`;
}

function dynamicRangeShort(candidate: MediaCandidate) {
  return candidate.format.dynamicRange === "dolby-vision"
    ? "DV"
    : dynamicRangeLabel[candidate.format.dynamicRange];
}

function spatialAudioLabel(candidate: MediaCandidate) {
  if (candidate.format.spatialAudio === "atmos") return "Atmos";
  if (candidate.format.spatialAudio === "apac") return "APAC";
  if (candidate.format.spatialAudio === "ambisonic") return "Ambisonic";
  return undefined;
}

function CandidateSummary({ candidate }: { candidate: MediaCandidate }) {
  const audio = spatialAudioLabel(candidate);
  return (
    <>
      <span>{candidate.directPlay ? "Direct Play" : "Transcode"}</span>
      <i>·</i>
      <span>{formatResolution(candidate.format)} {dynamicRangeShort(candidate)}</span>
      {audio ? <><i>·</i><span>{audio}</span></> : null}
    </>
  );
}

function HeroArtwork({ item }: { item: MediaItem }) {
  return <div key={item.id} className="cinema-hero-art cinema-hero-card"><Artwork cell={item.artworkCell} className="cinema-hero-sprite" /></div>;
}

function FocusCard({
  item,
  index,
  activeIndex,
  onActivate,
  onPlay,
}: {
  item: MediaItem;
  index: number;
  activeIndex: number;
  onActivate: (index: number) => void;
  onPlay: (item: MediaItem) => void;
}) {
  const active = index === activeIndex;
  const previous = index === activeIndex - 1;
  const remaining = item.remaining?.replace(" left", " remaining") ?? formatRuntime(item.runtimeMinutes);
  const hasLeadingArticle = item.title.toLowerCase().startsWith("the ");

  return (
    <button
      className={`focus-card${active ? " active" : ""}${previous ? " previous" : ""}`}
      data-focusable
      data-autofocus={active ? true : undefined}
      autoFocus={active}
      aria-label={active ? `Play ${item.title}` : `Select ${item.title}`}
      onClick={() => active ? onPlay(item) : onActivate(index)}
      onFocus={() => onActivate(index)}
      onMouseEnter={() => onActivate(index)}
    >
      <span className="focus-card-art"><Artwork cell={item.artworkCell} className="focus-card-sprite" /></span>
      <span className="focus-card-shade" />
      {active ? (
        <span className="focus-card-feature">
          <small>{hasLeadingArticle ? "THE" : item.kind === "series" ? "SERIES" : "FEATURE"}</small>
          <strong>{hasLeadingArticle ? item.title.slice(4) : item.title}</strong>
          <span>{item.subtitle ?? remaining}</span>
          <span className="focus-progress" aria-label={`${item.progress ?? 0}% watched`}>
            <i style={{ width: `${item.progress ?? 0}%` }} />
          </span>
          <span className="focus-play"><Play size={24} fill="currentColor" />Play</span>
        </span>
      ) : (
        <span className="focus-card-label">
          <strong>{item.title}</strong>
          <small>{item.subtitle ?? item.remaining ?? `${item.year}`}</small>
        </span>
      )}
    </button>
  );
}

export function DiscoverScreen() {
  const { catalog, beginPlayback, navigate, notify, kidsMode } = useApp();
  const featured = useMemo(() => catalog.slice(0, 6), [catalog]);
  const [activeIndex, setActiveIndex] = useState(0);
  const selected = featured[Math.min(activeIndex, featured.length - 1)] ?? featured[0];
  const selectedId = selected?.id;
  const [watchlisted, setWatchlisted] = useState(false);

  useEffect(() => {
    setWatchlisted(selectedId ? isInWatchlist(selectedId) : false);
  }, [selectedId]);

  if (!selected) {
    return (
      <main className="screen discover-screen cinema-discover">
        <div className="cinema-hero-treatment" />
        <section className="cinema-empty">
          <Brand />
          <h1>Nothing to show yet</h1>
          <p>Add a library, add-on, or YouTube channel in Sources to fill Discover.</p>
          <button className="action-button primary" data-focusable onClick={() => navigate("connections")}>Open Sources</button>
        </section>
      </main>
    );
  }

  const candidate = selected.candidates[0];
  const audio = candidate ? spatialAudioLabel(candidate) : undefined;
  const hasLeadingArticle = selected.title.toLowerCase().startsWith("the ");

  return (
    <main className="screen discover-screen cinema-discover">
      <HeroArtwork item={selected} />
      <div className="cinema-hero-treatment" />

      <button
        className="brand-button discover-brand"
        data-focusable
        aria-label="OpenStream home"
        onClick={() => navigate("discover")}
      >
        <Brand />
      </button>
      {kidsMode ? <span className="kids-badge discover-kids" title="Kids Mode is permanently on">Kids</span> : null}

      <section className="cinema-title" aria-live="polite" aria-labelledby="cinema-featured-title">
        <span className="cinema-kicker">{hasLeadingArticle ? "THE" : selected.kind === "series" ? "SERIES" : "FEATURE"}</span>
        <h1 id="cinema-featured-title">{hasLeadingArticle ? selected.title.slice(4) : selected.title}</h1>
        <span className="cinema-accent" />
        <div className="cinema-meta">
          <span>{selected.year}</span>
          <span>{selected.rating}</span>
          <span>{formatRuntime(selected.runtimeMinutes)}</span>
          {candidate ? <span>{formatResolution(candidate.format)}</span> : null}
          {candidate ? <span>{dynamicRangeLabel[candidate.format.dynamicRange]}</span> : null}
          {audio ? <span>{audio}</span> : null}
        </div>
      </section>

      <section className="focus-ribbon" aria-label="Continue watching">
        <div className="focus-track">
          {featured.map((item, index) => (
            <FocusCard
              key={item.id}
              item={item}
              index={index}
              activeIndex={activeIndex}
              onActivate={setActiveIndex}
              onPlay={(item) => item.candidates.length > 0 && beginPlayback(item)}
            />
          ))}
        </div>
        <div className="ribbon-pagination" aria-label="Featured title selection">
          {featured.map((item, index) => (
            <button
              key={item.id}
              className={index === activeIndex ? "selected" : ""}
              data-focusable
              aria-label={`Show ${item.title}`}
              aria-current={index === activeIndex ? "true" : undefined}
              onClick={() => setActiveIndex(index)}
            />
          ))}
        </div>
      </section>

      {candidate ? <div className="cinema-route" aria-label="Selected playback route">
        <span className="route-health" />
        <span>Playing from {candidate.sourceName}</span>
        <b />
        <CandidateSummary candidate={candidate} />
      </div> : null}

      <nav className="cinema-context-nav" aria-label="OpenStream destinations">
        <button data-focusable onClick={() => navigate("library")}>
          <span className="context-icon"><Clock3 size={24} /></span>
          <span><strong>Continue Watching</strong><small>{featured.length} items</small></span>
        </button>
        {kidsMode ? null : (
          <button data-focusable onClick={() => navigate("connections")}>
            <span className="context-icon"><Database size={24} /></span>
            <span><strong>Your Sources</strong><small>{catalog.length} available titles</small></span>
          </button>
        )}
        <button data-focusable onClick={() => navigate("discover")}>
          <span className="context-icon"><Compass size={24} /></span>
          <span><strong>Discover</strong><small>Movies, shows &amp; more</small></span>
        </button>
      </nav>

      <nav className="cinema-control-dock" aria-label="Quick controls">
        <button data-focusable aria-label="Search" title="Search" onClick={() => navigate("search")}><Search size={25} /></button>
        <button
          data-focusable
          aria-label={watchlisted ? "Remove from watchlist" : "Add to watchlist"}
          title={watchlisted ? "Remove from watchlist" : "Add to watchlist"}
          aria-pressed={watchlisted}
          onClick={() => {
            const added = toggleWatchlist(selected.id);
            setWatchlisted(added);
            notify({
              tone: added ? "success" : "neutral",
              title: added ? "Added to watchlist" : "Removed from watchlist",
              detail: selected.title,
            });
          }}
        >
          <Bookmark size={24} fill={watchlisted ? "currentColor" : "none"} />
        </button>
        <button data-focusable aria-label="Settings" title="Settings" onClick={() => navigate("settings")}><Settings size={25} /></button>
        <button className="profile-control" data-focusable aria-label="Profile and activity" title="Profile" onClick={() => navigate("activity")}>
          <CircleUserRound size={26} />
        </button>
      </nav>
    </main>
  );
}
