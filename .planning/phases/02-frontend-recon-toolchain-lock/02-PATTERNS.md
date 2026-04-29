# Phase 2: Frontend Recon + Toolchain Lock — Pattern Map

**Mapped:** 2026-04-29
**Files analyzed:** 21 (16 created, 1 created-then-committed dir, 3 edited, 1 implicit npm-install effect)
**Analogs found:** 4 in-repo (.gitignore, infra-tests/build, service-template/skeleton/README, .planning/research/SUMMARY) + 17 external/no-analog

---

## Honesty Disclaimer (read first)

This is a **greenfield phase** — Phase 1 just landed Java/Spring Boot/Gradle infra-tests skeleton, and there is **no existing frontend, no existing Playwright, no existing tools/ tree, no existing TypeScript anywhere in the repo**. The closest in-repo analog for the new files is `infra-tests/` (a small standalone test project under the repo root), and even that match is partial — it's a Gradle module, not an npm project, and the recon project deliberately stays *outside* the Gradle build (see RESEARCH §"Why `tools/recon/` and not the existing Gradle multi-module"). For ~80% of the new files there is no in-repo precedent and the executor must follow either Playwright/Vite official docs **or** a "shape contract" written below. Inventing a fake in-repo analog would mislead the executor — we say so plainly.

---

## File Classification

> 7 page-spec files (`tests/<page>.spec.ts`) and 7 screenshot artifacts (`screenshots/<page>-fullpage.png`) are listed once each below; the same pattern applies to all members of the group.

| New / Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `tools/recon/package.json` | recon-config | static config | external — npm `init -y` default | external |
| `tools/recon/package-lock.json` | recon-config (artifact) | static config | external — `npm install` writes it | external |
| `tools/recon/playwright.config.ts` | recon-config | static config | external — playwright.dev "Configuration" page | external |
| `tools/recon/.gitignore` | gitignore | n/a | **`/.gitignore` (repo root)** | role-match (different stack) |
| `tools/recon/README.md` | readme | n/a | **`service-template/skeleton/README.md`** | role-match (style only) |
| `tools/recon/tests/<page>.spec.ts` × 7 | recon-spec | request-response (per-page capture: nav → screenshot → harvest) | external — playwright.dev "Writing tests" + "Screenshots" | no in-repo analog |
| `tools/recon/lib/dismiss-banners.ts` | recon-helper | request-response (DOM manipulation) | external — playwright.dev locators page | no in-repo analog |
| `tools/recon/lib/harvest-copy.ts` | recon-helper | transform (DOM → JSON) | external — `page.evaluate` pattern in Playwright docs | no in-repo analog |
| `tools/recon/lib/harvest-colors.ts` | recon-helper | transform (computedStyle → JSON, rgb→hex) | external — getComputedStyle + Playwright `page.evaluate` | no in-repo analog |
| `tools/recon/assemble-recon.ts` | tooling-script | batch (JSON files → single Markdown) | external — Node `fs.readdirSync` / `fs.writeFileSync` idiom | no in-repo analog |
| `tools/recon/check-recon.ts` | tooling-script | batch (Markdown → assertions) | external — plain Node script | no in-repo analog |
| `tools/recon/output/*-phrases.json` | artifact-output (intermediate, gitignored) | n/a | n/a | n/a (intermediate) |
| `tools/recon/output/*-tokens.json` | artifact-output (intermediate, gitignored) | n/a | n/a | n/a (intermediate) |
| `.planning/intel/n11-recon.md` | artifact-output (committed) | n/a | **`.planning/research/SUMMARY.md`** | tone/format match (Markdown-tables-and-headings author voice) |
| `.planning/intel/screenshots/<page>-fullpage.png` × 7+ | artifact-output (committed binary) | n/a | n/a | no in-repo analog |
| `scripts/check-phase-02-artifacts.sh` | tooling-script | batch (file existence + grep counts) | external — POSIX `test -f` / `grep -c` idioms | no in-repo analog (no `scripts/` dir yet) |
| `.gitignore` (root, **edited**) | gitignore (edit) | n/a | **`/.gitignore` (current content)** | exact (same file) |
| `.planning/PROJECT.md` (Key Decisions section, **edited**) | decision-record (edit) | n/a | **`.planning/PROJECT.md` § Key Decisions** | exact (same file, append-only) |
| `.planning/PROJECT.md` (Open Questions section, **edited**) | decision-record (edit) | n/a | **`.planning/PROJECT.md` § Open Questions** | exact (same file, "Frontend toolchain" row gets resolved) |
| (npm browser binary install — `npx playwright install chromium`) | tooling-side-effect | n/a | external — Playwright docs | external |

---

## Pattern Assignments

### `tools/recon/package.json` (recon-config)

