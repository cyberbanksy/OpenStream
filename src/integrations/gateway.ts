import { z } from "zod";
import { type ConnectionKind, connectionKinds, type ConnectionSummary } from "./connection";
import type { MediaItem } from "../domain/media";

/** Base path the app and the loopback gateway share for proxied requests. */
export const GATEWAY_BASE_PATH = "/gateway";

const connectionSchema = z.object({
  id: z.string(),
  kind: z.enum(connectionKinds),
  name: z.string(),
  detail: z.string(),
  state: z.enum(["connected", "attention", "disconnected", "checking"]),
  lastChecked: z.string().optional(),
  capabilities: z.array(z.string()),
  managed: z.boolean().optional(),
});

const gatewayResponseSchema = z.object({
  connection: connectionSchema,
  authorizationUrl: z.string().url().optional(),
});

const MAX_ARR_OPTIONS_PER_KIND = 128;
const arrOptionsSchema = z.object({
  profiles: z.array(z.object({
    id: z.number().int().nonnegative(),
    name: z.string().trim().min(1).max(256),
  })).max(MAX_ARR_OPTIONS_PER_KIND),
  roots: z.array(z.object({
    id: z.number().int().nonnegative().optional(),
    path: z.string().trim().min(1).max(2048),
    freeSpace: z.number().finite().nonnegative().optional(),
  })).max(MAX_ARR_OPTIONS_PER_KIND),
});

const xrHandoffSchema = z.object({
  viewerUrl: z.string(),
});

const xrRedemptionSchema = z.object({
  source: z.string(),
  projection: z.enum(["180", "360", "wide"]),
  stereo: z.enum(["mono", "sbs", "tb"]),
  dynamicRange: z.string(),
});

const BROWSER_SESSION_STORAGE_KEY = "openstream.browserSession";
const BROWSER_SESSION_HEADER = "X-OpenStream-Browser-Session";

export type GatewayConnectionDraft = {
  kind: ConnectionKind;
  baseUrl?: string;
  credential: string;
  username?: string;
  allowPrivateNetwork?: boolean;
};

export type ArrOptions = z.infer<typeof arrOptionsSchema>;

export function parseArrOptions(value: unknown): ArrOptions {
  return arrOptionsSchema.parse(value);
}

async function gatewayJson<T>(path: string, init: RequestInit = {}): Promise<T> {
  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort(), 15_000);
  try {
    const browserSession = window.sessionStorage.getItem(BROWSER_SESSION_STORAGE_KEY);
    if (!browserSession) throw new Error("Open this app with the secure launch URL from the OpenStream host.");
    const response = await fetch(`${GATEWAY_BASE_PATH}${path}`, {
      ...init,
      signal: controller.signal,
      credentials: "omit",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
        [BROWSER_SESSION_HEADER]: browserSession,
        "X-OpenStream-Request": "1",
        ...init.headers,
      },
    });
    let payload: { error?: string };
    try {
      payload = await response.json() as { error?: string };
    } catch {
      // A proxy in front of the gateway may answer with an HTML error page.
      throw new Error(response.ok
        ? "The secure gateway returned an unreadable response."
        : `Secure gateway request failed (${response.status}).`);
    }
    if (!response.ok) throw new Error(payload.error || `Secure gateway request failed (${response.status}).`);
    return payload as T;
  } catch (error) {
    if (error instanceof DOMException && error.name === "AbortError") throw new Error("The secure gateway timed out.");
    if (error instanceof TypeError) throw new Error("The secure gateway is unavailable. Restart the OpenStream host and use its secure launch URL.");
    throw error;
  } finally {
    window.clearTimeout(timeout);
  }
}

export async function connectThroughGateway(draft: GatewayConnectionDraft) {
  const result = gatewayResponseSchema.parse(await gatewayJson<unknown>("/connections", {
    method: "POST",
    body: JSON.stringify({ ...draft, appOrigin: window.location.origin }),
  }));
  return {
    connection: result.connection satisfies ConnectionSummary,
    authorizationUrl: result.authorizationUrl,
  };
}

