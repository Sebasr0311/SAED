import { test, expect } from '@playwright/test';

// This fixture exercises the live component against synthetic HTTP responses only.
// It does not verify JWT authorization, Oracle RLS or email delivery.
async function openFixture(page, accountCreated) {
  const requests = [];
  await page.route('**/*', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    if (!['127.0.0.1', 'localhost'].includes(url.hostname)) {
      await route.abort();
      return;
    }
    if (url.pathname.startsWith('/api/v1/')) {
      if (url.pathname !== '/api/v1/usuarios' || request.method() !== 'POST') {
        await route.fulfill({ status: 500, contentType: 'application/json',
          body: JSON.stringify({ message: 'Unexpected fixture request' }) });
        return;
      }
      requests.push(request.postDataJSON());
      if (accountCreated === 'denied') {
        await route.fulfill({ status: 403, contentType: 'application/json',
          body: JSON.stringify({ message: 'No tiene autorización sobre el destino del registro' }) });
        return;
      }
      const data = accountCreated
        ? { idUsuario: 77, accountCreated: true, username: 'synthetic-user',
          passwordGenerada: 'synthetic-generated-value', email: 'synthetic@example.invalid' }
        : { idUsuario: 77, accountCreated: false, rol: 'RESIDENTE_CONVIVENCIA' };
      await route.fulfill({ status: 201, contentType: 'application/json',
        body: JSON.stringify({ success: true, data }) });
      return;
    }
    await route.continue();
  });
  await page.addInitScript(() => {
    sessionStorage.setItem('auth_token', 'synthetic-fixture-token');
    sessionStorage.setItem('saed_active_assignment_id', '100');
  });
  await page.goto('/tests/fixtures/registration-contract.html');
  await page.getByRole('button', { name: 'Agregar Conviviente', exact: true }).click();
  await page.locator('#conv-primer-nombre').fill('Persona');
  await page.locator('#conv-primer-apellido').fill('Sintetica');
  await page.locator('#conv-num-doc').fill('1234567890');
  await page.locator('#conv-email').fill('synthetic@example.invalid');
  await page.locator('#conv-usuario').fill('synthetic-user');
  return requests;
}

test('reusing an existing identity reports binding without displaying replacement credentials', async ({ page }) => {
  const requests = await openFixture(page, false);
  await page.getByRole('button', { name: /Registrar/i }).click();
  await expect(page.getByText(/sin modificar.*credenciales/i)).toBeVisible();
  await expect(page.getByRole('dialog', { name: /credenciales/i })).toHaveCount(0);
  await expect(page.getByText(/enviado.*credenciales.*correo/i)).toHaveCount(0);
  expect(requests).toHaveLength(1);
  expect(requests[0].idUnidad).toBe(1000);
  expect(requests[0].rol).toBe('RESIDENTE_CONVIVENCIA');
});

test('new identity reports new credentials while retaining the explicit unit request', async ({ page }) => {
  const requests = await openFixture(page, true);
  await page.getByRole('button', { name: /Registrar/i }).click();
  await expect(page.getByText('synthetic-generated-value')).toBeVisible();
  expect(requests).toHaveLength(1);
  expect(requests[0].idUnidad).toBe(1000);
});

test('a rejected registration remains visible as an error without displaying credentials', async ({ page }) => {
  const requests = await openFixture(page, 'denied');
  await page.getByRole('button', { name: /Registrar/i }).click();
  await expect(page.getByText('No tiene autorización sobre el destino del registro')).toBeVisible();
  await expect(page.getByRole('dialog', { name: /Registrar Residente/i })).toBeVisible();
  await expect(page.getByRole('dialog', { name: /credenciales/i })).toHaveCount(0);
  expect(requests).toHaveLength(1);
});
