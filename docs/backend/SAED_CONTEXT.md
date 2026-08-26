# SAED 2.0 Context Management

## Concept
En SAED 2.0, el modelo Multi-tenant y de seguridad Zero Trust está respaldado al 100% por Oracle VPD (Row-Level Security).
Para que las consultas a la base de datos funcionen y no retornen 0 filas o rechacen inserciones (ORA-28115), la conexión a la base de datos **DEBE** ser inicializada con el contexto de seguridad correcto utilizando `PKG_SAED_SESSION.SET_CONTEXT`.

## Objeto `SaedContext` (Backend)
El backend mantiene el contexto lógico de la sesión del usuario a través de la clase `SaedContext`, la cual típicamente se extrae de un JWT y se almacena en el `ThreadLocal` o `SecurityContextHolder` de Spring Security durante el ciclo de vida del request HTTP.

### Propiedades:
- `userId` (ID del usuario)
- `organizationId` (Tenant)
- `propertyId` (Edificio/Conjunto actual)
- `roleCode` (Código del rol, e.g., 'RESIDENTE', 'ADMIN_PROPIEDAD')

## Integración con Oracle
Cuando la capa de persistencia (`DAO` o `Repository`) necesita interactuar con la DB:
1. Pide un objeto `Connection` al DataSource (`HikariCP`).
2. **ANTES** de ejecutar cualquier query de negocio, invoca a `PKG_SAED_SESSION.SET_CONTEXT(userId, orgId, propId, roleCode)`.
3. Ejecuta las operaciones (SELECT, INSERT, UPDATE, DELETE).
4. Termina la transacción (Commit/Rollback).
5. **OBLIGATORIO**: Limpia el contexto llamando a `PKG_SAED_SESSION.CLEAR_CONTEXT()`.
6. Devuelve la conexión al pool.

## Control Explicito (Spring JDBC)
Se prohíbe el uso de abstracciones mágicas como JPA/Hibernate si no garantizan la inyección del contexto en la misma conexión física antes del envío del primer SQL. 
El Foundation usará un **Interceptor o Aspecto (`AOP`)** o envolverá `JdbcTemplate` para garantizar que toda transacción se enmarque en este ciclo.
