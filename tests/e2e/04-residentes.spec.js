import { test, expect } from '@playwright/test';
import { loginAs } from './helpers/auth.js';

test.describe('04 - Gestión de Residentes (CRUD Completo)', () => {

  test.beforeEach(async ({ page }) => {
    await loginAs(page, 'ADMIN_PROPIEDAD');
    await page.goto('/residentes');
    await page.waitForLoadState('networkidle');
  });

  test('04.1: Listado y KPIs de residentes cargan con datos reales', async ({ page }) => {
    await expect(page.locator('h1, h2, [role="heading"]').filter({ hasText: /Residentes/i })).toBeVisible();

    // Validar KPIs de censo
    await expect(page.locator('text=Total Residentes')).toBeVisible();

    // Tabla de residentes o empty state presente
    const tablaOEmpty = page.locator('table').or(page.getByText('No hay residentes registrados')).first();
    await expect(tablaOEmpty).toBeVisible({ timeout: 15000 });
  });

  test('04.2: Filtro y búsqueda en tiempo real', async ({ page }) => {
    const searchInput = page.locator('#search-residentes');
    await expect(searchInput).toBeVisible();

    // Buscar "Carlos" o "Martinez"
    await searchInput.fill('Carlos');
    await page.waitForTimeout(500);

    // Debe mostrar resultados o indicar sin resultados
    const content = page.locator('table tbody tr').or(page.getByText('Sin resultados encontrados')).first();
    await expect(content).toBeVisible();

    // Limpiar búsqueda
    await searchInput.fill('');
    await page.waitForTimeout(300);
  });

  test('04.3: Validación de campos obligatorios en formulario', async ({ page }) => {
    // Abrir modal de nuevo residente
    const nuevoBtn = page.getByRole('button', { name: /Nuevo Residente|Registrar Residente/i }).first();
    await nuevoBtn.click();

    const modal = page.locator('[role="dialog"], .modal-box, .fixed').filter({ hasText: /Registrar Residente|Nuevo Residente/i }).first();
    await expect(modal).toBeVisible();

    // Intentar guardar sin datos
    const submitBtn = modal.getByRole('button', { name: /Guardar|Registrar/i }).first();
    await submitBtn.click();

    // Verificar que los mensajes de validación aparecen y el modal sigue abierto
    await expect(modal).toBeVisible();
    await expect(modal.getByText(/obligatorio|ingrese|requerido|seleccione/i).first()).toBeVisible();

    // Cerrar modal
    const closeBtn = modal.getByRole('button', { name: /Cancelar|Cerrar/i }).or(modal.locator('button:has(.lucide-x)')).first();
    await closeBtn.click();
  });

  test('04.4: Registro exitoso de nuevo habitante con persistencia', async ({ page }) => {
    const nuevoBtn = page.getByRole('button', { name: /Nuevo Residente|Registrar Residente/i }).first();
    await nuevoBtn.click();

    const modal = page.locator('[role="dialog"], .modal-box, .fixed').filter({ hasText: /Registrar Residente|Nuevo Residente/i }).first();
    await expect(modal).toBeVisible();

    const docNum = '99' + Math.floor(1000000 + Math.random() * 9000000);
    const nombre = 'TestQA';
    const apellido = 'Automated';

    // Seleccionar tipo de documento CC (valor 1)
    const selectTipoDoc = modal.locator('select#idTipoDoc');
    if (await selectTipoDoc.isVisible()) {
      await selectTipoDoc.selectOption('1');
    }

    // Rellenar campos
    await modal.locator('input#numeroDocumento').first().fill(docNum);
    await modal.locator('input#nombres').first().fill(nombre);
    await modal.locator('input#apellidos').first().fill(apellido);
    await modal.locator('input#fechaNacimiento').first().fill('1990-05-15');
    await modal.locator('input#telefono').first().fill('3109876543');
    await modal.locator('input#email').first().fill(`testqa.${docNum}@saed.com`);

    // Seleccionar unidad si existe select
    const selectUnidad = modal.locator('select#idApartamento');
    if (await selectUnidad.isVisible()) {
      const options = await selectUnidad.locator('option').all();
      if (options.length > 1) {
        await selectUnidad.selectOption({ index: 1 });
      }
    }

    // Guardar
    const saveBtn = modal.getByRole('button', { name: /Guardar Residente|Guardar|Registrar/i }).first();
    await saveBtn.click();

    // Validar cierre de modal tras guardado exitoso
    await expect(modal).not.toBeVisible({ timeout: 10000 });

    // Verificar que aparece en la búsqueda y persiste
    const searchInput = page.locator('#search-residentes');
    await searchInput.fill(docNum);
    await page.waitForTimeout(500);

    await expect(page.locator(`text=${docNum}`).first()).toBeVisible();

    // Recargar página para verificar persistencia real en Oracle DB
    await page.reload();
    await page.waitForLoadState('networkidle');
    const searchAfterReload = page.locator('#search-residentes');
    await searchAfterReload.fill(docNum);
    await page.waitForTimeout(500);
    await expect(page.locator(`text=${docNum}`).first()).toBeVisible();
  });

});
