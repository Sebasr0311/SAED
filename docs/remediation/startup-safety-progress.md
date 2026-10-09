# Startup safety remediation

## Scope and status

Implemented first bounded F0-F11 remediation block, 2026-10-08. This block is not production certification. No application was started, no real Oracle connection was made, and no deployment, commit, push, credential rotation or migration was executed. Pre-existing `.atl` edits were preserved.

## Changes

- Removed the main tenant/demo purge implementations and the HTTP `POST /api/v1/platform/dashboard/purge-demo-seed` endpoint.
- Removed hardcoded privileged/demo account provisioning and account/assignment reactivation from the initializer.
- Removed credential construction, account provisioning and RLS policy deletion from DatabaseSeeder.
- Both legacy runners are disabled by default, require explicit independent flags, and are excluded whenever the `prod` profile is active (including `prod,dev`).
- Legacy schema routines remain available only for deliberate isolated non-production maintenance while versioned migrations are prepared. They can still rebuild security policies when explicitly enabled; never enable them against production data.
- Removed SQL-body logging in the demo-seeding failure helper.

## Verification

Runtime: process-scoped Microsoft JDK 17.0.19 and Maven 3.9.9, offline Maven `-o`. Datasource properties pointed at unavailable loopback port 1 with non-secret dummy values. Tests instantiate a limited ApplicationContextRunner and mock JDBC; they never invoke the Spring application entrypoint.

RED command: `mvn -o -Dtest=StartupSafetyTest "-DargLine=-Dnet.bytebuddy.experimental=true -Xshare:off" test`.

RED result: 5 tests, 4 failures, 0 errors. Existing production seeder exclusion passed; actual unsafe startup, purge API, credential and policy behavior failed the new assertions. Raw SQL diagnostics were discarded.

Final command: `mvn -o clean -Dtest=StartupSafetyTest "-DargLine=-Dnet.bytebuddy.experimental=true -Xshare:off" test checkstyle:check`.

Final result: 9 tests, 0 failures, 0 errors, 0 skipped; build success. Checkstyle gate passed with 3,899 existing warnings under its configured 4,500-warning allowance. This is not a clean-style certificate. `git diff --check` passed.

Coverage is limited to ProductionSchemaInitializer and DatabaseSeeder, not the entire application. Protected default runners absent/no SQL; explicit maintenance preserves business data and credentials; demo catalogs do not grant privileges or remove RLS; production flags cannot activate either runner; existing catalogs do not change identities; removed HTTP endpoint returns 404; purge methods no longer exist.

TestSprite CLI 0.10.0 is installed and auth status exits 0. External verification is blocked: no safe reachable target serves this changed build. Testing the existing deployed build would not verify these changes. No external run was launched.

## Residual work before production

1. Replace legacy runtime schema maintenance with reproducible versioned migrations and secure operator-only account provisioning. Existing accounts remain untouched; a fresh database does not get a privileged account on startup.
2. Validate against isolated Oracle, including production-profile startup with migrations already applied. The two protected runners no longer repair schema by default. Other runtime DDL still exists and must be migrated.
3. Remove runtime RLS deletion from GlobalExceptionHandler in the next security block; this change only removed startup/seeder policy mutation by default.
4. Demonstrate database/files backup and restoration; rotate previously exposed credentials through an authorized deployment procedure.
5. Complete requirements, security, functional and end-to-end verification through F11 before beginning F12.

## Independent review and corrective pass

Independent review found two residual destructive blocks in the explicitly enabled legacy initializer: deletion of users/persons for a fixed document and deletion of tenant contract templates outside organization 1. The initial test did not explore the existing-table path because mocked counts were null. The correction removes both blocks and adds a regression with existing table/catalog counts set to 1. This tests captured SQL effects without printing SQL or connecting to Oracle.

Review RED: 10 tests, 1 expected failure, 0 errors. Correction GREEN: 10 tests, 0 failures/errors/skips; clean compilation and Checkstyle succeeded (3,895 warnings under existing 4,500 threshold), 2026-10-08 15:14:57 America/Bogota.

Scope limitation confirmed: PlantillaContratoRepositoryImpl still runs CREATE/ALTER/RLS/schema-template maintenance at @PostConstruct and calls ensureTableExists during ordinary operations (original source lines 35-109, 266, 292 and 343). OnboardingServiceImpl @PostConstruct (60-89) still creates staging schema. These are G01/F01 migration blockers; this block does not claim whole-application zero JDBC or complete non-mutating startup.
