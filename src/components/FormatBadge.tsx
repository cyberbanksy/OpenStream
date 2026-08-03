import { dynamicRangeLabel, formatDolbyVisionProfile, formatResolution, type MediaFormat } from "../domain/media";

export function FormatBadges({ format, detailed = false }: { format: MediaFormat; detailed?: boolean }) {
  return (
    <span className="format-badges" aria-label="Media format">
      <span>{formatResolution(format)}</span>
      <span>{dynamicRangeLabel[format.dynamicRange]}</span>
      {format.dynamicRange === "dolby-vision" ? <span>{formatDolbyVisionProfile(format.dolbyVisionProfile)}</span> : null}
      {format.spatialAudio && format.spatialAudio !== "none" ? <span>{format.spatialAudio.toUpperCase()}</span> : null}
      {detailed ? <span>{Math.round(format.frameRate)} fps</span> : null}
    </span>
  );
}
