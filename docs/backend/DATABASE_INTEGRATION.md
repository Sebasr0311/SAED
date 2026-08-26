# SAED 2.0 Database Integration

## 1. Arquitectura de Conexión
SAED 2.0 Foundation abandona el manejo manual de `Connection` y adopta el estándar de la industria mediante **HikariCP** como Connection Pool, administrado por Spring Boot, en combinación con **Oracle JDBC 23c**.

## 2. Inyección del Contexto Multi-tenant (VPD/RLS)
El requisito primordial de SAED 2.0 es establecer el contexto de seguridad mediante `PKG_SAED_SESSION` antes de cualquier operación en base de datos.
Para evitar que un desarrollador "olvide" inyectar el contexto, se ha diseñado un Proxy Interceptor a nivel de DataSource:

### `SaedDataSourceProxy.java`
Sobrescribe `getConnection()`. Cuando Spring o `JdbcTemplate` piden una conexión, el proxy:
1. Extrae el `SaedContext` actual del `ThreadLocal` HTTP.
2. Prepara un `CallableStatement` y ejecuta silenciosamente `{call PKG_SAED_SESSION.SET_CONTEXT(?, ?, ?, ?)}`.
3. Devuelve la conexión envuelta en un proxy para capturar el método `close()`.

### `SaedConnectionProxy.java`
Intercepta `close()` para ejecutar silenciosamente `{call PKG_SAED_SESSION.CLEAR_CONTEXT()}` antes de que Hikari devuelva físicamente la conexión al pool. Esto previene contención y "fugas" de contexto entre peticiones.

## 3. Acceso a Datos (DAO)
Se utilizará `@Repository` estándar de Spring junto con `JdbcTemplate`.
Dado que `JdbcTemplate` interactúa con el `DataSource` proxy inyectado, *todo el código DML y Select hereda automáticamente el aislamiento per-tenant*.

Ejemplo:
```java
@Repository
public class UserRepository {
    private final JdbcTemplate jdbc;
    
    // Spring inyecta automáticamente el ProxyDataSource
    public UserRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    
    public List<User> findAll() {
        // En Oracle, esto ya viene filtrado mágicamente por RLS
        return jdbc.query("SELECT * FROM USUARIOS", new UserMapper());
    }
}
```

No se requieren sentencias `WHERE id_organizacion = ?` explícitas, Oracle se encarga del aislamiento físico.
