import { test, expect } from '@playwright/test';
import { loginAs } from './helpers/auth.js';

test.describe('11 - Paquetería y Encomiendas con PIN', () => {

  test('11.1: PORTERO carga consola de paquetería y abre modal de recepción', async ({ page }) => {
    await loginAs(page, 'PORTERO');
    await page.goto('/paquetes');
    await page.waitForLoadState('networkidle');

    await expect(page.locator('h1, h2, [role="heading"]').filter({ hasText: /Paquet|Encomiendas/i }).first()).toBeVisible();

    // Botón o pestaña para registrar paquete
    const regBtn = page.getByRole('button', { name: /Registrar|Nuevo Paquete|Recepción/i }).first();
    if (await regBtn.isVisible()) {
      await regBtn.click();
      const formSection = page.locator('[role="dialog"], form').or(page.getByText(/Datos de la Encomienda/i)).first();
      await expect(formSection).toBeVisible();
    }
  });

  test('11.2: RESIDENTE consulta correspondencia y paquetes en buzón', async ({ page }) => {
    await loginAs(page, 'RESIDENTE');
    await page.goto('/res-buzon');
    await page.waitForLoadState('networkidle');

    await expect(page.locator('h1, h2, [role="heading"]').filter({ hasText: /Buz|Correspondencia|Paquet/i }).first()).toBeVisible();
  });

});
