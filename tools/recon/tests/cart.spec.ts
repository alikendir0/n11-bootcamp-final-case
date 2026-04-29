// Cart capture: navigate to https://www.n11.com/sepetim as an anonymous user.
// Expect either the empty-cart state or a redirect to login — capture whatever
// state n11 shows. Never log in. Never add anything to cart.
//
// Source: 02-PATTERNS.md per-file spec contract.
import { test, expect } from '@playwright/test';
import * as fs from 'node:fs';
import * as path from 'node:path';
import { dismissBanners } from '../lib/dismiss-banners';
import { harvestCopy } from '../lib/harvest-copy';
import { harvestColors } from '../lib/harvest-colors';

const SLUG = 'cart';
const URL = 'https://www.n11.com/sepetim';
const ELEMENT_ZOOM_SELECTOR = '[class*="cart" i]';

const SCREENSHOT_DIR = path.resolve(__dirname, '../../../.planning/intel/screenshots');
const OUTPUT_DIR = path.resolve(__dirname, '../output');

test.describe(`n11 ${SLUG} recon`, () => {
  test.beforeAll(() => {
    fs.mkdirSync(SCREENSHOT_DIR, { recursive: true });
    fs.mkdirSync(OUTPUT_DIR, { recursive: true });
  });

  test(`capture ${SLUG}`, async ({ page }) => {
    const response = await page.goto(URL, { waitUntil: 'networkidle' });
    expect(response?.status()).toBeLessThan(400);
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
          path: path.join(SCREENSHOT_DIR, `${SLUG}-cart-element.png`),
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
