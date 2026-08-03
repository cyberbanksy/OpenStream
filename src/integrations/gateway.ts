import { z } from "zod";
import type { ConnectionKind, ConnectionSummary } from "./connection";
import type { MediaItem } from "../domain/media";

const connectionSchema = z.object({
  id: z.string(),
  kind: z.string(),
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
    if (!browserSession) throw new Error("Open this app with the secure launch URL printed by pnpm dev.");
    const response = await fetch(`/gateway${path}`, {
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
    const payload = await response.json() as { error?: string };
    if (!response.ok) throw new Error(payload.error || `Secure gateway request failed (${response.status}).`);
    return payload as T;
  } catch (error) {
    if (error instanceof DOMException && error.name === "AbortError") throw new Error("The secure gateway timed out.");
    if (error instanceof TypeError) throw new Error("The secure gateway is unavailable. Restart pnpm dev and use its secure launch URL.");
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
    connection: result.connection as ConnectionSummary,
    authorizationUrl: result.authorizationUrl,
  };
}

export async function listGatewayConnections() {
  const payload = await gatewayJson<{ connections?: unknown[] }>("/connections");
  return z.array(connectionSchema).parse(payload.connections ?? []) as ConnectionSummary[];
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

export function handoffNzb(connectionId: string, nzbUrl: string, category?: string) {
  return gatewayJson<{ result: unknown }>("/usenet/handoff", {
    method: "POST",
    body: JSON.stringify({ connectionId, nzbUrl, category }),
  });
}

export function loadGatewayStremioStreams(connectionId: string, type: string, id: string) {
  return gatewayJson<{ streams: unknown[] }>("/stremio/streams", {
    method: "POST",
    body: JSON.stringify({ connectionId, type, id }),
  });
}

export async function googleDriveGatewayMediaUrl(connectionId: string, fileId: string) {
  const result = await gatewayJson<{ mediaUrl: string }>("/google-drive/media-capability", {
    method: "POST",
    body: JSON.stringify({ connectionId, fileId }),
  });
  return result.mediaUrl;
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
