# PHASE 1E IMPLEMENTATION PLAN

## 1. SCOPE
Implement CRUD operations for Dependents and External Associates:
1. Mascota (Pets)
2. Vehiculo (Vehicles)
3. Tutor (Tutors)
4. Visitante (Visitors)

## 2. ARTIFACTS
**DTOs:**
- MascotaDTO, MascotaRequestDTO
- VehiculoDTO, VehiculoRequestDTO
- TutorDTO, TutorRequestDTO
- VisitanteDTO, VisitanteRequestDTO

**Repositories:**
- MascotaRepository (JdbcTemplate)
- VehiculoRepository (JdbcTemplate)
- TutorRepository (JdbcTemplate)
- VisitanteRepository (JdbcTemplate)

**Services:**
- MascotaService
- VehiculoService
- TutorService
- VisitanteService

**Controllers:**
- MascotaController
- VehiculoController
- TutorController
- VisitanteController

## 3. RULES & CONSTRAINTS
- **Vehicles:** ID_UNIDAD is technically nullable in the DB, but to ensure RLS visibility under FN_FILTRO_UNIDAD, the API will enforce unidadId as @NotNull.
- **Tutors:** The menorId must be a person who is a resident of a unit in the current context.
- **Visitors:** Will be created, but may not be immediately visible in GET /visitors queries if VISITAS are not implemented yet. This is acceptable as Phase 1 focuses on core entity registry.
- **Transactions:** Handled by @Transactional.
- **Authorization:** hasAuthority('SCOPE_ADMIN_PROPIEDAD') or similar depending on the operation, following Phase 1D's pattern.

