# Record F06 payment integrity findings

## Scope and evidence

Read-only delegated source mapping, 2026-10-08. These findings queue F06 remediation; no payment correction, provider call, database transaction or deployed exploit was performed. F06 remains open. The current source candidate is uncommitted.

## Confirmed source defects

| Finding | Evidence | Required regression |
|---|---|---|
| Ordinary payment registration accepts fabricated gateway approval | `PagosController.java:65` accepts client payment method; `FinanzasServiceImpl.java:526` approves `PASARELA_WOMPI` without provider evidence; `FinanzasRepositoryImpl.java:226` also infers approval when state is absent. | Reject client gateway approval before ledger writes. Provider settlement needs a separate internal operation with independently verified evidence. |
| A payment can reduce debt twice | Approved detail insertion invokes the V5.17 trigger, and the service debits again at `FinanzasServiceImpl.java:530`. Manual approval debits at `:695` before the state-update trigger. | Debt 100 plus payment 30 must produce balance 70, exactly once. Pending/rejected payments leave debt unchanged. Full-payment clamping can conceal this defect. |
| Webhook settlement can acknowledge failures | `WompiServiceImpl.java:901` swallows settlement errors; `PagosController.java:145` acknowledges failures with HTTP 200. | Failed settlement rolls back, sends no receipt and permits a provider retry. Prove behavior through actual Spring transactions. |
| Event binding and settlement linkage are incomplete | Event environment is ignored, currency is optional, reference is trusted for intent lookup, and no authoritative transaction lookup exists. The created payment is not linked back to the intent. | Bind provider ID, persisted intent, tenant, amount, currency and environment; persist idempotency and intent-to-payment linkage atomically. |

