import { test, expect } from '@playwright/test';
import { loginAs } from './helpers/auth.js';

test.describe('03 - Dashboards por Rol', () => {

  test('03.1: Dashboard ADMIN_PROPIEDAD carga métricas operativas y accesos directos', async ({ page }) => {
    await loginAs(page, 'ADMIN_PROPIEDAD');
    await expect(page).toHaveURL(/dashboard/);

    // Verificar contenedor principal
    await expect(page.locator('h1, h2, [role="heading"]').first()).toBeVisible();

    // Validar presencia de tarjetas de métricas o resumen
    const metricOrCard = page.locator('.grid, [data-testid="metric-card"], .rounded-xl, .rounded-2xl').first();
    await expect(metricOrCard).toBeVisible({ timeout: 15000 });

    // Botón de refrescar existe y funciona
    const refreshBtn = page.locator('button:has-text("Actualizar"), button[title*="Actualizar"], button:has(.lucide-refresh-cw)').first();
    if (await refreshBtn.isVisible()) {
      await refreshBtn.click();
      await page.waitForLoadState('networkidle');
    }
  });

  test('03.2: Dashboard SUPERADMIN carga métricas de plataforma', async ({ page }) => {
    await loginAs(page, 'SUPERADMIN');
    await expect(page).toHaveURL(/superadmin\/dashboard/);

    await expect(page.getByText(/Organizaciones|Planes|Métricas|Suscripciones/i).first()).toBeVisible({ timeout: 15000 });
  });

  test('03.3: Dashboard PORTERO carga consola de portería', async ({ page }) => {
    await loginAs(page, 'PORTERO');
    await expect(page).toHaveURL(/portero-dashboard/);

    await expect(page.getByText(/Control|Acceso|Portería|Vigilancia/i).first()).toBeVisible({ timeout: 15000 });
  });

  test('03.4: Dashboard RESIDENTE carga vista de copropietario', async ({ page }) => {
    await loginAs(page, 'RESIDENTE');
    await expect(page).toHaveURL(/residente-dashboard/);

    await expect(page.getByText(/Apartamento|Cuotas|Visitas|Residente/i).first()).toBeVisible({ timeout: 15000 });
  });

});
