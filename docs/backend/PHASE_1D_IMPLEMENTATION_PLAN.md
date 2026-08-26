# PHASE 1D IMPLEMENTATION PLAN

## 1. Database Patch (Prerequisite)
Author `V4.3__person_rls_patch.sql`:
- Execute `DBMS_RLS.ADD_GROUPED_POLICY` for `PERSONAS` pointing to `PKG_SAED_SECURITY_RLS.FN_FILTRO_ORGANIZACION`.
- Update `PKG_SAED_SECURITY_RLS` body to fix the `ORA-00904` issues on `VISITANTES` and `TUTORES` by providing the correct SQL joins in the RLS predicates.
- Update `FN_FILTRO_ORGANIZACION` to allow `INSERT` operations on `PERSONAS` for authenticated scopes by appending `OR 1=1` for `INSERT` context, or evaluating ownership dynamically (since a new person isn't tied to an org until the association record is created). *Wait, we need a safe strategy for Person creation.*

### Person Creation RLS Strategy
Since `PERSONAS` are global, but isolated by RLS, how does a user insert a new `PERSONA` when the dynamic filter checks if the person is already linked to their org? If the person is new, they have no links, so RLS `update_check=TRUE` would block the insert!
**Solution for V4.3**: Change the `PERSONAS` RLS policy to `update_check=FALSE` for `INSERT`, or adjust `FN_FILTRO_ORGANIZACION` to recognize the `SYS_CONTEXT('USERENV', 'STATEMENT_ID')` or handle it gracefully. The safest approach is simply configuring the `PERSONAS` policy with `check_option => FALSE` (which is the default) so `INSERT` succeeds, and then immediately the app inserts the `PROPIETARIOS_UNIDAD` association. 

## 2. Spring Boot Implementation
- **DTOs**: `PersonaDTO`, `PersonaRequestDTO`, `UnitOwnerDTO`, `UnitOwnerRequestDTO`, `UnitResidentDTO`, `UnitResidentRequestDTO`.
- **Controllers**:
  - `PersonaController`
  - `UnitInhabitantController`
- **Services**:
  - `PersonaService`
  - `InhabitantService`
- **Repositories**:
  - `PersonaRepositoryImpl`
  - `UnitOwnerRepositoryImpl`
  - `UnitResidentRepositoryImpl`

## 3. Testing
- `Phase1DAdversarialTest`:
  - Attempt to read a `PERSONA` from another organization (must fail).
  - Attempt to assign a `PROPIETARIO_UNIDAD` to a unit in another organization (must fail with 403).
  - Validate that `PERSONAS` creation works and successfully assigns to a unit in the same transaction.

## 4. Execution Order
1. Apply V4.3 DB patch locally.
2. Develop Java layers (DTO -> Repo -> Service -> Controller).
3. Write and run Adversarial Tests.
4. Report pre-merge status.