Existing signature validation, pending-state conditional updates, provider-ID/reference uniqueness and connection cleanup must be preserved. Dynamic signed property lists must remain supported. Wompi documents retries after non-200 responses, and its signature example does not bind every transaction field. Authoritative lookup currently requires backend private-key configuration. Sources: [Wompi events](https://docs.wompi.co/docs/colombia/eventos/), [Wompi transactions](https://docs.wompi.co/docs/colombia/transacciones/).

## Implementation and verification queue

1. Reproduce method spoofing with pure service/controller tests, then reject it in ordinary registration and remove repository-inferred approval.
2. Select one debit owner and implement versioned schema/service changes together. Retaining the corrected database triggers is the smallest proposed route; trigger behavior must be demonstrated before rollout.
3. Introduce independently verified internal settlement, atomic linkage/idempotency and after-commit receipt delivery. Missing provider configuration must fail closed.
4. Run isolated Oracle tests for partial payments, duplicate/concurrent approvals, rollback at each settlement step, two-tenant isolation and ledger/debt reconciliation.

Tests to revise: `ManualPaymentApprovalSecurityTest` currently expects the unsafe direct gateway approval and uses full-payment examples; `WompiPaymentFlowAdversarialTest` does not verify the remaining balance. Preserve `WompiSaaSSegregationSecurityTest` and `WompiContextIsolationSecurityTest`. Their default dev-profile database configuration is unsafe for local integration execution; use the guarded disposable target.

Excess-payment disposition and void/refund reversal policy require business decisions before their final implementation. Neither decision blocks rejecting fabricated approvals or correcting duplicate debits. No sandbox or live provider transaction is authorized by this document.

Memory attribution remains unavailable. This document preserves the findings locally; it does not imply an Engram save or production approval.

## 2026-10-09 — Remediation slice: forged gateway approval (C-09) and webhook failure acknowledgement

### Fixed

- **C-09 forged gateway approval.** `FinanzasServiceImpl.registrarPago` and `registrarPagoManual` now reject any gateway method (`PASARELA*` or containing `WOMPI`, case/whitespace-insensitive) with `GatewayPaymentMethodNotAllowedException` (a subclass of `IllegalArgumentException`, mapped to HTTP 400 by the existing global handler) before any lookup or ledger write. Ordinary registrations are always `PENDIENTE_APROBACION`, with no fee debit, no portfolio recalculation and no receipt. `FinanzasRepositoryImpl.registrarPago` no longer infers `APROBADO` from the method; an absent state defaults to `PENDIENTE_APROBACION`.
- **Gateway settlement path.** The Wompi webhook now settles fees through a new internal `FinanzasService.registrarPagoPasarelaVerificado`, not reachable from any controller. Its body is the previous gateway branch moved verbatim, so debit behavior is identical (the double debit below is unchanged).
- **Webhook acknowledged settlement failures.** `WompiServiceImpl.procesarWebhook` no longer swallows settlement exceptions; they propagate, and the method is now `@Transactional(rollbackFor = Exception.class)` because it declares checked exceptions. The residential receipt after settlement is therefore skipped for a failed attempt. `PagosController.wompiWebhook` returns HTTP 500 with the generic body `{"success":false,"error":"WEBHOOK_PROCESSING_FAILED"}` (no exception text, SQL or stack data) so Wompi retries.
- **Preserved semantics.** Unknown events, invalid signatures, missing fields, currency/amount mismatch, unknown references and duplicate already-processed events still return normally (HTTP 200). Unparseable JSON was previously acknowledged only through the controller catch; it is now discarded explicitly in the service, so it remains HTTP 200 instead of becoming a retry loop.

### Evidence (pure unit tests with Mockito and standalone MockMvc; no database)

- RED: `OrdinaryPaymentRegistrationIntegrityTest` and `WompiWebhookSettlementFailureTest` ran 16 tests with 12 failures before the production change. Examples: `Expected IllegalArgumentException to be thrown, but nothing was thrown`; `Status expected:<400> but was:<201>`; `expected: <PENDIENTE_APROBACION> but was: <APROBADO>`; `settlement failure must not be acknowledged, got 200`; `Expected DataIntegrityViolationException to be thrown, but nothing was thrown`; missing `rollbackFor`; malformed JSON threw `JsonParseException`.
- GREEN: the same two classes ran 18 tests with 0 failures; two tests were added after GREEN for the new internal settlement contract. The CI unit list (`mvn test -Dtest=...` from `.github/workflows/ci.yml`) ran 89 tests with 0 failures; `mvn compile` and `mvn checkstyle:check` passed. JDK 17, offline, with `DB_URL` pointed at a dead port.
- Revised: `ManualPaymentApprovalSecurityTest` MP-22 now expects rejection, no `PAGOS` row and an unchanged balance. It is DB-backed and was compiled but **not executed**.

### Changed files

- `backend/src/main/java/com/saed/backend/finanzas/exception/GatewayPaymentMethodNotAllowedException.java` (new)
- `backend/src/main/java/com/saed/backend/finanzas/service/FinanzasService.java`
- `backend/src/main/java/com/saed/backend/finanzas/service/impl/FinanzasServiceImpl.java`
- `backend/src/main/java/com/saed/backend/finanzas/repository/impl/FinanzasRepositoryImpl.java`
- `backend/src/main/java/com/saed/backend/finanzas/service/impl/WompiServiceImpl.java`
- `backend/src/main/java/com/saed/backend/finanzas/controller/PagosController.java`
- `backend/src/test/java/com/saed/backend/finanzas/OrdinaryPaymentRegistrationIntegrityTest.java` (new)
- `backend/src/test/java/com/saed/backend/finanzas/WompiWebhookSettlementFailureTest.java` (new)
- `backend/src/test/java/com/saed/backend/finanzas/ManualPaymentApprovalSecurityTest.java`

### Still open

- **Double debit** (V5.17 trigger plus Java debit in `registrarPagoPasarelaVerificado` and `aprobarPago`) is untouched.
- **Event binding and linkage**: environment, mandatory currency, authoritative transaction lookup and intent-to-payment linkage are still missing.
- **Receipts are sent before commit.** A receipt from `enviarReciboPagoSilencioso` inside settlement can still be emailed if a later step or the commit fails. The gateway path also still sends two receipts. After-commit delivery remains queue item 3.
- **Non-fee branches.** Settlement exceptions in the membership, renewal, upgrade and onboarding branches now also propagate. Earlier side effects that are not transactional, such as emails sent before the failure, are not undone.
- **No Oracle or transaction proof.** Mocked unit tests do not prove Oracle rollback, trigger behavior or Spring proxy transaction semantics. The DB-backed suites (`ManualPaymentApprovalSecurityTest`, `WompiPaymentFlowAdversarialTest`, `WompiSaaSSegregationSecurityTest`, `WompiContextIsolationSecurityTest`, `MembershipBillingSecurityTest`) were not run. F06 remains open until the isolated Oracle verification in queue item 4.
- **Frontend.** No frontend flow sends `PASARELA_WOMPI` to `POST /pagos` or `/pagos/manual`: the method selectors offer only EFECTIVO, TRANSFERENCIA, CONSIGNACION and OTRO. The online flow uses `/pagos/wompi/solicitud` and `/pagos/wompi/transaccion`.
