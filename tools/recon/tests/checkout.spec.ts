// Checkout step-1 capture: navigate to /sepetim, follow the checkout CTA. Capture
// whatever state n11 returns (likely a login prompt for anonymous users; sometimes
// the empty-cart screen if no CTA is rendered). URL is DERIVED at runtime.
//
// Source: 02-PATTERNS.md per-file spec contract.
import { test, expect } from '@playwright/test';
import * as fs from 'node:fs';
import * as path from 'node:path';
import { dismissBanners } from '../lib/dismiss-banners';
import { harvestCopy } from '../lib/harvest-copy';
import { harvestColors } from '../lib/harvest-colors';

const SLUG = 'checkout-step1';
const CART_URL = 'https://www.n11.com/sepetim';

const SCREENSHOT_DIR = path.resolve(__dirname, '../../../.planning/intel/screenshots');
const OUTPUT_DIR = path.resolve(__dirname, '../output');

test.describe(`n11 ${SLUG} recon`, () => {
  test.beforeAll(() => {
    fs.mkdirSync(SCREENSHOT_DIR, { recursive: true });
    fs.mkdirSync(OUTPUT_DIR, { recursive: true });
  });

  test(`capture ${SLUG}`, async ({ page }) => {
    const cartResponse = await page.goto(CART_URL, { waitUntil: 'networkidle' });
    expect(cartResponse?.status()).toBeLessThan(400);
    await page.waitForTimeout(2500);
    await dismissBanners(page);
    await page.waitForTimeout(500);

    // Try to follow a checkout CTA from the cart page (button or link).
    let followedCta = false;
    const ctaPattern = /devam et|siparişi tamamla|ödeme/i;
    const ctaCandidates = [
      page.getByRole('button', { name: ctaPattern }),
      page.getByRole('link', { name: ctaPattern }),
    ];
    for (const cta of ctaCandidates) {
      if (
        (await cta.count()) > 0 &&
        (await cta.first().isVisible().catch(() => false))
      ) {
        try {
          await cta.first().click({ timeout: 5000 });
          await page.waitForLoadState('networkidle', { timeout: 30_000 });
          followedCta = true;
          break;
        } catch {
          continue;
        }
      }
    }

    await page.waitForTimeout(2500);
    await dismissBanners(page);
    await page.waitForTimeout(500);

    await page.screenshot({
      path: path.join(SCREENSHOT_DIR, `${SLUG}-fullpage.png`),
      fullPage: true,
    });

    const note = followedCta
      ? null
      : 'no checkout CTA found on empty cart — captured cart state';

    const phrases = await harvestCopy(page);
    fs.writeFileSync(
      path.join(OUTPUT_DIR, `${SLUG}-phrases.json`),
      JSON.stringify(
        {
          page: SLUG,
          url: page.url(),
          phrases,
          captured_at: new Date().toISOString(),
          ...(note ? { note } : {}),
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
          ...(note ? { note } : {}),
        },
        null,
        2,
      ),
      'utf-8',
    );

    expect(phrases.length).toBeGreaterThan(5);
  });
});
