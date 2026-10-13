# User registration authorization remediation

## Scope and status

Bounded F02-01 registration slice, 2026-10-08. Only `POST /api/v1/usuarios` and its authorization policy are changed. This is an uncommitted candidate, not a production approval or complete F2 closure. PUT/DELETE user administration, person administration, activation, refresh, assignment resolution and the complete six-profile matrix remain separate work.

## Reproduction and correction

Six initial mock/standalone-HTTP tests failed against the previous implementation, with zero test errors. They reproduce missing-context registration, property-admin access to a privileged account, generic inactive-account reactivation, foreign-unit tenant realignment, unassociated-person access, and HTTP registration without tenant context.

- `UserRegistrationPolicy` derives scope from authenticated context and verifies unit/property hierarchy before any registration write. Requested organization/property/unit identifiers cannot replace a non-global caller's authorization perimeter.
- Missing organization, missing required property/unit, malformed IDs, invisible/nonexistent targets, unexpected caller roles and unsupported requested roles fail closed.
- Existing persons require a current organization/property/unit relationship according to caller scope. Global callers must still prove person existence.
- Registration never sets bootstrap context, impersonates user 1, changes role to SUPERADMIN or restores a forged database context. The authenticated context is preserved.
- Existing privileged identities and non-active accounts are rejected. Reactivation is not performed through generic registration.
- Existing active ordinary identities retain username, email, password, person attributes, habitation relationship and prior assignments. Only an authorized new assignment is inserted when an identical active assignment is absent. No credential email or generated-password response is produced for an existing identity.
- The account-role check includes returned historical assignments, rather than just active ones. Oracle visibility of assignments still requires integrated proof.
- Role resolution precedes inserts. Failure to obtain generated keys or validate habitation throws and rolls back the registration transaction. Habitation persistence errors are no longer swallowed as successful registrations.
- Conviviente quota and organization user-limit services remain in the registration path, after target authorization.

## Verification

Initial RED: `UserRegistrationAuthorizationTest`, 6 tests, 6 expected failures, 0 errors/skips.

Expanded targeted GREEN: 14 tests, 0 failures/errors/skips, including standalone HTTP denials, foreign organization/unit denial, privileged-account denial for both administrator scopes, active-account preservation and valid new scoped registration.

Environment: Microsoft JDK 17.0.19, Maven 3.9.9, offline dependencies. Tests use mocked JDBC and standalone MockMvc; no application entrypoint, Spring database integration context or real Oracle driver connection. Datasource is forced to unavailable loopback port 1 in environment and command properties.

Initial candidate bounded regression: **127 tests across 20 classes**, zero failures/errors/skips. Offline clean compilation and Checkstyle completed successfully at 2026-10-08 16:24:55 America/Bogota. The selection is the prior 19 classes/113 tests plus the 14-case `UserRegistrationAuthorizationTest`, not the full database integration suite. The corrected candidate evidence below supersedes these counts and hashes.

Initial candidate Checkstyle: 3,908 warnings under the existing 4,500 allowance (previous scoped candidate: 3,895). The corrective pass below removes newly introduced style warnings. `git diff --check` passed.

Exact command (JDK17 and unavailable datasource also set in command-scoped environment):

```powershell
mvn -o clean '-Dtest=OrganizationServiceTest,PropertyServiceTest,UnitServiceTest,AssignmentServiceTest,AssignmentManagementServiceTest,PropertyStatusServiceTest,PlantillaContratoServiceTest,PlantillaContratoRepositoryImplTest,ParqueaderosServiceTest,TokenActivacionServiceTest,AuthServiceTest,JwtAuthenticationFilterTest,InactivePropertyFilterTest,CorrelationIdFilterTest,AuditSanitizerTest,AuditAspectTest,FileStorageServiceTest,StartupSafetyTest,ExceptionSafetyTest,UserRegistrationAuthorizationTest' '-DargLine=-Dnet.bytebuddy.experimental=true -Xshare:off' '-Dspring.datasource.url=jdbc:oracle:thin:@127.0.0.1:1/SAED_OFFLINE' '-DDB_URL=jdbc:oracle:thin:@127.0.0.1:1/SAED_OFFLINE' test checkstyle:check
```

