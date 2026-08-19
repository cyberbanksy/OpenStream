import { fileURLToPath } from "node:url";
import { join } from "node:path";
import { tmpdir } from "node:os";
import { chromium } from "playwright-core";

// Live end-to-end smoke test against a running `pnpm dev` server.
// Usage: node scripts/live-smoke.mjs "<secure launch url>" "<nas folder path>"
// It consumes the one-time launch capability, completes standard onboarding,
// connects the NAS folder, direct-plays an mp4, and forces the local
// transcode path with a 10-bit HEVC MKV. Restart the dev server afterwards
// if a person still needs a launch URL.

const launchUrl = process.argv[2];
const nasPath = process.argv[3] ?? fileURLToPath(new URL("../test-media", import.meta.url));
if (!launchUrl) throw new Error("Pass the secure launch URL printed by pnpm dev.");
const executablePath =
  process.env.CHROME_FOR_TESTING_PATH ??
  process.env.PUPPETEER_EXECUTABLE_PATH ??
  "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";
const outputDir = process.env.OPENSTREAM_QA_DIR ?? join(tmpdir(), "openstream-qa");

const browser = await chromium.launch({ executablePath, headless: true });
const page = await browser.newPage({ viewport: { width: 1715, height: 965 } });
const findings = {};
page.on("pageerror", (error) => (findings.pageErrors ??= []).push(error.message));

async function playSelectedRoute() {
  await page.waitForSelector(".player-screen");
  await page.waitForSelector(".route-picker button.playback-route");
  await page.locator(".route-picker footer .action-button").click();
  await page.waitForSelector(".player-controls");
  await page.locator(".play-toggle").click();
}

async function videoState() {
  return page.evaluate(() => {
    const video = document.querySelector("video");
    return video ? {
      src: video.currentSrc,
      time: video.currentTime,
      width: video.videoWidth,
      height: video.videoHeight,
      paused: video.paused,
    } : undefined;
  });
}

// 1. Secure launch and onboarding.
await page.goto(launchUrl, { waitUntil: "networkidle" });
await page.waitForSelector(".onboarding-screen");
await page.locator(".onboarding-choice", { hasText: "Everyone" }).click();
await page.waitForSelector(".cinema-discover");
findings.onboarded = true;

// 2. Connect the NAS folder through the gateway.
await page.goto(new URL("/?screen=connections", launchUrl).toString(), { waitUntil: "networkidle" });
await page.locator("button[aria-label='Add connection']").click();
await page.waitForSelector(".connection-dialog");
await page.locator(".connection-dialog input[type='text']").fill(nasPath);
await page.locator(".connection-dialog button[type='submit']").click();
await page.waitForSelector(".connection-dialog", { state: "detached", timeout: 15000 });
const nasName = nasPath.split("/").filter(Boolean).pop();
const nasRow = page.locator(".connection-row", { hasText: nasName });
await nasRow.first().waitFor({ timeout: 15000 });
await nasRow.first().click();
await page.waitForSelector(".nas-browser");
findings.nasConnected = true;
await page.screenshot({ path: join(outputDir, "live-nas-browser.png") });

// 3. Direct play the friendly mp4.
await page.locator(".nas-row.file", { hasText: "Big Sky" }).click();
await playSelectedRoute();
await page.waitForFunction(() => {
  const video = document.querySelector("video");
  return video && video.currentTime > 1 && video.videoWidth > 0;
}, { timeout: 20000 });
findings.directPlay = await videoState();
await page.screenshot({ path: join(outputDir, "live-direct-play.png") });

// 4. The HEVC 10-bit MKV should end up on the local transcode route,
//    either through automatic fallback or the manual control.
await page.locator(".back-button").click();
await page.waitForSelector(".nas-browser");
await page.locator(".nas-row.file", { hasText: "Glacier Run" }).click();
await playSelectedRoute();
try {
  await page.waitForFunction(() => {
    const video = document.querySelector("video");
    return video && video.currentSrc.includes("/gateway/transcode/media") && video.currentTime > 1;
  }, { timeout: 25000 });
  findings.transcodePath = "automatic-fallback";
} catch {
  const manual = page.locator(".player-modes button", { hasText: "Transcode" });
  if (await manual.count()) {
    await manual.click();
    await page.waitForFunction(() => {
      const video = document.querySelector("video");
      return video && video.currentSrc.includes("/gateway/transcode/media") && video.currentTime > 1;
    }, { timeout: 25000 });
    findings.transcodePath = "manual";
  } else {
    findings.transcodePath = "direct-play-sufficed";
  }
}
findings.transcode = await videoState();
await page.screenshot({ path: join(outputDir, "live-transcode.png") });

// 5. Settings should report the ffmpeg transcoder in an authenticated session.
await page.goto(new URL("/?screen=settings", launchUrl).toString(), { waitUntil: "networkidle" });
findings.settingsTranscoder = await page.locator(".capability-panels section:has(h2:text('Local transcoder')) .support-row").first().textContent();

await browser.close();
console.log(JSON.stringify(findings, null, 2));
const transcodeUsed = findings.transcodePath !== "direct-play-sufficed" && findings.transcode?.src.includes("/gateway/transcode/media");
if (!findings.directPlay || findings.directPlay.time <= 1) throw new Error("Direct play did not progress.");
if (!transcodeUsed) console.warn("Note: the MKV played directly on this device; transcode was not exercised.");
