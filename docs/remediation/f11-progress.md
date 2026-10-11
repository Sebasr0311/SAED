# F11 progress — dashboards, reports, consumption

Branch `fix/f11-analytics`; merged `fix/f0-f11-p0-remediation` cleanly. Verified: unit tier 865/865, Python harness 98/98, frontend lint (0 errors) and build.

## Fixed items

| ID | Commit | Result |
|---|---|---|
| F11-01 configured reports | `d329565` | Scope from `SaedContextHolder` only: property admin bound to its property, organization admin to its own properties (`propertyBelongsToOrganization`), SUPERADMIN denied (DEC-02), absent org/property/user context denies (no more fallback to org/user 1). Malformed filter JSON, unknown filter keys, invalid dates/periods, inverted ranges, unknown states and unknown export formats → 400. Foreign configurations/history → 404 (indistinguishable). JSON generation runs once. Base report engine: same rules; cartera morosa counts `PAGADA_PARCIAL`, drops the non-existent `EN_MORA`. |
| F11-04 exports | `d6c62bb` | CSV/XLSX text cells starting with `= + - @ \t \r` are prefixed with `'`; numbers stay numeric, dates ISO. New dependency-free deterministic XLSX writer (inline strings only, XML-illegal chars stripped). Exports carry the server-resolved scope; unknown formats fail before querying; volume limit (10 000) applies to XLSX; explicit thread-safe COP formatting; no raw exception messages. PDF verified by rendering and parsing with PDFBox (Unicode, filter dates, totals). |
| F11-03 consumption | `1e29add` | Policy: readings ≥ 0; a lower current reading is rejected unless `reinicioMedidor=true`, stored with baseline 0 and the previous value in `LECTURA_PREVIA_REINICIO`; a reset flag without rollback is rejected. Anomaly (>150 % of meter average, edited reading excluded) recomputed on create and edit; manual anomaly kept only with its own observation. Last-reading contract returns `encontrada` + reading fields (no fabricated 0), honours common-area meters (`ID_UNIDAD IS NULL`) and the target period. `areasComunes` list filter, trend window honoured, residents bound to their unit (audit F11-05), 404/403 instead of 400/409. Frontend page fixed (field names, filter, contract, reset checkbox). |
| F11-02 dashboards | `e567981`, `37ddbd5` | `GET /api/v1/dashboard/kpis`: six profiles from context (platform, organization, property, porter, titular, conviviente without finances). `KpiCalculator`: collection rate, outstanding/overdue portfolio, % units in arrears, PQRS open/at-risk/overdue (DEC-05), reservations, 30-day churn; null instead of fabricated rates. Removed swallowed exceptions, CARTERA fallback, `EN_MORA`, fabricated latency/engine/scheduler values, tenant audit activity and operational/payment data from SUPERADMIN views (DEC-02). Shared `KpiSummaryPanel` on all dashboard pages. |

## RED → GREEN

| Test class | RED | GREEN |
|---|---|---|
| ConfiguredReportScopeTest | 17/20 failing | 20/20 |
| ReportEngineScopeTest | 5/7 failing | 7/7 |
| ReportExportFormatsTest | 4 failures + 1 error /6 | 6/6 |
| ConsumptionReadingPolicyTest, ConsumptionRepositorySqlTest | compile (API absent) | 19/19 |
| KpiCalculatorGoldenTest, DashboardKpiProfilesTest | compile (API absent) | 13/13 |
| PlatformAnalyticsDec02Test | 2/2 failing | 2/2 |

Database-tier tests updated to DEC-02 (SUPERADMIN → 403) and new signatures; not executed here (coordinator runs Oracle).

## Migrations
- `V5.80__mediciones_consumo_reinicio_medidor.sql`: `REINICIO_MEDIDOR` (added without default, then `MODIFY DEFAULT 'N'` + `UPDATE`), `LECTURA_PREVIA_REINICIO`, checks `CK_MEDICIONES_REINICIO`, `CK_MEDICIONES_REINICIO_BASE`. Needs Oracle verification.

## Integration points / open items
- F6: resident financial dashboard (`/residentes/{id}/dashboard`) stays in `finanzas`; KPIs read `CUOTAS` directly. Collection rate covers the last 12 periods plus any open balance.
- PQRS: `TicketServiceImpl` fallback SLA (12/24/72/120 h) differs from DEC-05 (1/4/24/72 h) used by KPIs — PQRS owner should align.
- F12: no automation here; meter-reset/anomaly alerts could become F12 events.
- `dashboard/controller/DashboardController` (`/api/v1/residentes`) is resident management (F5), untouched; it still swallows exceptions.
- Client-side XLSX in `exportUtils.js` uses typed string cells (safe); it is not audited server-side (audit F11-08).
