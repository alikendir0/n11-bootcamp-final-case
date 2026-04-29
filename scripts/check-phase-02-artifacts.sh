#!/usr/bin/env bash
# Phase 2 artifact lint. Verifies recon scaffold + (in default mode) capture outputs + recon report shape.
# Usage:
#   scripts/check-phase-02-artifacts.sh             # default — full Phase 2 verification
#   scripts/check-phase-02-artifacts.sh --bootstrap # Wave 0 only — scaffold checks only, skip capture/report checks
set -euo pipefail

MODE="full"
if [ "${1:-}" = "--bootstrap" ]; then
  MODE="bootstrap"
fi

FAIL_COUNT=0

check_ok() {
  echo "OK: $1"
}

check_fail() {
  echo "FAIL: $1"
  FAIL_COUNT=$((FAIL_COUNT + 1))
}

assert_file_exists() {
  local f="$1"
  local desc="$2"
  if [ -f "$f" ]; then
    check_ok "$desc ($f exists)"
  else
    check_fail "$desc — missing: $f"
  fi
}

assert_file_nonempty() {
  local f="$1"
  local desc="$2"
  if [ -s "$f" ]; then
    check_ok "$desc ($f is non-empty)"
  else
    check_fail "$desc — empty/missing: $f"
  fi
}

assert_grep() {
  local pattern="$1"
  local f="$2"
  local desc="$3"
  if grep -qE "$pattern" "$f" 2>/dev/null; then
    check_ok "$desc"
  else
    check_fail "$desc — pattern '$pattern' not found in $f"
  fi
}

# ---------------------------------------------------------------------------
# Bootstrap-mode checks (run in BOTH modes).
# ---------------------------------------------------------------------------

assert_file_exists "tools/recon/package.json" "recon package.json scaffolded"
assert_grep '"@playwright/test": "1\.59' "tools/recon/package.json" "Playwright pinned to 1.59.x exact"
assert_file_exists "tools/recon/tsconfig.json" "recon tsconfig.json scaffolded"
assert_grep '"noEmit": true' "tools/recon/tsconfig.json" "tsconfig.json has noEmit:true (canonical type-check gate)"
assert_file_exists "tools/recon/playwright.config.ts" "Playwright config scaffolded"
assert_grep 'headless:\s*false' "tools/recon/playwright.config.ts" "anti-bot posture: headless false"
assert_grep 'disable-blink-features=AutomationControlled' "tools/recon/playwright.config.ts" "anti-bot posture: AutomationControlled flag"
assert_grep 'workers:\s*1' "tools/recon/playwright.config.ts" "rate-limit: workers 1"
assert_file_exists "tools/recon/lib/dismiss-banners.ts" "banner-dismiss helper"
assert_file_exists "tools/recon/lib/harvest-copy.ts" "copy-harvest helper"
assert_file_exists "tools/recon/lib/harvest-colors.ts" "color-harvest helper"
assert_file_exists "tools/recon/assemble-recon.ts" "Markdown assembler"
assert_file_exists "tools/recon/check-recon.ts" "report sanity checker"
assert_file_exists "tools/recon/README.md" "recon README"
assert_file_exists "tools/recon/.gitignore" "recon-local .gitignore"
assert_grep 'tools/recon/node_modules/' ".gitignore" "root .gitignore has recon ignore block"
assert_file_exists ".planning/intel/screenshots/.gitkeep" "intel screenshots dir reserved"

# Negative checks: S-6 (recon stays out of Gradle).
if grep -F 'tools/recon' settings.gradle.kts >/dev/null 2>&1; then
  check_fail "S-6 violation: tools/recon should NOT appear in settings.gradle.kts"
else
  check_ok "S-6: tools/recon not registered as Gradle subproject"
fi

if [ -f "tools/recon/build.gradle.kts" ]; then
  check_fail "S-6 violation: tools/recon/build.gradle.kts should not exist"
else
  check_ok "S-6: no Gradle build file under tools/recon/"
fi

# ---------------------------------------------------------------------------
# Full-mode-only checks (skip in --bootstrap).
# ---------------------------------------------------------------------------

