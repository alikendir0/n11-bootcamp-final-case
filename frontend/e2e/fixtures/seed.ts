/**
 * Test-only credentials used by the Playwright demo-flow spec.
 * Sourced from env vars — do NOT commit a real password here.
 *
 * Required env vars when running e2e against a live dev stack:
 *   PW_TEST_EMAIL    — a registered seed user, default 'demo@n11test.local'
 *   PW_TEST_PASSWORD — that user's password, default 'TestPass123!'
 *   PW_TEST_QUERY    — product search term with at least one result, default 'macbook'
 */
export const SEED = {
  email: process.env.PW_TEST_EMAIL ?? 'demo@n11test.local',
  password: process.env.PW_TEST_PASSWORD ?? 'TestPass123!',
  query: process.env.PW_TEST_QUERY ?? 'macbook',
};
