// Computed-style harvester + rgbToHex helper.
// Source: extracted from 02-RESEARCH.md Pattern 1 (lines 343-366) and Pitfall #4 (lines 581-587).
//
// IMPORTANT (PATTERNS.md S-1 separation rule + Pitfall #4):
//   harvestColors does NOT call rgbToHex. Colors are harvested as the browser's
//   native rgb(...) representation so on-disk JSON preserves ground truth.
//   The assembler converts rgb -> hex when writing the Markdown table.
import { Page } from '@playwright/test';

export type TokenRow = {
  role: string;
  selector: string;
  color: string;
  backgroundColor: string;
  fontFamily: string;
  fontWeight: string;
  fontSize: string;
};

export async function harvestColors(page: Page): Promise<TokenRow[]> {
  const tokens = await page.evaluate(() => {
    function pick(selector: string, role: string): {
      role: string;
      selector: string;
      color: string;
      backgroundColor: string;
      fontFamily: string;
      fontWeight: string;
      fontSize: string;
    } | null {
      const el = document.querySelector(selector);
      if (!el) return null;
      const cs = window.getComputedStyle(el);
      return {
        role,
        selector,
        color: cs.color,
        backgroundColor: cs.backgroundColor,
        fontFamily: cs.fontFamily,
        fontWeight: cs.fontWeight,
        fontSize: cs.fontSize,
      };
    }
    return [
      pick('header', 'nav-bg'),
      pick('body', 'body-bg'),
      pick('button[class*="add" i], a[class*="addToBasket" i]', 'cta-primary'),
      pick('a', 'link'),
      pick('h1', 'heading-primary'),
    ].filter((t): t is NonNullable<typeof t> => t !== null);
  });
  return tokens;
}

/**
 * Converts a CSS rgb()/rgba() string into uppercase #RRGGBB hex.
 * Returns the input unchanged if it is not a recognizable rgb(...) form.
 */
export function rgbToHex(rgb: string): string {
  const m = rgb.match(/^rgba?\((\d+),\s*(\d+),\s*(\d+)/);
  if (!m) return rgb;
  const r = Number(m[1]);
  const g = Number(m[2]);
  const b = Number(m[3]);
  return (
    '#' +
    [r, g, b]
      .map((x) => x.toString(16).padStart(2, '0').toUpperCase())
      .join('')
  );
}
