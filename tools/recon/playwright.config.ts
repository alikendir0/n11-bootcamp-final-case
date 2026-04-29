// Source: https://playwright.dev/docs/api/class-browsertype#browser-type-launch and
//         https://playwright.dev/docs/api/class-browsercontext (newContext options)
import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './tests',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: 'list',
  // Per-test timeout bumped to 120s. Combined with slowMo:250, the 2.5s
  // post-goto wait, and networkidle on a tracker-heavy page like n11.com,
  // the Playwright default of 30s is consistently too short. Pitfall #7
  // (gentle rate limit) intentionally trades speed for politeness; the
  // budget is per-spec, not per-test-runner default.
  timeout: 120_000,
  use: {
    baseURL: 'https://www.n11.com',
    headless: false,
    locale: 'tr-TR',
    timezoneId: 'Europe/Istanbul',
    userAgent:
      'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 ' +
      '(KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36',
    viewport: { width: 1440, height: 900 },
    launchOptions: {
      slowMo: 250,
      args: ['--disable-blink-features=AutomationControlled'],
    },
    navigationTimeout: 60_000,
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
  ],
});