Local evidence: `%TEMP%\saed-identity-red.log`, `%TEMP%\saed-identity-green-expanded.log`, `%TEMP%\saed-identity-bounded-regression.log`. These logs contain synthetic test fixtures and existing style diagnostics; do not publish raw logs without privacy review.

## Initial review candidate identity

SHA-256 before independent review:

| File | SHA-256 |
|---|---|
| `backend/src/main/java/com/saed/backend/identity/controller/UsuarioController.java` | `B687D1D7225942BB34C2A2359CF84E66702F8AE9791509A6E17AA52F6AE34328` |
| `backend/src/main/java/com/saed/backend/identity/service/UserRegistrationPolicy.java` | `B3066CA49BD209A22F0B5106930B742363CABC2B6D770519F40124937061E14A` |
| `backend/src/test/java/com/saed/backend/identity/controller/UserRegistrationAuthorizationTest.java` | `D1E52153D1498AE9044C6211C40D00D3AB03AFDCB5E03FE78786632F480C3207` |

## Remaining proof and compatibility

- Oracle RLS may hide global privileged assignment records; mock success does not establish cross-tenant or privileged-role visibility. Real-context registration, quota locks, transactional rollback, same-person concurrency and repeat-assignment idempotency require isolated integration tests.
- `ResidentesPage` and `ConvivientesSection` send a unit; fresh portero creation in `EmpleadosPage` needs none. Legacy `UsuariosPage` omits a unit, but is unreachable: the `/usuarios` route redirects to `/residentes`. Treat this as orphan-code cleanup under F0, not a current user-flow regression. No route was activated and no arbitrary unit inference was restored.
- Existing persons outside the caller's current perimeter require a separate authorized identity-binding workflow; generic registration cannot grant permission to alter a global identity.
- The corrective pass below aligns existing-account binding messages in the three live registration callers. Whole-page E2E, full account lifecycle and final F02/F05 closure remain pending.
- API password delivery/email behavior for newly created identities is retained for this bounded slice and remains a broader onboarding-security review item.
- TestSprite cannot validate the changed build because no safe reachable target serves it. No external test was dispatched against production as a substitute; no terminal TestSprite pass is claimed.
- Isolated Oracle infrastructure remains blocked: Docker Desktop fails before engine startup on `sailor-ingest.sock`. WSL service is active, but no engine/containers are running. Production Oracle version is unknown. No image download, database connection, migration, deployment, credential rotation, commit or push occurred.
- User-owned review mode remains OFF. Runtime memory registration is unavailable; no agent-attributed memory calls were made.

Independent source review and integrated Oracle/external verification are pending. F02-01 remains **in progress**.

## Independent review disposition and one bounded corrective pass

The independent review corroborated removal of existing-account credential overwrite. Its HIGH claim that `UsuariosPage` breaks the current registration route was dismissed with counter-evidence: `App.jsx:473–474` redirects `/usuarios` to `/residentes`, and the only `UsuariosPage` reference is an unused lazy import. The orphan page was left untouched.

The live callers did have a compatibility defect: reusing an identity could display a fabricated replacement-password modal or announce new credentials. A local Chromium RED run reproduced this in the actual `ConvivientesSection`: one expected failure and one passing new-account case. The failure snapshot showed a credential modal despite `accountCreated: false`.

Corrections:

- Registration responses explicitly return `accountCreated: true` for new identities and `false` for preserved identities. The preserved-identity response contains no replacement credential.
- `ConvivientesSection` reports binding without opening a credential modal for an existing account.
- Both `ResidentesPage` registration paths and `EmpleadosPage` distinguish existing-account binding from new-account credential delivery.
- Backend tests now include successful resident/conviviente registration with quota invocation, successful unitless portero registration and reuse of an identical active assignment without writes.
- New policy/controller style warnings were removed without increasing allowances or suppressing checks. Total Checkstyle warnings decreased to **3,875**, below the 3,895 pre-slice candidate. `UserRegistrationPolicy` has zero Checkstyle warnings.

Corrective backend RED: 17 cases, 2 expected response-contract failures, zero errors/skips. Corrective targeted GREEN: 17 cases, zero failures/errors/skips.

Final corrected backend command is the exact 20-class command above; result: **130 tests, zero failures/errors/skips**, clean compilation and Checkstyle success, 2026-10-08 16:36:14 America/Bogota. Evidence: `%TEMP%\saed-identity-correction-red.log`, `%TEMP%\saed-identity-correction-green.log`, `%TEMP%\saed-identity-corrected-final.log`.

