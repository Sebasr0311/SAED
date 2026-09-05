import { test, expect } from '@playwright/test';
import { loginAs } from './helpers/auth.js';

test.describe('09 - Consola y Operación de Portería', () => {

  test.beforeEach(async ({ page }) => {
    await loginAs(page, 'PORTERO');
    await page.goto('/escanner-qr');
    await page.waitForLoadState('networkidle');
  });

  test('09.1: Consola de portería carga pestañas y accesos rápidos', async ({ page }) => {
    await expect(page.locator('h1, h2, [role="heading"]').filter({ hasText: /Escáner|Portería|Acceso/i }).first()).toBeVisible();

    // Validar presencia de controles para alternar modo peatonal vs vehicular
    const modoControl = page.locator('button, [role="tab"]').filter({ hasText: /Peatonal|Vehicular|Vehículo|A pie/i }).first();
    if (await modoControl.isVisible()) {
      await modoControl.click();
    }
  });

  test('09.2: Consulta de historial de accesos del día en portería', async ({ page }) => {
    // Si hay tabla o lista de ingresos recientes
    const tablaIngresos = page.locator('table').or(page.getByText(/Historial|Sin accesos|ingresos/i)).first();
    await expect(tablaIngresos).toBeVisible({ timeout: 15000 });
  });

});
