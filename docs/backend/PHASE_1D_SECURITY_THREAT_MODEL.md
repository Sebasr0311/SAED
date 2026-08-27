# PHASE 1D SECURITY THREAT MODEL

## 1. Vulnerability Findings in Baseline (V3.9)

### 1.1 Unprotected `PERSONAS` Table (IDOR / Data Leak)
- **Vector**: An authenticated user with a valid JWT could query or insert any `PERSONA` record regardless of their organization.
- **Root Cause**: The physical V3.9 schema forgot to apply `DBMS_RLS.ADD_GROUPED_POLICY` to `PERSONAS`, leaving it entirely exposed. `FN_FILTRO_ORGANIZACION` has logic for it, but it is not hooked up.
- **Mitigation Strategy**: Must deploy `V4.3__person_rls_patch.sql` to apply the policy.

### 1.2 Non-Existent Columns in RLS Predicate (Denial of Service)
- **Vector**: A legitimate user queries `VISITANTES` or `TUTORES`. The DB throws `ORA-00904` because `FN_FILTRO_PROPIEDAD` references `id_propiedad` and `FN_FILTRO_UNIDAD` references `id_unidad`, but neither table contains those columns.
- **Root Cause**: The V3.9 dynamic RLS functions naively assumed all tables in the grouped policy had `id_propiedad` or `id_unidad`. 
- **Mitigation Strategy**: The `V4.3` patch must modify the PKG body to map `VISITANTES` via `id_persona` -> `PERSONAS` -> `VISITAS` -> `UNIDADES` -> `PROPIEDADES`, and map `TUTORES` via `id_persona_menor` -> `RESIDENTES_UNIDAD` -> `UNIDADES`.

## 2. Injection & Mutation Threats
- **Context Bleed**: Evaluated and mitigated in Phase 1B. The connection pool safely clears the context.
- **Cross-Tenant Insert**: A user from Org 1 attempts to add an owner to a Unit in Org 2.
  - **Defense**: Oracle RLS `update_check = TRUE` will evaluate `FN_FILTRO_UNIDAD` on the new row. It will fail the check and raise `ORA-28115`, preventing the insert.

## 3. Principle of Least Privilege
The Spring application will continue to use the `SAED_APP` database user. This user only holds `CREATE SESSION`. Oracle RLS enforces the tenant boundaries unconditionally.
