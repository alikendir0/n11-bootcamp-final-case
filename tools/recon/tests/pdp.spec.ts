// PDP capture: navigate to https://www.n11.com/elektronik, click the first product
// card link, capture the resulting product detail page. URL is DERIVED at runtime
// (do NOT hardcode — products go out of stock).
//
// Source: 02-PATTERNS.md per-file spec contract. NEVER click "Sepete Ekle" against a
// real product (Anti-Pattern: never modify a real cart).
import { test, expect } from '@playwright/test';
import * as fs from 'node:fs';
import * as path from 'node:path';
import { dismissBanners } from '../lib/dismiss-banners';
import { harvestCopy } from '../lib/harvest-copy';
import { harvestColors } from '../lib/harvest-colors';

const SLUG = 'pdp';
const CATEGORY_URL = 'https://www.n11.com/elektronik';
const ELEMENT_ZOOM_SELECTOR = '[class*="product-detail" i], [class*="basket" i]';

const SCREENSHOT_DIR = path.resolve(__dirname, '../../../.planning/intel/screenshots');
const OUTPUT_DIR = path.resolve(__dirname, '../output');

test.describe(`n11 ${SLUG} recon`, () => {
  test.beforeAll(() => {
    fs.mkdirSync(SCREENSHOT_DIR, { recursive: true });
    fs.mkdirSync(OUTPUT_DIR, { recursive: true });
  });

  test(`capture ${SLUG}`, async ({ page }) => {
    const categoryResponse = await page.goto(CATEGORY_URL, { waitUntil: 'networkidle' });
    expect(categoryResponse?.status()).toBeLessThan(400);
    await page.waitForTimeout(2500);
    await dismissBanners(page);
    await page.waitForTimeout(500);

    // Find the first product card link via a best-effort selector chain.
    const candidates = [
      'a[href*="/urun"]',
      'a[class*="product" i]',
      'article a',
    ];
    let clicked = false;
    for (const selector of candidates) {
      const link = page.locator(selector).first();
      if ((await link.count()) > 0) {
        try {
          await link.click({ timeout: 5000 });
          await page.waitForLoadState('networkidle', { timeout: 30_000 });
          clicked = true;
          break;
        } catch {
          continue;
        }
      }
    }
    if (!clicked) {
      throw new Error(
        `pdp.spec.ts: could not click any product card link from ${CATEGORY_URL}. ` +
          `Tried selectors: ${candidates.join(', ')}.`,
      );
    }

    await page.waitForTimeout(2500);
    await dismissBanners(page);
    await page.waitForTimeout(500);

    await page.screenshot({
      path: path.join(SCREENSHOT_DIR, `${SLUG}-fullpage.png`),
      fullPage: true,
    });

    const el = page.locator(ELEMENT_ZOOM_SELECTOR).first();
    if ((await el.count()) > 0) {
      await el
        .screenshot({
          path: path.join(SCREENSHOT_DIR, `${SLUG}-cta-element.png`),
        })
        .catch(() => {});
    }

    const phrases = await harvestCopy(page);
    fs.writeFileSync(
      path.join(OUTPUT_DIR, `${SLUG}-phrases.json`),
      JSON.stringify(
        {
          page: SLUG,
          url: page.url(),
          phrases,
          captured_at: new Date().toISOString(),
        },
        null,
        2,
      ),
      'utf-8',
    );

    const tokens = await harvestColors(page);
    fs.writeFileSync(
      path.join(OUTPUT_DIR, `${SLUG}-tokens.json`),
      JSON.stringify(
        {
          page: SLUG,
          url: page.url(),
          tokens,
          captured_at: new Date().toISOString(),
        },
        null,
        2,
      ),
      'utf-8',
    );

    expect(phrases.length).toBeGreaterThan(5);
  });
});
