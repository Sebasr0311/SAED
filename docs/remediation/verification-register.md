# Track F0–F11 closure with candidate-bound evidence

All tasks are pending unless specifically recorded below. This register accompanies [the production-readiness plan](F0-F11-production-readiness-plan.md); it is not a production approval.

## Evidence conventions

| Field | Required content |
|---|---|
| Requirement | Master section/line or academic RF/RNF ID; approved decisions where applicable |
| Classification | Requirement, independently confirmed defect, candidate needing reproduction, decision or verification |
| Reproduction | Preconditions, safe fixture and observed failure; candidate dismissal requires counter-evidence |
| Correction | Exact source/schema change, commit or uncommitted candidate identity |
| Proof | Command/tool, environment, date, test counts/outcome and artifact reference |
| Isolation | Evidence target is synthetic/disposable and cannot reference production |
| Review | Independent review result and outstanding risk |
| Closure | Requirement satisfied and regression passed; skipped/unavailable checks cannot close task |

Never capture secret values, raw credentials, privileged PIN/OTP or private transcripts. Repository documents do not replace unavailable session-attributed Engram; no memory calls were made during this mapping.

## Baseline and current safety constraints

- Baseline: `7d26e89ff1c985d5132a55f229e35443bfb5d2b8`, 2026-10-01. Baseline CI 89 selected tests passed; Oracle integration/dependency scans skipped. It does not prove this modified build.
- Preserved pre-existing changes: `.atl/.skill-registry.cache.json` and `.atl/skill-registry.md`.
- Current source modifications: `DatabaseSeeder.java`, `ProductionSchemaInitializer.java`, `PlatformDashboardController.java`; new `StartupSafetyTest.java`. See startup progress for exact paths and checks.
- Docker Desktop recovered. The original isolated apply failed at bootstrap ORA-00972; its distinct recovery-01 then completed bootstrap and failed at 010-structure.sql because text-mode subprocess input used Windows cp1252. Both protected states/locks remain preserved without replay/reset. Read-only diagnosis now confirms a partial bootstrap, not the prior empty footprint. The old container was normally stopped and preserved; a separate fresh immutable-image target is running at exclusive loopback 11521 with no mounts, but SQL readiness is unproved. Local OracleXE18 on 1521 remains excluded. Default dev/test profiles are unsafe for indiscriminate integration execution. Java unit slices did not connect to a database.
- `ScriptRunnerTest` can execute unsafe scripts and is excluded until an explicitly isolated runner is established. Do not launch the entire integration suite with default settings.
- TestSpriteCLI/auth available, but no safe reachable target serves the changed build. Existing deployment would not validate current changes. External verification is pending.
- The two protected legacy runners no longer repair schema by default. Other @PostConstruct/ordinary-operation DDL remains (PlantillaContratoRepositoryImpl and OnboardingServiceImpl); migrate it before certifying startup. Fresh Oracle setup needs controlled migrations/account provisioning.

## Task status

Status values: **pending**, **in progress**, **implemented; proof pending**, **verified scoped**, **closed**. Scoped unit success alone does not close production behavior.

