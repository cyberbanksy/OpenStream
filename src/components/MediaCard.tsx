import { CheckCircle2, Play } from "lucide-react";
import { useMemo } from "react";
import { rankCandidates } from "../domain/capabilities";
import { dynamicRangeLabel, formatDolbyVisionProfile, formatResolution, type MediaItem } from "../domain/media";
import { useApp } from "../app/AppContext";
import { Artwork } from "./Artwork";

export function MediaCard({
  item,
  portrait = false,
  autoFocus = false,
  onOpen,
}: {
  item: MediaItem;
  portrait?: boolean;
  autoFocus?: boolean;
  onOpen?: (item: MediaItem) => void;
}) {
  const { capabilities, openItem } = useApp();
  const best = useMemo(() => rankCandidates(item.candidates, capabilities)[0], [capabilities, item.candidates]);
  const mediaFormat = best?.candidate.format;

  return (
    <button
      className={`media-card ${portrait ? "portrait" : "landscape"}`}
      data-focusable
      data-autofocus={autoFocus ? true : undefined}
      autoFocus={autoFocus}
      onClick={() => (onOpen ?? openItem)(item)}
      aria-label={`Open ${item.title}`}
    >
      <span className="media-visual">
        <Artwork cell={item.artworkCell} />
        <span className="media-gradient" />
        <span className="card-play"><Play size={24} fill="currentColor" /></span>
        <span className="media-title-block">
          <strong>{item.title}</strong>
          <small>{item.remaining ?? item.subtitle ?? (mediaFormat ? formatResolution(mediaFormat) : "Video")}</small>
        </span>
        {typeof item.progress === "number" ? (
          <span className="progress-track" aria-label={`${item.progress}% watched`}>
            <span style={{ width: `${item.progress}%` }} />
          </span>
        ) : null}
      </span>
      <span className="media-provenance">
        <b>{best?.candidate.sourceName ?? "No source"}</b>
        <i>·</i>
        <span>{best?.candidate.directPlay ? "Direct" : "Transcode"}</span>
        {best?.candidate.verified ? <CheckCircle2 size={13} aria-label="Verified source" /> : null}
        {mediaFormat ? (
          <span>
            {dynamicRangeLabel[mediaFormat.dynamicRange]}
            {mediaFormat.dynamicRange === "dolby-vision" ? ` ${formatDolbyVisionProfile(mediaFormat.dolbyVisionProfile)}` : ""}
          </span>
        ) : null}
      </span>
    </button>
  );
}
