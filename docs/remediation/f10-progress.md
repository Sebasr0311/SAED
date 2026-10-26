# F10 Governance Remediation - Progress

Date: 2026-10-09. Branch `fix/f10-governance` (merged `fix/f0-f11-p0-remediation` at 8d7a112, no conflicts).
Status: unit-verified; Oracle integration not run (coordinator).

## Fixed

| Area | Audit / plan IDs | Change |
|---|---|---|
| Voting (DEC-03 slice 2) | C-10, F10-01, F10-02, F10-10 (partial), F10-11 | Vote only while assembly `EN_CURSO` and voting `ABIERTA` (`CONVOCADA` rejected). Voter must be active legal owner (`PROPIETARIOS_UNIDAD`) or `APROBADO` proxy; no admin bypass; `idPersonaVotante` always the caller's person. Unit needs active attendance. Missing coefficient rejects vote and attendance (0.01 fallback and BOOTSTRAP elevation removed). Attendance weight server-derived; attendee must be owner or holder of an approved power for that unit. Withdrawal without active attendance -> 404; quorum recomputed on withdrawal with a single fraction scale (`QuorumCalculator`). |
| Sanctions | F10-04, F10-09, F10-15, F10-22; plan F10-02 | `GET /sanciones/{id}`: property admins, the accused, and active owners/residents of the unit only (others 404; SUPERADMIN/PORTERO denied). No resolution of closed files or before the discharge term without a defense. Resolution claimed with a conditional `UPDATE ... WHERE ESTADO IN (open)`; effects run only after the claim; existing fine per sanction is never duplicated (also DB unique index). Defense only by participants within the term. Charge sheet requires session property, unit in property, accused linked to unit (no `1L` fallback). E-mail HTML escaped. |
| Fine -> receivables | F10-06 | Integration point `sanciones.integration.FineReceivablesPort` called once per new fine; returned receivable id linked in `MULTAS.ID_CUOTA`. No implementation shipped (finance owns it). |
| Common-area restriction | F10-07 | Period persisted (`RESTRICCION_DESDE/HASTA`), required for `SUSPENSION_ZONAS_COMUNES`; `SancionService.hasActiveCommonAreaRestriction(unitId, date)` for reservations. |
| Documents | F10-05, F10-14; plan F10-03 | `POST /documentos` rejects `archivoUrl`, tenant ids from session only. Uploads stored under `documentos/org-{id}`; downloads refuse files outside the document's partition (legacy flat `documentos/` files still served). Residents: only active public documents, no internal path. SUPERADMIN denied (DEC-02). Failed or rolled-back metadata write deletes the stored file. Versions append-only. Repository fallbacks that masked errors removed. |
| Insurance | F10-08, F10-17 (partial); plan F10-04 | `PolizaVencimientoService.detectarVencimientos(propertyId, date)` requires matching tenant context, one alert per policy and expiry period (unique index). Scheduler runs per tenant from `PolizaTenantSource` in its own scope; no global query. Missing policy read/update/delete -> 404. |
| Emergencies | F10-20; plan F10-04 | Reads open to admins, porter, resident, owner, unit owner and cohabitant; writes admin only; not found -> 404. |
| Frontend | F10-01/02, F10-07 | Admin vote-on-behalf modal removed; attendance coefficient input removed, proxy attendance requires an approved power; resolution form asks for suspension end date. |

## Evidence (RED -> GREEN, fresh surefire reports)

- VotingEligibilityServiceTest + QuorumCalculatorTest: RED 20 tests / 14 failures -> GREEN 20/0 (VoteIntegrityServiceTest adapted, 13/0).
- SancionLifecycleServiceTest: RED 26/21 -> GREEN 26/0.
- DocumentoIntegrityServiceTest: RED 13/10 -> GREEN 13/0.
- PolizaVencimientoServiceTest: RED 7/5 -> GREEN 7/0.
- EmergencyInformationAccessTest: RED 3/2 -> GREEN 3/0.
- PolizaSeguroNotFoundTest: RED 3/3 -> GREEN 3/0.
- After merge, `-Dsaed.test.tier=unit` with compile + checkstyle: 61 classes, 521 tests, 0 errors, 0 failures, 0 skipped (includes the CI selection, DatabaseClockConsistencyTest, StartupDdlGuardTest, SuperAdminOperationalAccessTest, ModuleGuardCoverageTest).
- Frontend: eslint on both changed pages 0 errors (3 pre-existing warnings), run through the main checkout's eslint because this worktree has no `node_modules`; `npm run build` not run for the same reason.

