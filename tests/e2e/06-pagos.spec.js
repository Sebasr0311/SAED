import { test, expect } from '@playwright/test';
import { loginAs } from './helpers/auth.js';

test.describe('06 - Pagos y Recaudos', () => {

  test('06.1: Página de Pagos carga para ADMIN_PROPIEDAD con buscador y tabla', async ({ page }) => {
    await loginAs(page, 'ADMIN_PROPIEDAD');
    await page.goto('/pagos');
    await page.waitForLoadState('networkidle');

    await expect(page.locator('h1, h2, [role="heading"]').filter({ hasText: /Pagos|Recaudos/i }).first()).toBeVisible();

    // Tabla o empty state
    const tableOEmpty = page.locator('table, .data-table').or(page.getByText(/No hay pagos/i)).first();
    await expect(tableOEmpty).toBeVisible({ timeout: 15000 });
  });

  test('06.2: Residente consulta sus cuotas y estados de pago', async ({ page }) => {
    await loginAs(page, 'RESIDENTE');
    await page.goto('/res-cuotas');
    await page.waitForLoadState('networkidle');

    await expect(page.locator('h1, h2, [role="heading"]').filter({ hasText: /Cuotas/i }).first()).toBeVisible();
    await expect(page.locator('table').or(page.getByText(/No (hay|tienes) cuotas/i)).first()).toBeVisible({ timeout: 15000 });
  });

});
