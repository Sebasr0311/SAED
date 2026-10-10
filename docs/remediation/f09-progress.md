# F9 Operations Remediation - Progress

Date: 2026-10-10. Branch `fix/f09-operations` (merged `fix/f0-f11-p0-remediation` at the start and again at the end, at 6a5bdb4; no conflicts).
Status: unit-verified. The coordinator still needs to run the Oracle integration tests.

Codex's partial work (uncommitted, never compiled) was reviewed before it was kept. Kept: the resident reply guard, the admin check, the CAS check on replies and the 1/4/24/72 h fallback. Changed: `CRITICA` is no longer accepted as a stored priority, because `CK_PQRS_PRIORIDAD` only allows EMERGENCIA/ALTA/MEDIA/BAJA. `CRITICA` is now normalized to `EMERGENCIA`. Its single test was rewritten into a full suite.

## Fixed

| Area | Audit / plan IDs | Change |
|---|---|---|
| PQRS | F9-01, F9-04, F9-07 (unit), F09-05, DEC-05 | Residents can reply but cannot change state. All admin operations (status, assign, priority, SLA config, list all) are restricted to ADMIN_PROPIEDAD/ADMIN_ORGANIZACION in the service. Only valid transitions are allowed, and the CAS check applies to replies. A ticket can only be assigned to a staff member of the property. A resident's ticket uses the session unit, and an admin-supplied unit must belong to the property. Missing tickets and other residents' tickets return 404. `PqrsSlaPolicy` sets the defaults to 1/4/24/72 h and computes the status server-side: at risk from 75 %, overdue past 100 %, and closed tickets are measured at their closing time. The status is exposed as `slaEstado` and `slaPorcentajeConsumido` on `TicketResponseDTO`. |
| Workers | F9-02, F09-03 (data minimization) | Residents and owners see only active workers relevant to their property: workers assigned to the property's works, or employed by a supplier with a VIGENTE contract there. They get name, trade, company and the `arlVigente` flag only. Document, contact and ARL details are not returned. The SUPERADMIN admin path was removed. `ResObrasPage` no longer shows document or ARL details. |
| Incidents | F9-03, F9-08, F09-04 | New `POST /incidentes/{id}/reabrir` (ADMIN_PROPIEDAD). It only reopens CERRADO incidents and requires a reason of at least 10 characters. Reason, user, time and a reopen counter are persisted. A concurrent reopen returns 409. The generic status endpoint can no longer reopen an incident. Missing incidents and other units' incidents return 404, and SUPERADMIN is denied. Residents get a reduced view: no findings, suggested sanction, police report number or involved parties. Reporting an incident is transactional, and errors are no longer swallowed. `IncidentesAdminPage` sends `motivo` and shows load errors with a retry button instead of an empty list. |
| Assets / maintenance | F9-12, F09-01 | `GET /activos/{id}/historial` returns the asset's real orders (schedule, execution, cost, supplier, report). `ActivosPage` renders it in place of the placeholder. Foreign assets return 404. Recurring plans have their own endpoints (`/mantenimientos/planes`, plus `/generar`). `PlanMantenimientoService.generarOrdenesProgramadas(property, date)` creates the latest due order once per plan and period; it is tenant-scoped and its idempotency rests on `UQ_MANT_PLAN_PERIODO`. |
| Supplier contracts | F9-10, F09-02 | `ContratoProveedorValidator` checks required fields, end ≥ start, a non-negative value and alert days of 0–365. `AvisoRenovacionService.generarAvisos(property, date)` records one notice per VIGENTE contract and end date once the alert window opens, and raises an ALERTAS_ADMIN row in the same transaction. It is tenant-scoped and admin-only. |
| Works | F9-11, F09-03 | New `GET /obras/{id}/trabajadores/{idTrabajador}/ingreso` for porter and admins. Entry is allowed only when the work is APROBADA or EN_EJECUCION and the date is within its window, and the worker is assigned, authorized, active and has ARL valid on that date. Completion requires an accepting inspection, persisted with inspector and time. The generic status endpoint cannot finalize a work. Missing works and other units' works return 404. `ObrasAdminPage` collects the inspection before finalizing. |
| DEC-02 | F3-02 | Removed the `SCOPE_SUPERADMIN` grants from Activo, Mantenimiento and Proveedor controllers. Removed the SUPERADMIN branches in the incident and worker services. `F9OperationsSuperAdminAccessTest` covers all F9 controllers. |

