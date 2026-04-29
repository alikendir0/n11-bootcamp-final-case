// check-recon.ts — verifies .planning/intel/n11-recon.md shape across 9 sections
// (the 8 schema sections written by assemble-recon.ts + the ## Decision Matrix
// subsection appended by Plan 02-03 Task 1).
//
// Exit code: 0 on full pass; 1 on any failure (all 9 assertions evaluated and
// reported, then the exit code is set; failure count is capped at 1).
//
// Read-only on the filesystem.
import * as fs from 'node:fs';
import * as path from 'node:path';

const REPORT = path.resolve(__dirname, '../../.planning/intel/n11-recon.md');
const SCREENSHOT_DIR = path.resolve(__dirname, '../../.planning/intel/screenshots');

const REQUIRED_SECTIONS: string[] = [
  '## 1. Page Inventory',
  '## 2. Turkish Copy Catalog',
  '## 3. Category Taxonomy',
  '## 4. Color Token Table',
  '## 5. Typography Notes',
  '## 6. Layout Patterns',
  '## 7. Anti-pattern flags',
  '## 8. Open n11 questions',
];

const REQUIRED_SCREENSHOTS: string[] = [
  'homepage-fullpage.png',
  'category-elektronik-fullpage.png',
  'pdp-fullpage.png',
  'cart-fullpage.png',
  'checkout-step1-fullpage.png',
  'account-fullpage.png',
  'login-fullpage.png',
];

const MIN_SCREENSHOT_BYTES = 50_000;
const MIN_PHRASE_ROWS = 30;
const MIN_TOKEN_ROWS = 10;

let failures: string[] = [];

function fail(message: string): void {
  failures.push(message);
}

function readReport(): string | null {
  if (!fs.existsSync(REPORT)) {
    fail(`recon report missing at ${REPORT}`);
    return null;
  }
  return fs.readFileSync(REPORT, 'utf-8');
}

function checkSectionOrder(lines: string[]): void {
  let lastIdx = -1;
  for (const heading of REQUIRED_SECTIONS) {
    const idx = lines.findIndex((l) => l.startsWith(heading));
    if (idx === -1) {
      fail(`missing required section heading: "${heading}"`);
      continue;
    }
    if (idx <= lastIdx) {
      fail(`section "${heading}" appears out of order at line ${idx + 1}`);
    }
    lastIdx = idx;
  }
}

function sliceBetween(lines: string[], startPattern: RegExp, endPattern: RegExp): string[] {
  const startIdx = lines.findIndex((l) => startPattern.test(l));
  if (startIdx === -1) return [];
  const endIdx = lines.findIndex((l, i) => i > startIdx && endPattern.test(l));
  return endIdx === -1 ? lines.slice(startIdx) : lines.slice(startIdx, endIdx);
}

function countPhraseRows(lines: string[]): number {
  const section = sliceBetween(lines, /^## 2\. Turkish Copy Catalog/, /^## 3\./);
  return section.filter((l) => /^\|\s+\d+\s+\|/.test(l)).length;
}

function countTokenRows(lines: string[]): number {
  const section = sliceBetween(lines, /^## 4\. Color Token Table/, /^## 5\./);
  return section.filter((l) => /^\|\s*--[a-z]/.test(l)).length;
}

function checkColorTokenHexOnly(lines: string[]): void {
  const section = sliceBetween(lines, /^## 4\. Color Token Table/, /^## 5\./);
  for (const line of section) {
    if (!/^\|\s*--[a-z]/.test(line)) continue;
    // Split on pipes; columns are: '', token, hex, sourcePage, rgb-source, ''.
    const cols = line.split('|').map((c) => c.trim());
    const hexCol = cols[2];
    if (!hexCol) continue;
    if (!/^#[0-9A-F]{6}$/.test(hexCol)) {
      fail(`Color Token Table Hex column "${hexCol}" is not a clean #RRGGBB (Pitfall #4)`);
      return;
    }
  }
}

function checkScreenshots(): void {
  for (const name of REQUIRED_SCREENSHOTS) {
    const fullPath = path.join(SCREENSHOT_DIR, name);
    if (!fs.existsSync(fullPath)) {
      fail(`screenshot missing: ${name}`);
      continue;
    }
    const bytes = fs.statSync(fullPath).size;
    if (bytes < MIN_SCREENSHOT_BYTES) {
      fail(`screenshot ${name} is only ${bytes} bytes (need >= ${MIN_SCREENSHOT_BYTES} — Pitfall #1 anti-bot guard)`);
    }
  }
}

function checkDecisionMatrix(lines: string[]): void {
  // 9th assertion (Issue-3 fix) — guards against silent deletion of the
  // Phase-10 carry-forward subsection that Plan 02-03 Task 1 appends.
  const dmIdx = lines.findIndex((l) => /^## Decision Matrix/.test(l));
  if (dmIdx === -1) {
    fail('## Decision Matrix subsection missing — Plan 02-03 Task 1 must append it; Phase 10 reads this for the toolchain audit trail');
    return;
  }
  const dmBody = lines.slice(dmIdx).join('\n');
  if (!dmBody.includes('Vite + React 19 SPA')) {
    fail('## Decision Matrix subsection missing winner label "Vite + React 19 SPA"');
  }
  if (!/\b65\b/.test(dmBody)) {
    fail('## Decision Matrix subsection missing matrix winner score 65');
  }
}

function main(): void {
  const content = readReport();
  if (content === null) {
    console.error(`FAIL: ${failures[0]}`);
    process.exit(1);
  }
  const lines = content.split(/\r?\n/);

  checkSectionOrder(lines);
  const phraseCount = countPhraseRows(lines);
  const tokenCount = countTokenRows(lines);
  if (phraseCount < MIN_PHRASE_ROWS) {
    fail(`Turkish Copy Catalog has ${phraseCount} rows (need >= ${MIN_PHRASE_ROWS})`);
  }
  if (tokenCount < MIN_TOKEN_ROWS) {
    fail(`Color Token Table has ${tokenCount} rows (need >= ${MIN_TOKEN_ROWS})`);
  }
  checkColorTokenHexOnly(lines);
  checkScreenshots();
  checkDecisionMatrix(lines);

  if (failures.length === 0) {
    const screenshotCount = REQUIRED_SCREENSHOTS.length;
    console.log(
      `RECON OK — ${phraseCount} phrases, ${tokenCount} tokens, ${screenshotCount} screenshots, Decision Matrix present`,
    );
    process.exit(0);
  }

  for (const f of failures) {
    console.error(`FAIL: ${f}`);
  }
  process.exit(1);
}

main();
