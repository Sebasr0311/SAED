# F06 Finance and Payments Remediation - Progress

Date: 2026-10-11. Branch `fix/f0-f11-p0-remediation` (commits on top of f35e915). Status: unit-verified; the Oracle
integration tier has not been run (coordinator).

## Single owner of the fee debit (F06-03)

**The database triggers own the debit of `CUOTAS.SALDO_PENDIENTE`. Java never debits a fee.**

| Trigger (defined in `V5.50__pago_cuota_debito_unico_trigger.sql`) | Fires | Effect |
|---|---|---|
| `TRG_APLICAR_PAGO_CUOTA` | `AFTER INSERT ON PAGO_DETALLE` | Debits the fee only when the parent payment is already `APROBADO` (gateway settlement inserts the payment as APROBADO, then its detail). A detail of a `PENDIENTE_APROBACION` or `RECHAZADO` payment never debits. |
| `TRG_PAGOS_APROBACION_ESTADO` | `AFTER UPDATE OF ESTADO ON PAGOS WHEN (NEW='APROBADO' AND OLD='PENDIENTE_APROBACION')` | Debits every detail of the payment on that single transition (manual approval). |

Both compute `saldo = GREATEST(0, saldo - monto)`, state `PAGADA` at zero else `PAGADA_PARCIAL`, and skip `ANULADA` fees.
Why the trigger and not Java: it is the audited source (V5.17), it fires exactly once per row transition regardless of the
caller (service, SQL, future job), and it needs no second code path to keep consistent. Removed from Java:
`FinanzasRepository.actualizarSaldoCuota`, the debit loop in `aprobarPago` and the debit in the gateway settlement.

Guards that make "once" true:
- `FinanzasRepositoryImpl.aprobarPago` is `UPDATE PAGOS ... WHERE ESTADO = 'PENDIENTE_APROBACION'` and throws `IllegalStateException`
  when it matches no row, so a repeated or concurrent approval cannot succeed twice (and cannot fire the trigger twice).
- `FinanzasServiceImpl.aprobarPago` locks the payment and every fee (`FOR UPDATE`), requires an authenticated approver (no `1L`
  fallback) and refuses an approval that exceeds the fee's outstanding balance or targets a paid/cancelled fee.
- Ordinary registration (`registrarPago`, `registrarPagoManual`) rejects a payment above the fee balance or for a non-payable fee.
- Gateway settlement (`registrarPagoPasarelaVerificado`, now returns the payment id) still stores the payment (money was
  collected) but flags an over-balance charge in `PAGOS.OBSERVACIONES` ("SOBREPAGO ...") and logs it.
- `ProductionSchemaInitializer` still carries the V5.17 trigger text behind its legacy flag; V5.50 is the versioned definition.

## Fixed items

