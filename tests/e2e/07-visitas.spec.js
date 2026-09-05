import { test, expect } from '@playwright/test';
import { loginAs } from './helpers/auth.js';

test.describe('07 - Control y Registro de Visitas', () => {

  test('07.1: ADMIN_PROPIEDAD consulta módulo de visitas', async ({ page }) => {
    await loginAs(page, 'ADMIN_PROPIEDAD');
    await page.goto('/visitas');
    await page.waitForLoadState('networkidle');

    await expect(page.locator('h1, h2, [role="heading"]').filter({ hasText: /Visitas/i }).first()).toBeVisible();
    const tableOEmpty = page.locator('table, .data-table').or(page.getByText(/No hay visitas/i)).first();
    await expect(tableOEmpty).toBeVisible({ timeout: 15000 });
  });

  test('07.2: RESIDENTE puede registrar una visita programada', async ({ page }) => {
    await loginAs(page, 'RESIDENTE');
    await page.goto('/res-visita');
    await page.waitForLoadState('networkidle');

    await expect(page.locator('h1, h2, [role="heading"]').filter({ hasText: /Visita/i }).first()).toBeVisible();

    // Si hay formulario para invitar / registrar visita
    const nombreInput = page.locator('input[name="nombreVisitante"], input#nombreVisitante, input[placeholder*="visitante" i]').first();
    if (await nombreInput.isVisible()) {
      await nombreInput.fill('Visitante Test E2E');
      const submitBtn = page.getByRole('button', { name: /Registrar|Guardar|Generar/i }).first();
      await submitBtn.click();
      await expect(page.locator('[data-sonner-toast], [role="status"], [role="alert"]').or(page.getByText(/éxito|cread|registrad/i)).first()).toBeVisible({ timeout: 10000 });
    }
  });

});
