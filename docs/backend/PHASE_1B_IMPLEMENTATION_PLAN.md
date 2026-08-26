# PHASE 1B - IMPLEMENTATION PLAN

## 1. Objetivo General
Implementar el código funcional en Spring Boot para consultar asignaciones, validar el contexto seleccionado por el cliente (`X-Assignment-Id`), y orquestar la transición de estados (STATE 1 a STATE 2) de manera segura y transparente para la capa de negocio.

## 2. Iteraciones del Plan

### SPRINT 1: Modelado Base
- `Assignment`: Entidad DTO y modelos de negocio.
- `AssignmentDao`: Implementación de JdbcTemplate para consultar `USUARIO_ASIGNACIONES`, `ROLES`, `ORGANIZACIONES`, `PROPIEDADES` y `UNIDADES` mediante JOINs eficientes.

### SPRINT 2: Servicios
- `AssignmentService`: Lógica de validación, resolución jerárquica y mapeo.

### SPRINT 3: Endpoint de Contexto
- `AuthContextController`: Endpoint `GET /api/v1/auth/assignments` protegido bajo STATE 1.

### SPRINT 4: Interceptor y State Machine
- `SaedContextInterceptor`: Leer Header, invocar `AssignmentService`, transicionar a STATE 2.
- Registrar el interceptor en `WebMvcConfigurer`.

### SPRINT 5: Manejo de Excepciones
- `GlobalExceptionHandler`: Manejo estricto de `InvalidContextException` y `AccessDeniedException` en el contexto del header `X-Assignment-Id`.

## 3. Criterios de Aceptación
1. El usuario debe poder consultar sus organizaciones/propiedades.
2. Si envía un `X-Assignment-Id` de una asignación inactiva, recibe HTTP 403.
3. Si envía un `X-Assignment-Id` de otro usuario, recibe HTTP 403.
4. Si NO envía `X-Assignment-Id` a un endpoint de negocio, recibe HTTP 403 o HTTP 400.
5. El interceptor debe transicionar a STATE 2 inyectando los datos correctos en Oracle.

## 4. VEREDICTO DE DISEÑO
**PHASE 1B DESIGN STATUS: READY FOR IMPLEMENTATION**
El estado actual de la base de datos (V3.9, V4.0, V4.1) ha sido auditado rigurosamente, asegurando que las tablas necesarias existen y que la lógica RLS está preparada para procesar las transiciones descritas.
