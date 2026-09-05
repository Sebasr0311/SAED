import { test, expect } from '@playwright/test';
import { loginAs } from './helpers/auth.js';

test.describe('05 - Gestión y Balance de Cartera', () => {

  test.beforeEach(async ({ page }) => {
    await loginAs(page, 'ADMIN_PROPIEDAD');
    await page.goto('/cartera');
    await page.waitForLoadState('networkidle');
  });

  test('05.1: Módulo de Cartera carga métricas consolidadas y pestañas', async ({ page }) => {
    await expect(page.locator('h1, h2, [role="heading"]').filter({ hasText: /Cartera/i })).toBeVisible();

    // Validar tarjetas financieras (KPIs)
    await expect(page.getByText(/Balance Total|Recaudo|Total Cartera|Cartera/i).first()).toBeVisible();

    // Validar pestañas de navegación
    await expect(page.getByRole('tab', { name: /Cartera por Unidad/i })).toBeVisible();
    await expect(page.getByRole('tab', { name: /Detalle de Cuotas/i })).toBeVisible();
    await expect(page.getByRole('tab', { name: /Antigüedad/i })).toBeVisible();
  });

  test('05.2: Navegación entre pestañas y consulta de cuotas', async ({ page }) => {
    // Cambiar a pestaña de cuotas
    const cuotasTab = page.getByRole('tab', { name: /Detalle de Cuotas/i });
    await cuotasTab.click();

    // Verificar que la tabla o datos de cuotas se renderizan
    const tableOEmpty = page.locator('table').or(page.getByText(/No hay cuotas|cuotas registradas/i)).first();
    await expect(tableOEmpty).toBeVisible({ timeout: 15000 });

    // Cambiar a pestaña de antigüedad
    const antiguedadTab = page.getByRole('tab', { name: /Antigüedad/i });
    await antiguedadTab.click();
    await expect(page.getByText(/días|antigüedad|periodo|tramo/i).first()).toBeVisible({ timeout: 15000 });
  });

  test('05.3: Acción de recalcular cartera operativa', async ({ page }) => {
    const recalcularBtn = page.getByRole('button', { name: /Recalcular Cartera|Recalcular/i });
    if (await recalcularBtn.isVisible()) {
      await recalcularBtn.click();
      // Validar feedback o toast de recálculo (permite absorción de latencia cloud ATP)
      const toast = page.locator('[data-sonner-toast], [role="status"], [role="alert"]').or(page.getByText(/recalculad/i)).first();
      await expect(toast).toBeVisible({ timeout: 25000 });
    }
  });

});
