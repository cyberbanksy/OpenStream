import { z } from "zod";

const resourceSchema = z.union([
  z.enum(["catalog", "meta", "stream", "subtitles", "addon_catalog"]),
  z.object({
    name: z.enum(["catalog", "meta", "stream", "subtitles", "addon_catalog"]),
    types: z.array(z.string()).optional(),
    idPrefixes: z.array(z.string()).optional(),
  }),
]);

export const stremioManifestSchema = z.object({
  id: z.string().min(1),
  version: z.string().min(1),
  name: z.string().min(1),
  description: z.string().optional(),
  logo: z.string().url().optional(),
  types: z.array(z.string()),
  resources: z.array(resourceSchema),
  catalogs: z.array(
    z.object({
      type: z.string(),
      id: z.string(),
      name: z.string().optional(),
      extra: z.array(z.object({ name: z.string(), isRequired: z.boolean().optional() })).optional(),
    }),
  ).optional(),
  idPrefixes: z.array(z.string()).optional(),
});

export type StremioManifest = z.infer<typeof stremioManifestSchema>;

export const stremioStreamSchema = z.object({
  name: z.string().optional(),
  title: z.string().optional(),
  description: z.string().optional(),
  url: z.string().url().optional(),
  externalUrl: z.string().url().optional(),
  ytId: z.string().optional(),
  infoHash: z.string().optional(),
  fileIdx: z.number().int().nonnegative().optional(),
  behaviorHints: z.record(z.string(), z.unknown()).optional(),
}).refine(
  (stream) => Boolean(stream.url || stream.externalUrl || stream.ytId || stream.infoHash),
  "A Stremio stream must include a supported locator.",
);

export type StremioStream = z.infer<typeof stremioStreamSchema>;

export function buildStremioResourceUrl(
  transportUrl: string,
  resource: "catalog" | "meta" | "stream" | "subtitles",
  type: string,
  id: string,
  extra?: Record<string, string | number>,
) {
  const path = [resource, encodeURIComponent(type), encodeURIComponent(id)];
  if (extra && Object.keys(extra).length > 0) {
    path.push(new URLSearchParams(Object.entries(extra).map(([key, value]) => [key, String(value)])).toString());
  }
  return `${transportUrl}/${path.join("/")}.json`;
}
