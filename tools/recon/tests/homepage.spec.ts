// Homepage capture: navigate to https://www.n11.com/, full-page screenshot,
// element-zoom on the header, harvest copy + computed-style tokens.
//
// Source: 02-RESEARCH.md Pattern 1 (canonical example) + 02-PATTERNS.md per-file
// homepage.spec.ts contract (3-segment relative path to .planning/intel/screenshots/,
// hard-fail on 4xx, dismissBanners before screenshot, harvest two JSONs, phrase
// count assertion >5). Anti-bot posture lives ONLY in playwright.config.ts (S-8).
import { test, expect } from '@playwright/test';
import * as fs from 'node:fs';
import * as path from 'node:path';
import { dismissBanners } from '../lib/dismiss-banners';
import { harvestCopy } from '../lib/harvest-copy';
import { harvestColors } from '../lib/harvest-colors';

const SLUG = 'homepage';
const URL = 'https://www.n11.com/';
const ELEMENT_ZOOM_SELECTOR = 'header';

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

    // Best-effort hover on the Elektronik category to capture the mega-menu
    // dropdown (Open Question 3 in RESEARCH lines 1128-1131). Non-blocking.
    await page
      .hover('a[href*="elektronik"]', { timeout: 3000 })
      .catch(() => {});
    await page.waitForTimeout(1000);

    await page.screenshot({
      path: path.join(SCREENSHOT_DIR, `${SLUG}-fullpage.png`),
      fullPage: true,
    });

    const el = page.locator(ELEMENT_ZOOM_SELECTOR).first();
    if ((await el.count()) > 0) {
      await el
        .screenshot({
          path: path.join(SCREENSHOT_DIR, `${SLUG}-header-element.png`),
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
