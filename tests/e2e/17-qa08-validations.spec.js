import { test, expect } from '@playwright/test';
import { loginAs } from './helpers/auth.js';

test.describe('17 - QA-08: Auditoría y Validación Global de Formularios SAED 2.0', () => {

  test.beforeEach(async ({ page }) => {
    await loginAs(page, 'ADMIN_PROPIEDAD');
  });

  test('17.1: Formulario de Residentes - Validaciones estatutarias colombianas y estados visuales', async ({ page }) => {
    await page.goto('/residentes');
    await page.waitForLoadState('domcontentloaded');

    // Abrir modal de nuevo residente
    const nuevoBtn = page.getByRole('button', { name: /Nuevo Residente|Registrar Residente/i }).first();
    await nuevoBtn.click();

    const modal = page.locator('[role="dialog"]').filter({ hasText: /Registrar Residente|Nuevo Residente/i }).first();
    await expect(modal).toBeVisible();

    // 1. Probar campo Celular: rechaza letras y longitud incorrecta
    const telInput = modal.locator('#res-telefono, input[name="telefono"]').first();
    await telInput.fill('300123'); // 6 dígitos
    await telInput.blur();

    // Debe mostrar error de 10 dígitos y tener aria-invalid="true"
    await expect(telInput).toHaveAttribute('aria-invalid', 'true');
    await expect(modal.getByText(/10 dígitos/i).first()).toBeVisible();

    // Intentar escribir letras en celular
    await telInput.fill('300abc4567');
    await telInput.blur();
    await expect(telInput).toHaveAttribute('aria-invalid', 'true');

    // Corregir celular a 10 dígitos válidos
    await telInput.fill('3001234567');
    await telInput.blur();
    await expect(telInput).toHaveAttribute('aria-invalid', 'false');

    // 2. Probar Nombres con números: rechaza números
    const nomInput = modal.locator('#res-nombres, input[name="primerNombre"]').first();
    await nomInput.fill('Carlos123');
    await nomInput.blur();
    await expect(nomInput).toHaveAttribute('aria-invalid', 'true');
    await expect(modal.getByText(/letras y espacios/i).first()).toBeVisible();

    // Corregir a nombre válido
    await nomInput.fill('Carlos Alberto');
    await nomInput.blur();
    await expect(nomInput).toHaveAttribute('aria-invalid', 'false');

    // 3. Probar Email inválido
    const emailInput = modal.locator('#res-email, input[name="email"]').first();
    await emailInput.fill('carlos@');
    await emailInput.blur();
    await expect(emailInput).toHaveAttribute('aria-invalid', 'true');
    await expect(modal.getByText(/correo electrónico válido/i).first()).toBeVisible();

    // Corregir email
    await emailInput.fill('carlos.alberto@correo.com');
    await emailInput.blur();
    await expect(emailInput).toHaveAttribute('aria-invalid', 'false');

    // Cerrar modal
    const closeBtn = modal.getByRole('button', { name: /Cancelar|Cerrar/i }).first();
    await closeBtn.click();
  });

  test('17.2: Formulario de Visitas - Validaciones de visitante y placa vehicular', async ({ page }) => {
    await page.goto('/visitas');
    await page.waitForLoadState('domcontentloaded');

    const nuevaVisitaBtn = page.getByRole('button', { name: /Nueva Visita|Registrar Visita/i }).first();
    await nuevaVisitaBtn.click();

    const modal = page.locator('[role="dialog"]').filter({ hasText: /Registrar Visita|Nueva Visita/i }).first();
    await expect(modal).toBeVisible();

    // Validar placa de vehículo
    const tipoVehSelect = modal.locator('#vis-tipo-vehiculo, select[name="tipoVehiculo"]').first();
    if (await tipoVehSelect.isVisible()) {
      await tipoVehSelect.selectOption('VEHICULO');

      const placaInput = modal.locator('#vis-placa, input[name="placa"]').first();
      await expect(placaInput).toBeVisible();

      // Placa inválida (4 números para carro)
      await placaInput.fill('DEM1234');
      await placaInput.blur();
      await expect(placaInput).toHaveAttribute('aria-invalid', 'true');
      await expect(modal.getByText(/Formato de placa de carro/i).first()).toBeVisible();

      // Placa válida
      await placaInput.fill('DEM-123');
      await placaInput.blur();
      await expect(placaInput).toHaveAttribute('aria-invalid', 'false');
    }

    const cancelBtn = modal.getByRole('button', { name: /Cancelar|Cerrar/i }).first();
    await cancelBtn.click();
  });

});
