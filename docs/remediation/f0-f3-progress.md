# F0 (engineering/config/CI) and F3 (SaaS platform) remediation

Branch `fix/f0-f11-p0-remediation`, 2026-10-09. Writer: Claude (single writer for F0/F3). No database
connection, deployment, push or Docker use. Migration range used: **V5.33** only (V5.34–V5.39 unused).

## Commits

| Commit | Scope | Audit IDs |
|---|---|---|
| `31d4397` | Fail closed on missing or published production secrets | C-03, F00-03, F0-03, F0-04, F0-10 |
| `2a98e1a` | CI tiers: every database-free test class on all pushes and PRs | F00-02, F0-01, F0-02, F0-06, F0-07 |
| `c074514` | Remove SUPERADMIN grants from operational endpoints | F3-02, DEC-02 (fixes `AuditFixesSecurityIntegrationTest.superAdmin_cannotAccessTodasReservas`) |
| `46d78e8` | Module guard for finance, packages, parking, minutes; server-side org only | F3-07, F3-13 (entitlement fallback) |
| `938ca74` | Org admins confined to caller's organization; activation links | F3-03, C-05 remainder, F3-06 |
| `0e7169a` | Onboarding: server-decided trials, no purges, activation links, paid-intention recovery, default template; V5.33 | F3-04, F3-05, F3-06, F3-08, F3-09 (column), F3-12, DEC-06, DEC-09 |
| `96fcb1b` | Plan-limit locking verified; property re-activation consumes a slot | F03-02, F3-11 |

## Behavior changes

- **Profiles.** Unset `SPRING_PROFILES_ACTIVE` now means `prod` (was `dev`). Local runs must set `dev`.
  `ProductionConfigurationGuard` refuses to start any production-like profile (`prod`, or none of
  `dev`/`test`/`isolated`) without `JWT_SECRET` (≥32 bytes, not the repo dev secret), `DB_URL/USERNAME/PASSWORD`,
  `WOMPI_ENVIRONMENT` (`SANDBOX`|`LIVE`) with matching key prefixes, `CORS_ALLOWED_ORIGINS` (no `*`) and
  `APP_FRONTEND_URL` (not localhost). Repository-published values (dev JWT, public sandbox keys) are rejected
  by SHA-256 digest. Messages name variables only. `render.yaml` declares all of them by name.
- **Wompi** keys come only from `saed.wompi.*`; no hardcoded/`getenv` fallback; webhook checksum compared in
  constant time; paid onboarding answers 503 when keys are missing.
- **CORS** has no wildcard default; without configuration no cross-origin request is allowed.
- **SUPERADMIN** loses 84 grants on operational controllers (assemblies, minutes, payments, peace-and-safe,
  contracts/templates, mailbox, documents, reports, regulations, automations, reservations, units, blocks,
  property settings, assignments, org dashboard). Platform endpoints unchanged. Frontend routes `asambleas`
  and `empleados` no longer list SUPERADMIN.
- **Plan modules.** FINANZAS, PAQUETES, PARQUEADEROS and assembly minutes are now gated: suspended/expired
  memberships and plans without the module (FREE in the V5.13 matrix) get 403 there. Webhook, certificate
  verification and payment-status polling stay open. Module checks ignore `X-Organization-Id`,
  `X-Property-Id`, `X-Assignment-Id`, `idOrganizacion` and the "first active org" fallback.
- **Org admins.** No bootstrap context; existing accounts are reused only if they already belong to the org
  and are not privileged; other identities → 409; new accounts `PENDIENTE_VERIFICACION` + activation link;
  password requests send a reset link; identity edits only for accounts exclusive to the org.
- **Onboarding.** `esPrueba` ignored. Free (price 0), trial (`PLANES.PERMITE_PRUEBA='S'`, once per NIT/e-mail;
  all plans start `N`) or paid. No purge/overwrite; any NIT/e-mail/username/document collision → 409
  (inactive orgs are no longer purged and reused). Paid-but-unprovisionable payments → `PAGO_REQUIERE_REVISION`.
  Only `PENDIENTE` can be rejected; paid/provisioned/still-payable intentions cannot be deleted; manual approval
  provisions `PENDIENTE`/`PAGO_REQUIERE_REVISION` without marking a gateway transaction approved. No password
  is generated, set or e-mailed anywhere in onboarding, credential resend/update or membership payment.
  Each provisioned organization gets the default contract template (DEC-09).
