import { ArrowLeft, FolderOpen, Glasses, Pause, Play, RotateCcw, Volume2, VolumeX } from "lucide-react";
import { useEffect, useMemo, useRef, useState } from "react";
import * as THREE from "three";
import { Brand } from "../../components/Brand";
import { redeemXrViewerHandoff } from "../../integrations/gateway";
import {
  cropUvForEye,
  parseXrViewerOptions,
  projectionArc,
  type XrEye,
  type XrProjection,
  type XrStereoMode,
} from "./xrProjection";

type ViewerRuntime = {
  renderer: THREE.WebGLRenderer;
  video: HTMLVideoElement;
  camera: THREE.PerspectiveCamera;
  resetView: () => void;
};

export function XrViewer() {
  const initialOptions = useMemo(() => parseXrViewerOptions(window.location), []);
  const mountRef = useRef<HTMLDivElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const runtimeRef = useRef<ViewerRuntime | null>(null);
  const objectUrlRef = useRef<string | null>(null);
  const [source, setSource] = useState(initialOptions.source);
  const [projection, setProjection] = useState<XrProjection>(initialOptions.projection);
  const [stereo, setStereo] = useState<XrStereoMode>(initialOptions.stereo);
  const [dynamicRange, setDynamicRange] = useState(initialOptions.dynamicRange);
  const [isPlaying, setIsPlaying] = useState(false);
  const [isMuted, setIsMuted] = useState(true);
  // Mute is applied to the live video element; keeping it out of the scene
  // effect's dependencies means toggling it never rebuilds the WebGL scene.
  const isMutedRef = useRef(true);
  const [xrSupported, setXrSupported] = useState(false);
  const [sessionActive, setSessionActive] = useState(false);
  const [status, setStatus] = useState(initialOptions.handoffId ? "Opening secure media handoff" : "Choose a video to begin");
  const [error, setError] = useState<string | null>(initialOptions.rejectedLegacySource
    ? "Media URLs are no longer accepted in viewer links. Create a secure XR handoff instead."
    : null);

  useEffect(() => {
    const current = new URL(window.location.href);
    current.searchParams.delete("src");
    current.hash = "";
    window.history.replaceState(null, "", `${current.pathname}${current.search}`);

    if (!initialOptions.handoffId) return;
    let cancelled = false;
    redeemXrViewerHandoff(initialOptions.handoffId)
      .then((handoff) => {
        if (cancelled) return;
        setSource(handoff.source);
        setProjection(handoff.projection);
        setStereo(handoff.stereo);
        setDynamicRange(handoff.dynamicRange);
        setStatus("Secure media handoff ready");
        setError(null);
      })
      .catch((handoffError) => {
        if (cancelled) return;
        setStatus("Media handoff unavailable");
        setError(handoffError instanceof Error ? handoffError.message : "The secure media handoff could not be opened.");
      });
    return () => { cancelled = true; };
  }, [initialOptions]);

  useEffect(() => {
    let cancelled = false;
    navigator.xr?.isSessionSupported("immersive-vr")
      .then((supported) => { if (!cancelled) setXrSupported(supported); })
      .catch(() => { if (!cancelled) setXrSupported(false); });
    return () => { cancelled = true; };
  }, []);

  useEffect(() => {
    const mount = mountRef.current;
    if (!mount) return;

    const scene = new THREE.Scene();
    scene.background = new THREE.Color(0x020406);
    const camera = new THREE.PerspectiveCamera(72, mount.clientWidth / mount.clientHeight, 0.1, 1200);
    camera.layers.enable(1);
    const renderer = new THREE.WebGLRenderer({ antialias: true, powerPreference: "high-performance" });
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    renderer.setSize(mount.clientWidth, mount.clientHeight);
    renderer.xr.enabled = true;
    renderer.xr.setReferenceSpaceType("local");
    renderer.outputColorSpace = THREE.SRGBColorSpace;
    renderer.domElement.setAttribute("aria-label", "Immersive video preview");
    renderer.domElement.tabIndex = 0;
    mount.appendChild(renderer.domElement);

    const video = document.createElement("video");
    if (source) video.src = source;
    video.crossOrigin = "anonymous";
    video.loop = true;
    video.muted = isMutedRef.current;
    video.playsInline = true;
    video.preload = "auto";
    const texture = new THREE.VideoTexture(video);
    texture.colorSpace = THREE.SRGBColorSpace;
    texture.minFilter = THREE.LinearFilter;
    texture.magFilter = THREE.LinearFilter;

    const meshes = createVideoMeshes(texture, projection, stereo);
    meshes.forEach((mesh) => scene.add(mesh));

    let yaw = 0;
    let pitch = 0;
    let pointerId: number | null = null;
    let lastX = 0;
    let lastY = 0;
    const applyRotation = () => camera.rotation.set(pitch, yaw, 0, "YXZ");
    const resetView = () => {
      yaw = 0;
      pitch = 0;
      camera.fov = 72;
      camera.updateProjectionMatrix();
      applyRotation();
    };

    const onPointerDown = (event: PointerEvent) => {
      pointerId = event.pointerId;
      lastX = event.clientX;
      lastY = event.clientY;
      renderer.domElement.setPointerCapture(event.pointerId);
    };
    const onPointerMove = (event: PointerEvent) => {
      if (pointerId !== event.pointerId || renderer.xr.isPresenting) return;
      yaw -= (event.clientX - lastX) * 0.004;
      pitch = THREE.MathUtils.clamp(pitch - (event.clientY - lastY) * 0.004, -Math.PI / 2, Math.PI / 2);
      lastX = event.clientX;
      lastY = event.clientY;
      applyRotation();
    };
    const onPointerUp = (event: PointerEvent) => {
      if (pointerId === event.pointerId) pointerId = null;
    };
    const onWheel = (event: WheelEvent) => {
      if (renderer.xr.isPresenting) return;
      camera.fov = THREE.MathUtils.clamp(camera.fov + event.deltaY * 0.03, 45, 95);
      camera.updateProjectionMatrix();
    };
    const onKeyDown = (event: KeyboardEvent) => {
      if (renderer.xr.isPresenting) return;
      const step = 0.08;
      if (event.key === "ArrowLeft") yaw += step;
      else if (event.key === "ArrowRight") yaw -= step;
      else if (event.key === "ArrowUp") pitch = Math.min(Math.PI / 2, pitch + step);
      else if (event.key === "ArrowDown") pitch = Math.max(-Math.PI / 2, pitch - step);
      else return;
      event.preventDefault();
      applyRotation();
    };
    const onResize = () => {
      if (!mount.clientWidth || !mount.clientHeight) return;
      camera.aspect = mount.clientWidth / mount.clientHeight;
      camera.updateProjectionMatrix();
      renderer.setSize(mount.clientWidth, mount.clientHeight);
    };
    const onPlaying = () => {
      setIsPlaying(true);
      setStatus(renderer.xr.isPresenting ? "Immersive session active" : "Preview playing");
    };
    const onPause = () => setIsPlaying(false);
    const onVideoError = () => {
      setIsPlaying(false);
      setError("The media could not be decoded or its server did not allow cross-origin video textures.");
      setStatus("Media unavailable");
    };
    const onSessionStart = () => {
      setSessionActive(true);
      setStatus("Immersive session active");
    };
    const onSessionEnd = () => {
      setSessionActive(false);
      setStatus(video.paused ? "Preview paused" : "Preview playing");
    };

    renderer.domElement.addEventListener("pointerdown", onPointerDown);
    renderer.domElement.addEventListener("pointermove", onPointerMove);
    renderer.domElement.addEventListener("pointerup", onPointerUp);
    renderer.domElement.addEventListener("pointercancel", onPointerUp);
    renderer.domElement.addEventListener("wheel", onWheel, { passive: true });
    renderer.domElement.addEventListener("keydown", onKeyDown);
    video.addEventListener("playing", onPlaying);
    video.addEventListener("pause", onPause);
    video.addEventListener("error", onVideoError);
    renderer.xr.addEventListener("sessionstart", onSessionStart);
    renderer.xr.addEventListener("sessionend", onSessionEnd);
    window.addEventListener("resize", onResize);
    renderer.setAnimationLoop(() => renderer.render(scene, camera));
    runtimeRef.current = { renderer, video, camera, resetView };

    if (source) {
      video.play().catch(() => {
        setStatus("Ready to play");
        setIsPlaying(false);
      });
    }

    return () => {
      renderer.setAnimationLoop(null);
      renderer.domElement.removeEventListener("pointerdown", onPointerDown);
      renderer.domElement.removeEventListener("pointermove", onPointerMove);
      renderer.domElement.removeEventListener("pointerup", onPointerUp);
      renderer.domElement.removeEventListener("pointercancel", onPointerUp);
      renderer.domElement.removeEventListener("wheel", onWheel);
      renderer.domElement.removeEventListener("keydown", onKeyDown);
      video.removeEventListener("playing", onPlaying);
      video.removeEventListener("pause", onPause);
      video.removeEventListener("error", onVideoError);
      renderer.xr.removeEventListener("sessionstart", onSessionStart);
      renderer.xr.removeEventListener("sessionend", onSessionEnd);
      window.removeEventListener("resize", onResize);
      video.pause();
      video.removeAttribute("src");
      video.load();
      texture.dispose();
      meshes.forEach((mesh) => {
        mesh.geometry.dispose();
        (mesh.material as THREE.Material).dispose();
      });
      renderer.dispose();
      renderer.domElement.remove();
      if (runtimeRef.current?.renderer === renderer) runtimeRef.current = null;
    };
  }, [source, projection, stereo]);

  const togglePlayback = async () => {
    const video = runtimeRef.current?.video;
    if (!video) return;
    if (video.paused) {
      await video.play().catch(() => setError("Playback was blocked by the browser."));
    } else {
      video.pause();
      setStatus("Preview paused");
    }
  };

  const toggleMute = () => {
    const nextMuted = !isMuted;
    setIsMuted(nextMuted);
    isMutedRef.current = nextMuted;
    if (runtimeRef.current) runtimeRef.current.video.muted = nextMuted;
  };

  const enterXr = async () => {
    const renderer = runtimeRef.current?.renderer;
    if (!renderer || !navigator.xr) return;
    if (renderer.xr.getSession()) {
      await renderer.xr.getSession()?.end();
      return;
    }
    try {
      const session = await navigator.xr.requestSession("immersive-vr", {
        optionalFeatures: ["local-floor", "bounded-floor", "hand-tracking", "layers"],
      });
      await renderer.xr.setSession(session);
      await runtimeRef.current?.video.play();
    } catch {
      setError("The headset runtime declined the immersive session.");
    }
  };

  const openLocalFile = (file: File | undefined) => {
    if (!file || !file.type.startsWith("video/")) {
      if (file) setError("Choose a local video file.");
      return;
    }
    if (objectUrlRef.current) URL.revokeObjectURL(objectUrlRef.current);
    objectUrlRef.current = URL.createObjectURL(file);
    setSource(objectUrlRef.current);
    setError(null);
  };

  useEffect(() => () => {
    if (objectUrlRef.current) URL.revokeObjectURL(objectUrlRef.current);
  }, []);

  return (
    <main className="xr-viewer">
      <div ref={mountRef} className="xr-canvas" />
      <header className="xr-header">
        <button className="xr-icon-button" aria-label="Back to OpenStream" title="Back" onClick={() => { window.location.href = "/"; }}>
          <ArrowLeft size={22} />
        </button>
        <Brand />
        <div className="xr-title">
          <strong>Immersive viewer</strong>
          <span>{projection}° · {stereo.toUpperCase()} · {dynamicRange}</span>
        </div>
        <span className={xrSupported ? "xr-runtime supported" : "xr-runtime"}>
          {xrSupported ? "WebXR ready" : "2D preview"}
        </span>
      </header>

      <section className="xr-toolbar" aria-label="Immersive playback controls">
        <button className="xr-round-button primary" aria-label={isPlaying ? "Pause" : "Play"} title={isPlaying ? "Pause" : "Play"} onClick={togglePlayback}>
          {isPlaying ? <Pause size={25} /> : <Play size={25} fill="currentColor" />}
        </button>
        <button className="xr-round-button" aria-label={isMuted ? "Unmute" : "Mute"} title={isMuted ? "Unmute" : "Mute"} onClick={toggleMute}>
          {isMuted ? <VolumeX size={22} /> : <Volume2 size={22} />}
        </button>
        <button className="xr-round-button" aria-label="Reset view" title="Reset view" onClick={() => runtimeRef.current?.resetView()}>
          <RotateCcw size={21} />
        </button>
        <button className="xr-round-button" aria-label="Open local video" title="Open local video" onClick={() => fileInputRef.current?.click()}>
          <FolderOpen size={21} />
        </button>
        <input
          ref={fileInputRef}
          className="visually-hidden"
          type="file"
          aria-label="Local immersive video file"
          accept="video/*,.mkv"
          onChange={(event) => openLocalFile(event.target.files?.[0])}
        />
        <div className="xr-segmented" aria-label="Projection">
          {(["180", "360", "wide"] as XrProjection[]).map((mode) => (
            <button key={mode} className={projection === mode ? "selected" : ""} aria-pressed={projection === mode} onClick={() => setProjection(mode)}>
              {mode === "wide" ? "Wide" : `${mode}°`}
            </button>
          ))}
        </div>
        <div className="xr-segmented" aria-label="Stereo layout">
          {(["mono", "sbs", "tb"] as XrStereoMode[]).map((mode) => (
            <button key={mode} className={stereo === mode ? "selected" : ""} aria-pressed={stereo === mode} onClick={() => setStereo(mode)}>
              {mode.toUpperCase()}
            </button>
          ))}
        </div>
        <span className="xr-status" aria-live="polite">{status}</span>
        <button
          className="xr-enter-button"
          disabled={!xrSupported}
          onClick={enterXr}
        >
          <Glasses size={21} />
          {sessionActive ? "Exit VR" : "Enter VR"}
        </button>
      </section>
      {error ? <div className="xr-error" role="alert">{error}</div> : null}
    </main>
  );
}

