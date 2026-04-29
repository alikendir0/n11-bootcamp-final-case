// Category capture: navigate to homepage, click first link matching /elektronik/i,
// then capture the listing page (full-page + left-rail filters element zoom).
// Falls back to direct goto on https://www.n11.com/elektronik if the click fails.
//
// Source: 02-PATTERNS.md per-file spec contract (3-segment screenshot path, hard-fail
// on 4xx, dismissBanners before screenshot). Anti-bot posture lives in playwright.config.ts.
import { test, expect } from '@playwright/test';
import * as fs from 'node:fs';
import * as path from 'node:path';
import { dismissBanners } from '../lib/dismiss-banners';
import { harvestCopy } from '../lib/harvest-copy';
import { harvestColors } from '../lib/harvest-colors';

const SLUG = 'category-elektronik';
const HOMEPAGE = 'https://www.n11.com/';
const FALLBACK_URL = 'https://www.n11.com/elektronik';
const ELEMENT_ZOOM_SELECTOR = 'aside, [class*="filter" i]';

const SCREENSHOT_DIR = path.resolve(__dirname, '../../../.planning/intel/screenshots');
const OUTPUT_DIR = path.resolve(__dirname, '../output');

test.describe(`n11 ${SLUG} recon`, () => {
  test.beforeAll(() => {
    fs.mkdirSync(SCREENSHOT_DIR, { recursive: true });
    fs.mkdirSync(OUTPUT_DIR, { recursive: true });
  });

  test(`capture ${SLUG}`, async ({ page }) => {
    // Step 1 — load homepage and dismiss banners.
    const homeResponse = await page.goto(HOMEPAGE, { waitUntil: 'networkidle' });
    expect(homeResponse?.status()).toBeLessThan(400);
    await page.waitForTimeout(2500);
    await dismissBanners(page);
    await page.waitForTimeout(500);

    // Step 2 — try to click the first Elektronik link from the homepage.
    let landedViaClick = false;
    try {
      const elektronikLink = page
        .getByRole('link', { name: /elektronik/i })
        .first();
      if ((await elektronikLink.count()) > 0) {
        await elektronikLink.click({ timeout: 5000 });
        await page.waitForURL(/elektronik/i, { timeout: 30_000 });
        landedViaClick = true;
      }
    } catch {
      landedViaClick = false;
    }

    // Step 3 — fallback direct navigation if the click route did not work.
    if (!landedViaClick) {
      const directResponse = await page.goto(FALLBACK_URL, { waitUntil: 'networkidle' });
      expect(directResponse?.status()).toBeLessThan(400);
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
          path: path.join(SCREENSHOT_DIR, `${SLUG}-filters-element.png`),
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
