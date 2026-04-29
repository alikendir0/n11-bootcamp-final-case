// Turkish-charset phrase harvester (1-6 words, tag whitelist, deduplicated).
// Source: extracted from 02-RESEARCH.md Pattern 1 (lines 322-335).
import { Page } from '@playwright/test';

/**
 * Harvests visible Turkish copy from a page.
 *
 * Rules (binding — see PATTERNS.md per-file harvest-copy.ts contract):
 *   - Tag whitelist: 'a, button, span, h1, h2, h3, p, li' (do NOT loop over <*>)
 *   - Word-count range: 1..6
 *   - Turkish charset regex: ^[a-zA-Z0-9ÇĞİÖŞÜçğıöşü\s.,'!?₺%/-]+$
 *   - Set-based dedup
 *
 * Returns the unique phrase list as JS strings (no encoding gymnastics — UTF-8
 * is preserved by the JS string type; the consumer's fs.writeFileSync must
 * pass 'utf-8' explicitly per S-1).
 */
export async function harvestCopy(page: Page): Promise<string[]> {
  return await page.evaluate(() => {
    const result = new Set<string>();
    document
      .querySelectorAll<HTMLElement>('a, button, span, h1, h2, h3, p, li')
      .forEach((el) => {
        const txt = (el.textContent ?? '').trim();
        if (!txt) return;
        const wordCount = txt.split(/\s+/).length;
        if (wordCount < 1 || wordCount > 6) return;
        if (!/^[a-zA-Z0-9ÇĞİÖŞÜçğıöşü\s.,'!?₺%/-]+$/.test(txt)) return;
        result.add(txt);
      });
    return Array.from(result);
  });
}
