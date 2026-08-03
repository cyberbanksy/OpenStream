import { z } from "zod";
import { requestJson } from "./http";

const DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file";

export type GoogleDriveOAuthConfig = {
  clientId: string;
  redirectUri: string;
};

export type GoogleDriveAuthRequest = {
  authorizationUrl: string;
  codeVerifier: string;
  state: string;
};

const driveFileSchema = z.object({
  id: z.string(),
  name: z.string(),
  mimeType: z.string(),
  size: z.string().optional(),
  modifiedTime: z.string().optional(),
  thumbnailLink: z.string().url().optional(),
  webContentLink: z.string().url().optional(),
  videoMediaMetadata: z.object({ width: z.number().optional(), height: z.number().optional(), durationMillis: z.string().optional() }).optional(),
});

export type GoogleDriveFile = z.infer<typeof driveFileSchema>;

function base64Url(bytes: Uint8Array) {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function randomToken(length = 48) {
  return base64Url(crypto.getRandomValues(new Uint8Array(length)));
}

export async function createGoogleDriveAuthRequest(config: GoogleDriveOAuthConfig): Promise<GoogleDriveAuthRequest> {
  if (!config.clientId.trim()) throw new Error("A Google OAuth client ID is required.");
  const codeVerifier = randomToken();
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(codeVerifier));
  const state = randomToken(24);
  const url = new URL("https://accounts.google.com/o/oauth2/v2/auth");
  url.searchParams.set("client_id", config.clientId.trim());
  url.searchParams.set("redirect_uri", config.redirectUri);
  url.searchParams.set("response_type", "code");
  url.searchParams.set("scope", DRIVE_FILE_SCOPE);
  url.searchParams.set("code_challenge", base64Url(new Uint8Array(digest)));
  url.searchParams.set("code_challenge_method", "S256");
  url.searchParams.set("state", state);
  url.searchParams.set("access_type", "offline");
  url.searchParams.set("prompt", "consent");
  return { authorizationUrl: url.toString(), codeVerifier, state };
}

export async function listGoogleDriveVideos(accessToken: string, pageToken?: string) {
  const url = new URL("https://www.googleapis.com/drive/v3/files");
  url.searchParams.set("q", "trashed = false and mimeType contains 'video/'");
  url.searchParams.set(
    "fields",
    "nextPageToken,files(id,name,mimeType,size,modifiedTime,thumbnailLink,webContentLink,videoMediaMetadata)",
  );
  url.searchParams.set("pageSize", "100");
  url.searchParams.set("orderBy", "modifiedTime desc");
  if (pageToken) url.searchParams.set("pageToken", pageToken);

  const payload = await requestJson<{ nextPageToken?: string; files?: unknown[] }>(url.toString(), {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  return {
    nextPageToken: payload.nextPageToken,
    files: z.array(driveFileSchema).parse(payload.files ?? []),
  };
}

export function googleDriveMediaUrl(fileId: string) {
  return `https://www.googleapis.com/drive/v3/files/${encodeURIComponent(fileId)}?alt=media`;
}
