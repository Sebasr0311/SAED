# SAED 2.0 — PHASE 1 IMPLEMENTATION PLAN

## Resumen Ejecutivo
El plan de implementación se divide en bloques (1A a 1I) estrictamente secuenciales. No se avanzará al siguiente bloque sin que el anterior haya pasado `mvn clean test` y validación Zero Trust. Ningún bloque requiere modificar la Base de Datos V3.9. 

## Fase 1A: Authentication
- **Objetivo:** Materializar el endpoint público `/api/v1/auth/login`.
- **Artefactos:**
  - `LoginRequestDTO` y `LoginResponseDTO`.
  - Configuración Spring Security: `SecurityFilterChain` permitiendo `/auth/**`.
  - Refactorizar `AuthService.java` de mock a implementación BCrypt real y manejo de intentos.
  - Generación del JWT Token (`JwtProvider` existente).
- **Criterio Aceptación:** `POST /login` retorna Token válido con 200 OK, o rechaza credenciales con 401 sin exponer la razón específica.

## Fase 1B: Me / Assignments / Context
- **Objetivo:** Resolver el Bootstrap (STATE 1) para listar y elegir contextos de negocio.
- **Artefactos:**
  - `GET /api/v1/me` y `GET /api/v1/me/contexts` en `AuthController`.
  - `AssignmentRepository.java` que mapea la vista/tabla `USUARIO_ASIGNACIONES`.
  - `ContextSelectionInterceptor`: Valida el header HTTP `X-Assignment-Id`.
- **Criterio Aceptación:** El cliente debe ver sus contextos. Enviar un contexto falso revienta con Excepción de Seguridad en Java.

## Fase 1C: Users & Persons
- **Objetivo:** CRUD unificado para Usuarios y Personas.
- **Artefactos:**
  - `UserDTO`, `PersonaDTO`, `UserCreateDTO`.
  - `UserController`.
  - `UserRepository.java`, `PersonaRepository.java` (JdbcTemplate).
  - `@Transactional` service block.
- **Criterio Aceptación:** Creación limpia de 1 a 1 de identidad y credencial (estado PENDIENTE_VERIFICACION), con BCrypt aplicado a la clave temporal.

## Fase 1D: Organizations
- **Objetivo:** Gestión del Tenant principal.
- **Artefactos:**
  - `OrganizationController`, `OrganizationService`, `OrganizationRepository`.
  - Restricción de permisos vía validación RBAC (`@PreAuthorize` o Check nativo contra constantes de permisos).
- **Criterio Aceptación:** `GET /organizations` retorna **solamente** la organización del cliente autenticado, garantizado por Oracle RLS.

## Fase 1E: Properties
- **Objetivo:** CRUD físico del Real Estate.
- **Artefactos:**
  - `PropertyController`, `PropertyService`, `PropertyRepository`.
  - Vínculo 1:N entre Organizaciones y Propiedades.
- **Criterio Aceptación:** Inserción exitosa de una Propiedad; RLS previene que el ADMIN_PROPIEDAD X vea las propiedades del ADMIN_PROPIEDAD Y.

## Fase 1F: Assignments Administration
- **Objetivo:** Administración de Roles y acceso distribuido.
- **Artefactos:**
  - Endpoint `POST /api/v1/assignments`.
  - Validador `PrivilegeEscalationGuard` (Jerarquía Backend).
- **Criterio Aceptación:** Imposibilidad técnica de que un rol asigne permisos superiores a su propio Scope u asigne a organizaciones ajenas.

## Fase 1G: Membresías & Suspensión
- **Objetivo:** Bloqueos en caso de mora.
- **Artefactos:**
  - `MembershipService.getCurrentMembership()`.
  - Interceptor global que rechace (HTTP 403 / 402 Payment Required) peticiones DML si el tenant está `SUSPENDIDO`.
- **Criterio Aceptación:** Un Tenant con membresía suspendida puede leer, pero recibe 402/403 al intentar ejecutar un POST.

## Fase 1H & 1I: Security Hardening & Integration Tests
- **Objetivo:** Auditar, refinar y blindar la capa de endpoints.
- **Artefactos:**
  - Anotaciones `@Validated`.
  - Centralización de Exceptions (Exception Handler global).
  - Tests MockMvc para cada controlador creado.
- **Criterio Aceptación:** `mvn clean test` 100% PASS, 0 advertencias estáticas sobre SQL Injection.
