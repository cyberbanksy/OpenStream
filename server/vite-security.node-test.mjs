import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { once } from "node:events";
import { mkdtemp, rm } from "node:fs/promises";
import http from "node:http";
import { tmpdir } from "node:os";
import path from "node:path";
import test from "node:test";

function listen(server) {
  return new Promise((resolve, reject) => {
    server.once("error", reject);
    server.listen(0, "127.0.0.1", () => resolve(server.address().port));
  });
}

function close(server) {
  if (!server.listening) return Promise.resolve();
  return new Promise((resolve, reject) => {
    server.close((error) => error ? reject(error) : resolve());
  });
}

async function unusedPort() {
  const server = http.createServer();
  const port = await listen(server);
  await close(server);
  return port;
}

function request(port, requestPath, { method = "GET", headers = {}, body } = {}) {
  const encodedBody = body === undefined ? undefined : Buffer.from(JSON.stringify(body));
  return new Promise((resolve, reject) => {
    const outgoing = http.request({
      hostname: "127.0.0.1",
      port,
      path: requestPath,
      method,
      headers: {
        Accept: "application/json",
        Connection: "close",
        ...headers,
        ...(encodedBody ? { "Content-Type": "application/json", "Content-Length": encodedBody.length } : {}),
      },
    }, (response) => {
      const chunks = [];
      response.on("data", (chunk) => chunks.push(chunk));
      response.on("end", () => {
        const text = Buffer.concat(chunks).toString("utf8");
        const isJson = response.headers["content-type"]?.includes("application/json");
        resolve({
          status: response.statusCode,
          headers: response.headers,
          body: text && isJson ? JSON.parse(text) : text,
        });
      });
    });
    outgoing.on("error", reject);
    if (encodedBody) outgoing.write(encodedBody);
    outgoing.end();
  });
}

