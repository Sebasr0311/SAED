# SAED 2.0 - FOUNDATION FINAL AUDIT

## A. VERDICT
**APPROVED**

La arquitectura Foundation de SAED 2.0 (Spring Boot 3, Java 17, HikariCP, Oracle JDBC sin JPA) ha sido sometida a pruebas adversariales, pruebas de inyección de contexto multitenant concurrente, y validación exhaustiva de ciclo de vida de conexiones de base de datos. Se cumple estrictamente el contrato con la BD Oracle SAED V3.9 y es segura para implementar los módulos de negocio.

## B. Arquitectura Revisada
1. **Connection Pool & Oracle Context**: Se implementó `SaedDataSourceProxy` y `SaedConnectionProxy`. Este mecanismo envuelve HikariCP y asegura de forma implacable que:
   - Al adquirir la conexión, se llame a `PKG_SAED_SESSION.SET_CONTEXT`.
   - Al cerrarla (devolverla al pool), se llame a `PKG_SAED_SESSION.CLEAR_CONTEXT()`.
2. **Multi-Tenancy**: Aislada 100% a nivel RLS, alimentada por los proxies transaccionales de Spring.
3. **JWT / Spring Security**: Se configuró `JwtAuthenticationFilter` stateless, que popula el `SaedContext` para inyectarlo en Oracle y a su vez alimenta el `SecurityContextHolder` para los `@PreAuthorize`.
4. **Manejo de Errores**: `GlobalExceptionHandler` intercepta y anonimiza las respuestas, mapeando `ORA-28115` y `ORA-2008X` como un 403 Forbidden para ocultar metadatos a posibles atacantes.

## C. Hallazgos Críticos
**1. Connection Leak / Denial of Service (DoS) en DataSource Proxy (CORREGIDO):**
- **Problema:** En la primera versión de `SaedDataSourceProxy`, si la inyección de contexto (`PKG_SAED_SESSION.SET_CONTEXT`) fallaba (por ejemplo, porque un atacante enviaba un contexto forjado causando `ORA-20082` o `ORA-20083`), la excepción se propagaba y abortaba la operación, pero `connection.close()` jamás se llamaba. 
- **Impacto:** Un atacante podía agotar el connection pool de Hikari enviando 10 peticiones con tokens inválidos. Una vez agotado, toda la aplicación se detenía.
- **Solución implementada:** Se envolvió `applySaedContext` en un bloque `try-catch`, asegurando que `connection.close()` devuelva la conexión física al pool si el contexto falla, antes de hacer throw de la excepción.

## D. Hallazgos Altos
*Ninguno.*

## E. Hallazgos Medios
*Resolución de Contexto JWT.* Al no existir todavía los endpoints de autenticación funcionales con negocio, las pruebas actuales utilizan mocks duros del contexto, pero la inyección es mecánicamente segura.

## F. Hallazgos Bajos
*Limpieza de threads JUnit.* En las pruebas iniciales de concurrencia, el JVM no se apagaba debido a `ExecutorService` sin hacer `shutdown()`. Se corrigió en el test para evitar cuellos de botella en integraciones CI/CD.

## G. Pruebas Ejecutadas
- `SaedContextIntegrationTest`
- `AdversarialFoundationTest`
(Pruebas de intento de lectura sin contexto, intentos de alteración de rol (Role Spoofing), intentos de concurrencia/bleed).

## H. Evidencia de aislamiento multi-tenant
Ejecutada con `givenNoContext_whenSelect_thenZeroTrustShouldBlockOrReturnZero`, forzando al pool a entregar conexión sin ID, siendo bloqueado de raíz por el paquete PL/SQL.

## I. Evidencia de limpieza del contexto
En `testConcurrencyAndContextBleed`, se forzaron 20 hilos simultáneos usando un pool de 10. Ningún hilo sin contexto pudo ver registros dejados como "basura de contexto" por transacciones previas.

## J. Evidencia de concurrencia
Las conexiones son recicladas limpiamente por HikariCP y el Interceptor de Spring cierra las brechas de variables mutables.

## K. Evidencia de RLS
Las 88 políticas RLS de la V3.9 actúan de guardián maestro. Si el proxy llegara a fallar, la tabla responde con 0 filas para SELECT, y bloquea INSERT/UPDATE con `ORA-28115`.

## L. Estado del AuditService
**Contrato Diseñado**:
Se recomienda no saturar la BD transaccional de Oracle para logs rutinarios.
El `AuditService` deberá implementarse con:
- Eventos de backend (Auth, Password changes) como bitácora en una tabla `AUDITORIA_APP`.
- Oracle ya se encarga del DML en las tablas de negocio (con `AUDITORIA_LOG`). El backend NO REINVENTARÁ los triggers, sino que delegará la responsabilidad transaccional a la base de datos.

## M. Riesgos pendientes
- Filtración de Excepciones: Continuar verificando que futuros desarrolladores no atrapen `Exception e` indiscriminadamente, ocultando los rechazos por `DataAccessException`.

## N. Recomendación final sobre merge a main
**SI**. La capa de Fundación (Phase 0) se puede fusionar a `main`. Provee las defensas obligatorias para evitar que las Fases 1 a 17 (Residentes, Pagos, Propiedades, etc.) filtren datos entre organizaciones por accidente.
