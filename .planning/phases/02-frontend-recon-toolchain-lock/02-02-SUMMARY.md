---
phase: 02-frontend-recon-toolchain-lock
plan: 02
subsystem: frontend-recon
tags: [phase-2, frontend-recon, playwright, n11-capture]
requires:
  - "02-01 (recon scaffold: helpers, config, assembler, checker)"
  - "Xvfb on host (installed during this plan — needed because pts/0 has no DISPLAY)"
provides:
  - "7 full-page screenshots under .planning/intel/screenshots/"
  - ".planning/intel/n11-recon.md (8-section recon report — 644 phrases, 25 tokens)"
  - "Cross-plan contract pin: artifact-lint exits 1 with exactly one Plan-03 gap"
affects:
  - "Plan 02-03 (Wave 2) — must close the PROJECT.md Key Decisions Frontend toolchain row"
  - "Phase 10 (Storefront) — consumes screenshots + recon.md as the n11 hand-off contract"
  - "Phase 11 (Chat Bubble) — §7 'no chat panel observed' callout drives floating-bubble UX invention"
tech-stack:
  added:
    - "@playwright/test 1.59.1 (already pinned in 02-01)"
    - "Xvfb 2:21.1.12 (host-level — virtual framebuffer for headless:false on a tty session)"
  patterns:
    - "PATTERNS S-2: every spec hard-fails on 4xx response from page.goto"
    - "PATTERNS S-5: 3-segment relative path from tests/__dirname to .planning/intel/screenshots/"
    - "PATTERNS S-8: anti-bot posture lives only in playwright.config.ts (no spec-level overrides)"
key-files:
  created:
    - "tools/recon/tests/homepage.spec.ts"
    - "tools/recon/tests/category.spec.ts"
    - "tools/recon/tests/pdp.spec.ts"
    - "tools/recon/tests/cart.spec.ts"
    - "tools/recon/tests/checkout.spec.ts"
    - "tools/recon/tests/account.spec.ts"
    - "tools/recon/tests/login.spec.ts"
    - ".planning/intel/screenshots/homepage-fullpage.png"
    - ".planning/intel/screenshots/category-elektronik-fullpage.png"
    - ".planning/intel/screenshots/pdp-fullpage.png"
    - ".planning/intel/screenshots/cart-fullpage.png"
    - ".planning/intel/screenshots/checkout-step1-fullpage.png"
    - ".planning/intel/screenshots/account-fullpage.png"
    - ".planning/intel/screenshots/login-fullpage.png"
    - ".planning/intel/n11-recon.md"
  modified:
    - "tools/recon/playwright.config.ts (added test timeout: 120_000)"
    - "tools/recon/assemble-recon.ts (fixed -- prefix preservation)"
    - "scripts/check-phase-02-artifacts.sh (tightened PROJECT.md Key Decisions check)"
decisions:
  - "Use xvfb-run -a --server-args='-screen 0 1440x900x24' for capture orchestration on tty hosts; preserves headless:false anti-bot posture without a real desktop session."
  - "n11's actual login path is /giris-yap (NOT /giris); plan assumption was wrong."
  - "Cross-plan contract is pinned by tightening the lint regex, not by leaving a soft pre-existing match — Plan 02-03 must lock the row, not just edit the rationale."
metrics:
  duration: "~5 min capture wall-clock (2.1 min Playwright run + assemble + check)"
  tasks: 3  # all tasks complete — Task 3 human-verify approved 2026-04-29
  completed: "2026-04-29"
---

# Phase 02 Plan 02: n11.com Page Captures + Recon Report Summary

Captured the public surface of n11.com for the 7 plan-mandated page slugs (homepage / category-elektronik / pdp / cart / checkout-step1 / account / login), assembled `.planning/intel/n11-recon.md` from harvest JSONs, and pinned the cross-plan contract so Plan 02-03 has exactly one expected gap to close.

## Per-page phrase harvest counts

Ground truth from `tools/recon/output/<slug>-phrases.json`:

| Slug | Phrases harvested | Captured URL |
| --- | ---: | --- |
| homepage | 359 | `https://www.n11.com/` |
| category-elektronik | 214 | `https://www.n11.com/elektronik` |
| pdp | 269 | `https://www.n11.com/urun/apple-macbook-air-mc6t4tua-16-gb-256-gb-ssd-136-macos-dizustu-bilgisayar-74956976?magaza=troyapple` (DERIVED — first product card on /elektronik) |
| cart | 123 | `https://www.n11.com/sepetim` (anonymous empty-cart with full mega-menu) |
| checkout-step1 | 116 | `https://www.n11.com/genel/odeme-secenekleri-393251` (CTA followed; landed on payment-options info page) |
| account | 10 | `https://www.n11.com/giris-yap?redirectUrl=/hesabim` (redirect to login as expected) |
| login | 8 | `https://www.n11.com/giris-yap` (corrected from /giris which 404s) |

