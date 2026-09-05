import { test, expect } from '@playwright/test';
import { loginAs } from './helpers/auth.js';

test.describe('12 - Centro de Notificaciones y Comunicaciones', () => {

  test('12.1: Campana de notificaciones abre popover flotante con tabs Todas y No leídas', async ({ page }) => {
    await loginAs(page, 'ADMIN_PROPIEDAD');
    await page.goto('/dashboard');
    await page.waitForLoadState('networkidle');

    // Localizar botón de campana
    const bellBtn = page.locator('#notification-bell-button');
    await expect(bellBtn).toBeVisible();

    // Click para abrir popover
    await bellBtn.click();

    // Verificar popover flotante
    const popover = page.locator('#notification-popover');
    await expect(popover).toBeVisible();
    await expect(popover.locator('#notification-title')).toHaveText('Notificaciones');

    // Verificar tabs de filtro
    await expect(popover.getByRole('tab', { name: /Todas/i })).toBeVisible();
    await expect(popover.getByRole('tab', { name: /No leídas/i })).toBeVisible();

    // Presionar Escape para cerrar
    await page.keyboard.press('Escape');
    await expect(popover).not.toBeVisible();
  });

  test('12.2: RESIDENTE accede al buzón de comunicados de la comunidad', async ({ page }) => {
    await loginAs(page, 'RESIDENTE');
    await page.goto('/res-buzon');
    await page.waitForLoadState('networkidle');

    await expect(page.locator('h1, h2, [role="heading"]').filter({ hasText: /Buzón|Avisos/i }).first()).toBeVisible();
  });

});
