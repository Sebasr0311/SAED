# F4 (properties and units) and F5 (inhabitants, guardians, contracts) remediation

Branch `fix/f0-f11-p0-remediation`, 2026-10-10/11. Single writer for F4/F5. No database connection, Docker,
deployment or push. Migrations used: **V5.35, V5.36, V5.37** (V5.38–V5.39, V5.50–V5.54 unused).

## Commits

| Commit | Scope | IDs |
|---|---|---|
| `18a79b7` | Territory (país/departamento/ciudad) and floor round-trip, identifier uniqueness incl. null block (409), DEC-04 coefficients (0<c≤1, sum ≤1.0000 under a property row lock, incomplete sum = warning via `GET /units/coefficient-summary`), updates affecting 0 rows → 404; V5.35 | F04-01, F4-H03, F4-H04, F4-H07, DEC-04 |
| `237b765` | Deletion OTP: per-challenge serialized verification, block after 5 wrong codes (also under concurrency), null/blank = wrong attempt, injectable clock, stale purge, undelivered OTP revoked and reported; OTP/provider text never logged | F04-02, F4-H02, F4-H05 |
| `6c07237` | Inactive property read-only at service layer for org-scoped callers (property update, unit create/update, blocks, property config, new assignments); status lookup fails closed | F04-03, F4-H08 |
| `553f6f4` | PERSONAS scoping (DEC-01): PUT/GET/DELETE/import scoped; no-scope list empty, detail self-only; delete unlinks only in caller scope; import never overwrites another tenant, no raw DB messages, no suffix unit match | F5-H05, F05-01, DEC-02 |
| `d7f7888` | Household: fail-closed scope, convivencia cannot manage, titular manages only non-titular members; unlink/delete without context elevation, revoking only that unit's resident assignments (admin in own context; titular via `SP_REVOCAR_ACCESO_UNIDAD`); quota fail-closed, lock-then-count, import cannot relabel CONVIVIENTE; V5.36/V5.37 | F05-01, F05-02, F5-H07, F5-H09 |
| `b4f0179` | Contracts and paz y salvo closed to RESIDENTE_CONVIVENCIA; `mi-estado-financiero` returns only the arrears flag to that role | F5-H03, F05-02 |
| `8e63553` | Guardians scoped to the dependent's unit/property; residents page links guardian after assignment and reports failures | F05-02, F5-H08 |
| `bdc343c` | Template variables HTML-escaped (server-built signature block only trusted); PDF renderer loads only `data:` URIs; previews in sandboxed iframes | F05-03, F5-H06 |

## TDD evidence

| Test class | RED | GREEN |
|---|---|---|
| UnitPropertyContractTest | compile RED | 17/17 |
| PropertyDeletionChallengeServiceTest + PropertyDeletionServiceTest | 3 failures, 1 error / 17 | 17/17 |
| InactivePropertyReadOnlyTest | compile RED (new constructors) | 24/24 (91 with related classes) |
| PersonaScopeAuthorizationTest | 10 failures / 14 (repository cases written with the repo change) | 14/14 |
| UnitInhabitantScopeTest | 7 failures / 11 | 11/11 |
| ConvivienteQuotaLockingTest | 4 failures / 6 | 6/6 |
| ConvivenciaFinanceExposureTest | 3 failures / 4 | 4/4 |
| GuardianScopeTest | 4 failures / 4 | 4/4 |
| ContractTemplateSafetyTest | compile RED only (not run before the fix) | 6/6 |

Final: `rm -rf target/surefire-reports; mvnw -o -q compile checkstyle:check test -Dsaed.test.tier=unit`
→ **83 classes, 735 tests, 0 failures, 0 errors, 0 skipped**; Checkstyle passed.
Frontend: `npm run lint` 0 errors (340 warnings), `npm run build` OK.

## Migrations (not applied anywhere; need coordinator Oracle verification)

- `V5.35__unidades_piso_territorio_coeficiente.sql`: `PROPIEDADES.DEPARTAMENTO`, `UNIDADES.PISO` (no defaults),
  `CK_UNIDADES_COEF_FRACCION` ENABLE NOVALIDATE, catalog checks + rows `PARQUEADERO_PRIVADO`, `URBANIZACION_MIXTA`.
- `V5.36__household_access_revocation_grants.sql` (app owner): SELECT on RESIDENTES_UNIDAD and
  UPDATE(ESTADO, FECHA_FIN) on USUARIO_ASIGNACIONES to the owner of PKG_AUTH_BOOTSTRAP.
- `V5.37__sec_household_access_revocation.sql` (security principal): synonym + `SP_REVOCAR_ACCESO_UNIDAD`
  (AUTHID DEFINER, validates titular/unit/inactive member from SAED_CTX), EXECUTE to PKG_AUTH_BOOTSTRAP grantees.

## Frontend

UnidadesPage (piso field, coefficient warning banner), PropiedadesPage/OrgPropiedadesPage (send
país/departamento), ResReservasPage (amount only when provided), ResidentesPage (guardian after assignment,
sandboxed preview), OrgPlantillasContratosPage (sandboxed previews). No package added.

## Verified without change

- Tenure does not rewrite runtime roles: contract code in `FinanzasServiceImpl` only reads USUARIO_ASIGNACIONES.
- No `new RuntimeException("... no encontrado")` remains in authorization/person/contratos; only platform
  controllers (Organization, Property) still grant SCOPE_SUPERADMIN.

## Open items

- Oracle unproven: V5.35–V5.37, the security procedure and its grants, VPD behavior of `unlinkFromScope`,
  the property row lock, and `DiferenciacionPropietarioResidenteTest` (fixture person must belong to the org).
- Deletion OTP challenges remain in memory (lost on restart; fail closed across instances) — durable store pending.
- F05-01 composite creation is still frontend-orchestrated (person → tutor person → assignment → guardian →
  account); each step reports failures but is not one transaction. A composite endpoint needs
  `DashboardController`/`UsuarioController` (other packages).
- Resident status changes (INACTIVO) do not revoke assignments; only unlink/delete do.
- Block/property-config services keep SUPERADMIN branches (unreachable via HTTP).
- `InactivePropertyFilter` (security package, not editable) still fails open for callers without a property;
  covered here at service level for the property/unit/block/config/assignment mutations only.