## Migrations

- `V5.70__sanciones_restriccion_y_multa_unica.sql`: SANCIONES restriction columns + check; `UQ_MULTAS_SANCION_ORIGEN` (aborts with ORA-20970 if duplicate fines already exist).
- `V5.71__alertas_poliza_periodo_unico.sql`: `ALERTAS_ADMIN.PERIODO_REFERENCIA` + function-based unique index.

## Integration points

- Finance (F6): implement `FineReceivablesPort` (idempotent per `idMulta`, same transaction, and avoid double collection with the MULTAS/Wompi path).
- Reservations: call `SancionService.hasActiveCommonAreaRestriction(unitId, date)` when creating a reservation.
- Platform: implement `PolizaTenantSource` (audited tenant enumeration + acting admin user); until then the scheduler only logs a warning.

## Open items

- Oracle integration suites (`F10Votaciones/F10Asambleas/DebidoProceso/Polizas/F10Documentos`) still assume CONVOCADA voting, admin voting, client coefficients and resolution before term; fixtures need owners, attendance and EN_CURSO. Only the policy expiry test was updated.
- F10-03 VOTOS RLS predicate, F10-12 (closing result/majority, annul closed voting), F10-13 proxy voting UI for residents, F10-16 expediente numbering race, remaining F10-10 power checks (`decidirPoder` ignores path assembly), F10-17 resident policy field minimization, F10-19 fixed audit IP.
- Without the UNIDADES BOOTSTRAP fallback, a proxy whose RLS scope hides the represented unit cannot vote (fails closed); verify under Oracle.
- Legacy flat `documentos/` files remain readable by any document of the same tenant scope that references them.

## P0 backend remediation (health, insurance job, activation tokens)
- Health: `/api/v1/health` probes `SELECT 1 FROM DUAL` (3 s bound, no details, 503 when down); `/ping` stays a cheap liveness answer. Unit tier `HealthControllerTest` (RED compile failure, GREEN 5).
- Insurance expiry job (F10-08): `PlatformPolizaTenantSource` enumerates properties with non-cancelled policies and an active property administrator (ids only, platform context set and restored/cleared); the scheduler runs each tenant in its own `ADMIN_PROPIEDAD` property context, contains per-tenant and enumeration failures, and is switchable with `saed.seguros.vencimiento-enabled` (default true). Unit RED compile failure / GREEN 9; DB tier `PolizaVencimientoJobIntegrationTest` RED 1 failure (bean disabled) / GREEN 1 on recovery-30.
- V5.72: `GASTOS_SOPORTES_HISTORIAL` gets `ID_PROPIEDAD` (backfill, NOT NULL, trigger, index) + `FN_FILTRO_PROPIEDAD` policy; `TOKENS_ACTIVACION` gets a `FN_FILTRO_USUARIOS` policy; grants for the security principal; `EVENTOS_SISTEMA` documented as a global catalog without policy.
- V5.73 (`__sec_`): `SP_EMITIR_TOKEN_ACTIVACION`, `SP_CONSULTAR_TOKEN_ACTIVACION`, `SP_ACTIVAR_CUENTA`, `SP_REENVIAR_TOKEN_ACTIVACION`. Finding: the unauthenticated activation had no session context, so under the USUARIOS policy the password update could not reach any row; the procedures fix that and stop a token from reactivating blocked/inactive accounts.
- DEC-13: no password by e-mail or response; activation link from `app.frontend.url`. `correo_credenciales_creado_por_usuario.html` and `EmailService.enviarCredencialesCreadoPorUsuario*` removed.
- Open: frontend still reads `passwordGenerada` (EmpleadosPage, ResidentesPage, ConvivientesSection, SuperAdminOnboardingPage) and tests/e2e/25-registration-contract.spec.js; `correo_bienvenida_credenciales.html` (onboarding) still renders a password; `backend/.env.example` needs `APP_FRONTEND_URL=` (edit blocked by a permission rule).
