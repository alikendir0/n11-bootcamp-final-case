---
status: partial
phase: 10-frontend-storefront
source: [10-VERIFICATION.md]
started: 2026-05-01T00:00:00Z
updated: 2026-05-01T00:00:00Z
---

## Current Test

[awaiting human testing]

## Tests

### 1. Visual smoke of storefront pages on http://localhost:8083
expected: Header/footer/home/listing/PDP/cart/checkout/auth/account pages match the Turkish UI contract without English flashes or layout regressions.
why_human: Visual fidelity, sticky-header scroll behavior, 3-4 slide hero carousel, category nav, and account sidebar layout cannot be fully proven by static code checks.
result: [pending]

### 2. Seed-backed demo flow after adding visible catalog seed data
expected: Search/category navigation can reach a visible product; user can reach PDP, add to cart, login, checkout, and reach Iyzico hand-off or /odeme/sonuc.
why_human: Full browser flow depends on live stack/seed data, auth, and hosted payment interaction.
result: [pending]

### 3. Account section live flow — /hesabim, /siparislerim, /siparislerim/:orderId, /adreslerim
expected: /hesabim renders greeting + Toplam Sipariş + Kayıtlı Adres stat cards via real TanStack Query calls; /siparislerim renders order list sorted date-desc with Turkish status badges + Detay links; /siparislerim/:orderId renders OrderTimeline + items + address + payment + Siparişi İptal Et CTA for PENDING orders only; /adreslerim renders address grid + Yeni Adres Ekle inline form.
why_human: Live account section requires authenticated session with real order/address data in backend.
result: [pending]

### 4. Cancel order flow from /siparislerim/:orderId for a PENDING order
expected: Clicking Siparişi İptal Et opens CancelOrderDialog (Vazgeç + Evet, İptal Et buttons), confirming posts to /api/v1/orders/{id}/cancel, order status badge updates to İptal Edildi and timeline switches to red banner.
why_human: Requires live backend saga + authenticated PENDING order; mutation side-effect visible only in running browser.
result: [pending]

### 5. Sandbox Iyzico completion
expected: Hosted payment completes and redirects back to /odeme/sonuc?orderId=<id>, which renders confirmed/failure/timeout state as appropriate.
why_human: External Iyzico hosted-page behavior requires live sandbox credentials and browser interaction.
result: [pending]

## Summary

total: 5
passed: 0
issues: 0
pending: 5
skipped: 0
blocked: 0

## Gaps
