# F7/F8 remediation progress (access, intercom, parking, reservations, packages, communications, pets/vehicles)

Date: 2026-10-10. Branch `fix/f07-f08-community`; merged with `fix/f0-f11-p0-remediation` (F0/F3 and F10). Not pushed, not deployed.

The earlier C-06/F7-02 (`porteria-isolation-progress.md`) and C-11 (`property-package-progress.md`) behavior is kept.

## Fixed items

| ID | Change |
|---|---|
| Turnos (open item of C-06) | `listarPorterosDisponibles`, `listarTurnos`, `asignarTurno` and `desasignarTurno` return 403 without a property context, with no fallback to property 1. `asignarTurno` checks that the porteria belongs to the property and that the user is an active PORTERO of it. The `MERGE` is replaced by a property-scoped UPDATE/INSERT, because PORTERIA_TURNOS is VPD-protected since V5.32 and `MERGE` raises ORA-28132 on such tables. |
| F07-01 (F7-05/06/07/08) | The server issues the QR token, the use count (1–10), the state and `generadoPor`. Expiry is clamped to 5 min–7 days. A QR can only be generated for an open visit in the caller's scope. A QR of a cancelled, rejected or finished visit never admits entry or validates. Cancel (`PUT /porteria/visitas/{id}/cancelar`, and `DELETE /visitas/{id}`, which the visits page already called but which did not exist) and exit revoke active QRs. `POST /porteria/qr/{id}/revocar` revokes one QR. Visit, QR and access-record reads are scoped to the caller's unit or property, with 404 when out of scope. QR entry requires a context: no `propId`/`porteroId` = 1. Consumption stays in `SP_VALIDAR_CONSUMIR_QR`, and internal SP errors (SQLERRM) are no longer returned. On update, the unit and visitor are immutable; residents may only keep or cancel a visit, and a closed visit is never reopened. |
| F07-02 | Intercom requests are persisted in `INTERCOM_SOLICITUDES` (V5.60). `POST /porteria/qr/notificar` opens or reuses the pending request. Residents of the unit answer once through the existing `/buzon/confirmar-pendiente` and `/buzon/confirmar` calls. The gate reads the persisted state from `/buzon/resultado-notificar`. A request expires after 5 minutes, and a replayed or late decision gets 409. The gate can cancel a request. The fixed `confirmado: 1` stub and the no-op endpoints are removed. |
| F07-03 (F7-03/04/13) | One transactional exit finalizes the visit, closes the vehicles still inside with a server-side overtime fee, releases their spots, revokes QRs and records the SALIDA access. A repeated exit is a no-op. The fee uses per-property config `TARIFA_PARQUEADERO_VISITANTE_HORA` (default 0) per started hour beyond `MINUTOS_GRATIS_PARQUEADERO_VISITANTE` (default: the maximum visit time). A client-sent `costoTotal` is ignored. A visitor spot is claimed by type (VISITANTES or MOTOS) with a conditional update, and resident assignment (`crearAsignacion`) uses the same kind of claim, so the same spot is never assigned twice. |
| `getPaquetesByUnidad` | Staff can only read units of their property; lists are filtered to the caller's property. A missing context and SUPERADMIN are denied, and there is no fallback to property 1 or user 1 on register. |
| F08-02 | Each PIN attempt is reserved atomically (conditional increment in REQUIRES_NEW) before the comparison, so at most 3 comparisons happen even under concurrency. Logs never contain the PIN or database text; this was confirmed RED, because the previous warning echoed the exception message. The global `MIN(ID_COMUNICADO)` cross-tenant fallback is removed. `POST /paquetes/{id}/notificar` re-sends a missing notification. |
| F08-01 (+F10-07) | Per-zone operating hours, minimum and maximum advance, active-reservation quota, deposit and cancellation window are stored in V5.61 and editable through the zone update. Hours are validated as HH:MM and past dates are rejected. A unit row lock prevents parallel or overlapping bookings across zones. Approval requires the deposit (`PUT /reservas/{id}/deposito`), and residents can only cancel before the window. An applied common-area suspension blocks booking through the `CommonAreaRestrictionChecker` seam, which now delegates to F10's `SancionService.hasActiveCommonAreaRestriction`. RESIDENTE_CONVIVENCIA sees only eligibility, never amounts or reasons. SUPERADMIN gets 403. |
| F08-03 (F8-08/10/11) | Notices are scoped to the caller's property (or the org admin's properties). Residents get TODOS, their tower/block, and the owner or tenant segments, and never expired notices. Legacy segment values are mapped. TORRE_BLOQUE requires a block of the property. `PUBLICADO_POR` is recorded, the e-mail HTML is escaped, and e-mails follow the segment. Read and per-user archive state is stored in `COMUNICADOS_LECTURAS` (V5.62) through `PUT /buzon/avisos/{id}/leido` and `/archivar`. Noise notices require a unit of the property, with no fallback to user 1. |
| F08-04 | Pets and vehicles can only be managed for the caller's own unit. A resident with no unit in the context is denied, and staff only reach units of their property. The owner must be a resident or owner of the unit. Species, vehicle type and plate are validated. Media must be an http(s) or `/uploads/` URL, or a PNG, JPEG, WEBP or PDF data URI of at most 2 MB. No `RuntimeException("... no encontrado")` remained; the repository's bare `orElseThrow()` now throws `NoSuchElementException` with a message. |
| DEC-02 | Service-level SUPERADMIN denial in porteria, intercom, packages, reservations, communications and pets/vehicles. |

