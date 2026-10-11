// Route-by-route browser walk: one account per runtime role visits every route declared in frontend/src/App.jsx.
// For each route: redirected away (no access) or rendered. On rendered routes it records API 5xx, API 403/401
// (the screen calls an endpoint its own role may not use), uncaught page errors and blank screens.
// Never prints passwords or tokens.
//
// Usage (repo root): node scripts/verification/browser_routes_walk.mjs <state> <frontend-url> [usernames,...]
import { createRequire } from 'node:module';
import { readFileSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';

const require = createRequire(resolve('frontend/package.json'));
const { chromium } = require('playwright');

const [, , stateName, baseUrl = 'http://127.0.0.1:5199', only] = process.argv;
const state = JSON.parse(readFileSync(join('local', 'isolated-oracle', stateName, 'private-state.json'), 'utf-8'));
const PUBLIC = new Set(['/', '/suscripciones', '/planes', '/login', '/activar-cuenta', '/registro-organizacion', '/checkout-plan', '/onboarding', '/app']);
const routes = [...new Set([...readFileSync('frontend/src/App.jsx', 'utf-8').matchAll(/path="([^"]+)"/g)]
  .map((m) => (m[1].startsWith('/') ? m[1] : `/${m[1]}`))
  .filter((p) => !p.includes(':') && !p.includes('*') && !PUBLIC.has(p)))];

const defaultUsers = ['iso_0_superadmin', 'iso_0_admin_organizacion', 'iso_0_admin_propiedad', 'iso_0_portero', 'iso_0_residente', 'iso_0_residente_convivencia'];
const users = only ? only.split(',') : defaultUsers;
const report = [];

const browser = await chromium.launch();
for (const username of users) {
  const account = state.accounts.find((a) => a.username === username);
  const context = await browser.newContext({ viewport: { width: 1366, height: 860 } });
  const page = await context.newPage();
  let current = { api: [], errors: [] };
  page.on('response', (res) => {
    const url = res.url();
    if (url.includes('/api/') && (res.status() >= 500 || res.status() === 403 || res.status() === 401)) {
      current.api.push(`${res.status()} ${res.request().method()} ${new URL(url).pathname}`);
    }
  });
  page.on('pageerror', (err) => current.errors.push(String(err.message).slice(0, 140)));

  await page.goto(`${baseUrl}/login`, { waitUntil: 'domcontentloaded' });
  await page.fill('#login-username', account.email);
  await page.fill('#login-password', account.password);
  await page.click('button[type="submit"]');
  await page.waitForTimeout(3000);
  const home = new URL(page.url()).pathname;

  const rendered = [];
  const redirected = [];
  for (const route of routes) {
    current = { api: [], errors: [] };
    try {
      await page.goto(`${baseUrl}${route}`, { waitUntil: 'domcontentloaded' });
    } catch (err) {
      rendered.push({ route, api: [], errors: [`NAVIGATION ${String(err.message).split(/\r?\n/)[0].slice(0, 100)}`], blank: false });
      continue;
    }
    await page.waitForTimeout(1400);
    const landed = new URL(page.url()).pathname;
    if (landed !== route) {
      redirected.push(route);
      continue;
    }
    const text = (await page.innerText('body').catch(() => '')).trim();
    rendered.push({ route, api: [...new Set(current.api)], errors: [...new Set(current.errors)], blank: text.length < 40 });
  }
  report.push({ username, role: account.role, home, rendered, redirected });
  await context.close();
}
await browser.close();
writeFileSync(join('local', 'isolated-oracle', stateName, 'browser-routes.json'), JSON.stringify(report, null, 2));

let defects = 0;
for (const r of report) {
  const bad = r.rendered.filter((x) => x.api.length || x.errors.length || x.blank);
  defects += bad.length;
  console.log(`${r.username.padEnd(28)} ${r.role.padEnd(22)} home=${r.home.padEnd(24)} rendered=${String(r.rendered.length).padStart(3)} redirected=${String(r.redirected.length).padStart(3)} withIssues=${bad.length}`);
  for (const x of bad.slice(0, 25)) {
    console.log(`    ${x.route.padEnd(26)} ${x.blank ? 'BLANK ' : ''}${x.api.slice(0, 4).join(' | ')}${x.errors.length ? ' | PAGEERROR ' + x.errors[0] : ''}`);
  }
}
console.log(`\nROUTES WITH ISSUES: ${defects}`);
