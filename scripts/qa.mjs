import { access, mkdir } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { join } from "node:path";
import axe from "axe-core";
import { chromium } from "playwright-core";

const baseUrl = process.env.OPENSTREAM_URL ?? "http://127.0.0.1:5173";
const executablePath =
  process.env.CHROME_FOR_TESTING_PATH ??
  process.env.PUPPETEER_EXECUTABLE_PATH;
const screens = [
  "discover",
  "library",
  "live",
  "connections",
  "activity",
  "settings",
  "details",
  "search",
];
const outputDir = fileURLToPath(new URL("../design/qa-automated/", import.meta.url));

if (!executablePath) {
  throw new Error(
    "Set CHROME_FOR_TESTING_PATH or PUPPETEER_EXECUTABLE_PATH to the existing shared Chrome for Testing binary.",
  );
}
await access(executablePath);
await mkdir(outputDir, { recursive: true });

const browser = await chromium.launch({ executablePath, headless: true });
const page = await browser.newPage({ viewport: { width: 1715, height: 965 } });
await page.route("**/gateway/connections", (route) => route.fulfill({
  status: 200,
  contentType: "application/json",
  body: JSON.stringify({ connections: [] }),
}));
const consoleErrors = [];
page.on("console", (message) => {
  if (message.type() === "error") consoleErrors.push(message.text());
});
page.on("pageerror", (error) => consoleErrors.push(error.message));

async function waitForStablePage() {
  await page.evaluate(() => document.fonts.ready);
  await page.waitForFunction(() =>
    Array.from(document.images).every((image) => image.complete),
  );
}

async function auditLayout() {
  return page.locator(".app-shell").evaluate((stage) => {
    const stageBounds = stage.getBoundingClientRect();
    const elements = Array.from(
      stage.querySelectorAll("button, input, select, textarea, h1, h2, .media-card"),
    ).filter((element) => {
      const style = getComputedStyle(element);
      const rect = element.getBoundingClientRect();
      return style.visibility !== "hidden" && style.display !== "none" && rect.width > 0 && rect.height > 0;
    });
    const interactive = elements.filter((element) =>
      element.matches("button, input, select, textarea"),
    );
    const label = (element) =>
      element.getAttribute("aria-label") || element.textContent?.trim().slice(0, 60) || element.tagName;
    const outOfBounds = elements
      .filter((element) => {
        const rect = element.getBoundingClientRect();
        return (
          rect.left < stageBounds.left - 1 ||
          rect.top < stageBounds.top - 1 ||
          rect.right > stageBounds.right + 1 ||
          rect.bottom > stageBounds.bottom + 1
        );
      })
      .map(label);
    const overlaps = [];
    for (let leftIndex = 0; leftIndex < interactive.length; leftIndex += 1) {
      const left = interactive[leftIndex];
      const leftRect = left.getBoundingClientRect();
      for (let rightIndex = leftIndex + 1; rightIndex < interactive.length; rightIndex += 1) {
        const right = interactive[rightIndex];
        if (left.contains(right) || right.contains(left)) continue;
        const rightRect = right.getBoundingClientRect();
        const overlapWidth = Math.max(0, Math.min(leftRect.right, rightRect.right) - Math.max(leftRect.left, rightRect.left));
        const overlapHeight = Math.max(0, Math.min(leftRect.bottom, rightRect.bottom) - Math.max(leftRect.top, rightRect.top));
        if (overlapWidth * overlapHeight > 4) overlaps.push(`${label(left)} <> ${label(right)}`);
      }
    }
    return {
      width: Math.round(stageBounds.width),
      height: Math.round(stageBounds.height),
      outOfBounds: [...new Set(outOfBounds)],
      overlaps: [...new Set(overlaps)],
    };
  });
}

async function runAxe() {
  await page.evaluate(axe.source);
  return page.evaluate(async () => {
    const result = await globalThis.axe.run(document, {
      runOnly: { type: "tag", values: ["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"] },
    });
    return result.violations.map((violation) => ({
      id: violation.id,
      impact: violation.impact,
      targets: violation.nodes.map((node) => node.target.join(" ")),
    }));
  });
}

