# SAED 2.0 Backend Architecture

## Monolito Modular
El sistema está diseñado como un Monolito Modular construido sobre Java 17 y Spring Boot 3. 

## Capas
1. **Presentación (Web)**: `@RestController`. Solo recibe HTTP, procesa JWT, maneja Validation (`@Valid`) y llama a Servicios.
2. **Aplicación/Servicios**: `@Service`. Maneja la lógica de negocio y delimita la transacción (`@Transactional`).
3. **Persistencia (Infraestructura)**: `@Repository`. Usa `JdbcTemplate`. Nunca usa JPA para evitar que los proxys lazy de Hibernate puenteen el control exacto sobre las conexiones y el Oracle RLS.

## Seguridad Físicamente Obligatoria
Cualquier hilo que intente hacer query a la DB debe tener un `SaedContext` (cargado desde JWT). 
El `SaedDataSourceProxy` garantiza que `PKG_SAED_SESSION.SET_CONTEXT` siempre se llame antes de devolver la conexión de la pool y la limpie (`CLEAR_CONTEXT`) al devolverla.

## Stack
- Java 17
- Spring Boot 3.2.x
- Spring Security (JWT sin estado)
- HikariCP (Connection Pooling)
- Oracle JDBC (`ojdbc11`)
- No Hibernate/JPA.
