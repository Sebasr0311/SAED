import { test, expect } from '@playwright/test';
import { loginAs } from './helpers/auth.js';

test.describe('02 - Control de Acceso Basado en Roles (RBAC)', () => {

  test('02.1: RESIDENTE no puede acceder a rutas administrativas ni de plataforma', async ({ page }) => {
    await loginAs(page, 'RESIDENTE');

    // Intentar acceder a superadmin
    await page.goto('/superadmin/dashboard');
    await page.waitForLoadState('networkidle');
    // En App.jsx, ProtectedRoute sin rol permitido no renderiza el componente protegido
    await expect(page.locator('text=Super Administrador')).toHaveCount(0);

    // Intentar acceder a personas (CRUD administrativo)
    await page.goto('/personas');
    await page.waitForLoadState('networkidle');
    await expect(page.locator('text=Gestión de Personas')).toHaveCount(0);

    // Intentar acceder a cartera general
    await page.goto('/cartera');
    await page.waitForLoadState('networkidle');
    await expect(page.locator('text=Balance General de Cartera')).toHaveCount(0);
  });

  test('02.2: PORTERO no puede acceder a administración ni configuración', async ({ page }) => {
    await loginAs(page, 'PORTERO');

    // Intentar acceder a personas
    await page.goto('/personas');
    await page.waitForLoadState('networkidle');
    await expect(page.locator('text=Gestión de Personas')).toHaveCount(0);

    // Intentar acceder a cartera
    await page.goto('/cartera');
    await page.waitForLoadState('networkidle');
    await expect(page.locator('text=Balance General de Cartera')).toHaveCount(0);

    // Intentar acceder a superadmin
    await page.goto('/superadmin/dashboard');
    await page.waitForLoadState('networkidle');
    await expect(page.locator('text=Super Administrador')).toHaveCount(0);
  });

  test('02.3: ADMIN_PROPIEDAD no puede acceder a la consola SUPERADMIN', async ({ page }) => {
    await loginAs(page, 'ADMIN_PROPIEDAD');

    await page.goto('/superadmin/dashboard');
    await page.waitForLoadState('networkidle');
    await expect(page.locator('text=Super Administrador')).toHaveCount(0);

    await page.goto('/superadmin/organizaciones');
    await page.waitForLoadState('networkidle');
    await expect(page.locator('text=Gestión Global de Organizaciones')).toHaveCount(0);
  });

  test('02.4: Rutas permitidas por rol cargan correctamente', async ({ page }) => {
    // Admin puede acceder a /residentes y /cartera
    await loginAs(page, 'ADMIN_PROPIEDAD');
    await page.goto('/residentes');
    await expect(page).toHaveURL(/residentes/);

    await page.goto('/cartera');
    await expect(page).toHaveURL(/cartera/);
  });

});
