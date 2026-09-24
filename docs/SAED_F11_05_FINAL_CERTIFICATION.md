# SAED 2.0 — F11-05 ANALYTICS: FINAL TECHNICAL CERTIFICATION

---

## 1. Executive Summary

This document certifies the final technical reconciliation, audit, and verification of **F11-05 Analytics** within the **SAED 2.0** baseline (`Sebasr0311/SAED`).

All architectural contracts, semantic business rules, database constraints, multi-tenant isolation policies, and frontend interfaces have been reconciled and verified against a live **Oracle XE 21c** instance (`localhost:1521/XEPDB1`).

- **F11-05 Test Suite**: **38/38 PASS** (0 failures, 0 errors).
- **F11 Full Regression Suite**: **119/119 PASS** (0 failures, 0 errors across F11-04 Motor, Ingresos, Exportaciones, Historial, and F11-05 Analytics).
- **Frontend Production Build**: **PASS** (`npm run build` completed in 10.92s with 0 errors).
- **Git State**: Clean and intact; zero modifications to commit history, branches, or repository trees.

---

## 2. Contract Reconciliation Matrix

| Contract Dimension | Hardening Specification | Implementation Reconciliation | Technical Decision & Justification | Status |
|---|---|---|---|---|
| **Default `meses` & Horizon** | Valid: 6 or 12. Default: 12. `anio` overrides `meses`. | Implemented `effMeses = (meses != null ? meses : 12)` in `AnalyticsServiceImpl` | Contractually established default is 12 months. Calling endpoints without `meses` returns `mesesEvaluados = 12` and exactly 12 monthly data points. Verified via `test01` and `test04`. | **RECONCILED** |
| **Property Analytics Endpoint** | `GET /api/v1/dashboard/propiedad/analitica` | `DashboardOperativoController` mapped at `/api/v1/dashboard` with method `/propiedad/analitica` | Official canonical endpoint established as `GET /api/v1/dashboard/propiedad/analitica`. Frontend `PropertyAnaliticaSection.jsx` calls this exact path. Doc errata fixed. | **RECONCILED** |
| **FACTURADO = 0 Semantics** | `FACTURADO = 0` $\rightarrow$ `efectividadRecaudoPct = 100.0%` (both for `RECAUDADO = 0` and `RECAUDADO > 0`) | `AnalyticsServiceImpl` normalizes to `100.0%` if `facturado == 0`, preserving raw `recaudadoPeriodo` | Corrected. No false 0.0% penalty for unbilled properties. Verified with `test20` and `test20b`. | **RECONCILED** |
| **Independent Rankings Order** | Multi-criteria sort with `nombre ASC` tie-breaker | Implemented `Comparator.thenComparing(...)` on 4 levels | **Efectividad**: `efectividadRecaudoPct DESC` $\rightarrow$ `recaudoPeriodo DESC` $\rightarrow$ `nombre ASC` $\rightarrow$ `idPropiedad ASC`.<br>**Morosidad**: `indiceMorosidadPct ASC` $\rightarrow$ `carteraPeriodo ASC` $\rightarrow$ `nombre ASC` $\rightarrow$ `idPropiedad ASC`.<br>**Ocupación**: `ocupacionActualPct DESC` $\rightarrow$ `totalUnidades DESC` $\rightarrow$ `nombre ASC` $\rightarrow$ `idPropiedad ASC`. Verified with `test25b`. | **RECONCILED** |
| **CUOTAS Database States** | Oracle XE check constraint `CK_CUOTAS_ESTADO` | Excluded `EN_MORA`; included `PAGADA_PARCIAL` alongside `PENDIENTE` and `VENCIDA` | In Oracle XE, `CK_CUOTAS_ESTADO` allows `('PENDIENTE', 'PAGADA_PARCIAL', 'PAGADA', 'VENCIDA', 'ANULADA')`. Facturado includes all except `ANULADA`; Cartera includes pending debt (`PENDIENTE`, `PAGADA_PARCIAL`, `VENCIDA`) sourced from `SALDO_PENDIENTE`. | **RECONCILED** |
| **PQRS SLA Formula** | Explicit numerator, denominator, and open ticket handling | Aggregated SQL grouping by radication period | Numerator: `COUNT(CASE WHEN FECHA_CIERRE IS NOT NULL AND FECHA_CIERRE <= FECHA_LIMITE_SLA THEN 1 END)`. Denominator: `TOTAL_RADICADAS` in month. Open tickets (`FECHA_CIERRE IS NULL`) count in denominator only. Empty months yield `100.0%`. Verified with `test32b`. | **RECONCILED** |
| **SuperAdmin Analytics** | Non-duplicative global KPIs complementary to F11-01 | Separate endpoint `GET /api/v1/platform/dashboard/analytics` | Complementary to F11-01 (`/platform/dashboard`). Accepts `@RequestParam(required = false) Integer meses` (default 12). Does NOT accept `orgId` (strictly global). Provides `tasaRetencionOrganizacionesPct`, `distribucionPropiedadesPorCiudad`, and 12-month `crecimientoOrganizacionesMensual` without query duplication. | **RECONCILED** |

