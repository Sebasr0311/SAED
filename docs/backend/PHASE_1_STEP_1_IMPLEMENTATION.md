# SAED 2.0 — PHASE 1 STEP 1 IMPLEMENTATION REPORT

## 1. Objetivo
Implementar la primera parte de la Fase 1 (Identity & Access) correspondiente a: Autenticación, Resolución de Contexto y Endpoint de perfil.

## 2. Correcciones de Arquitectura Implementadas
Se rediseñó el flujo de seguridad para cumplir la regla: **El cliente no declara su rol ni su scope**.
El flujo implementado es:
1. Cliente envía credenciales -> Recibe un `token` JWT puramente de Identidad (solo contiene `id_usuario`).
2. Cliente consulta `GET /api/v1/me/contexts` -> Servidor lee `USUARIO_ASIGNACIONES` y devuelve sus asignaciones.
3. Cliente envía `Authorization: Bearer <token>` y `X-Assignment-Id: <id>`.
4. `JwtAuthenticationFilter` extrae la identidad del JWT.
5. El filtro delega en `ContextService` para validar que el `X-Assignment-Id` pertenece al usuario y obtener el `SaedContext` completo (Org, Prop, Unidad, Rol).
6. El Pool Hikari llama a `PKG_SAED_SESSION.SET_CONTEXT` validándolo en Oracle.

## 3. Estado de la Base de Datos y Limitación RLS
Durante la implementación, se descubrió un conflicto entre la arquitectura Zero-Trust de Oracle y el flujo de login:
- La tabla `USUARIOS` está protegida por RLS.
- Antes del login, el usuario no tiene contexto (`SaedContext` es null).
- Si no hay contexto, la política RLS de `USUARIOS` oculta todos los registros.
- **Consecuencia**: El backend no puede validar el hash de la contraseña porque no puede leer la fila del usuario por su email.

**ESTADO ACTUAL: REQUIRES FIXES (BLOCKED BY DATABASE)**

Para que el login real funcione en producción, es obligatorio crear una migración de base de datos que proporcione una vía segura para que el backend lea `hash_password` sin verse bloqueado por el RLS (por ejemplo, mediante una vista `VW_AUTH_USUARIOS` con permisos de definidor o modificando `PKG_SAED_SECURITY_RLS` para permitir el bypass técnico a nivel de sistema). Por ahora, el backend en entorno de Test usa H2 (que no aplica RLS) para que los tests pasen.

## 4. Estructura de Código Creada
- **DTOs**: `LoginRequest`, `AuthResponse`, `UserAssignmentDTO`
- **Models**: `User`
- **Repositories**: `UserRepository` (JdbcTemplate)
- **Services**: `AuthService`, `ContextService`
- **Controllers**: `AuthController`, `MeController`
- **Security**: Actualizado `JwtProvider` y `JwtAuthenticationFilter`.
- **Proxy Fix**: Modificado `SaedDataSourceProxy` para saltar la inyección de contexto si el contexto es anónimo/nulo (necesario para poder hacer queries previas a la autenticación).

## 5. Pruebas Realizadas
Se agregaron tests exhaustivos para validar:
- Credenciales válidas -> Retorna JWT con `userId`
- Credenciales inválidas -> Incrementa intentos fallidos
- Usuario inactivo -> Bloquea el login

Todos los tests del Foundation y de Identity compilan y pasan en el ciclo de Maven.