| Audit / plan IDs | Change |
|---|---|
| F06-03, F6-03 | Single debit owner (above). Partial payments: debt 100, pay 30 gives 70 (verify on Oracle). Pending/rejected payments change nothing. |
| F6-06, F6-07, F6-08 | One outstanding-fee definition (`domain/OutstandingFees`: PENDIENTE, VENCIDA, PAGADA_PARCIAL with balance > 0) used by paz y salvo, portfolio, aging, cash flow, pending list and online payment. `domain/CarteraRecalculation` writes only `CK_CARTERA_ESTADO` states (AL_DIA, MORA_30, MORA_60, MORA_CRITICA; COBRO_JURIDICO is preserved), without MERGE, one current row per unit. Summary counts the real states. Double charge: manual payments above balance refused, over-balance approvals refused, a second live Wompi intent per charge refused (409), over-balance gateway charges flagged. `FlujoCajaRepository` no longer turns database errors into zeros. |
| F06-02, DEC-08, F6-02 | `finanzas/billing`: `BillingService.generarCuotasMensuales(property, YearMonth, today)` and `procesarVencimientos(property, today)` (the methods the F12 scheduler calls, inside the property administrator context; SUPERADMIN and other properties denied). Monthly fee = approved `EGRESO` budget of the year / 12 shared by unit coefficient, or the configured fixed fee; rounding residue handed to the largest remainders when coefficients sum to 1 (4-decimal tolerance), units without coefficient skipped and reported, no budget and no fixed fee generates nothing. Idempotent per unit/concept/period (`INSERT ... WHERE NOT EXISTS`, duplicate-key race counted as existing). Early-payment discount (percentage and days, applied only while the window is open, forfeited once after it) and simple monthly interest per started month after grace days (optional ceiling, accrued on the unpaid principal, never twice) default to 0 and are disabled. PENDIENTE fees past due become VENCIDA; PAGADA_PARCIAL stays outstanding. API: `GET/PUT /api/v1/finanzas/facturacion/configuracion`, `POST /generar`, `POST /vencimientos`. |
| F06-04, F6-04, F6-05 | `WompiServiceImpl`: event accepted only for the configured environment (`saed.wompi.environment` SANDBOX or LIVE; event `environment` test/prod; unset or missing rejects, fail closed), mandatory currency equal to the intent's `MONEDA`, amount, intent unit owns the fee/fine, provider transaction id not bound to another intent. The settled payment id is written to `TRANSACCIONES_PAGO.ID_PAGO` in the same transaction. Receipts (fee, fine, membership, renewal, upgrade) are sent through `AfterCommit` only after commit and only once; the gateway settlement itself sends none, so there is a single receipt. Stray empty `{ }` block removed. `crearIntencion`: unit from the session only (no cross-unit fallback), PAGADA_PARCIAL payable, millisecond references, `IllegalStateException`/`NoSuchElementException` instead of `RuntimeException`. `WompiController` keeps exception types (404/400/409) and `GET /pagos/registrados` lists explicit columns. |
| F06-05, F6-13, F6-15 | `GastosController`: transactional writes; `domain/BudgetExecution` applies the net change to every affected budget (move, re-amount, cancel; cancelled expenses do not count) under row locks in id order; target budget must belong to the session property (404); unknown status refused (was silently replaced). `ConciliacionController`: accepts `bancoCuenta` or `cuentaBanco`, persists the selected status on create, refuses CONCILIADO while bank and books differ, one reconciliation per account and period (409 + unique index), repeated PATCH to the same status is a no-op, proper 4xx. `PresupuestoController`: persists BORRADOR/APROBADO/CERRADO (the page sent ACTIVO/INACTIVO, which the table rejects), validates input, 404/409. Pages: `ConciliacionPage`, `PresupuestoPage`. |
| F10-06 | `finanzas/service/FineReceivablesAdapter` implements `FineReceivablesPort`: locks the MULTAS row, returns the linked fee when present, otherwise inserts one fee guarded by `NOT EXISTS (ID_MULTA_ORIGEN)` and links it; a retry or a crash between insert and link returns the same fee. `MultaServiceImpl.pagar` refuses a fine that is already in the receivables (no second collection path), and `crearIntencion` refuses a fine with a fee. Paz y salvo already excludes fines with `ID_CUOTA`. |
| F9 integration | `ContratoProveedorRepository`: calls `ContratoProveedorValidator`, foreign/unknown supplier or contract gives `NoSuchElementException` (404, no organization named), `listar` requires the session property, `actualizarEstado` validates VIGENTE/VENCIDO/TERMINADO/SUSPENDIDO. |
| Resident dashboard | `GET /residentes/{id}/dashboard`: a resident outside the caller's scope is 404 (was 200 echoing the id); household members see only themselves, administrators only people with a unit in their property; the fee list covers the units the person belongs to (resident, owner, main tenant), not only rental contracts. |
| F06-06, DEC-02, F6-09 | RESIDENTE_CONVIVENCIA is denied at the service layer for payments, fees, contracts and dashboards; SUPERADMIN denied for contracts, payments, dashboards, unit statements and contract participants. `getContratoDetalle` reads under the caller's context (no privilege elevation) and out-of-scope is 404. Fee totals are computed (`VALOR_BASE - VALOR_DESCUENTO + VALOR_INTERES_MORA`), not read from the constant virtual column. Not-found cases raise `NoSuchElementException`. First rent fee of a contract: no MERGE, no swallowed errors, no arbitrary concept fallback. |

## Evidence (RED then GREEN, fresh surefire reports)

| Test class | RED | GREEN |
|---|---|---|
| PaymentSingleDebitOwnerTest | 14 tests, 11 failing | 14/0 |
| OutstandingBalanceSemanticsTest | 11 tests, 10 failing | 11/0 |
| FeeCalculatorTest, BillingServiceTest, BillingRepositorySqlTest | compile (API absent) | 33/0 |
| WompiEventBindingTest | 20 tests, 20 failing | 19/0 (one derived-key case dropped, see below) |
| WompiControllerErrorMappingTest | 3/3 failing | 3/0 |
| GastoBudgetExecutionTest, ConciliacionControllerTest | 22 tests, 16 failing | 22/0 |
| ContratoProveedorRepositoryTest | 11 tests, 10 failing | 11/0 |
| FinanceScopeAndPrivacyTest | 12 tests, 10 failing | 12/0 |
| FineReceivablesAdapterTest | compile (adapter absent) | 10/0 |
| ContractFeeGenerationTest, PresupuestoControllerTest | written with the change | 2/0, 7/0 |

Adapted: OrdinaryPaymentRegistrationIntegrityTest (no Java debit), WompiWebhookSettlementFailureTest (environment, currency),
Phase9ProveedoresIntegrationTest (foreign supplier is 404), WompiSaaSSegregationSecurityTest (event carries `environment`).
Final: `-Dsaed.test.tier=unit` with compile and checkstyle: 114 classes, 1009 tests, 0 failures, 0 errors, 0 skipped.
Python harness (`scripts/verification`): 98 tests OK. Frontend `npm run lint`: 0 errors (340 pre-existing warnings); `npm run build`: OK.

## Migrations (V5.50-V5.53)

