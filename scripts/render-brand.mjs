import { chromium } from "playwright-core";
import { fileURLToPath, pathToFileURL } from "node:url";
import path from "node:path";

const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const executablePath = process.env.CHROME_FOR_TESTING_PATH;

if (!executablePath) {
  throw new Error("Set CHROME_FOR_TESTING_PATH to the shared Chrome executable.");
}

const browser = await chromium.launch({ executablePath, headless: true });
const page = await browser.newPage({ viewport: { width: 1200, height: 800 }, deviceScaleFactor: 1 });
await page.goto(pathToFileURL(path.join(projectRoot, "brand/preview.html")).href);
await page.waitForTimeout(80);
await page.locator(".tile").screenshot({
  path: path.join(projectRoot, "brand/previews/openstream-x-frame.png"),
  omitBackground: true,
});
await page.waitForTimeout(1_260);
await page.screenshot({
  path: path.join(projectRoot, "brand/previews/openstream-x-cube-preview.png"),
});
await page.locator(".tile").screenshot({
  path: path.join(projectRoot, "brand/previews/openstream-x-cube-mark.png"),
  omitBackground: true,
});
await browser.close();
