// Source: https://playwright.dev/docs/locators
import { Page } from '@playwright/test';

export async function dismissBanners(page: Page): Promise<void> {
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
  const closeBtn = page.getByRole('button', { name: /Kapat|Daha Sonra|×/i });
  if (await closeBtn.count() > 0 && await closeBtn.first().isVisible().catch(() => false)) {
    await closeBtn.first().click().catch(() => {});
  }
}
