import { test, expect } from '@playwright/test';

test.describe('Onboarding y Registro de Organización (SaaS Multi-tenant)', () => {

  test.beforeEach(async ({ page }) => {
    // Mock catálogo de planes para pruebas E2E consistentes
    await page.route('**/api/v1/auth/onboarding/planes', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          success: true,
          data: [
            {
              idPlan: 1,
              codigo: 'FREE',
              nombre: 'Prueba Gratuita',
              descripcion: 'Prueba la plataforma sin costo por 14 días.',
              precioMensual: 0,
              limitePropiedades: 1,
              limiteUnidades: 20,
              limiteUsuarios: 2,
            },
            {
              idPlan: 2,
              codigo: 'PRO',
              nombre: 'Profesional',
              descripcion: 'Ideal para copropiedades y edificios residenciales medianos.',
              precioMensual: 149000,
              limitePropiedades: 5,
              limiteUnidades: 150,
              limiteUsuarios: 10,
            },
            {
              idPlan: 3,
              codigo: 'ENTERPRISE',
              nombre: 'Corporativo',
              descripcion: 'Para empresas de administración inmobiliaria multisede.',
              precioMensual: 399000,
              limitePropiedades: 50,
              limiteUnidades: 1500,
              limiteUsuarios: 50,
            },
          ],
        }),
      });
    });

    // Mock endpoint de registro de onboarding
    await page.route('**/api/v1/auth/onboarding/registro', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          success: true,
          data: {
            idOrganizacion: 101,
            nombreOrganizacion: 'Organización Onboarding Test',
            idMembresia: 88,
            requierePago: true,
            referencia: 'SAED-TX-TEST-E2E',
            montoCentavos: 143040000,
            moneda: 'COP',
            wompiPublicKey: 'pub_test_Q5yDA9xoKdePzhSGeVe9HAez7HgGObFG',
            firmaIntegridad: 'e2e_mock_hash',
          },
        }),
      });
    });
  });

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
    await expect(btnContinuarPaso1).toBeEnabled({ timeout: 10000 });
    await btnContinuarPaso1.click();

    // 3. Paso 2: Datos de la Organización (Empresa)
    await expect(page.locator('text=Datos de la Empresa o Copropiedad')).toBeVisible({ timeout: 5000 });
    await page.locator('#nombreOrg').fill('Inmobiliaria San Pedro S.A.S.');
    await page.locator('#nit').fill('901888777-2');
    await page.locator('#emailContacto').fill('contacto@inmobiliariasanpedro.com');
    await page.locator('#telContacto').fill('6017894561');

    // Selección territorial
    await page.locator('#departamento').selectOption('Cundinamarca');
    await expect(page.locator('#ciudad')).toContainText('Chía');
    await page.locator('#ciudad').selectOption('Chía');
    await page.locator('#direccion').fill('Carrera 7 # 116-50 Of. 402');

    // Avanzar al Paso 3
    const btnContinuarPaso2 = page.getByRole('button', { name: /continuar a administrador/i });
    await btnContinuarPaso2.click();

    // 4. Paso 3: Datos del Administrador Principal (quien recibe las credenciales)
    await expect(page.locator('text=Administrador Principal de la Cuenta')).toBeVisible({ timeout: 5000 });
    await page.locator('#pNombre').fill('Sebastian');
    await page.locator('#pApellido').fill('Rincon');
    await page.locator('#tipoDoc').selectOption('CC');
    await page.locator('#numDoc').fill('1065888999');
    await page.locator('#adminUsername').fill('srincon.admin');
    await page.locator('#adminEmail').fill('juansebastianrincon+e2e@unicesar.edu.co');
    await page.locator('#adminTel').fill('3157891234');

    // Verificar resumen en vivo en el panel
    await expect(page.locator('text=Profesional').first()).toBeVisible();

    // Captura de pantalla de evidencia en el paso de Administrador
    await page.screenshot({ path: './docs/screenshots/onboarding_admin_step.png', fullPage: true });

    // Verificar botón de proceder al pago
    const btnConfirmarPago = page.getByRole('button', { name: /confirmar y proceder al pago wompi/i });
    await expect(btnConfirmarPago).toBeVisible();
    await expect(btnConfirmarPago).toBeEnabled();
  });

  test('Flujo de registro como Persona Natural (Dueño / Propietario) con cascada territorial y auto-transferencia', async ({ page }) => {
    // 1. Acceso a onboarding
    await page.goto('/registro-organizacion?plan=PRO&cycle=MENSUAL');
    await page.waitForLoadState('domcontentloaded');

    // Paso 1: Avanzar
    await expect(page.locator('text=Elige la escala para tu organización')).toBeVisible({ timeout: 15000 });
    const btnContinuarPaso1 = page.getByRole('button', { name: /continuar a datos de la organización/i });
    await expect(btnContinuarPaso1).toBeEnabled({ timeout: 10000 });
    await btnContinuarPaso1.click();

    // Paso 2: Cambiar a Persona Natural
    await expect(page.locator('text=Datos de la Empresa o Copropiedad')).toBeVisible({ timeout: 5000 });
    const btnPersonaNatural = page.getByRole('button', { name: /persona natural/i });
    await expect(btnPersonaNatural).toBeVisible();
    await btnPersonaNatural.click();

    // Llenar datos de propiedad y propietario
    await page.locator('#nombreEdificio').fill('Edificio Residencial Las Palmas');
    await page.locator('#pNombreDueno').fill('Carlos');
    await page.locator('#sNombreDueno').fill('Andres');
    await page.locator('#pApellidoDueno').fill('Mendoza');
    await page.locator('#sApellidoDueno').fill('Torres');

    // Tipo y número de documento
    await page.locator('#tipoDocDueno').selectOption('CC');
    await page.locator('#numDocDueno').fill('1098765432');

    // Contacto
    await page.locator('#emailDueno').fill('carlos.mendoza@propietarios.co');
    await page.locator('#telDueno').fill('3109876543');

    // Selección territorial en cascada: Antioquia -> Medellín
    await page.locator('#departamento').selectOption('Antioquia');
    await expect(page.locator('#ciudad')).toContainText('Medellín');
    await page.locator('#ciudad').selectOption('Medellín');

    // Dirección
    await page.locator('#direccion').fill('Carrera 43A # 1-50 El Poblado');

    // Verificar checkbox "Soy el administrador principal"
    const checkboxMismoAdmin = page.locator('#esMismoAdmin');
    await expect(checkboxMismoAdmin).toBeChecked();

    // Avanzar al Paso 3
    const btnContinuarPaso2 = page.getByRole('button', { name: /continuar a administrador/i });
    await btnContinuarPaso2.click();

    // Paso 3: Verificar auto-transferencia de datos
    await expect(page.locator('text=Administrador Principal de la Cuenta')).toBeVisible({ timeout: 5000 });
    await expect(page.locator('text=Datos personales transferidos de tu registro de propietario')).toBeVisible();

    await expect(page.locator('#pNombre')).toHaveValue('Carlos');
    await expect(page.locator('#pApellido')).toHaveValue('Mendoza');
    await expect(page.locator('#numDoc')).toHaveValue('1098765432');
    await expect(page.locator('#adminEmail')).toHaveValue('carlos.mendoza@propietarios.co');
    await expect(page.locator('#adminTel')).toHaveValue('3109876543');

    // Completar nombre de usuario único
    const adminUsernameInput = page.locator('#adminUsername');
    await adminUsernameInput.fill('cmendoza.admin');

    // Captura de pantalla de evidencia Persona Natural
    await page.screenshot({ path: './docs/screenshots/onboarding_persona_natural_admin.png', fullPage: true });

    // Botón de checkout habilitado
    const btnConfirmarPago = page.getByRole('button', { name: /confirmar y proceder al pago wompi/i });
    await expect(btnConfirmarPago).toBeVisible();
    await expect(btnConfirmarPago).toBeEnabled();
  });
});
