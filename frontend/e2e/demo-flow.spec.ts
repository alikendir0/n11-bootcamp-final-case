import { expect, test } from '@playwright/test';
import { SEED } from './fixtures/seed';

test('demo flow: search → PDP → add → login → checkout → Iyzico hand-off', async ({ page }) => {
  await page.goto('/');
  await expect(page).toHaveTitle(/n11/i);

  const searchInput = page.getByPlaceholder('Aradığınız ürün, kategori veya markayı yazınız');
  await searchInput.fill(SEED.query);
  await searchInput.press('Enter');
  await expect(page).toHaveURL(new RegExp(`/arama\\?q=${encodeURIComponent(SEED.query)}`));

  const firstResult = page.locator('main a[href^="/urun/"]').first();
  await expect(firstResult, 'backend not seeded: search returned no visible product result').toBeVisible({ timeout: 15_000 });
  await firstResult.click();
  await expect(page).toHaveURL(/\/urun\//);

  await page.getByRole('button', { name: 'Sepete Ekle' }).click();
  await expect(page).toHaveURL(/\/giris-yap\?redirectUrl=/);

  await page.getByLabel('E-posta').fill(SEED.email);
  await page.getByLabel('Şifre').fill(SEED.password);
  await page.getByRole('button', { name: 'Giriş Yap' }).click();

  await expect(page).toHaveURL(/\/urun\//, { timeout: 10_000 });
  await page.getByRole('button', { name: 'Sepete Ekle' }).click();
  await expect(page.getByText('Ürün sepete eklendi.')).toBeVisible({ timeout: 8_000 });
  await expect(page.getByLabel(/Sepetim/)).toBeVisible();

  await page.getByRole('link', { name: /Sepetim/ }).first().click();
  await expect(page).toHaveURL(/\/sepetim$/);

  await page.getByRole('button', { name: 'Siparişi Tamamla' }).click();
  await expect(page).toHaveURL(/\/odeme\/adres/);

  const saveAddressButton = page.getByRole('button', { name: 'Adresi Kaydet' });
  if (await saveAddressButton.isVisible({ timeout: 1_000 }).catch(() => false)) {
    await page.getByLabel('Adres Başlığı').fill('Ev');
    await page.getByLabel('Ad Soyad').fill('Demo Kullanıcı');
    await page.getByLabel('Telefon').fill('05551234567');
    await page.getByLabel('İl').fill('İstanbul');
    await page.getByLabel('İlçe').fill('Beşiktaş');
    await page.getByLabel('Mahalle').fill('Levent');
    await page.getByLabel('Sokak / Cadde / No / Daire').fill('Büyükdere Cd. No 1');
    await page.getByLabel('Posta Kodu').fill('34330');
    await saveAddressButton.click();
  }

  await page.getByRole('button', { name: 'Devam Et' }).click();
  await expect(page).toHaveURL(/\/odeme\/odeme/);

  await page.getByRole('button', { name: 'Sipariş Ver' }).click();
  await page.waitForURL(/iyzipay|odeme\/sonuc/, { timeout: 15_000 });
});