## Frontend proof and environment

The local fixture `frontend/tests/fixtures/registration-contract.html` mounts the actual live `ConvivientesSection`. `tests/e2e/25-registration-contract.spec.js` mocks every API request, permits only loopback hosts, and uses synthetic identity/token data. It tests existing-account binding without replacement credentials, new-account credentials with explicit unit ID, and an HTTP 403 error without a success/credential modal.

Final Playwright result: **3 passed**, zero failures/skips, Chromium. This is component/browser proof with mocked HTTP, not real JWT/RLS, quota concurrency, email delivery or whole-page end-to-end proof. `ResidentesPage` and `EmpleadosPage` branches have source/build/lint corroboration, not dedicated browser journeys.

```powershell
# Vite process: VITE_API_BASE_URL=http://127.0.0.1:1/api/v1
frontend\node_modules\.bin\vite.cmd --host 127.0.0.1 --port 5179 --strictPort
# Browser process: PLAYWRIGHT_BASE_URL=http://127.0.0.1:5179
frontend\node_modules\.bin\playwright.cmd test tests/e2e/25-registration-contract.spec.js --config frontend/playwright.config.js --reporter=line --timeout=20000
```

No backend ran. The launched Vite process was stopped after verification. The fixture is not included in `frontend/dist`.

Existing `node_modules` lacked declared `recharts` and `qrcode.react`. Restored the existing dependency tree with `pnpm.cmd install --offline --frozen-lockfile --ignore-scripts`: 37 packages reused from cache, zero downloads, no package/lockfile changes. No framework was added. Local pnpm is 11.20.0 versus CI's declared 10; version parity remains F0 work.

`pnpm.cmd build` passed; bundle-size warning remains. `pnpm.cmd lint` passed with **0 errors and 342 warnings**; no threshold was raised. `git diff --check` passed.

Read-only TestSprite CLI 0.10.0 inventory found the existing SAED Frontend project, with one unrelated SuperAdmin onboarding/credential-modal scenario and no scenario covering this changed ConvivientesSection contract. No workspace TestSprite config exists. No new external test/project was created and no unrelated scenario was run against a previous deployment. External verification remains pending; local Playwright is not presented as a TestSprite terminal verdict.

Frontend evidence: `%TEMP%\saed-registration-frontend-red.log`, `%TEMP%\saed-registration-frontend-final.log`, `%TEMP%\saed-registration-frontend-build.log`, `%TEMP%\saed-registration-frontend-lint.log`. Red-only browser artifacts under `frontend/test-results` use synthetic data.

## Corrected candidate SHA-256

| File | SHA-256 |
|---|---|
| `UsuarioController.java` | `6A5C924B40BFC9F7CC29517D152BBB2ECB44440EE98B4F933F2B4A60D561B568` |
| `UserRegistrationPolicy.java` | `98FEE5106F48BFD9A01D6BB81F693460BA6F2DE32B0123CA6A70E0A5A42A8AA1` |
| `UserRegistrationAuthorizationTest.java` | `0A3684DE603A418E7B2F8970594567DFC506E4AEA80142D34E6019FC01C054BF` |
| `ConvivientesSection.jsx` | `C9BA6BC01A94E5FF6288C734170F267956BF19134242E63C9784BF4B51172A12` |
| `ResidentesPage.jsx` | `329F1FF8C44F7DE517B0A1490FBC017568E9769F6D814C00E954BAF4AA40EAB9` |
| `EmpleadosPage.jsx` | `B9338705CC69CAE8029C9BBB46714A5161D4FC575FE7A52BEB57A34C03DB00B2` |
| `25-registration-contract.spec.js` | `0F45CDD2ACE908A8B578BC4AD416040F8CB776C5D1F00925A1B130D6ED95B854` |
| `registration-contract.html` | `E58693FF0A7875D2EF9B65A881EF294B8303E66480CAD62796A4604E332E5477` |

Skill paths loaded: workspace `saed-frontend-design` and `testsprite-verify`; global `backend-audit`, `frontend-audit` and `context7-mcp`; OpenCode `modern-web-guidance`. The requested global TestSprite skill path does not exist; its existing workspace counterpart was used. Modern guidance and official React documentation were consulted before frontend changes. No production readiness or F02 closure is claimed.
