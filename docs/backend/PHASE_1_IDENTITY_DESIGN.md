# SAED 2.0 - PHASE 1: IDENTITY, ACCESS, AND ORGANIZATIONS DESIGN

## 1. Objetivo
Diseñar la arquitectura funcional de la Fase 1 del backend de SAED 2.0, enfocada en la gestión de identidad (Personas, Usuarios), control de acceso (Roles, Permisos, Asignaciones, Contextos) y aislamiento lógico (Organizaciones, Membresías). Este diseño sirve como contrato estricto antes de iniciar la implementación en Spring Boot, asegurando compatibilidad 100% con la base de datos Oracle V3.9.

## 2. Alcance
- Autenticación (Login, JWT, Hashing).
- Gestión de Usuarios y Personas.
- Autorización Multitenant (Resolución de Contexto, Scopes).
- Gestión del Tenant (Organizaciones y Configuración).
- Gestión Comercial (Planes, Módulos, Membresías e Historial).
- (Fuera de alcance temporal: Residentes, Pagos, Reservas, Vehículos, etc.)

## 3. Tablas Involucradas (Baseline V3.9)
El módulo se fundamenta en las siguientes tablas reales de Oracle V3.9:
- **Identidad**: `PERSONAS`, `USUARIOS`. (Relación 1:1, `USUARIOS.id_persona` es UQ).
- **Control de Acceso**: `ROLES`, `PERMISOS`, `ROL_PERMISO`.
- **Asignaciones**: `USUARIO_ASIGNACIONES`, `USUARIO_PROPIEDADES_ASIGNADAS`, `ADMINISTRADORES_SAED`.
- **Tenant Lógico**: `ORGANIZACIONES`, `ORGANIZACION_CONFIGURACION`.
- **Comercial**: `PLANES`, `MODULOS`, `PLAN_MODULOS`, `MEMBRESIAS`, `MEMBRESIAS_HISTORIAL`.

## 4. Relaciones Clave
- **PERSONA -> USUARIO**: 1:1 estricto. La persona almacena datos demográficos, el usuario credenciales.
- **USUARIO -> ASIGNACIONES**: 1:N. Un usuario puede tener múltiples asignaciones simultáneas (ej. Residente en un lugar, Admin en otro).
- **ROL -> ALCANCE**: El trigger `TRG_ASIGNACION_VALIDA_SCOPE` obliga a que, según el alcance del rol (`GLOBAL`, `ORGANIZACION`, `PROPIEDAD`, etc.), la asignación exija los IDs correspondientes.
- **ORGANIZACION -> MEMBRESIA**: 1:N (históricamente), pero resguardada por el índice `UIX_MEMBRESIAS_VIGENTE` garantizando 1 sola membresía activa/prueba.

## 5. Arquitectura General y Zero-Trust
El principio fundamental es que **Spring Boot orquesta, pero Oracle autoriza**. 
El Frontend no envía comandos autoritativos, envía intenciones (Context Headers).
*Corrección de Diseño (Client-selected assignment, server-resolved authorization context)*: El cliente no declarará su rol, ni su alcance, ni su ID de organización. El cliente enviará un `assignmentId` y el servidor resolverá los datos reales desde la base de datos para inyectarlos a Oracle.

## 6. Flujo de Autenticación y Resolución de Contexto
1. **Login**: Cliente envía `{email, password}` a `/api/v1/auth/login`.
2. **Validación**: Backend usa `UserRepository` para verificar credenciales contra `USUARIOS.hash_password`.
3. **Generación JWT**: Se genera un JWT de IDENTIDAD que **SOLO contiene el `id_usuario`**. No contiene roles ni tenant.
4. **Listado de Contextos**: El cliente consulta `/api/v1/me/contexts`. El backend lee `USUARIO_ASIGNACIONES` y devuelve los contextos reales del usuario.
5. **Selección**: El usuario escoge una asignación.
6. **Siguientes Peticiones**: El frontend inyecta en HTTP Headers:
   - `Authorization: Bearer <token>`
   - `X-Assignment-Id: 348`