- **CI.** `-Dsaed.test.tier=unit|database|all` (`TestTierFilter`, a JUnit `PostDiscoveryFilter`). The Oracle
  tier and OWASP are separate jobs, *skipped* (not passed) unless repo variables `SAED_ORACLE_CI` /
  `SAED_OWASP_CI` are `true`, and then fail without secrets. CI runs on every push and PR. Frontend uses
  `--frozen-lockfile`.

## Migration

`V5.33__onboarding_trial_eligibility_and_recovery_state.sql`: `PLANES.PERMITE_PRUEBA CHAR(1) DEFAULT 'N'`
(+check); `ONBOARDING_INTENCIONES.TIPO_FLUJO, IDENTIFICACION_FISCAL, EMAIL_ADMIN, ID_PLAN, MOTIVO_ESTADO`
with JSON_VALUE backfill; `CK_ONB_INTENCIONES_ESTADO` / `_TIPO` (`ENABLE NOVALIDATE`); two lookup indexes
(tolerate only ORA-01408). Starts with the bootstrap block; constant defaults only; no MERGE. **Not applied
to any database** — needs coordinator Oracle verification.

## TDD evidence

| Test class | RED | GREEN |
|---|---|---|
| ProductionConfigurationSafetyTest | compile RED (17); +1 rule RED 2 failures | 18/18 |
| TestTierFilterTest | compile RED | 6/6 |
| H04SuperAdminResidual… (repaired slice) | 46 errors → 7 DEC-02 failures | 46/46 |
| SuperAdminOperationalAccessTest | 1 failure (84 grants) | 2/2 |
| AuditFixesSecurityIntegrationTest | 1 failure (Reservas) | 18/18 |
| PorteroPasswordChangeWebMvcSecurityTest (repaired) | 9 errors | 9/9 |
| ModuleGuardCoverageTest / ModuleEntitlementResolutionTest | 1 / 4 failures | 2/2, 7/7 |
| OrgAdminAccountIsolationTest / OrgAdminsControllerSafetyTest | compile RED / 3 failures | 9/9, 3/3 |
| OnboardingProvisioningIntegrityTest / OnboardingAdminRecoveryTest | compile RED | 13/13, 7/7 |
| PlanLimitLockingTest | 1 failure (re-activation) | 7/7 |

Final run (`rm -rf target/surefire-reports`; `mvnw -o compile checkstyle:check test -Dsaed.test.tier=unit`,
unroutable `DB_URL`): **52 classes, 446 tests, 0 failures, 0 errors, 0 skipped**; Checkstyle passed.
Frontend: `npm run lint` 0 errors (342 warnings, unchanged), `npm run build` OK.

## Open items

- Database tier not run (no DB access). `@SpringBootTest` classes without `@ActiveProfiles` need
  `SPRING_PROFILES_ACTIVE=test` (set in the CI DB job). DB tests that relied on `X-Organization-Id` /
  `@WithMockUser` org fallback (e.g. `ModuleEntitlementsSecurityTest`) or on SUPERADMIN operational access may
  need a server-side context; Oracle concurrency proof of plan limits (two sessions) remains pending.
- V5.33 must be applied/verified on the isolated Oracle harness.
- Defense in depth: RLS functions still return `1=1` for SUPERADMIN (F3-02 DB side) — needs a reviewed
  `PKG_SAED_SECURITY_RLS` migration.
- Out of my packages: SUPERADMIN grants in `ActivoController`, `MantenimientoController`,
  `ProveedorController` (Codex packages); `DashboardController` visitor registration and
  `PorteriaController` still elevate to SUPERADMIN context (F7). Service-layer `"SUPERADMIN"` branches in
  reservations/assemblies/documents/regulations are now unreachable via HTTP; cleanup pending.
- Not addressed: rate limit/captcha on public onboarding (F3-04 remainder), membership expiry job and
  downgrade validation (F3-07/F3-10), `PlatformAdminsController` SOPORTE level (F3-13), `MultasController`
  module mapping, `PlatformOnboardingAdminServiceImpl` context helper still swallows context-set errors.
- Platform admins must enable `PERMITE_PRUEBA` per plan to offer trials; FREE-plan tenants lose finance,
  packages and parking per the V5.13 matrix (product confirmation advisable).
