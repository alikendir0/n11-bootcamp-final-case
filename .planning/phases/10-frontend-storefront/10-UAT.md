---
status: diagnosed
phase: 10-frontend-storefront
source: [10-01-SUMMARY.md, 10-02-SUMMARY.md, 10-03-SUMMARY.md, 10-04-SUMMARY.md, 10-05-SUMMARY.md, 10-06-SUMMARY.md, 10-07-SUMMARY.md, 10-08-SUMMARY.md, 10-09-SUMMARY.md]
started: 2026-05-01T12:38:15Z
updated: 2026-05-01T12:45:22Z
---

## Current Test

[testing paused — storefront blocker prevents further UAT]

## Tests

### 1. Frontend Cold Start
expected: From a clean frontend start, the app builds/boots without errors and the homepage loads with the Turkish n11-style header, category nav, main content area, and footer.
result: issue
reported: |
  Unexpected Application Error!
  Cannot read properties of undefined (reading 'toLocaleLowerCase')
  TypeError: Cannot read properties of undefined (reading 'toLocaleLowerCase')
      at productSlug (http://192.168.1.46:8083/src/components/listing/ProductCard.tsx:8:22)
      at ProductCard (http://192.168.1.46:8083/src/components/listing/ProductCard.tsx:14:22)
  React Router displayed its default developer error boundary message.
severity: blocker

### 2. Header Navigation and Search
expected: The sticky header shows the logo, Turkish search placeholder, account links, cart link/badge, and 8 category links. Searching submits to `/arama?q=<term>` and category links route to their listing pages.
result: pass

### 3. Auth Pages and Guards
expected: `/giris-yap` and `/uye-ol` show Turkish forms with inline validation. Protected pages redirect anonymous users to `/giris-yap?redirectUrl=...`, successful login/register stores the session, and authenticated users are redirected away from auth pages.
result: issue
reported: |
  zod.mjs:1 Uncaught (in promise) ZodError for password minimum 8 characters while trying to register. There were no UI clues for password warnings, only console output. A POST to /api/v1/identity/auth/register returned 400 Bad Request even after fixing the password issue.
severity: blocker

### 4. Homepage Hero and Product Rail
expected: The homepage shows a 3-slide hero carousel with dot navigation and a `Yeni Gelenler` rail backed by product data, with loading skeletons or clear retry/error states while data loads.
result: issue
reported: |
  Unexpected Application Error! Cannot read properties of undefined (reading 'toLocaleLowerCase') at productSlug in ProductCard.tsx. Same error as Test 1; user stopped further testing because the system is broken beyond testing.
severity: blocker

### 5. Category Listing
expected: A category URL such as `/elektronik` shows breadcrumb, title, responsive product grid, sort dropdown, and pagination. Sorting and page changes update Turkish URL params and keep category filtering intact.
result: blocked
blocked_by: other
reason: "User stopped further UAT because the storefront crashes with ProductCard productSlug error and is broken beyond testing."

### 6. Search Results
expected: `/arama?q=macbook` shows a Turkish search-results heading, product grid, sort/pagination controls, and an empty state with `Aramanız için sonuç bulunamadı.` when no products match.
result: blocked
blocked_by: other
reason: "User stopped further UAT because the storefront crashes with ProductCard productSlug error and is broken beyond testing."

### 7. Product Detail Page
expected: A product URL shows breadcrumbs, image gallery, product title, TRY price, `KDV Dahil`, taksit options for 1/2/3/6/9/12, stock status, `Kargo Bedava` when eligible, and `Açıklama / Özellikler / Kargo` tabs.
result: blocked
blocked_by: other
reason: "User stopped further UAT because the storefront crashes with ProductCard productSlug error and is broken beyond testing."

### 8. Add to Cart
expected: Authenticated users can click `Sepete Ekle`, see `Ürün sepete eklendi.`, and the header cart badge increments. Anonymous users are redirected to login with a redirectUrl back to the product.
result: blocked
blocked_by: other
reason: "User stopped further UAT because the storefront crashes with ProductCard productSlug error and is broken beyond testing."

### 9. Cart Page
expected: `/sepetim` shows the Turkish empty state when empty. With items, it shows line items, quantity stepper, remove button with undo toast, `Sipariş Özeti`, `KDV Dahil`, shipping/free-shipping messaging, and `Siparişi Tamamla` routing correctly for anonymous vs authenticated users.
result: blocked
blocked_by: other
reason: "User stopped further UAT because the storefront crashes with ProductCard productSlug error and is broken beyond testing."

### 10. Checkout Address Step
expected: `/odeme/adres` requires auth, shows `Adres › Ödeme › Onay`, loads saved addresses, lets the user select one or add a new address with Turkish validation, then `Devam Et` navigates to `/odeme/odeme`.
result: blocked
blocked_by: other
reason: "User stopped further UAT because the storefront crashes with ProductCard productSlug error and is broken beyond testing."

### 11. Checkout Payment and Iyzico Handoff
expected: `/odeme/odeme` requires a selected address, shows `Kredi Kartı` enabled and `Kapıda Ödeme` disabled with `Yakında`, then `Sipariş Ver` creates an order with idempotency and redirects to an Iyzico hosted payment page or the result page while polling for the payment URL.
result: blocked
blocked_by: other
reason: "User stopped further UAT because the storefront crashes with ProductCard productSlug error and is broken beyond testing."

### 12. Checkout Result States
expected: `/odeme/sonuc?orderId=<id>` polls order status, shows `Ödemeniz işleniyor...` while pending, `Siparişiniz Alındı` on confirmed orders, `Ödemeniz Alınamadı` on failed/cancelled orders, and a timeout follow-up state after 30 seconds.
result: blocked
blocked_by: other
reason: "User stopped further UAT because the storefront crashes with ProductCard productSlug error and is broken beyond testing."

### 13. Account Hub and Orders List
expected: Authenticated users can open `/hesabim` and see a Turkish greeting plus order/address stats. `/siparislerim` lists orders newest first with dates, TRY totals, Turkish status badges, detail links, and a Turkish empty state when there are no orders.
result: blocked
blocked_by: other
reason: "User stopped further UAT because the storefront crashes with ProductCard productSlug error and is broken beyond testing."

### 14. Order Detail and Cancellation
expected: `/siparislerim/:orderId` shows order status timeline, items, delivery address, payment method, and only shows `Siparişi İptal Et` for pending orders. Confirming cancellation calls the backend, closes the modal, refreshes order data, and shows a success toast.
result: blocked
blocked_by: other
reason: "User stopped further UAT because the storefront crashes with ProductCard productSlug error and is broken beyond testing."

### 15. Address Book and Fallback Pages
expected: `/adreslerim` shows saved addresses or `Henüz kayıtlı adresiniz yok.`, supports adding a new address, and the catch-all route shows `Aradığınız sayfayı bulamadık.` with `Ana Sayfaya Dön`.
result: blocked
blocked_by: other
reason: "User stopped further UAT because the storefront crashes with ProductCard productSlug error and is broken beyond testing."

## Summary

total: 15
passed: 1
issues: 3
pending: 0
skipped: 0
blocked: 11

## Gaps

- truth: "From a clean frontend start, the app builds/boots without errors and the homepage loads with the Turkish n11-style header, category nav, main content area, and footer."
  status: failed
  reason: |
    User reported: Unexpected Application Error! Cannot read properties of undefined (reading 'toLocaleLowerCase') at productSlug in ProductCard.tsx, with React Router default developer error boundary visible.
  severity: blocker
  test: 1
  root_cause: "Frontend listing DTO mismatch: ProductCard expects product.name, but GET /products returns ProductSummaryDto fields such as nameTr, firstImageUrl, and categoryName. productSlug receives undefined and crashes on toLocaleLowerCase."
  artifacts:
    - path: "frontend/src/components/listing/ProductCard.tsx"
      issue: "Unconditionally reads product.name and calls toLocaleLowerCase."
    - path: "frontend/src/api/productApi.ts"
      issue: "Returns raw backend product listing DTOs as ProductPage without mapping field names."
    - path: "frontend/src/lib/types.ts"
      issue: "Frontend Product interface does not match backend ProductSummaryDto/listing response."
  missing:
    - "Map backend listing DTO fields to frontend product card fields before rendering."
    - "Add a regression test or invariant for listing DTO normalization."
    - "Ensure the route-level error boundary uses the custom Turkish fallback instead of React Router's default developer error UI."
  debug_session: ".planning/debug/phase10-productcard-slug-crash.md"
- truth: "`/giris-yap` and `/uye-ol` show Turkish forms with inline validation. Protected pages redirect anonymous users to `/giris-yap?redirectUrl=...`, successful login/register stores the session, and authenticated users are redirected away from auth pages."
  status: failed
  reason: |
    User reported: Register form throws an uncaught ZodError in the console for password length, shows no UI validation clue, and POST /api/v1/identity/auth/register returns 400 even after fixing the password issue.
  severity: blocker
  test: 3
  root_cause: "Register validation has two defects: @hookform/resolvers 3.10.0 is incompatible with Zod 4.4.1 and rejects with ZodError instead of returning field errors, and frontend password validation only checks min(8) while the backend requires at least one letter and one digit."
  artifacts:
    - path: "frontend/package.json"
      issue: "@hookform/resolvers 3.x is incompatible with installed zod 4.x."
    - path: "frontend/src/pages/RegisterPage.tsx"
      issue: "Password schema is weaker than backend and inline validation errors are not produced due resolver incompatibility."
    - path: "identity-service/src/main/java/com/n11/identity/auth/dto/RegisterRequest.java"
      issue: "Backend requires password regex with at least one letter and one digit."
  missing:
    - "Upgrade @hookform/resolvers to a Zod 4 compatible version or otherwise make the resolver return react-hook-form field errors."
    - "Mirror the backend password regex in frontend register validation with a Turkish inline message."
    - "Surface backend register 400 problem details as user-visible inline errors or Turkish toast messages."
  debug_session: ".planning/debug/phase10-register-validation-post.md"
- truth: "The homepage shows a 3-slide hero carousel with dot navigation and a `Yeni Gelenler` rail backed by product data, with loading skeletons or clear retry/error states while data loads."
  status: failed
  reason: |
    User reported: Unexpected Application Error! Cannot read properties of undefined (reading 'toLocaleLowerCase') at productSlug in ProductCard.tsx. Same error as Test 1; further testing stopped because the system is broken beyond testing.
  severity: blocker
  test: 4
  root_cause: "Same as Test 1: frontend listing DTO mismatch causes ProductCard to receive product.name as undefined for product rail/listing responses and crash during homepage render."
  artifacts:
    - path: "frontend/src/components/listing/ProductCard.tsx"
      issue: "Unconditionally reads product.name and calls toLocaleLowerCase."
    - path: "frontend/src/components/home/ProductRail.tsx"
      issue: "Passes raw fetchProducts content directly to ProductCard."
    - path: "frontend/src/api/productApi.ts"
      issue: "Returns raw backend product listing DTOs as ProductPage without mapping field names."
  missing:
    - "Map backend listing DTO fields to frontend product card fields before rendering."
    - "Add a homepage/product rail regression test covering backend ProductSummaryDto shape."
  debug_session: ".planning/debug/phase10-productcard-slug-crash.md"
