# SAED 2.0 — PHASE 1 CURRENT STATE AUDIT

## 1. Qué existe actualmente
- **Base de Datos (Baseline V3.9):** Esquema sólido y relacional que incluye tablas clave para el dominio de Identidad y Organización (`PERSONAS`, `USUARIOS`, `ROLES`, `PERMISOS`, `ROL_PERMISO`, `USUARIO_ASIGNACIONES`, `ORGANIZACIONES`, `PROPIEDADES`, `MEMBRESIAS`, `PLANES`).
- **Políticas RLS:** 88 políticas RLS ya desplegadas y funcionales en V3.9.
- **V4.0 Authentication Bootstrap:** Migración aprobada que permite la autenticación inicial (resolución de usuario) sin violar el modelo de aislamiento de RLS mediante el paquete `PKG_AUTH_BOOTSTRAP` en `SAED_SEC_MASTER`.
- **V4.1 Core Session Patch:** Migración aprobada que implementa una máquina de estados para la sesión en Oracle (`STATE 0: ANONYMOUS`, `STATE 1: BOOTSTRAP`, `STATE 2: BUSINESS`, `STATE 3: CLEARING/INVALID`).
- **Backend Foundation:** Arquitectura Spring Boot 3.2 sin Hibernate/JPA (solo JDBC), configurada con un `SaedDataSourceProxy` y `SaedConnectionProxy` que inyecta de forma segura el contexto en HikariCP, maneja JWT Stateless, e intercepta cierres de conexión para prevenir *Context Bleed*.

## 2. Qué está implementado
- **Capa de Persistencia y Seguridad (Infraestructura):** Inyección automática de `SET_CONTEXT` y `CLEAR_CONTEXT` a nivel de conexión de base de datos.
- **Seguridad en Pruebas:** Eviction de conexiones contaminadas en caso de fallo, prevención activa de fugas de contexto (*Context Bleed*) y tests que certifican la incapacidad de realizar DML cross-tenant.

## 3. Qué está parcialmente implementado
- **AuthService y ContextService:** Existen actualmente en el backend solo como esqueletos/stubs funcionales probados para validar la Foundation. Faltan las reglas de negocio reales (ej: manejo de intentos fallidos, bloqueos, bcrypt real en flujos completos, y validación exhaustiva de estados de cuenta).

## 4. Qué falta
- Endpoints REST completos (Controllers).
- Data Transfer Objects (DTOs) específicos para validación de entrada/salida.
- Flujos de recuperación y cambio de contraseña.
- Gestión de expiración de membresías y su interbloqueo con la autenticación.
- Administración y ABM (CRUD) de Usuarios, Asignaciones, Organizaciones y Propiedades.

## 5. Qué está incorrecto y debe corregirse
- El diseño heredado o la asunción inicial permitía al cliente frontend enviar su propio Rol (`X-SAED-Role-Code`) o Scope. Esto se ha rectificado a nivel arquitectónico y debe ser bloqueado implacablemente en el backend: **el cliente solo debe enviar un `X-Assignment-Id`**.

## 6. Qué debe conservarse
- Baseline V3.9 inmutable.
- Interfaz estricta con `PKG_AUTH_BOOTSTRAP`.
- Proxy JDBC personalizado sin Hibernate/JPA.
- Separación de responsabilidades: Oracle RLS siempre actúa como fuente final de autoridad (Zero Trust).

## 7. Qué debe refactorizarse
- Los DAOs actuales que fueron creados temporalmente para pruebas de integración deben evolucionar a repositorios limpios alineados al patrón Data Access Object (ej. `UserRepository`, `AssignmentRepository`) consumiendo consultas SQL puras con `JdbcTemplate`.

## 8. Qué debe eliminarse / archivarse
- Clases, DTOs y Mocks utilizados puramente para lograr el "verde" en los tests de Foundation, si no sirven para el modelo final (por ejemplo, el mock agresivo del JWT si no soporta Claims estándar de negocio).

## 9. Dependencias entre componentes
- `AuthService` depende estrechamente de `PKG_AUTH_BOOTSTRAP` (V4.0).
- `ContextService` depende de la tabla `USUARIO_ASIGNACIONES` y llama a `PKG_SAED_SESSION.SET_CONTEXT` (V4.1).
- Los Controllers de Negocio dependen de que `ContextService` haya resuelto exitosamente un `STATE 2` (Business) en Oracle antes de disparar cualquier lógica.

## 10. Riesgos
- **Riesgos de Seguridad:** Si un desarrollador futuro olvida pasar por el `SaedDataSourceProxy` y usa el `DataSource` original para consultas DML, saltará el aislamiento. Mitigado por el proxy por defecto. Riesgo de enumeración de usuarios en endpoints de Auth.
- **Riesgos de Arquitectura:** Tendencia a duplicar lógica RLS en Java. La aplicación Spring Boot no debe "re-filtrar" lo que Oracle RLS ya filtra por defecto (ej. al pedir `GET /propiedades`, Java no debe hacer `WHERE id_organizacion = X`, Oracle RLS ya lo inyecta). Esto debe cuidarse para evitar sobrecarga.