| Phase | IDs | Status / evidence |
|---|---|---|
| Foundations | G00 | In progress: requirements/backlog persisted; full implementation-to-test traceability still pending. |
| Foundations | G01 | Implemented; proof pending. Source startup guards/removal implemented;9 offline tests passed; isolated Oracle two-start/data-preservation and external verification pending. |
| Foundations | G02 | In progress: UTF-8 transport correction and explicitly authorized immutable-target rebind pass 84 local guard cases; fresh independent review and recovery-02 preparation/apply remain pending. Original ORA-00972 and recovery-01 Unicode failure retain 41 files each, including blocked states/locks. Latest read-only diagnosis shows only partial bootstrap; actual installation and RLS/HTTP isolation remain unverified. See oracle-isolation-progress.md. |
| Foundations | G03,G04 | Pending restore proof and finalized authorization matrix. |
| F0 | F00-01,F00-02,F00-03,F00-04,F00-05 | Pending. Offline startup build/checkstyle is scoped evidence, not full engineering closure. |
| F1 | F01-01,F01-02,F01-03,F01-04 | Pending. No deployed/catalog/fresh-install proof yet. |
| F2 | F02-01 | In progress. Registration target authorization and existing-identity preservation implemented; final corrected 17-case scoped suite plus baseline = 130 passing tests, three local Chromium component/HTTP-mock cases. Independent review corroborates credential fix; Oracle/RLS/concurrency and complete lifecycle proof pending. See identity-authorization-progress.md. |
| F2 | F02-02,F02-03 | Implemented; scoped Java proof: authoritative profile scope retained without assignment DML, whole-request JWT cleanup and explicit-assignment fail-closed behavior. RED 28 cases/19 expected failures; final 37 new cases plus baseline = 167 passing tests/22 classes. Oracle default/date validity, bootstrap predicate, pooled context and full HTTP matrix remain pending. See identity-context-progress.md. |
| F2 | F02-04,F02-05 | Pending. Runtime exception policy deletion addressed by the scoped F02 exception-safety slice below; remaining account/resource authorization and full profile matrix are not closed. |
| F3 | F03-01,F03-02,F03-03 | Pending. Startup purge/account behavior addressed under G01 only. |
| F4 | F04-01,F04-02,F04-03 | Pending. |
| F5 | F05-01,F05-02,F05-03 | Pending. |
| F6 | F06-01,F06-02,F06-03,F06-04,F06-05,F06-06 | Pending. Financial integrity follows security/schema prerequisite closure. |
| F7 | F07-01,F07-02,F07-03 | Pending. |
| F8 | F08-01,F08-02,F08-03,F08-04 | Pending. |
| F9 | F09-01,F09-02,F09-03,F09-04,F09-05 | Pending. |
| F10 | F10-01,F10-02,F10-03,F10-04 | Pending. |
| F11 | F11-01,F11-02,F11-03,F11-04 | Pending. |
| Decisions | D01–D09 | D01 six profiles fixed by user/master; other contract details remain to resolve or verify when their slice starts. |
| Final release | Integrated release gate | Pending; F12 remains deferred. |

## Recorded scoped proof

| Evidence ID | Task / candidate | Result and limit |
|---|---|---|
| E-G01-RED | StartupSafetyTest before correction |5 tests,4 failures,0 errors; reproduced unsafe startup/purge/credential/policy expectations with mocked JDBC. |
| E-G01-GREEN | Uncommitted startup-safety candidate,2026-10-08 | Microsoft JDK 17.0.19/Maven 3.9.9 offline; `mvn -o clean -Dtest=StartupSafetyTest "-DargLine=-Dnet.bytebuddy.experimental=true -Xshare:off" test checkstyle:check`;9 tests,0 failures/errors/skips, build success. Datasource points at unavailable loopback port 1; limited ApplicationContextRunner, no app entrypoint or real Oracle. |
| E-G01-STYLE | Same scoped candidate | Checkstyle passes configured 4500-warning allowance with 3899 existing warnings; `git diff --check` passed. Not zero-warning certification. |
| E-G01-LIMIT | Startup safety progress readback | Default/prod runners excluded; explicit legacy nonprod maintenance still able to rebuild policies. Migration replacement, Oracle preservation and production provisioning unverified. |

Source for these recorded results: [startup-safety-progress.md](startup-safety-progress.md). No application start, deployment, migration, commit, push, production connection or credential rotation occurred in this block.

## Additional source corroboration during requirement mapping

These are source observations, not executed regression results:

- `TicketServiceImpl.java:212–217` defaults 12/24/72/120h, contradicting master 353–356's1/4/24/72h; F09-05.
- `IncidentesAdminPage.jsx:204` calls POST`/{id}/reabrir`; `IncidenteController` has no corresponding mapping; F09-04.
- `AsambleaServiceImpl.java:452–515` accepts CONVOCADA and client-provided positive coefficient; attendance/quorum guard absent in that method. F10-01 must settle approved lifecycle and test whole path.
- `SancionServiceImpl.java:143–178` lacks previous-state/deadline guard before fine creation. F10-02 must test repository/constraint behavior before claiming duplicated deployed fines.
- Wompi/PazYSalvo/Cartera/FlujoCaja predicates omit PAGADA_PARCIAL in places while financial repository/Analytics include it;F06-03 requires cross-view golden fixtures.

## Next action

Continue the bounded security correction with failing tests. In parallel as permitted by available review tooling, prepare safe isolated integration infrastructure. Never treat local XE availability or existing deployment as permission to run destructive tests there.

## G01 independent review correction

Review found residual user/person and contract-template DELETE statements in the explicitly enabled ProductionSchemaInitializer. These blocks were removed in a corrective pass. New mocked-JDBC coverage supplies pre-existing table/catalog counts so template cleanup cannot be hidden by null mock results. Review RED: 10 tests, 1 expected failure, 0 errors. Review GREEN: 10 tests, 0 failures/errors/skips; clean compilation and Checkstyle succeeded (3,895 warnings under existing 4,500 threshold), 2026-10-08 15:14:57 America/Bogota.

