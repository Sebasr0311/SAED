# PHASE 1D CURRENT STATE AUDIT

## 1. Objective
Identify the next logical functional module after Tenant Management (Phase 1C) and audit its baseline state in the V3.9 architecture.

## 2. Identified Module: Person & Inhabitant Management
With the core tenant hierarchy established (Organizations, Properties, Units, and Admin Assignments), the next architectural requirement is managing the inhabitants and their real-world entities.

**Core Entities:**
- `PERSONAS` (The foundational entity for users, owners, residents, and visitors)
- `PROPIETARIOS_UNIDAD` (Unit Owners)
- `RESIDENTES_UNIDAD` (Unit Residents)
- `VISITANTES` (Visitors)
- `TUTORES` (Legal guardians for minors)
- `MASCOTAS` (Pets associated with units/people)
- `VEHICULOS` (Vehicles associated with units/people)

## 3. Baseline Audit (V3.9 Physical Schema)
An inspection of `V3.9__baseline_multitenant.sql` reveals severe structural anomalies in the RLS application for this specific module:

### 3.1 Unprotected Data (CRITICAL)
- **`PERSONAS`**: 
  - **Issue**: The `PKG_SAED_SECURITY_RLS.FN_FILTRO_ORGANIZACION` contains filtering logic specifically written for `p_tab = 'PERSONAS'`. However, the corresponding `DBMS_RLS.ADD_GROUPED_POLICY` statement was **omitted** in the baseline script.
  - **Impact**: The table is physically completely unprotected. Any database query against `PERSONAS` circumvents RLS entirely.

### 3.2 ORA-00904 Invalid Identifier Crashes
- **`VISITANTES`**:
  - **Issue**: Protected by `FN_FILTRO_PROPIEDAD`. The function defaults to appending `id_propiedad IN (...)` or `id_propiedad = ...`. However, `VISITANTES` does not have an `id_propiedad` column.
  - **Impact**: Queries by non-superadmins will instantly crash with `ORA-00904: "ID_PROPIEDAD": invalid identifier`.
- **`TUTORES`**:
  - **Issue**: Protected by `FN_FILTRO_UNIDAD`. The function appends `id_unidad IN (...)`. However, `TUTORES` does not have an `id_unidad` column.
  - **Impact**: Queries by non-superadmins will instantly crash with `ORA-00904: "ID_UNIDAD": invalid identifier`.

### 3.3 Intact Entities
- `PROPIETARIOS_UNIDAD` (Has `id_unidad`, correctly protected by `FN_FILTRO_UNIDAD`)
- `RESIDENTES_UNIDAD` (Has `id_unidad`, correctly protected by `FN_FILTRO_UNIDAD`)
- `MASCOTAS` (Has `id_unidad`, correctly protected by `FN_FILTRO_UNIDAD`)
- `VEHICULOS` (Has `id_unidad`, correctly protected by `FN_FILTRO_UNIDAD`)

## 4. Conclusion
The Phase 1D module requires an explicit Oracle Database Patch (`V4.3__person_rls_patch.sql`) to attach the missing policy to `PERSONAS` and update the RLS predicate logic for `VISITANTES` and `TUTORES`. Until this patch is applied, the backend cannot securely or functionally interact with these tables.
