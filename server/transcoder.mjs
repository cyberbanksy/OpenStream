import { spawn } from "node:child_process";

const FFMPEG = process.env.OPENSTREAM_FFMPEG ?? "ffmpeg";
const PROBE_TIMEOUT_MS = 8000;
const PROBE_FAILURE_TTL_MS = 30_000;

let cachedProbe;
let cachedProbeExpiry = 0;

function run(binary, args, timeoutMs) {
  return new Promise((resolve) => {
    const child = spawn(binary, args, { stdio: ["ignore", "pipe", "pipe"] });
    const chunks = [];
    const timer = setTimeout(() => child.kill("SIGKILL"), timeoutMs);
    child.stdout.on("data", (chunk) => chunks.push(chunk));
    child.stderr.on("data", (chunk) => chunks.push(chunk));
    child.on("error", () => {
      clearTimeout(timer);
      resolve(undefined);
    });
    child.on("close", (code) => {
      clearTimeout(timer);
      resolve(code === 0 ? Buffer.concat(chunks).toString("utf8") : undefined);
    });
  });
}

/**
 * Detects the local ffmpeg installation once and reports which encoders the
 * gateway can offer. Hardware encoders are preferred so transcoding stays
 * within what this device can sustain in real time.
 */
export async function probeTranscoder() {
  // A positive detection is cached for the process lifetime; a missing or
  // partial ffmpeg is only cached briefly so installing it mid-run is picked
  // up on the next request without a gateway restart.
  if (cachedProbe && (cachedProbe.available || Date.now() < cachedProbeExpiry)) return cachedProbe;
  const version = await run(FFMPEG, ["-hide_banner", "-version"], PROBE_TIMEOUT_MS);
  if (!version) {
    cachedProbe = { available: false };
    cachedProbeExpiry = Date.now() + PROBE_FAILURE_TTL_MS;
    return cachedProbe;
  }
  const encoderList = await run(FFMPEG, ["-hide_banner", "-encoders"], PROBE_TIMEOUT_MS) ?? "";
  const has = (name) => new RegExp(`\\b${name}\\b`).test(encoderList);
  const h264 = has("h264_videotoolbox") ? "h264_videotoolbox" : has("libx264") ? "libx264" : undefined;
  const hevc = has("hevc_videotoolbox") ? "hevc_videotoolbox" : has("libx265") ? "libx265" : undefined;
  const aac = has("aac_at") ? "aac_at" : has("aac") ? "aac" : undefined;
  cachedProbe = {
    available: Boolean(h264 && aac),
    version: version.split("\n")[0]?.replace(/^ffmpeg version\s+/, "").split(" ")[0],
    encoders: { h264, hevc, aac },
    hardwareAccelerated: Boolean(h264?.endsWith("_videotoolbox")),
  };
  if (!cachedProbe.available) cachedProbeExpiry = Date.now() + PROBE_FAILURE_TTL_MS;
  return cachedProbe;
}

const PROFILE_LIMITS = {
  maxWidth: { floor: 320, ceiling: 8192, fallback: 1920 },
  maxHeight: { floor: 240, ceiling: 8192, fallback: 1080 },
};

/**
 * Clamps a device-reported playback profile to sane transcoder bounds.
 * The client sends what the device probe says it can decode; the gateway
 * never exceeds it and never accepts absurd values.
 */
export function clampTranscodeProfile(requested = {}) {
  const clampDimension = (value, { floor, ceiling, fallback }) => {
    const parsed = Number(value);
    if (!Number.isFinite(parsed)) return fallback;
    return Math.min(ceiling, Math.max(floor, Math.round(parsed)));
  };
  return {
    maxWidth: clampDimension(requested.maxWidth, PROFILE_LIMITS.maxWidth),
    maxHeight: clampDimension(requested.maxHeight, PROFILE_LIMITS.maxHeight),
    videoCodec: requested.videoCodec === "hevc" ? "hevc" : "h264",
  };
}

function targetVideoBitrate(maxHeight) {
  if (maxHeight >= 2160) return "16M";
  if (maxHeight >= 1440) return "10M";
  if (maxHeight >= 1080) return "6M";
  if (maxHeight >= 720) return "3500k";
  return "1800k";
}

/**
 * Builds the ffmpeg argument list for one progressive fMP4 transcode.
 * Output always fits inside the clamped profile; smaller sources are never
 * upscaled thanks to force_original_aspect_ratio=decrease with min().
 */
export function buildTranscodeArgs({ input, profile, encoders }) {
  const clamped = clampTranscodeProfile(profile);
  const videoEncoder = clamped.videoCodec === "hevc" && encoders.hevc ? encoders.hevc : encoders.h264;
  const scale = `scale=w=min(iw\\,${clamped.maxWidth}):h=min(ih\\,${clamped.maxHeight}):force_original_aspect_ratio=decrease:force_divisible_by=2`;
  const args = [
    "-hide_banner",
    "-nostdin",
    "-loglevel", "warning",
    // Inputs are only local files or the gateway's loopback relay; the
    // whitelist keeps ffmpeg from dialing anything else on its own.
    "-protocol_whitelist", "file,http,tcp",
    "-i", input,
    "-map", "0:v:0",
    "-map", "0:a:0?",
    "-vf", scale,
    "-c:v", videoEncoder,
  ];
  if (videoEncoder.endsWith("_videotoolbox")) {
    args.push("-b:v", targetVideoBitrate(clamped.maxHeight), "-allow_sw", "1");
  } else {
    args.push("-preset", "veryfast", "-crf", "21", "-maxrate", targetVideoBitrate(clamped.maxHeight), "-bufsize", "32M");
  }
  if (videoEncoder.includes("hevc") || videoEncoder.includes("265")) {
    args.push("-tag:v", "hvc1");
  }
  args.push(
    "-c:a", encoders.aac,
    "-ac", "2",
    "-b:a", "192k",
    "-f", "mp4",
    "-movflags", "frag_keyframe+empty_moov+default_base_moof",
    "pipe:1",
  );
  return { args, clamped, videoEncoder };
}

export function startTranscode({ input, profile, encoders }) {
  const plan = buildTranscodeArgs({ input, profile, encoders });
  const child = spawn(FFMPEG, plan.args, { stdio: ["ignore", "pipe", "pipe"] });
  return { child, plan };
}
