# Isolated Oracle verification progress

Status: recovery-01 completed bootstrap on 2026-10-09, then stopped at 010-structure.sql with a Windows ANSI UnicodeEncodeError. The original ORA-00972 attempt and recovery-01 both retain their private states and installation locks. Independent read-only inspection confirms a partial bootstrap, not a schema installation. The bounded UTF-8 transport correction passes 84 local guard tests; it has not been applied to Oracle. A fresh owned target exists for a separately reviewed recovery-02 attempt; its SQL readiness is unproved. G02/F01 remain in progress.

Scope: a disposable Oracle 18.4 container for compatibility verification. The existing Windows Oracle listener on port 1521 and all production databases are excluded.

The harness verifies the explicit full container ID, exact name, `saed.run` execution label, `saed.scope=isolated-fixture`, immutable image reference, exclusive loopback port 11521 and absence of all mounts. Historical diagnosis after ORA-00972 found zero SAED users/objects/contexts in the original container. Recovery-01 subsequently created bootstrap objects there, so that empty-footprint proof is superseded. The original partially installed container has been normally stopped and preserved, not reset or reused. The replacement target metadata and remaining boundaries are recorded below.

Preparation extracts structural objects, functional catalogs and the V5.1–V5.29 chain without the original security-user deletion, fixed credentials, identity bootstrap or V5.30 purge. It excludes V3/V4 and rollback scripts. Imported SQLPlus settings/EXIT directives are removed without stripping SQL UPDATE SET or PL/SQL loop EXIT statements.

## Persisted implementation

- `scripts/verification/isolated_oracle.py`: explicit successor-state preparation, exact container checks and prepared-source/artifact integrity checks only. No apply/init/SQL executor is exposed. Schema passwords use 30 ASCII hexadecimal characters; the separate marker remains 48 hexadecimal characters.
- `scripts/verification/test_isolated_oracle.py`: 48 scoped tests including explicit state selection, containment/reparse rejection, legacy preservation, Oracle password generation and manifest-state binding.
- `scripts/verification/apply_isolated_oracle.py`: explicit single-attempt apply for a bound container/run and successor, with ordered SQL stages. The reviewed recovery-01 source connected to Oracle but failed after bootstrap. The subsequent UTF-8 correction is local-test-only and has not been used for a database attempt.
- `scripts/verification/test_apply_isolated_oracle.py`: 36 executor guard checks, including 11 transport/principal regressions. Docker/SQL processes are mocked; one test launches only a synthetic local Python child to verify UTF-8 stdin/stdout/stderr.
- `scripts/verification/SyntheticPasswordHashes.java`: local BCrypt generation/verification helper. Password input and hash output are captured privately, never supplied on the command line or printed in task output. Uses existing JDK17 and Maven-cache dependencies; no download/install.
- `scripts/verification/ProtectIsolatedState.ps1`: workspace-bound, link-rejecting private directory/file DACL replacement and readback. Preserves ownership/SACL and grants only the current Windows user. It uses native Access-only security APIs, avoiding unavailable inherited PowerShell modules.
- `backend/src/test/resources/application-isolated.yml`: exact synthetic runtime datasource on 11521/XEPDB1, owner current schema, no environment-file import, SQL initialization disabled and both legacy startup runners explicitly disabled. Future launches must supply it through `spring.config.location` and clear inherited configuration overrides.
- `local/isolated-oracle/`: 41 original root-level files, including private artifacts, blocked execution state and lock. Its distinct `recovery-01/` directory now contains another 41 files, including its own prepared artifacts, blocked state and lock. Both inventories are Git-ignored and preserved. Do not print/copy fixture SQL, credential state or hash values into reports. Recovery-02 has not been prepared by the source correction actor.

## Reviewed-input normalizations

Preparation records each normalization in the private manifest; source migrations were not edited:

1. Complete the baseline's dangling `ALTER TRIGGER TRG_HISTREP_INMUTABLE` with COMPILE.
2. Extend the baseline role constraint for RESIDENTE_CONVIVENCIA before V5.10 inserts it.
3. Remove the SAED_APP qualifier from V5.3 for execution in the isolated owner.
4. Split authentication object grants, synonyms and package into separate owner/security-account stages. Authentication synonyms explicitly reference SAED_ISO_OWNER; cross-schema ANY creation is not needed.
5. Handle only Oracle's absent PUBLIC grant on authentication revocation; other errors must propagate in the future executor.

