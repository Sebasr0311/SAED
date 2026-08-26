# SAED 2.0 Transactions

## Gestión Declarativa
Se utilizará `@Transactional` de Spring.
Al marcar un servicio con `@Transactional`, Spring adquiere la conexión del pool al inicio de la ejecución. Gracias al `SaedDataSourceProxy`, el contexto de sesión (`PKG_SAED_SESSION.SET_CONTEXT`) se aplica en ese exacto instante.

Toda llamada subsiguiente a los Repositorios (dentro del mismo servicio) reutiliza la misma conexión, manteniendo el contexto.

Al finalizar:
- Si el método retorna exitosamente: Spring hace `COMMIT`.
- Si se lanza una `RuntimeException`: Spring hace `ROLLBACK`.
- En cualquiera de los dos casos, Spring llama a `Connection.close()`, lo que desencadena `PKG_SAED_SESSION.CLEAR_CONTEXT()` en el pool proxy.

**Regla de Oro**: Nunca crear hilos (Threads/Asíncronos) dentro del ámbito de `@Transactional` si estos necesitan acceso a base de datos, porque no heredarán automáticamente el `SaedContext` del `ThreadLocal` de Spring, lo cual terminaría lanzando ORA-28115 al intentar operar.