function launchUrlFrom(output) {
  const plain = output.replace(/\u001b\[[0-9;]*m/g, "");
  const match = plain.match(/OpenStream secure launch:\s+(https?:\/\/\S+)/);
  return match ? new URL(match[1]) : undefined;
}

async function waitForVite(port, child, output) {
  for (let attempt = 0; attempt < 100; attempt += 1) {
    if (child.exitCode !== null) throw new Error(`Vite exited before startup.\n${output()}`);
    const launchUrl = launchUrlFrom(output());
    if (launchUrl) {
      try {
        const response = await request(port, "/");
        if (response.status) return launchUrl;
      } catch {
        // Startup polling is bounded below.
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
  throw new Error(`Vite did not become ready.\n${output()}`);
}

async function stopChild(child) {
  if (!child || child.exitCode !== null) return;
  const exited = once(child, "exit");
  child.kill("SIGTERM");
  await Promise.race([exited, new Promise((resolve) => setTimeout(resolve, 2000))]);
  if (child.exitCode === null) child.kill("SIGKILL");
}

test("Vite rejects raw/canonical gateway disagreement before adding gateway authority", async () => {
  const projectRoot = process.cwd();
  const vitePort = await unusedPort();
  const gatewayPort = await unusedPort();
  const cacheRoot = await mkdtemp(path.join(tmpdir(), "openstream-vite-security-"));
  let child;
  let output = "";

  try {
    child = spawn(path.join(projectRoot, "node_modules", ".bin", "vite"), [
      "--configLoader",
      "runner",
      "--host",
      "127.0.0.1",
      "--port",
      String(vitePort),
      "--strictPort",
    ], {
      cwd: projectRoot,
      env: {
        ...process.env,
        NO_COLOR: "1",
        OPENSTREAM_ALLOWED_ORIGINS: `http://127.0.0.1:${vitePort}`,
        OPENSTREAM_GATEWAY_PORT: String(gatewayPort),
        OPENSTREAM_VITE_CACHE_DIR: path.join(cacheRoot, "vite-cache"),
      },
      stdio: ["ignore", "pipe", "pipe"],
    });
    child.stdout.on("data", (chunk) => { output += chunk.toString("utf8"); });
    child.stderr.on("data", (chunk) => { output += chunk.toString("utf8"); });

    const launchUrl = await waitForVite(vitePort, child, () => output);
    assert.equal(launchUrl.hostname, "127.0.0.1");
    assert.equal(launchUrl.port, String(vitePort));
    const launch = await request(vitePort, `${launchUrl.pathname}${launchUrl.search}`);
    assert.equal(launch.status, 200);
    assert.equal(launch.headers["set-cookie"], undefined, "the launch must not create a host-wide cookie");
    assert.match(launch.headers["content-security-policy"], /frame-ancestors 'none'/);
    assert.equal(launch.headers["x-frame-options"], "DENY");
    const browserToken = launch.body.match(/sessionStorage\.setItem\("openstream\.browserSession","([A-Za-z0-9_-]+)"\)/)?.[1];
    assert.ok(browserToken, "the launch document should deliver an origin-scoped browser proof");

    for (const documentPath of ["/", "/details/northstar"]) {
      const document = await request(vitePort, documentPath, { headers: { Accept: "text/html" } });
      assert.equal(document.status, 200, documentPath);
      assert.match(document.headers["content-security-policy"], /frame-ancestors 'none'/, documentPath);
      assert.equal(document.headers["x-frame-options"], "DENY", documentPath);
    }

    const denied = await request(vitePort, "/gateway/health");
    assert.equal(denied.status, 401);
    const allowed = await request(vitePort, "/gateway/health", {
      headers: { "X-OpenStream-Browser-Session": browserToken },
    });
    assert.equal(allowed.status, 200);
    assert.equal(allowed.body.ok, true);

    const browserHeaders = {
      Origin: `http://127.0.0.1:${vitePort}`,
      "X-OpenStream-Browser-Session": browserToken,
      "X-OpenStream-Request": "1",
    };
    const signedSource = "https://media.example/movie.mp4?token=secret-marker#private-marker";
    const handoff = await request(vitePort, "/gateway/xr/handoffs", {
      method: "POST",
      headers: browserHeaders,
      body: { source: signedSource, projection: "180", stereo: "sbs", dynamicRange: "Dolby Vision" },
    });
    assert.equal(handoff.status, 201);
    assert.match(handoff.body.viewerUrl, /^\/xr\/viewer#handoff=/);
    assert.equal(handoff.body.viewerUrl.includes("secret-marker"), false);
    const viewerUrl = new URL(handoff.body.viewerUrl, `http://127.0.0.1:${vitePort}`);
    const viewerDocument = await request(vitePort, viewerUrl.pathname, { headers: { Accept: "text/html" } });
    assert.equal(viewerDocument.status, 200);
    assert.equal(viewerDocument.body.includes("secret-marker"), false);
    const handoffId = viewerUrl.hash.slice("#handoff=".length);
    const redemption = await request(vitePort, "/gateway/xr/handoffs/redeem", {
      method: "POST",
      headers: browserHeaders,
      body: { handoffId },
    });
    assert.equal(redemption.status, 200);
    assert.equal(redemption.body.source, signedSource);
    const replay = await request(vitePort, "/gateway/xr/handoffs/redeem", {
      method: "POST",
      headers: browserHeaders,
      body: { handoffId },
    });
    assert.equal(replay.status, 400);

    const operations = [
      { method: "GET", path: "/api/connections" },
      { method: "POST", path: "/api/connections" },
      { method: "GET", path: "/api/arr/options" },
      { method: "POST", path: "/api/arr/request" },
      { method: "POST", path: "/api/usenet/handoff" },
      { method: "POST", path: "/api/stremio/streams" },
      { method: "GET", path: "/api/stremio/media" },
      { method: "GET", path: "/api/google-drive/files" },
      { method: "GET", path: "/api/google-drive/media" },
    ];
    for (const operation of operations) {
      const rawPath = `/gateway/..${operation.path}`;
      const result = await request(vitePort, rawPath, { method: operation.method });
      assert.equal(result.status, 400, `${operation.method} ${rawPath}`);
      assert.match(result.body.error, /canonical \/gateway route/i, rawPath);
    }

    for (const rawPath of [
      "/gateway/%2e%2e/api/connections",
      "/gateway//connections",
      "/gateway\\connections",
    ]) {
      const result = await request(vitePort, rawPath);
      assert.equal(result.status, 400, rawPath);
      assert.match(result.body.error, /canonical \/gateway route/i, rawPath);
    }
  } finally {
    await stopChild(child);
    await rm(cacheRoot, { recursive: true, force: true });
  }
});
