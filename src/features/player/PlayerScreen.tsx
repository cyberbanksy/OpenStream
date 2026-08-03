import { ArrowLeft, CheckCircle2, CircleHelp, Expand, Glasses, Pause, Play, RotateCcw, ShieldAlert, Volume2 } from "lucide-react";
import { useMemo, useRef, useState } from "react";
import { useApp } from "../../app/AppContext";
import northstarHero from "../../assets/northstar-hero.png";
import signalHero from "../../assets/last-signal-hero.png";
import { FormatBadges } from "../../components/FormatBadge";
import { dynamicRangeLabel } from "../../domain/media";
import { createXrViewerHandoff } from "../../integrations/gateway";

export function PlayerScreen() {
  const { playback, goBack, selectPlaybackRoute, setPlaybackStatus, notify } = useApp();
  const videoRef = useRef<HTMLVideoElement>(null);
  const [playing, setPlaying] = useState(false);
  const [openingImmersive, setOpeningImmersive] = useState(false);
  const selected = useMemo(() => playback?.routes.find((route) => route.candidate.id === playback.selectedRouteId), [playback]);
  const poster = playback?.item.heroAsset === "signal" ? signalHero : northstarHero;

  if (!playback) return <main className="screen player-screen"><button className="back-button" onClick={goBack}><ArrowLeft />Back</button><p>No playback session is active.</p></main>;

  const togglePlay = async () => {
    const video = videoRef.current;
    if (!video) return;
    if (video.paused) {
      try {
        await video.play();
        setPlaying(true);
        setPlaybackStatus("playing");
      } catch {
        notify({ tone: "warning", title: "Playback could not start", detail: "The browser rejected this route. Choose another source or use a native adapter." });
      }
    } else {
      video.pause();
      setPlaying(false);
      setPlaybackStatus("ready");
    }
  };

  const openImmersiveViewer = async () => {
    if (!selected || selected.candidate.format.projection === "flat") return;
    const projectionName = selected.candidate.format.projection;
    const projection = projectionName.includes("360") ? "360" : projectionName === "wide-fov" ? "wide" : "180";
    const stereo = projectionName.startsWith("stereo") || projectionName === "spatial" || projectionName === "apple-immersive"
      ? "sbs"
      : "mono";
    setOpeningImmersive(true);
    try {
      const viewerUrl = await createXrViewerHandoff({
        source: videoRef.current?.currentSrc || "/demo/openstream-demo.mp4",
        projection,
        stereo,
        dynamicRange: dynamicRangeLabel[selected.candidate.format.dynamicRange],
      });
      window.location.assign(viewerUrl);
    } catch (error) {
      setOpeningImmersive(false);
      notify({
        tone: "warning",
        title: "Immersive viewer could not open",
        detail: error instanceof Error ? error.message : "The secure media handoff failed.",
      });
    }
  };

  return (
    <main className="screen player-screen">
      <video ref={videoRef} className="player-video" poster={poster} preload="metadata" onEnded={() => setPlaying(false)}>
        <source src="/demo/openstream-demo.mp4" type="video/mp4" />
      </video>
      <div className="player-scrim" />
      <header className="player-header"><button className="back-button" data-focusable onClick={goBack}><ArrowLeft size={21} />Back</button><span><strong>{playback.item.title}</strong><small>{selected?.candidate.sourceName ?? "Choose a route"}</small></span><FormatBadges format={(selected ?? playback.routes[0]).candidate.format} detailed /></header>
      {playback.status === "choosing" || playback.status === "blocked" ? (
        <section className="route-picker" aria-labelledby="route-picker-title"><header><span><h1 id="route-picker-title">Choose a stream route</h1><p>OpenStream checks the current device before selecting a format.</p></span>{playback.status === "blocked" ? <ShieldAlert size={28} /> : <CircleHelp size={28} />}</header><div className="route-list">{playback.routes.map((route, index) => <button key={route.candidate.id} data-focusable data-autofocus={index === 0 ? true : undefined} autoFocus={index === 0} className={!route.playable ? "playback-route blocked" : route.candidate.id === playback.selectedRouteId ? "playback-route selected" : "playback-route"} disabled={!route.playable} onClick={() => { selectPlaybackRoute(route.candidate.id); setPlaybackStatus("ready"); }}><span className="route-rank">{index + 1}</span><span><strong>{route.candidate.sourceName}</strong><small>{route.candidate.directPlay ? "Direct play" : "Transcode"} · {route.candidate.bitrateMbps} Mbps</small></span><FormatBadges format={route.candidate.format} /><span className="route-check">{route.playable ? route.requiresProbe ? <CircleHelp size={18} /> : <CheckCircle2 size={18} /> : <ShieldAlert size={18} />}<small>{route.playable ? route.requiresProbe ? "Probe" : "Ready" : "Blocked"}</small></span></button>)}</div><footer><span>Availability is not a decoder guarantee.</span><button className="action-button primary" data-focusable disabled={!selected?.playable} onClick={() => setPlaybackStatus("ready")}><Play size={20} fill="currentColor" />Use selected route</button></footer></section>
      ) : (
        <section className="player-controls"><div className="timeline"><span><i style={{ width: "38%" }} /></span><small>48:12</small><small>2:07:00</small></div><div className="transport"><button data-focusable aria-label="Restart" onClick={() => { if (videoRef.current) videoRef.current.currentTime = 0; }}><RotateCcw size={23} /></button><button className="play-toggle" data-focusable data-autofocus autoFocus aria-label={playing ? "Pause" : "Play"} onClick={togglePlay}>{playing ? <Pause size={28} fill="currentColor" /> : <Play size={28} fill="currentColor" />}</button><button data-focusable aria-label="Volume"><Volume2 size={23} /></button></div><div className="player-modes"><button data-focusable onClick={openImmersiveViewer} disabled={openingImmersive || selected?.candidate.format.projection === "flat"}><Glasses size={20} />{openingImmersive ? "Opening" : "Immersive"}</button><button data-focusable><Expand size={20} />Fit</button></div><div className="now-route"><strong>{selected?.candidate.sourceName}</strong><span>{selected?.requiresProbe ? "Runtime probe required" : "Direct path verified"}</span></div></section>
      )}
    </main>
  );
}
