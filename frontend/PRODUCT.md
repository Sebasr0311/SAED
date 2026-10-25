# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

- **Primary buyer:** the property administrator of a residential complex under Colombian "propiedad horizontal" (ADMIN_PROPIEDAD), and administration companies that run several complexes (ADMIN_ORGANIZACION). They evaluate SAED on desktop, during office hours, comparing it with spreadsheets, WhatsApp groups and paper logbooks.
- **Daily users inside a complex:** doormen at the gatehouse (PORTERO), residents and owners on their phones (RESIDENTE, RESIDENTE_CONVIVENCIA, PROPIETARIO).

## Product Purpose

SAED 2.0 (Sistema de Administración de Edificios y Copropiedades) is a multi-tenant SaaS that connects administration, gatehouse, residents and finances of a residential complex in one platform. Success: an administrator replaces scattered tools with SAED and runs the complex from it.

## Positioning

One shared record for the whole complex: what the administrator registers, the doorman validates at the gate and the resident sees on the phone, without duplicate data. Built for Colombian propiedad horizontal (Ley 675 of 2001: coefficients, assemblies, minutes).

## Operating Context

- Gatehouse: visitor QR invitations validated with a scanner, visitor parking, package custody.
- Administration: units, residents, lease contracts, portfolio and dues, invoicing, budgets, expenses, reconciliations, paz y salvo, fines, assemblies, documents and bylaws, PQRS, common-area bookings, maintenance, insurance, incidents.
- Resident portal: dues and online payment, visits and frequent visitors, mailbox, bookings, fines, assemblies.
- Organization console for administration companies with several properties.

## Capabilities and Constraints

- Confirmed integrations: Wompi payments (PSE and cards), QR generation and scanning, account activation by emailed link, transactional email (Brevo).
- Tenant data isolation is enforced in the database (Oracle VPD/RLS).
- Works in the browser; no special hardware required.
- Plans and prices come from the API (`/planes`); the landing must not hardcode prices. Annual billing carries a discount computed in code.
- Not built yet (must not be presented as available): license-plate recognition, live real-time assembly quorum, push notifications/PWA, IoT parking barriers.
- Spanish-only copy; currency COP formatted with `es-CO`.

## Brand Commitments

- Name: SAED / SAED 2.0.
- Light identity pinned by the owner: Blanco #FFFFFF, Perla #F7F9FE, Azul pastel #DFEBFF, Lavanda #EEE8FF, Menta #DCF4EB, Índigo SAED #5267DA, Azul tinta #1D2942. The previous dark palette stays as the landing's dark mode.
- Glassmorphism is a requested material.
- Logo assets in `public/imagenes/` (`saed_logo_emblem_only.png`, `saed_logo_corporate.png`).
- 15-second promo film in `public/videos/` (illustrative screens, teal-on-ink palette).

## Evidence on Hand

- No customers, testimonials, case studies, usage metrics or savings figures exist. Do not fabricate them.
- Demonstrations may use illustrative data, always labeled as example data.
- Current landing copy contains unverified figures (time savings, ROI tables, delinquency reduction, setup time, Habeas Data compliance); they must be removed or rewritten as non-quantified statements.

## Product Principles

1. Show the product working; never claim results it cannot prove.
2. One record, three experiences: administrator, doorman, resident.
3. Built for Colombian propiedad horizontal, not a generic property tool.
4. Calm operation: fewer tools, fewer messages, clearer money.

## Accessibility & Inclusion

Target WCAG 2.2 AA. Respect `prefers-reduced-motion` (video falls back to the poster). Residents range widely in age and digital literacy.
