import { createHash, randomBytes, timingSafeEqual } from "node:crypto";
import { parseCanonicalRequestTarget } from "./security.mjs";

const LAUNCH_PATH = "/__openstream/session";
const MIN_TOKEN_LENGTH = 32;
const DEV_ORIGIN = "http://127.0.0.1";
const CAPABILITY_MEDIA_PATHS = new Set([
  "/gateway/google-drive/media",
  "/gateway/stremio/media",
]);

export const BROWSER_SESSION_HEADER = "x-openstream-browser-session";
export const BROWSER_SESSION_STORAGE_KEY = "openstream.browserSession";

export function randomSessionToken() {
  return randomBytes(32).toString("base64url");
}

function assertSessionToken(value, label) {
  if (typeof value !== "string" || value.length < MIN_TOKEN_LENGTH || value.length > 512 || !/^[A-Za-z0-9_-]+$/.test(value)) {
    throw new Error(`${label} must be a high-entropy base64url token.`);
  }
  return value;
}

function tokenDigest(value) {
  return createHash("sha256").update(value).digest();
}

export function sessionTokenMatches(value, expectedDigest) {
  if (typeof value !== "string" || value.length > 512) return false;
  return timingSafeEqual(tokenDigest(value), expectedDigest);
}

function reject(response, statusCode, message) {
  response.statusCode = statusCode;
  response.setHeader("Cache-Control", "no-store");
  response.setHeader("Content-Type", "application/json; charset=utf-8");
  response.setHeader("Cross-Origin-Resource-Policy", "same-origin");
  response.setHeader("X-Content-Type-Options", "nosniff");
  response.end(JSON.stringify({ error: message }));
}

function launchDocument(browserToken) {
  const nonce = randomBytes(18).toString("base64url");
  const script = `sessionStorage.setItem(${JSON.stringify(BROWSER_SESSION_STORAGE_KEY)},${JSON.stringify(browserToken)});location.replace("/");`;
  return {
    nonce,
    html: `<!doctype html><html><head><meta charset="utf-8"><meta name="referrer" content="no-referrer"><title>Opening OpenStream</title></head><body><script nonce="${nonce}">${script}</script></body></html>`,
  };
}

export function rewriteGatewayRequestTarget(value) {
  const requestUrl = parseCanonicalRequestTarget(value, { origin: DEV_ORIGIN, prefix: "/gateway" });
  if (!requestUrl) throw new Error("Gateway proxy received an unrelated request target.");
  return `/api${requestUrl.pathname.slice("/gateway".length)}${requestUrl.search}`;
}

export function createDevSessionBoundary({
  launchToken = randomSessionToken(),
  browserToken = randomSessionToken(),
} = {}) {
  assertSessionToken(launchToken, "Launch capability");
  assertSessionToken(browserToken, "Browser session");
  const launchDigest = tokenDigest(launchToken);
  const browserDigest = tokenDigest(browserToken);
  let launchConsumed = false;

  return {
    launchToken,
    browserToken,
    launchPath: `${LAUNCH_PATH}?launch=${encodeURIComponent(launchToken)}`,
    middleware(request, response, next) {
      let launchUrl;
      let gatewayUrl;
      try {
        launchUrl = parseCanonicalRequestTarget(request.url ?? "/", { origin: DEV_ORIGIN, prefix: LAUNCH_PATH });
        gatewayUrl = parseCanonicalRequestTarget(request.url ?? "/", { origin: DEV_ORIGIN, prefix: "/gateway" });
      } catch (error) {
        reject(response, 400, error.message);
        return;
      }

      if (launchUrl) {
        if (request.method !== "GET") {
          reject(response, 405, "The secure launch endpoint only accepts GET requests.");
          return;
        }
        if (launchConsumed) {
          reject(response, 410, "The secure launch capability has already been used. Restart the development server to issue another.");
          return;
        }
        if (!sessionTokenMatches(launchUrl.searchParams.get("launch"), launchDigest)) {
          reject(response, 401, "The secure launch capability is invalid.");
          return;
        }
        launchConsumed = true;
        const document = launchDocument(browserToken);
        response.statusCode = 200;
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Content-Security-Policy", `default-src 'none'; script-src 'nonce-${document.nonce}'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'`);
        response.setHeader("Content-Type", "text/html; charset=utf-8");
        response.setHeader("Cross-Origin-Resource-Policy", "same-origin");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.end(document.html);
        return;
      }

      if (gatewayUrl) {
        const capabilityMediaRequest = request.method === "GET" && CAPABILITY_MEDIA_PATHS.has(gatewayUrl.pathname);
        const supplied = request.headers?.[BROWSER_SESSION_HEADER];
        if (capabilityMediaRequest) {
          next();
          return;
        }
        if (!sessionTokenMatches(supplied, browserDigest)) {
          reject(response, 401, "Open this app with the secure launch URL printed by pnpm dev.");
          return;
        }
      }
      next();
    },
  };
}
