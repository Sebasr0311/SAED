import { test, expect } from '@playwright/test';
import { USERS, loginAs } from './helpers/auth.js';
import path from 'path';

const ARTIFACT_DIR = 'C:/Users/JUAN/.gemini/antigravity-cli/brain/e5fcc17b-38b0-48c5-ac29-6a1f549d4b5a';

test.describe('Verificación de Destinatarios (Titular y Conviviente) en Paquetes', () => {

  test('Portero selecciona apartamento y se listan Titular y Convivientes en Destinatario', async ({ page }) => {
    await loginAs(page, 'PORTERO');
    await page.goto('/paquetes');
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(2000);

    // Abrir modal de recepción de encomienda
    const btnNuevo = page.getByRole('button', { name: /Registrar Encomienda|Nuevo Paquete|Recepci[oó]n/i }).first();
    await expect(btnNuevo).toBeVisible();
    await btnNuevo.click();

    // Esperar a que el modal aparezca
    await expect(page.getByText(/Recepción de Encomienda|Datos de la Encomienda/i).first()).toBeVisible();

    // Seleccionar la unidad (Apto 101)
    const selectUnidad = page.locator('select').first();
    await selectUnidad.waitFor({ state: 'visible' });

    // Encontrar la opción del Apto 101 o valor '1'
    await selectUnidad.selectOption({ index: 1 });
    await page.waitForTimeout(1500);

    // Verificar el select de destinatario
    const selectDestinatario = page.locator('select').nth(1);
    await expect(selectDestinatario).toBeVisible();

    // Obtener los textos de las opciones
    const optionsText = await selectDestinatario.locator('option').allInnerTexts();
    console.log('Opciones de destinatarios encontradas:', optionsText);

    // Debe contener tanto a Carlos Martinez como a Sofia Martinez
    const hasTitular = optionsText.some(t => t.includes('Carlos') && (t.includes('Martinez') || t.includes('Titular')));
    const hasConviviente = optionsText.some(t => t.includes('Sofia') || t.includes('Conviviente'));

    expect(hasTitular).toBeTruthy();
    expect(hasConviviente).toBeTruthy();

    // Tomar captura de pantalla como evidencia
    await page.screenshot({ path: path.join(ARTIFACT_DIR, 'portero_paquetes_destinatarios_titular_conviviente.png'), fullPage: true });
  });

});
