# Preserve assignment authority and request context isolation

## Scope and status

Bounded F02-02/F02-03 Java remediation, 2026-10-08. Workspace: `C:/Users/JUAN/Antigravity IDE/SAED`. This uncommitted candidate changes only `AuthRepositoryImpl.getUserProfile`, `JwtAuthenticationFilter`, two pure mock regression classes, and this slice's documentation. It does not close F02 or establish production readiness.

SQL migrations, the isolated Oracle harness/state/profile, registry changes and finance source are outside this writer's scope. No application entrypoint, default integration suite, real database connection or external provider was launched. Review mode remains OFF; runtime memory attribution is unavailable, so no memory calls were made.

## Reproduced defects and correction

- The old profile read used the user's latest habitation to replace a package-selected unit/organization/property and performed assignment UPDATEs, including role changes. The new read retains the stored package-selected role, scope, organization, property and unit. Descriptive habitation metadata is queried only for the selected unit; it cannot infer or persist a replacement authorization scope.
- The optional bootstrap/enrichment block saves and restores the exact prior `SaedContext` in `finally`. Missing context is cleared after the read. An enrichment failure preserves authoritative profile values; bootstrap failure prevents the subsequent enrichment queries. Failure of the authoritative package call leaves the prior Java context unchanged.
- The old JWT filter's rejected-assignment returns bypassed cleanup, and absent/failed assignment resolution could reach the downstream chain. Its outer `finally` now clears both `SaedContextHolder` and Spring `SecurityContextHolder` for every return, resolution failure, response-writer failure and downstream failure.
- Explicit assignment selection rejects malformed/nonpositive/overflow identifiers with HTTP 400, unauthorized/inactive or structurally incomplete assignments with HTTP 403, and unavailable/failed resolution with HTTP 503. These responses stop the chain. Logs record only the exception class; responses contain a fixed generic message without the exception text or stack trace.
- Valid explicit assignments retain their exact tenant identifiers. Supported no-header/default profile selection, identity-only discovery, anonymous requests and existing authenticated Spring-context compatibility remain. The generic filter still passes public/CORS requests to the downstream chain; full HTTP/CORS integration is pending.

Source anchors: `AuthRepositoryImpl.java:91` (authoritative profile), `:112` (context snapshot), `:136` (selected-unit metadata), `:154` (unchanged authorization fields), `:156` (restoration); `JwtAuthenticationFilter.java:57` (whole-request lifecycle), `:97` (explicit selection), `:170` (sanitized failure), `:176` (cleanup).

## RED to GREEN proof

Initial RED, before either production-source edit: **28 tests, 19 expected failures, zero errors/skips**. `AuthRepositoryProfileSafetyTest`: 14 cases/10 failures; `JwtRequestContextSafetyTest`: 14 cases/9 failures. This demonstrated assignment mutation/scope replacement and request-context/fail-open defects with mocked JDBC and HTTP objects.

First targeted GREEN: **34 tests, zero failures/errors/skips**. The expanded final candidate contains **37 new cases**: 15 profile cases and 22 JWT cases. Coverage includes all six stored roles with contradictory habitation metadata, no assignment DML, selected-unit scoping, unitless/identity-only profiles, success/failure restoration, explicit rejection and missing dependencies, sequential same-thread requests, successful explicit/default/discovery paths, sanitized logs and response/chain exceptions.

The first bounded regression completed with **167 tests across 22 classes, zero failures/errors/skips**, clean compilation and Checkstyle success. Tracked bytes outside `getUserProfile` were then restored to remove unrelated line-ending changes. The final normalized candidate was recompiled and checked with the same command: **167 tests across 22 classes, zero failures/errors/skips**, read back from the 22 Surefire XML reports at **2026-10-08 20:59:37 America/Bogota**. This second run supersedes the first run for byte identity.

Final Checkstyle: **3,852 warnings**, down from the preceding 3,875-warning candidate, with the existing 4,500 allowance unchanged. The new profile/filter logic introduced no Checkstyle warnings; four retained repository warnings belong to untouched empty catches outside `getUserProfile`. Scoped `git diff --check` passed; both new test files have zero trailing-whitespace lines. No source normalization was performed after this final proof.

Environment: Microsoft JDK 17.0.19 and Maven 3.9.9, cached dependencies only (`-o`). New tests use Mockito, generated in-process signing material, real token parsing and servlet mocks; no token/key value is recorded. Existing selected baseline: 130 cases/20 classes from [identity-authorization-progress.md](identity-authorization-progress.md).

