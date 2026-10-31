import { chromium } from 'playwright-core';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const chrome = 'C:/Program Files/Google/Chrome/Application/chrome.exe';
const pageUrl = 'file:///' + path.join(__dirname, 'mocks.html').replace(/\\/g, '/');
const outDir = path.join(__dirname, 'mocks');

const browser = await chromium.launch({ executablePath: chrome, headless: true });
const page = await browser.newPage({
  viewport: { width: 1120, height: 1000 },
  deviceScaleFactor: 2,
});
await page.goto(pageUrl, { waitUntil: 'networkidle' });
await page.evaluate(() => document.fonts.ready);
await page.waitForTimeout(300);

const ids = ['mock-a','mock-b','mock-c','mock-d','mock-e','mock-f','mock-g','mock-h','mock-i','mock-j'];
for (const id of ids) {
  const card = page.locator('#' + id);
  await card.scrollIntoViewIfNeeded();
  await page.waitForTimeout(120);
  await card.screenshot({ path: path.join(outDir, id + '.png') });
  console.log('saved', id);
}

await page.screenshot({ path: path.join(outDir, 'contact-sheet.png'), fullPage: true });
console.log('saved contact-sheet');
await browser.close();
