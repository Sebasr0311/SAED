import { test, expect } from '@playwright/test';
import { loginAs } from './helpers/auth.js';

test.describe('08 - Sistema de Códigos QR de Acceso', () => {

  test('08.1: Módulo de Escáner QR carga correctamente en consola de portería', async ({ page }) => {
    await loginAs(page, 'PORTERO');
    await page.goto('/escanner-qr');
    await page.waitForLoadState('networkidle');

    await expect(page.locator('h1, h2, [role="heading"]').filter({ hasText: /Escáner|QR|Acceso/i }).first()).toBeVisible();

    // Validar presencia de campo para ingreso manual de token QR
    const inputToken = page.locator('input[placeholder*="token" i], input[placeholder*="código" i], input[type="text"]').first();
    await expect(inputToken).toBeVisible({ timeout: 15000 });
  });

  test('08.2: Rechazo semántico de código QR inválido o inexistente', async ({ page }) => {
    await loginAs(page, 'PORTERO');
    await page.goto('/escanner-qr');
    await page.waitForLoadState('networkidle');

    const inputToken = page.locator('input[placeholder*="token" i], input[placeholder*="código" i], input[type="text"]').first();
    await inputToken.fill('QR_TOTALMENTE_INVALIDO_XYZ_000');

    const validarBtn = page.getByRole('button', { name: /Validar|Verificar|Buscar/i }).first();
    await validarBtn.click();

    // Debe mostrar mensaje de error o toast indicando token no encontrado/inválido
    const errorAlert = page.locator('[data-sonner-toast], [role="status"], [role="alert"]').or(page.getByText(/inválido|no encontrado|expirado|error/i)).first();
    await expect(errorAlert).toBeVisible({ timeout: 10000 });
  });

});
