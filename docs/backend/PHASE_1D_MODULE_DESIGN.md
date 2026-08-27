# PHASE 1D MODULE DESIGN

## 1. Overview
The **Person & Inhabitant Management** module bridges the administrative tenant hierarchy (Organizations, Properties, Units) with the real-world actors (People, Pets, Vehicles). It establishes the core registry required for access control, billing, and communications.

## 2. Entity Relational Design
Relying exactly on the V3.9 baseline:

### 2.1 Personas (`PERSONAS`)
- **Primary Key**: `id_persona`
- **Role**: Master record for human entities (Natural or Legal). Holds identity documents, names, contact info.
- **Dependency**: Must exist before a User (`USUARIOS`), Owner, or Resident can be created.

### 2.2 Inhabitant Associations
- **Owners (`PROPIETARIOS_UNIDAD`)**:
  - Links `PERSONAS` to `UNIDADES`.
  - Tracks ownership percentage and primary owner status.
- **Residents (`RESIDENTES_UNIDAD`)**:
  - Links `PERSONAS` to `UNIDADES`.
  - Categorizes residents (e.g., Arrendatario, Familiar).

### 2.3 Dependents
- **Pets (`MASCOTAS`)**: Linked to `UNIDADES` and the `PERSONAS` responsible.
- **Vehicles (`VEHICULOS`)**: Linked to `PERSONAS` and optionally `UNIDADES`.

## 3. System Behavior & Constraints
- **Person Lifecycle**: A `PERSONA` record is global in structure but RLS isolates visibility. If a person belongs to Unit A (Org 1) and Unit B (Org 2), both organizations can view the `PERSONA` due to the dynamic RLS predicate in V3.9:
  ```sql
  id_persona IN (SELECT id_persona FROM PROPIETARIOS_UNIDAD JOIN ...) 
  ```
- **Creation Flow**: 
  1. Create `PERSONA`.
  2. Create association (`PROPIETARIOS_UNIDAD` or `RESIDENTES_UNIDAD`).
  - *Constraint*: Because `PERSONAS` visibility requires the association to exist (Catch-22 for RLS), `SUPERADMIN` or a bypassing stored procedure might be required, OR the `PERSONAS` RLS logic needs to allow insertion if the creator is in context. Wait, V3.9 doesn't enforce RLS on `PERSONAS` yet. The upcoming V4.3 patch will need to allow `INSERT` for authenticated admins.

## 4. Phase 1D Scope Limitation
To keep the PR focused and reviewable, Phase 1D will implement:
1. Oracle V4.3 RLS Patch (Fixing the 3 baseline bugs).
2. `PERSONAS` CRUD.
3. `PROPIETARIOS_UNIDAD` & `RESIDENTES_UNIDAD` management.
(Pets, Vehicles, Visitors, and Tutors will be deferred to Phase 1E).
