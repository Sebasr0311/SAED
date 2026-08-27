# PHASE 1D SERVICE DESIGN

## 1. DTO Layer
- `PersonaDTO`, `PersonaRequestDTO`
- `UnitOwnerDTO`, `UnitOwnerRequestDTO`
- `UnitResidentDTO`, `UnitResidentRequestDTO`

## 2. Repository Layer (JdbcTemplate)
- **`PersonaRepository`**:
  - `Optional<Persona> findById(Long id)`
  - `Long save(Persona persona)`
  - `List<Persona> findAll(int offset, int limit)`
- **`UnitOwnerRepository`**:
  - `List<UnitOwner> findByUnitId(Long unitId)`
  - `Long save(UnitOwner owner)`
- **`UnitResidentRepository`**:
  - `List<UnitResident> findByUnitId(Long unitId)`
  - `Long save(UnitResident resident)`

## 3. Service Layer
- **`PersonaService`**:
  - Validates document uniqueness.
  - Handles creation of person profiles.
- **`InhabitantService`**:
  - Encapsulates logic for Owners and Residents.
  - Ensures a unit exists and the caller has permissions (delegated to RLS on insert).
  - Validates `porcentajePropiedad` logic (e.g., total ownership cannot exceed 100%).
  - Manages `esPrincipal` logic (only one primary owner).

## 4. Security Delegations
Services **must not** check if a `unitId` belongs to the `X-Assignment-Id` org manually. They should execute `INSERT INTO PROPIETARIOS_UNIDAD`, and if the `unitId` is outside the RLS scope, Oracle will raise `ORA-28115` (Check Option violation), which Spring will catch as `DataAccessException` and map to `403 Forbidden` (just like in Phase 1C).
