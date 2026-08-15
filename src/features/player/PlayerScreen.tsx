import { ArrowLeft, CheckCircle2, CircleHelp, Cpu, Expand, Glasses, Pause, Play, RotateCcw, ShieldAlert, Volume2 } from "lucide-react";
import { useEffect, useMemo, useRef, useState } from "react";
import { useApp } from "../../app/AppContext";
import { logClient } from "../../app/log";
import { FormatBadges } from "../../components/FormatBadge";
import { dynamicRangeLabel } from "../../domain/media";
import { createTranscodeSession, createXrViewerHandoff, GATEWAY_BASE_PATH } from "../../integrations/gateway";

function formatClock(seconds: number) {
  if (!Number.isFinite(seconds) || seconds <= 0) return "0:00";
  const whole = Math.floor(seconds);
  const hours = Math.floor(whole / 3600);
  const minutes = Math.floor((whole % 3600) / 60);
  const rest = whole % 60;
  return hours > 0
    ? `${hours}:${String(minutes).padStart(2, "0")}:${String(rest).padStart(2, "0")}`
    : `${minutes}:${String(rest).padStart(2, "0")}`;
}

export function PlayerScreen() {
  const { playback, goBack, selectPlaybackRoute, setPlaybackStatus, notify, capabilities, transcoder } = useApp();
  const videoRef = useRef<HTMLVideoElement>(null);
  const [playing, setPlaying] = useState(false);
  const [openingImmersive, setOpeningImmersive] = useState(false);
  const [transcodeUrl, setTranscodeUrl] = useState<string>();
  const [transcodeState, setTranscodeState] = useState<"idle" | "starting" | "active">("idle");
  const [currentTime, setCurrentTime] = useState(0);
  const [duration, setDuration] = useState(0);
  const selected = useMemo(() => playback?.routes.find((route) => route.candidate.id === playback.selectedRouteId), [playback]);
  const gatewayRoute = selected?.candidate.uri.startsWith(`${GATEWAY_BASE_PATH}/`) ?? false;
  const directSrc = selected?.candidate.uri ?? "";
  const activeSrc = transcodeUrl ?? directSrc;
  const canTranscode = gatewayRoute && transcoder?.available === true;

  useEffect(() => {
    setTranscodeUrl(undefined);
    setTranscodeState("idle");
    setCurrentTime(0);
    setDuration(0);
  }, [playback?.selectedRouteId, playback?.item.id]);

  useEffect(() => {
    const video = videoRef.current;
    if (!video) return;
    video.load();
    if (transcodeUrl) {
      void video.play().then(() => {
        setPlaying(true);
        setPlaybackStatus("playing");
      }).catch(() => setPlaying(false));
    }
  }, [activeSrc, setPlaybackStatus, transcodeUrl]);

  if (!playback) return <main className="screen player-screen"><button className="back-button" onClick={goBack}><ArrowLeft />Back</button><p>No playback session is active.</p></main>;

  const startTranscode = async (reason: "manual" | "fallback") => {
    if (!selected || !gatewayRoute || transcodeState === "starting" || transcodeUrl) return;
    setTranscodeState("starting");
    try {
      const url = await createTranscodeSession(selected.candidate.uri, {
        maxWidth: capabilities.maxWidth,
        maxHeight: capabilities.maxHeight,
        videoCodec: "h264",
      });
      logClient("info", `local transcode started (${reason}) for ${playback.item.title}`);
      setTranscodeUrl(url);
      setTranscodeState("active");
      if (reason === "fallback") {
        notify({ tone: "neutral", title: "Transcoding locally", detail: "This device could not play the source directly, so the gateway is converting it on the fly." });
      }
    } catch (error) {
      setTranscodeState("idle");
      notify({ tone: "warning", title: "Local transcode unavailable", detail: error instanceof Error ? error.message : "The gateway could not start ffmpeg." });
    }
  };

  const handleVideoError = () => {
    if (gatewayRoute && !transcodeUrl && transcoder?.available) {
      void startTranscode("fallback");
      return;
    }
    if (playing) {
      setPlaying(false);
      notify({ tone: "warning", title: "Playback failed", detail: "The current route stopped. Choose another source." });
    }
  };

  const togglePlay = async () => {
    const video = videoRef.current;
    if (!video) return;
    if (video.paused) {
      try {
        await video.play();
        setPlaying(true);
        setPlaybackStatus("playing");
      } catch {
        if (gatewayRoute && !transcodeUrl && transcoder?.available) {
          void startTranscode("fallback");
          return;
        }
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
        source: videoRef.current?.currentSrc || activeSrc,
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

  const progressPercent = duration > 0 ? Math.min(100, (currentTime / duration) * 100) : 0;
  const headerRoute = selected ?? playback.routes[0];

  return (
    <main className="screen player-screen">
      <video
        ref={videoRef}
        className="player-video"
        preload="metadata"
        onEnded={() => setPlaying(false)}
        onError={handleVideoError}
        onTimeUpdate={(event) => setCurrentTime(event.currentTarget.currentTime)}
        onDurationChange={(event) => setDuration(event.currentTarget.duration)}
      >
        {activeSrc ? <source src={activeSrc} type={transcodeUrl ? "video/mp4" : undefined} /> : null}
      </video>
      <div className="player-scrim" />
      <header className="player-header"><button className="back-button" data-focusable onClick={goBack}><ArrowLeft size={21} />Back</button><span><strong>{playback.item.title}</strong><small>{selected?.candidate.sourceName ?? "Choose a route"}{transcodeUrl ? " · Local transcode" : ""}</small></span>{headerRoute ? <FormatBadges format={headerRoute.candidate.format} detailed /> : null}</header>
      {playback.status === "choosing" || playback.status === "blocked" ? (
        <section className="route-picker" aria-labelledby="route-picker-title"><header><span><h1 id="route-picker-title">Choose a stream route</h1><p>OpenStream checks the current device before selecting a format.</p></span>{playback.status === "blocked" ? <ShieldAlert size={28} /> : <CircleHelp size={28} />}</header><div className="route-list">{playback.routes.length === 0 ? <p className="dialog-inline-note">No stream routes are available for this title on this device. Add or check a source, then try again.</p> : playback.routes.map((route, index) => <button key={route.candidate.id} data-focusable data-autofocus={index === 0 ? true : undefined} autoFocus={index === 0} className={!route.playable ? "playback-route blocked" : route.candidate.id === playback.selectedRouteId ? "playback-route selected" : "playback-route"} disabled={!route.playable} onClick={() => { selectPlaybackRoute(route.candidate.id); setPlaybackStatus("ready"); }}><span className="route-rank">{index + 1}</span><span><strong>{route.candidate.sourceName}</strong><small>{route.candidate.directPlay ? "Direct play" : "Transcode"} · {route.candidate.bitrateMbps} Mbps</small></span><FormatBadges format={route.candidate.format} /><span className="route-check">{route.playable ? route.requiresProbe ? <CircleHelp size={18} /> : <CheckCircle2 size={18} /> : <ShieldAlert size={18} />}<small>{route.playable ? route.requiresProbe ? "Probe" : "Ready" : "Blocked"}</small></span></button>)}</div><footer><span>Availability is not a decoder guarantee.</span><button className="action-button primary" data-focusable disabled={!selected?.playable} onClick={() => setPlaybackStatus("ready")}><Play size={20} fill="currentColor" />Use selected route</button></footer></section>
      ) : (
        <section className="player-controls"><div className="timeline"><span><i style={{ width: `${progressPercent}%` }} /></span><small>{formatClock(currentTime)}</small><small>{duration > 0 ? formatClock(duration) : "--:--"}</small></div><div className="transport"><button data-focusable aria-label="Restart" onClick={() => { if (videoRef.current) videoRef.current.currentTime = 0; }}><RotateCcw size={23} /></button><button className="play-toggle" data-focusable data-autofocus autoFocus aria-label={playing ? "Pause" : "Play"} onClick={togglePlay}>{playing ? <Pause size={28} fill="currentColor" /> : <Play size={28} fill="currentColor" />}</button><button data-focusable aria-label="Volume"><Volume2 size={23} /></button></div><div className="player-modes"><button data-focusable onClick={openImmersiveViewer} disabled={openingImmersive || selected?.candidate.format.projection === "flat"}><Glasses size={20} />{openingImmersive ? "Opening" : "Immersive"}</button>{canTranscode && !transcodeUrl ? <button data-focusable disabled={transcodeState === "starting"} onClick={() => void startTranscode("manual")}><Cpu size={20} />{transcodeState === "starting" ? "Starting…" : "Transcode"}</button> : null}<button data-focusable><Expand size={20} />Fit</button></div><div className="now-route"><strong>{selected?.candidate.sourceName}</strong><span>{transcodeUrl ? `Local transcode ≤ ${capabilities.maxWidth}×${capabilities.maxHeight}` : selected?.requiresProbe ? "Runtime probe required" : "Direct path verified"}</span></div></section>
      )}
    </main>
  );
}
