---
status: partial
phase: 05-cart-order-skeleton
source: [05-VERIFICATION.md]
started: 2026-04-30T14:30:00Z
updated: 2026-04-30T14:30:00Z
---

## Current Test

Confirm CartView KDV breakdown + shipping preview scope is intentionally deferred to Phase 10 frontend.

## Tests

### 1. CartView KDV breakdown + shipping preview scope decision
expected: Either (a) accept that Phase 5 SC-1 "KDV breakdown + shipping preview via REST" means frontend-computed display from CartView items[] (in Phase 10 storefront), OR (b) require cart-service to extend CartView with subtotal + kdvTotal + shippingFee fields before Phase 10 can ship.
result: deferred-to-phase-10

### Decision (recorded 2026-04-30)

**Accepted as deferred to Phase 10 — frontend-computed display.**

Rationale:
- Phase 5 PLAN deliberately narrowed CART-02 must_have to `items[]+lineTotal` (the executor agent followed the plan exactly).
- KDV/shipping is a display-layer concern: LOC-01 stores `price_gross` + `kdv_rate` on products; LOC-02 uses `Intl.NumberFormat` on the frontend.
- Phase 5's primary purpose is "feed the saga" — order-service computes `total_amount` at order creation time, not from cart subtotal.
- Phase 10 storefront SC-2/3 explicitly owns Turkish KDV display.
- Live smoke test (12/12) signed off without these fields → not blocking the saga skeleton.

**Phase 10 owns this:** if Phase 10 prefers backend-computed totals over frontend-computed, it'll need a small follow-up plan to extend CartView with 3 fields (subtotal, kdvTotal, shippingFee) and update CartPersistenceService. This file surfaces in `/gsd-progress` and `/gsd-audit-uat` until Phase 10 makes that call.

## Summary

total: 1
passed: 0
issues: 0
pending: 1
skipped: 0
blocked: 0

## Gaps