The safety guarantee covers the two guarded legacy runners and removed purge API only. It does not certify whole-application non-mutating startup: PlantillaContratoRepositoryImpl @PostConstruct/ensureTableExists and OnboardingServiceImpl @PostConstruct still execute runtime DDL and remain G01/F01 migration work. Oracle preservation and TestSprite on a safe changed-build target remain pending. No production closure is granted.

## F02 scoped exception safety evidence

| Evidence | Result and limit |
|---|---|
| E-F02-EX-RED | Synthetic ExceptionSafetyTest: 8 tests, 5 expected failures, 0 errors; reproduced unsafe diagnostics/policy repair, raw response/log leakage and canonical-contract violations. |
| E-F02-EX-GREEN | Initial candidate before cause-chain review correction. Offline JDK17/Maven3.9.9 clean build: 10 ExceptionSafetyTest plus 10 StartupSafetyTest, 20 total, 0 failures/errors/skips. Checkstyle succeeds with 3,895 warnings under configured 4,500 allowance. |
| E-F02-EX-CONTRACT | HTTP 500 + DATABASE_ERROR for DataAccess policy failure; transaction failure preserves INTERNAL_SERVER_ERROR; no diagnostic/repair JDBC. Existing six-key error envelope and correlation header/body retained; conflict 409 mappings remain. |
| E-F02-EX-LIMIT | Covers persistence/transaction/general fallback errors only. Other domain exception/audit sanitization, Oracle integration, authorization matrix and safe changed-build TestSprite target remain pending. Not full F02 or production closure. |

No app startup, real Oracle connection, production testing, deployment, migration, commit or push. Source/proof hashes and commands are in [exception-safety-progress.md](exception-safety-progress.md). Independent review remains required before accepting this scoped candidate.

### F02 cause-chain corrective evidence

| Evidence | Result and limit |
|---|---|
| E-F02-CAUSE-RED | 14 synthetic exception cases, 4 expected failures, 0 errors; intermediate policy failures, cyclic and excessive-depth chains previously returned audited 403 instead of non-mutating 500. |
| E-F02-CAUSE-GREEN | 24 scoped exception/startup cases, zero failures/errors/skips; identity-cycle protected 16-node traversal fails closed for unreadable/cyclic/truncated chains before audit JDBC. |
| E-F02-CI-FINAL | Final exact 19 classes: 89 current CI baseline cases plus 24 new cases = 113, zero failures/errors/skips; offline JDK17/Maven3.9.9 clean compilation and Checkstyle success, 2026-10-08 15:28:06 America/Bogota. No integration suite, Oracle connection or application entrypoint. |

The updated source/test SHA-256, exact selection/commands and sanitized evidence location are in exception-safety-progress.md. Prior 20-case proof refers to the pre-review candidate; the 113-case proof binds the corrected final bytes. Oracle and external TestSprite verification remain pending; no F02/production closure.

## F02-01 registration authorization evidence

| Evidence | Result and limit |
|---|---|
| E-F02-REG-RED | Six synthetic registration authorization cases failed against the old implementation, zero errors/skips. No database connection or application start. |
| E-F02-REG-GREEN | Expanded 14-case mock/standalone HTTP suite passed, zero failures/errors/skips. Protects scoped target selection and preserves active existing identity/credentials/assignments; generic inactive-account reactivation denied. |
| E-F02-REG-FINAL | Initial pre-review candidate: offline clean compilation and Checkstyle passed with 127 tests/20 classes, zero failures/errors/skips, 2026-10-08 16:24:55 America/Bogota. Superseded by corrected evidence below. |
| E-F02-REG-REVIEW | Credential overwrite correction corroborated by independent source review. HIGH legacy-page regression claim dismissed: App.jsx redirects /usuarios to /residentes; UsuariosPage is unreachable orphan code. |
| E-F02-REG-CORRECTION | Explicit accountCreated response, compatible messages/modal in three live callers, three additional positive backend cases and style cleanup. Backend RED 17 cases/2 expected failures; final GREEN 130 tests/20 classes, zero failures/errors/skips, 2026-10-08 16:36:14 America/Bogota. Clean build and Checkstyle pass: 3,875 warnings, no new policy warnings or allowance increase. |
| E-F02-REG-BROWSER | RED reused identity incorrectly opened credential modal. Corrected local Chromium suite: 3 passed, synthetic HTTP only; existing/new identity and denied registration. Frontend build passes; lint 0 errors/342 warnings. No backend launched. |
| E-F02-REG-LIMIT | Isolated Oracle, actual RLS visibility, concurrency, complete six-profile HTTP security chain and TestSprite on changed build remain unverified. Full live-page journeys remain pending; orphan UsuariosPage cleanup belongs to F0. |