export async function listGatewayConnections(): Promise<ConnectionSummary[]> {
  const payload = await gatewayJson<{ connections?: unknown[] }>("/connections");
  return z.array(connectionSchema).parse(payload.connections ?? []);
}

export async function getArrOptions(connectionId: string) {
  const query = new URLSearchParams({ connectionId });
  return parseArrOptions(await gatewayJson<unknown>(`/arr/options?${query}`));
}

export function sendToArrGateway(
  connectionId: string,
  item: MediaItem,
  qualityProfileId: number,
  rootFolderPath: string,
  monitored = true,
) {
  return gatewayJson<{ result: unknown }>("/arr/request", {
    method: "POST",
    body: JSON.stringify({
      connectionId,
      request: {
        title: item.title,
        year: item.year,
        imdbId: item.externalIds.imdb,
        tmdbId: item.externalIds.tmdb,
        tvdbId: item.externalIds.tvdb,
        qualityProfileId,
        rootFolderPath,
        monitored,
      },
    }),
  });
}

const transcoderCapabilitiesSchema = z.object({
  available: z.boolean(),
  version: z.string().optional(),
  hardwareAccelerated: z.boolean().optional(),
  encoders: z.object({
    h264: z.string().optional(),
    hevc: z.string().optional(),
    aac: z.string().optional(),
  }).optional(),
});

export type TranscoderCapabilities = z.infer<typeof transcoderCapabilitiesSchema>;

export async function getTranscoderCapabilities() {
  return transcoderCapabilitiesSchema.parse(await gatewayJson<unknown>("/transcode/capabilities"));
}

export type TranscodeProfile = {
  maxWidth: number;
  maxHeight: number;
  videoCodec?: "h264" | "hevc";
};

export async function createTranscodeSession(mediaUrl: string, profile: TranscodeProfile) {
  const result = await gatewayJson<{ transcodeUrl: string }>("/transcode/sessions", {
    method: "POST",
    body: JSON.stringify({ mediaUrl, profile }),
  });
  return result.transcodeUrl;
}

const nasListingSchema = z.object({
  path: z.string(),
  directories: z.array(z.object({ name: z.string(), path: z.string() })),
  files: z.array(z.object({ name: z.string(), path: z.string(), size: z.number().optional() })),
});

export type NasDirectoryListing = z.infer<typeof nasListingSchema>;

export async function browseNasDirectory(connectionId: string, path = "") {
  const query = new URLSearchParams({ connectionId, path });
  return nasListingSchema.parse(await gatewayJson<unknown>(`/nas/browse?${query}`));
}

export async function createNasMediaCapability(connectionId: string, path: string) {
  const result = await gatewayJson<{ mediaUrl: string }>("/nas/media-capability", {
    method: "POST",
    body: JSON.stringify({ connectionId, path }),
  });
  return result.mediaUrl;
}

export type ClientLogEntry = {
  level: "info" | "warn" | "error";
  message: string;
  context?: Record<string, string>;
};

/** Fire-and-forget log shipping; failures must never affect the UI. */
export async function postClientLogs(entries: ClientLogEntry[], options: { keepalive?: boolean } = {}) {
  try {
    await gatewayJson("/logs", {
      method: "POST",
      body: JSON.stringify({ entries }),
      keepalive: options.keepalive,
    });
  } catch {
    // The gateway may be absent in production builds; logs stay client-side.
  }
}

export type XrHandoffDraft = {
  source: string;
  projection: "180" | "360" | "wide";
  stereo: "mono" | "sbs" | "tb";
  dynamicRange: string;
};

export async function createXrViewerHandoff(draft: XrHandoffDraft) {
  return xrHandoffSchema.parse(await gatewayJson<unknown>("/xr/handoffs", {
    method: "POST",
    body: JSON.stringify(draft),
  })).viewerUrl;
}

export async function redeemXrViewerHandoff(handoffId: string) {
  return xrRedemptionSchema.parse(await gatewayJson<unknown>("/xr/handoffs/redeem", {
    method: "POST",
    body: JSON.stringify({ handoffId }),
  }));
}