These transformations are candidate compatibility input, not proven successful migration execution or a production migration fix.

## Synthetic fixture

Generated input contains 2 organizations, 4 properties, 8 units and 11 accounts spanning all 6 profiles. An existing resident additionally administers a different property within the same organization to test role switching. Identities/emails are synthetic; login and database passwords are generated independently. No real tenant or production identity seed is included.

## Scoped evidence

| Check | Outcome and limit |
|---|---|
| Python guard/extraction suite | Historical recovery-01 constructor: 48 preparation plus 25 executor checks, 73 passing cases. Current UTF-8 source: 48 preparation plus 36 executor checks, 84 passing cases, zero failures/errors. Only mocks and one synthetic local Python child; no Docker/DB process. |
| Original prepare | 37 SQL artifacts prepared privately before the initial apply. Their overlength schema passwords are retained as forensic evidence and are not reused. No successor secrets/SQL artifacts were prepared by this writer. |
| Prepared integrity | Original and recovery-01 preparations preceded their respective bounded attempts. Empty/missing/extra inventories, changed source paths/digests/version, state selectors and altered canonical SQL remain rejected. Existing prepared states are not valid inputs to the changed transport candidate. No recovery-02 preparation occurred in this correction. |
| Live container guard/diagnosis | Historical ORA-00972 diagnosis found an empty footprint. The later recovery-01 diagnosis found four bootstrap accounts, two CONTROL objects, one context and one guard row; zero owner/runtime/security objects. The target is no longer fresh. Installation/catalog/RLS remain unverified. |
| Credential DACL | Original directory/generated-file protection was verified before apply. The successor helper is restricted to its explicitly selected directory and rejects ancestor/file reparse points; effective successor ACL readback is pending reviewed preparation. Original permissions were not modified in this correction. |
| Failed-attempt preservation | Internal before/after SHA-256 comparison during the UTF-8 correction confirms all 41 original root files and all 41 recovery-01 files retain identical bytes and inventories. Values/contents are not printed. Both manifests/private states/blocked states/locks remain intact. |
| Existing application source | Previous startup/identity/frontend changes preserved; this work unit did not compile/run the backend or change domain Java source. |

Commands: `python -B -m unittest discover -s scripts/verification -p 'test_*.py'`; PowerShell AST parse of the ACL helper; bounded `git diff --check`. Previous preparation/check commands used the original constructor. Recovery requires explicit `--state` and fresh review. No test result here closes a production requirement.

Independent review found that the initial integrity checker accepted empty/partial artifact/source inventories. The correction was independently accepted with 38 preparation cases; non-object rejection increased it to 39. The original 59-case executor was accepted for one bounded apply, which exposed the password-length defect. The new 73-case successor candidate received fresh independent source acceptance on 2026-10-09, limited to one guarded recovery-01 preparation/check/apply attempt. This is not successful database execution or production approval.

## Remaining clean-target successor execution — not performed by this correction

Independent root review precedes SQL application. The constructed executor implements the following boundaries, with database behavior still unverified:

1. Validate unchanged reviewed artifacts, explicit successor selector and exact Docker metadata again before each connection. Require an explicit apply operation; there is no default apply or reset/retry operation.
2. Connect exclusively inside the matched Docker container; select XEPDB1 explicitly and assert the PDB. Never discover/use the Windows listener on 1521 or read application .env.
3. Perform bounded readiness/freshness catalog SELECTs in the same XEPDB1 bootstrap session before DDL. Refuse existing SAED users, objects or context instead of dropping or replacing them.
4. Initialize one randomly marked isolated control schema, then bind every subsequent SQL stage to that marker and container identity. The initial marker creation must be a separately reviewed bootstrap step.
5. Provision separate installer owner, runtime and authentication accounts from private generated values. Runtime must have no DBA, ANY, EXEMPT ACCESS POLICY or schema DDL privileges. Authentication privilege needs must be explicitly scoped; never raise runtime privileges to hide failures.
6. Execute structure/catalog/fixture, separate authentication stages, baseline policies and ordered migrations with controlled context. Recheck all invalid objects, policy coverage and required columns. Abort on invalid objects or SQL/compile errors; capture raw output privately and expose only sanitized counts/error codes.
7. Backend launch is outside this constructor. Launch selected integration tests through a scrubbed environment and explicit isolated config only after catalog checks and startup-DDL remediation. Runtime USER_TABLES/metadata-dependent @PostConstruct DDL remains a known migration blocker, not a reason to grant CREATE/ANY rights.
8. Subsequent verification must demonstrate six-profile RLS/HTTP isolation, mixed-role switching, two-start preservation and restore into a second isolated destination.

