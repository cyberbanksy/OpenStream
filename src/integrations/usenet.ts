import { z } from "zod";
import { normalizeServiceUrl, requestJson } from "./http";

export type SabnzbdConfig = {
  kind: "sabnzbd";
  baseUrl: string;
  apiKey: string;
  allowPrivateNetwork: boolean;
};

export type NzbGetConfig = {
  kind: "nzbget";
  baseUrl: string;
  username: string;
  password: string;
  allowPrivateNetwork: boolean;
};

const sabAddSchema = z.object({ status: z.boolean(), nzo_ids: z.array(z.string()).optional() });

function normalized(value: string, allowPrivateNetwork: boolean) {
  return normalizeServiceUrl(value, {
    allowPrivateNetwork,
    allowHttpLoopback: allowPrivateNetwork,
  });
}

export async function checkSabnzbd(config: SabnzbdConfig) {
  const url = new URL(`${normalized(config.baseUrl, config.allowPrivateNetwork)}/api`);
  url.searchParams.set("mode", "version");
  url.searchParams.set("output", "json");
  url.searchParams.set("apikey", config.apiKey);
  return requestJson<{ version: string }>(url.toString());
}

export async function addToSabnzbd(
  config: SabnzbdConfig,
  nzbUrl: string,
  category = "*",
) {
  if (new URL(nzbUrl).protocol !== "https:") throw new Error("NZB handoff URLs must use HTTPS.");
  const url = new URL(`${normalized(config.baseUrl, config.allowPrivateNetwork)}/api`);
  url.searchParams.set("mode", "addurl");
  url.searchParams.set("output", "json");
  url.searchParams.set("apikey", config.apiKey);
  url.searchParams.set("name", nzbUrl);
  url.searchParams.set("cat", category);
  return sabAddSchema.parse(await requestJson<unknown>(url.toString()));
}

let rpcId = 0;

export async function callNzbGet<T>(config: NzbGetConfig, method: string, params: unknown[] = []) {
  const token = btoa(`${config.username}:${config.password}`);
  const url = `${normalized(config.baseUrl, config.allowPrivateNetwork)}/jsonrpc`;
  const payload = await requestJson<{ result?: T; error?: { message?: string } }>(url, {
    method: "POST",
    headers: { Authorization: `Basic ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify({ jsonrpc: "2.0", id: ++rpcId, method, params }),
  });
  if (payload.error) throw new Error(payload.error.message ?? "NZBGet rejected the request.");
  return payload.result as T;
}

export function checkNzbGet(config: NzbGetConfig) {
  return callNzbGet<string>(config, "version");
}

export function addToNzbGet(config: NzbGetConfig, nzbUrl: string, category = "") {
  if (new URL(nzbUrl).protocol !== "https:") throw new Error("NZB handoff URLs must use HTTPS.");
  return callNzbGet<number>(config, "append", [
    "",
    nzbUrl,
    category,
    0,
    false,
    false,
    "",
    0,
    "SCORE",
    true,
    [],
  ]);
}
