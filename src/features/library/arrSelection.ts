import type { ArrOptions } from "../../integrations/gateway";

export function isCurrentArrSelection(
  connectionId: string,
  loadedConnectionId: string,
  options: ArrOptions,
  qualityProfileId: number,
  rootFolderPath: string,
) {
  if (!connectionId || loadedConnectionId !== connectionId) return false;
  return options.profiles.some((profile) => profile.id === qualityProfileId)
    && options.roots.some((root) => root.path === rootFolderPath);
}