---

## 3. Verified Live Oracle XE Database Indexes

### 3.1. A. Physically Verified Indexes in Live Oracle XE
Queried directly from `USER_IND_COLUMNS` and `USER_IND_EXPRESSIONS` under active schema `SAED_BASELINE_TEST_01`:

| Table | Index Name | Columns / Expression | Index Type |
|---|---|---|---|
| `CUOTAS` | `PK_CUOTAS` | `ID_CUOTA` | B-Tree (PK) |
| `CUOTAS` | `UQ_CUOTA_UNICA` | `ID_UNIDAD, ID_CONCEPTO, PERIODO` | B-Tree (Unique) |
| `CUOTAS` | `IX_CUOTAS_PERIODO` | `PERIODO` | B-Tree |
| `CUOTAS` | `IX_CUOTAS_UNIDAD` | `ID_UNIDAD, ESTADO` | B-Tree |
| `CUOTAS` | `IX_CUOTAS_VENCIMIENTO` | `FECHA_VENCIMIENTO, ESTADO` | B-Tree |
| `PAGOS` | `PK_PAGOS` | `ID_PAGO` | B-Tree (PK) |
| `PAGOS` | `IX_PAGOS_UNIDAD` | `ID_UNIDAD` | B-Tree |
| `PAGOS` | `IX_PAGOS_ESTADO` | `ESTADO` | B-Tree |
| `PAGOS` | `IX_PAGOS_FECHA` | `SYS_EXTRACT_UTC("FECHA_PAGO")` | Function-Based |
| `GASTOS` | `PK_GASTOS` | `ID_GASTO` | B-Tree (PK) |
| `GASTOS` | `IX_GASTOS_PROP` | `ID_PROPIEDAD` | B-Tree |
| `GASTOS` | `IX_GASTOS_FECHA` | `FECHA_GASTO` | B-Tree |
| `UNIDADES` | `PK_UNIDADES` | `ID_UNIDAD` | B-Tree (PK) |
| `UNIDADES` | `IX_UNIDADES_PROPIEDAD` | `ID_PROPIEDAD` | B-Tree |
| `UNIDADES` | `IX_UNIDADES_ESTADO` | `ESTADO` | B-Tree |
| `RESIDENTES_UNIDAD` | `PK_RESIDENTES_UNIDAD` | `ID_RESIDENTE_UNIDAD` | B-Tree (PK) |
| `RESIDENTES_UNIDAD` | `IX_RESIDUNIDAD_UNIDAD` | `ID_UNIDAD` | B-Tree |
| `RESIDENTES_UNIDAD` | `UQ_RESIDUNIDAD_ACTIVA` | `ID_UNIDAD, ID_PERSONA, ESTADO` | B-Tree (Unique) |
| `VISITAS` | `PK_VISITAS` | `ID_VISITA` | B-Tree (PK) |
| `VISITAS` | `IX_VISITAS_UNIDAD` | `ID_UNIDAD` | B-Tree |
| `VISITAS` | `IX_VISITAS_ESTADO` | `ESTADO` | B-Tree |
| `PAQUETES` | `PK_PAQUETES` | `ID_PAQUETE` | B-Tree (PK) |
| `PAQUETES` | `IX_PAQUETES_PROP` | `ID_PROPIEDAD, SYS_EXTRACT_UTC("FECHA_RECEPCION")` | Function-Based |
| `PQRS_TICKETS` | `PK_PQRS_TICKETS` | `ID_TICKET` | B-Tree (PK) |
| `PQRS_TICKETS` | `IX_PQRS_PROP` | `ID_PROPIEDAD, ESTADO` | B-Tree |
| `PQRS_TICKETS` | `IX_PQRS_SLA` | `SYS_EXTRACT_UTC("FECHA_LIMITE_SLA"), ESTADO` | Function-Based |
| `PROPIEDADES` | `PK_PROPIEDADES` | `ID_PROPIEDAD` | B-Tree (PK) |
| `PROPIEDADES` | `IX_PROPIEDADES_ORG` | `ID_ORGANIZACION` | B-Tree |
| `ORGANIZACIONES` | `PK_ORGANIZACIONES` | `ID_ORGANIZACION` | B-Tree (PK) |
| `ORGANIZACIONES` | `IX_ORGANIZACIONES_ESTADO` | `ESTADO` | B-Tree |