## Evidence (RED -> GREEN, fresh surefire reports)

- PqrsSlaPolicyTest + TicketServiceF9Test: RED 21/15 -> GREEN 21/0.
- TrabajadorPrivacyF9Test: RED 5/4 -> GREEN 5/0 (ArlVigenciaTest still green).
- IncidenteReaperturaF9Test: RED 11/10 -> GREEN 11/0.
- F9OperationsSuperAdminAccessTest: RED 1/1 -> GREEN 1/0.
- PlanMantenimientoF9Test + ActivoHistorialF9Test: RED 10/9 -> GREEN 10/0.
- ContratoProveedorF9Test: RED 9/9 -> GREEN 7/0. The 2 tests that exercised the finance repository were removed after it was reverted (see Integration points).
- ObraIngresoInspeccionF9Test: RED 8/7 -> GREEN 8/0.
- After the final merge, `-Dsaed.test.tier=unit` with compile + checkstyle: 83 classes, 699 tests, 0 failures, 0 errors.
- Python harness (`scripts/verification`, `test_*.py`): 98 tests OK.
- Frontend lint: ESLint on the 4 changed pages found 0 errors and 17 warnings, all pre-existing. The worktree has no `node_modules`, so ESLint ran from the main checkout's `node_modules` on copies of the files. `npm run build` was not run.
- Updated Oracle integration expectations: `Phase9PqrsUnifiedIntegrationTest` test 24 (403 -> 404); `Phase9IncidentesIntegrationTest` test 14 (reopen via `/reabrir` with a persisted reason; `/estado` now returns 400) and test 32 (residents get 403 on involved parties).

## Migrations (V5.40–V5.43)

No column with a DEFAULT is added to an existing table. New tables get FN_FILTRO_PROPIEDAD policies.

- `V5.40__incidentes_reapertura.sql`: INCIDENTES `MOTIVO_REAPERTURA`, `REABIERTO_POR`, `FECHA_REAPERTURA`, `NUMERO_REAPERTURAS`.
- `V5.41__planes_mantenimiento_recurrente.sql`: `PLANES_MANTENIMIENTO` (with RLS); MANTENIMIENTOS `ID_PLAN_MANTENIMIENTO`, `PERIODO_PLAN`, FK, `UQ_MANT_PLAN_PERIODO`.
- `V5.42__avisos_renovacion_contrato_proveedor.sql`: `AVISOS_RENOVACION_CONTRATO` (with RLS) and `UQ_AVISO_RENOV_PERIODO`.
- `V5.43__obras_inspeccion_final.sql`: OBRAS inspection columns and `CK_OBRAS_INSPECCION_RES`.

## Integration points

- Finance (F6): `finanzas/repository/ContratoProveedorRepository` is outside this slice and was not changed. It should do four things:
  - call `proveedores.contratos.ContratoProveedorValidator.validar(req)` in `crear`;
  - return 404 (`NoSuchElementException`) instead of a 403 whose message names the other organization;
  - require the session property in `listar`, which today lists every row RLS allows when there is no property;
  - validate the target state in `actualizarEstado`.
- Scheduler/automation (F12): call `PlanMantenimientoService.generarOrdenesProgramadas` and `AvisoRenovacionService.generarAvisos` per property, inside that property's admin context. Both are idempotent.

## Open items

- PQRS: there is no proactive at-risk alert job yet (the status is computed on read). Existing `PQRS_SLA_CONFIGURACION` rows that still hold the old 12/24/72/120 h values are not migrated. Resident priority choice (F9-07) is still accepted.
- To verify under Oracle: whether OBRAS RLS (per unit) lets PORTERO read works for the gate check; whether `FN_FILTRO_PROPIEDAD` covers the new tables generically; whether residents can see `CONTRATOS_PROVEEDOR`/`OBRA_TRABAJADORES` rows in the worker scope query.
- Supplier controller: foreign ids still return 403, not 404 (the brief allows either).
- Not done: F9-05 (INCIDENTES module seed), F9-06 (`@RequireModule` for assets/maintenance/suppliers), F9-09 (evidence URL validation), F9-13 (PQRS admin UI).
