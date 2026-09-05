import { test, expect } from '@playwright/test';
import { loginAs } from './helpers/auth.js';

test.describe('13 - PQRS y Convivencia', () => {

  test('13.1: ADMIN_PROPIEDAD consulta bandeja de PQRS y quejas', async ({ page }) => {
    await loginAs(page, 'ADMIN_PROPIEDAD');
    await page.goto('/quejas-admin');
    await page.waitForLoadState('networkidle');

    await expect(page.locator('h1, h2, [role="heading"]').filter({ hasText: /Quejas|PQRS|Peticiones/i }).first()).toBeVisible();
  });

  test('13.2: RESIDENTE accede al formulario de radicación de PQRS', async ({ page }) => {
    await loginAs(page, 'RESIDENTE');
    await page.goto('/res-quejas');
    await page.waitForLoadState('networkidle');

    await expect(page.locator('h1, h2, [role="heading"]').filter({ hasText: /Quejas|PQRS|Solicitudes/i }).first()).toBeVisible();
  });

});
