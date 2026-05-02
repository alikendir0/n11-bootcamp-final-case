#!/usr/bin/env bash
# Frontend phase 10 invariants — single CI gate enforcing the 11 cross-cutting truths.
# Run from repo root: bash frontend/scripts/check-frontend-invariants.sh

set -u

FAIL=0
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FRONTEND_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
FRONTEND_SRC="$FRONTEND_DIR/src"

step() { echo "==> $1"; }
pass() { echo "    OK"; }
fail() { echo "    FAIL: $1"; FAIL=$((FAIL + 1)); }

if [ ! -d "$FRONTEND_SRC" ]; then
  echo "frontend/src/ not found. Expected source directory at: $FRONTEND_SRC"
  exit 2
fi

step "1. All API calls go through VITE_API_BASE_URL (no hardcoded service URLs in src/)"
bad=$(grep -rlnE "localhost:[0-9]" "$FRONTEND_SRC" --include='*.ts' --include='*.tsx' | grep -v "vite-env.d.ts" || true)
if [ -n "$bad" ]; then fail "hardcoded localhost service URL in: $bad"; else pass; fi

step "2. n11_auth_token localStorage key only used in tokenStore.ts"
bad=$(grep -rln "n11_auth_token" "$FRONTEND_SRC" --include='*.ts' --include='*.tsx' | grep -vE "lib/tokenStore\.(ts|test\.ts)$" || true)
if [ -n "$bad" ]; then fail "n11_auth_token referenced outside tokenStore: $bad"; else pass; fi

step "3. No dangerouslySetInnerHTML in source"
bad=$(grep -rln "dangerouslySetInnerHTML" "$FRONTEND_SRC" --include='*.ts' --include='*.tsx' || true)
if [ -n "$bad" ]; then fail "dangerouslySetInnerHTML found in: $bad"; else pass; fi

step "4. Intl.NumberFormat only in lib/format.ts (and its test)"
bad=$(grep -rln "Intl\.NumberFormat" "$FRONTEND_SRC" --include='*.ts' --include='*.tsx' | grep -vE "lib/format\.(ts|test\.ts)$" || true)
if [ -n "$bad" ]; then fail "Intl.NumberFormat used outside lib/format.ts: $bad"; else pass; fi

step "5. Intl.DateTimeFormat only in lib/format.ts (and its test)"
bad=$(grep -rln "Intl\.DateTimeFormat" "$FRONTEND_SRC" --include='*.ts' --include='*.tsx' | grep -vE "lib/format\.(ts|test\.ts)$" || true)
if [ -n "$bad" ]; then fail "Intl.DateTimeFormat used outside lib/format.ts: $bad"; else pass; fi

step "6. Route constants centralized in lib/routes.ts (no string literals outside)"
bad=$(grep -rlnE "['\"]/(sepetim|giris-yap|uye-ol|hesabim|siparislerim|adreslerim)['\"]" "$FRONTEND_SRC" --include='*.ts' --include='*.tsx' | grep -vE "lib/routes\.ts$|router\.tsx$" || true)
if [ -n "$bad" ]; then fail "Hardcoded protected route literal outside routes.ts/router.tsx: $bad"; else pass; fi

step "7. CTA primary color #1C1C1E in @theme block (recon-locked — not promo orange)"
if ! grep -q -- "--color-cta-primary-bg: #1C1C1E" "$FRONTEND_SRC/index.css"; then
  fail "@theme block missing --color-cta-primary-bg: #1C1C1E"
else
  pass
fi
bad=$(grep -Ein "#?(ff6600|ff7a00|f5a623)" "$FRONTEND_SRC/index.css" || true)
if [ -n "$bad" ]; then fail "promo-orange color detected on CTA stylesheet — must be #1C1C1E"; fi

step "8. RequireAuth guard wraps the auth-required routes in router.tsx"
if ! grep -q "RequireAuth" "$FRONTEND_SRC/router.tsx"; then
  fail "RequireAuth not used in router.tsx"
else
  pass
fi

step "9. Iyzico return path is /odeme/sonuc — checkout flow uses ROUTES.CHECKOUT_RESULT"
if ! grep -q "/odeme/sonuc" "$FRONTEND_SRC/lib/routes.ts"; then
  fail "ROUTES.CHECKOUT_RESULT path '/odeme/sonuc' missing"
elif ! grep -Rql "ROUTES.CHECKOUT_RESULT" "$FRONTEND_SRC" --include='*.ts' --include='*.tsx'; then
  fail "checkout/result flow does not reference ROUTES.CHECKOUT_RESULT"
else
  pass
fi

step "10. Order POST sends Idempotency-Key header (Phase 5 D-05 contract)"
if ! grep -q "'Idempotency-Key'" "$FRONTEND_SRC/api/orderApi.ts"; then
  fail "Idempotency-Key header not sent on POST /orders — violates Phase 5 D-05"
else
  pass
fi

step "11. No chat-bubble placeholder text (real components are allowed)"
bad=$(grep -rinE "coming soon|chat ?placeholder" "$FRONTEND_SRC" --include='*.ts' --include='*.tsx' || true)
if [ -n "$bad" ]; then fail "Chat placeholder text detected: $bad"; else pass; fi

step "12. chatApi.ts uses gateway route /api/v1/chat/stream"
if ! grep -q "chat/stream" "$FRONTEND_SRC/api/chatApi.ts"; then
  fail "chatApi.ts missing gateway route chat/stream"
else
  pass
fi

step "13. chatApi.ts sends Accept: text/event-stream"
if ! grep -q "text/event-stream" "$FRONTEND_SRC/api/chatApi.ts"; then
  fail "chatApi.ts missing Accept text/event-stream header"
else
  pass
fi

step "14. chatApi.ts does not instantiate native EventSource"
bad=$(grep -rln "new EventSource" "$FRONTEND_SRC" --include='*.ts' --include='*.tsx' | grep -v "check-frontend-invariants" || true)
if [ -n "$bad" ]; then fail "Native EventSource found in: $bad"; else pass; fi

step "15. No direct ai-service or :8088 URLs in frontend source"
bad=$(grep -rlnE "ai-service|localhost:8088|:8088" "$FRONTEND_SRC" --include='*.ts' --include='*.tsx' | grep -vE "check-frontend-invariants|\.test\." || true)
if [ -n "$bad" ]; then fail "Direct ai-service or :8088 URL in: $bad"; else pass; fi

echo
if [ "$FAIL" -eq 0 ]; then
  echo "All 15 frontend invariants OK."
  exit 0
else
  echo "$FAIL invariant(s) violated."
  exit 1
fi
