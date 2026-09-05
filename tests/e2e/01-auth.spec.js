import { test, expect } from '@playwright/test';
import { USERS, loginAs, logout } from './helpers/auth.js';

test.describe('01 - Autenticación y Ciclo de Sesión', () => {

  test('01.1: Login exitoso SUPERADMIN redirige a /superadmin/dashboard', async ({ page }) => {
    await loginAs(page, 'SUPERADMIN');
    await expect(page).toHaveURL(/superadmin\/dashboard/);
    await expect(page.getByText(/SAED|Super Administrador|Métricas/i).first()).toBeVisible();
  });

  test('01.2: Login exitoso ADMIN_PROPIEDAD redirige a /dashboard', async ({ page }) => {
    await loginAs(page, 'ADMIN_PROPIEDAD');
    await expect(page).toHaveURL(/dashboard/);
    await expect(page.getByText(/Dashboard|Propiedad|Cartera/i).first()).toBeVisible();
  });

  test('01.3: Login exitoso PORTERO redirige a /portero-dashboard', async ({ page }) => {
    await loginAs(page, 'PORTERO');
    await expect(page).toHaveURL(/portero-dashboard/);
    await expect(page.getByText(/Portería|Control de Acceso|Vigilancia/i).first()).toBeVisible();
  });

  test('01.4: Login exitoso RESIDENTE redirige a /residente-dashboard', async ({ page }) => {
    await loginAs(page, 'RESIDENTE');
    await expect(page).toHaveURL(/residente-dashboard/);
    await expect(page.getByText(/Mi Apartamento|Mis Cuotas|Residente/i).first()).toBeVisible();
  });

  test('01.5: Error al ingresar credenciales incorrectas', async ({ page }) => {
    await page.goto('/login');
    const userInput = page.locator('input[type="text"], input[name="username"]').first();
    const passInput = page.locator('input[type="password"]').first();
    const submitBtn = page.locator('button[type="submit"]').first();

    await userInput.fill('admin');
    await passInput.fill('contrasena_totalmente_invalida_999');
    await submitBtn.click();

    // Debe mostrar toast o alerta de error
    const errorAlert = page.locator('.sonner-toast, [role="alert"], :text("inválid"), :text("incorrect")').first();
    await expect(errorAlert).toBeVisible({ timeout: 10000 });
    await expect(page).toHaveURL(/login/);
  });

  test('01.6: Error con usuario inexistente', async ({ page }) => {
    await page.goto('/login');
    const userInput = page.locator('input[type="text"], input[name="username"]').first();
    const passInput = page.locator('input[type="password"]').first();
    const submitBtn = page.locator('button[type="submit"]').first();

    await userInput.fill('usuario_inexistente_xyz_123');
    await passInput.fill('password123');
    await submitBtn.click();

    const errorAlert = page.locator('.sonner-toast, [role="alert"], :text("inválid"), :text("incorrect")').first();
    await expect(errorAlert).toBeVisible({ timeout: 10000 });
    await expect(page).toHaveURL(/login/);
  });

  test('01.7: Validación con campos vacíos', async ({ page }) => {
    await page.goto('/login');
    const submitBtn = page.locator('button[type="submit"]').first();
    await submitBtn.click();

    // Sigue en login y el formulario previene o muestra validación
    await expect(page).toHaveURL(/login/);
  });

  test('01.8: Acceso a ruta protegida sin sesión redirige a /login', async ({ page }) => {
    await page.goto('/dashboard');
    await expect(page).toHaveURL(/login/);

    await page.goto('/superadmin/dashboard');
    await expect(page).toHaveURL(/login/);

    await page.goto('/residentes');
    await expect(page).toHaveURL(/login/);
  });

  test('01.9: Sesión se preserva después de refresh (F5)', async ({ page }) => {
    await loginAs(page, 'ADMIN_PROPIEDAD');
    await expect(page).toHaveURL(/dashboard/);

    await page.reload();
    await page.waitForLoadState('networkidle');

    // Sigue autenticado y no redirige a login
    await expect(page).toHaveURL(/dashboard/);
    await expect(page.locator('text=admin').first()).toBeVisible();
  });

  test('01.10: Logout limpia sesión y redirige a /login', async ({ page }) => {
    await loginAs(page, 'ADMIN_PROPIEDAD');
    await logout(page);
    await expect(page).toHaveURL(/login/);

    // Intentar volver a dashboard debe rebotar a login
    await page.goto('/dashboard');
    await expect(page).toHaveURL(/login/);
  });

});
