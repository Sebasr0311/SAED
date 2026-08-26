# SAED 2.0 — PHASE 1 SERVICE DESIGN

La arquitectura Backend Foundation prohíbe JPA/Hibernate, utilizando en su lugar un enfoque táctico con `JdbcTemplate` para dominar explícitamente el ciclo de conexión y el filtrado por RLS.

## Estructura por Capas
`Controller -> Service -> Repository (DAO) -> Database`

### 1. AuthService
**Responsabilidades:** Autenticación de primer nivel, control de hashes y ciclos de vida de credenciales (Account lock/unlock).
- **Dependencias:** `JwtProvider`, `UserRepository`, inyección de `PasswordEncoder` (BCrypt).
- **Flujo Especial:** Utiliza explícitamente `PKG_AUTH_BOOTSTRAP` mediante un row-mapper directo para saltar RLS sin contaminar el pool (STATE 1).
- **Auditoría:** Registra `LOGIN_SUCCESS`, `LOGIN_FAILED`, `ACCOUNT_LOCKED` mediante servicio asíncrono o inserciones secundarias si no las dispara la DB automáticamente.

### 2. ContextService
**Responsabilidades:** Orquestar el puente entre JWT Identidad y Oracle Authorization.
- **Dependencias:** `AssignmentRepository`.
- **Transaccionalidad:** Ninguna. Opera en modo Solo Lectura de Bootstrap (`STATE 1`) para extraer `GET_ASSIGNMENT_CONTEXT`.

### 3. AssignmentService
**Responsabilidades:** Alta, baja y modificación de asignaciones (Roles y dimensionalidad) para los Usuarios del Tenant.
- **Autorización:** Ejecuta la lógica Anti-Elevación de Privilegios (`Privilege Escalation Prevention`) comparando la jerarquía del rol solicitante contra el asignado.
- **Dependencias:** `RoleRepository`, `AssignmentRepository`, `OrganizationRepository`.
- **Base de Datos:** Al persistir (INSERT/UPDATE/DELETE), delega en Oracle la integridad referencial y RLS (STATE 2).

### 4. UserService / PersonaService
**Responsabilidades:** Abstracción unificada del CRUD de `USUARIOS` y `PERSONAS`.
- **Mapeo:** Recibe `UserCreateDTO` y lo descompone.
- **Transacciones:** `@Transactional` obligatorio. Inserta en `PERSONAS`, recupera `id_persona`, e inserta en `USUARIOS`. Si falla la segunda, retrocede la primera.
- **Seguridad:** Encripta la contraseña enviada en el DTO (usualmente una temporal auto-generada) con BCrypt antes de persistir. Pasa bandera `requiere_cambio_password = 'S'`.

### 5. OrganizationService
**Responsabilidades:** Lectura y edición de metadatos de la Organización y configuración K/V.
- **Flujo:** Las consultas leen `ORGANIZACIONES` y `ORGANIZACION_CONFIGURACION`. Oracle automáticamente recorta los resultados a la organización asignada en sesión.
- **Validación:** Impide intentar actualizar la PK o el NIT sin los debidos procedimientos especiales de backend, ya que la DB posee `UQ_ORGANIZACIONES_NIT`.

### 6. PropertyService
**Responsabilidades:** Administración de la tabla `PROPIEDADES` (conjuntos, edificios) dentro de una Organización.
- **Dependencias:** `PropertyRepository`.
- **Lógica Específica:** Al crear, se enlaza tanto en `PROPIEDADES` como en la tabla puente `ORGANIZACION_PROPIEDAD` para cerrar la dimensionalidad. Se inyecta la organización del Contexto Activo (`SaedContextHolder.getOrgId()`).

### 7. MembershipService
**Responsabilidades:** Visor del estado comercial.
- **Dependencias:** `MembershipRepository`.
- **Solo Lectura (Business):** A nivel tenant, solo expone el método `getCurrentMembership()` para advertirle al usuario en el Frontend si está en estado de `PRUEBA` (trial) o `SUSPENDIDA` (mora). No se permiten DMLs en la API de Tenant.
