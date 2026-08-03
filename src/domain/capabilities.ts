import type {
  AudioCodec,
  Container,
  DolbyVisionProfile,
  DynamicRange,
  MediaCandidate,
  Projection,
  VideoCodec,
} from "./media";

export type PlatformId =
  | "web"
  | "tvos"
  | "visionos"
  | "android-xr"
  | "meta-quest"
  | "samsung-tizen";

export type SupportLevel = "supported" | "runtime-check" | "fallback" | "unsupported";

export type PlaybackCapabilities = {
  platform: PlatformId;
  videoCodecs: Record<VideoCodec, SupportLevel>;
  audioCodecs: Record<AudioCodec, SupportLevel>;
  containers: Record<Container, SupportLevel>;
  dynamicRanges: Record<DynamicRange, SupportLevel>;
  dolbyVisionProfiles: Partial<Record<DolbyVisionProfile, SupportLevel>>;
  projections: Record<Projection, SupportLevel>;
  maxWidth: number;
  maxHeight: number;
  maxFrameRate: number;
  secureNativePlayback: boolean;
  probedAt?: string;
};

export type CompatibilityReason = {
  code:
    | "container"
    | "video-codec"
    | "audio-codec"
    | "dynamic-range"
    | "dolby-vision-profile"
    | "projection"
    | "resolution"
    | "frame-rate"
    | "offline";
  level: SupportLevel;
  message: string;
};

export type CandidateEvaluation = {
  candidate: MediaCandidate;
  score: number;
  playable: boolean;
  requiresProbe: boolean;
  reasons: CompatibilityReason[];
};

const videoRuntime: Record<VideoCodec, SupportLevel> = {
  h264: "supported",
  hevc: "runtime-check",
  av1: "runtime-check",
  vp9: "runtime-check",
  unknown: "runtime-check",
};

const audioRuntime: Record<AudioCodec, SupportLevel> = {
  aac: "supported",
  ac3: "runtime-check",
  eac3: "runtime-check",
  truehd: "runtime-check",
  dts: "runtime-check",
  "dts-hd": "runtime-check",
  flac: "runtime-check",
  opus: "runtime-check",
  unknown: "runtime-check",
};

const containersRuntime: Record<Container, SupportLevel> = {
  mp4: "supported",
  mov: "runtime-check",
  mkv: "runtime-check",
  webm: "runtime-check",
  "mpeg-ts": "runtime-check",
  hls: "runtime-check",
  dash: "runtime-check",
  unknown: "runtime-check",
};

const projectionsFlatOnly: Record<Projection, SupportLevel> = {
  flat: "supported",
  spatial: "unsupported",
  "stereo-180": "unsupported",
  "mono-180": "unsupported",
  "stereo-360": "unsupported",
  "mono-360": "unsupported",
  "wide-fov": "unsupported",
  "apple-immersive": "unsupported",
};

