/**
 * Helper de autenticación y navegación para los tests E2E de SAED 2.0
 */

export const USERS = {
  SUPERADMIN: {
    username: 'admin_global',
    password: 'admin_global123',
    homeUrl: '/superadmin/dashboard',
  },
  ADMIN_PROPIEDAD: {
    username: 'admin',
    password: 'admin123',
    homeUrl: '/dashboard',
  },
  PORTERO: {
    username: 'portero01',
    password: 'admin123',
    homeUrl: '/portero-dashboard',
  },
  RESIDENTE: {
    username: 'camartinez',
    password: 'admin123',
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
  await page.waitForLoadState('networkidle');

  // Rellenar formulario de login
  const userInput = page.locator('input[type="text"], input[name="username"], input[id="username"]').first();
  const passInput = page.locator('input[type="password"]').first();
  const submitBtn = page.locator('button[type="submit"]').first();

  await userInput.fill(credentials.username);
  await passInput.fill(credentials.password);
  await submitBtn.click();

  // Esperar navegación fuera de /login (absorbe cold start de Render si ocurre)
  await page.waitForURL((url) => !url.pathname.includes('/login'), { timeout: 35000 });
  await page.waitForLoadState('networkidle');
}

export async function logout(page) {
  // Intentar botón de logout directo o mediante menú
  const logoutBtn = page.locator('button[aria-label="Cerrar sesión"], button:has-text("Cerrar sesión")').first();
  if (await logoutBtn.isVisible()) {
    await logoutBtn.click();
  } else {
    // Si no está visible, limpiar storage y recargar a /login
    await page.evaluate(() => {
      sessionStorage.clear();
      localStorage.removeItem('auth_token');
      localStorage.removeItem('auth_refresh_token');
      window.location.href = '/login';
    });
  }
  await page.waitForURL(/\/login/, { timeout: 10000 });
}