Production Oracle version remains undecided. Fixture validity, migration compilation, Oracle catalog, least-privilege application startup, RLS, concurrency and restore are unverified. Construction proof cannot substitute for these results.

## Separate executor construction — historical recovery-01 scope

The executor exposes only `apply --state recovery-01 --container-id <reviewed full ID> --run-id <reviewed run>`. Preparation and integrity checks require the same explicit selector. The manifest, private state, candidate identity and execution state bind it. Preparation refuses an existing successor directory. The selector accepts only `recovery-` followed by 2–4 digits, resolves inside the fixed ignored workspace root and rejects traversal, absolute paths, junctions and ancestor reparse points. The ACL helper changes only selected-directory/file DACLs; the failed root-level attempt is preserved.

Every SQL subprocess first revalidates canonical prepared inputs and the bound source/config candidate, then exact Docker metadata. SQLPlus receives a clean child environment and credentials only through captured stdin; no application .env, inherited datasource override, credential argv or raw output is used.

Initial SYS bootstrap selects XEPDB1 and rejects existing SAED users/objects/context before creating CONTROL with `NO AUTHENTICATION ACCOUNT LOCK` and three separate password-authenticated accounts. The owner/runtime/security generator uses `token_hex(15)`: 30 ASCII characters from 120 random bits. The 48-character marker is independent and is never an authentication password. Oracle 18 documents both the [30-byte password maximum and schema-only accounts](https://docs.oracle.com/en/database/oracle/oracle-database/18/dbseg/configuring-authentication.html).

Every later owner/security/SYS session validates the PDB, principal and marker before its payload in that same connection. Structure/catalog/fixture stages precede baseline policy activation: the source extraction contains zero ADD_POLICY calls in structure/catalog and policy activation begins in the later baseline-policy fragment. Ordered split authentication stages, baseline policies and V5.1–V5.29 precede lifecycle fixture changes, runtime grants, catalog checks and installer-account sealing. Post-policy owner stages explicitly set bootstrap/SUPERADMIN context; schema ownership is not treated as a VPD bypass. The second organization supplies inactive resident and blocked conviviente cases; the first retains all six active profiles. Actual predicate/context behavior still requires Oracle verification.

Catalog checks reject invalid owner/security objects, missing baseline tables/report columns, insufficient enabled policies and excessive runtime system/role privileges. Successful sealing removes transient owner DDL/policy privileges and locks installer accounts; the locked authentication definer retains its explicitly scoped policy exemption. Runtime has only CREATE SESSION as a system privilege, application object grants and no DBA/ANY/EXEMPT or policy DDL grant.

An exclusive installation lock and candidate-bound execution state preserve partial/unknown outcomes as blocked. There is no automatic reset, recreate or retry. An already complete matching installation permits only marker/sealed-catalog readback. A failure before sealing can leave transient installer privileges in the disposable container; independent inspection is required and no cleanup SQL is run automatically.

Raw SQLPlus output remains captured in process memory. Only accepted numeric counts/completion markers and bounded Oracle error codes are exposed. Compilation warnings/errors, missing/duplicate completion, unknown output and process failures stop the stage. Recovery-01 reached Oracle through the pinned Linux-engine pipe but stopped before completing structure. Fresh-install compatibility, complete catalog and actual grant/RLS behavior remain pending a separately reviewed clean-target attempt. Backend launch remains out of scope.

## Initial apply defect and bounded successor route

The first apply exited 1 with bootstrap ORA-00972 and zero completed stages. All original schema passwords and the marker were generated as 48-byte strings; the first CONTROL CREATE USER incorrectly reused that marker as its password, exceeding Oracle 18's documented limit. Independent read-only proof at that historical checkpoint found no SAED user/object/context, permitting one distinct reviewed successor subject to a repeated empty-footprint guard. Recovery-01 later changed that footprint; the old container is no longer eligible for another ordinary successor. No reset, account drop or automatic retry is authorized.

The historical fresh review authorized preparation/check and one explicit `recovery-01` apply with the then-reviewed container/run identities. That attempt is now blocked and preserved, not pending or reusable. Production-version parity, migrations/catalog/grants, six-profile HTTP/RLS, startup-DDL removal, preservation and restore remain pending.

## 2026-10-09 historical Docker continuation checkpoint

This pre-recovery-01 checkpoint is retained as history and superseded by the transport-failure checkpoint below; its next-step instruction is not the current execution route.

- Goal remains functional, verified F0-F11 readiness before F12; AI remains deferred.
- Fresh review returned `accepted_one_successor_prepare_apply_with_limits`. It inspected the five harness/helper/test files, isolated profile and this report without executing tests, Docker or SQL. The prior usage-limited review did not supply terminal acceptance.
- Review source SHA-256: preparer `9c99988917f4eeb4fff3142a9398712511ec4cce99690799c41c2b136ef00c86`; executor `78d33a387197b2f7eb193da2600b2ad7ed7085dbc408c5f2869df409691367c2`; ACL helper `4a98c9bd211e3f812ad36fa1890a1829e287343ce8196137471c4a58b868c1c8`.
- Docker CLI is available. A single normal `docker desktop start --timeout 90` timed out; subsequent API readback remained unavailable. No reset, recreation, unrelated-container operation, configuration change, SQL or successor preparation occurred.
- Next step: the user opens Docker Desktop normally and waits for the Linux engine. Then revalidate the existing exact owned container and unchanged reviewed source, prepare/check recovery-01 with effective DACL verification, and authorize its single explicit apply. Preserve any failure without retry/reset.
- Java identity corrections retain independently reviewed 167-test/22-class scoped proof. Oracle/RLS/HTTP/pool proof is still pending. F06 payment findings are retained in `payment-integrity-findings.md`; they are not implemented.
- No session-attributed Engram operation was attempted because runtime identity remains unregistered. Durable remediation documents preserve this checkpoint, without claiming a memory save.

## 2026-10-09 recovery-01 transport failure and source correction

Recovery-01 completed only `bootstrap`; its next stage, `010-structure.sql`, raised UnicodeEncodeError when Python's text-mode subprocess used the Windows cp1252 default. Independent diagnosis reproduced the incompatibility for U+0081 while UTF-8 accepted it. The source offset was 88029 and the SQL wrapper offset 90517; these locate the encoding failure, not a semantic SQL defect. No SQL text, migration character or fixture secret was stripped, replaced or rewritten to hide it.

Independent guarded read-only inspection of the original owned target confirmed XEPDB1, version 18.0.0.0.0, COMPATIBLE 18.0.0 and AL32UTF8. Each of OWNER, RUNTIME, SECURITY and CONTROL exists once; owner/runtime/security object counts are zero, CONTROL has two objects, SAED_CTX exists once and RUN_GUARD has one row. The inspection returned no diagnostic error codes. This is **partial bootstrap evidence**, not successful structure, migrations, policy coverage or runtime least-privilege sealing. The old instance must not receive an ordinary reapply.

The correction is limited to the applier and matching guard tests:

- Explicit `encoding="utf-8", errors="strict"` applies to stdin, stdout and stderr. The Linux SQLPlus child receives only the nonsecret `NLS_LANG=AMERICAN_AMERICA.AL32UTF8` Docker exec option; the Windows environment and persistent container configuration are unchanged.
- Unicode encode/decode errors become the stable `SQL stage failed: UTF-8 transport failure` classification without raw exception detail, chained traceback, replacement decoding or a fallback/retry.
- The four existing principals remain an exact allowlist. Bootstrap additionally requires SYS before credential lookup or SQL subprocess invocation; unknown principals are not reflected in the error.
- Existing candidate/source hashes, state selector, container metadata, marker/PDB guards, ACLs, unknown-output suppression and single-attempt locks remain in force. Root separately authorized the final mechanical rebind of the immutable container/run constants to the fresh owned target below; no permissive dynamic target or endpoint discovery was added. The constructor rejects the original container/run and mismatched replacement runs before secret-state reads.

Python documents that explicit encoding/error settings apply to all three subprocess text streams: [Python 3.13 subprocess](https://docs.python.org/3.13/library/subprocess.html). Oracle documents SQLPlus reading NLS_LANG at startup and the charset component describing the client encoding: [Oracle 18 SQLPlus globalization](https://docs.oracle.com/en/database/oracle/oracle-database/18/sqpug/SQL-Plus-globalization-support.html), [Oracle 18 globalization environment](https://docs.oracle.com/en/database/oracle/oracle-database/18/nlspg/setting-up-globalization-support-environment.html). This client runs inside Linux and receives UTF-8 pipe bytes; the setting does not force a Windows SQLPlus client to mislabel ANSI bytes.

| Source proof | Outcome |
|---|---|
| Existing baseline | 73 local guard tests passed before changes. |
| UTF-8 RED | 83 tests, 7 expected failures, zero errors; reproduced cp1252 input failure, absent explicit client/pipe encoding, unsanitized Unicode errors and unbound bootstrap principal. |
| Bootstrap-principal RED | 84 tests, 3 expected failing non-SYS subcases, zero errors, before the SYS-only guard. An intermediate incorrect guard placement was caught by tests and corrected before final proof. |
| Final GREEN | 84 tests: 48 preparation plus 36 executor; zero failures/errors. Real synthetic local-child roundtrip preserves U+0081 and Spanish accents through stdin, stdout and stderr with an ANSI default forced. No Docker or Oracle subprocess is launched by the tests. |
| Preservation | Original 41-file root and recovery-01 41-file inventories/bytes remain identical by internal hash comparison; no secret/hash output. Domain Java, isolated profile, prepared SQL, manifests and locks were not edited. |
| Scoped limits | No prepare/apply, SQL, backend launch, network installation, commit, push, deployment, reset/drop or memory operation by this correction actor. Java's prior independently reviewed 167-test/22-class proof remains unchanged; these Python tests do not renew Oracle/HTTP proof. |

Exact local command: `C:\Python313\python.exe -B -m unittest discover -s scripts/verification -p 'test_*.py'`. Scoped `git diff --check` passes; AST/readback checks remain local only.

Final source SHA-256 after the explicitly authorized immutable-target rebind and repeated 84-case GREEN: applier `70efe60b566bccb3af0bd870e7ffe731ee806692c4475ec37534aaee366b6580`; executor tests `6104605abe8db3df791ae49f8a23260709796204912793a43a9639d3d46baaeb`. These are public source identities, never hashes of private generated state. Prior source hashes do not authorize this candidate.

### Fresh owned target — metadata only, SQL readiness unproved

The separate runtime actor normally stopped and preserved original container `4cc90670a9c69c5340985e355463cab8084e8c17bfbcdde5b920e9d4a7a81b6f`, then created and started one replacement, with no host file writes or SQL:

- ID: `9dceede37edd3018492bb3ad0bcb1b8ce9735200cf5c7e8c45332777d9c86e91`.
- Name: `saed-isolated-0bd41784f0ac`; run label: `0bd41784f0ac76608626a4565c0e053e`; scope: `isolated-fixture`.
- Same immutable image: `gvenzl/oracle-xe@sha256:ee0d7b8ac3665fb80219cbcb5a080cc202cc970120e1fbef592d7592274cef22`.
- Four GiB memory, two CPUs, restart=no, zero mounts; sole published port `127.0.0.1:11521 -> 1521/tcp`.

The explicit immutable-target rebind is complete and passed the same 84-case local suite. Next step is fresh independent review of these exact final bytes. Only after acceptance may a separate actor prepare/check `recovery-02`, validate effective DACLs and unchanged candidate inputs, and perform one guarded freshness/bootstrap/apply attempt. A running container is not SQL readiness or installation success. Preserve any failure; do not replay either historical blocked attempt.

## 2026-10-09 actual recovery-02 result and status-only interruption

This checkpoint supersedes the pending recovery-02 instruction and the earlier claim that the fixture precedes all policy activation. Fresh independent source review accepted one bounded attempt. Recovery-02 then completed bootstrap, 010-structure.sql and 020-catalogs.sql, but stopped at 030-synthetic-fixture.sql with ORA-28115. Its blocked state and exclusive lock remain preserved; final migrations/catalog checks, runtime grants and installer sealing were not reached. No retry or manual continuation occurred.

Independent guarded read-only diagnosis confirmed XEPDB1 / Oracle 18.0.0.0.0, 567 owner objects with zero invalid, two control objects, zero runtime/security objects, 25 enabled owner policies and one SAED_CTX. ROLES retains five rows; organization, property, unit, person, user and assignment fixture tables have zero rows. This is partial structure/catalog evidence, not a completed fixture or RLS-functional proof.

The previous ordering assumption was wrong: structural extraction retains 25 DBMS_RLS.ADD_GROUPED_POLICY calls. V5.0__master_baseline.sql activates POL_RLS_ORG_ORGANIZACIONES using FN_FILTRO_ORGANIZACION; missing context is ANONYMOUS and produces 1=0. Stage 030 skips owner-context initialization, so the first protected organization INSERT is denied. Searching only ADD_POLICY missed grouped-policy activation.

The proposed minimal correction provisions only canonical stage 030 through the existing SYS mechanism, with same-session principal/PDB/marker validation followed by fixed CURRENT_SCHEMA=SAED_ISO_OWNER and its assertion. Policies remain enabled; runtime and owner receive no added EXEMPT, DBA or ANY privilege. This correction is NOT implemented: the writer was interrupted before edits or new RED tests. Its repeated 84-case GREEN verifies the previous transport/guard candidate only. An internal initial preservation snapshot covers 41 + 41 + 41 protected files, without printing private contents or digests. BOOTSTRAP predicates returning 1=1 remain a separate F02 verification concern, not proof of tenant isolation.

Before interruption, the runtime-only actor began one fresh-container operation without SQL or host-file writes. A subsequent root read-only Docker snapshot shows original saed-isolated-cf2540e41716 and failed saed-isolated-0bd41784f0ac stopped and preserved, and new saed-isolated-3eba1726566e (observed ID prefix 461d56bc2672) running with 127.0.0.1:11521 mapped to 1521. The actor did not receive its command's final output; the new full ID/run and post-start isolation guards remain unconfirmed. Do not repeat creation, invent identity, rebind the applier or apply SQL until that outcome is reconciled read-only.

Next: reconcile the new owned target, implement/test/review the fixture-only correction, then authorize one distinct guarded recovery-03 preparation/apply. Application startup, real six-profile HTTP/RLS/pool tests, two-start preservation, restore and F0-F11 phase closure remain pending. No production operations, commits, push or F12 work occurred. Engram writes remain unavailable because the host runtime identity is unregistered; this file checkpoint is not a memory save.

## 2026-10-09 recovery-02 read-only reconciliation and recovery-03 correction

Read-only reconciliation was attempted with `docker inspect saed-isolated-3eba1726566e`. The Docker API denied access to `npipe:////./pipe/dockerDesktopLinuxEngine`; therefore no full ID, labels, image digest, mounts, or port mapping were verified. The observed ID prefix and container name are not sufficient identity proof. No lifecycle command or SQL was run, and the Windows Oracle listener was not touched. The immutable target constants were not rebound.

The minimal stage-030 correction routes only `030-synthetic-fixture.sql` through the existing SYS path. The existing same-session PDB/principal and run-marker guards execute before the fixed `CURRENT_SCHEMA=SAED_ISO_OWNER` change and its `SYS_CONTEXT` assertion; only then does the fixture payload run. Policies remain enabled, and no owner/runtime EXEMPT ACCESS POLICY, DBA, or ANY privileges were added. Two guard tests cover this ordering and ensure stage 031 remains owner-executed.

| TDD proof | Result |
|---|---|
| RED | Both new guard tests failed before implementation (2 failing guard cases); no external process was launched. |
| Focused GREEN | Both new guard tests passed (2 passed). |
| Required full command | `C:\Python313\python.exe -B -m unittest discover -s scripts/verification -p "test_*.py"` ran 86 tests: 53 passed, 33 errors. Errors are Windows `PermissionError` during temporary-directory use/cleanup; no Docker or Oracle was launched. Full-suite GREEN is therefore not established. |

Working-copy SHA-256 values (raw bytes): `isolated_oracle.py` `9c99988917f4eeb4fff3142a9398712511ec4cce99690799c41c2b136ef00c86`; `apply_isolated_oracle.py` `684c104883d5c27b4854d9f89e74020c25a07b472053146cb2684bda322be4b5`; `test_isolated_oracle.py` `acbce37806dd910aaefd72a0eff139535bd900a15d4defc82e34d28b64f44c1e`; `test_apply_isolated_oracle.py` `fdf91299af50b8bc1f785e972c40c79e7e7f099535c3557463703d0564b52c7e`. The first and third match the previously recorded hashes; the modified applier/test hashes identify the new candidate. No private-state hashes are reported.

After the target is fully reconciled and recovery-03 is separately approved, the command that would apply it is `C:\Python313\python.exe -B scripts/verification/apply_isolated_oracle.py apply --state recovery-03 --container-id <confirmed-full-container-id> --run-id <confirmed-run-id>`. It was not run. Preparation, review, exact target identity and explicit user authorization remain prerequisites.

## 2026-10-09 target reconciliation and rebind (coordinator)

Read-only `docker inspect` from the coordinator (outside the Codex sandbox, which was denied the Linux engine pipe) confirmed the owned target:

- ID `461d56bc26721beba86b3cd05044ddb03e9824812fa619e3f5bad83797ad1b67`, name `saed-isolated-3eba1726566e`, run label `3eba1726566eee334c7fa17135877e54`, scope `isolated-fixture`.
- Image `gvenzl/oracle-xe@sha256:ee0d7b8ac3665fb80219cbcb5a080cc202cc970120e1fbef592d7592274cef22`, zero mounts, sole binding `127.0.0.1:11521 -> 1521/tcp`, restart `no`, 4 GiB, 2 CPUs, running.
- Previous targets `saed-isolated-cf2540e41716` and `saed-isolated-0bd41784f0ac` remain stopped and preserved.

The `CONTAINER`/`RUN_ID` constants in `apply_isolated_oracle.py` were rebound to this identity. The recovery-02 pair was added to the constructor rejection test; that test passed before the rebind as well, because the constructor raises earlier for unrelated reasons, so it does not by itself prove identity rejection (known test weakness).

Stage 031 review: it inserts into `USUARIO_ASIGNACIONES` as OWNER without business context. The V5.0 baseline's 25 grouped policies do not cover that table (its policy exists only in the excluded V3.9 baseline), so 031 is not expected to hit ORA-28115. This is static reasoning, not Oracle proof.

Full suite outside the sandbox: 86 tests, OK (the 33 Codex-reported errors were sandbox temp-directory permissions). Source SHA-256: applier `8b3af592f5cd60796cbe44833166b14a2847292f989e17346c49413a77220eae`; executor tests `18aea80b23bd10eb52648882c0a20bc2a9edc89b0cc1fb3420ba96de136510ac`.

Next, requiring explicit user approval: `isolated_oracle.py prepare --state recovery-03`, ACL protection/readback, `check`, then one `apply_isolated_oracle.py apply --state recovery-03 --container-id 461d56bc26721beba86b3cd05044ddb03e9824812fa619e3f5bad83797ad1b67 --run-id 3eba1726566eee334c7fa17135877e54`. Preserve any failure; no retry or reset.

## 2026-10-09 recovery-03 result (user-authorized single attempt)

With explicit user authorization, the coordinator ran `check-container` (pass), `prepare --state recovery-03` (37 artifacts, 2 organizations, 11 users, ACL protection), `check-prepared` (pass) and one `apply` against `461d56bc…ad1b67` / run `3eba1726…7e54`. Result: `BLOCKED: SQL stage failed: ORA-02290`. Execution state: `blocked`, completed `bootstrap`, `010-structure.sql`, `020-catalogs.sql`. State, lock and artifacts are preserved; no retry or reset occurred. The target is no longer fresh and must not receive another apply.

Progress versus recovery-02: stage 030 now runs past the grouped-RLS denial (ORA-28115 did not recur), so the SYS + asserted owner-schema correction is effective for the earlier inserts.

Root cause (source analysis, no database query): the V5.0 baseline defines `CK_RESIDUNIDAD_TIPO` as `('PROPIETARIO','ARRENDATARIO','FAMILIAR','OTRO')`; `CONVIVIENTE` is added only by V5.9, which runs after stage 030. The fixture inserts the conviviente resident with `TIPO_RESIDENTE='CONVIVIENTE'`, the value the application uses (`AssignmentRepositoryImpl`, `FinanzasRepositoryImpl`). The exact failing statement is inferred, not read from Oracle output.

Correction: a structure normalization extends the extracted baseline constraint exactly as V5.9 does (V5.9 drops and re-adds it, so it stays compatible), and is recorded in the manifest transformations. New test `test_structure_allows_every_fixture_resident_type` checks that every resident type the fixture inserts is allowed by the extracted constraint: RED 87 tests / 1 failure reproducing the exact value set, then GREEN 87 / OK.

Next, requiring new explicit user authorization: one fresh owned container (same immutable image, loopback 11521, no mounts), read-only reconciliation and rebind, then one guarded `recovery-04` prepare/check/apply.

## 2026-10-09 recovery-04 result (user-authorized single attempt)

With explicit user authorization the coordinator stopped `saed-isolated-3eba1726566e` (preserved; exit 137 after Docker's default stop grace period) and created one fresh owned target: ID `3a8094922c610c2acf504933437cf91c90e374459168072638ca439290bd0e18`, name `saed-isolated-f87171a5bae3`, run `f87171a5bae3baaa03302b507618bd31`, scope `isolated-fixture`, same immutable image digest, zero mounts, sole binding `127.0.0.1:11521 -> 1521/tcp`, restart `no`, 4 GiB, 2 CPUs, random SYS password (`ORACLE_RANDOM_PASSWORD`, never printed). Read-only reconciliation, rebind (commit c0c078a), `check-container`, `prepare --state recovery-04` and `check-prepared` passed.

The single apply returned `BLOCKED: SQL stage failed: ORA-06512, ORA-20995`. Completed: `bootstrap`, `010-structure.sql`, `020-catalogs.sql`, **`030-synthetic-fixture.sql`** (the CONVIVIENTE constraint correction is effective). The failing stage is `031-mixed-assignment.sql`, at its `ISOLATED_INVALID_OBJECTS` guard. State, lock and artifacts are preserved; no retry or reset.

Read-only diagnosis (`DBA_OBJECTS`/`DBA_ERRORS` as SYSDBA inside the container, names only): 3 invalid owner objects — `PACKAGE BODY PKG_SAED_SESSION`, `TRIGGER TRG_CONTRATO_SYNC_RESIDENTES`, `TRIGGER TRG_TUTORES_VALIDA_MENOR` — and zero `DBA_ERRORS` rows. These are dependency invalidations caused by the fixture's identity `ALTER TABLE ... MODIFY`, not compile errors. Stage 030 runs as SYS and had no owner-scoped guard, so stage 031 was the first to observe them.

Correction (TDD): stage 030 now runs `DBMS_UTILITY.COMPILE_SCHEMA(schema => OWNER, compile_all => FALSE)` after its payload, then an owner-scoped `DBA_OBJECTS` invalid-object guard. Owner/security stages likewise recompile only invalid objects in their own schema before the existing `USER_OBJECTS` guard, because migration DDL (e.g. V5.4 column type change) causes the same dependency invalidation. Objects with real compile errors stay invalid and the guard still fails closed. RED: 88 tests / 1 failure, then 89 tests / 1 error; GREEN: 89 tests OK.

Next, requiring new explicit user authorization: stop and preserve `saed-isolated-f87171a5bae3`, one fresh owned target, read-only reconciliation and rebind, then one guarded `recovery-05` prepare/check/apply.
