import { test, expect } from '@playwright/test';
import { USERS, loginAs, logout } from './helpers/auth.js';
import path from 'path';

const ARTIFACT_DIR = 'C:/Users/JUAN/.gemini/antigravity-cli/brain/e5fcc17b-38b0-48c5-ac29-6a1f549d4b5a';

test.describe('Verificación Exhaustiva Web de Todos los Perfiles de Usuario (Playwright)', () => {

  test('Perfil 1: SUPERADMIN (admin_global) inicia sesión y accede a su dashboard', async ({ page }) => {
    await loginAs(page, 'SUPERADMIN');
    await expect(page).toHaveURL(/superadmin\/dashboard/);
    await page.waitForTimeout(2500);
    await page.screenshot({ path: path.join(ARTIFACT_DIR, 'web_login_01_superadmin.png'), fullPage: true });
    await expect(page.getByText(/Super Administrador|Métricas|Organizaciones|Plataforma/i).first()).toBeVisible();
    await logout(page);
  });

  test('Perfil 2: ADMIN_ORGANIZACION (admin_org) inicia sesión y accede a su dashboard', async ({ page }) => {
    await loginAs(page, 'ADMIN_ORGANIZACION');
    await expect(page).toHaveURL(/org\/dashboard/);
    await page.waitForTimeout(2500);
    await page.screenshot({ path: path.join(ARTIFACT_DIR, 'web_login_02_admin_organizacion.png'), fullPage: true });
    await expect(page.getByText(/Organización|Propiedades|Inmobiliaria|Panel/i).first()).toBeVisible();
    await logout(page);
  });

  test('Perfil 3: ADMIN_PROPIEDAD (admin) inicia sesión y accede a su dashboard', async ({ page }) => {
    await loginAs(page, 'ADMIN_PROPIEDAD');
    await expect(page).toHaveURL(/dashboard/);
    await page.waitForTimeout(2500);
    await page.screenshot({ path: path.join(ARTIFACT_DIR, 'web_login_03_admin_propiedad.png'), fullPage: true });
    await expect(page.getByText(/Dashboard|Propiedad|Cartera|Residentes/i).first()).toBeVisible();
    await logout(page);
  });

  test('Perfil 4: PORTERO (portero01) inicia sesión y accede a su panel de garita', async ({ page }) => {
    await loginAs(page, 'PORTERO');
    await expect(page).toHaveURL(/portero-dashboard/);
    await page.waitForTimeout(2500);
    await page.screenshot({ path: path.join(ARTIFACT_DIR, 'web_login_04_portero.png'), fullPage: true });
    await expect(page.getByText(/Portería|Control de Acceso|Vigilancia|Visitas/i).first()).toBeVisible();
    await logout(page);
  });

  test('Perfil 5: RESIDENTE TITULAR (camartinez) inicia sesión y accede a su portal', async ({ page }) => {
    await loginAs(page, 'RESIDENTE');
    await expect(page).toHaveURL(/residente-dashboard/);
    await page.waitForTimeout(2500);
    await page.screenshot({ path: path.join(ARTIFACT_DIR, 'web_login_05_residente_titular.png'), fullPage: true });
    await expect(page.getByText(/Mi Apartamento|Mis Cuotas|Residente|Visitas/i).first()).toBeVisible();
    await logout(page);
  });

  test('Perfil 6: RESIDENTE CONVIVIENTE (sofiamartinez) inicia sesión y accede a su portal', async ({ page }) => {
    await loginAs(page, 'RESIDENTE_CONVIVENCIA');
    await expect(page).toHaveURL(/residente-dashboard/);
    await page.waitForTimeout(2500);
    await page.screenshot({ path: path.join(ARTIFACT_DIR, 'web_login_06_residente_conviviente.png'), fullPage: true });
    await expect(page.getByText(/Mi Apartamento|Residente|Visitas|Conviviente/i).first()).toBeVisible();
    await logout(page);
  });

  test('Perfil 7: RESIDENTE ADICIONAL (anagomez - Apto 102) inicia sesión y accede a su portal', async ({ page }) => {
    await loginAs(page, 'RESIDENTE_ANAGOMEZ');
    await expect(page).toHaveURL(/residente-dashboard/);
    await page.waitForTimeout(2500);
    await page.screenshot({ path: path.join(ARTIFACT_DIR, 'web_login_07_residente_anagomez.png'), fullPage: true });
    await expect(page.getByText(/Mi Apartamento|Mis Cuotas|Residente/i).first()).toBeVisible();
    await logout(page);
  });

});
