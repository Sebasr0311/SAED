import { test, expect } from '@playwright/test';
import { loginAs, logout } from './helpers/auth.js';
import path from 'path';

const ARTIFACT_DIR = 'C:/Users/JUAN/.gemini/antigravity-cli/brain/e5fcc17b-38b0-48c5-ac29-6a1f549d4b5a';

test('Verificar que Carlos Martinez ve a Sofia Martinez en Mi Perfil / Convivientes', async ({ page }) => {
  await loginAs(page, 'RESIDENTE');
  await expect(page).toHaveURL(/residente-dashboard/);

  // Navegar a /res-convivientes
  await page.goto('/res-convivientes');
  await page.waitForLoadState('domcontentloaded');

  // Esperar a que cargue Sofia Martinez
  const sofiaText = page.getByText(/Sofia Martinez/i).first();
  await sofiaText.waitFor({ state: 'visible', timeout: 15000 });
  await expect(sofiaText).toBeVisible();

  const docText = page.getByText(/CC6000/i).first();
  await expect(docText).toBeVisible();

  await page.screenshot({
    path: path.join(ARTIFACT_DIR, 'carlos_martinez_convivientes_sofia.png'),
    fullPage: true,
  });

  // Navegar también a /res-perfil
  await page.goto('/res-perfil');
  await page.waitForLoadState('domcontentloaded');
  const aptoTab = page.getByRole('tab', { name: /Mi Apartamento/i });
  if (await aptoTab.isVisible()) {
    await aptoTab.click();
    await page.waitForTimeout(1000);
  }
  await page.screenshot({
    path: path.join(ARTIFACT_DIR, 'carlos_martinez_perfil_apartamento.png'),
    fullPage: true,
  });

  await logout(page);
});
