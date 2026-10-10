# F9 Operations Progress

## Work unit: PQRS reply and default SLA guardrails

- Fixed items: F9-01/F09-05 partial — resident replies now reject status changes; status transitions through the service require property-admin roles; CAS failures during admin replies are surfaced; fallback SLA is 1/4/24/72 wall-clock hours and accepts CRITICA.
- Tests: 1 new adversarial unit test (`TicketServiceF9Test`). RED/GREEN not runnable in this sandbox: `./mvnw.cmd -o -q -Dtest=TicketServiceF9Test test` fails before test execution because the available Java is 8 (`invalid target release: 17`).
- Changed files: `TicketServiceImpl.java`, `TicketServiceF9Test.java`.
- Migrations: none in this work unit.
- Open: SLA at-risk DTO exposure/75% calculation; persistence seed/config defaults; incident reopen; worker minimization; recurring maintenance and real asset history; supplier contract/renewal idempotency; works authorization/inspection; frontend error behavior; broader tenant/security and test verification.
