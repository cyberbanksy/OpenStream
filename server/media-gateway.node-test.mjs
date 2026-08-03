import assert from "node:assert/strict";
import { mkdtemp, mkdir, readFile, readdir, rm, symlink, writeFile } from "node:fs/promises";
import http from "node:http";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { createLogger } from "./logger.mjs";
import { listNasDirectory, resolveWithinRoot, validateNasRoot } from "./nas.mjs";
import { createGatewayServer } from "./openstream-gateway.mjs";
import { buildTranscodeArgs, clampTranscodeProfile, probeTranscoder } from "./transcoder.mjs";

const GATEWAY_SESSION_TOKEN = "test-session-token-that-is-long-enough-for-auth";
const OTHER_GATEWAY_SESSION_TOKEN = "other-session-token-that-is-long-enough-for-auth";

function listen(server) {
  return new Promise((resolve, reject) => {
    server.once("error", reject);
    server.listen(0, "127.0.0.1", () => resolve(server.address().port));
  });
}

function close(server) {
  return new Promise((resolve, reject) => {
    server.close((error) => error ? reject(error) : resolve());
  });
}

function gatewayRequest(port, requestPath, {
  method = "GET",
  body,
  sessionToken = GATEWAY_SESSION_TOKEN,
  range,
} = {}) {
  const encodedBody = body === undefined ? undefined : Buffer.from(JSON.stringify(body));
  const headers = {
    Host: "127.0.0.1:8787",
    Accept: "application/json",
    Origin: "http://127.0.0.1:5173",
    ...(sessionToken ? { "X-OpenStream-Session": sessionToken } : {}),
    ...(range ? { Range: range } : {}),
    ...(encodedBody ? { "Content-Type": "application/json", "Content-Length": encodedBody.length } : {}),
    ...(method === "POST" ? { "X-OpenStream-Request": "1" } : {}),
  };
  return new Promise((resolve, reject) => {
    const request = http.request({ hostname: "127.0.0.1", port, path: requestPath, method, headers }, (response) => {
      const chunks = [];
      response.on("data", (chunk) => chunks.push(chunk));
      response.on("end", () => {
        const raw = Buffer.concat(chunks);
        const isJson = response.headers["content-type"]?.includes("application/json");
        resolve({
          status: response.statusCode,
          headers: response.headers,
          body: isJson && raw.length ? JSON.parse(raw.toString("utf8")) : raw,
        });
      });
    });
    request.on("error", reject);
    if (encodedBody) request.write(encodedBody);
    request.end();
  });
}

async function makeNasFixture() {
  const root = await mkdtemp(path.join(os.tmpdir(), "openstream-nas-"));
  await mkdir(path.join(root, "Movies"));
  await writeFile(path.join(root, "Movies", "Family Trip (2026).mp4"), Buffer.from("0123456789abcdef"));
  await writeFile(path.join(root, "Movies", "notes.txt"), "not a video");
  await writeFile(path.join(root, "clip.mkv"), Buffer.alloc(32, 7));
  return root;
}

test("NAS path validation stays inside the connected root", async () => {
  const root = await validateNasRoot(await makeNasFixture());
  const inside = await resolveWithinRoot(root, "Movies");
  assert.ok(inside.startsWith(root));
  await assert.rejects(resolveWithinRoot(root, "../"), /invalid|escapes|ENOENT/i);
  await assert.rejects(resolveWithinRoot(root, "Movies/../../etc"), /escapes|ENOENT/i);
  await assert.rejects(resolveWithinRoot(root, "a\u0000b"), /invalid/i);

  const outside = await mkdtemp(path.join(os.tmpdir(), "openstream-outside-"));
  await writeFile(path.join(outside, "secret.mp4"), "secret");
  await symlink(path.join(outside, "secret.mp4"), path.join(root, "escape.mp4"));
  await assert.rejects(resolveWithinRoot(root, "escape.mp4"), /escapes/i);
  await rm(root, { recursive: true, force: true });
  await rm(outside, { recursive: true, force: true });
});

