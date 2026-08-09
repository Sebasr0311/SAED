# SAED Frontend — Design Language

This document is the single source of truth for the visual language of the
SAED frontend (`client/`). It describes the token system, the component kit,
and the rules that keep the UI uniform, accessible, and responsive.

## Principles

The interface follows shadcn/ui conventions applied to the existing SAED
stack (Vite + React 18 + Tailwind CSS v3, no TypeScript):

1. **One source of truth for tokens.** `tailwind.config.js` and the `:root`
   block in `client/index.css` define the same palette. Tailwind utility
   classes used by React components and inline `var(--...)` styles used by
   pages must resolve to identical values. **Never hardcode a hex color in a
   component or page**; add a token instead.
2. **Semantic colors, not raw values.** Colors are named by their role
   (`primary`, `error`, `success`, `on-surface`, `border`) not by their hue.
3. **Compose, don't reinvent.** Prefer the `components/ui` kit (`Button`,
   `Input`, `Select`, `Textarea`, `Modal`, `DataTable`, `Pagination`,
   `Toast`, `PageHeader`) over custom markup.
4. **One control radius.** Interactive controls share a single radius
   (`--radius-control` / Tailwind `rounded`). Pills are reserved for badges
   and small state chips, not for buttons or inputs.
5. **Uniformity by default.** Two pages of the same category (CRUD table,
   dashboard, resident portal) must read as the same system: same header,
   same table container, same spacing rhythm, same button variants.

## Palette

Brand: Navy + Amber. Semantic status colors are derived from it.

| Token (Tailwind / CSS var) | Value | Role |
|---|---|---|
| `primary` / `--primary` | `#0F2044` | Brand / main actions |
| `primary.hover` | `#163060` | Primary hover |
| `accent` | `#D97706` | Amber highlight (limited use) |
| `background` | `#EEF2F8` | App canvas |
| `surface` / `--surface` | `#FFFFFF` | Cards, panels, table containers |
| `surface-container` | `#F8FAFC` | Table header strip, toolbars |
| `on-surface` / `--on-surface` | `#0F172A` | Primary text on surface |
| `on-surface-variant` / `--on-surface-variant` | `#475569` | Secondary text |
| `text-muted` / `--text-muted` | `#94A3B8` | Hints, placeholders, empty states |
| `border` / `--border` | `#E2E8F0` | Default dividers |
| `border-subtle` / `--border-subtle` | `#F1F5F9` | Hairline dividers (cards, rows) |
| `error` / `--error` | `#E11D48` | Destructive actions, field errors |
| `error-container` / `--error-container` | `#FFE4E6` | Error callout background |
| `success` / `accent-green` | `#059669` | Positive states, money, confirmations |
| `accent-green` (text/bg pair) | `#047857` / `#ECFDF5` | Positive actions (contrast AA) + callout bg |
| `success-strong` | `#065F46` | Dark green text/icon on light green |
| `warn` / `warn-amber` | `#B45309` | Warning states (contrast AA) |
| `warn-amber-bg` | `#FFFBEB` | Warning callout background |
| `info` / `--info` | `#0369A1` | Info blue text/icon |
| `info-bg` | `#E0F2FE` | Info callout background |

Status badges (`success`, `danger`, `warn`, `info`, `neutral`, and the
domain-specific `activo`/`ocupado`/`cancelado`/...) in `index.css` reuse the
tinted-background + dark-foreground pattern above so a status always reads
its semantic.

## Radii

| Token | Value | Used for |
|---|---|---|
| `rounded` / `--radius-control` | `12px` | Buttons, inputs, selects, tabs, filters |
| `rounded-md` | `14px` | Table containers, side tabs |
| `rounded-lg` | `18px` | Cards, modals, stat cards |

## Accessibility

- Text contrast targets WCAG AA (≥4.5:1). Keep `text-secondary` for secondary text, use `text-muted` sparingly for hints/placeholders (4.76:1).
- Touch targets are ≥44px on mobile (WCAG 2.5.8).
- `prefers-reduced-motion: reduce` disables layout/entrance animations (WCAG 2.3.3).
- Clickable table rows expose keyboard access (`tabIndex`, Enter/Space, focus ring).
- All `<img>` carry descriptive `alt`; modals have focus trap + Escape + return focus.

## Spacing

