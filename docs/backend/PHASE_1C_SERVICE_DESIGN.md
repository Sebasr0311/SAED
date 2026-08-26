# PHASE 1C SERVICE DESIGN

## Capa de Controladores (Controllers)
- Validarán Request DTOs (Bean Validation @NotNull, @Size).
- Extraerán parámetros del SaedContext y los inyectarán al servicio, impidiendo la manipulación por parte del usuario.

## Capa de Servicios (Services)
- OrganizationService: Validará lógicamente los permisos y gestionará CRUD.
- PropertyService: Sobreescribirá o validará que idOrganizacion == SaedContext.getOrganizationId().
- AssignmentService: 
  - Validará jerarquía (quién asigna a quién).
  - Verificará que el Rol exista y cargará su alcance.
  - Dependiendo del alcance, exigirá que ciertos IDs no sean nulos.
  - Insertará transaccionalmente. Manejará DataIntegrityViolationException para UIX_ASIGNACION_UNICA y devolverá 409 Conflict.

## Capa de Repositorios (Repositories)
- Uso de NamedParameterJdbcTemplate.
- Todas las inserciones o consultas sufrirán inherentemente el filtrado de Oracle RLS en base al State 2 activado previamente en la cadena de filtros de Phase 1B.
