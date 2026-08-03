import { z } from "zod";
import { normalizeServiceUrl, requestJson } from "./http";

export type ArrKind = "sonarr" | "radarr";

export type ArrConfig = {
  kind: ArrKind;
  baseUrl: string;
  apiKey: string;
  allowPrivateNetwork: boolean;
};

const statusSchema = z.object({
  appName: z.string().optional(),
  version: z.string(),
  instanceName: z.string().optional(),
  isProduction: z.boolean().optional(),
});

const lookupSchema = z.object({
  title: z.string(),
  year: z.number().optional(),
  tmdbId: z.number().optional(),
  tvdbId: z.number().optional(),
  imdbId: z.string().optional(),
  images: z.array(z.object({ coverType: z.string(), remoteUrl: z.string().optional(), url: z.string().optional() })).optional(),
});

export type ArrLookupResult = z.infer<typeof lookupSchema>;

function endpoint(config: ArrConfig, path: string) {
  const base = normalizeServiceUrl(config.baseUrl, {
    allowPrivateNetwork: config.allowPrivateNetwork,
    allowHttpLoopback: config.allowPrivateNetwork,
  });
  return `${base}/api/v3${path}`;
}

function headers(config: ArrConfig) {
  if (!config.apiKey.trim()) throw new Error("An ARR API key is required.");
  return { "X-Api-Key": config.apiKey.trim(), "Content-Type": "application/json" };
}

export async function checkArrConnection(config: ArrConfig) {
  return statusSchema.parse(
    await requestJson<unknown>(endpoint(config, "/system/status"), { headers: headers(config) }),
  );
}

export async function lookupArrTitle(config: ArrConfig, term: string) {
  const resource = config.kind === "radarr" ? "/movie/lookup" : "/series/lookup";
  const url = new URL(endpoint(config, resource));
  url.searchParams.set("term", term);
  return z.array(lookupSchema).parse(await requestJson<unknown>(url.toString(), { headers: headers(config) }));
}

export type ArrDownloadRequest = {
  title: string;
  year: number;
  imdbId?: string;
  tmdbId?: number;
  tvdbId?: number;
  qualityProfileId: number;
  rootFolderPath: string;
};

export async function sendToArr(config: ArrConfig, request: ArrDownloadRequest) {
  if (config.kind === "radarr") {
    return requestJson<unknown>(endpoint(config, "/movie"), {
      method: "POST",
      headers: headers(config),
      body: JSON.stringify({
        title: request.title,
        year: request.year,
        tmdbId: request.tmdbId,
        imdbId: request.imdbId,
        qualityProfileId: request.qualityProfileId,
        rootFolderPath: request.rootFolderPath,
        monitored: true,
        minimumAvailability: "released",
        addOptions: { searchForMovie: true },
      }),
    });
  }

  return requestJson<unknown>(endpoint(config, "/series"), {
    method: "POST",
    headers: headers(config),
    body: JSON.stringify({
      title: request.title,
      year: request.year,
      tvdbId: request.tvdbId,
      imdbId: request.imdbId,
      qualityProfileId: request.qualityProfileId,
      rootFolderPath: request.rootFolderPath,
      monitored: true,
      seasonFolder: true,
      addOptions: { searchForMissingEpisodes: true, monitor: "all" },
    }),
  });
}