if [ "$MODE" = "full" ]; then
  # Required screenshots (all 7 expected slugs):
  #   homepage-fullpage.png, category-elektronik-fullpage.png, pdp-fullpage.png,
  #   cart-fullpage.png, checkout-step1-fullpage.png, account-fullpage.png,
  #   login-fullpage.png — each must be >= 50000 bytes (Pitfall #1 anti-bot guard).
  for slug in homepage category-elektronik pdp cart checkout-step1 account login; do
    shot=".planning/intel/screenshots/${slug}-fullpage.png"
    if [ -f "$shot" ]; then
      bytes=$(stat -c%s "$shot" 2>/dev/null || echo 0)
      if [ "$bytes" -ge 50000 ]; then
        check_ok "screenshot >=50KB: ${slug} (${bytes} bytes)"
      else
        check_fail "screenshot ${slug} is only ${bytes} bytes (need >=50000 — Pitfall #1 guard)"
      fi
    else
      check_fail "missing screenshot: ${slug}-fullpage.png"
    fi
  done

  assert_file_exists ".planning/intel/n11-recon.md" "recon report"
  assert_grep '^## 1\. Page Inventory$' ".planning/intel/n11-recon.md" "report §1 heading"
  assert_grep '^## 2\. Turkish Copy Catalog$' ".planning/intel/n11-recon.md" "report §2 heading"
  assert_grep '^## 3\. Category Taxonomy$' ".planning/intel/n11-recon.md" "report §3 heading"
  assert_grep '^## 4\. Color Token Table$' ".planning/intel/n11-recon.md" "report §4 heading"
  assert_grep '^## 5\. Typography Notes$' ".planning/intel/n11-recon.md" "report §5 heading"
  assert_grep '^## 6\. Layout Patterns$' ".planning/intel/n11-recon.md" "report §6 heading"
  assert_grep '^## 7\. Anti-pattern flags' ".planning/intel/n11-recon.md" "report §7 heading"
  assert_grep '^## 8\. Open n11 questions' ".planning/intel/n11-recon.md" "report §8 heading"

  PHRASE_COUNT=$(awk '/^## 2\. Turkish/,/^## 3\./' .planning/intel/n11-recon.md | grep -cE '^\|\s+[0-9]+\s+\|' || true)
  if [ "$PHRASE_COUNT" -ge 30 ]; then
    check_ok "Turkish copy catalog >=30 rows ($PHRASE_COUNT)"
  else
    check_fail "Turkish copy catalog has only $PHRASE_COUNT rows (need >=30)"
  fi

  TOKEN_COUNT=$(awk '/^## 4\. Color/,/^## 5\./' .planning/intel/n11-recon.md | grep -cE '^\|\s+--[a-z]' || true)
  if [ "$TOKEN_COUNT" -ge 10 ]; then
    check_ok "Color token table >=10 rows ($TOKEN_COUNT)"
  else
    check_fail "Color token table has only $TOKEN_COUNT rows (need >=10)"
  fi

  if awk '/^## 4\. Color/,/^## 5\./' .planning/intel/n11-recon.md | grep -qE 'rgb\('; then
    # rgb( may appear in the "Computed source (rgb)" column legitimately;
    # we re-check that it is NOT in the Hex column (column 2 between pipes).
    HEX_RGB_HITS=$(awk '/^## 4\. Color/,/^## 5\./' .planning/intel/n11-recon.md \
      | grep -E '^\|' \
      | awk -F '|' '{ gsub(/^ +| +$/, "", $3); print $3 }' \
      | grep -cE 'rgb\(' || true)
    if [ "$HEX_RGB_HITS" -gt 0 ]; then
      check_fail "Color Token Table Hex column contains rgb() — should be hex only (Pitfall #4)"
    else
      check_ok "Color Token Table Hex column is hex-only (Pitfall #4)"
    fi
  else
    check_ok "Color Token Table has no rgb( anywhere"
  fi

  assert_grep 'Frontend toolchain' ".planning/PROJECT.md" "PROJECT.md mentions Frontend toolchain decision"

  if grep -A 200 '^## Key Decisions$' .planning/PROJECT.md 2>/dev/null | grep -qE 'Vite|Next\.js'; then
    check_ok "PROJECT.md Key Decisions mentions Vite or Next.js"
  else
    check_fail "PROJECT.md Key Decisions has no Vite/Next.js entry"
  fi
fi

# ---------------------------------------------------------------------------
# Final summary.
# ---------------------------------------------------------------------------

if [ "$FAIL_COUNT" -eq 0 ]; then
  echo "ALL CHECKS PASSED (mode=$MODE)"
  exit 0
else
  echo "FAILED: $FAIL_COUNT check(s) failed (mode=$MODE)"
  exit 1
fi
