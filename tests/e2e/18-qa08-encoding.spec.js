import { test, expect } from '@playwright/test';
import { loginAs } from './helpers/auth.js';

// Patrones de mojibake y caracteres corruptos en DOM:
// \uFFFD (carácter de reemplazo ), Ã, Â, â, ð
const MOJIBAKE_REGEX = /[\uFFFD]|Ã[\x80-\xFF\w\s]|Â[\x80-\xFF\w\s]|â[\x80-\xFF]{1,2}|ð[\x80-\xFF]{1,3}/;

const VIEWPORTS = [
  { width: 1440, height: 900, name: 'Desktop Grande' },
  { width: 1280, height: 800, name: 'Desktop Estándar' },
  { width: 1024, height: 768, name: 'Tablet Horizontal' },
  { width: 768, height: 1024, name: 'Tablet Vertical' },
  { width: 390, height: 844, name: 'Mobile iPhone' },
  { width: 360, height: 740, name: 'Mobile Android' },
];

async function assertNoMojibakeInDOM(page, contextName) {
  // Extraer el texto completo renderizado en el body
  const bodyText = await page.evaluate(() => document.body.innerText || '');
  
  // Buscar coincidencia
  const match = bodyText.match(MOJIBAKE_REGEX);
  if (match) {
    throw new Error(`Mojibake detectado en ${contextName}: "${match[0]}"`);
  }
  
  // Verificar explícitamente ausencia de carácter de reemplazo
  expect(bodyText).not.toContain('\uFFFD');
  expect(bodyText).not.toContain('CÃ©dula');
  expect(bodyText).not.toContain('TelÃ©fono');
  expect(bodyText).not.toContain('InformaciÃ³n');
  expect(bodyText).not.toContain('AdministraciÃ³n');
  expect(bodyText).not.toContain('NiÃ±o');
}

test.describe('18 - QA-08.2: Auditoría Global de Encoding y Mojibake en DOM', () => {

  test('18.1: Landing y Login libres de mojibake en todas las resoluciones responsivas', async ({ page }) => {
    for (const vp of VIEWPORTS) {
      await page.setViewportSize({ width: vp.width, height: vp.height });
      
      // 1. Landing
      await page.goto('/');
      await page.waitForLoadState('domcontentloaded');
      await assertNoMojibakeInDOM(page, `Landing (${vp.name} - ${vp.width}x${vp.height})`);

      // 2. Login
      await page.goto('/login');
      await page.waitForLoadState('domcontentloaded');
      await assertNoMojibakeInDOM(page, `Login (${vp.name} - ${vp.width}x${vp.height})`);
    }
  });

  test('18.2: Páginas clave autenticadas de Administración y Residente sin mojibake en DOM', async ({ page }) => {
    await loginAs(page, 'ADMIN_PROPIEDAD');

    const adminRoutes = [
      { path: '/dashboard', name: 'Dashboard' },
      { path: '/residentes', name: 'Residentes' },
      { path: '/unidades', name: 'Unidades' },
      { path: '/visitas', name: 'Visitas' },
      { path: '/cartera', name: 'Cartera' },
      { path: '/pagos', name: 'Pagos' },
      { path: '/porterias', name: 'Porterías' },
      { path: '/paquetes', name: 'Paquetería' },
      { path: '/parqueaderos', name: 'Parqueaderos' },
      { path: '/quejas', name: 'PQRS Admin' },
      { path: '/avisos', name: 'Comunicados' },
      { path: '/usuarios', name: 'Usuarios' },
      { path: '/contratos', name: 'Contratos' },
    ];

    for (const route of adminRoutes) {
      await page.goto(route.path);
      await page.waitForLoadState('domcontentloaded');
      await assertNoMojibakeInDOM(page, route.name);
    }
  });

  test('18.3: Formulario de Residentes: Verificación de tipos de documento y acentuación en español', async ({ page }) => {
    await loginAs(page, 'ADMIN_PROPIEDAD');
    await page.goto('/residentes');
    await page.waitForLoadState('domcontentloaded');

    // Abrir modal si está disponible
    const nuevoBtn = page.getByRole('button', { name: /Nuevo Residente|Registrar Residente/i }).first();
    if (await nuevoBtn.isVisible()) {
      await nuevoBtn.click();
      const modal = page.locator('[role="dialog"]').first();
      await expect(modal).toBeVisible();

      // Verificar que el DOM del modal no tiene mojibake
      await assertNoMojibakeInDOM(page, 'Modal Residentes');

      // Verificar que Cédula de Ciudadanía y Teléfono aparecen correctamente
      const modalText = await modal.innerText();
      expect(modalText).toMatch(/Cédula|Documento|Identidad/i);
      expect(modalText).toMatch(/Teléfono|Celular/i);
      expect(modalText).not.toContain('CÃ©dula');
      expect(modalText).not.toContain('TelÃ©fono');
    }
  });

  test('18.4: Portal Residente: Visitas, Buzón y Cuotas sin mojibake', async ({ page }) => {
    await loginAs(page, 'RESIDENTE');

    const residentRoutes = [
      { path: '/residente-dashboard', name: 'Dashboard Residente' },
      { path: '/residente/visita', name: 'Residente Visitas' },
      { path: '/residente/buzon', name: 'Residente Buzón' },
      { path: '/residente/cuotas', name: 'Residente Cuotas' },
      { path: '/residente/quejas', name: 'Residente PQRS' },
    ];

    for (const route of residentRoutes) {
      await page.goto(route.path);
      await page.waitForLoadState('domcontentloaded');
      await assertNoMojibakeInDOM(page, route.name);
    }
  });

});
