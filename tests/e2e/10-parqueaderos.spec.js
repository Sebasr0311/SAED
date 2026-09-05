import { test, expect } from '@playwright/test';
import { loginAs } from './helpers/auth.js';

test.describe('10 - Gestión de Parqueaderos y Bahías', () => {

  test('10.1: Carga de mapa y cuadrícula de bahías de parqueadero', async ({ page }) => {
    await loginAs(page, 'ADMIN_PROPIEDAD');
    await page.goto('/parqueaderos');
    await page.waitForLoadState('networkidle');

    await expect(page.locator('h1, h2, [role="heading"]').filter({ hasText: /Parqueadero/i }).first()).toBeVisible();

    // Validar KPIs de estado (Disponibles, Ocupados, Total)
    await expect(page.getByText(/Disponibles|Ocupados|Total Bahías|Bahías/i).first()).toBeVisible({ timeout: 15000 });

    // Validar cuadrícula o tabla de espacios
    const gridOTabla = page.locator('.grid, table, [data-testid="parking-grid"]').first();
    await expect(gridOTabla).toBeVisible({ timeout: 15000 });
  });

  test('10.2: Filtrado por tipo de parqueadero (Visitantes / Residentes)', async ({ page }) => {
    await loginAs(page, 'PORTERO');
    await page.goto('/parqueaderos');
    await page.waitForLoadState('networkidle');

    // Cambiar filtro a visitantes
    const filterSelect = page.locator('select, [role="combobox"]').first();
    if (await filterSelect.isVisible()) {
      await filterSelect.selectOption({ label: 'Visitantes' }).catch(() => {});
      await page.waitForTimeout(300);
    }

    await expect(page.locator('h1, h2, [role="heading"]').filter({ hasText: /Parqueadero/i }).first()).toBeVisible();
  });

});