export const platformCapabilityHints: Record<PlatformId, PlaybackCapabilities> = {
  web: {
    platform: "web",
    videoCodecs: videoRuntime,
    audioCodecs: audioRuntime,
    containers: containersRuntime,
    dynamicRanges: {
      sdr: "supported",
      hdr10: "runtime-check",
      "hdr10-plus": "runtime-check",
      hlg: "runtime-check",
      "dolby-vision": "runtime-check",
    },
    dolbyVisionProfiles: { unknown: "runtime-check" },
    projections: {
      ...projectionsFlatOnly,
      spatial: "runtime-check",
      "stereo-180": "runtime-check",
      "mono-180": "runtime-check",
      "stereo-360": "runtime-check",
      "mono-360": "runtime-check",
      "wide-fov": "runtime-check",
    },
    maxWidth: 3840,
    maxHeight: 2160,
    maxFrameRate: 60,
    secureNativePlayback: false,
  },
  tvos: {
    platform: "tvos",
    videoCodecs: { ...videoRuntime, hevc: "supported" },
    audioCodecs: { ...audioRuntime, ac3: "supported", eac3: "supported" },
    containers: { ...containersRuntime, mov: "supported", hls: "supported" },
    dynamicRanges: {
      sdr: "supported",
      hdr10: "supported",
      "hdr10-plus": "runtime-check",
      hlg: "supported",
      "dolby-vision": "runtime-check",
    },
    dolbyVisionProfiles: {
      "5": "runtime-check",
      "7": "runtime-check",
      "8.1": "runtime-check",
      "8.2": "runtime-check",
      "8.4": "runtime-check",
      unknown: "runtime-check",
    },
    projections: projectionsFlatOnly,
    maxWidth: 3840,
    maxHeight: 2160,
    maxFrameRate: 60,
    secureNativePlayback: true,
  },
  visionos: {
    platform: "visionos",
    videoCodecs: { ...videoRuntime, hevc: "supported" },
    audioCodecs: { ...audioRuntime, ac3: "supported", eac3: "supported" },
    containers: { ...containersRuntime, mov: "supported", hls: "supported" },
    dynamicRanges: {
      sdr: "supported",
      hdr10: "supported",
      "hdr10-plus": "runtime-check",
      hlg: "supported",
      "dolby-vision": "runtime-check",
    },
    dolbyVisionProfiles: {
      "5": "runtime-check",
      "7": "runtime-check",
      "8.1": "runtime-check",
      "8.2": "runtime-check",
      "8.4": "runtime-check",
      unknown: "runtime-check",
    },
    projections: {
      flat: "supported",
      spatial: "supported",
      "stereo-180": "supported",
      "mono-180": "supported",
      "stereo-360": "supported",
      "mono-360": "supported",
      "wide-fov": "supported",
      "apple-immersive": "supported",
    },
    maxWidth: 8192,
    maxHeight: 8192,
    maxFrameRate: 90,
    secureNativePlayback: true,
  },
  "android-xr": {
    platform: "android-xr",
    videoCodecs: videoRuntime,
    audioCodecs: audioRuntime,
    containers: containersRuntime,
    dynamicRanges: {
      sdr: "supported",
      hdr10: "runtime-check",
      "hdr10-plus": "runtime-check",
      hlg: "runtime-check",
      "dolby-vision": "runtime-check",
    },
    dolbyVisionProfiles: { unknown: "runtime-check" },
    projections: {
      ...projectionsFlatOnly,
      spatial: "runtime-check",
      "stereo-180": "runtime-check",
      "mono-180": "runtime-check",
      "stereo-360": "runtime-check",
      "mono-360": "runtime-check",
      "wide-fov": "runtime-check",
    },
    maxWidth: 8192,
    maxHeight: 8192,
    maxFrameRate: 90,
    secureNativePlayback: true,
  },
  "meta-quest": {
    platform: "meta-quest",
    videoCodecs: videoRuntime,
    audioCodecs: audioRuntime,
    containers: containersRuntime,
    dynamicRanges: {
      sdr: "supported",
      hdr10: "runtime-check",
      "hdr10-plus": "runtime-check",
      hlg: "runtime-check",
      "dolby-vision": "runtime-check",
    },
    dolbyVisionProfiles: { unknown: "runtime-check" },
    projections: {
      ...projectionsFlatOnly,
      spatial: "runtime-check",
      "stereo-180": "supported",
      "mono-180": "supported",
      "stereo-360": "supported",
      "mono-360": "supported",
      "wide-fov": "runtime-check",
    },
    maxWidth: 8192,
    maxHeight: 8192,
    maxFrameRate: 90,
    secureNativePlayback: true,
  },
  "samsung-tizen": {
    platform: "samsung-tizen",
    videoCodecs: { ...videoRuntime, h264: "supported", hevc: "runtime-check" },
    audioCodecs: audioRuntime,
    containers: containersRuntime,
    dynamicRanges: {
      sdr: "supported",
      hdr10: "runtime-check",
      "hdr10-plus": "runtime-check",
      hlg: "runtime-check",
      "dolby-vision": "runtime-check",
    },
    dolbyVisionProfiles: { unknown: "runtime-check" },
    projections: projectionsFlatOnly,
    maxWidth: 7680,
    maxHeight: 4320,
    maxFrameRate: 60,
    secureNativePlayback: true,
  },
};

