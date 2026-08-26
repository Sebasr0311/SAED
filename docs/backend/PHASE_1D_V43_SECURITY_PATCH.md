# SAED 2.0 - Phase 1D V4.3 Security Patch Report

## 1. Vulnerability Findings in V3.9 Baseline

During the preparation for Phase 1D (Person & Inhabitant Management), a physical audit of the `V3.9__baseline_multitenant.sql` baseline script revealed three critical discrepancies affecting Row-Level Security (RLS) enforcement on the Oracle Database.

1. **`PERSONAS` Unprotected:** While `PKG_SAED_SECURITY_RLS.FN_FILTRO_ORGANIZACION` contained logic for the `PERSONAS` table, the actual `DBMS_RLS.ADD_GROUPED_POLICY` registration for the table was omitted in the V3.9 baseline. This resulted in `PERSONAS` being completely unprotected at the database layer (IDOR and cross-tenant data exposure).
2. **`VISITANTES` Predicate Error:** The `VISITANTES` table was protected by `FN_FILTRO_PROPIEDAD`, which evaluated `id_propiedad = v_prop`. However, the physical table `VISITANTES` does not possess an `id_propiedad` column. Any query against this table inside a property context would crash with `ORA-00904: invalid identifier`.
3. **`TUTORES` Predicate Error:** The `TUTORES` table was protected by `FN_FILTRO_UNIDAD`, which evaluated `id_unidad IN (...)`. However, `TUTORES` does not possess an `id_unidad` column, causing queries to crash with `ORA-00904: invalid identifier`.

## 2. V4.3 Patch Implementation

A security patch (`V4.3__person_rls_patch.sql`) was created and executed to resolve these issues physically within the database.

### `PERSONAS` RLS Policy
- Added `DBMS_RLS.ADD_GROUPED_POLICY` for `PERSONAS` using `FN_FILTRO_ORGANIZACION`.
- **Insert Catch-22 Resolved:** Because a new person has no relations (no `USUARIOS`, `VISITANTES`, or `PROPIETARIOS_UNIDAD`) at the exact moment of `INSERT`, `update_check => FALSE` was used during policy registration. This allows the application to gracefully insert the `PERSONA` and link it to an organization in a single transaction without triggering `ORA-28115`.

### `VISITANTES` Predicate Fix
- Modified `FN_FILTRO_PROPIEDAD` to enforce isolation via the physical relational path: `VISITANTES` -> `VISITAS` -> `UNIDADES` -> `PROPIEDADES`.
- **Property Context:** `id_visitante IN (SELECT id_visitante FROM VISITAS JOIN UNIDADES ON VISITAS.id_unidad = UNIDADES.id_unidad WHERE UNIDADES.id_propiedad = v_prop)`
- **Organization Context:** Evaluates the same path but joins `PROPIEDADES` to verify `id_organizacion = v_org`.

### `TUTORES` Predicate Fix
- Modified `FN_FILTRO_UNIDAD` to enforce isolation via the physical relational path: `TUTORES` (`id_persona_menor`) -> `RESIDENTES_UNIDAD` (`id_persona`) -> `UNIDADES`.
- **Unit Context:** `id_persona_menor IN (SELECT id_persona FROM RESIDENTES_UNIDAD JOIN UNIDADES ON RESIDENTES_UNIDAD.id_unidad = UNIDADES.id_unidad WHERE UNIDADES.id_propiedad = v_prop)`
- **Organization Context:** Evaluates the same path joining up to `PROPIEDADES` for `id_organizacion`.

## 3. Impact Assessment
- The `SUPERADMIN` 1=1 bypass established in V4.2 remains intact.
- STATE 0 (Anonymous), STATE 1 (Bootstrap), and STATE 2 (Authorized) contexts correctly apply to the patched entities.
- Zero modifications were made to Spring Boot code. The authority of isolation strictly remains inside Oracle RLS.
