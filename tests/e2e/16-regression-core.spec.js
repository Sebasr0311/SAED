import { test, expect } from '@playwright/test';
import { loginAs } from './helpers/auth.js';

test.describe('16 - Regresión Core MVP y Responsividad', () => {

  test('16.1: Interfaz móvil (390x844) operativa sin desbordamientos horizontales', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await loginAs(page, 'ADMIN_PROPIEDAD');
    await page.goto('/dashboard');
    await page.waitForLoadState('networkidle');

    // Comprobar ausencia de scroll horizontal involuntario
    const hasHorizontalOverflow = await page.evaluate(() => {
      return document.documentElement.scrollWidth > document.documentElement.clientWidth;
    });
    expect(hasHorizontalOverflow).toBeFalsy();

    // Botón de menú móvil visible y desplegable
    const menuBtn = page.getByRole('button', { name: 'Abrir menú' });
    await expect(menuBtn).toBeVisible();
  });

  test('16.2: Interfaz móvil ultra-compacta (360x740) utilizable', async ({ page }) => {
    await page.setViewportSize({ width: 360, height: 740 });
    await loginAs(page, 'RESIDENTE');
    await page.goto('/residente-dashboard');
    await page.waitForLoadState('networkidle');

    const hasHorizontalOverflow = await page.evaluate(() => {
      return document.documentElement.scrollWidth > document.documentElement.clientWidth;
    });
    expect(hasHorizontalOverflow).toBeFalsy();
  });

  test('16.3: Detección y ausencia de errores JS críticos no controlados en consola', async ({ page }) => {
    const pageErrors = [];
    page.on('pageerror', (err) => pageErrors.push(err.message));

    await loginAs(page, 'ADMIN_PROPIEDAD');
    await page.goto('/residentes');
    await page.waitForLoadState('networkidle');

    await page.goto('/cartera');
    await page.waitForLoadState('networkidle');

    await page.goto('/dashboard');
    await page.waitForLoadState('networkidle');

    // No debe haber unhandled exceptions en ninguna de las páginas clave
    expect(pageErrors).toEqual([]);
  });

});