const supportScore: Record<SupportLevel, number> = {
  supported: 30,
  "runtime-check": 8,
  fallback: -15,
  unsupported: -1000,
};

function reason(code: CompatibilityReason["code"], level: SupportLevel, message: string) {
  return { code, level, message } satisfies CompatibilityReason;
}

export function evaluateCandidate(candidate: MediaCandidate, capabilities: PlaybackCapabilities): CandidateEvaluation {
  const { format } = candidate;
  const checks: CompatibilityReason[] = [
    reason("container", capabilities.containers[format.container], `Container: ${format.container}`),
    reason("video-codec", capabilities.videoCodecs[format.videoCodec], `Video: ${format.videoCodec.toUpperCase()}`),
    reason("audio-codec", capabilities.audioCodecs[format.audioCodec], `Audio: ${format.audioCodec.toUpperCase()}`),
    reason("dynamic-range", capabilities.dynamicRanges[format.dynamicRange], `Range: ${format.dynamicRange}`),
    reason("projection", capabilities.projections[format.projection], `Projection: ${format.projection}`),
  ];

  if (format.dynamicRange === "dolby-vision") {
    const profile = format.dolbyVisionProfile ?? "unknown";
    checks.push(
      reason(
        "dolby-vision-profile",
        capabilities.dolbyVisionProfiles[profile] ?? capabilities.dolbyVisionProfiles.unknown ?? "runtime-check",
        `Dolby Vision profile: ${profile}`,
      ),
    );
  }

  if (format.width > capabilities.maxWidth || format.height > capabilities.maxHeight) {
    checks.push(reason("resolution", "unsupported", `${format.width}x${format.height} exceeds the probed limit`));
  }

  if (format.frameRate > capabilities.maxFrameRate) {
    checks.push(reason("frame-rate", "unsupported", `${format.frameRate} fps exceeds the probed limit`));
  }

  if (candidate.availability === "offline") {
    checks.push(reason("offline", "unsupported", `${candidate.sourceName} is offline`));
  }

  let score = candidate.directPlay ? 75 : 15;
  score += candidate.verified ? 16 : 0;
  score -= Math.min(candidate.bitrateMbps, 200) / 20;
  score -= Math.min(candidate.latencyMs ?? 0, 5000) / 500;
  for (const check of checks) score += supportScore[check.level];

  return {
    candidate,
    score,
    playable: checks.every((check) => check.level !== "unsupported"),
    requiresProbe: checks.some((check) => check.level === "runtime-check"),
    reasons: checks,
  };
}

export function rankCandidates(candidates: MediaCandidate[], capabilities: PlaybackCapabilities) {
  return candidates
    .map((candidate) => evaluateCandidate(candidate, capabilities))
    .sort((a, b) => b.score - a.score);
}

export function mergeCapabilityProbe(
  base: PlaybackCapabilities,
  probe: Partial<Omit<PlaybackCapabilities, "platform">>,
): PlaybackCapabilities {
  return {
    ...base,
    ...probe,
    videoCodecs: { ...base.videoCodecs, ...probe.videoCodecs },
    audioCodecs: { ...base.audioCodecs, ...probe.audioCodecs },
    containers: { ...base.containers, ...probe.containers },
    dynamicRanges: { ...base.dynamicRanges, ...probe.dynamicRanges },
    dolbyVisionProfiles: { ...base.dolbyVisionProfiles, ...probe.dolbyVisionProfiles },
    projections: { ...base.projections, ...probe.projections },
  };
}