function createVideoMeshes(
  texture: THREE.VideoTexture,
  projection: XrProjection,
  stereo: XrStereoMode,
): THREE.Mesh[] {
  if (stereo === "mono") {
    const mesh = createEyeMesh(texture, projection, stereo, "left");
    mesh.layers.set(1);
    mesh.layers.enable(2);
    return [mesh];
  }
  const left = createEyeMesh(texture, projection, stereo, "left");
  left.layers.set(1);
  const right = createEyeMesh(texture, projection, stereo, "right");
  right.layers.set(2);
  return [left, right];
}

function createEyeMesh(
  texture: THREE.VideoTexture,
  projection: XrProjection,
  stereo: XrStereoMode,
  eye: XrEye,
): THREE.Mesh {
  const arc = projectionArc(projection);
  const geometry = new THREE.SphereGeometry(500, 64, 40, arc.start, arc.length, 0, Math.PI);
  geometry.scale(-1, 1, 1);
  const uv = geometry.getAttribute("uv") as THREE.BufferAttribute;
  uv.array.set(cropUvForEye(new Float32Array(uv.array as ArrayLike<number>), stereo, eye));
  uv.needsUpdate = true;
  const material = new THREE.MeshBasicMaterial({ map: texture, side: THREE.FrontSide });
  const mesh = new THREE.Mesh(geometry, material);
  mesh.rotation.y = -Math.PI / 2;
  return mesh;
}