test("NAS listing returns only directories and video files", async () => {
  const root = await validateNasRoot(await makeNasFixture());
  const listing = await listNasDirectory(root, "");
  assert.deepEqual(listing.directories.map((entry) => entry.name), ["Movies"]);
  assert.deepEqual(listing.files.map((entry) => entry.name), ["clip.mkv"]);
  const movies = await listNasDirectory(root, "Movies");
  assert.deepEqual(movies.files.map((entry) => entry.name), ["Family Trip (2026).mp4"]);
  assert.equal(typeof movies.files[0].size, "number");
  await rm(root, { recursive: true, force: true });
});

test("gateway serves NAS media with range support and owner binding", async () => {
  const root = await makeNasFixture();
  const server = createGatewayServer({ sessionToken: GATEWAY_SESSION_TOKEN, logger: null });
  const port = await listen(server);
  try {
    const connected = await gatewayRequest(port, "/api/connections", {
      method: "POST",
      body: { kind: "nas-folder", baseUrl: root, credential: "unused" },
    });
    assert.equal(connected.status, 200);
    assert.equal(connected.body.connection.state, "connected");
    const connectionId = connected.body.connection.id;

    const browse = await gatewayRequest(port, `/api/nas/browse?connectionId=${encodeURIComponent(connectionId)}&path=Movies`);
    assert.equal(browse.status, 200);
    assert.equal(browse.body.files[0].name, "Family Trip (2026).mp4");

    const traversal = await gatewayRequest(port, `/api/nas/browse?connectionId=${encodeURIComponent(connectionId)}&path=${encodeURIComponent("Movies/../..")}`);
    assert.equal(traversal.status, 400);

    const capability = await gatewayRequest(port, "/api/nas/media-capability", {
      method: "POST",
      body: { connectionId, path: "Movies/Family Trip (2026).mp4" },
    });
    assert.equal(capability.status, 201);
    const mediaPath = capability.body.mediaUrl.replace("/gateway/", "/api/");

    const media = await gatewayRequest(port, mediaPath, { range: "bytes=4-7" });
    assert.equal(media.status, 206);
    assert.equal(media.headers["content-range"], "bytes 4-7/16");
    assert.equal(media.body.toString("utf8"), "4567");

    const full = await gatewayRequest(port, mediaPath);
    assert.equal(full.status, 200);
    assert.equal(full.headers["content-type"], "video/mp4");
    assert.equal(full.body.length, 16);

    const foreign = await gatewayRequest(port, mediaPath, { sessionToken: OTHER_GATEWAY_SESSION_TOKEN });
    assert.equal(foreign.status, 401);
  } finally {
    await close(server);
    await rm(root, { recursive: true, force: true });
  }
});

test("transcode profile clamps to sane bounds and never upscales", () => {
  const clamped = clampTranscodeProfile({ maxWidth: 99999, maxHeight: -5, videoCodec: "hevc" });
  assert.equal(clamped.maxWidth, 8192);
  assert.equal(clamped.maxHeight, 240);
  assert.equal(clamped.videoCodec, "hevc");
  const defaults = clampTranscodeProfile({});
  assert.equal(defaults.maxWidth, 1920);
  assert.equal(defaults.maxHeight, 1080);
  assert.equal(defaults.videoCodec, "h264");

  const plan = buildTranscodeArgs({
    input: "/tmp/example.mkv",
    profile: { maxWidth: 3840, maxHeight: 2160 },
    encoders: { h264: "h264_videotoolbox", hevc: "hevc_videotoolbox", aac: "aac" },
  });
  assert.equal(plan.videoEncoder, "h264_videotoolbox");
  const scaleArg = plan.args[plan.args.indexOf("-vf") + 1];
  assert.match(scaleArg, /min\(iw\\,3840\)/);
  assert.match(scaleArg, /force_original_aspect_ratio=decrease/);
  assert.ok(plan.args.includes("frag_keyframe+empty_moov+default_base_moof"));

  const softwarePlan = buildTranscodeArgs({
    input: "/tmp/example.mkv",
    profile: { maxWidth: 1280, maxHeight: 720 },
    encoders: { h264: "libx264", aac: "aac" },
  });
  assert.equal(softwarePlan.videoEncoder, "libx264");
  assert.ok(softwarePlan.args.includes("-crf"));
});

