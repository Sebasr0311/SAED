# SAED 2.0 — Arquitectura de Auditoría y Trazabilidad Centralizada

**Fecha de Publicación:** 01 de Septiembre de 2026  
**Fase:** `Fase 5 — Auditoría AOP y Trazabilidad`  
**Objetivo:** Trazabilidad inmutable, transaccionalmente aislada y segura para el 100% de operaciones sensibles.

---

## 1. Diagrama de Flujo Arquitectónico

```
HTTP Request
    │
    ▼
[CorrelationIdFilter] ──► Extrae/Genera X-Correlation-Id ──► Setea en MDC y CorrelationIdHolder
    │
    ▼
[JwtAuthenticationFilter] ──► Valida token ──► Setea SaedContext (User, Org, Prop, Role)
    │
    ▼
[Controller Layer] ──► DTO Validation (@Valid)
    │
    ▼
[Service Layer] ──► Interceptado por @Auditable + AuditAspect
    │                      │
    │                      ├─► Resuelve contexto de seguridad (SaedContext)
    │                      ├─► Resuelve IP (X-Forwarded-For seguro) y User-Agent
    │                      ├─► Sanitiza payload recursivamente (AuditSanitizer)
    │                      └─► Ejecuta operación de negocio
    │
    ▼ (Resultado)
[AuditService (REQUIRES_NEW)]
    │
    ├─► Si Negocio OK ──► Persiste AUDITORIA_LOG con RESULTADO = 'EXITOSO'
    └─► Si Negocio EX ──► Persiste AUDITORIA_LOG con RESULTADO = 'FALLIDO' + Relanza Excepción (Rollback seguro)
    │
    ▼
[Response Pipeline] ──► Inyecta X-Correlation-Id en Header HTTP ──► finally: Limpieza estricta de ThreadLocal y MDC
```

---

## 2. Componentes Core

1. **`CorrelationIdHolder`:** Contenedor `ThreadLocal<String>` thread-safe con método `clear()` invocado estrictamente en bloques `finally`.
2. **`CorrelationIdFilter`:** `OncePerRequestFilter` registrado con orden de máxima prioridad (`Ordered.HIGHEST_PRECEDENCE`), valida cadenas alfanuméricas seguras (`^[a-zA-Z0-9_-]{1,64}$`) o genera UUID v4, propagándolo a `MDC` y al encabezado HTTP de respuesta.
3. **`@Auditable`:** Anotación declarativa para marcar métodos de servicios y mutaciones con `action`, `resource`, `category` y `severity`.
4. **`AuditSanitizer`:** Sanitizador universal recursivo que enmascara claves que contienen `password`, `hash`, `token`, `jwt`, `secret`, `signature`, `cvv`, `apiKey` tanto en objetos planos, POJOs, colecciones como en cadenas JSON.
5. **`AuditService` (`AuditServiceImpl`):** Implementación con `@Transactional(propagation = Propagation.REQUIRES_NEW)` que escribe en `AUDITORIA_LOG`. Al ejecutarse en una transacción independiente, las auditorías de operaciones fallidas sobreviven al rollback de la transacción de negocio.
6. **`AuditAspect`:** Aspecto Spring AOP (`@Around("@annotation(auditable)")`) que captura métricas de ejecución, parámetros de entrada, entidad afectada y resultado.

---

## 3. Comportamiento Transaccional y Manejo de Errores

- **Éxito:** La transacción de negocio hace `COMMIT` y la auditoría queda persistida con `RESULTADO = 'EXITOSO'`.
- **Fallo / Excepción:** La transacción de negocio hace `ROLLBACK`. `AuditAspect` captura la excepción, invoca `AuditService.recordFailure` (en transacción `REQUIRES_NEW` que hace `COMMIT` del log de fallo) y relanza la excepción original intacta sin enmascarar errores ni alterar el flujo de Spring ExceptionHandler.
- **Fail-Safe de Auditoría:** Si la base de datos no puede registrar el log de auditoría por un fallo de infraestructura, el error es capturado y registrado mediante SLF4J para evitar que errores del subsistema de logging corrompan la continuidad operativa.
