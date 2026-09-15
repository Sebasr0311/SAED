import { test, expect } from '@playwright/test';
import { loginAs, logout } from './helpers/auth.js';

test.describe('Fase 8 - Smoke E2E: Paquetería, PIN de 4 Dígitos y Entrega Segura', () => {

  test('Flujo E2E Completo 100%: Portería registra -> Notificación multi-residente con PIN -> Residente consulta PIN en buzón y campana -> Portero entrega con PIN -> Notificación de entrega', async ({ page }) => {
    // 1. Login como PORTERO
    await loginAs(page, 'PORTERO');
    await expect(page).toHaveURL(/portero-dashboard/);

    // 2. Navegar a la Consola de Paquetería
    await page.goto('/paquetes');
    await page.waitForLoadState('domcontentloaded');
    await expect(page.locator('h1').filter({ hasText: /Recepci[oó]n y Custodia de Paqueter[ií]a/i })).toBeVisible();

    // 3. Pestaña "Registrar Recepción"
    const tabRegistrar = page.getByRole('button', { name: /Registrar Recepci[oó]n/i });
    await tabRegistrar.click();

    // 4. Llenar formulario de recepción para Unidad 1 (Apto 101)
    const selectUnidad = page.locator('select').first();
    await selectUnidad.waitFor({ state: 'visible' });
    
    // Seleccionar Unidad 1 (Apto 101 de la Propiedad 1)
    const options = await selectUnidad.locator('option').all();
    let selectedValue = '1';
    for (const opt of options) {
      const val = await opt.getAttribute('value');
      if (val === '1') {
        selectedValue = '1';
        break;
      }
    }
    await selectUnidad.selectOption(selectedValue);

    // Llenar descripción y empresa
    const descInput = page.locator('textarea, input[placeholder*="descripci" i], input[name="descripcion"]').first();
    const testPackageDesc = `Smoke E2E PIN 100% - ${Date.now()}`;
    if (await descInput.isVisible()) {
      await descInput.fill(testPackageDesc);
    }

    // Botón de Registrar y Notificar
    const submitBtn = page.getByRole('button', { name: /Registrar y Notificar/i });
    await expect(submitBtn).toBeEnabled();
    await submitBtn.click();

    // 5. Verificar Modal de Éxito con PIN generado de 4 dígitos
    const pinCard = page.locator('text=Código de Retiro PIN').locator('..');
    await expect(pinCard).toBeVisible({ timeout: 15000 });

    const pinElement = pinCard.locator('.font-mono.tracking-widest').first();
    await expect(pinElement).toBeVisible();
    const generatedPin = (await pinElement.textContent())?.trim();
    console.log(`[E2E Smoke 100%] PIN de 4 dígitos generado: ${generatedPin}`);

    expect(generatedPin).toBeDefined();
    expect(generatedPin).toMatch(/^\d{4}$/); // Exactamente 4 dígitos numéricos

    // Continuar cerrando el modal de éxito
    const btnContinuar = page.getByRole('button', { name: /Continuar/i });
    await btnContinuar.click();
    await expect(pinCard).not.toBeVisible();

    // 6. Cerrar sesión de Portero para verificar notificación del RESIDENTE
    await logout(page);

    // 7. Login como RESIDENTE (camartinez - Unidad 1 / Apto 101)
    await loginAs(page, 'RESIDENTE');
    await expect(page).toHaveURL(/residente-dashboard/);

    // 8. Verificar Campana de Notificaciones (TopBar)
    const bellBtn = page.locator('#notification-bell-button');
    await expect(bellBtn).toBeVisible({ timeout: 10000 });
    await bellBtn.click();

    const notifPopover = page.locator('#notification-popover');
    await expect(notifPopover).toBeVisible({ timeout: 5000 });

    // Verificar que en la lista de notificaciones aparece el paquete y el PIN
    const notifItem = notifPopover.locator('button').filter({ hasText: testPackageDesc }).or(
      notifPopover.locator('button').filter({ hasText: generatedPin })
    ).first();
    await expect(notifItem).toBeVisible({ timeout: 8000 });
    await expect(notifItem.locator(`text=${generatedPin}`).first()).toBeVisible();
    console.log(`[E2E Smoke 100%] Verificado PIN ${generatedPin} en la campana de notificaciones del residente titular.`);

    // 9. Navegar al Buzón del Residente (/res-buzon)
    await page.goto('/res-buzon');
    await page.waitForLoadState('domcontentloaded');
    await expect(page.locator('h1').filter({ hasText: /Buz[oó]n/i })).toBeVisible({ timeout: 10000 });

    // Verificar que la tarjeta del mensaje muestra el paquete con su PIN destacado
    const cardPaquete = page.locator('.saed-card-interactive').filter({ hasText: testPackageDesc }).or(
      page.locator('.saed-card-interactive').filter({ hasText: generatedPin })
    ).first();
    await expect(cardPaquete).toBeVisible({ timeout: 10000 });
    await expect(cardPaquete.locator(`text=${generatedPin}`).first()).toBeVisible();

    // Abrir detalle del paquete en el modal
    await cardPaquete.click();
    const modalDetalle = page.locator('[role="dialog"]').filter({ hasText: /Detalle del Mensaje|Paquete/i });
    await expect(modalDetalle).toBeVisible({ timeout: 5000 });

    // Verificar tarjeta de PIN de seguridad dentro del modal
    await expect(modalDetalle.getByText(/Código de Retiro PIN/i).first()).toBeVisible();
    await expect(modalDetalle.locator(`text=${generatedPin}`).first()).toBeVisible();

    // Probar botón Copiar PIN
    const btnCopiar = modalDetalle.getByRole('button', { name: /Copiar/i });
    if (await btnCopiar.isVisible()) {
      await btnCopiar.click();
      await expect(page.getByText(/PIN copiado/i)).toBeVisible({ timeout: 5000 });
    }

    // Cerrar modal de detalle
    const btnCerrarModal = modalDetalle.getByRole('button', { name: /Cerrar/i }).first();
    await btnCerrarModal.click();
    await expect(modalDetalle).not.toBeVisible();

    // 10. Cerrar sesión de camartinez y verificar segundo habitante de la misma unidad (sofiamartinez)
    await logout(page);

    // Login directo como sofiamartinez (conviviente de la misma unidad Apto 101)
    await page.goto('/login');
    await page.waitForLoadState('domcontentloaded');
    const userInput = page.locator('input[type="text"], input[name="username"], input[name="email"]').first();
    const passInput = page.locator('input[type="password"]').first();
    await userInput.fill('sofiamartinez');
    await passInput.fill('Password123!');
    await page.getByRole('button', { name: /Iniciar Sesi[oó]n|Entrar/i }).click();

    // Si aparece selector de asignaciones, seleccionar la de Apto 101
    try {
      const modalSelect = page.locator('[role="dialog"]').filter({ hasText: /Seleccionar Asignaci[oó]n|Rol/i });
      if (await modalSelect.isVisible({ timeout: 3000 })) {
        await modalSelect.locator('button, tr, div').filter({ hasText: /Apto 101|Residente/i }).first().click();
      }
    } catch {
      // Ignorar si entra directo
    }

    await expect(page).toHaveURL(/residente-dashboard|res-buzon/, { timeout: 15000 });

    // Si no estamos en el buzón, navegar al buzón
    if (!page.url().includes('/res-buzon')) {
      await page.goto('/res-buzon');
      await page.waitForLoadState('domcontentloaded');
    }
    const cardSofia = page.locator('.saed-card-interactive').filter({ hasText: testPackageDesc }).or(
      page.locator('.saed-card-interactive').filter({ hasText: generatedPin })
    ).first();
    await expect(cardSofia).toBeVisible({ timeout: 10000 });
    await expect(cardSofia.locator(`text=${generatedPin}`).first()).toBeVisible();
    console.log(`[E2E Smoke 100%] Verificado PIN ${generatedPin} en el buzón del habitante conviviente de la misma unidad.`);

    // 11. Cerrar sesión de sofiamartinez
    await logout(page);

    // 12. Login nuevamente como PORTERO para efectuar la entrega con PIN
    await loginAs(page, 'PORTERO');
    await page.goto('/paquetes');
    await page.waitForLoadState('domcontentloaded');

    // Ir a "En Custodia"
    const tabCustodia = page.getByRole('button', { name: /En Custodia/i });
    await tabCustodia.click();
    await page.waitForTimeout(1000);

    // Localizar el paquete y abrir modal de Entrega
    const row = page.locator('tr').filter({ hasText: testPackageDesc }).or(
      page.locator('tr').filter({ hasText: 'Servientrega' })
    ).first();
    await expect(row).toBeVisible({ timeout: 10000 });

    const btnEntregar = row.getByRole('button', { name: /Entregar/i });
    await btnEntregar.click();

    // Probar PIN Inválido (Prueba Negativa)
    const pinInput = page.locator('input[placeholder*="PIN de 4 d" i]');
    await expect(pinInput).toBeVisible({ timeout: 5000 });
    await pinInput.fill('0000');
    const btnConfirmarEntrega = page.getByRole('button', { name: /Confirmar Entrega/i });
    await btnConfirmarEntrega.click();

    // Debe fallar con toast de error
    const toastError = page.locator('[data-sonner-toast][data-type="error"], .text-destructive').or(
      page.getByText(/PIN incorrecto|error/i)
    );
    await expect(toastError.first()).toBeVisible({ timeout: 8000 });

    // Probar PIN Válido (Prueba Positiva)
    await pinInput.fill(generatedPin);
    await btnConfirmarEntrega.click();

    // Verificar toast de éxito y cierre de modal
    const toastSuccess = page.getByText(/Paquete entregado con [eé]xito/i);
    await expect(toastSuccess).toBeVisible({ timeout: 10000 });
    await expect(pinInput).not.toBeVisible();

    // 13. Verificar en Pestaña "Historial Entregados"
    const tabHistorial = page.getByRole('button', { name: /Historial Entregados/i });
    await tabHistorial.click();
    await page.waitForTimeout(1000);

    const rowEntregado = page.locator('tr').filter({ hasText: testPackageDesc }).or(
      page.locator('tr').filter({ hasText: 'Servientrega' })
    ).first();
    await expect(rowEntregado).toBeVisible({ timeout: 10000 });
    await expect(rowEntregado.getByText(/Entregado/i)).toBeVisible();

    // 14. Cerrar sesión de Portero y verificar notificación de entrega en Residente
    await logout(page);

    await loginAs(page, 'RESIDENTE');
    await page.goto('/res-buzon');
    await page.waitForLoadState('domcontentloaded');

    // Verificar notificación de entrega recibida
    const notifEntrega = page.locator('.saed-card-interactive').filter({ hasText: /Paquete entregado/i }).first();
    await expect(notifEntrega).toBeVisible({ timeout: 10000 });

    console.log('[E2E Smoke 100%] Flujo 100% comprobado exitosamente: Notificación a todos los residentes del apartamento con PIN, consulta en campana y buzón, entrega segura con PIN en portería, y notificación final de entrega.');
  });

});