const results = [];
let discoverInteraction;
for (const screen of screens) {
  await page.goto(`${baseUrl}/?screen=${screen}`, { waitUntil: "networkidle" });
  await waitForStablePage();

  if (screen === "discover") {
    const mark = page.locator(".discover-brand .brand-mark-motion");
    const transformBefore = await mark.evaluate((element) => getComputedStyle(element).transform);
    await page.waitForTimeout(900);
    const transformAfter = await mark.evaluate((element) => getComputedStyle(element).transform);

    const focusCards = page.locator(".focus-card");
    await focusCards.nth(2).focus();
    await page.waitForFunction(() => document.querySelector(".cinema-title h1")?.textContent === "Solar Drift");
    const focusedTitle = await page.locator(".cinema-title h1").textContent();
    await focusCards.nth(1).focus();
    await page.waitForFunction(() => document.querySelector(".cinema-title h1")?.textContent === "Last Signal");

    await page.emulateMedia({ reducedMotion: "reduce" });
    const reducedMotionAnimation = await mark.evaluate((element) => getComputedStyle(element).animationName);
    await page.emulateMedia({ reducedMotion: "no-preference" });

    await focusCards.nth(1).press("Enter");
    await page.waitForSelector(".player-screen");
    const playbackOpened = await page.locator(".player-screen").isVisible();
    await page.goto(`${baseUrl}/?screen=discover`, { waitUntil: "networkidle" });
    await waitForStablePage();

    await page.locator(".cinema-control-dock button[aria-label='Search']").click();
    await page.waitForSelector(".search-screen");
    const searchOpened = await page.locator(".search-screen").isVisible();
    await page.goto(`${baseUrl}/?screen=discover`, { waitUntil: "networkidle" });
    await waitForStablePage();

    discoverInteraction = {
      logoMotionChanged: transformBefore !== transformAfter,
      reducedMotionAnimation,
      focusedTitle,
      playbackOpened,
      searchOpened,
    };
  }

  const layout = await auditLayout();
  const accessibility = await runAxe();
  await page.screenshot({
    path: join(outputDir, `${screen}-desktop.png`),
    animations: "disabled",
  });
  results.push({ screen, layout, accessibility });
}

await page.setViewportSize({ width: 390, height: 844 });
await page.goto(`${baseUrl}/xr/viewer`, { waitUntil: "networkidle" });
await waitForStablePage();
const xrMobileAccessibility = await runAxe();
await page.screenshot({
  path: join(outputDir, "xr-viewer-mobile.png"),
  animations: "disabled",
});

await browser.close();

const failures = [
  ...consoleErrors.map((error) => `console: ${error}`),
  ...results.flatMap(({ screen, layout, accessibility }) => [
    ...layout.outOfBounds.map((label) => `${screen}: out of bounds: ${label}`),
    ...layout.overlaps.map((label) => `${screen}: overlap: ${label}`),
    ...accessibility.map((violation) => `${screen}: axe ${violation.id}: ${violation.targets.join(", ")}`),
  ]),
  ...xrMobileAccessibility.map((violation) => `xr mobile: axe ${violation.id}: ${violation.targets.join(", ")}`),
  ...(discoverInteraction?.logoMotionChanged ? [] : ["discover: animated logo transform did not change"]),
  ...(discoverInteraction?.reducedMotionAnimation === "none" ? [] : ["discover: logo animation remained active in reduced-motion mode"]),
  ...(discoverInteraction?.focusedTitle === "Solar Drift" ? [] : ["discover: focus did not activate Solar Drift"]),
  ...(discoverInteraction?.playbackOpened ? [] : ["discover: focused Play action did not open the player"]),
  ...(discoverInteraction?.searchOpened ? [] : ["discover: Search control did not open search"]),
];

console.log(JSON.stringify({ results, discoverInteraction, xrMobileAccessibility, consoleErrors }, null, 2));
if (failures.length > 0) {
  console.error(failures.join("\n"));
  process.exitCode = 1;
}
