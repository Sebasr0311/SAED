# PHASE 1B - SERVICE DESIGN

Este documento detalla los componentes arquitectónicos de Spring Boot que habilitarán la Fase 1B.

## 1. Módulos Core

### 1.1 `AssignmentService`
- **Responsabilidad:** Consultar las asignaciones disponibles para un usuario, y validar una asignación específica.
- **Transaccionalidad:** `ReadOnly`.

### 1.2 `AssignmentRepository` (DAO)
- **Responsabilidad:** Interfaz con Oracle usando `JdbcTemplate`.
- **Query 1:** `findAssignmentsByUsuarioId(Long idUsuario)`
- **Query 2:** `findByIdAndUsuarioId(Long idAsignacion, Long idUsuario)`
- **Conexión:** Al consultar asignaciones durante la etapa de enrutamiento (STATE 1), utiliza la conexión inyectada vía V4.0 (con `SET_BOOTSTRAP_CONTEXT` ya llamado).

### 1.3 `SaedContextInterceptor` (Implementa `HandlerInterceptor`)
- **Responsabilidad:** Bloqueador (Gatekeeper). Se ejecuta antes del Controller en cada request.
- **Flujo:**
  1. Extrae el header `X-Assignment-Id`.
  2. Si NO existe: Verifica si el endpoint requiere contexto. Si sí, lanza `400 Bad Request` o `403 Forbidden`.
  3. Si SÍ existe: 
     - Llama a `AssignmentService.validateAssignment(idAsignacion, idUsuario)`.
     - Si es inválido: Llama a `SecurityContextHolder.clearContext()` y lanza `AccessDeniedException`.
     - Si es válido: Retorna un objeto `SaedSecurityContext` con el Tenant.
  4. Llama al `OracleContextService` para transicionar a STATE 2.

### 1.4 `OracleContextService`
- **Responsabilidad:** Wrappea las llamadas PL/SQL de contexto usando `JdbcTemplate`.
- **Método `activateTenantContext(SaedSecurityContext ctx)`:**
  - Invoca `{call PKG_SAED_SESSION.SET_CONTEXT(?, ?, ?, ?)}` pasando los IDs resueltos.

## 2. Inyección de Dependencias de Contexto

Para evitar que los servicios consulten el `X-Assignment-Id` de los headers repetidamente, el interceptor inyectará el `SaedSecurityContext` como un bean de Scope `Request`, o utilizará `ThreadLocal` (ej. vía Spring Security custom authentication details).

## 3. Anti-Privilege Escalation

El `AssignmentService.validateAssignment()` DEBE consultar en BBDD para asegurarse de que la asignación no ha sido revocada desde que se emitió el JWT. Esta consulta se beneficia de RLS y del Bootstrap Context (V4.0), por lo que un usuario A **nunca** podrá ver ni validar la asignación del usuario B, incluso si intenta un ataque de iteración de IDs.
