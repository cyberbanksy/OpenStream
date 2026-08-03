export type ConnectionKind =
  | "google-drive"
  | "stremio"
  | "sonarr"
  | "radarr"
  | "sabnzbd"
  | "nzbget"
  | "smb"
  | "jellyfin"
  | "plex";

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
