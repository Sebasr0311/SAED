# MAPA SAED 1.0 -> SAED 2.0 (BACKEND)

## Matriz de Transición de Componentes

| Componente actual | Estado | Acción | Motivo |
|---|---|---|---|
| `src/main/java/com/edificio/admin/view/` (JavaFX UI) | obsoleto | ARCHIVAR | SAED 2.0 es puramente web (API-first). La interfaz de escritorio desaparece. |
| `src/main/java/com/edificio/admin/rest/` (HttpServer) | obsoleto | ELIMINAR / REEMPLAZAR | Será reemplazado por controladores `@RestController` de Spring Web. |
| `src/main/java/com/edificio/admin/dao/` | parcial | REFACTORIZAR | Se transformarán en componentes `@Repository` de Spring usando `JdbcTemplate`. Se inyectará el contexto Oracle (`PKG_SAED_SESSION`). |
| `src/main/java/com/edificio/admin/service/` | útil | REFACTORIZAR | Se transformarán en `@Service` de Spring. Se eliminará la instanciación directa (`new DAO()`). |
| `src/main/java/com/edificio/admin/model/` | útil | CONSERVAR / REFACTORIZAR | Servirán de base para los DTOs y modelos de dominio en SAED 2.0. |
| `src/main/java/com/edificio/admin/util/` | útil | CONSERVAR | Lógica matemática, validadores y utilitarios genéricos de fecha se migran tal cual. |
| `ConexionBD.java` (Gestión JDBC manual) | obsoleto | ELIMINAR | Spring Boot configurará un pool `HikariCP` de alto rendimiento. |
| `RestServerMain.java` & `Main.java` | obsoleto | ELIMINAR | Se reemplazan por la clase principal `@SpringBootApplication`. |
| Autenticación manual (`AuthHandler`) | obsoleto | REEMPLAZAR | Reemplazado por **Spring Security** y filtros JWT de autenticación/autorización. |
| Contexto Multi-tenant | n/a | NUEVO | Creación de `SaedContext`, filtros y `ContextHolder` para aislar organizaciones. |
| Global Exception Handling | n/a | NUEVO | Creación de `@ControllerAdvice` para mapear errores ORA- a respuestas HTTP limpias. |
| Tests Automáticos | faltante | NUEVO | Creación de suite con JUnit 5, Mockito y Spring Boot Test. |
