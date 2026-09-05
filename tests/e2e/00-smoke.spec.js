import { test, expect } from '@playwright/test';

test('Smoke test: landing page y login cargan correctamente', async ({ page }) => {
  await page.goto('/');
  await expect(page).toHaveTitle(/SAED/i);

  // Navegar a login
  const loginBtn = page.getByRole('button', { name: /iniciar sesi[oó]n|acceder|ingresar/i }).or(page.getByRole('link', { name: /iniciar sesi[oó]n|acceder|ingresar/i }));
  if (await loginBtn.count() > 0) {
    await loginBtn.first().click();
    await expect(page).toHaveURL(/login/);
  } else {
    await page.goto('/login');
  }

  // Verificar elementos de login
  await expect(page.locator('input[type="text"], input[name="username"], input[id="username"]').first()).toBeVisible();
  await expect(page.locator('input[type="password"]').first()).toBeVisible();
});