Every page exceeded the per-spec threshold of `phrases.length > 5`.

## Final recon-report counts (after dedup)

- **Phrases (§2 Turkish Copy Catalog):** 644 unique rows after dedup across all seven harvest files (well above the ≥30 threshold).
- **Color tokens (§4 Color Token Table):** 25 rows (≥10 threshold) — covers `--color-body-bg`, `--color-body-bg-bg`, `--color-link`, `--color-heading-primary` × 5 source pages, all hex-only (Pitfall #4).
- **Screenshots (committed binaries):** 7 full-page (all ≥50 KB; smallest = login at 50KB, largest = homepage at 3.0MB) + 3 best-effort element-zoom shots (account-login, cart, pdp-cta — homepage `header` and category `aside` and login `form` zoom locators didn't resolve in time, treated as best-effort per plan spec).

## Anti-bot fallback used? **No (rung 1)**

Captures completed on rung 1 of the anti-bot ladder: configured Playwright posture (headless:false + real desktop UA + `--disable-blink-features=AutomationControlled` + slowMo:250 + tr-TR locale + Europe/Istanbul timezone). No 403, no Cloudflare interstitial, no captcha across 7 pages. The `launchPersistentContext` rung was not invoked.

The host environment did require Xvfb to satisfy the headless:false flag from a tty SSH session — see Deviations.

## URL drift from RESEARCH §3 page-inventory guesses

| Slug | RESEARCH guess | Actual captured URL | Notes |
| --- | --- | --- | --- |
| login | `https://www.n11.com/giris` | `https://www.n11.com/giris-yap` | `/giris` returns 404; n11 uses `/giris-yap`. Fixed in `tools/recon/tests/login.spec.ts`. **Action for Plan 02-03 §8 enrichment:** call out in Open n11 Questions that future references to login in REQUIREMENTS / Phase 10 must use `/giris-yap`. |
| account | `https://www.n11.com/hesabim` (likely redirects to /giris) | redirected to `https://www.n11.com/giris-yap?redirectUrl=/hesabim` | Predicted redirect was correct in shape, wrong in target path; same root-cause. |
| checkout-step1 | follow CTA from cart, "likely a login prompt" | `https://www.n11.com/genel/odeme-secenekleri-393251` | The cart's "Hemen Giriş Yap" CTA navigates to a generic payment-options info page, NOT the login form. n11 evidently shows this page when the cart is empty. The capture is rich (116 phrases including full mega-menu) — Plan 10 still gets the "what does anonymous-checkout look like" signal. |
| pdp | first product card from /elektronik | landed on a real Apple MacBook Air listing (74956976) | URL is per-product and ephemeral; do not hardcode in tests. |

## Open n11 questions for §8 (Plan-03 enrichment input)

These were unresolved during recon and should drive Plan 02-03's §8 enrichment:

1. **Login path canonical form.** RESEARCH guessed `/giris`; actual is `/giris-yap`. Update FE-02 / FE-04 references and the Phase 10 router config to match.
2. **Anonymous-cart "checkout" CTA semantics.** Pressing the cart-page CTA on an empty cart redirected to a generic payment-options info page (`/genel/odeme-secenekleri-393251`), not a login form. Phase 10 should NOT model that flow — likely a quirk of n11's empty-cart CTA wiring. Ship our own anonymous-cart-protect-checkout flow instead.
3. **Header element-zoom didn't render.** The homepage `header` element-zoom screenshot did not capture (likely the locator resolved before sticky-positioned content settled). Not blocking — fullpage screenshot conveys the same info.
4. **PDP CTA element-zoom captured a 1px shell** (710-byte PNG). The `[class*="product-detail" i], [class*="basket" i]` selector matched a near-zero-size container on the captured PDP. Not blocking — fullpage screenshot has the CTA cluster.
5. **Mega-menu hover dropdown** was attempted on `a[href*="elektronik"]`; non-blocking try/catch. Hover did fire (1s `waitForTimeout`) before fullpage screenshot, but the mega-menu state in the screenshot needs human verification (Plan 02-03 §8 may want to escalate to a Locator-based hover approach if the mega-menu is missing from the homepage shot).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Installed Xvfb on host so headless:false works on a tty SSH session.**
- **Found during:** Task 2 Step 2 (smoke test) — `headless:false` Playwright launch failed with `Missing X server or $DISPLAY`. Per worktree-base SSH session has no `$DISPLAY`.
- **Fix:** Installed `xvfb` via `sudo apt-get install -y xvfb` (passwordless sudo configured); ran captures via `xvfb-run -a --server-args='-screen 0 1440x900x24' npx playwright test`. Anti-bot posture preserved end-to-end (still `headless:false` from Playwright's POV).
- **Files modified:** none in repo (host-level installation only).
- **Commit:** 43b95e9 (described in commit body).

**2. [Rule 3 - Blocking] Bumped per-test timeout in playwright.config.ts from 30s default to 120s.**
- **Found during:** First homepage smoke test under Xvfb — Playwright's default 30s test-timeout is consistently shorter than the 2.5s post-`goto` wait + slowMo:250 + networkidle on n11's tracker-heavy homepage. The `await page.waitForTimeout(2500)` itself blew the budget.
- **Fix:** Added `timeout: 120_000` at the top level of `defineConfig({...})` in `tools/recon/playwright.config.ts`.
- **Files modified:** `tools/recon/playwright.config.ts`.
- **Commit:** 43b95e9.

**3. [Rule 1 - Bug] login.spec.ts URL was /giris (404); n11's actual login path is /giris-yap.**
- **Found during:** First full capture run — login.spec.ts was the only failing spec, with `Received: 404` on the `expect(response?.status()).toBeLessThan(400)` assertion.
- **Investigation:** account.spec.ts had captured the redirect target `https://www.n11.com/giris-yap?redirectUrl=/hesabim` — confirming n11's canonical login path.
- **Fix:** Updated SLUG-URL constant in `tools/recon/tests/login.spec.ts` from `https://www.n11.com/giris` to `https://www.n11.com/giris-yap`. Added a code comment pointing Plan 02-03 §8 enrichment at the discovery.
- **Files modified:** `tools/recon/tests/login.spec.ts`.
- **Commit:** 43b95e9.

**4. [Rule 1 - Bug] assemble-recon.ts dash-collapse regex collapsed the `--` CSS prefix.**
- **Found during:** `npm run check` — Color Token Table had 0 rows by checker count (regex `^\|\s*--[a-z]` failed because rows started with `-color-` not `--color-`).
- **Investigation:** `baseName.replace(/-+/g, '-')` was applied to `--color-body-bg`, collapsing the leading `--` to `-`. The intent was to normalize internal repeated dashes, not to strip the CSS custom-property prefix.
- **Fix:** Changed line 201 from `baseName.replace(/-+/g, '-')` to `'--' + baseName.replace(/^-+/, '').replace(/-+/g, '-')` — strip leading dashes, collapse interior repeats, then re-prepend the canonical `--` prefix.
- **Files modified:** `tools/recon/assemble-recon.ts`.
- **Commit:** 43b95e9.

**5. [Rule 3 - Blocking] scripts/check-phase-02-artifacts.sh PROJECT.md regex was too lenient.**
- **Found during:** First end-to-end artifact-lint after assemble — script exited 0 (not 1 as the cross-plan contract requires). The lenient `Vite|Next\.js` grep matched the pre-existing "Frontend toolchain deferred until n11 recon" rationale row, which already mentions both names.
- **Investigation:** Plan 02-02 acceptance criteria require `bash scripts/check-phase-02-artifacts.sh` to exit 1 with EXACTLY ONE failure on the PROJECT.md Key Decisions row at the end of this plan, so Plan 02-03 has a single concrete deliverable. The lenient regex would have hidden the gap entirely.
- **Fix:** Tightened the check from `Vite|Next\.js` to a same-line conjunction of `Vite 8 + React 19 SPA` AND `Locked YYYY-MM-DD` (the locked row's exact winner label and date stamp per PATTERNS.md per-file PROJECT.md edits contract). Now exits 1 with a single FAIL line: `PROJECT.md Key Decisions has no locked 'Vite 8 + React 19 SPA' row (Plan 02-03 closes this)`.
- **Files modified:** `scripts/check-phase-02-artifacts.sh`.
- **Commit:** 43b95e9.

### Authentication gates

None occurred. n11's anti-bot rung 1 (configured posture) was sufficient for all 7 captures.

## Cross-plan contract evidence (Issue-4 pin)

Captured at the end of Task 2:

| Metric | Required | Observed |
| --- | --- | --- |
| `bash scripts/check-phase-02-artifacts.sh` exit code | `1` (exactly) | `1` |
| Lines matching `^FAIL:` | `1` (exactly) | `1` |
| Lines matching `PROJECT\.md.*Key Decisions` | `1` (exactly) | `1` |
| The single FAIL line text | quotes PROJECT.md Key Decisions | `FAIL: PROJECT.md Key Decisions has no locked 'Vite 8 + React 19 SPA' row (Plan 02-03 closes this)` |

Plan 02-03 closes this gap by appending a locked Frontend toolchain row to PROJECT.md Key Decisions and the `## Decision Matrix` subsection to `.planning/intel/n11-recon.md`. After Plan 02-03 lands, the lint will exit 0.

## Hand-off note for Plan 02-03

**Already complete from the assembler (no Plan 02-03 enrichment needed):**
- §1 Page Inventory (7-row table with captured URLs)
- §2 Turkish Copy Catalog (644 rows; recon-driven)
- §3 Category Taxonomy (8-row table; from CLAUDE.md / REQUIREMENTS PROD-03)
- §4 Color Token Table (25 hex rows)
- §6 Layout Patterns (assembler ships 6 bullet observations)
- §7 Anti-pattern flags (the Pitfall #19 callout is the headline bullet)

**Plan 02-03 must enrich:**
- §5 Typography Notes — currently the assembler only fills body/heading family/size/weight from the homepage tokens; Plan 02-03 should add observed font weights, line-heights, and any hover/focus state notes.
- §8 Open n11 questions — the 5 unresolved-during-recon points listed above need to be expanded into the §8 table format and turned into testable carry-forward items for Phase 10 / Phase 11.
- **Append `## Decision Matrix` subsection** to `.planning/intel/n11-recon.md` (Plan 02-03 Task 1) — the 9th `npm run check` assertion enforces this; it must include the score-65 Vite+React 19 SPA winner row.
- **Append the locked toolchain row** to `.planning/PROJECT.md` § Key Decisions (Plan 02-03 Task 2) — this is the artifact-lint's single FAIL.

After Plan 02-03 lands both, the artifact-lint will exit 0 and `npm run check` will print `RECON OK — N phrases, M tokens, 7 screenshots, Decision Matrix present`.

## Known Stubs

None. The recon report's `<placeholder ...>` token rows were displaced by the 25 harvested tokens; the assembler's seed table fallback was not triggered.

## Self-Check: PASSED

Verified at SUMMARY-write time:

- `[ -f tools/recon/tests/homepage.spec.ts ]` → FOUND
- `[ -f tools/recon/tests/category.spec.ts ]` → FOUND
- `[ -f tools/recon/tests/pdp.spec.ts ]` → FOUND
- `[ -f tools/recon/tests/cart.spec.ts ]` → FOUND
- `[ -f tools/recon/tests/checkout.spec.ts ]` → FOUND
- `[ -f tools/recon/tests/account.spec.ts ]` → FOUND
- `[ -f tools/recon/tests/login.spec.ts ]` → FOUND
- `[ -f .planning/intel/screenshots/homepage-fullpage.png ]` → FOUND (3 160 703 bytes)
- `[ -f .planning/intel/screenshots/category-elektronik-fullpage.png ]` → FOUND (1 662 064 bytes)
- `[ -f .planning/intel/screenshots/pdp-fullpage.png ]` → FOUND (804 593 bytes)
- `[ -f .planning/intel/screenshots/cart-fullpage.png ]` → FOUND (235 334 bytes)
- `[ -f .planning/intel/screenshots/checkout-step1-fullpage.png ]` → FOUND (337 220 bytes)
- `[ -f .planning/intel/screenshots/account-fullpage.png ]` → FOUND (63 735 bytes)
- `[ -f .planning/intel/screenshots/login-fullpage.png ]` → FOUND (51 639 bytes)
- `[ -f .planning/intel/n11-recon.md ]` → FOUND
- `git log --oneline | grep 6f511e1` → FOUND (Task 1 commit: feat(02-02): add 7 page-capture specs ...)
- `git log --oneline | grep 43b95e9` → FOUND (Task 2 commit: feat(02-02): run n11.com captures and assemble recon report)

## Task 3: HUMAN VERIFICATION — APPROVED

Task 3 is a `checkpoint:human-verify` blocking gate. The orchestrator presented the three-check spot-check (screenshot authenticity, Turkish copy verbatim, §7 chat-panel callout) and the human responded `approved` on 2026-04-29.

- All 7 fullpage PNGs confirmed authentic n11 pages (no Cloudflare/captcha walls)
- §2 Turkish copy catalog passes verbatim spot-check
- §7 anti-pattern callout present with the no-floating-chat-panel line that gates Phase 11

Plan 02-02 is complete; Plan 02-03 (Wave 2) is unblocked.
