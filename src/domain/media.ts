export type MediaKind = "movie" | "series" | "episode" | "live" | "video";

export type DynamicRange =
  | "sdr"
  | "hdr10"
  | "hdr10-plus"
  | "hlg"
  | "dolby-vision";

export type DolbyVisionProfile =
  | "5"
  | "7"
  | "8.1"
  | "8.2"
  | "8.4"
  | "unknown";

export type Projection =
  | "flat"
  | "spatial"
  | "stereo-180"
  | "mono-180"
  | "stereo-360"
  | "mono-360"
  | "wide-fov"
  | "apple-immersive";

export type VideoCodec = "h264" | "hevc" | "av1" | "vp9" | "unknown";
export type AudioCodec =
  | "aac"
  | "ac3"
  | "eac3"
  | "truehd"
  | "dts"
  | "dts-hd"
  | "flac"
  | "opus"
  | "unknown";

export type Container = "mp4" | "mov" | "mkv" | "webm" | "mpeg-ts" | "hls" | "dash" | "unknown";

export type MediaFormat = {
  container: Container;
  videoCodec: VideoCodec;
  audioCodec: AudioCodec;
  width: number;
  height: number;
  frameRate: number;
  bitDepth: 8 | 10 | 12;
  dynamicRange: DynamicRange;
  dolbyVisionProfile?: DolbyVisionProfile;
  projection: Projection;
  spatialAudio?: "atmos" | "apac" | "ambisonic" | "none";
};

export type SourceKind =
  | "local"
  | "smb"
  | "nfs"
  | "webdav"
  | "google-drive"
  | "jellyfin"
  | "plex"
  | "emby"
  | "stremio";

export type MediaCandidate = {
  id: string;
  mediaId: string;
  sourceId: string;
  sourceName: string;
  sourceKind: SourceKind;
  uri: string;
  format: MediaFormat;
  bitrateMbps: number;
  directPlay: boolean;
  verified: boolean;
  latencyMs?: number;
  availability: "ready" | "remote" | "offline" | "unknown";
};

export type MediaItem = {
  id: string;
  externalIds: {
    imdb?: string;
    tmdb?: number;
    tvdb?: number;
  };
  kind: MediaKind;
  title: string;
  subtitle?: string;
  synopsis: string;
  year: number;
  rating: string;
  runtimeMinutes: number;
  artworkCell: [number, number];
  heroAsset?: "northstar" | "signal";
  progress?: number;
  remaining?: string;
  candidates: MediaCandidate[];
};

export const dynamicRangeLabel: Record<DynamicRange, string> = {
  sdr: "SDR",
  hdr10: "HDR10",
  "hdr10-plus": "HDR10+",
  hlg: "HLG",
  "dolby-vision": "Dolby Vision",
};

export function formatDolbyVisionProfile(profile?: DolbyVisionProfile) {
  return profile && profile !== "unknown" ? `P${profile}` : "Profile unverified";
}

export function formatResolution(format: MediaFormat) {
  if (format.width >= 7600 || format.height >= 4200) return "8K";
  if (format.width >= 3800 || format.height >= 2100) return "4K";
  if (format.width >= 2500 || format.height >= 1400) return "1440p";
  if (format.width >= 1900 || format.height >= 1000) return "1080p";
  return `${format.height}p`;
}