test("transcode sessions only accept vault-issued gateway media", async () => {
  const root = await makeNasFixture();
  const server = createGatewayServer({ sessionToken: GATEWAY_SESSION_TOKEN, logger: null });
  const port = await listen(server);
  try {
    const probe = await probeTranscoder();
    const arbitrary = await gatewayRequest(port, "/api/transcode/sessions", {
      method: "POST",
      body: { mediaUrl: "https://example.com/video.mp4", profile: {} },
    });
    if (!probe.available) {
      assert.equal(arbitrary.status, 400);
      return;
    }
    assert.equal(arbitrary.status, 400);
    assert.match(arbitrary.body.error, /NAS and Stremio/);

    const connected = await gatewayRequest(port, "/api/connections", {
      method: "POST",
      body: { kind: "nas-folder", baseUrl: root, credential: "unused" },
    });
    const capability = await gatewayRequest(port, "/api/nas/media-capability", {
      method: "POST",
      body: { connectionId: connected.body.connection.id, path: "clip.mkv" },
    });
    const session = await gatewayRequest(port, "/api/transcode/sessions", {
      method: "POST",
      body: { mediaUrl: capability.body.mediaUrl, profile: { maxWidth: 1280, maxHeight: 720 } },
    });
    assert.equal(session.status, 201);
    assert.match(session.body.transcodeUrl, /^\/gateway\/transcode\/media\?transcodeId=/);

    const stolen = await gatewayRequest(port, "/api/transcode/sessions", {
      method: "POST",
      body: { mediaUrl: capability.body.mediaUrl, profile: {} },
      sessionToken: OTHER_GATEWAY_SESSION_TOKEN,
    });
    assert.equal(stolen.status, 401);
  } finally {
    await close(server);
    await rm(root, { recursive: true, force: true });
  }
});

test("gateway logger writes redacted JSONL entries and accepts client logs", async () => {
  const directory = await mkdtemp(path.join(os.tmpdir(), "openstream-logs-"));
  const logger = createLogger({ directory, mirrorToConsole: false });
  await logger.info("test", "connecting with ?apikey=super-secret-key done", { url: "https://user:pass@nas.local/x" });

  const server = createGatewayServer({ sessionToken: GATEWAY_SESSION_TOKEN, logger });
  const port = await listen(server);
  try {
    const accepted = await gatewayRequest(port, "/api/logs", {
      method: "POST",
      body: { entries: [{ level: "error", message: "client exploded", context: { screen: "player" } }] },
    });
    assert.equal(accepted.status, 202);
    assert.equal(accepted.body.accepted, 1);
  } finally {
    await close(server);
  }

  await new Promise((resolve) => setTimeout(resolve, 50));
  const files = await readdir(directory);
  assert.equal(files.length, 1);
  const lines = (await readFile(path.join(directory, files[0]), "utf8")).trim().split("\n").map((line) => JSON.parse(line));
  assert.ok(lines.length >= 2);
  assert.match(lines[0].message, /apikey=\[redacted\]/);
  assert.match(lines[0].context.url, /\[redacted\]@\[host\]/);
  const clientLine = lines.find((line) => line.source === "client");
  assert.equal(clientLine.level, "error");
  assert.equal(clientLine.message, "client exploded");
  await rm(directory, { recursive: true, force: true });
});
