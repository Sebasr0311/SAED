import { test, expect } from '@playwright/test';

test.describe('Onboarding y Registro de Organización (SaaS Multi-tenant)', () => {
  test('Flujo completo de registro: selección de plan, datos de empresa, datos de admin y checkout', async ({ page }) => {
    // 1. Acceso a la página de registro
    await page.goto('/registro-organizacion?plan=PRO&cycle=ANUAL');
    await page.waitForLoadState('domcontentloaded');

    // Verificar que el Wizard cargue el paso 1
    await expect(page.locator('text=Elige la escala para tu organización')).toBeVisible({ timeout: 15000 });
    await expect(page.locator('text=-20% DCTO')).toBeVisible();

    // 2. Verificar cambio de ciclo de facturación
    const btnMensual = page.getByRole('button', { name: /facturación mensual/i });
    if (await btnMensual.isVisible()) {
      await btnMensual.click();
    }
    const btnAnual = page.getByRole('button', { name: /facturación anual/i });
    if (await btnAnual.isVisible()) {
      await btnAnual.click();
    }

    // Avanzar al Paso 2
    const btnContinuarPaso1 = page.getByRole('button', { name: /continuar a datos de la organización/i });
    await expect(btnContinuarPaso1).toBeEnabled();
    await btnContinuarPaso1.click();

    // 3. Paso 2: Datos de la Organización
    await expect(page.locator('text=Datos de la Empresa o Copropiedad')).toBeVisible({ timeout: 5000 });
    await page.locator('input[placeholder*="ej. Inversiones Inmobiliarias"]').fill('Inmobiliaria San Pedro S.A.S.');
    await page.locator('input[placeholder*="ej. 901234567-8"]').fill('901.888.777-2');
    await page.locator('input[placeholder*="ej. Calle 100"]').fill('Carrera 7 # 116-50 Of. 402');
    await page.locator('input[placeholder*="admin@tuempresa.com"]').fill('contacto@inmobiliariasanpedro.com');
    await page.locator('input[placeholder*="ej. 6013004000"]').fill('6017894561');

    // Avanzar al Paso 3
    const btnContinuarPaso2 = page.getByRole('button', { name: /continuar a administrador/i });
    await btnContinuarPaso2.click();

    // 4. Paso 3: Datos del Administrador Principal (quien recibe las credenciales)
    await expect(page.locator('text=Administrador Principal de la Cuenta')).toBeVisible({ timeout: 5000 });
    await page.locator('input[placeholder*="ej. Carlos"]').fill('Sebastian');
    await page.locator('input[placeholder*="ej. Rodríguez"]').fill('Rincon');
    await page.locator('input[placeholder*="ej. 1020304050"]').fill('1065888999');
    await page.locator('input[placeholder*="carlos@tuempresa.com"]').fill('juansebastianrincon+e2e@unicesar.edu.co');
    await page.locator('input[placeholder*="ej. 3101234567"]').fill('3157891234');

    // Verificar resumen en vivo en el panel lateral/inferior
    await expect(page.locator('text=Profesional')).toBeVisible();

    // Captura de pantalla de evidencia en el paso de Administrador
    await page.screenshot({ path: './docs/screenshots/onboarding_admin_step.png', fullPage: true });

    // Verificar botón de proceder al pago
    const btnConfirmarPago = page.getByRole('button', { name: /confirmar y proceder al pago wompi/i });
    await expect(btnConfirmarPago).toBeVisible();
    await expect(btnConfirmarPago).toBeEnabled();
  });
});
