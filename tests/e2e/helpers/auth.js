/**
 * Helper de autenticación y navegación para los tests E2E de SAED 2.0
 */

export const USERS = {
  SUPERADMIN: {
    username: 'admin_global',
    password: process.env.E2E_PASSWORD || 'admin123',
    homeUrl: '/superadmin/dashboard',
  },
  ADMIN_ORGANIZACION: {
    username: 'admin_org',
    password: process.env.E2E_PASSWORD || 'admin123',
    homeUrl: '/org/dashboard',
  },
  ADMIN_PROPIEDAD: {
    username: 'admin',
    password: process.env.E2E_PASSWORD || 'admin123',
    homeUrl: '/dashboard',
  },
  PORTERO: {
    username: 'portero01',
    password: process.env.E2E_PASSWORD || 'admin123',
    homeUrl: '/portero-dashboard',
  },
  RESIDENTE: {
    username: 'camartinez',
    password: process.env.E2E_PASSWORD || 'admin123',
    homeUrl: '/residente-dashboard',
  },
  RESIDENTE_CONVIVENCIA: {
    username: 'sofiamartinez',
    password: process.env.E2E_PASSWORD || 'admin123',
    homeUrl: '/residente-dashboard',
  },
  RESIDENTE_ANAGOMEZ: {
    username: 'anagomez',
    password: process.env.E2E_PASSWORD || 'admin123',
    homeUrl: '/residente-dashboard',
  },
};

export async function loginAs(page, userRole) {
  const credentials = USERS[userRole];
  if (!credentials) throw new Error(`Rol desconocido: ${userRole}`);

  await page.goto('/login');
  await page.evaluate(() => {
    try {
      localStorage.clear();
      sessionStorage.clear();
    } catch (_) {}
  });
  await page.goto('/login');
  await page.waitForLoadState('domcontentloaded');

  // Rellenar formulario de login
  const userInput = page.locator('input[type="text"], input[name="username"], input[id="username"]').first();
  await userInput.waitFor({ state: 'visible', timeout: 15000 });
  const passInput = page.locator('input[type="password"]').first();
  const submitBtn = page.locator('button[type="submit"]').first();

  await userInput.fill(credentials.username);
  await passInput.fill(credentials.password);
  await submitBtn.click();

  // Esperar navegación fuera de /login (absorbe cold start de Render si ocurre)
  await page.waitForURL((url) => !url.pathname.includes('/login'), { timeout: 35000 });
  await page.waitForLoadState('domcontentloaded');
}

export async function logout(page) {
  const logoutBtn = page.locator('button[aria-label="Cerrar sesión"], button:has-text("Cerrar sesión")').first();
  if (await logoutBtn.isVisible()) {
    await logoutBtn.click();
    const confirmBtn = page.getByRole('button', { name: /S[ií], cerrar sesi[oó]n|Confirmar/i });
    try {
      if (await confirmBtn.isVisible({ timeout: 2000 })) {
        await confirmBtn.click();
      }
    } catch (_) {}
  }
  
  if (!page.url().includes('/login')) {
    await page.evaluate(() => {
      sessionStorage.clear();
      localStorage.clear();
      window.location.href = '/login';
    });
  }
  await page.waitForURL(/\/login/, { timeout: 10000 });
}