**Analog:** external — `npm init -y` default + RESEARCH §"Recon Tooling" pin table.
**Reason no in-repo analog:** repo has zero existing `package.json` files (it's a Gradle/Java repo).
**Shape contract for executor:**
1. Pin `@playwright/test` at the **exact** version called out in RESEARCH §"Standard Stack" (`1.59.1` as of research date — re-run `npm view @playwright/test version` and use whatever 1.59.x patch is current; do **not** use `^1.59.0` ranges, pin exact).
2. Declare `"private": true` so the project can't accidentally be published.
3. Add `"type": "module"` only if the spec files use top-level `await` or ESM-only imports — Playwright tests are happiest with the default CommonJS resolution. **Default: omit `"type"`** unless a spec breaks.
4. Three npm scripts: `"capture": "playwright test"`, `"assemble": "tsx assemble-recon.ts"`, `"check": "tsx check-recon.ts"`. (Add `tsx` as a devDependency if not already pulled in — Playwright 1.59 does ship with `ts-node` integration so `playwright test` reads `*.spec.ts` natively, but the standalone scripts under the project root need `tsx`.)
5. Node engines: `"engines": { "node": ">=22.12" }` — matches Vite 8 floor (RESEARCH §"Standard Stack").

**Reference (paste-ready skeleton, executor verifies versions at install time):**
```json
{
  "name": "n11-recon",
  "private": true,
  "version": "0.0.0",
  "description": "Phase-2 throwaway recon project against n11.com. Not part of the Gradle build.",
  "scripts": {
    "capture": "playwright test",
    "assemble": "tsx assemble-recon.ts",
    "check": "tsx check-recon.ts"
  },
  "devDependencies": {
    "@playwright/test": "1.59.1",
    "tsx": "^4.19.0"
  },
  "engines": { "node": ">=22.12" }
}
```

---

### `tools/recon/playwright.config.ts` (recon-config)

**Analog:** external — verbatim from RESEARCH §"Pattern 2: Playwright config (anti-bot posture)" lines 386-425. **Use that excerpt as the implementation; do not re-derive.**
**Reason no in-repo analog:** no Playwright code in repo.
**Critical knobs (do not change without justification):** `headless: false`, `workers: 1`, `fullyParallel: false`, `slowMo: 250`, `--disable-blink-features=AutomationControlled`, `locale: 'tr-TR'`, `timezoneId: 'Europe/Istanbul'`, real desktop Chrome `userAgent`. RESEARCH §"Common Pitfalls #1, #7" explain why each knob exists.

---

### `tools/recon/.gitignore` (gitignore)

**Analog:** **`/.gitignore` (repo root)** — read for *posture only* (terse, comment-grouped sections, no boilerplate templates).

**Excerpt to mirror in style** (`/.gitignore` lines 1-29):
```gitignore
# Build outputs
build/
.gradle/
out/

# Secrets — NEVER commit
.env
.env.local
.env.*.local
secrets/
**/application-local.yml
*.pem
*.key

# IDE
.idea/
*.iml
.vscode/

# OS
.DS_Store
Thumbs.db

# Test artifacts
**/target/
**/.attach_pid*
hs_err_pid*.log
**/bin/
```

**What to keep (style):** comment-grouped sections (`# Build outputs`, `# IDE`, etc.), no Github-template boilerplate (no `# generated by …`), explicit "NEVER commit" callouts for secret-bearing patterns.
**What to swap (content):** the recon project ignores **node-ecosystem** outputs, not Java/Gradle:
```gitignore
# Node / npm
node_modules/

# Playwright outputs
test-results/
playwright-report/
output/
.playwright/

# IDE (mirror root)
.idea/
.vscode/
```
**Note:** the **root** `.gitignore` (separate, edited file) gets *additional* lines for the recon outputs so a contributor running recon from the repo root doesn't accidentally commit them — see "Edited file: root `.gitignore`" below.

---

### `tools/recon/README.md` (readme)

**Analog:** **`service-template/skeleton/README.md`** lines 1-40. Match its **author voice** (terse heading, "Why X" rationale section, table for token/argument lookup, code blocks with shell-comment context).

**Excerpt to mirror** (`service-template/skeleton/README.md` lines 1-10):
```markdown
# service-template/skeleton/

Copy-paste source tree for scaffolding a new business service. Used by Phase 3+ executors.

## Why a `.template`-suffixed mirror instead of plain source?

- The `.template` suffix prevents the skeleton files from being picked up by the runnable `service-template` subproject's compile classpath (the runnable subproject reads from `service-template/src/main/`, NOT `service-template/skeleton/src-main/`).
- The runnable subproject (under `src/main/`) and the skeleton tree are kept in sync MANUALLY. Any change to one must be applied to the other; Plan 01-07 Task 7's drift verification catches stale mirrors at the end of Phase 1.
- See `01-07-PLAN.md` `<objective>` "CD-02 Hybrid Decision Rationale" for the design trade-off.
```

**Required sections for the recon README** (executor must include all):
1. **What this is** — one paragraph: throwaway Phase-2 Playwright project, NOT a Gradle module, runs once, writes to `.planning/intel/`.
2. **How to run from a fresh clone** — `cd tools/recon && npm install && npx playwright install chromium && npm run capture && npm run assemble && npm run check`. Mention each step's purpose.
3. **Anti-bot fallback ladder** — exact wording from RESEARCH §"Common Pitfalls #1": (1) the configured posture (UA + `--disable-blink-features=AutomationControlled` + `headless:false`), (2) if that fails, switch to `chromium.launchPersistentContext(userDataDir)` per RESEARCH §"Code Examples — Verified pattern: persistent context".
4. **What it writes & where** — table: `screenshots/*.png → .planning/intel/screenshots/`, `output/*.json → tools/recon/output/ (gitignored)`, `n11-recon.md → .planning/intel/n11-recon.md`.
5. **What it does NOT do** — two bullet points: no real login, no real cart adds (anti-pattern guard from RESEARCH §"Anti-Patterns to Avoid").
6. **Re-running individual specs** — `npx playwright test homepage.spec.ts` (per RESEARCH §"Validation Architecture — Sampling Rate").

---

### `tools/recon/tests/homepage.spec.ts` (and 6 sibling specs) (recon-spec)

**Analog:** external — RESEARCH §"Pattern 1: Playwright capture script" lines 285-376 is the canonical full example. The other 6 specs (`category`, `pdp`, `cart`, `checkout`, `account`, `login`) are structural copies of `homepage.spec.ts` with: (a) different URL in `page.goto`, (b) different element-zoom selector, (c) different `data.page` slug in the harvest JSON. **Use Pattern 1 verbatim for `homepage.spec.ts`; clone-and-modify for the rest.**

**Reason no in-repo analog:** no Playwright code in repo.

**Shape contract (binding for all 7 specs):**
1. **One URL per `test()`** — no shared fixtures yet, no `test.beforeAll` that does network. (Refactoring to fixtures is out of scope for Phase 2; spec files stay self-contained for the throwaway nature of the project.)
2. **Screenshots write to `../../.planning/intel/screenshots/<slug>-fullpage.png`** (relative path resolved from the spec's `__dirname`), **not** to a `tools/recon/screenshots/` folder. RESEARCH §"Anti-Patterns" line 510 is explicit on this: "Storing screenshots inside `tools/recon/`. They go directly into `.planning/intel/screenshots/`."
3. **Exit non-zero on any 4xx response from `page.goto`** — Playwright's default is to *not* fail on 4xx (the default `assert.ok(response)` is only on connection failure). Each spec must add `expect(response?.status()).toBeLessThan(400)` immediately after `page.goto`.
4. **Call `dismissBanners(page)` from `lib/dismiss-banners.ts` before screenshotting** — the cookie banner dominates the screenshot otherwise (Pitfall #2 in RESEARCH).
5. **Harvest two JSONs per page** — `<slug>-phrases.json` and `<slug>-tokens.json` written to `output/` (gitignored). The assembler reads from there.
6. **Phrase count assertion** — `expect(phrases.length).toBeGreaterThan(5)` per spec (RESEARCH §"Pattern 1" line 373).
7. **Per-page slug allowed list** — `homepage`, `category-elektronik`, `pdp`, `cart`, `checkout-step1`, `account`, `login`. Validation script `check-phase-02-artifacts.sh` greps for these exact slugs.

**Per-spec URL + element-zoom map** (executor fills the URL guesses; recon will validate or correct):

| Spec | Initial URL guess (assumption A2 in RESEARCH) | Element-zoom selector |
|------|------|------|
| `homepage.spec.ts` | `https://www.n11.com/` | `header` (nav strip) |
| `category.spec.ts` | `https://www.n11.com/elektronik` (or whatever the visible link points to from homepage) | left-rail filters: `aside, [class*="filter" i]` |
| `pdp.spec.ts` | first product link clicked from category page (do **not** hardcode a URL — products go out of stock) | `[class*="product-detail" i]`, `[class*="basket" i]` |
| `cart.spec.ts` | `https://www.n11.com/sepetim` | `[class*="cart" i]` (likely shows empty-cart state) |
| `checkout.spec.ts` | follow "Checkout" CTA from cart page (will likely redirect to login — capture the redirect target) | n/a (capture full-page only) |
| `account.spec.ts` | `https://www.n11.com/hesabim` (likely redirects to `/giris`) | `[class*="login" i]` form |
| `login.spec.ts` | `https://www.n11.com/giris` | `form` (login form fields) |

**Anti-patterns (carry into spec):** never log in, never click "Add to Cart" against a real product (RESEARCH §"Anti-Patterns to Avoid").

---

### `tools/recon/lib/dismiss-banners.ts` (recon-helper)

**Analog:** external — RESEARCH §"Pattern 3: Cookie-banner / login-wall handling" lines 435-460 is the canonical implementation. **Use that excerpt verbatim.** No in-repo analog.

**Critical defensive idiom (do not skip):** every locator interaction guards with `await locator.count() > 0 && await locator.first().isVisible().catch(() => false)` before `.click()` — Pitfall in RESEARCH line 462: "calling `.click()` on `.first()` of an empty locator throws."

---

### `tools/recon/lib/harvest-copy.ts` (recon-helper)

**Analog:** external — extract the in-line `page.evaluate` block from RESEARCH §"Pattern 1" lines 322-335 into a standalone exported function. The logic doesn't change; only the location does (so all 7 specs share one TR-text harvester).

**Shape contract:**
1. Export `harvestCopy(page: Page): Promise<string[]>` — returns deduplicated phrase list (1-6 words, Turkish-charset filter).
2. The Turkish charset regex from RESEARCH line 331: `/^[a-zA-Z0-9ÇĞİÖŞÜçğıöşü\s.,'!?₺%/-]+$/` — keep exactly. The `₺` and `%` are intentional (price phrasing like "%50 indirim" and "1.299 ₺").
3. Tag whitelist from RESEARCH line 325: `'a, button, span, h1, h2, h3, p, li'` — keep exactly. RESEARCH §"Pattern 1" anti-pattern flag (line 378): "do not loop over all `<*>` elements."
4. The function does **not** write JSON itself — that's the spec's job (one harvest call per spec, then `fs.writeFileSync` per page slug).
5. UTF-8 only — RESEARCH §"Common Pitfalls #3" — but `page.evaluate` returns native JS strings, so encoding doesn't bite at this layer; the Pitfall #3 risk is in the *consumer* (`fs.writeFileSync` must pass `'utf-8'`).

---

### `tools/recon/lib/harvest-colors.ts` (recon-helper)

**Analog:** external — extract the `page.evaluate` block from RESEARCH §"Pattern 1" lines 343-366 into a standalone exported function, **plus** the `rgbToHex` helper from RESEARCH §"Common Pitfalls #4" lines 581-587.

**Shape contract:**
1. Export `harvestColors(page: Page): Promise<TokenRow[]>` where `TokenRow = { role, selector, color, backgroundColor, fontFamily, fontWeight, fontSize }`.
2. Selector list from RESEARCH lines 359-365: `header / body / button[class*="add" i] / a / h1` — these are *best-effort* per RESEARCH; recon may extend them per-page.
3. **Do NOT call `rgbToHex` inside `harvestColors`** — keep colors as `rgb(...)` strings at harvest time so JSON files preserve ground-truth. Conversion happens in `assemble-recon.ts` so the report is the only file that has hex. RESEARCH §"Common Pitfalls #4" line 578: "the assembler script converts each `rgb(R, G, B)` to `#RRGGBB` before writing the Markdown table."
4. Re-export `rgbToHex` from this module (so the assembler imports it from one place):
   ```typescript
   export function rgbToHex(rgb: string): string {
     const m = rgb.match(/^rgba?\((\d+),\s*(\d+),\s*(\d+)/);
     if (!m) return rgb;
     const [r, g, b] = [+m[1], +m[2], +m[3]];
     return '#' + [r, g, b].map(x => x.toString(16).padStart(2, '0').toUpperCase()).join('');
   }
   ```

---

### `tools/recon/assemble-recon.ts` (tooling-script)

**Analog:** external — RESEARCH §"Pattern 4: Recon assembler" lines 470-501 is the skeleton. **The §"Recon Report Schema" section (RESEARCH lines 856-1003) is the binding contract for the Markdown output** — section headings, table column names, and ordering must match exactly. Pitfall #5 in RESEARCH line 591: "Phase 10 wastes time hunting" if the schema drifts.

**Reason no in-repo analog:** no node tooling scripts in repo.

**Shape contract:**
1. Reads from `tools/recon/output/*-phrases.json` and `tools/recon/output/*-tokens.json`.
2. Writes a *single* file: `.planning/intel/n11-recon.md` (resolved as `path.resolve(__dirname, '../../.planning/intel/n11-recon.md')`).
3. The 8 sections from RESEARCH §"Recon Report Schema" must appear **in order** with **exact heading text**:
   1. `## 1. Page Inventory`
   2. `## 2. Turkish Copy Catalog`
   3. `## 3. Category Taxonomy`
   4. `## 4. Color Token Table`
   5. `## 5. Typography Notes`
   6. `## 6. Layout Patterns`
   7. `## 7. Anti-pattern flags (what we will NOT copy)`
   8. `## 8. Open n11 questions for Phase 10 / 11`
4. Color Token Table column order: `| Token | Hex | Source page | Computed source (rgb) |` — Phase 10 reads these by column name.
5. Apply `rgbToHex(...)` to every harvested color before writing the table (RESEARCH §"Common Pitfalls #4").
6. Always pass `'utf-8'` to `fs.readFileSync` and `fs.writeFileSync` — RESEARCH §"Common Pitfalls #3" mojibake mitigation.
7. Idempotent — re-running with the same `output/*.json` produces byte-identical Markdown.
8. Logs the unique phrase count to stdout for the executor's sanity check (e.g., `Wrote .planning/intel/n11-recon.md with 47 unique phrases`).

---

### `tools/recon/check-recon.ts` (tooling-script)

**Analog:** external — no published reference; small bespoke script. Implementation is straight Node `fs` + regex grep.

**Shape contract:** the script verifies the assembled `n11-recon.md` against minimum thresholds and exits non-zero on any failure. Required checks (executable mirror of `02-VALIDATION.md` rows):
1. File exists at `.planning/intel/n11-recon.md`.
2. All 8 section headings present and in order.
3. Turkish Copy Catalog table has **≥30** distinct phrase rows (REQUIREMENT FE-01 deliverable threshold; mirrors `02-VALIDATION.md` line 47 awk-based check).
4. Color Token Table has **≥10** named tokens.
5. All 7 expected screenshots exist under `.planning/intel/screenshots/`: `homepage-fullpage.png`, `category-elektronik-fullpage.png`, `pdp-fullpage.png`, `cart-fullpage.png`, `checkout-step1-fullpage.png`, `account-fullpage.png`, `login-fullpage.png`.
6. No `rgb(` substrings remain in the Color Token Table (Pitfall #4 evidence — every cell got hex-converted).
7. Print a one-line summary on success: `RECON OK — N phrases, M tokens, K screenshots`.

The script is idempotent and read-only on the filesystem.

---

### `scripts/check-phase-02-artifacts.sh` (tooling-script)

**Analog:** external — bash + POSIX. **No in-repo `scripts/` directory exists yet** (Phase 1 used Gradle tasks for everything). Creating one is fine; future phases will likely reuse.

**Shape contract:** dual-mode bash script invoked as `bash scripts/check-phase-02-artifacts.sh` or `bash scripts/check-phase-02-artifacts.sh --bootstrap`.
- **Default mode:** runs all the file-existence + grep-count checks from `02-VALIDATION.md` "Per-Task Verification Map" — every "shape" automated command in the table. Exits non-zero on any failure. Should complete in **< 2s** (no network, no Playwright run).
- **`--bootstrap` mode:** for Wave 0 — skips checks that depend on the captures landing (i.e., all `.planning/intel/*` checks); only verifies the `tools/recon/` scaffold exists. Exits 0 on a fresh Wave-0-only state.
- Use `set -euo pipefail` at the top.
- Use `LF` line endings — repo `.gitattributes` line 7 already enforces `*.sh text eol=lf`.
- Print one line per check: `OK: <description>` or `FAIL: <description>`. Sum failures at the end and return that count as exit code (capped at 1).

---

### `.planning/intel/n11-recon.md` (artifact-output, committed)

**Analog:** **`.planning/research/SUMMARY.md`** — read for **Markdown author voice and table style only**. The structural schema is fully owned by RESEARCH §"Recon Report Schema" (lines 856-1003).

**Excerpt to mirror in style** (`.planning/research/SUMMARY.md` lines 1-9):
```markdown
# Project Research Summary

**Project:** n11 Bootcamp Final Case — Agentic E-Commerce Clone
**Domain:** Turkish online marketplace (Spring Boot microservices) + agentic-commerce layer (Gemini + MCP)
**Researched:** 2026-04-28
**Confidence:** HIGH (Spring/JVM stack, architecture, pitfall prevention); MEDIUM (Gemini 3 model ID, Iyzico sample location)
```

**What to mirror:**
- Top-level `# Title` plus 3-5 metadata lines (`**Project:**`, `**Researched:**`, etc.).
- Tables use the `| Col | Col |` pipe-aligned style with explicit divider row (the recon report has 4-5 tables; all should look identical).
- Confidence/source citations end of report — pattern from `SUMMARY.md` §Sources lines 1145-1180.

**What NOT to mirror:** SUMMARY.md is research synthesis (multi-paragraph executive summary first); the recon report is *evidence + tables*, not narrative. Section §1-§8 from RESEARCH schema is the contract, not SUMMARY's structure.

---

### `.planning/intel/screenshots/<page>-fullpage.png` × 7 (artifact-output, committed binary)

**Analog:** none — first PNG binaries committed to the repo.
**Reason no in-repo analog:** repo has been all-text-source until now.
**Shape contract:**
1. **Naming:** exactly the slug list above (`homepage-fullpage.png`, etc.). One per spec. Element-zoom screenshots (e.g., `homepage-header-element.png`) are optional but **encouraged where the element is high-signal** (header, PDP CTA, cart row) — RESEARCH §"Pattern 1" line 314 demonstrates the element-screenshot pattern.
2. **Size sanity:** each PNG should be ≥ 50 KB. A < 10 KB PNG is almost certainly a Cloudflare interstitial captured instead of the real page (Pitfall #1 in RESEARCH).
3. **Manual verification gate:** `02-VALIDATION.md` "Manual-Only Verifications" requires human spot-check that screenshots show the *intended n11 page*, not a 403 wall or captcha overlay. Automated pixel checks are out of scope.
4. **Git LFS not needed** — 7 PNGs at typical sizes (<2 MB total) won't bloat the repo.

---

### `.gitignore` (repo root, **edited file**)

**Analog:** **itself** — `/.gitignore` lines 1-29 (read above). Append-only edit.
**Shape contract:** **add a new commented section at the end of the file** (matching the existing `# Build outputs` / `# Secrets — NEVER commit` / `# IDE` / `# OS` / `# Test artifacts` style):
```gitignore
# Playwright recon (tools/recon/)
tools/recon/node_modules/
tools/recon/test-results/
tools/recon/playwright-report/
tools/recon/output/
tools/recon/.playwright/
```
**Forbidden:** rewriting existing lines or reordering sections. The 5 patterns above are additive only. RESEARCH §"Recommended Project Structure" lines 269-277 is the source of these patterns.

---

### `.planning/PROJECT.md` § Key Decisions (**edited file**)

**Analog:** **itself** — the existing 16-row Key Decisions table at lines 109-125. Append-only edit at the bottom of the table.
**Excerpt to mirror** (`.planning/PROJECT.md` lines 109-114):
```markdown
| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Java 21 + Spring Boot 3.x | LTS, virtual threads, modern record/pattern-matching syntax — strong "modern stack" signal to graders | — Pending |
| 13-service decomposition (eureka + config + gateway + identity + product + inventory + cart + order + payment + notification + search + mcp + ai-service) | Comfortably exceeds the 10-service mandate; every service has a clearly bounded responsibility | — Pending |
```

**Shape contract for the new row:**
1. **One row** with the decision: `Frontend toolchain: Vite 8 + React 19 SPA + TypeScript strict + Tailwind 4 + Zustand 5 + React Router 7 + TanStack Query 5 + react-hook-form + zod`.
2. **Rationale column must reference at least 2 specific recon findings** (per `02-VALIDATION.md` Manual-Only row 3): e.g., "n11 PDP is fully client-rendered after initial HTML — no SSR-only data we'd lose by going SPA" and "n11 has no in-storefront chat panel, so Phase 11's floating bubble UX is greenfield — Vite+React owns that DOM cleanly with no RSC re-render gymnastics."
3. **Rationale must call out `VITE_API_BASE_URL` env-var convention** (RESEARCH §"Pitfall #23 carryover" line 645-647 — prevents Phase 10 from hardcoding `http://localhost:8080`).
4. **Outcome column: `Locked 2026-04-29 (Phase 2 — see .planning/intel/n11-recon.md decision matrix)`**. Match the existing "Locked YYYY-MM-DD" format used in row line 125.
5. The decision matrix table itself (RESEARCH §"Decision Matrix" lines 833-848) lives in the recon report under a "Decision Matrix" subsection — referenced by the Key Decisions row, not duplicated in PROJECT.md.

---

### `.planning/PROJECT.md` § Open Questions (**edited file**)

**Analog:** **itself** — lines 127-137. The existing Open Question on line 133 (`Frontend toolchain — Vite + TypeScript + Tailwind + Zustand (likely) vs Next.js…`) gets **resolved** by Phase 2.
**Shape contract:**
1. **Move** the "Frontend toolchain" bullet from the open list (lines 127-133) to the `### Resolved` section (line 135).
2. Match the existing resolved-line format from line 137: `- **Frontend toolchain** — RESOLVED 2026-04-29 (Phase 2): Vite 8 + React 19 SPA. See Key Decisions row and `.planning/intel/n11-recon.md` for the decision matrix.`
3. Do NOT delete the "Public tunnel choice" or "MCP server tunnel exposure" open questions — those resolve in Phase 6 / Phase 9.

---

### `tools/recon/output/*-phrases.json`, `*-tokens.json` (intermediate artifacts, gitignored)

**Analog:** none, no shape contract needed — fully internal to recon. Format derives from the harvest helpers above. Not committed. Listed here only for completeness.

---

### `npx playwright install chromium` (tooling side-effect, no committed file)

**Analog:** external — Playwright docs.
**Shape contract:** must run **after** `npm install` (so the `playwright` CLI is available) and **before** the first capture run. Document the command in `tools/recon/README.md` step 2. Downloads only Chromium (~170 MB), not Firefox/WebKit (RESEARCH line 112). Adds the binary to the user's `~/.cache/ms-playwright/` — not to the repo.

---

## Shared Patterns

### S-1. UTF-8 file I/O on every read/write
**Source:** RESEARCH §"Common Pitfalls #3" lines 559-570.
**Apply to:** `assemble-recon.ts`, `check-recon.ts`, harvest-spec output writes.
**Rule:** every `fs.readFileSync` and `fs.writeFileSync` passes `'utf-8'` explicitly. Spot-check after assembly: `grep -c 'Önceki' .planning/intel/n11-recon.md` — if Turkish chars appear as `?` or `Ã–`, the encoding is broken.
```typescript
fs.writeFileSync(path, content, 'utf-8');
const data = fs.readFileSync(path, 'utf-8');
```

---

### S-2. Hard-fail on 4xx from `page.goto`
**Source:** Playwright default does NOT fail on 4xx — bespoke pattern for this phase.
**Apply to:** all 7 `<page>.spec.ts` files.
**Rule:** every `await page.goto(...)` call captures the response and asserts status < 400. RESEARCH §"Common Pitfalls #1" line 545 warning sign: "screenshot shows a Cloudflare challenge page" — this assertion is the safety net.
```typescript
const response = await page.goto('https://www.n11.com/', { waitUntil: 'networkidle' });
expect(response?.status()).toBeLessThan(400);
```

---

### S-3. Defensive locator interaction (always guard before .click())
**Source:** RESEARCH §"Pattern 3" lines 446-462 explicitly calls this out.
**Apply to:** `dismiss-banners.ts` and any spec that interacts with optional UI (cookie banner, login modal close button, mega-menu hover dropdown).
**Rule:**
```typescript
if (await locator.count() > 0 && await locator.first().isVisible().catch(() => false)) {
  await locator.first().click().catch(() => {});
}
```
The empty `.catch(() => {})` swallows the click error if the banner closes mid-click (race), which is acceptable for recon.

---

### S-4. Gentle rate limit (do not flood n11)
**Source:** RESEARCH §"Common Pitfalls #7" lines 611-623.
**Apply to:** `playwright.config.ts` (workers/parallel knobs) + every spec (post-`page.goto` `waitForTimeout`).
**Rule:** `workers: 1`, `fullyParallel: false`, plus `await page.waitForTimeout(2500)` after each `page.goto` — measured 90s total recon, well within budget. Do **not** dial parallelism up to "make recon faster" — Pitfall #7 directly forbids it.

---

### S-5. Screenshots write OUT of `tools/recon/`, into `.planning/intel/screenshots/`
**Source:** RESEARCH §"Anti-Patterns to Avoid" line 510. Storing screenshots inside `tools/recon/` is explicitly forbidden because Phase 10 references them by relative path from `.planning/intel/`.
**Apply to:** all 7 specs.
**Rule:** every screenshot path resolves through:
```typescript
const SCREENSHOT_DIR = path.resolve(__dirname, '../../../.planning/intel/screenshots');
```
Note the **three** `..` segments — `__dirname` is `tools/recon/tests/`, so we ascend three levels (tests → recon → tools) before descending into `.planning/intel/screenshots/`. Off-by-one here is the most likely silent bug.

---

### S-6. Keep recon **outside** Gradle
**Source:** RESEARCH §"Recommended Project Structure" line 267 + S-1 hand-off-rule against accidental Java toolchain pollution.
**Apply to:** `tools/recon/` directory (entire).
**Rule (negative pattern, contrast with `gradle/libs.versions.toml`):**
- Do **NOT** add `tools/recon` to `settings.gradle.kts` `include(...)`.
- Do **NOT** add a `tools/recon/build.gradle.kts`.
- Do **NOT** add Playwright or Node dependencies to `gradle/libs.versions.toml` (the existing libs.versions.toml above only owns Java/Spring versions — see lines 1-20: java, spring-boot, spring-cloud, springdoc, flyway, logstash-logback, testcontainers, networknt-json-schema). Recon's npm pins live exclusively in `tools/recon/package.json`.
- Do **NOT** declare a Gradle task that runs Playwright (e.g., `tasks.register("recon")`) — keep the recon project fully npm-driven.

This contrasts with the only "test-only" in-repo precedent, **`infra-tests/`**, which IS a Gradle module (see `infra-tests/build.gradle.kts` lines 18-32 declaring `testImplementation` deps). The recon project deliberately rejects that pattern. Executor must not be tempted by the surface similarity ("standalone test project under repo root").

**Excerpt from `infra-tests/build.gradle.kts` lines 7-10 (read for *what NOT to do* in tools/recon/):**
```kotlin
// Plugin discipline (Cross-Cutting #1): `java` plugin ONLY, applied at root
// subprojects {} block. No Spring Boot plugin — this module never boots Spring.
// No `src/main`: pure test module.
```
That comment is the right energy ("plugin discipline, no scope creep") but the **applied stack is wrong** for recon — recon's discipline is "no Gradle at all."

---

### S-7. Versions pinned exact, not ranged
**Source:** RESEARCH §"Standard Stack" header note on `dist-tags.latest` verification + the existing `gradle/libs.versions.toml` discipline (every Java dep has an exact `version.ref`).
**Apply to:** `tools/recon/package.json`.
**Rule:** `"@playwright/test": "1.59.1"` — no `^`, no `~`. Re-verify with `npm view @playwright/test version` at install time and use the latest 1.59.x patch published by then. RESEARCH §"Standard Stack" line 188: "Implementer must re-run `npm view <pkg> version` for each before locking exact pins in `package.json`."

This mirrors the in-repo Gradle TOML excerpt (`gradle/libs.versions.toml` lines 1-9), where every version is exact:
```toml
[versions]
java = "21"
spring-boot = "3.5.14"
spring-cloud = "2025.0.0"
testcontainers = "2.0.5"
```
Not `"3.5.+"`, not `"latest"` — exact strings. The recon `package.json` adopts the same posture in the npm idiom (no `^` prefix).

---

### S-8. Anti-bot posture is a *single* config decision, do not splatter
**Source:** RESEARCH §"Common Pitfalls #1" lines 533-547.
**Apply to:** `playwright.config.ts` (the *only* place that should set UA, headless, slowMo, args).
**Rule:** never re-set `headless`, `userAgent`, `slowMo`, or `--disable-blink-features=AutomationControlled` inside individual specs. If a spec needs a different posture (very unlikely — fallback only), it goes through a new `projects[]` entry in `playwright.config.ts`, not via `test.use({...})` overrides scattered in spec files.

---

## No Analog Found (summary)

Files with **zero** in-repo precedent (planner should rely on RESEARCH and the shape contracts above):

| File | Role | Why no in-repo analog |
|------|------|----|
| `tools/recon/package.json` + `package-lock.json` | recon-config | repo is Java/Gradle — zero existing npm projects |
| `tools/recon/playwright.config.ts` | recon-config | no Playwright code in repo |
| `tools/recon/tests/<page>.spec.ts` × 7 | recon-spec | no TypeScript code anywhere yet |
| `tools/recon/lib/dismiss-banners.ts` | recon-helper | no TypeScript code; bespoke helper for one-shot recon |
| `tools/recon/lib/harvest-copy.ts` | recon-helper | bespoke DOM extractor |
| `tools/recon/lib/harvest-colors.ts` | recon-helper | bespoke DOM extractor + rgb→hex helper |
| `tools/recon/assemble-recon.ts` | tooling-script | no Node tooling in repo; first Markdown templating script |
| `tools/recon/check-recon.ts` | tooling-script | no Node tooling in repo |
| `scripts/check-phase-02-artifacts.sh` | tooling-script | no `scripts/` dir yet (Phase 1 used Gradle tasks) |
| `.planning/intel/screenshots/*.png` × 7 | binary artifact | first committed binaries in repo |

**Mitigation pattern:** every "no analog" row above has a **shape contract** in the per-file section. The planner pastes the shape contract into the task's `<acceptance_criteria>` block; the executor pastes the RESEARCH excerpt (verbatim where Pattern 1-4 are referenced) into the task's source-of-truth block.

---

## In-Repo Analogs Used (summary)

| Analog file | Used by | What was extracted |
|---|---|---|
| `/.gitignore` (lines 1-29) | `tools/recon/.gitignore` (style) + root `.gitignore` (edit target) | Comment-grouped section style; LF-only line endings; "NEVER commit" callout posture |
| `service-template/skeleton/README.md` (lines 1-10) | `tools/recon/README.md` | Author voice: terse heading, "Why X" rationale, table for token lookup, code blocks with shell-comment context |
| `.planning/research/SUMMARY.md` (lines 1-9) | `.planning/intel/n11-recon.md` | Title + metadata-block convention; pipe-aligned table style; confidence/source citation pattern |
| `.planning/PROJECT.md` § Key Decisions (lines 109-125) | edits to PROJECT.md Key Decisions | Row format: `\| Decision \| Rationale \| Outcome \|`; "Locked YYYY-MM-DD" outcome convention |
| `infra-tests/build.gradle.kts` (lines 7-10) | `tools/recon/` (negative analog — what NOT to do) | "Plugin discipline, no scope creep" energy — but recon goes further: no Gradle at all |
| `gradle/libs.versions.toml` (lines 1-9) | `tools/recon/package.json` (negative analog) | Exact-version pinning discipline carries across to npm; **but** recon's pins do NOT live in this Gradle file |

---

## Metadata

**Analog search scope:** `/hey/projects/n11-bootcamp-final-case/{infra-tests,service-template,gradle,.planning,.gitignore,.gitattributes}` — read-only.
**Files scanned:** 9 (`.gitignore`, `.gitattributes`, `infra-tests/build.gradle.kts`, `infra-tests/src/test/java/com/n11/infra/CrossSchemaDenyTest.java`, `infra-tests/src/test/resources/init.sh` head, `service-template/skeleton/README.md`, `gradle/libs.versions.toml`, `.planning/PROJECT.md`, `.planning/research/SUMMARY.md`).
**External authoritative references the executor must follow:**
- [Playwright — Screenshots](https://playwright.dev/docs/screenshots)
- [Playwright — Page API (page.evaluate)](https://playwright.dev/docs/api/class-page)
- [Playwright — BrowserType (chromium.launch options)](https://playwright.dev/docs/api/class-browsertype)
- [Playwright — Test Configuration](https://playwright.dev/docs/test-configuration)
- RESEARCH §§"Pattern 1-4", "Code Examples", "Recon Report Schema", "Common Pitfalls #1-7" (the 02-RESEARCH.md sibling file is the operational source-of-truth; this PATTERNS.md is the *honesty layer* about which copy-from-codebase analogs do and don't exist).

**Pattern extraction date:** 2026-04-29.