Exact final command, from the workspace's `backend` directory:

```powershell
$env:JAVA_HOME='C:\Users\JUAN\.jdks\ms-17.0.19'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:DB_URL='jdbc:oracle:thin:@127.0.0.1:1/SAED_OFFLINE'
$env:SPRING_DATASOURCE_URL=$env:DB_URL
& 'C:\Users\JUAN\Tools\apache-maven-3.9.9\bin\mvn.cmd' -o clean '-Dtest=OrganizationServiceTest,PropertyServiceTest,UnitServiceTest,AssignmentServiceTest,AssignmentManagementServiceTest,PropertyStatusServiceTest,PlantillaContratoServiceTest,PlantillaContratoRepositoryImplTest,ParqueaderosServiceTest,TokenActivacionServiceTest,AuthServiceTest,JwtAuthenticationFilterTest,InactivePropertyFilterTest,CorrelationIdFilterTest,AuditSanitizerTest,AuditAspectTest,FileStorageServiceTest,StartupSafetyTest,ExceptionSafetyTest,UserRegistrationAuthorizationTest,AuthRepositoryProfileSafetyTest,JwtRequestContextSafetyTest' '-DargLine=-Dnet.bytebuddy.experimental=true -Xshare:off' '-Dspring.datasource.url=jdbc:oracle:thin:@127.0.0.1:1/SAED_OFFLINE' '-DDB_URL=jdbc:oracle:thin:@127.0.0.1:1/SAED_OFFLINE' test checkstyle:check
```

Local evidence: `%TEMP%\saed-identity-context-red.log`, `%TEMP%\saed-identity-context-green.log`, `%TEMP%\saed-identity-context-final.log`, `%TEMP%\saed-identity-context-normalized-final.log`. Publish only sanitized summaries. An earlier Checkstyle invocation from the repository root failed because that directory has no POM; it is excluded from code verification evidence.

## Final candidate identity

SHA-256 after restoring original bytes outside the scoped method:

| File | SHA-256 |
|---|---|
| `backend/src/main/java/com/saed/backend/identity/repository/AuthRepositoryImpl.java` | `9BED432319E5FD337085146CAE4CF898AC515680BCB77CD385926766BA2C9637` |
| `backend/src/main/java/com/saed/backend/security/filter/JwtAuthenticationFilter.java` | `62883B25E27A827F9EE7AE82A8816AE013B6D52923A103AA3159184DCCD72585` |
| `backend/src/test/java/com/saed/backend/identity/repository/AuthRepositoryProfileSafetyTest.java` | `D6F9B225C69A8BF67776251937377CD040687C7E5AE5D5EBB628EC22BFC04F47` |
| `backend/src/test/java/com/saed/backend/security/filter/JwtRequestContextSafetyTest.java` | `0468C19C3912F4394B8E2D4B28BCABCBD697402FE877938E94057BE2BB41303A` |

## Remaining proof and risks

- Java holder cleanup does not establish Oracle pooled-session reset. The bootstrap predicate, connection checkout/return, selected property/unit binding and two-user pool isolation need isolated Oracle proof and, where defective, a versioned migration.
- Existing Oracle package default selection uses an unordered first assignment and lacks complete assignment date checks. This Java slice deliberately retains package-selected authorization; it does not certify the package's default selection, start/end date validity or legacy role derivation elsewhere.
- Full six-profile HTTP/RLS denials, frontend assignment-switch atomicity, refresh/account lifecycle and real default/public/CORS routes remain pending. Existing authenticated Spring-context fallback remains supported and requires separate whole-chain review.
- Independent source review accepted the Java source and mock-test scope with no candidate-caused HIGH/MEDIUM findings. Its four source/test identities match this report; root readback confirms 167 tests across 22 Surefire XML files, zero failures/errors/skips. No Oracle behavior is certified. TestSprite cannot validate these changed bytes without an authorized safe target serving them; no remote test was dispatched against an older deployment. No TestSprite terminal verdict or production approval is claimed.
- A separate isolated Oracle apply failed at bootstrap with ORA-00972; its blocked state and lock were preserved without retry. The guarded read-only diagnosis found an empty database footprint. See [verification-register.md](verification-register.md); this writer performed no Oracle operation.

`skill_resolution: paths-injected`. Loaded: global `backend-audit`; ECC `security-review` and `production-audit`; workspace `testsprite-verify`. Work used delegated direct execution, with no SDD/RDD artifacts or subagents.
