# SAED 2.0 — Auditoría Integral de Seguridad y Aislamiento

**Fecha:** 01 de Septiembre de 2026  
**Plan Maestro:** `Versión 4.0 — Definitiva`  
**Fase:** `Fase 1 — Auditoría Definitiva`  
**Auditor:** Principal Security Architect  

---

## 1. Resumen Ejecutivo de Seguridad

La arquitectura de seguridad de SAED 2.0 combina:
1. **Autenticación:** JWT stateless emitido tras validación en `PKG_AUTH_BOOTSTRAP` (sin roles en payload para evitar privilege spoofing).
2. **Contexto de Sesión:** `SaedContext` resuelto en `JwtAuthenticationFilter` a partir del header `X-Assignment-Id` validado en base de datos.
3. **Aislamiento Físico:** `SaedDataSourceProxy` aplica `SET_BOOTSTRAP_CONTEXT` y `SET_CONTEXT` sobre la conexión JDBC antes de entregarla al hilo de ejecución.
4. **Oracle Virtual Private Database (VPD/RLS):** 90 políticas RLS activas en `SAED_V39_FINAL_TEST`.

A pesar de contar con una base de seguridad robusta y 115 tests en verde, se detectaron vulnerabilidades y oportunidades de endurecimiento críticas antes de producción.

---

## 2. Matriz de Hallazgos de Seguridad

| ID | Severidad | Módulo | Archivo / Componente | Vulnerabilidad / Riesgo | Comportamiento Actual | Comportamiento Esperado | Estado |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **SEC-001** | **P0** | `RLS Predicate` | `PKG_SAED_SECURITY_RLS` (`FN_FILTRO_UNIDAD`) | Aislamiento RLS a nivel de propiedad en lugar de unidad para RESIDENTE en tablas de unidad. | `FN_FILTRO_UNIDAD` retorna `id_unidad IN (SELECT id_unidad FROM UNIDADES WHERE id_propiedad = :prop)` para todos los roles, permitiendo que un residente consulte filas de otras unidades si el backend no agrega `WHERE id_unidad = :u`. | Debe retornar `id_unidad IN (SELECT id_unidad FROM RESIDENTES_UNIDAD WHERE id_persona = (SELECT id_persona FROM USUARIOS WHERE id_usuario = :usr))` cuando `ROL_CODIGO = 'RESIDENTE'`. | `OPEN` |
| **SEC-002** | **P1** | `Wompi Webhook` | `WompiServiceImpl.java` | Falta de validación estricta de monto y tenant en procesamiento de eventos webhook. | El webhook actualiza el estado basándose en la referencia pero no verifica si el monto cobrado coincide con el saldo de la cuota en centavos ni valida pertenencia del pago en un paso transaccional atómico. | Validación obligatoria de: Checksum SHA-256 con `WOMPI_EVENTS_SECRET`, matching de monto exacto en centavos, idempotencia por `ID_TRANSACCION_PASARELA` y matching de tenant. | `OPEN` |
| **SEC-003** | **P1** | `JWT Invalidation` | `JwtAuthenticationFilter.java` / `JwtProvider.java` | Ausencia de lista de revocación de tokens (Token Blacklist / Invalidation). | Los JWT emitidos son válidos hasta su expiración (24h) sin posibilidad de revocación inmediata si el usuario cambia de rol o es desactivado en Oracle. | Validar contra cache/DB si el token fue revocado o si la asignación sigue `ACTIVA` (actualmente `AssignmentService.validateAssignment` valida asignación, pero tokens de login general no se revocan). | `OPEN` |
| **SEC-004** | **P2** | `DataSource Logging` | `SaedDataSourceProxy.java:52` | Exposición de IDs de usuario, organización y rol en `System.out` en cada obtención de conexión del pool. | Imprime en consola estándar: `SAED CONTEXT TO ORACLE: userId=... orgId=... propId=... role=...`. | Migrar a logger estructurado en nivel `DEBUG` configurable por entorno, desactivado en producción. | `OPEN` |
| **SEC-005** | **P2** | `Exception Handling` | `GlobalExceptionHandler.java` | Manejo de excepciones con posibilidad de fuga de detalles SQL internos. | Aunque se eliminó `printStackTrace()`, algunos mensajes de `DataIntegrityViolationException` o `SQLException` podrían exponer nombres de constraints o esquemas en respuestas HTTP 400/409. | Sanitización universal de respuestas de error devolviendo códigos canónicos y mensajes neutrales. | `OPEN` |
| **SEC-006** | **P2** | `CORS / CSP` | `SecurityConfig.java` | Cabeceras de seguridad CSP (Content Security Policy) y HSTS no configuradas explícitamente para producción. | Configuración básica de CORS permitiendo `localhost:5173` sin definición de perfiles estrictos de producción. | Configurar HSTS, X-Content-Type-Options: nosniff, X-Frame-Options: DENY, y CSP estricto. | `OPEN` |

---

## 3. Verificación de Secret Scanning

* **Archivos escaneados:** Todo el árbol de `backend/src/main` y `frontend/src`.
* **Resultado:** **0 secretos de producción activos detectados**.
* **Observación:** El archivo local `Parche.java` fue eliminado del disco. `.env` permanece correctamente ignorado en `.gitignore`.
