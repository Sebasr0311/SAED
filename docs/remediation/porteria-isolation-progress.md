# Porteria Isolation Progress

Date: 2026-10-09
Scope: C-06 (F5-H02 = F7-01) and F7-02 in `PorteriaController`.

## Fixed

| Defect | Change |
|---|---|
| C-06 `GET /porteria/visitas/buscar` | Only `SCOPE_ADMIN_PROPIEDAD` and `SCOPE_PORTERO` may call it. Residents get 403. The SUPERADMIN/bootstrap elevation is removed. The query runs under the caller's own context and the WHERE clause requires a visit to a unit in the caller's property (`ID_PROPIEDAD = :propId`). Email is no longer read or returned, and the phone is masked to its last 4 digits. A missing property context, an out-of-scope person and a nonexistent person all return the same `200 {}`. |
| F7-02 `POST /porteria/visitas` (and `/visitas/rapida`) | The request is denied when there is no context. A client-supplied `idPropiedad`/`propiedadId` must equal the caller's property, otherwise 403. `idUnidad` is now read as an alias of `unidadId`, so it cannot bypass the unit check. Admin and portero callers must have a property, and every unit they use must belong to it, including units resolved from `idResidente`. Before this change PORTERO had no check and units from `idResidente` were not checked. The silent fallback to unit `1` is removed (now 400). A client `visitanteId` that is not visible under the caller's RLS is discarded. Client `idPersona`/`idVisitante` inside the `visitante` payload are ignored. All scope checks run before any elevated statement. |

## Deviation: elevation in `programarVisita` not fully removed

The elevated block that upserts the visitor identity (PERSONAS/VISITANTES) is kept. It is now narrowed: it runs only after the scope checks pass and receives no client-supplied ids. Evidence that removing it entirely would break visitor registration on Oracle:

- `UQ_PERSONAS_DOCUMENTO (ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO)` is global (V3.9). A person who is invisible under caller RLS cannot be re-inserted.
- `POL_RLS_PROP_VISITANTES` uses `update_check = TRUE`, and its predicate requires an existing VISITAS row in the caller's property or unit (V4.15). Inserting a new VISITANTE without a visit would violate the policy.

Fully removing it requires a DB-side definer procedure (for example `UPSERT_VISITOR_IDENTITY`) that returns only ids.

## Tests

- New: `PorteriaVisitorIsolationTest` (12, Mockito) and `PorteriaVisitorLookupAuthorizationTest` (3, `@WebMvcTest` + `SecurityConfig`).
- RED: 15 run, 12 failed for the expected reasons (status 200 instead of 403, email returned, SUPERADMIN context switch, no 403 for foreign unit/property/resident unit, fallback to unit 1, foreign visitor/persona ids used).
- GREEN: 15/15 pass. The CI `-Dtest` list plus the new tests: 19 suites, 104 tests, 0 failures, 0 errors. `checkstyle:check compile` pass.
- `AuditFixesSecurityIntegrationTest`: 17/18 pass. `superAdmin_cannotAccessTodasReservas` fails because of the committed `ReservasController` (unrelated to this change).
- Mocked tests do not prove RLS behavior on Oracle. The DB-backed suites (`ResidenteAdversarialAuthorizationTest`, `AdminPropiedadAdversarialAuthorizationTest`) were not run.

## Changed files

- `backend/src/main/java/com/saed/backend/porteria/controller/PorteriaController.java`
- `backend/src/test/java/com/saed/backend/porteria/controller/PorteriaVisitorIsolationTest.java` (new)
- `backend/src/test/java/com/saed/backend/porteria/controller/PorteriaVisitorLookupAuthorizationTest.java` (new)

## Frontend impact

- The only callers of `/porteria/visitas/buscar` are resident pages (`ResVisitaPage.jsx`, `ResVisitasPage.jsx`). Both catch errors and return `null`, so for residents visitor autofill silently stops working. No page breaks. No gate page calls the lookup.
- `email` is no longer returned, and the pages fall back to `''`. `telefono` is masked and gets pre-filled in its masked form. That is harmless for existing persons, because they are resolved by document and the phone is not rewritten.

## Other elevations in this controller

- `restoreSaedContext` calls `SET_BOOTSTRAP_CONTEXT(<caller id>)` followed by the caller's `SET_CONTEXT`. This puts the caller back in their own context and is not an escalation. It still interpolates the role into SQL with `String.format`, but the role comes from the server-side context.
- No other `SUPERADMIN` elevation exists in `PorteriaController`, `PorteriaService`/`Impl` or `PorteriaRepository`/`Impl`.

## Open items

- Replace the remaining identity-upsert elevation with a definer procedure.
- `listarPorterosDisponibles`, `listarTurnos`, `asignarTurno` and `desasignarTurno` default `propId` to `1L` when the context is missing. This is a cross-tenant default and should deny instead.
- `asignarTurno` does not check that `idUsuario` is a PORTERO of the property, and its MERGE `ON` clause has no `ID_PROPIEDAD`.
- The QR email is still sent to whatever email the visitor's PERSONA row holds.
- Run the DB-backed adversarial suites on Oracle. Before, unresolvable `visitanteId=10` returned 201. It now returns 400 unless that visitor is visible.

## 2026-10-09 cross-model review follow-up

An independent review (Gemini 3.1 Pro via Antigravity, read-only) found that `programarVisita` still fell back to the first unit of the caller's property (`ROWNUM = 1`) when no unit was resolvable; only the final `unidadId = 1L` default had been removed. The existing test passed because its mock returned no units. A new test (`unresolvableUnitIsRejectedEvenWhenPropertyHasUnits`) failed first (13 tests, 1 failure), and removing the fallback made it pass. Full CI selection plus remediation tests: 234 tests, 0 failures, 0 errors; compile and checkstyle pass. Frontend: `VisitasPage` already requires a unit or resident; resident pages send their unit or rely on assignment resolution.

Dismissed review items after code inspection: self-invocation from `programarVisitaRapida` (both methods carry identical `@PreAuthorize` and the scope checks live in the method body); self-invocation of `PaquetesServiceImpl.getPaqueteById` (PIN filtering is in the method body, not an aspect). Retained as a follow-up, not caused by these commits: `PropertyDeletionService` reports "code sent" even when OTP email delivery fails.
