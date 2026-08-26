# PHASE 1C API CONTRACT

## Endpoints de Organizaciones
- GET /api/v1/organizations
- POST /api/v1/organizations
- GET /api/v1/organizations/{id}
- PUT /api/v1/organizations/{id}
- PATCH /api/v1/organizations/{id}/status

## Endpoints de Propiedades
- GET /api/v1/properties
- POST /api/v1/properties
- GET /api/v1/properties/{id}
- PUT /api/v1/properties/{id}

## Endpoints de Unidades
- GET /api/v1/units
- POST /api/v1/units
- GET /api/v1/units/{id}
- PUT /api/v1/units/{id}

## Endpoints de Asignaciones (Usuarios)
- POST /api/v1/assignments
- GET /api/v1/assignments/search (Búsqueda por usuario, org, o prop)
- PATCH /api/v1/assignments/{id}/status

Todos protegidos por el filtro existente JwtAuthenticationFilter y el header X-Assignment-Id.
