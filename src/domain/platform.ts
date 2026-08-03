import { mergeCapabilityProbe, platformCapabilityHints, type PlatformId } from "./capabilities";

export function inferPlatform(userAgent = navigator.userAgent): PlatformId {
  const value = userAgent.toLowerCase();
  if (value.includes("tizen")) return "samsung-tizen";
  if (value.includes("oculus") || value.includes("quest")) return "meta-quest";
  if (value.includes("android") && value.includes("xr")) return "android-xr";
  return "web";
}

type MediaCapabilitiesInfo = {
  supported: boolean;
  smooth: boolean;
  powerEfficient: boolean;
};

async function probe(type: string): Promise<MediaCapabilitiesInfo | undefined> {
  if (!navigator.mediaCapabilities?.decodingInfo) return undefined;
  try {
    return await navigator.mediaCapabilities.decodingInfo({
      type: "file",
      video: {
        contentType: type,
        width: 3840,
        height: 2160,
        bitrate: 25_000_000,
        framerate: 60,
      },
    });
  } catch {
    return undefined;
  }
}

export async function probeBrowserCapabilities(platform = inferPlatform()) {
  const base = platformCapabilityHints[platform];
  const [h264, hevc, av1, vp9] = await Promise.all([
    probe('video/mp4; codecs="avc1.640033"'),
    probe('video/mp4; codecs="hvc1.2.4.L153.B0"'),
    probe('video/mp4; codecs="av01.0.13M.10"'),
    probe('video/webm; codecs="vp09.02.51.10"'),
  ]);

  const result = mergeCapabilityProbe(base, {
    videoCodecs: {
      ...base.videoCodecs,
      h264: h264?.supported ? "supported" : base.videoCodecs.h264,
      hevc: hevc?.supported ? "supported" : base.videoCodecs.hevc,
      av1: av1?.supported ? "supported" : base.videoCodecs.av1,
      vp9: vp9?.supported ? "supported" : base.videoCodecs.vp9,
    },
    probedAt: new Date().toISOString(),
  });

  return result;
}
