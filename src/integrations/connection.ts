export const connectionKinds = [
  "google-drive",
  "stremio",
  "sonarr",
  "radarr",
  "sabnzbd",
  "nzbget",
  "nas-folder",
  "smb",
  "jellyfin",
  "plex",
] as const;

export type ConnectionKind = (typeof connectionKinds)[number];

export type ConnectionState = "connected" | "attention" | "disconnected" | "checking";

export type ConnectionSummary = {
  id: string;
  kind: ConnectionKind;
  name: string;
  detail: string;
  state: ConnectionState;
  lastChecked?: string;
  capabilities: string[];
  managed?: boolean;
};

export type SecretField = {
  name: string;
  value: string;
};

export type ConnectionDraft = {
  id: string;
  kind: ConnectionKind;
  name: string;
  baseUrl?: string;
  allowPrivateNetwork?: boolean;
  fields: Record<string, string | boolean>;
  secrets: SecretField[];
};

/**
 * Merges the gateway's fresh managed connections into the current list.
 * Managed entries the gateway no longer reports are dropped, and an unmanaged
 * entry sharing an id with a managed one is removed so ids stay unique.
 */
export function mergeGatewayConnections(
  current: ConnectionSummary[],
  managed: ConnectionSummary[],
): ConnectionSummary[] {
  const managedIds = new Set(managed.map((connection) => connection.id));
  return [
    ...managed,
    ...current.filter((connection) => !connection.managed && !managedIds.has(connection.id)),
  ];
}

/** Field-wise equality so an unchanged poll payload can keep the current state reference. */
export function connectionListsEqual(a: ConnectionSummary[], b: ConnectionSummary[]): boolean {
  if (a.length !== b.length) return false;
  return a.every((connection, index) => {
    const other = b[index];
    return connection.id === other.id &&
      connection.kind === other.kind &&
      connection.name === other.name &&
      connection.detail === other.detail &&
      connection.state === other.state &&
      connection.lastChecked === other.lastChecked &&
      connection.managed === other.managed &&
      connection.capabilities.length === other.capabilities.length &&
      connection.capabilities.every((capability, capabilityIndex) => capability === other.capabilities[capabilityIndex]);
  });
}