## 7. Flujo Multi-Tenant (Defensa en Profundidad)
1. `JwtAuthenticationFilter` extrae la identidad (`id_usuario`) del token.
2. Extrae el `X-Assignment-Id` del header.
3. **Resolución en Servidor**: El backend consulta la BD para verificar que la asignación 348 pertenece al `id_usuario` activo y está `ACTIVA`.
4. El backend construye el `SaedContext` (org, prop, rol, unidad) con los datos REALES de la BD.
5. El pool Hikari solicita conexión -> `SaedDataSourceProxy` lanza `PKG_SAED_SESSION.SET_CONTEXT`.
6. **Validación Oracle**: Oracle vuelve a validar la tupla. RLS se activa con seguridad impenetrable.

## 8. Roles
Roles quemados en la BD (Check constraint `CK_ROLES_CODIGO`):
- `SUPERADMIN`, `ADMIN_ORGANIZACION`, `PROPIETARIO`, `ADMIN_GENERAL`, `ADMIN_PROPIEDAD`, `PORTERO`, `VIGILANTE`, `RESIDENTE`, `PROPIETARIO_UNIDAD`.

## 9. Permisos (RBAC)
La BD define la estructura en `PERMISOS` y `ROL_PERMISO`.
*Decisión Arquitectónica*: Spring Security evaluará el Scope/Rol global mediante `@PreAuthorize("hasRole('ADMIN_PROPIEDAD')")`. La DB realizará la contención de filas (RLS). Para validación de Permisos Atómicos granulares (ej. `hasAuthority('ELIMINAR_USUARIO')`), el Backend consultará una caché en memoria o Redis alimentada por `ROL_PERMISO` para no recargar JDBC en cada petición, pero *Oracle RLS sigue siendo la defensa final*.

## 10. Scopes de Asignación
Controlados por `CK_ROLES_ALCANCE` en BD:
- `GLOBAL` (Superadmin, sin org asignada).
- `ORGANIZACION` (Admin de empresa administradora).
- `PROPIEDADES_SELECCIONADAS` (Admin General con subconjunto en `USUARIO_PROPIEDADES_ASIGNADAS`).
- `PROPIEDAD` (Admin / Portero de un solo inmueble).
- `UNIDAD` (Residente de apartamento).

## 11. Organizaciones (Tenant Lógico)
- Endpoint primario de aislamiento.
- `estado` de la organización (ACTIVA/SUSPENDIDA/INACTIVA) bloquea flujos operacionales.
- `ORGANIZACION_CONFIGURACION` debe exponerse de manera controlada para branding y parámetros (ej. día de corte).

## 12. Membresías y Comercial
La V3.9 es estricta con el trigger `TRG_MEMBHIST_INMUTABLE`.
- El Backend tiene la lógica de: Crear prueba (Trial), Activar, Vencer, Renovar.
- Ante un upgrade, el backend **debe** insertar un registro en `MEMBRESIAS_HISTORIAL`.
- Si se intenta modificar un historial viejo, Oracle lanzará `ORA-20030`, lo cual debe ser mapeado a 409 Conflict o 422.

## 13. API Contract (Draft Restful)
### Autenticación y Perfil
- `POST /api/v1/auth/login` -> `{ email, password }` | Retorna Token
- `POST /api/v1/auth/refresh` -> `{ token }`
- `GET /api/v1/auth/me` -> Retorna Perfil Persona/Usuario.
- `GET /api/v1/auth/contexts` -> Retorna `List<UserAssignmentDTO>`.

### Organizaciones (Requerido Rol SUPERADMIN)
- `POST /api/v1/organizations` -> Crea tenant, asigna Admin y crea membresía.
- `GET /api/v1/organizations` -> Lista tenants.

### Tenant Local (Basado en Contexto)
- `GET /api/v1/tenant/organization` -> Retorna datos org actual.
- `PUT /api/v1/tenant/organization` -> Edita datos org actual.
- `GET /api/v1/tenant/membership` -> Retorna membresía y plan actual.
- `GET /api/v1/tenant/config` -> Parámetros clave-valor.

### Asignaciones e Invitaciones
- `POST /api/v1/tenant/users/invite` -> Envía correo/crea asig.

## 14. DTOs
Separación estricta entre Capa JDBC y API:
- `LoginRequestDTO` (email, pwd)
- `AuthResponseDTO` (token, expiresIn)
- `ContextHeaderDTO` (Headers inyectados por UI)
- `PersonDTO` / `UserDTO` (Sin hash, sin logs internos)
- `AssignmentDTO` (id_rol, nombre_rol, id_org, id_propiedad)
- `OrganizationDTO`, `MembershipDTO`.

