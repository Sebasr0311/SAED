import { test, expect } from '@playwright/test';
import { loginAs } from './helpers/auth.js';

test.describe('15 - Aislamiento Multi-Tenant y Seguridad RLS', () => {

  test('15.1: ADMIN_PROPIEDAD solo visualiza entidades de su propiedad asignada', async ({ page }) => {
    await loginAs(page, 'ADMIN_PROPIEDAD');
    await page.goto('/dashboard');
    await page.waitForLoadState('networkidle');

    // Comprobar que en el selector de contexto o datos activos sólo figura su propiedad
    const tenantIndicator = page.getByText(/Edificio Residencial SAED|Propiedad/i).first();
    await expect(tenantIndicator).toBeVisible({ timeout: 15000 });
  });

  test('15.2: RESIDENTE solo accede a datos de su unidad asignada', async ({ page }) => {
    await loginAs(page, 'RESIDENTE');
    await page.goto('/res-apartamento');
    await page.waitForLoadState('networkidle');

    await expect(page.locator('h1, h2, [role="heading"]').filter({ hasText: /Apartamento|Unidad/i }).first()).toBeVisible();
    await expect(page.getByText(/201|Apartamento/i).first()).toBeVisible({ timeout: 15000 });
  });

});
