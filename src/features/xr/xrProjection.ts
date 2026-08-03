export type XrProjection = "180" | "360" | "wide";
export type XrStereoMode = "mono" | "sbs" | "tb";
export type XrEye = "left" | "right";

export type XrViewerOptions = {
  projection: XrProjection;
  stereo: XrStereoMode;
  dynamicRange: string;
  source: string;
  handoffId?: string;
  rejectedLegacySource: boolean;
};

const defaultSource = "/demo/openstream-demo.mp4";

export function parseXrViewerOptions(location: Pick<Location, "href" | "origin">): XrViewerOptions {
  const url = new URL(location.href);
  const fragment = new URLSearchParams(url.hash.replace(/^#/, ""));
  return {
    projection: parseProjection(url.searchParams.get("projection")),
    stereo: parseStereo(url.searchParams.get("stereo")),
    dynamicRange: cleanLabel(url.searchParams.get("range"), "runtime check"),
    source: resolveMediaSource(null, location.origin),
    handoffId: parseHandoffId(fragment.get("handoff")),
    rejectedLegacySource: url.searchParams.has("src"),
  };
}

export function resolveMediaSource(raw: string | null, origin: string): string {
  const source = raw?.trim() || defaultSource;
  const url = new URL(source, origin);
  const sameOrigin = url.origin === origin;
  const loopback = url.hostname === "127.0.0.1" || url.hostname === "localhost" || url.hostname === "[::1]";
  if (!sameOrigin && url.protocol !== "https:" && !(url.protocol === "http:" && loopback)) {
    throw new Error("Media URLs must use HTTPS or a loopback development address.");
  }
  if (url.username || url.password) {
    throw new Error("Credentials are not accepted in media URLs.");
  }
  return url.href;
}

export function cropUvForEye(
  source: Float32Array,
  stereo: XrStereoMode,
  eye: XrEye,
): Float32Array {
  const result = source.slice();
  if (stereo === "mono") return result;
  for (let index = 0; index < result.length; index += 2) {
    if (stereo === "sbs") {
      result[index] = result[index] * 0.5 + (eye === "right" ? 0.5 : 0);
    } else {
      result[index + 1] = result[index + 1] * 0.5 + (eye === "left" ? 0.5 : 0);
    }
  }
  return result;
}

export function projectionArc(projection: XrProjection): { start: number; length: number } {
  const length = projection === "360" ? Math.PI * 2 : projection === "wide" ? Math.PI * 1.35 : Math.PI;
  return {
    start: projection === "360" ? 0 : Math.PI - length / 2,
    length,
  };
}

function parseProjection(value: string | null): XrProjection {
  if (value === "360" || value === "wide") return value;
  return "180";
}

function parseStereo(value: string | null): XrStereoMode {
  if (value === "sbs" || value === "tb") return value;
  return "mono";
}

function cleanLabel(value: string | null, fallback: string): string {
  if (!value) return fallback;
  return value.replace(/[^a-z0-9+ ._-]/gi, "").slice(0, 32) || fallback;
}

function parseHandoffId(value: string | null): string | undefined {
  if (!value || !/^[A-Za-z0-9_-]{32,128}$/.test(value)) return undefined;
  return value;
}