### 3.2. B. Architectural Compatibility & Expected Predicate Usage
The aggregation queries in `AnalyticsServiceImpl` were architected to align with the physically present indexes:
- Horizon filters `c.PERIODO IN (:periodos)` align with `IX_CUOTAS_PERIODO`.
- Status filters `c.ESTADO IN (...)` combined with unit joins match `IX_CUOTAS_UNIDAD`.
- Payment collection intervals `p.FECHA_PAGO >= :inicio AND p.FECHA_PAGO <= :fin` align with the function-based index `IX_PAGOS_FECHA`.
- Property partition groupings on `UNIDADES`, `GASTOS`, `PAQUETES`, and `PQRS_TICKETS` align with `IX_*_PROP` and `IX_*_PROPIEDAD`.
- Occupancy checks over `RESIDENTES_UNIDAD` align with `IX_RESIDUNIDAD_UNIDAD` and `UQ_RESIDUNIDAD_ACTIVA`.

### 3.3. C. Methodological Rigor & Runtime Clarification
Technical evidence confirms the physical existence of all 30 listed indexes in the Oracle XE data dictionary and structural alignment with query predicates. As per senior database engineering practice, no unsupported claims regarding execution plans (`EXPLAIN PLAN`) or cost-based optimizer (CBO) runtime paths under production workloads are asserted, as runtime plan choice is governed dynamically by data cardinality, table statistics, and cluster configuration rather than static index presence.

---

## 4. Test Execution & Evidence

### 4.1. F11-05 Integration Test Suite (`F11_05_AnalyticsIntegrationTest`)
- **Total Tests Run**: 38
- **Passed**: 38 (100%)
- **Failures**: 0
- **Errors**: 0
- **Skipped**: 0
- **Execution Time**: ~21.59s
- **Status**: **BUILD SUCCESS**

