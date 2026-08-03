import {
  Bookmark,
  Clock3,
  Compass,
  HardDrive,
  Play,
  Search,
  Settings,
} from "lucide-react";
import { useMemo, useState } from "react";
import { useApp } from "../../app/AppContext";
import northstarCard from "../../assets/catalog/art-00.png";
import lastSignalCard from "../../assets/catalog/art-01.png";
import solarDriftCard from "../../assets/catalog/art-02.png";
import echoesOfMarsCard from "../../assets/catalog/art-03.png";
import frontierOutpostCard from "../../assets/catalog/art-04.png";
import redlineCard from "../../assets/catalog/art-05.png";
import lastSignalCityHero from "../../assets/last-signal-city-hero.png";
import lastSignalCityBackdrop from "../../assets/last-signal-city-hero-v2.png";
import northstarHero from "../../assets/northstar-hero.png";
import profileAvatar from "../../assets/profile-avatar.png";
import { Artwork } from "../../components/Artwork";
import { Brand } from "../../components/Brand";
import {
  dynamicRangeLabel,
  formatResolution,
  type MediaCandidate,
  type MediaItem,
} from "../../domain/media";

const featuredIds = [
  "northstar",
  "last-signal",
  "solar-drift",
  "echoes-of-mars",
  "frontier-outpost",
  "redline",
];

const focusArtwork: Record<string, string> = {
  "northstar": northstarCard,
  "last-signal": lastSignalCard,
  "solar-drift": solarDriftCard,
  "echoes-of-mars": echoesOfMarsCard,
  "frontier-outpost": frontierOutpostCard,
  "redline": redlineCard,
};

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
  if (item.id === "last-signal") {
    return <div className="cinema-hero-art cinema-hero-signal" style={{ backgroundImage: `url(${lastSignalCityBackdrop})` }} />;
  }
  if (item.id === "northstar") {
    return <div className="cinema-hero-art" style={{ backgroundImage: `url(${northstarHero})` }} />;
  }
  if (!focusArtwork[item.id]) {
    return <div key={item.id} className="cinema-hero-art cinema-hero-card"><Artwork cell={item.artworkCell} className="cinema-hero-sprite" /></div>;
  }
  return <div key={item.id} className="cinema-hero-art cinema-hero-card" style={{ backgroundImage: `url(${focusArtwork[item.id]})` }} />;
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
      {active && item.id === "last-signal" ? (
        <span className="focus-card-art generated" style={{ backgroundImage: `url(${lastSignalCityHero})` }} />
      ) : focusArtwork[item.id] ? (
        <img className="focus-card-art" src={focusArtwork[item.id]} alt="" />
      ) : (
        <span className="focus-card-art"><Artwork cell={item.artworkCell} className="focus-card-sprite" /></span>
      )}
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
  const featured = useMemo(() => {
    const preferred = featuredIds
      .map((id) => catalog.find((item) => item.id === id))
      .filter((item): item is MediaItem => Boolean(item));
    const fillers = catalog.filter((item) => !preferred.includes(item));
    return [...preferred, ...fillers].slice(0, 6);
  }, [catalog]);
  const initialIndex = Math.max(0, featured.findIndex((item) => item.id === "last-signal"));
  const [activeIndex, setActiveIndex] = useState(initialIndex);
  const selected = featured[Math.min(activeIndex, featured.length - 1)] ?? featured[0];

  if (!selected) {
    return (
      <main className="screen discover-screen cinema-discover">
        <div className="cinema-hero-treatment" />
        <section className="cinema-empty">
          <Brand />
          <h1>Nothing to show yet</h1>
          <p>No titles match this device's content settings. Add age-appropriate media to a connected library to fill Discover.</p>
        </section>
      </main>
    );
  }

  const candidate = selected.candidates[0];
  const audio = spatialAudioLabel(candidate);
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
          <span>{formatResolution(candidate.format)}</span>
          <span>{dynamicRangeLabel[candidate.format.dynamicRange]}</span>
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
              onPlay={beginPlayback}
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

      <div className="cinema-route" aria-label="Selected playback route">
        <span className="route-health" />
        <span>Playing from {candidate.sourceName}</span>
        <b />
        <CandidateSummary candidate={candidate} />
      </div>

      <nav className="cinema-context-nav" aria-label="OpenStream destinations">
        <button data-focusable onClick={() => navigate("library")}>
          <span className="context-icon"><Clock3 size={24} /></span>
          <span><strong>Continue Watching</strong><small>{featured.length} items</small></span>
        </button>
        {kidsMode ? null : (
          <button data-focusable onClick={() => navigate("connections")}>
            <span className="context-icon"><HardDrive size={24} /></span>
            <span><strong>From Atlas NAS</strong><small>1.2 TB available</small></span>
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
          aria-label="Add to watchlist"
          title="Add to watchlist"
          onClick={() => notify({ tone: "success", title: "Added to watchlist", detail: selected.title })}
        >
          <Bookmark size={24} />
        </button>
        <button data-focusable aria-label="Settings" title="Settings" onClick={() => navigate("settings")}><Settings size={25} /></button>
        <button className="profile-control" data-focusable aria-label="Profile and activity" title="Profile" onClick={() => navigate("activity")}>
          <img src={profileAvatar} alt="" />
        </button>
      </nav>
    </main>
  );
}