- `V5.50__pago_cuota_debito_unico_trigger.sql`: `CREATE OR REPLACE` of the two triggers above (+ `ENABLE`).
- `V5.51__configuracion_cobro_propiedad.sql`: new table `CONFIGURACION_COBRO_PROPIEDAD` (defaults only in `CREATE TABLE`), checks, `POL_RLS_PROP_CONFIG_COBRO` via `FN_FILTRO_PROPIEDAD`.
- `V5.52__conciliaciones_unicas_por_cuenta_y_periodo.sql`: unique function-based index `(ID_PROPIEDAD, UPPER(BANCO_CUENTA), PERIODO)` (aborts with ORA-20972 if duplicates exist) and `CK_CONCIL_CONCILIADO_SALDOS` (NOVALIDATE).
- `V5.53__cuotas_origen_multa.sql`: `CUOTAS.ID_MULTA_ORIGEN` (no default) + FK, `UQ_CUOTA_MULTA_ORIGEN`, `UQ_CUOTA_UNICA_ORIGEN` (unit, concept, period, NVL(origin, 0)) replacing `UQ_CUOTA_UNICA`, trigger `TRG_CUOTA_MULTA_PAGADA` (fine marked PAGADA when its fee is).

## For the coordinator to verify on Oracle

1. V5.50-V5.53 apply as the non-exempt owner under VPD (V5.53 drops `UQ_CUOTA_UNICA`; V5.52 aborts on duplicate reconciliations).
2. Debit arithmetic: debt 100, pay 30 gives 70 and `PAGADA_PARCIAL` for (a) manual: PAGOS PENDIENTE + detail, then approve; (b) gateway: PAGOS APROBADO then detail. Pending and rejected leave 100. A second approval fails with 409/IllegalState and leaves 70. Two concurrent approvals: exactly one succeeds. Payment 70 more gives 0 and `PAGADA`.
3. SQL syntax that mocks cannot prove: `CarteraRecalculation` (multi-column `UPDATE ... SET (a,b,c,d) = (SELECT ...)`, `INSERT ... SELECT ... FROM (... GROUP BY) t`), recalculation with a partial fee and with `COBRO_JURIDICO`; `BillingRepository.findOutstandingFees` (`FOR UPDATE OF c.SALDO_PENDIENTE` with an `IN (subquery)`), `markOverdue`, `insertFeeIfAbsent` with null discount date, `ensureAdministrationConcept` under the CONCEPTOS_COBRO org policy; dashboard unit lookup (`UNION` inside `IN`, `FETCH FIRST`); the pending-intent query (`SYSTIMESTAMP - INTERVAL '15' MINUTE`).
4. Billing: generate a period for a property with an approved EGRESO budget and coefficients (sum 1): fees add up to budget/12; replay creates 0; fixed fee; with discount 10 % / 5 days then `vencimientos` after day 5 restores the balance once; with interest 2 % the accrual is once per started month.
5. Wompi (sandbox): `WOMPI_ENVIRONMENT=SANDBOX` set; event with `environment: test` settles and fills `TRANSACCIONES_PAGO.ID_PAGO`; `prod` event, missing currency, foreign-unit fee, reused provider id are ignored; one receipt after commit.
6. Fines: resolving a sanction with a fine creates exactly one fee (`ID_MULTA_ORIGEN`), two fines of the same concept in one month both post, paying the fee marks the fine PAGADA (`TRG_CUOTA_MULTA_PAGADA`), the fine path refuses it.
7. DB-tier suites that may need fixture updates (not run): `ManualPaymentApprovalSecurityTest` (registration above balance is now 400, approval of an over-balance payment is refused), `WompiPaymentFlowAdversarialTest`, `PazYSalvoFinancialIntegrityTest`, `FlujoCajaFinancialIntegrityTest`, `Phase1IFinanzasIntegrationTest`, `PhaseBFinanzasAdversarialTest`.

## Open items

- `createContrato`/`actualizarEstadoContrato` still use `setElevatedContext()` (SUPERADMIN bootstrap) for lookups and swallow its errors; `restoreSaedContext` too. Replace with caller-context reads (contracts/F5 owner).
- Excess-payment disposition and void/refund reversal need a business decision (payments are flagged `SOBREPAGO`, not refunded). DEC-08 interest is simple monthly on unpaid principal; no legal usury table is encoded (optional per-property ceiling only).
- Gastos: hard delete still cascades the support history (F6-13 recommends cancel instead); supporting file still optional (F6-14); reconciliation remains a manual header (no statement import, books balance not derived from PAGOS/GASTOS, F6-15).
- F6-16 segregation of duties (registrant vs approver) and the idempotency-key duplicate handling are not done; F6-19 renewal cycle heuristic, F6-20 legacy MEMBRESIA path, F6-23 certificate wording/rate limit and F6-25 `@RequireModule` gating of the remaining finance controllers are untouched.
- The V5.17 trigger text remains in `ProductionSchemaInitializer` (legacy flag); remove with the F1 startup cleanup.
- F12 must schedule `BillingService` per property (`generarCuotasMensuales` monthly, `procesarVencimientos` daily) under that property's administrator context; `WompiEventBindingTest` dropped the "derive environment from key prefix" case because `ProductionConfigurationSafetyTest` forbids key prefixes in main sources (the environment is configuration-only).
