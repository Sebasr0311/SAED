# SAED 2.0 — PHASE 1 API CONTRACT

*Notas de Diseño: Todas las llamadas de negocio requieren JWT Bearer y el Header `X-Assignment-Id`, excepto los módulos públicos de Autenticación y `GET /api/v1/me/contexts` (que solo usa JWT).*

## Módulo: Auth & Identidad

### `POST /api/v1/auth/login`
- **Auth/Scope:** Público.
- **Request:** `{ "email": "...", "password": "..." }`
- **Response:** `{ "token": "jwt...", "requires_password_change": false, "id_usuario": 100 }`
- **Lógica:** Llama a PKG_AUTH_BOOTSTRAP. Compara BCrypt. Actualiza `ultimo_login`.

### `POST /api/v1/auth/refresh`
- **Auth/Scope:** JWT válido y no expirado.
- **Request:** Vacío.
- **Response:** Nuevo JWT token.

### `GET /api/v1/me`
- **Auth/Scope:** JWT (STATE 1).
- **Response:** DTO consolidado con `Usuario` y `Persona`.

### `GET /api/v1/me/contexts`
- **Auth/Scope:** JWT (STATE 1).
- **Response:** `[ { "id_asignacion": 105, "rol": "ADMIN_PROPIEDAD", "organizacion": { "id": 1, "nombre": "Org A" }, "propiedad": { ... } } ]`

## Módulo: Organización (Tenant)

### `GET /api/v1/organizations`
- **Auth/Scope:** Requiere Assignment (STATE 2). Permiso: `ORG_READ`.
- **Response:** Paginated list of Organizations. (Nota: Por RLS, un ADMIN_GENERAL solo verá la fila correspondiente a su Tenant. El SUPERADMIN verá todas).

### `GET /api/v1/organizations/{id}`
- **Auth/Scope:** Requiere Assignment. Permiso: `ORG_READ`.
- **Response:** Organización completa + Configuración K/V actual + Membresía Vigente.

### `PATCH /api/v1/organizations/{id}`
- **Auth/Scope:** Requiere Assignment. Permiso: `ORG_UPDATE`. Scope: `ORGANIZACION` o `GLOBAL`.
- **Request:** Solo los campos editables (`email_contacto`, `telefono`, configuración básica). `estado` restringido a SUPERADMIN.
- **RLS:** Bloqueará automáticamente si el ID no corresponde al Tenant del Assignment (Retornando vacío o lanzando Exception controlada por Oracle).

## Módulo: Administración de Usuarios (RBAC Tenant)

### `POST /api/v1/users` (Invitación o Creación)
- **Auth/Scope:** Assignment. Permiso: `USER_CREATE`. 
- **Lógica:** Crea `PERSONAS` y `USUARIOS` (con pwd temporal y `requiere_cambio_password = 'S'`).
- **Transacción:** `@Transactional` de Spring, insertando cascada 1:1.

### `POST /api/v1/assignments` (Distribución de Roles)
- **Auth/Scope:** Assignment. Permiso: `ASSIGNMENT_CREATE`.
- **Request:** `{ "id_usuario": 10, "id_rol": 5, "id_propiedad": null }`
- **Validación:** Backend validará que no intente otorgarse permisos a sí mismo y que el scope destino esté contenido en el scope del solicitante (Un ADMIN_PROPIEDAD no puede crear un ADMIN_GENERAL).

### `DELETE /api/v1/assignments/{id}`
- **Auth/Scope:** Assignment. Permiso: `ASSIGNMENT_DELETE`.
- **Lógica:** Soft-delete (Cambia estado a `REVOCADA`).
- **RLS:** Prevendrá revocar asignaciones fuera del Tenant o Propiedad del solicitante.

## Módulo: Propiedades

### `POST /api/v1/properties`
- **Auth/Scope:** Assignment. Permiso: `PROP_CREATE`. Scope: `ORGANIZACION`.
- **Request:** DTO de Propiedad (Nombre, Tipo, Dirección).
- **Lógica:** Crea fila en `PROPIEDADES` e inyecta la relación en `ORGANIZACION_PROPIEDAD` usando el `id_organizacion` del Assignment.

### `GET /api/v1/properties`
- **Auth/Scope:** Assignment. Permiso: `PROP_READ`. 
- **Response:** Listado de Propiedades.
- **RLS:** Un ADMIN_PROPIEDAD solo verá las suyas. Un ADMIN_ORGANIZACION verá todas las del Tenant. Todo manejado transparentemente por Oracle.