Exact paths, hashes and remaining limitations: [identity-authorization-progress.md](identity-authorization-progress.md). No F02-01 or production closure is granted.

Historical infrastructure diagnosis: Docker Desktop initially failed before engine startup on its `sailor-ingest.sock` pipe. After the user's restart, a disposable isolated Oracle container was created. The first apply exposed ORA-00972; recovery-01 subsequently exposed Windows-ANSI transport failure after bootstrap. The corrected transport/new-target binding now awaits fresh review, not a replay into the partially installed instance. Registration/context Java unit slices performed no Oracle operation. Production Oracle version still needs authoritative confirmation; the isolated integration step must preserve the local XE database.

## G02 isolated Oracle preparation evidence

| Evidence | Result and limit |
|---|---|
| E-G02-GUARDS | 2026-10-08: 39 Python preparation guard/extraction checks pass, including non-object manifest rejection; no Docker/DB process in those tests. Rejects wrong ID/name/image/labels, port 1521/external interfaces/additional published ports and any mount. Preparation was independently accepted after the 38-case inventory correction. |
| E-G02-PREPARE | 37 private SQL artifacts prepared from V5.0 and V5.1–V5.29 with explicit compatibility normalizations; no fixed identity credentials, original identity seed or V5.30 purge. Six profiles, 2 organizations, 4 properties, 8 units, 11 generated accounts and one mixed-role user. No SQL executed. |
| E-G02-INTEGRITY | All 37 SQL/30 source checks pass. Empty/partial/extra inventory, altered path/digest/version and canonical SQL/fixture drift rejected following independent review. Generated state remains Git-ignored; directory and 39 files have protected current-user-only DACLs after effective readback. No secret/hash values recorded here. |
| E-G02-CONTAINER | Exact live metadata guard passes for the disposable immutable-image, mount-free loopback 11521 container. Running is not readiness and no readiness SQL was run. |
| E-G02-EXECUTOR-CONSTRUCTION | Historical original constructor: 20 mocked executor plus 39 preparation cases, 59 total, zero failures/errors. Independent review accepted one bounded apply with limits. No SQL process was launched during that construction phase; the subsequent bootstrap failure and distinct recovery evidence are recorded below. |
| E-G02-APPLY-BLOCKED | First isolated apply reached bootstrap and failed with ORA-00972: the 48-byte password-generator setting exceeded Oracle 18's 30-byte bound. Blocked state and lock were preserved; no retry or harness edit by the Java writer. This execution supersedes the earlier construction-only/no-SQL status. |
| E-G02-READONLY-DIAGNOSIS | Independent guarded read-only diagnosis of the same owned container: XEPDB1; version 18.0.0.0.0; compatible 18.0.0; SAED_USERS=0; OBJECTS=0; CONTEXTS=0; errors=[]. This establishes an empty SAED footprint, not installation success or production-version parity. |
| E-G02-RECOVERY-CONSTRUCTION | Historical distinct `--state recovery-01` constructor: 30-ASCII/120-bit passwords, independent 48-hex marker, CONTROL NO AUTHENTICATION ACCOUNT LOCK, containment/reparse/ACL guards and same-session empty-footprint freshness. Its 73 local tests passed and fresh review authorized one preparation/check/apply; subsequent runtime failure is recorded below. Constructor proof did not certify SQL execution. |
| E-G02-FAILED-STATE-PRESERVATION | Before/after internal SHA-256 capture confirms all 41 original protected files have identical bytes; hashes/contents were not printed. Original credentials, manifest, blocked state and lock were not changed or reset. Successor ACL effectiveness remains pending reviewed preparation. |
| E-G02-RECOVERY01-FAILED | Recovery-01 completed bootstrap only, then 010-structure.sql raised UnicodeEncodeError on U+0081 under Windows cp1252 text-mode transport. The failed attempt/lock remain preserved; no encoding replacement, retry or reset. This is not a semantic SQL or installation PASS. |
| E-G02-RECOVERY01-DIAGNOSIS | Independent read-only XEPDB1 inspection: version 18.0.0.0.0, COMPATIBLE 18.0.0, AL32UTF8; OWNER/RUNTIME/SECURITY/CONTROL one account each; owner/runtime/security objects zero, CONTROL objects two; SAED_CTX one, RUN_GUARD row one; no diagnostic error codes. Original target is partially provisioned and not fresh. |
| E-G02-UTF8-RED | Existing 73-case baseline passed. Expanded 83 cases then produced 7 expected failures, zero errors, reproducing ANSI input and absent encoding/sanitization/principal guards. Additional SYS-only bootstrap test: 84 cases, 3 expected failing subcases, zero errors. |
| E-G02-UTF8-GREEN | Final 84 cases (48 preparation, 36 executor) pass, zero failures/errors, including one real synthetic local Python subprocess for U+0081/Spanish stdin/stdout/stderr roundtrip with ANSI default forced. Explicit UTF-8/strict, Linux SQLPlus AMERICAN_AMERICA.AL32UTF8, sanitized UnicodeError, no fallback/retry, exact principals and SYS-only bootstrap. Tests launch no Docker/Oracle. |
| E-G02-UTF8-PRESERVATION | Internal before/after comparisons confirm identical 41-file original root and 41-file recovery-01 inventories/bytes. No private contents/digests printed; source migrations, generated state, manifests and locks untouched. Prior Java 167-test/22-class proof remains unchanged, not rerun by this actor. |
| E-G02-RECOVERY02-TARGET | Separate runtime actor stopped/preserved the old container and created/started one new target with no SQL/host file writes. Exact new ID/run are fixed in the applier; same pinned image, scope isolated-fixture, 4 GiB/2 CPUs, restart=no, loopback 11521 only, zero mounts. Repeated 84-case local suite passes after binding; fresh review, preparation/DACL proof and SQL readiness/apply remain pending. Metadata/details and final source hashes are in oracle-isolation-progress.md. |
| E-G02-LIMIT | Original attempt failed before provisioning; recovery-01 completed bootstrap only. Structure/migrations/catalog, sealed runtime grants, startup, six-profile RLS/HTTP tests, two-start preservation and restore remain unverified. Backend launch remains outside this constructor; G02/F01 are not closed. |

