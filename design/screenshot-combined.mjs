import { chromium } from 'playwright-core';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const chrome = 'C:/Program Files/Google/Chrome/Application/chrome.exe';
const pageUrl = 'file:///' + path.join(__dirname, 'combined.html').replace(/\\/g, '/');
const outDir = path.join(__dirname, 'mocks');

const browser = await chromium.launch({ executablePath: chrome, headless: true });
const page = await browser.newPage({ viewport: { width: 1180, height: 1000 }, deviceScaleFactor: 2 });
await page.goto(pageUrl, { waitUntil: 'networkidle' });
await page.evaluate(() => document.fonts.ready);
await page.waitForTimeout(300);

await page.locator('.phone').screenshot({ path: path.join(outDir, 'combined-phone.png') });
await page.locator('.sizes').screenshot({ path: path.join(outDir, 'combined-sizes.png') });
await page.screenshot({ path: path.join(outDir, 'combined-full.png'), fullPage: true });
console.log('saved combined mockups');
await browser.close();
