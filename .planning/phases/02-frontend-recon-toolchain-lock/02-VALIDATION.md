---
phase: 2
slug: frontend-recon-toolchain-lock
status: approved
nyquist_compliant: true
wave_0_complete: false
created: 2026-04-29
approved: 2026-04-29
---

# Phase 2 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Phase 2 is recon-and-decision; the "tests" are file-existence + content-shape checks plus a single Playwright dry-run.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | none on the storefront yet — `tools/recon/` uses `@playwright/test 1.59.x` for the dry-run only |
| **Config file** | `tools/recon/playwright.config.ts` (Wave 0 creates) |
| **Quick run command** | `bash scripts/check-phase-02-artifacts.sh` (Wave 0 creates — file-existence + grep checks) |
| **Full suite command** | `cd tools/recon && npx playwright test --project=chromium --reporter=list --grep @recon-dryrun` |
| **Estimated runtime** | ~30s (dry-run hits a single n11.com URL with `--workers=1`) |

---

## Sampling Rate

- **After every task commit:** Run `bash scripts/check-phase-02-artifacts.sh` (artifact-shape lint, < 2s)
- **After every plan wave:** same artifact lint (full suite is the same — recon has no unit pyramid)
- **Before `/gsd-verify-work`:** full Playwright dry-run must succeed against n11.com homepage
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

> Filled in by the planner during Step 8. Until then, leave the row template below as the contract gsd-planner must satisfy.

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 2-01-01 | 01 | 0 | FE-01 | — | Recon project scaffolds without leaking secrets | smoke | `cd tools/recon && npx playwright --version` | ❌ W0 | ⬜ pending |
| 2-01-02 | 01 | 0 | FE-01 | — | Artifact-lint script exits 0 on empty state | shape | `bash scripts/check-phase-02-artifacts.sh --bootstrap` | ❌ W0 | ⬜ pending |
| 2-02-XX | 02 | 1 | FE-01 | — | Each n11.com page captured with screenshot file present | shape | `test -s .planning/intel/screenshots/<slug>.png` | ❌ W0 | ⬜ pending |
| 2-02-XX | 02 | 1 | FE-01 | — | Turkish copy catalog has ≥30 distinct phrases | shape | `awk -F'\|' 'NR>2 && NF>3 {print $2}' .planning/intel/n11-recon.md \| sort -u \| wc -l` returns ≥ 30 | ❌ W0 | ⬜ pending |
| 2-03-XX | 03 | 2 | FE-01 | — | Toolchain decision recorded in PROJECT.md Key Decisions | shape | `grep -q "## Key Decisions" .planning/PROJECT.md && grep -A 200 "## Key Decisions" .planning/PROJECT.md \| grep -qE "Vite\|Next\.js"` | ✅ | ⬜ pending |
| 2-03-XX | 03 | 2 | FE-01 | — | Color token table has ≥10 named tokens | shape | `awk -F'\|' '/^\| --[a-z]/ {print $2}' .planning/intel/n11-recon.md \| wc -l` returns ≥ 10 | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `tools/recon/package.json` — `@playwright/test` pinned, `npm run capture` script
- [ ] `tools/recon/playwright.config.ts` — single project (chromium), `workers: 1`, `fullyParallel: false`, real desktop UA, `slowMo: 250`, `headless: false`
- [ ] `tools/recon/.gitignore` — node_modules, test-results, playwright-report
- [ ] `tools/recon/tests/` directory — capture specs go here in later tasks (matches Playwright default `testDir: './tests'`)
- [ ] `scripts/check-phase-02-artifacts.sh` — artifact-shape lint (file existence + grep counts), used as quick run
- [ ] `.planning/intel/` directory — `screenshots/` subdir reserved
- [ ] `tools/recon/README.md` — how to run captures, anti-bot disclaimer (no real cart, gentle rate)
- [ ] `npx playwright install chromium` — browser binary; one-time per machine
- [ ] `npm i -D @playwright/test@1.59.x` — pinned at decision-matrix-recommended version

*Wave 0 is one plan ("01 — Recon Project Bootstrap") that all subsequent plans depend on.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Screenshots actually depict the right n11 page (not a 403 wall, not a captcha) | FE-01 | Pixel content is not grep-checkable; humans verify | Open each `.planning/intel/screenshots/*.png` and confirm it shows the expected page (homepage, PDP, cart, etc.) without a captcha overlay |
| Turkish copy is *verbatim* from n11 (not translated by Claude) | FE-01 (and Pitfall #20) | LLM-translated Turkish drifts; only original-source phrases prevent chat drift in Phase 8 | Spot-check 5 random rows in the copy catalog against the matching screenshot — phrase must appear in the screenshot exactly |
| Toolchain decision rationale ties to recon evidence | FE-01 | "Why Vite over Next" is a judgement call grounded in observed n11 SPA-vs-SSR posture | Read PROJECT.md Key Decisions entry — must reference at least 2 specific recon findings (e.g., "n11 PDP is fully client-rendered after initial HTML — no SSR-only data we'd lose by going SPA") |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies (confirmed by plan-checker pass 2)
- [x] Sampling continuity: no 3 consecutive tasks without automated verify (Wave 0 = 3/3, Wave 1 = 2 auto + 1 checkpoint, Wave 2 = 3/3)
- [x] Wave 0 covers all MISSING references (10 items: scaffold + tsconfig.json + browser install)
- [x] No watch-mode flags (`npx playwright test`, `npx tsc --noEmit`, plain bash — all one-shot)
- [x] Feedback latency < 30s (artifact-lint < 2s; full Playwright dry-run ~30s)
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved 2026-04-29 (after plan-checker VERIFICATION PASSED, pass 2)
