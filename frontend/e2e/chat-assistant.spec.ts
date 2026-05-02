import { expect, test } from '@playwright/test';

test.describe('Chat Assistant route persistence', () => {
  const routes = ['/', '/arama', '/sepetim'];

  for (const route of routes) {
    test(`bubble is visible on ${route}`, async ({ page }) => {
      await page.goto(route);
      const bubble = page.getByRole('button', {
        name: 'Yapay Zeka Alışveriş Asistanını Aç',
      });
      await expect(bubble).toBeVisible();
    });
  }

  test('bubble is visible on homepage', async ({ page }) => {
    await page.goto('/');
    const bubble = page.getByRole('button', {
      name: 'Yapay Zeka Alışveriş Asistanını Aç',
    });
    await expect(bubble).toBeVisible();
  });

  test('drawer opens and title is visible', async ({ page }) => {
    await page.goto('/');
    const bubble = page.getByRole('button', {
      name: 'Yapay Zeka Alışveriş Asistanını Aç',
    });
    await bubble.click();
    await expect(
      page.getByRole('dialog', { name: 'Yapay Zeka Alışveriş Asistanı' })
    ).toBeVisible();
    await expect(page.getByText('Yapay Zeka Alışveriş Asistanı').first()).toBeVisible();
  });

  test('chat assistant persists across route navigation', async ({ page }) => {
    await page.goto('/');
    const bubble = page.getByRole('button', {
      name: 'Yapay Zeka Alışveriş Asistanını Aç',
    });
    await bubble.click();
    await expect(
      page.getByRole('dialog', { name: 'Yapay Zeka Alışveriş Asistanı' })
    ).toBeVisible();

    // Close drawer before navigating (scrim intercepts main-content clicks)
    await page.getByRole('button', { name: 'Asistanı Kapat' }).click();
    await expect(
      page.getByRole('dialog', { name: 'Yapay Zeka Alışveriş Asistanı' })
    ).toBeHidden();

    // Navigate to another route
    await page.goto('/arama');
    await expect(bubble).toBeVisible();

    // Reopen drawer — component persists because Layout never unmounts
    await bubble.click();
    await expect(
      page.getByRole('dialog', { name: 'Yapay Zeka Alışveriş Asistanı' })
    ).toBeVisible();
    await expect(page.getByText('Yapay Zeka Alışveriş Asistanı').first()).toBeVisible();
  });

  test('chat assistant is present on /sepetim and can open drawer', async ({ page }) => {
    await page.goto('/sepetim');
    const bubble = page.getByRole('button', {
      name: 'Yapay Zeka Alışveriş Asistanını Aç',
    });
    await expect(bubble).toBeVisible();
    await bubble.click();
    await expect(
      page.getByRole('dialog', { name: 'Yapay Zeka Alışveriş Asistanı' })
    ).toBeVisible();
    await expect(page.getByText('Yapay Zeka Alışveriş Asistanı').first()).toBeVisible();
  });
});