## 15. Repositories (DAO)
Sin Hibernate. Usaremos `JdbcTemplate`.
- `UserRepositoryImpl`: `findByEmail`, `save`, `updateStatus`.
- `RoleRepositoryImpl`: `findByCode`, `findPermissionsByRole`.
- `AssignmentRepositoryImpl`: `findActiveByUserId`.
- `OrganizationRepositoryImpl`: `createTenant` (Transaccional, inserta Org, Config, Membresía y Asignación del primer Admin).

*Regla de Oro*: Ningún repositorio "lee todo". Todos operan bajo la presunción de que el RLS ya está inyectado.

## 16. Seguridad Compartida
- **Spring Boot**: Valida la firma del JWT, el tiempo de expiración y anida los headers. Protege las rutas REST mediante roles (`@PreAuthorize`). Evita ataques de fuerza bruta.
- **Oracle BD**: Valida que la tupla `(userId, orgId, propId, rol)` exista y sea ACTIVA. Ejecuta las políticas de RLS `DBMS_RLS.ADD_POLICY`, ocultando filas que no pertenecen a la organización o propiedad solicitada.

## 17. Auditoría
- La Fase 1 implementará el `AuditService` de la Fase 0.
- Insertará en una tabla de backend (ej. `AUDITORIA_APP` o logs ELK) para los eventos: `LOGIN_SUCCESS`, `LOGIN_FAILED`, `PASSWORD_RESET`, `CONTEXT_BLOCKED`.
- Los INSERT/UPDATE de `ORGANIZACIONES` o `MEMBRESIAS` serán auditados **automáticamente por Oracle** mediante `AUDITORIA_LOG`. No duplicar lógica.

## 18. Manejo de Errores
- `401 Unauthorized`: Token ausente, vencido o firmas inválidas.
- `403 Forbidden`: Oracle retorna `ORA-20083` (Suplantación de rol) o `ORA-28115` (Intento de INSERT fuera de RLS).
- `404 Not Found`: Recurso no hallado (Oracle retorna 0 filas gracias al RLS).
- `422 Unprocessable Entity`: Violación de check constraint (ej. Rol desconocido, fecha inválida).
- `500 Internal Server Error`: Errores genéricos o caídas de BD, siempre ocultando el Stack Trace original.

## 19. Testing Strategy
- **Unit Tests**: Lógica de JWT y Services (Mockeando Repositories).
- **Integration Tests**: `@SpringBootTest` con BD local H2/Testcontainers u Oracle XE.
- **Security Adversarial Tests**: Iniciar sesión, alterar `X-SAED-Role-Code` a `SUPERADMIN` en Postman, verificar caída en 403. Inyectar `X-SAED-Organization-Id` de la competencia, verificar caída 403.
- Verificar que el `TRG_ASIGNACION_VALIDA_SCOPE` se dispara si mandamos un NULL incorrecto.

## 20. Riesgos Identificados
- **Multiplicidad de Contextos**: Un frontend mal diseñado puede enviar el contexto de la Organización A mientras llama a un endpoint queriendo crear datos en la Propiedad B. El backend **NO** sobreescribirá el payload, Oracle abortará la operación, lo que resultará en error. (Riesgo mitigado por la arquitectura).
- **Caché de Permisos**: Si se hace caché de RBAC en el backend, los permisos alterados en DB requerirán un evento de invalidación.

## 21. Dependencias / Pendientes
- **Matriz de Permisos V3.9**: La tabla `ROL_PERMISO` existe pero requiere Seed Data oficial. Si está vacía, Fallback a verificación genérica de Rol.
- Envío de Emails (SMTP) para recuperación de clave (Pendiente de implementación).

## 22. Criterios de Aceptación
1. Endpoints de Auth funcionales (Login/Contextos).
2. Repositories JDBC blindados e integrados.
3. El frontend puede alternar entre "Vigilante" y "Residente" enviando el Header correspondiente, cambiando su RLS sin tener que loguearse 2 veces.
4. Cobertura de pruebas superior al 80%.

---
**PHASE 1 DESIGN STATUS: READY FOR IMPLEMENTATION**
El diseño arquitectónico respeta meticulosamente el baseline V3.9. No se han inventado tablas y no se requiere ninguna alteración al modelo de la base de datos para construir esta fase.
