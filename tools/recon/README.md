# tools/recon/

Throwaway Phase-2 Playwright project that captures the public surface of n11.com so Phase 10 (Storefront) and Phase 11 (Chat Bubble) have a written-down hand-off contract. Runs once, writes screenshots + a Markdown report into `.planning/intel/`, then sits in the repo as the audit trail.

## Why this is NOT a Gradle module

- The repo is a Java/Spring Boot multi-module (Gradle); recon is a Node/Playwright tool. Bridging them would either pollute `gradle/libs.versions.toml` with npm pins or force a Gradle-driven Playwright invocation, both of which obscure the fact that recon is a one-shot research tool — not a service.
- See `02-PATTERNS.md` S-6 ("Keep recon outside Gradle") for the full negative-pattern rationale: no `build.gradle.kts`, no `settings.gradle.kts` `include(...)`, no Playwright entry in the version catalog, no Gradle task that runs Playwright.

## How to run from a fresh clone

```bash
# 1. Install npm deps (Playwright 1.59.x, tsx, typescript)
cd tools/recon && npm install

# 2. Download the Chromium binary into ~/.cache/ms-playwright (NOT in repo).
#    Only Chromium — no Firefox, no WebKit. ~170 MB.
cd tools/recon && npx playwright install chromium

# 3. Capture all 7 pages (visible browser; 60-90 s total at workers:1, slowMo:250).
cd tools/recon && npm run capture

# 4. Assemble the .planning/intel/n11-recon.md report from output/*.json.
cd tools/recon && npm run assemble

# 5. Sanity-check the report shape (≥30 phrases, ≥10 tokens, all 7 screenshots).
cd tools/recon && npm run check
```

## Anti-bot fallback ladder

n11 sits behind Cloudflare. The default config already passes; if it ever 403s, escalate one rung at a time:

1. **Configured posture** (default — already on):
   - `headless: false` (visible browser)
   - Real desktop Chrome `userAgent` (no `HeadlessChrome` substring)
   - `args: ['--disable-blink-features=AutomationControlled']` (drops `navigator.webdriver`)
   - `slowMo: 250` (looks human-paced)
   - `locale: 'tr-TR'`, `timezoneId: 'Europe/Istanbul'`

2. **Persistent context** (only if rung 1 fails — uses a real Chrome profile with cookies):
   ```typescript
   import { chromium } from '@playwright/test';
   import * as os from 'node:os';
   import * as path from 'node:path';

   const userDataDir = path.join(os.tmpdir(), 'recon-chrome-profile');
   const context = await chromium.launchPersistentContext(userDataDir, {
     headless: false,
     channel: 'chrome',
     viewport: { width: 1440, height: 900 },
     locale: 'tr-TR',
     timezoneId: 'Europe/Istanbul',
   });
   const page = await context.newPage();
   // ... capture as usual
   await context.close();
   ```
   Source: <https://playwright.dev/docs/api/class-browsertype> (`browserType.launchPersistentContext`).

## What it writes & where

| Artifact | Location | Committed? |
| --- | --- | --- |
| Full-page + element screenshots (7 pages) | `.planning/intel/screenshots/*.png` | yes (committed binaries) |
| Per-page harvested phrase JSON | `tools/recon/output/*-phrases.json` | no (gitignored) |
| Per-page harvested computed-style JSON | `tools/recon/output/*-tokens.json` | no (gitignored) |
| Final 8-section recon report | `.planning/intel/n11-recon.md` | yes |

## What it does NOT do

- **No real login.** The `/account` and `/login` captures are of the unauthenticated form / landing page only. We never type credentials into n11.
- **No real cart adds.** The `/sepetim` capture is of the empty-cart state ("Sepetiniz boş") plus any anonymous-user "recommended" rail. We never click "Sepete Ekle" against a live product.

These boundaries match RESEARCH §"Anti-Patterns to Avoid". Phase 2 stays at the public-page surface.

## Re-running individual specs

After the first full capture, individual pages can be re-shot without re-running everything:

```bash
cd tools/recon && npx playwright test homepage.spec.ts
cd tools/recon && npx playwright test pdp.spec.ts
```

Then re-run `npm run assemble` so the Markdown report picks up the refreshed JSON.

## Type-check gate

```bash
cd tools/recon && npm run typecheck   # alias for `tsc --noEmit`
```

Runs the strict TypeScript compiler against every `*.ts` file (specs, helpers, assembler, checker) — exits 0 on a clean tree, non-zero on any type error. This is the canonical CI/code-review signal that the recon project compiles; do not skip it before committing changes to this directory.
