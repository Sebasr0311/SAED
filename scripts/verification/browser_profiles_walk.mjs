// Browser walk of every synthetic profile against the isolated stack (Vite proxy -> isolated backend).
// Logs in through the real login form, discovers the profile's in-app navigation links and visits each one,
// recording console errors, API 5xx responses and blank screens. Never prints passwords or tokens.
//
// Usage (from repo root): node scripts/verification/browser_profiles_walk.mjs <state> <frontend-url>
import { createRequire } from 'node:module';
import { readFileSync, mkdirSync } from 'node:fs';
import { join, resolve } from 'node:path';

const require = createRequire(resolve('frontend/package.json'));
const { chromium } = require('playwright');

const [, , stateName, baseUrl = 'http://127.0.0.1:5199'] = process.argv;
const state = JSON.parse(readFileSync(join('local', 'isolated-oracle', stateName, 'private-state.json'), 'utf-8'));
const shotDir = join('local', 'isolated-oracle', stateName, 'browser');
mkdirSync(shotDir, { recursive: true });

const MAX_PAGES = 45;
const summary = [];

const browser = await chromium.launch();
for (const account of state.accounts) {
  const context = await browser.newContext({ viewport: { width: 1366, height: 860 } });
  const page = await context.newPage();
  const consoleErrors = [];
  const serverErrors = [];
  page.on('console', (msg) => { if (msg.type() === 'error') consoleErrors.push(`${page.url()} :: ${msg.text().slice(0, 160)}`); });
  page.on('pageerror', (err) => consoleErrors.push(`${page.url()} :: PAGEERROR ${String(err.message).slice(0, 160)}`));
  page.on('response', (res) => {
    if (res.url().includes('/api/') && res.status() >= 500) serverErrors.push(`${res.status()} ${res.request().method()} ${new URL(res.url()).pathname}`);
  });

  const row = { user: account.username, role: account.role, login: 'n/a', pages: 0, blank: [], consoleErrors, serverErrors };
  try {
    await page.goto(`${baseUrl}/login`, { waitUntil: 'domcontentloaded' });
    await page.fill('#login-username', account.email);
    await page.fill('#login-password', account.password);
    await page.click('button[type="submit"]');
    await page.waitForTimeout(3500);
    const stillOnLogin = page.url().includes('/login');
    row.login = stillOnLogin ? 'rejected' : 'ok';
    if (!stillOnLogin) {
      await page.screenshot({ path: join(shotDir, `${account.username}-home.png`) });
      const links = await page.$$eval('a[href^="/"]', (as) => [...new Set(as.map((a) => a.getAttribute('href')))]);
      const targets = links.filter((h) => h && !h.startsWith('/login') && !h.includes('logout')).slice(0, MAX_PAGES);
      for (const href of targets) {
        await page.goto(`${baseUrl}${href}`, { waitUntil: 'domcontentloaded' });
        await page.waitForTimeout(1800);
        row.pages++;
        const text = (await page.innerText('body').catch(() => '')).trim();
        if (text.length < 40) row.blank.push(href);
        if (page.url().includes('/login')) row.blank.push(`${href} (redirected to login)`);
      }
    }
  } catch (err) {
    row.login = `error: ${String(err.message).slice(0, 120)}`;
  }
  summary.push(row);
  await context.close();
}
await browser.close();

let problems = 0;
for (const r of summary) {
  const issues = r.consoleErrors.length + r.serverErrors.length + r.blank.length;
  problems += issues;
  console.log(`${r.user.padEnd(30)} ${r.role.padEnd(22)} login=${r.login.padEnd(8)} pages=${String(r.pages).padStart(2)} consoleErrors=${r.consoleErrors.length} api5xx=${r.serverErrors.length} blank=${r.blank.length}`);
  for (const e of [...new Set(r.serverErrors)].slice(0, 6)) console.log(`    5xx  ${e}`);
  for (const e of [...new Set(r.blank)].slice(0, 6)) console.log(`    blank ${e}`);
  for (const e of [...new Set(r.consoleErrors)].slice(0, 6)) console.log(`    console ${e}`);
}
console.log(`\nTOTAL ISSUES: ${problems}`);