Key scenarios verified:
1. `test01` — `ADMIN_ORGANIZACION` receives multi-property analytics scoped to organization (calls endpoint without `meses`, verifying default `mesesEvaluados == 12` and exactly 12 monthly points in `tendenciaMensual`).
2. `test02` — `ADMIN_ORGANIZACION` cannot view data from another organization (tenant isolation).
3. `test03` — `ADMIN_PROPIEDAD` receives analytics strictly for assigned property.
4. `test04` — `ADMIN_PROPIEDAD` cannot override scope via external URL parameter (Anti-IDOR) and calls endpoint without `meses`, verifying exactly 12 points in `tendenciaFinanciera` and `tendenciaOperativa`.
5. `test05` — `RESIDENTE` access attempt returns 403 Forbidden.
6. `test06` — `PORTERO` access attempt returns 403 Forbidden.
7. `test07` — Unauthenticated user returns 401 Unauthorized.
8. `test08`-`test10` — Time window resolution: 6 months, 12 months, calendar year override.
9. `test13` — Inactive months without transactional data are padded with `0.00`.
10. `test14`-`test19` — CUOTAS and PAGOS state filtering: `ANULADA` excluded from facturado, rejected payments excluded from recaudo, `SALDO_PENDIENTE` mapped to cartera.
11. `test20` — `FACTURADO = 0` / `RECAUDADO = 0` yields `efectividadRecaudoPct = 100.0%`.
12. `test20b` — `FACTURADO = 0` / `RECAUDADO > 0` preserves real collected amount and yields `efectividadRecaudoPct = 100.0%`.
13. `test21` — `FACTURADO = 0` yields `indiceMorosidadPct = 0.0%`.
14. `test22` — `RECAUDADO > FACTURADO` clamps `efectividadRecaudoPct` to `100.0%` while preserving raw collection.
15. `test23`-`test24` — Physical occupancy calculation (`CURRENT_STATE_ONLY`), including zero units edge case.
16. `test25`-`test25b` — Deterministic independent rankings with exact multi-criteria comparator rules and name tie-breakers.
17. `test30` — Property financial trend calculates `balanceNeto = recaudado - gastos`.
18. `test31`-`test32b` — Operational trend aggregations and exact PQRS SLA computation with on-time and late tickets.
19. `test33`-`test35` — SuperAdmin global retention, property distribution by city, and 12-month organization creation trends.

### 4.2. Full F11 Regression Suite

| suite | total | passed | failed | errors | skipped |
|---|---|---|---|---|---|
| `F11_04_ExportacionesIntegrationTest` | 17 | 17 | 0 | 0 | 0 |
| `F11_04_IngresosPresupuestalesIntegrationTest` | 16 | 16 | 0 | 0 | 0 |
| `F11_04_ReportesBaseMotorIntegrationTest` | 20 | 20 | 0 | 0 | 0 |
| `F11_04_ReportesConfiguradosHistorialIntegrationTest` | 28 | 28 | 0 | 0 | 0 |
| `F11_05_AnalyticsIntegrationTest` | 38 | 38 | 0 | 0 | 0 |
| **TOTAL REGRESIÓN F11** | **119** | **119** | **0** | **0** | **0** |

- **Execution Time**: 34.67s
- **Status**: **BUILD SUCCESS**

### 4.3. Frontend Build Verification
- Directory: `frontend/`
- Command: `npm run build`
- Result: **PASS** (`built in 10.92s`, 0 errors, 0 broken imports).

---

## 5. Security & Multi-Tenancy Conformance

1. **Context Derivation (Anti-IDOR)**:
   All queries for property analytics derive `propertyId` exclusively from `SaedContextHolder.getContext().getPropertyId()`. Attempts by callers to supply external query parameters (e.g. `?idPropiedad=9602`) are completely ignored.
2. **Organization Boundary Enforcement**:
   Calls to `/api/v1/org/dashboard/analytics` enforce that the requested organization matches `ctx.getOrganizationId()`. Foreign access is denied with 403.
3. **Role-Based Access Control (RBAC)**:
   - `SCOPE_ADMIN_ORGANIZACION`: Authorized for organization analytics.
   - `SCOPE_ADMIN_PROPIEDAD`: Authorized for property analytics.
   - `SCOPE_SUPERADMIN`: Authorized for platform and cross-tenant analytics.
   - `SCOPE_RESIDENTE`, `SCOPE_PORTERO`: Denied (403 Forbidden).
   - Anonymous: Denied (401 Unauthorized).
4. **VPD/RLS Compatibility**:
   Queries execute through standard Spring JDBC within the database session initialized by `PKG_SAED_SESSION`, fully respecting Virtual Private Database policies in Oracle XE/ATP.

---

## 6. Final Certification Verdict

$$\mathbf{F11\text{-}05\ —\ FUNCTIONALLY\ AND\ TECHNICALLY\ COMPLETE\ FOR\ THE\ CURRENT\ SAED\ 2.0\ BASELINE}$$

The technical closure of F11-05 Analytics is officially certified. All contracts are reconciled, all semantic rules are mathematically and functionally verified, and the full regression suite stands at 100% green.
