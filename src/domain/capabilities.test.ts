import { describe, expect, it } from "vitest";
import { evaluateCandidate, platformCapabilityHints, rankCandidates } from "./capabilities";
import type { MediaCandidate } from "./media";

const candidate = (overrides: Partial<MediaCandidate> = {}): MediaCandidate => ({
  id: "candidate-1",
  mediaId: "northstar",
  sourceId: "atlas",
  sourceName: "Atlas NAS",
  sourceKind: "smb",
  uri: "https://media.openstream.test/northstar.m3u8",
  bitrateMbps: 26,
  directPlay: true,
  verified: true,
  availability: "ready",
  format: {
    container: "hls",
    videoCodec: "hevc",
    audioCodec: "eac3",
    width: 3840,
    height: 2160,
    frameRate: 24,
    bitDepth: 10,
    dynamicRange: "dolby-vision",
    dolbyVisionProfile: "8.1",
    projection: "flat",
    spatialAudio: "atmos",
  },
  ...overrides,
});

describe("playback capability negotiation", () => {
  it("requires a runtime Dolby Vision probe instead of claiming unconditional tvOS support", () => {
    const result = evaluateCandidate(candidate(), platformCapabilityHints.tvos);
    expect(result.playable).toBe(true);
    expect(result.requiresProbe).toBe(true);
    expect(result.reasons.find((item) => item.code === "dolby-vision-profile")?.level).toBe("runtime-check");
  });

  it("rejects immersive media on tvOS but accepts APMP-style projection on visionOS", () => {
    const immersive = candidate({
      format: { ...candidate().format, projection: "stereo-180" },
    });
    expect(evaluateCandidate(immersive, platformCapabilityHints.tvos).playable).toBe(false);
    expect(evaluateCandidate(immersive, platformCapabilityHints.visionos).playable).toBe(true);
  });

  it("ranks a compatible direct-play source above an offline source", () => {
    const ready = candidate({ id: "ready" });
    const offline = candidate({ id: "offline", availability: "offline", bitrateMbps: 12 });
    expect(rankCandidates([offline, ready], platformCapabilityHints.tvos)[0].candidate.id).toBe("ready");
  });
});
