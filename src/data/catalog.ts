import type { MediaItem } from "../domain/media";

/**
 * Production starts empty. Catalog entries arrive from sources the viewer
 * configures; product examples and requested channels never ship as content.
 */
export const catalog: MediaItem[] = [];

export const catalogById = new Map(catalog.map((item) => [item.id, item]));
