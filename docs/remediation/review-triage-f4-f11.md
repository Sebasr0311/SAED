# Cross-model review triage — F4/F5, F9, F11 (2026-10-11)

Independent read-only review by Gemini 3.1 Pro (Antigravity) of `e7fb51f..8f9e3d9`. Every finding was checked against the code before any change. A Codex security review of the same range was started but was stopped by the host for low memory and was not re-run.

| # | Finding | Verdict | Evidence / action |
|---|---|---|---|
| 1 | Report download fails with 405 (GET vs POST) | **False** | `ReportesPage.jsx:110` passes `{ method: 'POST' }`; the reviewer read only the utility default. |
| 2 | `/generar` without `formato` returns JSON metadata instead of the template's default format | Real, low | The UI always sends `formato`. API-only callers get metadata. Open (follow-up). |
| 3 | ADMIN_ORGANIZACION cannot write consumption readings | Not a regression | Same `@PreAuthorize` as the baseline (`7d26e89`); organization admins operate at organization level. |
| 4 | Editing a stored meter-reset reading erases the reset and the previous reading | **Real — fixed** | `92ae81a`: `update` preserves a stored reset when the edit sends the 0 baseline without the flag. RED 15/1 → GREEN 15/0. |
| 5 | KPI counts a ticket at exactly 100 % of SLA as overdue (DEC-05 and `PqrsSlaPolicy`: only past 100 %) | **Real — fixed** | `af648c8`; the golden test encoded the wrong boundary and was corrected. RED 6/1 → GREEN 6/0. |
| 6 | Null unit id inflates units in arrears | **False** | `FeeRow.unitId` is a primitive `long`; `CUOTAS.ID_UNIDAD` is NOT NULL. |
| 7 | PROPIETARIO cannot use PQRS | Not applicable | PROPIETARIO is a tenure, not one of the six runtime profiles (D01); the titular owner acts as RESIDENTE. |
| 8 | ADMIN_ORGANIZACION cannot manage incidents | Not a regression | `IncidenteController` grants are identical to the baseline. |
| 9 | Renewal notices / maintenance orders require a property context, blocking organization admins and schedulers | Real, by design for request paths | Scheduler execution needs a tenant-scoped system path: **F12 requirement**. |
| 10 | ADMIN_ORGANIZACION cannot view/create/edit units by id | Not a regression | Baseline grants unchanged (one more organization grant than before). |
| 11 | V5.37 household revocation fails for a PROPIETARIO assignment without unit | Not applicable | Titular residents hold a unit-scoped RESIDENTE assignment; PROPIETARIO is not a runtime profile. |
| 12 | ADMIN_ORGANIZACION cannot manage guardians | Not a regression | Property-scoped operation, consistent with #3/#8/#10. |
| 13 | `PersonaServiceImpl` titular check returns false without unit | Not applicable | Same as #7/#11. |
| T | Tests that would pass with defects | Partly real | Covered by the new tests for #4 and #5; the reflection-only reopen test remains and the report `GET` concern is moot (#1). |

Also checked: the F11 note that the PQRS fallback SLA was 12/24/72/120 h is outdated — `TicketServiceImpl.fallbackHorasSla` delegates to `PqrsSlaPolicy.defaultHours` (DEC-05). Per-property rows in `PQRS_SLA_CONFIGURACION` keep precedence by design.

Result: 2 real defects fixed, 1 low open, 1 carried to F12; 3 false positives; 7 not regressions or not applicable. Unit tier after fixes: 114 classes / 1011 tests.
