# Exception handling safety remediation

## Scope

Bounded F02 persistence-error security slice, 2026-10-08. Only exception handler behavior changes; no user-management remediation, production connection, application boot, migrations, deployment, commits or pushes.

Existing contract preserved: `success`, numeric `status`, stable functional `code`, sanitized `message`, `traceId`, ISO-8601 `timestamp`. Existing conflict/validation/security mappings remain. Established Spanish client messages follow the existing application language.

## Changes

- ORA-28112 fails closed as HTTP 500 without JDBC diagnostics, package compilation, policy removal, audit retries or repair.
- The transaction-wrapped policy failure also fails closed without entering context-spoofing audit handling.
- Generic database responses no longer contain nested error/SQL details; Oracle metadata keys are removed.
- Persistence, transaction and unexpected fallback logging contains only stable classification and bounded correlation, without raw exception messages/stack traces.
- Correlation follows existing filter format `[a-zA-Z0-9_-]{1,64}`; invalid IDs are replaced with UUIDs.

## Evidence

RED command: `mvn -o -Dtest=ExceptionSafetyTest "-DargLine=-Dnet.bytebuddy.experimental=true -Xshare:off" test`.

RED: eight tests, five expected failures, zero errors. All error data is synthetic. Initial GREEN before cause-chain review correction: 10 ExceptionSafetyTest + 10 StartupSafetyTest cases; 20 total, zero failures/errors/skips; clean compilation and Checkstyle successful (3,895 warnings under existing 4,500 threshold).

Tests use standalone MockMvc with the existing correlation filter, direct handler calls, mocked NamedParameterJdbcTemplate and a scoped log appender. They never start the application or connect to a database. Command-scoped JDK17/Maven3.9.9; offline Maven and dummy loopback port 1 datasource.

## Limits

- Zero JDBC is guaranteed/tested for the scoped policy-failure and fallback paths, not every exception route. Legitimate access-denial audit writes on other 403 branches remain.
- Other domain exception messages and audit-input sanitization remain a separate review scope; no blanket handler-wide secrecy certificate is claimed.
- TestSprite CLI/auth exist, but no safe reachable target serves this changed build. External verification remains blocked; production was not used as a substitute.
- Oracle integration and final F0-F11 release validation are pending.

Final command: `mvn -o clean -Dtest=ExceptionSafetyTest,StartupSafetyTest "-DargLine=-Dnet.bytebuddy.experimental=true -Xshare:off" test checkstyle:check`.
Candidate SHA-256 GlobalExceptionHandler.java: `41E8EF26A9CBD4397C3B2A89F6AC416942E6B2879C0A3C1810A979A789F96091`.
Candidate SHA-256 ExceptionSafetyTest.java: `9478DE06B30142304D692BC1495C66B742FDDEBB9062D2E0EB64A0785DAE2988`.
`git diff --check` passed; independent source review remains the next handoff, not a production approval.

## Independent cause-chain review correction

Review found that an intermediate context-spoofing wrapper could hide a deeper ORA-28112 and incorrectly issue an audit JDBC call. Four new tests reproduce intermediate/nested policy failures, cyclic chains and a 40-wrapper depth chain. RED: 14 exception cases, 4 expected failures, 0 errors.

The corrected classification examines at most 16 cause nodes with identity-based cycle detection. Policy failure takes priority before any audit. Cyclic, unreadable or depth-exhausted chains return sanitized HTTP 500 with zero JDBC; no uninspected remainder is treated as safe. No raw chain messages are concatenated into logs or responses.

Targeted GREEN: 14 ExceptionSafetyTest + 10 StartupSafetyTest, 24 total, zero failures/errors/skips.

Final bounded regression: exact 17 CI mock/web classes plus these two classes; 113 total (89 baseline + 24 new), zero failures/errors/skips. Offline JDK17/Maven3.9.9 clean compilation and Checkstyle succeeded, 2026-10-08 15:28:06 America/Bogota. Checkstyle reports 3,895 warnings under the existing 4,500 allowance. Every selected class passed a precheck excluding full Spring/database integration contexts and driver bootstraps. Datasource was forced to unavailable loopback port 1 in environment and JVM properties.
Final exact test selection: `OrganizationServiceTest,PropertyServiceTest,UnitServiceTest,AssignmentServiceTest,AssignmentManagementServiceTest,PropertyStatusServiceTest,PlantillaContratoServiceTest,PlantillaContratoRepositoryImplTest,ParqueaderosServiceTest,TokenActivacionServiceTest,AuthServiceTest,JwtAuthenticationFilterTest,InactivePropertyFilterTest,CorrelationIdFilterTest,AuditSanitizerTest,AuditAspectTest,FileStorageServiceTest,StartupSafetyTest,ExceptionSafetyTest`.
Final command: `mvn -o clean -Dtest=<selection above> "-DargLine=-Dnet.bytebuddy.experimental=true -Xshare:off" -Dspring.datasource.url=jdbc:oracle:thin:@127.0.0.1:1/SAED_OFFLINE -DDB_URL=jdbc:oracle:thin:@127.0.0.1:1/SAED_OFFLINE test checkstyle:check`.
Sanitized final evidence: `C:UsersJUANAppDataLocalTempsaed-final-bounded-regression-20261008.log`.
