import assert from "node:assert/strict";
import test from "node:test";
import {
  BROWSER_SESSION_HEADER,
  createDevSessionBoundary,
  rewriteGatewayRequestTarget,
} from "./dev-session.mjs";

function responseRecorder() {
  const headers = new Map();
  return {
    body: "",
    headers,
    statusCode: 200,
    setHeader(name, value) {
      headers.set(name.toLowerCase(), value);
    },
    end(body = "") {
      this.body = body;
    },
  };
}

function runMiddleware(boundary, { url, method = "GET", browserSession } = {}) {
  const request = {
    method,
    url,
    headers: { ...(browserSession ? { [BROWSER_SESSION_HEADER]: browserSession } : {}) },
  };
  const response = responseRecorder();
  let nextCalled = false;
  boundary.middleware(request, response, () => { nextCalled = true; });
  return { response, nextCalled };
}

test("dev session redeems a one-time launch into an origin-scoped browser proof", () => {
  const boundary = createDevSessionBoundary({
    launchToken: "launch-capability-with-at-least-thirty-two-characters",
    browserToken: "browser-session-with-at-least-thirty-two-characters",
  });

  const denied = runMiddleware(boundary, { url: "/gateway/connections" });
  assert.equal(denied.nextCalled, false);
  assert.equal(denied.response.statusCode, 401);
  assert.match(denied.response.body, /secure launch URL/i);

  const prefixVariant = runMiddleware(boundary, { url: "/gateway-shadow" });
  assert.equal(prefixVariant.response.statusCode, 400);

  const forged = runMiddleware(boundary, {
    url: "/gateway/connections",
    browserSession: "forged-browser-session-token",
  });
  assert.equal(forged.response.statusCode, 401);

  const launched = runMiddleware(boundary, { url: boundary.launchPath });
  assert.equal(launched.response.statusCode, 200);
  assert.equal(launched.response.headers.get("referrer-policy"), "no-referrer");
  assert.equal(launched.response.headers.has("set-cookie"), false);
  assert.match(launched.response.headers.get("content-security-policy"), /script-src 'nonce-/);
  assert.match(launched.response.headers.get("content-security-policy"), /frame-ancestors 'none'/);
  assert.equal(launched.response.headers.get("x-frame-options"), "DENY");
  assert.match(launched.response.body, /sessionStorage\.setItem/);
  assert.equal(launched.response.body.includes(boundary.browserToken), true);
  assert.equal(launched.response.body.includes(boundary.launchToken), false);

  const replay = runMiddleware(boundary, { url: boundary.launchPath });
  assert.equal(replay.response.statusCode, 410);

  const authorized = runMiddleware(boundary, {
    url: "/gateway/connections",
    browserSession: boundary.browserToken,
  });
  assert.equal(authorized.nextCalled, true);
  assert.equal(authorized.response.body, "");

  const capabilityMedia = runMiddleware(boundary, { url: "/gateway/stremio/media?streamId=opaque" });
  assert.equal(capabilityMedia.nextCalled, true);
});

test("dev session rejects malformed launch attempts without consuming the capability", () => {
  const boundary = createDevSessionBoundary({
    launchToken: "launch-capability-with-at-least-thirty-two-characters",
    browserToken: "browser-session-with-at-least-thirty-two-characters",
  });

  const malformed = runMiddleware(boundary, { url: "/__openstream/session?launch=wrong" });
  assert.equal(malformed.response.statusCode, 401);

  const launched = runMiddleware(boundary, { url: boundary.launchPath });
  assert.equal(launched.response.statusCode, 200);
});

test("dev session rejects values that could escape a cookie or header", () => {
  assert.throws(() => createDevSessionBoundary({
    launchToken: `${"a".repeat(32)};Path=/`,
    browserToken: "browser-session-with-at-least-thirty-two-characters",
  }), /base64url/i);
});

test("dev session and proxy reject ambiguous gateway request targets", () => {
  const boundary = createDevSessionBoundary({
    launchToken: "launch-capability-with-at-least-thirty-two-characters",
    browserToken: "browser-session-with-at-least-thirty-two-characters",
  });
  for (const url of [
    "/gateway/../api/connections",
    "/gateway/%2e%2e/api/connections",
    "/gateway//connections",
    "/gateway\\connections",
    "http://127.0.0.1:5173/gateway/connections",
  ]) {
    const result = runMiddleware(boundary, { url, browserSession: boundary.browserToken });
    assert.equal(result.nextCalled, false, url);
    assert.equal(result.response.statusCode, 400, url);
  }

  assert.equal(
    rewriteGatewayRequestTarget("/gateway/arr/options?connectionId=abc"),
    "/api/arr/options?connectionId=abc",
  );
  assert.throws(() => rewriteGatewayRequestTarget("/gateway/../api/connections"), /canonical/i);
});