## Tests (RED → GREEN)

| Class | RED | GREEN |
|---|---|---|
| PorteriaTurnoScopeTest | 5/5 failed (+1 for MERGE) | 5 |
| PorteriaVisitLifecycleTest | 23 run: 18 F + 3 E (+3 compile-RED) | 26 |
| PorteriaVisitorIsolationTest (+2) | compile-RED | 15 |
| PorteriaParkingClaimTest | written with the repository (no RED) | 3 |
| IntercomServiceTest / IntercomControllerTest | 12/12; 1/4 | 12 / 4 |
| PackageScopeAndCustodyTest (+PackagePinExposureTest updated) | 9/11 (+1 compile) | 12 / 10 |
| ReservationPolicyTest / ReservationPolicyPersistenceTest / SanctionsRestrictionCheckerTest | 14/18; compile; compile | 18 / 3 / 1 |
| ComunicadoSegmentationTest / ComunicadosControllerScopeTest | 13/13; compile | 13 / 3 |
| DependentOwnershipTest | 8/10 | 10 |
| ParkingAssignmentConcurrencyTest | 3/3 | 3 |

Unit tier (`-Dsaed.test.tier=unit`, fresh reports, after the merge): 74 classes, 636 tests, 0 failures, 0 errors. `compile checkstyle:check` passes. Frontend: `npm run lint` gives 0 errors, and `npm run build` passes.

Updated existing tests: `PackagePinExposureTest` (missing context is now 403; attempts go through `reservarIntentoPin`), `CentroComunicacionesTest` (DB tier: the notice test now runs as ADMIN_PROPIEDAD, per DEC-02), and `P301SuperAdminOperationalRestrictionSecurityTest` (`@MockBean ComunicadoService`).

## Migrations

- V5.60 `INTERCOM_SOLICITUDES`, with policy `POL_RLS_PROP_INTERCOM`.
- V5.61 zone booking-policy columns and checks on `ZONAS_COMUNES`.
- V5.62 `COMUNICADOS_LECTURAS`, with policy `POL_RLS_PROP_COM_LECTURAS`.

None of them has been run against Oracle.

## Frontend

- `ComunicacionesPage` sends the canonical segments (plus CONSEJO_ADMIN) and subscribes to the notification sync bus.
- The resident pet and vehicle forms no longer fall back to person 1 as owner.

## Open items

- Mocks do not prove Oracle behavior. These still need to run on Oracle: V5.60–V5.62 under VPD, the DB-backed suites (QrAtomicConsumption, Phase8*, PackageDelivery, Phase1E/G/H, CentroComunicaciones, VisitAuthorization), and the RLS visibility of the new tables for residents.
- Not yet verified on Oracle: whether NOTIFICACIONES accepts rows without ID_COMUNICADO (F8-02, RLS). Recovery today is the resend endpoint plus the resident's package list.
- QR SP: the visit-state check is done in Java and through revocation, not inside the SP, and the SP still uses `WHEN OTHERS` internally.
- The identity-upsert elevation in `programarVisita` remains (C-06 deviation).
- CONSEJO_ADMIN has no membership model, so it is staff-only.
- The PIN is still 4 digits and stored in plain text (hash/6 digits not done).
- Package photos are still listed in full (F8-06).
- PORTERO can still edit full parking spot data (F7-09).
- Camera behavior on a real device is unverified.
- `getVisitasByUnidad` for staff relies on RLS.
- The frontend has no UI yet for QR revocation, the deposit, the zone policy or per-user archive (the APIs exist).
