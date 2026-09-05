import { test, expect } from '@playwright/test';
import { loginAs } from './helpers/auth.js';

test.describe('14 - Gestión de Usuarios y Roles', () => {

  test('14.1: ADMIN_PROPIEDAD consulta lista de usuarios del sistema', async ({ page }) => {
    await loginAs(page, 'ADMIN_PROPIEDAD');
    await page.goto('/usuarios');
    await page.waitForLoadState('networkidle');

    await expect(page.locator('h1, h2, [role="heading"]').filter({ hasText: /Usuarios/i }).first()).toBeVisible();
    await expect(page.locator('table').or(page.getByText(/No hay usuarios/i)).first()).toBeVisible({ timeout: 15000 });
  });

  test('14.2: Gestión de Roles y Asignaciones protegida', async ({ page }) => {
    await loginAs(page, 'ADMIN_PROPIEDAD');
    await page.goto('/roles-asignaciones');
    await page.waitForLoadState('networkidle');

    await expect(page.locator('h1, h2, [role="heading"]').filter({ hasText: /Asignaciones|Roles/i }).first()).toBeVisible();
  });

});
