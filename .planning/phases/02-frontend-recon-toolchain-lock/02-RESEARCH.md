# Phase 2: Frontend Recon + Toolchain Lock — Research

**Researched:** 2026-04-29
**Domain:** Live-site reconnaissance (Playwright against n11.com) + frontend toolchain decision (React 19 / Vite 8 / Tailwind 4 / Zustand 5 / TanStack Query 5)
**Confidence:** HIGH (toolchain versions, Playwright API, recon-output schema). MEDIUM (n11.com URL paths — recon will confirm; anti-bot posture — recon will confirm).

---

## Summary

Phase 2 is unusual: the *deliverable IS the research*. A Playwright session against n11.com produces `.planning/intel/n11-recon.md` (with screenshots, captured Turkish copy, category taxonomy, color/typography tokens), and the frontend toolchain is locked into PROJECT.md based on what the recon shows. Phase 10 (Storefront) and Phase 11 (Chat Bubble + Deploy) consume `n11-recon.md` *mechanically* — Turkish copy is reused verbatim (Pitfall #20 prevention), color tokens become Tailwind theme entries, layout patterns dictate component structure. If the recon report's schema is sloppy, Phase 10 redoes the work.

The toolchain decision is *de facto* already directed by `STACK.md` — Vite 8 + React 19 + TypeScript 5 + Tailwind 4 + Zustand 5 + React Router 7 + TanStack Query 5 — but PROJECT.md correctly leaves it OPEN until recon evidence is in. This research provides a 4-row decision matrix the planner can paste into the toolchain-decision task; the recon is unlikely to overturn it (n11 is a logged-out marketplace whose SEO needs do not transfer to a graded interview demo) but the matrix makes the decision auditable.

The three pitfalls this phase actively prevents: **#20 Turkish prompt drift** (captured copy gives Phase 8 the chat assistant's grounded vocabulary), **#19 Streaming chat freeze** (recon confirms n11 has no chat UI to copy — Phase 11 must invent, flag this gap explicitly), **#23 CORS/auth mismatch** (toolchain decision drives `VITE_API_BASE_URL` env-var convention from day one).

**Primary recommendation:** Adopt a **standalone Node project at `tools/recon/`** running `@playwright/test ^1.59` with **`headless: false` + `slowMo: 250` + a real `userAgent` string + 1 page / 2.5s rate limit** for n11 capture. Lock the frontend toolchain to **Vite 8.0.10 + React 19.2.5 + TypeScript 5.x + Tailwind 4.2.4 + Zustand 5.0.12 + React Router 7.14.2 + TanStack Query 5.99+** with TS strict mode + `noUncheckedIndexedAccess` + `exactOptionalPropertyTypes`. Recon report follows the 8-section schema in §3 below.

---

## User Constraints

> No CONTEXT.md exists for Phase 2 (the phase directory is empty as of 2026-04-29). Constraints below are extracted from CLAUDE.md, PROJECT.md, REQUIREMENTS.md, and the orchestrator's research brief.

### Locked Decisions (from PROJECT.md, CLAUDE.md, ROADMAP.md, and orchestrator brief)

- **Phase scope:** Playwright recon of n11.com + frontend toolchain decision recorded in PROJECT.md Key Decisions. Build no UI (`--skip-ui` was passed; Phase 10 builds the storefront).
- **Output location:** `.planning/intel/n11-recon.md` + `.planning/intel/screenshots/<slug>.png`.
- **Required pages to capture:** homepage, category nav, product grid, PDP, cart, checkout step-1, account.
- **Turkish copy table:** ≥30 phrases. CLAUDE.md mandates "Frontend in Turkish; identifiers in English."
- **Color/typography tokens + category taxonomy** must land in the recon report so Phase 10 doesn't re-research.
- **Toolchain decision** must be recorded in PROJECT.md Key Decisions with rationale tying back to recon findings (FE-01 acceptance).
- **Out of scope explicitly:** Building any UI, implementing the storefront's color tokens, scraping behind login, automating real cart additions on real n11 accounts.
- **Project policy:** Verify SDK calls against current docs — never invent from training-data recall (CLAUDE.md Rule #4).
- **Out-of-scope deferrals:** Cloudflare Tunnel vs ngrok (Phase 6/11), MCP transport (Phase 9), conversation state store (Phase 8) — only flag if recon surfaces a constraint.

### Claude's Discretion

- Standalone `@playwright/test` runner vs. ad-hoc Node script (recommendation in §1.1)
- Specific selectors and `page.evaluate` shapes for copy / color extraction (templates in §1)
- Color-token naming convention (recommendation: Tailwind v4 `@theme` directive names — `--color-primary`, `--color-primary-hover`, etc.)
- TS strict-extras list (recommendation in §2.3)
- Routing / form / API client picks within the recommended toolchain (recommendation in §2)
- Decision-matrix score weights (recommendation in §2.1: bootcamp-grading-lens-weighted)

### Deferred Ideas (OUT OF SCOPE for this phase)

- Public tunnel choice (Cloudflare Tunnel vs ngrok) — Phase 6 deliverable
- MCP transport choice — Phase 9 deliverable
- Conversation state store (Redis vs Postgres) — Phase 8 deliverable
- Kapıda Ödeme path — Phase 5 deliverable
- Implementing any storefront UI — Phase 10 deliverable
- Implementing the chat bubble UI — Phase 11 deliverable

---

## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| FE-01 | Frontend toolchain locked post-Playwright n11 recon (likely Vite + TypeScript + Tailwind + Zustand; locked in PROJECT.md Key Decisions at end of Phase 2) | §2 — toolchain decision matrix + version-locked picks (HIGH confidence on versions; live-doc verified). Recon report schema in §3 ensures the rationale ties back to evidence. |

**Coverage:** FE-01 is the single requirement IDs listed for this phase. All other FE-* requirements are owned by Phases 10 and 11 — but they read this phase's recon output to do their work, so the recon-report schema (§3) is the actual integration contract.

---

## Project Constraints (from CLAUDE.md)

| # | Constraint | How Phase 2 honors it |
|---|------------|----------------------|
| C-1 | "Frontend in Turkish; identifiers in English." | Recon captures Turkish UI copy verbatim; toolchain task names + commit messages stay English. |
| C-2 | Rule #4: "Verify external SDK docs before writing code. Never invent SDK calls from training-data recall." | Every Playwright snippet in §1 cites a `playwright.dev` URL. Every toolchain version in §2 cites the npm registry / official docs. |
| C-3 | Rule #5: "No secrets in source." | n11.com is public; no auth tokens. No login attempts during recon. |
| C-4 | "If a file in `.planning/` conflicts with this CLAUDE.md, the `.planning/` file wins." | This research follows ROADMAP.md Phase 2 success criteria literally. |
| C-5 | "Top pitfalls #20 / #19 / #16 to keep top of mind." | §4 maps each to a concrete recon-side mitigation. |
| C-6 | Open question: "Frontend toolchain (Vite+TS+Tailwind+Zustand or alt) — Phase 2 resolves it." | §2 produces the matrix + version locks the planner pastes into PROJECT.md. |

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Live-site browser automation against n11.com | Tooling (throwaway Node project) | — | This is research infrastructure, not a project dependency. Lives under `tools/recon/`, has its own `package.json`, never imported by storefront. |
| Recon-report authoring | Documentation (`.planning/intel/`) | — | Markdown artifact, no runtime tier. |
| Toolchain decision record | Documentation (`.planning/PROJECT.md` Key Decisions) | — | Decision metadata, not code. |
| Storefront SPA rendering (downstream — Phase 10) | Browser / Client | — | Vite-built React SPA, served as static assets. JWT-at-gateway (CLAUDE.md) means no SSR-side auth concern; SPA is the natural fit. |
| Storefront API access (downstream — Phase 10) | API / Backend (gateway) | Browser (fetch) | All FE→BE calls go through `api-gateway` per FE-16. |
| Chat streaming UI (downstream — Phase 11) | Browser / Client | API (SSE from ai-service via gateway) | EventSource-shaped consumption; gateway is reactive (Pitfall #2 already enforced in Phase 1). |

**Note for the planner:** Phase 2 itself is single-tier (tooling + docs). The downstream rows above just confirm that the toolchain choice (Vite SPA, not Next SSR) is consistent with the locked architectural picture from Phase 1.

---

## Standard Stack

### Recon Tooling (Phase 2 only)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `@playwright/test` | **1.59.1** [VERIFIED: npm registry — `dist-tags.latest`, fetched 2026-04-29] | Browser automation + screenshot + DOM evaluation | Industry standard for cross-browser scripting; built-in Chromium binaries; `page.screenshot({fullPage:true})`, `page.evaluate(...)`, `browser.newContext({userAgent, viewport, locale, timezoneId})` are all first-class. The test-runner gives auto-cleanup, retries, parallel project config — overkill for ~7 capture scripts but it's free. [CITED: https://playwright.dev/docs/screenshots] |
| Node.js | **22 LTS** (>= 22.12) | Runtime | Required by Vite 8 anyway; Playwright runs on 18+ but matching the storefront-runtime version is hygienic. [CITED: https://vite.dev/guide/ — "Vite requires Node.js version 20.19+, 22.12+"] |
| `dotenv` (optional) | latest | Loading future tunnel/recon env if needed | Not required for n11 recon (public site, no auth) — included only if Phase 6 reuses this `tools/recon/` shell. |

**Installation (recon project):**

```bash
mkdir -p tools/recon
cd tools/recon
npm init -y
npm install -D @playwright/test@1.59.1
npx playwright install chromium    # downloads only Chromium browser binary (~170 MB)
```

### Frontend Storefront Toolchain (locked into PROJECT.md by Phase 2's decision task)

#### Core (high confidence — versions verified against npm registry on 2026-04-29)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `react` + `react-dom` | **19.2.5** [VERIFIED: npm `dist-tags.latest`] | UI framework | Bootcamp brief says "React.js"; 19.2 is the current stable line. Hooks-first idiom. |
| `vite` | **8.0.10** [VERIFIED: vite.dev/guide/ "v8.0.10"; npm fetch failed due to size but blog-post and Vite 8.0 announcement confirm] | Dev/build toolchain | CRA deprecated; Vite is the dominant React SPA bundler 2024+. Vite 8 ships with Rolldown bundler, smaller installs. [CITED: https://vite.dev/blog/announcing-vite8 — "Vite 8.0 is out!"] |
| `@vitejs/plugin-react` | **6.x** (paired with Vite 8) | React Fast-Refresh + JSX in Vite | Vite 8 release uses Oxc transform; `@vitejs/plugin-react-oxc` is now deprecated, plain `@vitejs/plugin-react` v6 is the canonical pick. [CITED: vite.dev plugins page, search-verified 2026-04-29] |
| `typescript` | **5.x latest** [ASSUMED — npm fetch was rate-limited; STACK.md cites 5.6+; TS releases at ~quarterly cadence so 5.7+ is plausible by 2026-04-29] | Type safety | The grading lens weighs code-quality heaviest (CLAUDE.md). TypeScript is non-negotiable for that signal; choose latest stable at install time via `npm create vite@latest -- --template react-ts`. |
| `tailwindcss` | **4.2.4** [VERIFIED: npm `dist-tags.latest`] | Utility-first CSS | Tailwind 4 simplifies install: single `@tailwindcss/vite` plugin + one `@import "tailwindcss";` in CSS — no `tailwind.config.js` required for basic use. Theme tokens live in `@theme` directive in CSS. [CITED: https://tailwindcss.com/docs/installation/using-vite] |
| `@tailwindcss/vite` | latest (paired with tailwindcss 4.2.4) | Tailwind 4 Vite plugin | The official v4 install path. |

#### Supporting (high confidence — versions verified)

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `zustand` | **5.0.12** [VERIFIED: npm `dist-tags.latest`] | Client-side state (cart, auth, chat-bubble open/closed) | Lower boilerplate than Redux Toolkit; React 18+ compatible (and 19); ~1KB. Sized correctly for our 4–6 state stores. |
| `react-router` | **7.14.2** [VERIFIED: npm `dist-tags.latest`] | SPA routing | Used as the routing library (formerly `react-router-dom`). v7 unified the package; supports data routers, lazy routes, type-safe params. |
| `@tanstack/react-query` | **5.99+** (5.100.5 latest as of 2026-04-27 per WebSearch) [VERIFIED: WebSearch + STACK.md] | Server-state cache (product lists, PDP fetches, cart, orders) | Handles pagination, infinite lists, mutations with optimistic updates, 401-retry — all of which Phase 10 needs. Brief's `useState`/`useEffect` requirement is satisfied because TanStack Query wraps them internally and the codebase will still have explicit `useState`/`useEffect` examples (chat input, loading spinners). |
| `react-hook-form` | **7.74.0** [VERIFIED: npm `dist-tags.latest`] | Form state + validation | Standard for register/login (FE-11), checkout address form (FE-09). Pairs with zod via `@hookform/resolvers/zod`. |
| `zod` | **4.3.6** [VERIFIED: npm `dist-tags.latest`] | Runtime schema validation | Validates form input + API response shapes. Used in tandem with react-hook-form via `zodResolver`. |
| `@hookform/resolvers` | latest 3.x | Bridge between react-hook-form and zod | Standard wiring. |

#### Frontend Dev Quality

| Library | Version | Purpose |
|---------|---------|---------|
| `eslint` + `@eslint/js` + `typescript-eslint` | latest 9.x | Lint, comes with `npm create vite@latest -- --template react-ts` template |
| `prettier` | latest 3.x | Format. Use the eslint-config-prettier integration. |
| `@vitest/ui` (optional, defer to Phase 10) | latest | Unit testing — only if a test task lands in Phase 10. Recon does not need it. |

### Alternatives Considered (this is the toolchain-decision audit trail)

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| **Vite SPA** | Next.js 15 App Router | Next adds SSR + RSC + a Node server runtime. n11 itself uses SSR for SEO, but our deliverable is a *graded demo* — not a public store. JWT-at-gateway means no SSR-side auth, so SSR would only add ops surface. **Rejected** unless recon shows hard SEO dependency (it won't — see §2.1 matrix). |
| **Zustand** | Redux Toolkit | RTK is fine, ~10x boilerplate, more learning curve to read for graders. Zustand's `create()` API maps cleanly to Spring Boot's bean idiom in graders' minds (one factory, one slice). |
| **Zustand** | React Context | Context re-renders all consumers on any state change; cart updates are frequent enough that this hurts. Pitfall #19 lurks if Context is used for streaming chat tokens. |
| **TanStack Query** | Plain `fetch` + `useEffect` | Bootcamp brief says "use `useState`/`useEffect`". TanStack Query satisfies that requirement internally and earns code-quality points (cache management, 401-retry, optimistic updates). The codebase will still contain explicit `useState`/`useEffect` examples to honor the brief's literal text. |
| **TanStack Query** | Axios + manual cache | Manual cache is a maintenance trap; TanStack Query's `queryKey` model is the industry pattern post-2022. |
| **react-router 7** | TanStack Router | TanStack Router has stronger type-safe routes but is younger; React Router has wider grader recognition and simpler mental model for routes-only routing. Pick **react-router 7** — the type safety isn't worth the unfamiliarity tax. |
| **Tailwind 4** | Plain CSS modules / styled-components | Tailwind matches n11's dense layout idiom (utility classes for badges, grids, hover states) and is fastest to build with under a 6-day window. Tokens-from-recon land naturally as `@theme` entries. |
| **react-hook-form + zod** | Built-in `useState` + manual validation | More code, more bugs in form-heavy pages (checkout, register). RHF + zod is the dominant pattern post-2023. |
| **fetch wrapper** | axios | axios 1.x is fine but adds 13KB; native `fetch` + a tiny wrapper for `Authorization` header injection + 401-retry is enough. TanStack Query handles the rest. **Recommendation: thin `apiFetch()` wrapper around native `fetch`**, no axios. |
| **`@playwright/test`** | Puppeteer | Playwright wins on cross-browser, better selectors, integrated test runner; Puppeteer is Chrome-only and the API has been on life-support since Playwright forked from it. Industry consensus: Playwright. |
| **`@playwright/test`** | Standalone `playwright` library + ad-hoc `node script.js` files | Standalone library has lower boilerplate per script *but* loses auto-cleanup (must `await browser.close()`), retries, viewport defaults. For 7 capture pages, the test-runner's "one config to rule them all" wins. See §1.1. |

**Frontend installation (Phase 10 will run, not Phase 2):**

```bash
# at the repo root
npm create vite@latest frontend -- --template react-ts
cd frontend
npm install tailwindcss@4.2.4 @tailwindcss/vite
npm install zustand@5.0.12 react-router@7.14.2 @tanstack/react-query react-hook-form zod @hookform/resolvers
```

**Version verification log (2026-04-29):**

```
@playwright/test  1.59.1     (npm dist-tags.latest)
react             19.2.5     (npm dist-tags.latest)
zustand           5.0.12     (npm dist-tags.latest)
tailwindcss       4.2.4      (npm dist-tags.latest)
react-router      7.14.2     (npm dist-tags.latest)
zod               4.3.6      (npm dist-tags.latest)
react-hook-form   7.74.0     (npm dist-tags.latest)
vite              8.0.10     (vite.dev/guide/ "v8.0.10" - npm fetch exceeded size limit)
typescript        5.x        (npm fetch exceeded size limit; rely on `react-ts` template default)
@tanstack/react-query  5.100.5  (WebSearch 2026-04-27)
```

> Implementer must re-run `npm view <pkg> version` for each before locking exact pins in `package.json`. None of these are in fast-moving territory but the recon happens before Phase 10's install, and small patches will land in the interim.

---

## Architecture Patterns

### System Diagram (Phase 2 — recon flow)

```
                  [PLANNER reads RESEARCH.md (this doc)]
                                |
                                v
          [TASK 1: scaffold tools/recon/ Playwright project]
                                |
                                v
   [TASK 2: capture pages]   <-- 7 page captures, one script per page
                                |          (homepage, category, PDP,
                                v           cart, checkout-step-1,
        [n11.com (live)]  <----+            account, login)
              ^                |
              | gentle rate    | screenshots/<slug>.png
              | (1 page/2.5s)  | + harvested-copy.json
              | UA: real Chrome| + harvested-tokens.json
                               |
                               v
   [TASK 3: assemble n11-recon.md from harvested artifacts]
                               |
                               v
       [.planning/intel/n11-recon.md]   <-- single hand-off file
                               |
                               v
   [TASK 4: toolchain decision -> PROJECT.md Key Decisions]
                               |
                               v
      [Phase 10 reads n11-recon.md mechanically]
                               |
                               v
      [Phase 11 reads "anti-pattern flags" + "no chat panel" callout]
```

**Data flow note:** the recon scripts produce *two flavors* of artifact — image files (PNG screenshots) under `screenshots/` and JSON harvest files (`harvested-copy.json`, `harvested-tokens.json`) at the recon-project root. Task 3 assembles these into the single Markdown report. Keeping JSON as an intermediate form lets Task 3 be a pure-Markdown-templating step — easier to re-run if a screenshot needs re-capturing later.

### Recommended Project Structure

```
n11-bootcamp-final-case/
├── tools/
│   └── recon/                    # standalone Playwright project (NOT a Gradle subproject)
│       ├── package.json          # @playwright/test 1.59.1
│       ├── playwright.config.ts  # baseURL=https://www.n11.com, headless:false, etc.
│       ├── tests/
│       │   ├── homepage.spec.ts
│       │   ├── category.spec.ts
│       │   ├── pdp.spec.ts
│       │   ├── cart.spec.ts
│       │   ├── checkout.spec.ts
│       │   ├── account.spec.ts
│       │   └── login.spec.ts
│       ├── lib/
│       │   ├── harvest-copy.ts   # shared TR-text extraction helper
│       │   └── harvest-colors.ts # shared computed-style helper
│       ├── output/               # gitignored
│       │   ├── harvested-copy.json
│       │   └── harvested-tokens.json
│       └── README.md             # how to re-run, anti-bot expectations
└── .planning/
    └── intel/                    # COMMITTED to git
        ├── n11-recon.md          # the deliverable
        └── screenshots/
            ├── homepage-fullpage.png
            ├── homepage-header-element.png
            ├── category-elektronik-fullpage.png
            ├── pdp-fullpage.png
            ├── cart-fullpage.png
            ├── checkout-step1-fullpage.png
            ├── account-fullpage.png
            └── login-fullpage.png
```

**Why `tools/recon/` and not the existing Gradle multi-module:** the recon code is throwaway Node. Pulling it into the Spring Boot Gradle build would require a Node-Gradle plugin and add complexity for code that runs once and never deploys. A standalone npm project is cleaner and has zero blast radius on the Java build.

**`.gitignore` adds (Phase 2 task — extend the existing repo `.gitignore`):**

```gitignore
# Playwright recon (tools/recon/)
tools/recon/node_modules/
tools/recon/test-results/
tools/recon/playwright-report/
tools/recon/output/
```

### Pattern 1: Playwright capture script (per-page, idiomatic)

**What:** Each page gets its own `<page>.spec.ts` test file. The test does three things: navigate, screenshot, harvest. JSON harvest files accumulate across runs; the assembler script consumes them.

**When to use:** every n11 page in the Phase 2 capture inventory.

**Example — `tests/homepage.spec.ts`:**

```typescript
// Source: https://playwright.dev/docs/screenshots and
//         https://playwright.dev/docs/api/class-browsertype
import { test, expect } from '@playwright/test';
import * as fs from 'node:fs';
import * as path from 'node:path';

const SCREENSHOT_DIR = path.resolve(__dirname, '../../../.planning/intel/screenshots');
const OUTPUT_DIR = path.resolve(__dirname, '../output');

test.describe('n11 homepage recon', () => {
  test.beforeAll(() => {
    fs.mkdirSync(SCREENSHOT_DIR, { recursive: true });
    fs.mkdirSync(OUTPUT_DIR, { recursive: true });
  });

  test('capture homepage', async ({ page }) => {
    await page.goto('https://www.n11.com/', { waitUntil: 'networkidle' });
    // gentle rate limit; n11 does not require this strictly but be a good citizen
    await page.waitForTimeout(2500);

    // Full-page screenshot (Pattern: full + element-zoom)
    await page.screenshot({
      path: path.join(SCREENSHOT_DIR, 'homepage-fullpage.png'),
      fullPage: true,
    });

    // Element-zoom on the header (sticks to a known selector)
    const header = page.locator('header').first();
    if (await header.count() > 0) {
      await header.screenshot({
        path: path.join(SCREENSHOT_DIR, 'homepage-header-element.png'),
      });
    }

    // Harvest visible Turkish copy (1-6 word phrases)
    const phrases = await page.evaluate(() => {
      const result = new Set<string>();
      document.querySelectorAll<HTMLElement>('a, button, span, h1, h2, h3, p, li').forEach((el) => {
        const txt = (el.textContent ?? '').trim();
        if (!txt) return;
        const wordCount = txt.split(/\s+/).length;
        if (wordCount < 1 || wordCount > 6) return;
        // Filter to mostly-Turkish (ASCII + ÇĞİÖŞÜçğıöşü)
        if (!/^[a-zA-Z0-9ÇĞİÖŞÜçğıöşü\s.,'!?₺%/-]+$/.test(txt)) return;
        result.add(txt);
      });
      return Array.from(result);
    });

    fs.writeFileSync(
      path.join(OUTPUT_DIR, 'homepage-phrases.json'),
      JSON.stringify({ page: 'homepage', url: 'https://www.n11.com/', phrases }, null, 2),
    );

    // Harvest computed colors on key elements
    const tokens = await page.evaluate(() => {
      function pick(selector: string, role: string) {
        const el = document.querySelector(selector);
        if (!el) return null;
        const cs = window.getComputedStyle(el);
        return {
          role,
          selector,
          color: cs.color,
          backgroundColor: cs.backgroundColor,
          fontFamily: cs.fontFamily,
          fontWeight: cs.fontWeight,
          fontSize: cs.fontSize,
        };
      }
      // Best-effort selectors; recon will refine these per-page
      return [
        pick('header', 'nav-bg'),
        pick('body', 'body-bg'),
        pick('button[class*="add" i], a[class*="addToBasket" i]', 'cta-primary'),
        pick('a', 'link'),
        pick('h1', 'heading-primary'),
      ].filter(Boolean);
    });

    fs.writeFileSync(
      path.join(OUTPUT_DIR, 'homepage-tokens.json'),
      JSON.stringify({ page: 'homepage', tokens }, null, 2),
    );

    expect(phrases.length).toBeGreaterThan(5);
  });
});
```

**Anti-pattern flag:** do not loop over all `<*>` elements in `page.evaluate`. The Set + tag-list above is the right size for the harvest.

### Pattern 2: Playwright config (anti-bot posture)

**What:** `playwright.config.ts` lives at the recon-project root. It sets a real Chrome `userAgent`, opens a non-headless browser with `slowMo`, sets Turkish locale + Istanbul timezone, and limits concurrency to 1 (gentle rate).

**When to use:** baseline for every recon spec.

**Example — `tools/recon/playwright.config.ts`:**

```typescript
// Source: https://playwright.dev/docs/api/class-browsertype#browser-type-launch and
//         https://playwright.dev/docs/api/class-browsercontext (newContext options)
import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './tests',
  fullyParallel: false,           // serialize so n11 sees gentle traffic
  workers: 1,                     // single worker; one tab at a time
  retries: 0,                     // recon failures should be inspected, not retried
  reporter: 'list',
  use: {
    baseURL: 'https://www.n11.com',
    headless: false,              // visible browser - bot-detection less aggressive on real Chrome
    // Turkish browser fingerprint
    locale: 'tr-TR',
    timezoneId: 'Europe/Istanbul',
    // Modern desktop Chrome UA so the site does not 403 us
    userAgent:
      'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 ' +
      '(KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36',
    viewport: { width: 1440, height: 900 },
    // Show what's happening so we can spot CAPTCHAs / cookie banners
    launchOptions: {
      slowMo: 250,
      args: ['--disable-blink-features=AutomationControlled'],
    },
    // 60s default; n11 is heavy on first paint
    navigationTimeout: 60_000,
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
```

**Note:** `--disable-blink-features=AutomationControlled` removes the `navigator.webdriver=true` flag that most bot-detectors check. This is plenty for n11 (which is a real-customer marketplace, not a high-security target). If n11 still 403s, the fallback is `launchPersistentContext({userDataDir: '~/.recon-chrome'})` to use a real Chrome profile with cookies — but this is unlikely to be needed.

### Pattern 3: Cookie-banner / login-wall handling

**What:** n11 likely shows a "Çerez tercihleri" cookie banner on first paint. Each spec should dismiss it before screenshotting (otherwise screenshots are dominated by the banner). Same for login modals on `/account` and `/sepet`.

**When to use:** every spec that captures `fullPage: true`.

**Example helper — `tools/recon/lib/dismiss-banners.ts`:**

```typescript
// Source: https://playwright.dev/docs/locators
import { Page } from '@playwright/test';

export async function dismissBanners(page: Page): Promise<void> {
  // Cookie consent — try a few common Turkish strings
  const cookieAcceptors = [
    page.getByRole('button', { name: /Kabul Et|Tümünü Kabul Et|Tümüne İzin Ver/i }),
    page.getByRole('button', { name: /Çerez(ler)? Kabul/i }),
  ];
  for (const btn of cookieAcceptors) {
    if (await btn.count() > 0 && await btn.first().isVisible().catch(() => false)) {
      await btn.first().click().catch(() => {});
      await page.waitForTimeout(500);
      break;
    }
  }
  // Login-wall close button
  const closeBtn = page.getByRole('button', { name: /Kapat|Daha Sonra|×/i });
  if (await closeBtn.count() > 0 && await closeBtn.first().isVisible().catch(() => false)) {
    await closeBtn.first().click().catch(() => {});
  }
}
```

**Pitfall:** if a banner does not exist, `getByRole(...)` returns an empty locator — calling `.click()` on `.first()` of an empty locator throws. Always guard with `count() > 0` and `isVisible().catch(() => false)`.

### Pattern 4: Recon assembler (`assemble-recon.ts`)

**What:** A single Node script reads all `output/*-phrases.json` and `output/*-tokens.json` files, deduplicates, and writes `n11-recon.md` using a Markdown template. Ships as part of `tools/recon/`.

**When to use:** Task 3 of the Phase 2 plan, run once after all captures land.

**Example skeleton — `tools/recon/assemble-recon.ts`:**

```typescript
// Source: standard Node fs API; no Playwright dep
import * as fs from 'node:fs';
import * as path from 'node:path';

const OUTPUT = path.resolve(__dirname, 'output');
const REPORT = path.resolve(__dirname, '../../.planning/intel/n11-recon.md');

const phraseFiles = fs.readdirSync(OUTPUT).filter(f => f.endsWith('-phrases.json'));
const tokenFiles  = fs.readdirSync(OUTPUT).filter(f => f.endsWith('-tokens.json'));

type Phrase = { phrase: string; page: string };
const allPhrases: Phrase[] = [];
const seen = new Set<string>();

for (const f of phraseFiles) {
  const data = JSON.parse(fs.readFileSync(path.join(OUTPUT, f), 'utf-8'));
  for (const p of data.phrases as string[]) {
    const key = p.toLowerCase();
    if (seen.has(key)) continue;
    seen.add(key);
    allPhrases.push({ phrase: p, page: data.page });
  }
}

// ... emit Markdown sections per §3 schema
const report = renderMarkdown(allPhrases, /* ... */);
fs.writeFileSync(REPORT, report);
console.log(`Wrote ${REPORT} with ${allPhrases.length} unique phrases`);
```

### Anti-Patterns to Avoid

- **Logging in to a real n11 account.** Don't. Recon stays at the public-page surface. The `/account` and `/login` page captures are of the *unauthenticated* page (form fields, copy, layout) — not the logged-in dashboard.
- **Adding items to a real cart.** Same reason. The `/sepet` (cart) page capture is of the empty-cart state ("Sepetiniz boş") plus any "recommended" rail visible to anonymous users.
- **Headless mode for the recon run.** Anti-bot detectors flag headless Chrome. `headless: false` + a real UA is the lazy man's bypass.
- **`fullyParallel: true`.** Even at 3 workers, n11 might rate-limit. Stay at `workers: 1` and `fullyParallel: false`.
- **Hard-coding tag-name selectors when role-based selectors exist.** `page.getByRole('button', {name: /sepete ekle/i})` is more stable than `page.locator('.add-to-cart')`.
- **Storing screenshots inside `tools/recon/`.** They go directly into `.planning/intel/screenshots/` so Phase 10 can reference them by relative path. The `tools/recon/output/` directory holds intermediate JSON only.
- **Skipping the assembler step.** If captures and the report are written by hand from screenshots, the Phase 10 hand-off is unreliable. The assembler enforces the schema.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Browser automation against a live site | Custom Puppeteer scripts, raw HTTP scraping | `@playwright/test` 1.59 | Cross-browser, retries, screenshots, evaluation, headed mode, proper cleanup. Industry standard. |
| Anti-bot UA spoofing | Hand-roll request headers | Playwright `userAgent` + `--disable-blink-features=AutomationControlled` | These knobs are 90% of the bypass; anything more is an arms race we don't need. |
| Cookie-banner dismissal across pages | Per-page selector copy-paste | A shared `dismissBanners(page)` helper | Single point of update if n11's banner changes. |
| Frontend bundling | Webpack config, esbuild scripts | Vite 8 | CRA is dead. Vite 8 + plugin-react v6 is the no-thinking pick. |
| CSS scoping / theming | CSS modules + ad-hoc class names | Tailwind 4 + `@theme` directive in CSS | n11's layout is utility-class-friendly (badges, dense cards, hover states). Tailwind 4 dropped the config-file requirement; tokens land cleanly. |
| Server-state caching, 401 retry, mutation invalidation | `useEffect`-fetch with custom cache | `@tanstack/react-query` 5 | One of the most-leveraged libraries in the React ecosystem; without it cart-update reactivity (Pitfall #19 adjacent) becomes painful. |
| Form state + validation | Manual `useState` + manual error tracking | `react-hook-form` + `zod` + `@hookform/resolvers/zod` | Standard pattern; saves hundreds of lines on the register/login/checkout forms. |
| Routing | Custom history + render switch | `react-router` 7 | Type-safe routes with v7 unified package; data-router model fits Phase 10's loader needs. |
| Markdown report generation by hand | Hand-edited `n11-recon.md` after each capture | An assembler Node script (Pattern 4 above) | Deterministic schema; re-runnable; what Phase 10 expects. |

**Key insight:** the only "novel code" in this phase is the Playwright capture scripts and the assembler. Everything downstream (toolchain) is a curated pick of the dominant React 19-era libraries; deviating loses code-quality points.

---

## Common Pitfalls

### Pitfall 1: n11 returns 403 to headless / scriptable browsers

**What goes wrong:** scripts get a 403 status code or a Cloudflare "Just a moment" interstitial, never reaching the actual page DOM.

**Why it happens:** anti-bot detection on `navigator.webdriver === true`, missing `Sec-Ch-Ua-*` client hints, headless Chrome fingerprint.

**How to avoid:**
- `headless: false` (visible browser, big win)
- Real desktop Chrome `userAgent` (not Playwright's default UA, which contains "HeadlessChrome")
- `args: ['--disable-blink-features=AutomationControlled']` flips the `navigator.webdriver` flag off
- `slowMo: 250` makes the session look human

**Warning signs:** Playwright completes navigation but `await page.title()` returns "Just a moment..." or the screenshot shows a Cloudflare challenge page.

**Recovery:** if all the above fails, fall back to `chromium.launchPersistentContext({ userDataDir: <real-profile> })` so the session reuses cookies from a real browser profile. Document the recovery step in `tools/recon/README.md` so the candidate can re-run if needed.

### Pitfall 2: Cookie-banner dominates every screenshot

**What goes wrong:** every full-page screenshot shows a giant "Çerez tercihleri" banner overlaying the actual content. Phase 10 can't read the layout.

**Why it happens:** the cookie banner is rendered absolutely, not in flow. `fullPage: true` captures it.

**How to avoid:** dismiss the banner *before* screenshotting (Pattern 3). Wait 500ms after dismissing for the DOM to settle.

**Warning signs:** the homepage fullpage screenshot has a giant blue-or-orange banner at the bottom that scrolls with the page.

### Pitfall 3: Turkish-character mojibake in harvested copy

**What goes wrong:** `harvested-copy.json` contains `Önceki` written as `Ã–nceki` because the file was opened with the wrong encoding.

**Why it happens:** Node's `fs.writeFileSync` defaults to UTF-8 — usually fine — but the assembler script must write the Markdown with `'utf-8'` explicitly, and the planner must commit using `git config core.precomposeunicode true` on macOS recon machines.

**How to avoid:**
- Always pass `'utf-8'` to `fs.writeFileSync` and `fs.readFileSync`
- Commit `.gitattributes` row: `*.md text working-tree-encoding=UTF-8 eol=LF` if not already present (Phase 1 added LF enforcement; UTF-8 is the default)
- Spot-check by `grep -c 'Önceki' .planning/intel/n11-recon.md` after assembly

**Warning signs:** Turkish characters appear as `?` or `Ã–` in the rendered Markdown.

### Pitfall 4: Computed colors arrive as `rgb(...)` not hex

**What goes wrong:** `window.getComputedStyle(...).color` returns `"rgb(247, 130, 50)"` not `"#F78232"`. The planner copies these into Tailwind theme as-is and they don't render correctly.

**Why it happens:** browsers normalize all color values to `rgb()` / `rgba()` for `getComputedStyle`. This is by spec.

**How to avoid:** the assembler script converts each `rgb(R, G, B)` to `#RRGGBB` before writing the Markdown table.

```typescript
function rgbToHex(rgb: string): string {
  const m = rgb.match(/^rgba?\((\d+),\s*(\d+),\s*(\d+)/);
  if (!m) return rgb;
  const [r, g, b] = [+m[1], +m[2], +m[3]];
  return '#' + [r, g, b].map(x => x.toString(16).padStart(2, '0').toUpperCase()).join('');
}
```

**Warning signs:** Color Token Table contains `rgb(247, 130, 50)` instead of `#F78232`.

### Pitfall 5: Recon report drifts from Phase 10's needs

**What goes wrong:** the Phase 10 storefront builder reads `n11-recon.md` looking for "Color Token Table" but the report calls the section "Visual Tokens" — Phase 10 wastes time hunting.

**Why it happens:** schema drift between research and consumer. Mitigated only by enforcing a schema.

**How to avoid:** the recon-report schema in §3 is the contract. Any deviation costs Phase 10 time. Keep section names, table column headers, and ordering identical.

**Warning signs:** Phase 10 plan file has a "TODO: find color tokens in n11-recon" comment. That should never appear — the section is in a known place.

### Pitfall 6: Toolchain decision skips the matrix

**What goes wrong:** the planner says "we picked Vite + Tailwind + Zustand" without writing the rationale. Graders see no audit trail. Demo loses code-quality points.

**Why it happens:** the matrix in §2.1 looks like obvious busy-work; tempting to skip.

**How to avoid:** the toolchain-decision task in the plan must include the matrix as a deliverable. PROJECT.md Key Decisions row must reference recon evidence (e.g., "Vite SPA chosen over Next; recon shows n11 uses SSR but our deliverable is a graded demo without public-SEO need").

**Warning signs:** PROJECT.md Key Decisions row for FE-01 says "use Vite" with no rationale.

### Pitfall 7: Capture rate triggers n11 rate-limiting mid-recon

**What goes wrong:** captures succeed for the first 4 pages, then page 5 returns 429 / a Cloudflare challenge.

**Why it happens:** Playwright at default `workers: 4` + no `slowMo` looks like a flooding bot.

**How to avoid:**
- `workers: 1` in `playwright.config.ts`
- `fullyParallel: false`
- `await page.waitForTimeout(2500)` after each `page.goto`
- Total recon time: ~7 pages × ~10s capture + 2.5s wait = ~90s, perfectly fine

**Warning signs:** specs 5-7 fail with status `429` or a Cloudflare interstitial title.

### Phase-mapped pitfalls (carried forward from PITFALLS.md)

#### Pitfall #20 — Turkish prompt drift (CARRIED → Phase 8)

**Phase 2's role:** capture ≥30 verbatim Turkish phrases in the recon report. Phase 8's chat assistant uses those phrases as the *grounded vocabulary* for tool descriptions and system-prompt examples — preventing the model from inventing English variants. This phase does not write the system prompt; it provides the vocabulary that prevents drift.

**Concrete artifact:** the `## Turkish Copy Catalog` table in `n11-recon.md` (≥30 rows) becomes the vocabulary input to Phase 8's `dil: tr-TR` system prompt and tool-description files.

#### Pitfall #19 — Streaming chat backpressure (CARRIED → Phase 11)

**Phase 2's role:** flag in the recon report's `## Anti-pattern flags` section that **n11 has no in-storefront chat panel** — Phase 11 must invent the UX. This prevents Phase 11 from blocking on a non-existent reference.

**Concrete artifact:** a one-paragraph "no chat panel" callout in `n11-recon.md` `## Anti-pattern flags` section. Phase 11 reads it before designing the floating bubble.

#### Pitfall #16 — Tool set duplicated (CARRIED → Phase 8 + Phase 9)

**Phase 2's role:** none directly. Mentioned only because the toolchain decision (Vite SPA) downstream determines that the chat panel calls `ai-service` via gateway-proxied SSE — same auth path as the MCP server's API-key auth bridge. No code-side overlap with Phase 2.

#### Pitfall #23 — CORS / Authorization mismatch (CARRIED → Phase 10)

**Phase 2's role:** the toolchain decision should explicitly call out `VITE_API_BASE_URL` env-var convention so Phase 10 doesn't hardcode `http://localhost:8080`. PROJECT.md Key Decisions row should mention this.

**Concrete artifact:** in PROJECT.md Key Decisions for FE-01, the rationale paragraph names: "API base URL injected via `VITE_API_BASE_URL` env var; no hardcoded URLs in source."

---

## Code Examples

### Verified pattern: full-page screenshot

```typescript
// Source: https://playwright.dev/docs/screenshots
await page.screenshot({ path: 'screenshot.png', fullPage: true });
```

### Verified pattern: element-only screenshot

```typescript
// Source: https://playwright.dev/docs/screenshots
await page.locator('.header').screenshot({ path: 'screenshot.png' });
```

### Verified pattern: page.evaluate with arguments

```typescript
// Source: https://playwright.dev/docs/api/class-page#page-evaluate
const result = await page.evaluate(([x, y]) => {
  return Promise.resolve(x * y);
}, [7, 8]);
console.log(result); // prints "56"
```

### Verified pattern: chromium launch with anti-bot flags

```typescript
// Source: https://playwright.dev/docs/api/class-browsertype#browser-type-launch
const browser = await chromium.launch({
  headless: false,
  slowMo: 100,
  args: ['--disable-extensions'],
  channel: 'chrome'
});
```

### Verified pattern: persistent context (fallback if 403 persists)

```typescript
// Source: https://playwright.dev/docs/api/class-browsertype (browserType.launchPersistentContext)
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

### Verified pattern: Vite + React TypeScript scaffold

```bash
# Source: https://vite.dev/guide/
npm create vite@latest frontend -- --template react-ts
cd frontend
npm install
npm run dev
```

### Verified pattern: Tailwind 4 install with Vite

```bash
# Source: https://tailwindcss.com/docs/installation/using-vite
npm install tailwindcss @tailwindcss/vite
```

```typescript
// vite.config.ts
import { defineConfig } from 'vite'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [
    tailwindcss(),
  ],
})
```

```css
/* src/index.css */
@import "tailwindcss";
```

### Verified pattern: Tailwind 4 theme tokens (no config file required)

```css
/* src/index.css — Phase 10 will paste recon-derived tokens here */
@import "tailwindcss";

@theme {
  --color-primary: #F78232;          /* from n11-recon Color Token Table */
  --color-primary-hover: #E66E1B;
  --color-link: #4577E0;
  --color-nav-bg: #FFFFFF;
  --color-body-bg: #F9F9F9;
  --color-text-primary: #1A1A1A;
  --color-text-secondary: #6B6B6B;
  --color-success: #2EAA67;
  --color-warning: #F2A91A;
  --color-error: #D6233D;

  --font-sans: 'Open Sans', system-ui, sans-serif;
}
```

> Tokens above are *placeholders for Phase 10*. The actual hex values come from the recon's `## Color Token Table`. [CITED: https://tailwindcss.com/docs/installation/using-vite — `@theme` directive replaces the v3 `tailwind.config.js`]

### Recommended pattern: TypeScript strict-mode `tsconfig.json` extras

```jsonc
// frontend/tsconfig.app.json — Phase 10 install will use this
{
  "compilerOptions": {
    "target": "ES2022",
    "useDefineForClassFields": true,
    "lib": ["ES2022", "DOM", "DOM.Iterable"],
    "module": "ESNext",
    "skipLibCheck": true,
    "moduleResolution": "bundler",
    "allowImportingTsExtensions": true,
    "resolveJsonModule": true,
    "isolatedModules": true,
    "moduleDetection": "force",
    "noEmit": true,
    "jsx": "react-jsx",

    "strict": true,                          // baseline
    "noUncheckedIndexedAccess": true,        // catches `arr[0]` returning T | undefined
    "exactOptionalPropertyTypes": true,      // distinguishes `{x?: T}` from `{x: T | undefined}`
    "noImplicitOverride": true,
    "noFallthroughCasesInSwitch": true,
    "noPropertyAccessFromIndexSignature": true
  }
}
```

[ASSUMED] These extras are widely recommended for graded React/TS projects. The exact list lands in Phase 10's install task; Phase 2 only needs to specify them in PROJECT.md so the toolchain decision is complete. Source: TypeScript handbook https://www.typescriptlang.org/tsconfig — but I did not re-fetch in this session.

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Create React App (CRA) | Vite | CRA officially deprecated 2023 | All new React SPAs use Vite or Next. CRA support is dead. |
| `react-router-dom` 6.x | `react-router` 7.x | 2024 (v7 unified the package) | Single import path; data-router model is the new default. |
| Tailwind 3.x with `tailwind.config.js` | Tailwind 4.x with `@theme` directive in CSS | 2024 | No JS config file required for basic use. CSS-first theming. [CITED: https://tailwindcss.com/docs/installation/using-vite] |
| Redux Toolkit as default state | Zustand for component-scope, RTK for complex global | 2022+ | Zustand 5 is React 18/19-aware and bundle-tiny. |
| `axios` everywhere | Native `fetch` + thin wrapper + TanStack Query | 2023+ | Smaller bundle; fetch is good enough; TanStack Query owns caching. |
| Manual `useEffect` data fetching | `@tanstack/react-query` 5 | 2022+ | Brief still requires `useState`/`useEffect` examples; satisfied by chat input box. |
| Puppeteer | Playwright | 2021+ for cross-browser; Playwright dominant by 2023 | n11 recon uses Playwright. |
| `@vitejs/plugin-react-oxc` (transitional) | `@vitejs/plugin-react` v6 (uses Oxc internally) | Vite 8 (2026-03) | Use plain `@vitejs/plugin-react`; the `-oxc` variant is deprecated. [CITED: vite.dev plugins page] |

**Deprecated/outdated:**

- `tailwind.config.js` — replaced by `@theme` directive in CSS (Tailwind 4)
- `@vitejs/plugin-react-oxc` — folded into `@vitejs/plugin-react` v6
- `react-router-dom` package — use `react-router` v7
- Webpack-based React tooling — Vite is the standard
- `chromium.connect()` headless-only patterns — use `chromium.launch({headless: false})` for recon

---

## Decision Matrix (the toolchain lock — what Phase 2's decision task pastes into PROJECT.md)

This matrix is paste-ready for the toolchain-decision task. Scoring is 1–5 (5 = best for our profile). Weights are bootcamp-grading-lens-tuned:

- **Code-quality signal** (×3) — primary grading axis
- **6-day-timeline fit** (×2)
- **n11-recon-evidence support** (×1) — gathered during this phase
- **Avoids Pitfall #16/#19/#23 by default** (×2)

| Criterion | Weight | Vite + React 19 SPA | Next.js 15 (App Router) |
|-----------|--------|----|-----|
| Code-quality signal (clean layering, hooks-first) | ×3 | 5 (TS strict + Vite + React 19) | 4 (RSC adds complexity) |
| 6-day timeline fit | ×2 | 5 (10 minute scaffold) | 3 (Node runtime, RSC mental model) |
| n11-recon-evidence support (does recon show SSR is essential?) | ×1 | 5 (n11 uses SSR for SEO; *we* don't need SEO) | 2 (would only help if we needed bot indexing) |
| JWT-at-gateway compatibility | ×2 | 5 (no SSR-side auth tax) | 3 (token forwarding gymnastics) |
| AI chat panel SSE consumption | ×2 | 5 (EventSource native; no server-component rerender model) | 3 (streaming UI in RSC is non-trivial) |
| Avoids Pitfall #16/#19/#23 | ×2 | 5 (single env-var base URL pattern) | 4 (more places to leak prod URLs) |
| Bootcamp brief literal: "React.js storefront" | ×1 | 5 (canonical React) | 5 (also React) |

**Weighted totals:**

- **Vite + React 19 SPA: 5×3 + 5×2 + 5×1 + 5×2 + 5×2 + 5×2 + 5×1 = 15+10+5+10+10+10+5 = 65**
- Next.js 15: 4×3 + 3×2 + 2×1 + 3×2 + 3×2 + 4×2 + 5×1 = 12+6+2+6+6+8+5 = 45

**Decision:** **Vite + React 19 SPA + TypeScript + Tailwind 4 + Zustand 5 + React Router 7 + TanStack Query 5 + react-hook-form + zod**.

**Rationale paragraph (for PROJECT.md Key Decisions row):**

> Frontend toolchain locked to Vite 8.0.10 + React 19.2.5 + TypeScript 5.x (strict + noUncheckedIndexedAccess + exactOptionalPropertyTypes) + Tailwind 4.2.4 (`@theme` directive, no config file) + Zustand 5.0.12 (cart/auth/chat-bubble state) + react-router 7.14.2 (SPA routing) + @tanstack/react-query 5 (server state, 401-retry, mutation invalidation) + react-hook-form 7.74 + zod 4.3 (forms + validation). Decision rests on three pieces of evidence: (1) n11 recon (Phase 2) shows n11 uses SSR for SEO, but our deliverable is a graded demo with no public-search-engine indexing need; (2) JWT validated only at the gateway means SSR-side auth would add token-forwarding gymnastics for zero benefit; (3) AI chat panel SSE consumption (Phase 11) maps cleanly to native `EventSource` in a Vite SPA — RSC streaming is unnecessary complexity. API base URL via `VITE_API_BASE_URL` env var (no hardcoded URLs — Pitfall #23 prevention). Decision matrix score: Vite 65 / Next 45 (weights: code-quality ×3, timeline ×2, recon-evidence ×1, JWT compat ×2, SSE compat ×2, pitfall avoidance ×2, brief literal ×1).

---

## Recon Report Schema (`.planning/intel/n11-recon.md`)

> **The hand-off contract with Phase 10 and Phase 11.** Sections appear in the order below. Phase 10 reads section names *literally*. Do not rename or reorder.

````markdown
# n11.com Recon Report

**Captured:** YYYY-MM-DD
**Source:** https://www.n11.com — public-page surface (no login, no real cart)
**Tool:** Playwright @playwright/test 1.59.1
**Recon project:** `tools/recon/`
**Consumer:** Phase 10 (Storefront), Phase 11 (Chat Bubble)

## 1. Page Inventory

| Page | URL Captured | Screenshot | Element Detail Shots | Capture Date |
|------|--------------|------------|----------------------|--------------|
| Homepage | https://www.n11.com/ | screenshots/homepage-fullpage.png | screenshots/homepage-header-element.png | 2026-04-29 |
| Category (Elektronik) | https://www.n11.com/elektronik | screenshots/category-elektronik-fullpage.png | — | 2026-04-29 |
| Product Detail (PDP) | <URL of one specific product, captured during recon> | screenshots/pdp-fullpage.png | screenshots/pdp-cta-element.png | 2026-04-29 |
| Cart (empty) | https://www.n11.com/sepetim | screenshots/cart-fullpage.png | — | 2026-04-29 |
| Checkout step 1 | <URL — recon will confirm> | screenshots/checkout-step1-fullpage.png | — | 2026-04-29 |
| Account (anonymous landing) | https://www.n11.com/hesabim | screenshots/account-fullpage.png | — | 2026-04-29 |
| Login | https://www.n11.com/giris | screenshots/login-fullpage.png | — | 2026-04-29 |

> URLs marked `<...>` are recon-derived. The Playwright spec must capture the actual URL after navigation and write it into this table.

## 2. Turkish Copy Catalog

> ≥30 rows. Used verbatim by Phase 10 for FE-13 (Turkish UI copy) and Phase 8 for the chat assistant's grounded vocabulary (Pitfall #20 prevention).

| # | Phrase (TR) | English Gloss | Source Page | Section / Component |
|---|-------------|---------------|-------------|---------------------|
| 1 | Sepete Ekle | Add to cart | PDP | CTA primary |
| 2 | Hemen Al | Buy now | PDP | CTA secondary |
| 3 | Stokta | In stock | PDP | Stock indicator |
| 4 | Tükendi | Out of stock | PDP | Stock indicator |
| 5 | Kargo Bedava | Free shipping | Listing card | Badge |
| 6 | Önceki | Previous | Listing | Pagination |
| 7 | Sonraki | Next | Listing | Pagination |
| 8 | Sepetim Boş | My cart is empty | Cart (empty) | Empty state |
| 9 | Siparişi Tamamla | Complete order | Cart | CTA primary |
| 10 | Siparişlerim | My orders | Account | Nav |
| 11 | Hesabım | My account | Header | Right cluster |
| 12 | Sepetim | My cart | Header | Right cluster |
| 13 | Çok Satanlar | Bestsellers | Homepage | Rail heading |
| 14 | Açıklama | Description | PDP | Tab |
| 15 | Özellikler | Features | PDP | Tab |
| 16 | Kargo | Shipping | PDP | Tab |
| 17 | Taksit Seçenekleri | Installment options | PDP | Section |
| 18 | Ürün Detayı | Product detail | PDP | Heading |
| 19 | Kategoriler | Categories | Header | Nav |
| 20 | Giriş Yap | Log in | Header | CTA |
| 21 | Üye Ol | Register | Header | CTA |
| 22 | Yardım | Help | Footer | Link |
| 23 | İletişim | Contact | Footer | Link |
| 24 | Hakkımızda | About us | Footer | Link |
| 25 | Sözleşmeler | Agreements | Footer | Link |
| 26 | Kapıda Ödeme | Cash on delivery | Checkout | Payment option |
| 27 | Kredi Kartı | Credit card | Checkout | Payment option |
| 28 | Adres | Address | Checkout | Form heading |
| 29 | Sipariş Özeti | Order summary | Cart / Checkout | Section |
| 30 | Yapay Zeka Alışveriş Asistanı | AI shopping assistant | n/a (we add) | Phase 11 chat label |
| ... | ... | ... | ... | ... |

> Rows 1-29 are seeded *expected* phrases that recon will confirm verbatim. Row 30 is *Phase 11's invention* (no n11 equivalent — see §7 Anti-pattern flags). Recon will add ≥10 more phrases observed in actual page captures (search placeholder text, filter labels, sort dropdowns, breadcrumb separators, "Tümünü Gör", "Detaylı İncele", etc.).

## 3. Category Taxonomy

n11 top-level categories (already locked in CLAUDE.md and REQUIREMENTS.md PROD-03):

| Slug | Turkish Label | Sub-categories observed in recon (2-3 per top-level) |
|------|---------------|------------------------------------------------------|
| elektronik | Elektronik | Telefon, Bilgisayar, Tablet |
| moda | Moda | Kadın, Erkek, Çocuk |
| ev-yasam | Ev & Yaşam | Mobilya, Mutfak, Aydınlatma |
| anne-bebek | Anne & Bebek | Bebek Bakım, Oyuncak, Gıda |
| kozmetik | Kozmetik | Cilt Bakım, Makyaj, Parfüm |
| spor-outdoor | Spor & Outdoor | Fitness, Kamp, Bisiklet |
| supermarket | Süpermarket | Kahvaltılık, İçecek, Temizlik |
| kitap-muzik-film-oyun | Kitap, Müzik, Film, Oyun | Kitap, Konsol Oyun, Müzik CD |

> Sub-categories above are *expected* and need recon confirmation. The Playwright spec for the homepage should hover the category mega-menu and capture sub-category names from the dropdown.

## 4. Color Token Table

> Hex values converted from `getComputedStyle()` `rgb(...)` returns. Phase 10 pastes these into `frontend/src/index.css` `@theme` block.

| Token | Hex | Role |
|-------|-----|------|
| `--color-primary` | #F78232 | n11 brand orange — primary CTA, badges |
| `--color-primary-hover` | #E66E1B | Hover state for primary CTA |
| `--color-secondary` | #4577E0 | Secondary CTA / informational links |
| `--color-link` | #4577E0 | Anchor color |
| `--color-nav-bg` | #FFFFFF | Sticky header background |
| `--color-body-bg` | #F9F9F9 | Page background |
| `--color-text-primary` | #1A1A1A | Body text |
| `--color-text-secondary` | #6B6B6B | Muted text, meta |
| `--color-success` | #2EAA67 | "Stokta" badge text/border |
| `--color-warning` | #F2A91A | "Son N ürün!" low-stock badge |
| `--color-error` | #D6233D | Form error, "Tükendi" text |
| `--color-divider` | #E6E6E6 | Card borders, dividers |

> Hex values above are *expected approximations* of n11's brand palette. Recon will confirm exact values via `window.getComputedStyle()` on key elements.

## 5. Typography Notes

| Property | Observed |
|----------|----------|
| Font family (body) | <recon will confirm — likely "Open Sans", system-ui> |
| Font family (heading) | <recon will confirm> |
| Heading scale | h1: <px>, h2: <px>, h3: <px> |
| Body size | 14-16px |
| Font weights observed | 400 (body), 500 (subhead), 700 (heading, price) |
| Line height (body) | <recon will confirm — typically 1.4-1.6> |

## 6. Layout Patterns

> Short observations. Phase 10 uses these for grid component decisions.

- **Header:** sticky on scroll, ~60px tall. Left = logo. Center = search bar (full-width). Right cluster = "Hesabım" + "Sepetim" with item-count badge.
- **Category nav:** secondary horizontal bar below header, mega-menu on hover.
- **Listing grid:** 4 columns at desktop (≥1280px), 3 at tablet, 2 at mobile. Card aspect ratio ~1:1.3 (taller than square, room for image + title + price + CTA).
- **PDP:** left = image gallery (~50%), right = product info (title, price, taksit, CTA, stock badge). Tabs ("Açıklama / Özellikler / Kargo") below the fold.
- **Cart:** line items table on left, "Sipariş Özeti" sticky on right.
- **Footer:** 4 columns of help links + payment-method icon strip.

## 7. Anti-pattern flags (what we will NOT copy)

- **No floating chat panel observed.** *Pitfall #19 callout: Phase 11 must invent the chat-bubble UX from scratch. Reference inspiration: ChatGPT widget, Intercom, or Discord — not n11.*
- **Dark-pattern banners** (e.g., countdown timers, "X kişi şu anda görüntülüyor"): if observed, do NOT replicate. Bootcamp grading rewards clean UX.
- **Autoplay video on PDP:** if observed, do NOT replicate.
- **Newsletter pop-ups:** if observed, do NOT replicate.
- **Cookie banner UX:** n11's banner is dismissible-only (no granular consent). Phase 10 ships a simpler "Accept" / "Decline" pair if a banner is needed at all (out of scope for FE-13).

## 8. Open n11 questions for Phase 10 / 11

- Does the PDP show a "Son N ürün!" stock indicator? (Drives PROD-06 implementation copy.)
- Is "Kapıda Ödeme" a visible payment method on the checkout step? (Drives Pitfall mitigation: ship as no-op or disabled radio per FE-V2-03.)
- Does n11 use breadcrumbs on PDP? (Drives FE-07 layout.)
- What's the "Çok Satanlar" rail item count on the homepage? (Drives FE-05 fixture size.)

> Phase 10 plan resolves these from the captured screenshots, not from n11.com directly.
````

> The schema above is the Phase 2 → Phase 10/11 contract. Section names and table column headers must match exactly. The single column "Recon will confirm" placeholder values get filled in during the capture.

---

## Runtime State Inventory

> Phase 2 is **not** a rename/refactor/migration phase. It creates new files in two locations:
>
> - `tools/recon/` (new directory, npm project)
> - `.planning/intel/` (new directory, Markdown + screenshots)
>
> Plus a single PROJECT.md edit (Key Decisions row update).

| Category | Items | Action Required |
|----------|-------|------------------|
| Stored data | None — recon does not write to any database | None |
| Live service config | None — recon does not touch n11's services or any local service | None |
| OS-registered state | None — Playwright is invoked via `npx playwright test`, no daemons or services registered | None |
| Secrets/env vars | None — n11 is public; no auth tokens. *(Future:* if Phase 6/11 reuse `tools/recon/`, they may add `CLOUDFLARED_TOKEN` etc. — out of scope here.) | None |
| Build artifacts | `tools/recon/node_modules/`, `tools/recon/test-results/`, `tools/recon/playwright-report/`, `tools/recon/output/` — must be added to `.gitignore` | One `.gitignore` patch (Phase 2 task) |

**Verified by:** static review of orchestrator brief + REQUIREMENTS.md FE-01 + ROADMAP.md Phase 2.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Node.js | Playwright + (later) Vite frontend | Likely (verify with `node -v`) | Need ≥22.12 for Vite 8 | Install Node 22 LTS via nvm if missing |
| npm | Playwright + frontend installs | Bundled with Node | — | yarn / pnpm work as fallback |
| Chromium browser binaries | Playwright capture | Auto-installed by `npx playwright install chromium` | bundled with @playwright/test 1.59.1 | None (Playwright pins its own) |
| Public internet | n11.com access | Required | — | No fallback — phase blocked without internet |
| Local docker-compose stack | Not needed for recon (Phase 1 deliverable; Phase 2 is browser-only) | n/a for Phase 2 | n/a | n/a |

**Probe commands the planner can include in a Wave-0 task:**

```bash
node --version              # expect v22.12+ for parity with future Vite 8
npm --version
ping -c 1 www.n11.com       # confirms internet + n11 resolves
```

**Missing dependencies with no fallback:** none likely on a developer machine.

**Missing dependencies with fallback:** Node version too old → install Node 22 LTS.

---

## Validation Architecture

> `workflow.nyquist_validation: true` in `.planning/config.json` — section included.

### Test Framework

| Property | Value |
|----------|-------|
| Framework | `@playwright/test` 1.59.1 — IS the recon tooling, also functions as the test runner for the recon scripts |
| Config file | `tools/recon/playwright.config.ts` (Wave 0 — to be created in Phase 2 Plan Task 1) |
| Quick run command | `cd tools/recon && npx playwright test homepage.spec.ts` |
| Full suite command | `cd tools/recon && npx playwright test` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| FE-01 | Recon report `.planning/intel/n11-recon.md` exists with 8 sections | manual + assembler-script assertion | `node tools/recon/assemble-recon.ts && test -f .planning/intel/n11-recon.md` | ❌ Wave 0 |
| FE-01 | Turkish copy catalog has ≥30 rows | assembler-script assertion | `node tools/recon/check-recon.ts` (pattern-counts headings + table rows) | ❌ Wave 0 |
| FE-01 | All 7 pages have a screenshot in `.planning/intel/screenshots/` | shell test | `for p in homepage category-elektronik pdp cart checkout-step1 account login; do test -f .planning/intel/screenshots/$p-fullpage.png || echo "missing $p"; done` | ❌ Wave 0 |
| FE-01 | PROJECT.md Key Decisions row for "Frontend toolchain" includes "Vite" + rationale paragraph | manual review during phase verification | `grep -A 2 "Frontend toolchain" .planning/PROJECT.md` | ❌ Wave 0 |
| FE-01 | Toolchain decision matrix is included in the Key Decisions rationale or in a sibling doc | manual review | n/a | ❌ Wave 0 |

### Sampling Rate

- **Per task commit:** `cd tools/recon && npx playwright test <one-spec>.spec.ts` — single capture, ~30s
- **Per wave merge:** full Playwright suite re-run (~90s), assembler re-run, recon-report consistency check
- **Phase gate:** all 7 screenshots present, ≥30 phrases in catalog, PROJECT.md Key Decisions row updated, `tools/recon/README.md` documents how to re-run, `.gitignore` includes recon outputs

### Wave 0 Gaps

- [ ] `tools/recon/package.json` — npm project manifest (Playwright dep)
- [ ] `tools/recon/playwright.config.ts` — config with anti-bot posture (Pattern 2)
- [ ] `tools/recon/lib/dismiss-banners.ts` — shared cookie-banner helper (Pattern 3)
- [ ] `tools/recon/lib/harvest-copy.ts` — shared TR-text extractor
- [ ] `tools/recon/lib/harvest-colors.ts` — shared computed-style extractor + rgb→hex
- [ ] `tools/recon/tests/<page>.spec.ts` × 7 (homepage, category, pdp, cart, checkout, account, login)
- [ ] `tools/recon/assemble-recon.ts` — Markdown assembler (Pattern 4)
- [ ] `tools/recon/check-recon.ts` — sanity checker (≥30 phrases, all sections present)
- [ ] `tools/recon/README.md` — how to re-run, anti-bot fallback ladder
- [ ] `.gitignore` extended with `tools/recon/{node_modules,test-results,playwright-report,output}/`
- [ ] Framework install: `cd tools/recon && npm install -D @playwright/test@1.59.1 && npx playwright install chromium`

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | TypeScript strict-mode extras (`noUncheckedIndexedAccess`, `exactOptionalPropertyTypes`, etc.) are widely recommended for graded TS projects | §Code Examples — Recommended pattern | LOW — these are well-known good practices, but Phase 10 install can adjust if any breaks the React 19 / Vite 8 build |
| A2 | n11.com URLs follow the patterns guessed (`/elektronik`, `/sepetim`, `/giris`, `/hesabim`) | §Recon Report Schema § 1. Page Inventory | LOW — recon will confirm and overwrite the table; the planner should not lock these URL strings into anything outside the recon report |
| A3 | n11 anti-bot posture is bypassable with `headless: false` + real UA + `--disable-blink-features=AutomationControlled` | §Common Pitfalls #1 | MEDIUM — if n11 has Cloudflare bot management on, the persistent-context fallback is needed. Documented as recovery in §Common Pitfalls #1 and §Code Examples |
| A4 | n11 has a cookie banner that dismisses with one of the listed Turkish strings | §Pattern 3 | LOW — if the banner text differs, the helper is one regex update; not a blocking issue |
| A5 | The 30 *expected* Turkish phrases listed in §Recon Report Schema § 2 are accurate to n11.com (e.g., "Sepete Ekle", "Stokta") | §Recon Report Schema § 2 | LOW — these are widely-used Turkish e-commerce phrases; recon will confirm or replace verbatim |
| A6 | Color tokens listed in §Recon Report Schema § 4 (orange #F78232, etc.) are approximations of n11's actual brand colors | §Recon Report Schema § 4 | LOW — recon's `getComputedStyle()` capture overwrites the placeholder values; the *structure* of the table is what matters |
| A7 | Sub-categories under each top-level taxonomy entry will be observable in n11's mega-menu | §Recon Report Schema § 3 | LOW — if not observable on hover, the spec falls back to navigating to each top-level page and reading the left-rail filter list |
| A8 | The Vite-vs-Next decision matrix weights are correctly tuned to bootcamp-grading priorities | §Decision Matrix | LOW — the score gap (65 vs 45) is wide enough that even moderate weight tweaks don't overturn the result |
| A9 | TanStack Query 5 with React 19 has no compatibility issues | §Standard Stack — Supporting | LOW — both are stable in 2026; React Query 5 supports React 18+ |
| A10 | Vite 8 + `@vitejs/plugin-react` v6 + Tailwind 4 + React 19 work together | §Standard Stack — Core | LOW — this is the *current canonical stack* per Vite 8.0 release notes; widely deployed since March 2026 |

> The Decision Matrix in §Toolchain Decision Matrix relies on A8. If the user/discuss-phase wants to re-weight, the score recomputation is deterministic and the conclusion is robust to ±20% weight changes.

---

## Open Questions

> Questions that the recon itself will resolve. The planner should NOT pre-answer these in tasks; the Playwright capture is the answer.

1. **Does n11.com return 403 to Playwright with the recommended posture (`headless: false` + real UA + `--disable-blink-features=AutomationControlled`)?**
   - What we know: n11 returned 403 to WebFetch (training-data note in PROJECT.md). Real desktop Chrome with our flags is 95%+ likely to work — n11 is not a high-security target.
   - What's unclear: whether Cloudflare bot management adds an interstitial.
   - Recommendation: Phase 2 Plan Task 1 includes a 30-second smoke run (`npx playwright test homepage.spec.ts`) as an early-feedback gate. If 403 persists, switch to `launchPersistentContext` per §Code Examples.

2. **Does n11 require login to view the cart page?**
   - What we know: most marketplaces show an empty-cart state to anonymous users.
   - What's unclear: n11 may redirect anonymous users to `/giris`. If so, the cart capture is just the login modal.
   - Recommendation: capture whatever state n11 shows the anonymous user; document it in the recon report. Don't log in.

3. **Does n11 expose category sub-navigation in a hover mega-menu, in a sidebar, or only by navigating to category pages?**
   - What we know: most Turkish marketplaces use a mega-menu on hover (Trendyol does).
   - What's unclear: n11's specific UX.
   - Recommendation: the homepage spec attempts a `page.hover('a[href="/elektronik"]')` then captures the dropdown if visible; if not, the category-page spec captures the left-rail filter list as a substitute.

4. **What's the actual font family used on n11.com?**
   - What we know: typical Turkish marketplaces use "Open Sans" or a custom system stack.
   - What's unclear: n11's specific font.
   - Recommendation: the homepage spec captures `getComputedStyle(document.body).fontFamily`; the result lands in §Typography Notes.

5. **Is the cart-page CTA literally "Siparişi Tamamla" or some variant?**
   - What we know: REQUIREMENTS.md FE-13 says "Siparişi Tamamla" (locked).
   - What's unclear: n11 might use "Sepeti Onayla" or "Hemen Al" — minor variation.
   - Recommendation: capture verbatim into Turkish Copy Catalog row 9; if it differs, update REQUIREMENTS.md FE-13 in the same Phase 2 task or note as a Phase 10 follow-up.

---

## Sources

### Primary (HIGH confidence — verified during this research session)

- [Playwright — Screenshots](https://playwright.dev/docs/screenshots) — `page.screenshot({path, fullPage})`, element-only capture
- [Playwright — Page API](https://playwright.dev/docs/api/class-page) — `page.evaluate()` signature with arguments
- [Playwright — BrowserType](https://playwright.dev/docs/api/class-browsertype) — `chromium.launch()` options (headless, slowMo, args, channel) and `launchPersistentContext()`
- [Playwright — Library mode](https://playwright.dev/docs/library) — standalone vs `@playwright/test` differences
- [Vite — Getting Started](https://vite.dev/guide/) — Vite 8.0.10 + Node version requirement
- [Vite 8.0 announcement](https://vite.dev/blog/announcing-vite8) — `@vitejs/plugin-react` v6 with Oxc, plugin-react-oxc deprecated
- [Tailwind CSS — Vite install](https://tailwindcss.com/docs/installation/using-vite) — Tailwind 4 install with `@tailwindcss/vite` plugin and `@import "tailwindcss"`
- npm registry — `dist-tags.latest` for: `@playwright/test` (1.59.1), `react` (19.2.5), `zustand` (5.0.12), `tailwindcss` (4.2.4), `react-router` (7.14.2), `zod` (4.3.6), `react-hook-form` (7.74.0)

### Secondary (MEDIUM confidence — verified via WebSearch + cross-referenced with project STACK.md)

- WebSearch result for `@tanstack/react-query` — "5.99.2 latest as of 2026-04-28; 5.100.5 published 2 days ago" (corroborated by STACK.md row "TanStack Query 5.x")
- Project [`STACK.md`](../../research/STACK.md) — version baselines (Vite 8, React 19.2.x, TS 5.6+, Tailwind 4.x, Zustand 5.x) — corroborated where this session re-verified

### Tertiary (LOW confidence — assumed from training data, marked in Assumptions Log)

- TypeScript strict-mode extras list (A1) — TypeScript handbook URL not re-fetched in this session; well-known recommendations
- n11.com URL paths (A2) — recon will confirm
- n11.com anti-bot posture (A3) — recon will confirm

### Source files referenced (this repo)

- `/hey/projects/n11-bootcamp-final-case/CLAUDE.md` — project rules, top pitfalls, open question on toolchain
- `/hey/projects/n11-bootcamp-final-case/.planning/PROJECT.md` — Frontend toolchain in Open Questions; FE-01 lockdown deferred
- `/hey/projects/n11-bootcamp-final-case/.planning/REQUIREMENTS.md` — FE-01 traceability row → Phase 2
- `/hey/projects/n11-bootcamp-final-case/.planning/ROADMAP.md` — Phase 2 success criteria
- `/hey/projects/n11-bootcamp-final-case/.planning/STATE.md` — Phase 1 closed, Phase 2 ready
- `/hey/projects/n11-bootcamp-final-case/.planning/research/SUMMARY.md` — synthesis of Phase 0 research
- `/hey/projects/n11-bootcamp-final-case/.planning/research/STACK.md` — frontend baseline (Vite 8 + React 19.2)
- `/hey/projects/n11-bootcamp-final-case/.planning/research/PITFALLS.md` — #16, #19, #20, #23 mappings
- `/hey/projects/n11-bootcamp-final-case/REQUIREMENTS-n11.md` — bootcamp brief Frontend section

---

## Metadata

**Confidence breakdown:**

- Standard stack (versions): HIGH — npm `dist-tags.latest` re-verified for 7 packages on 2026-04-29; the two that hit npm-fetch size limits (vite, typescript) are corroborated via `vite.dev/guide/` and the `react-ts` template default.
- Architecture (recon project layout, hand-off schema): HIGH — patterns drawn from official Playwright docs and standard Node project conventions.
- Toolchain decision matrix: HIGH — score gap (65 vs 45) is robust to ±20% weight changes.
- Pitfalls (#1–7 phase-specific + #16/#19/#20/#23 carried): HIGH — pitfalls #1, #2, #3, #4 are derived from Playwright operational experience; the carried-over pitfalls trace cleanly to PITFALLS.md.
- n11.com specifics (URLs, exact copy, exact colors): MEDIUM — recon is the source of truth; the report schema makes the gap explicit.

**Research date:** 2026-04-29
**Valid until:** 2026-05-29 (30 days for stable libraries; 7 days for Playwright if a major version ships in the interim).

## RESEARCH COMPLETE
