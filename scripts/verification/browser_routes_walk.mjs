// Route-by-route browser walk: one account per runtime role visits every route declared in frontend/src/App.jsx.
// For each route: redirected away (no access) or rendered. On rendered routes it records API 5xx, API 403/401
// (the screen calls an endpoint its own role may not use), API 404 (path the backend does not serve), API 400 on
// page load (wrong request contract), uncaught page errors and blank screens.
// Never prints passwords or tokens.
//
// Usage (repo root): node scripts/verification/browser_routes_walk.mjs <state> <frontend-url|serve> [usernames,...]
// With "serve" the walk itself serves local/isolated-oracle/<state>/dist (a `vite build` with VITE_API_BASE_URL=/api/v1)
// and proxies /api to the isolated backend from port.txt, in this same process: no separate dev server can die mid-walk.
import { createRequire } from 'node:module';
import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { createServer, request as httpRequest } from 'node:http';
import { extname, join, normalize, resolve } from 'node:path';

const require = createRequire(resolve('frontend/package.json'));
const { chromium } = require('playwright');

const [, , stateName, target = 'http://127.0.0.1:5199', only] = process.argv;
const state = JSON.parse(readFileSync(join('local', 'isolated-oracle', stateName, 'private-state.json'), 'utf-8'));

const MIME = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.svg': 'image/svg+xml', '.png': 'image/png',
  '.ico': 'image/x-icon', '.json': 'application/json', '.woff2': 'font/woff2', '.woff': 'font/woff', '.jpg': 'image/jpeg' };

async function serveBuild() {
  const dist = resolve('local', 'isolated-oracle', stateName, 'dist');
  const backendPort = Number(readFileSync(join('local', 'isolated-oracle', stateName, 'port.txt'), 'utf-8').trim());
  const server = createServer((req, res) => {
    if (req.url.startsWith('/api/')) {
      const upstream = httpRequest({ host: '127.0.0.1', port: backendPort, path: req.url, method: req.method,
        headers: { ...req.headers, host: `127.0.0.1:${backendPort}` } }, (up) => {
        res.writeHead(up.statusCode, up.headers);
        up.pipe(res);
      });
      upstream.on('error', () => { res.writeHead(502); res.end(); });
      req.pipe(upstream);
      return;
    }
    const path = normalize(join(dist, decodeURIComponent(req.url.split('?')[0])));
    const file = path.startsWith(dist) && existsSync(path) && extname(path) ? path : join(dist, 'index.html');
    res.writeHead(200, { 'Content-Type': MIME[extname(file)] || 'application/octet-stream' });
    res.end(readFileSync(file));
  });
  // 5199 is the only origin the isolated profile allows for CORS (application-isolated.yml).
  await new Promise((ok) => server.listen(5199, '127.0.0.1', ok));
  return { server, url: `http://127.0.0.1:${server.address().port}` };
}

const served = target === 'serve' ? await serveBuild() : null;
const baseUrl = served ? served.url : target;
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
    // 404 = path the backend does not serve; 400 on page load = wrong request contract (both went unnoticed before).
    const status = res.status();
    if (url.includes('/api/') && (status >= 500 || status === 403 || status === 401 || status === 404 || status === 400)) {
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
  if (home === '/login') {
    // A failed login would make every route "redirected" and the walk silently green.
    report.push({ username, role: account.role, home, rendered: [], redirected: [], loginFailed: true });
    await context.close();
    continue;
  }

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
if (served) served.server.close();
writeFileSync(join('local', 'isolated-oracle', stateName, 'browser-routes.json'), JSON.stringify(report, null, 2));

let defects = 0;
for (const r of report) {
  if (r.loginFailed) {
    defects++;
    console.log(`${r.username.padEnd(28)} ${r.role.padEnd(22)} LOGIN FAILED (still on /login)`);
    continue;
  }
  const bad = r.rendered.filter((x) => x.api.length || x.errors.length || x.blank);
  defects += bad.length;
  console.log(`${r.username.padEnd(28)} ${r.role.padEnd(22)} home=${r.home.padEnd(24)} rendered=${String(r.rendered.length).padStart(3)} redirected=${String(r.redirected.length).padStart(3)} withIssues=${bad.length}`);
  for (const x of bad.slice(0, 25)) {
    console.log(`    ${x.route.padEnd(26)} ${x.blank ? 'BLANK ' : ''}${x.api.slice(0, 4).join(' | ')}${x.errors.length ? ' | PAGEERROR ' + x.errors[0] : ''}`);
  }
}
console.log(`\nROUTES WITH ISSUES: ${defects}`);
process.exitCode = defects ? 1 : 0;