Exact source paths, normalizations and the bounded next execution plan: [oracle-isolation-progress.md](oracle-isolation-progress.md). Independent review is required before the separate SQL-execution work unit. No production connection, deployment, commit, push or memory call occurred.

## F02-02/F02-03 assignment authority and request context evidence

| Evidence | Result and limit |
|---|---|
| E-F02-CONTEXT-RED | 28 mock cases against the old Java source: 19 expected failures, zero errors/skips. Reproduced assignment mutation/scope replacement and rejected/error request-context leakage or downstream continuation. |
| E-F02-CONTEXT-GREEN | First correction: 34 passing cases, zero failures/errors/skips. Expanded final new suite: 15 profile plus 22 JWT cases, including all six stored roles, context restoration, fail-closed explicit selection, sequential requests, sanitized logs and chain/writer failures. |
| E-F02-CONTEXT-FINAL | Final normalized source: offline JDK17/Maven3.9.9 clean compilation, 167 tests/22 classes, zero failures/errors/skips; Surefire XML readback 2026-10-08 20:59:37 America/Bogota. Includes the selected 130-case baseline. No application entrypoint or DB/provider connection. |
| E-F02-CONTEXT-STYLE | Checkstyle passes with 3,852 warnings versus previous 3,875; existing allowance 4,500 unchanged. Scoped diff check passes and new tests have no trailing whitespace. Original tracked bytes outside getUserProfile restored before final verification. |
| E-F02-CONTEXT-REVIEW | Independent reviewer accepted Java source and mock-test scope with no candidate-caused HIGH/MEDIUM findings; its four source/test identities match identity-context-progress.md. Root readback of 22 Surefire XML files confirms 167 tests, zero failures/errors/skips. This is no Oracle or production proof. |
| E-F02-CONTEXT-LIMIT | Isolated Oracle default/date/predicate/pool proof, frontend assignment-switch atomicity, full six-profile HTTP/CORS/security chain and TestSprite on the changed build remain pending. Java cleanup does not certify Oracle session reset. No F02 or production closure. |

Exact command, source anchors, SHA-256 identities and remaining scope: [identity-context-progress.md](identity-context-progress.md). Review mode remains OFF; no memory, SQL, harness/state/profile, registry or finance edits were performed by this writer.