- Content canvas padding: `32px` desktop, `20px 16px` mobile.
- Card padding: `24px`; stat card `20px`.
- Grid gaps: `16px` between cards; toolbar gaps `8px`.
- Field groups: label above control, `6px` gap between label and control.

## Type

- Font: **Plus Jakarta Sans** (weights 400–800), loaded in `index.html`.
- Mono: **JetBrains Mono** for codes/QR/tokens.
- Base size `14px`, line-height `1.6`. Canvas headings `20px/700`,
  card titles `16px/700`, table header `11px/700 uppercase`.

## Components (`client/components/ui`)

| Kit component | Notes |
|---|---|
| `Button` | Variants: `primary`, `accent`, `danger`, `outline`, `ghost`. Sizes `sm/md/lg`. Radius `rounded` (12px). |
| `Input/Select/Textarea` | Radius `rounded` (12px), focus ring `primary/20`, error state `error` border + message. |
| `Modal` | `rounded-lg` (18px), focus trap + Escape + return focus (a11y hardened). |
| `DataTable` | Headings uppercase `11px/700`; container `overflow-x: auto` so wide tables scroll on mobile. |
| `EmptyState` | Reusable empty state: icon chip + title + optional subtitle. `DataTable` accepts `empty` as string (title) or `{ icon, title, subtitle }`. |
| `Pagination` | Prev/Next + `Página x / y` helper text. |
| `Toast` | Fixed bottom-right; semantic colors from tokens. |
| `PageHeader` | `title` (`20px/700`) + optional `subtitle` + `action`. |

## Responsive behavior

- **Desktop:** sidebar rail (72px) / expanded (240px) with hover-expand.
- **Mobile (≤768px):** sidebar becomes a drawer (264px, translated off-canvas);
  topbar shows only avatar + logout; `form-row` stacks to one column;
  `dashboard-panels` stack vertically; **wide tables scroll horizontally**
  (never clip).
- **Small (≤480px):** `form-row-3/4` collapse to one column; `card-grid-4`
  becomes 2 columns.
- Test every page at 390px and 1440px before shipping.

## Dark Mode

- **Theme system:** `data-theme="dark"` on `<html>`. Light tokens live in `:root`, dark overrides in `[data-theme='dark']` (both in `client/index.css`).
- **Tailwind:** `darkMode: ['selector', '[data-theme="dark"]']`; semantic colors in `tailwind.config.js` resolve to the CSS vars, so kit components and pages invert automatically.
- **Toggle:** topbar button (icon `light_mode`/`dark_mode`) in `AppShell`; persisted in `localStorage['saed_theme']` via `client/lib/theme.js`; first load follows `prefers-color-scheme` (applied in `main.jsx` before render, no flash).
- **Rules:**
  - Solid buttons (`--btn-*`) stay deep in both modes so white text keeps AA contrast.
  - Status tints (`--tint-*`) switch to translucent dark backgrounds with light foregrounds.
  - Focus rings use RGB-triplet tokens (`--ring-primary`, `--ring-error`) for Tailwind alpha.
  - `.card-dark` (navy gradient card) and the sidebar stay dark in both modes by design.

## Motion

- Page entrances: single `slide-up` 0.25s on the content area (no cascades).
- Theme switch: surfaces/text/borders cross-fade 0.25s.
- All motion respects `prefers-reduced-motion: reduce` (killed globally).

## Performance

- Routes are code-split with `React.lazy` + `Suspense` (per-route chunks; login stays eager). Heavy deps (`xlsx-js-style`) load **only on the Export click** (`await import('xlsx-js-style')` inside `exportarExcel` in Ganancias/Historial) — the 850KB chunk never blocks page open.
- Avoid layout-property animations (`width`/`height`/`margin`); prefer `transform`/`opacity`.

## Browser surfaces

- `document.title` is dynamic per page: `"{Sección} — SAED"` (AppShell) and `"Iniciar sesión — SAED"` (login).
- Global keyboard focus ring (WCAG 2.4.7): `:focus-visible` outline `--border-focus` on all controls, consistent with table-row focus.

## Contribution rules

- Add a new color to **both** `tailwind.config.js` and the `:root` block in
  `client/index.css` using the same value; never change only one.
- New UI component → put it in `client/components/ui` and consume it from
  pages; do not hand-roll a styled `div`.
- Inline styles should reference `var(--token)` or Tailwind utilities; avoid
  literal hex in `style={{}}`.
- When rendering an error value that may be an `Error` object, use
  `err?.message || err`.
