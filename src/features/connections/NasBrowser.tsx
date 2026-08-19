import { ArrowUp, Film, Folder, Loader2, Play } from "lucide-react";
import { useEffect, useState } from "react";
import { useApp } from "../../app/AppContext";
import type { MediaFormat, MediaItem } from "../../domain/media";
import type { ConnectionSummary } from "../../integrations/connection";
import { browseNasDirectory, createNasMediaCapability, type NasDirectoryListing } from "../../integrations/gateway";

function containerFromName(name: string): MediaFormat["container"] {
  const extension = name.slice(name.lastIndexOf(".") + 1).toLowerCase();
  if (extension === "mp4" || extension === "m4v") return "mp4";
  if (extension === "mov") return "mov";
  if (extension === "mkv") return "mkv";
  if (extension === "webm") return "webm";
  if (extension === "ts" || extension === "m2ts") return "mpeg-ts";
  return "unknown";
}

/**
 * NAS files are streamed without server-side probing, so only the container is
 * known (from the file name). Everything else is a neutral placeholder the
 * device replaces with real values when it probes the stream at playback time.
 */
const PLACEHOLDER_NAS_FORMAT: Omit<MediaFormat, "container"> = {
  videoCodec: "unknown",
  audioCodec: "unknown",
  width: 1920,
  height: 1080,
  frameRate: 24,
  bitDepth: 8,
  dynamicRange: "sdr",
  projection: "flat",
  spatialAudio: "none",
};

function nasMediaItem(connection: ConnectionSummary, file: { name: string; path: string; size?: number }, mediaUrl: string): MediaItem {
  const title = file.name.replace(/\.[^.]+$/, "");
  return {
    id: `nas-${connection.id}-${file.path}`,
    externalIds: {},
    kind: "video",
    title,
    subtitle: connection.name,
    synopsis: `Playing directly from ${connection.name}. Format details are probed on this device before playback.`,
    year: new Date().getFullYear(),
    rating: "",
    runtimeMinutes: 0,
    artworkCell: [1, 1],
    candidates: [{
      id: `nas-candidate-${file.path}`,
      mediaId: `nas-${connection.id}-${file.path}`,
      sourceId: connection.id,
      sourceName: connection.name,
      sourceKind: "local",
      uri: mediaUrl,
      format: {
        container: containerFromName(file.name),
        ...PLACEHOLDER_NAS_FORMAT,
      },
      bitrateMbps: file.size ? Math.max(1, Math.round(file.size / 1_000_000 / 60)) : 20,
      directPlay: true,
      verified: true,
      availability: "ready",
    }],
  };
}

function formatSize(size?: number) {
  if (!size) return "";
  if (size >= 1_000_000_000) return `${(size / 1_000_000_000).toFixed(1)} GB`;
  if (size >= 1_000_000) return `${Math.round(size / 1_000_000)} MB`;
  return `${Math.round(size / 1_000)} KB`;
}

export function NasBrowser({ connection }: { connection: ConnectionSummary }) {
  const { beginPlayback, notify } = useApp();
  const [path, setPath] = useState("");
  const [listing, setListing] = useState<NasDirectoryListing>();
  const [state, setState] = useState<"loading" | "ready" | "error">("loading");
  const [opening, setOpening] = useState<string>();

  useEffect(() => {
    let cancelled = false;
    setState("loading");
    browseNasDirectory(connection.id, path)
      .then((result) => {
        if (cancelled) return;
        setListing(result);
        setState("ready");
      })
      .catch(() => !cancelled && setState("error"));
    return () => { cancelled = true; };
  }, [connection.id, path]);

  const play = async (file: { name: string; path: string; size?: number }) => {
    setOpening(file.path);
    try {
      const mediaUrl = await createNasMediaCapability(connection.id, file.path);
      beginPlayback(nasMediaItem(connection, file, mediaUrl));
    } catch (error) {
      notify({
        tone: "warning",
        title: "Could not open the file",
        detail: error instanceof Error ? error.message : "The NAS media handoff failed.",
      });
    } finally {
      setOpening(undefined);
    }
  };

  return (
    <section className="nas-browser" aria-label={`Browse ${connection.name}`}>
      <header>
        <span className="side-label">Browse files</span>
        <div className="nas-path">
          <button data-focusable className="utility-button" disabled={!path} onClick={() => setPath(path.split("/").slice(0, -1).join("/"))}><ArrowUp size={17} />Up</button>
          <small>/{listing?.path ?? path}</small>
        </div>
      </header>
      {state === "loading" ? <p className="nas-note"><Loader2 size={17} className="spin" />Reading folder…</p> : null}
      {state === "error" ? <p className="nas-note">This folder could not be read. Check that the share is still mounted.</p> : null}
      {state === "ready" && listing ? (
        <div className="nas-entries">
          {listing.directories.map((directory) => (
            <button key={directory.path} data-focusable className="nas-row" onClick={() => setPath(directory.path)}>
              <Folder size={19} /><span>{directory.name}</span>
            </button>
          ))}
          {listing.files.map((file) => (
            <button key={file.path} data-focusable className="nas-row file" disabled={opening === file.path} onClick={() => void play(file)}>
              <Film size={19} /><span>{file.name}</span><small>{formatSize(file.size)}</small>
              <span className="nas-play">{opening === file.path ? <Loader2 size={17} className="spin" /> : <Play size={17} fill="currentColor" />}</span>
            </button>
          ))}
          {listing.directories.length === 0 && listing.files.length === 0 ? <p className="nas-note">No folders or video files here.</p> : null}
        </div>
      ) : null}
    </section>
  );
}
