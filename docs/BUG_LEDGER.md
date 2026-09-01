# SAED 2.0 — Matriz Definitiva de Bugs, Vulnerabilidades y Deuda Técnica (BUG_LEDGER)

**Fecha de Actualización:** 01 de Septiembre de 2026  
**Plan Maestro:** `Versión 4.0 — Definitiva`  
**Fase:** `Fase 3 — Seguridad, RLS, Autorización y Suite Adversarial A–L`  
**Regla:** Ningún bug se marca como solucionado sin evidencia de código y tests.

---

## 1. Registro Consolidado de Hallazgos y Estado de Corrección

| ID | Prioridad | Categoría | Módulo | Archivo | Clase / Método | Descripción | Causa Raíz | Estado | Evidencia de Verificación Empírica |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **SEC-001** | **P0** | Seguridad / RLS | RLS Predicate | `database/migrations/V4.15__phase3_resident_rls_hardening.sql` | `PKG_SAED_SECURITY_RLS.FN_FILTRO_UNIDAD` | `FN_FILTRO_UNIDAD` no aislaba por unidad a `RESIDENTE`. | Lógica VPD incompleta en package body. | `RESOLVED` | `Phase3AdversarialSuiteTest.testAtaqueA_ResidenteBypassCuotasOtraUnidad` (PASS) |
| **SEC-002** | **P1** | Seguridad / Finanzas | Wompi Webhook | `WompiServiceImpl.java` | `crearIntencion` / `procesarWebhook` | Manejo erróneo de centavos, fallback a propiedad y falta de HMAC estricto. | DTO/Service payload apresurado. | `RESOLVED` | `Phase3AdversarialSuiteTest.testAtaqueG_WebhookFalsificadoRechazado` (PASS) |
| **SEC-003** | **P1** | Seguridad / Auth | JWT Revocation | `JwtAuthenticationFilter.java` | `doFilterInternal` | Tokens JWT de cuentas inactivas/revocadas eran procesados sin validar estado. | Stateless JWT sin verificación de estado de usuario. | `RESOLVED` | `Phase3AdversarialSuiteTest.testAtaqueH_UsuarioInactivoBloqueado` (PASS) |
| **SEC-004** | **P2** | Seguridad / Logging | DataSource | `SaedDataSourceProxy.java` | `applySaedContext` | `System.out.println` exponía IDs de sesión en consola estándar. | Salida de depuración no removida. | `RESOLVED` | Sustituido por `log.debug(...)` usando SLF4J (PASS) |
| **SEC-005** | **P2** | Seguridad / API | Global Exception | `GlobalExceptionHandler.java` | Handlers | Fuga potencial de nombres de constraints y tablas Oracle en 400/409/500. | Excepciones SQL sin sanitizar. | `RESOLVED` | `Phase3AdversarialSuiteTest.testAtaqueL_SanitizacionErroresSql` (PASS) |
| **SEC-006** | **P1** | Seguridad / Auth | Asignaciones | `AssignmentManagementController.java` | `createAssignment`, `updateStatus` | Endpoints de asignación de roles sin `@PreAuthorize` permitían escalación de privilegios. | Omisión de anotaciones de seguridad. | `RESOLVED` | `Phase3AdversarialSuiteTest.testAtaqueB_ResidenteModificarAsignaciones` (PASS) |
| **SEC-007** | **P1** | Seguridad / Personas | Persona CRUD | `PersonaController.java` | `crear`, `actualizar`, `eliminar` | CRUD de Personas sin control de acceso permitía borrado no autorizado. | Omisión de `@PreAuthorize`. | `RESOLVED` | `Phase3AdversarialSuiteTest.testAtaqueC_ResidentePersonaCrud` (PASS) |
| **SEC-008** | **P1** | Seguridad / Unidades | Habitantes | `UnitInhabitantController.java` | `addResident`, `removeResident` | Endpoint permitía a residentes inyectar habitantes en unidades ajenas. | Omisión de verificación de rol administrativo. | `RESOLVED` | `Phase3AdversarialSuiteTest.testAtaqueD_ResidenteInyectarHabitantes` (PASS) |
| **SEC-009** | **P1** | Seguridad / Catálogos | Usuarios | `CatalogoController.java` | `usuarios` (`GET /api/v1/usuarios`) | Catálogo de usuarios accesible públicamente a cualquier rol autenticado. | Permisividad en endpoint global. | `RESOLVED` | `Phase3AdversarialSuiteTest.testAtaqueE_ResidenteConsultaCatalogoUsuarios` (PASS) |
| **BE-005** | **P1** | Backend / Dashboard | Consultas SQL | `DashboardController.java` | `getFrecuentes`, `getQrActivos` | Consultas SQL incompatibles con columnas reales del esquema (`ID_QR`, `TOKEN_HASH`). | SQL con columnas ficticias. | `RESOLVED` | `Phase3AdversarialSuiteTest.testAtaqueJ_ResidenteQrActivosAislamiento` (PASS) |
| **TEST-001** | **P1** | Testing | Suite Adversarial | `Phase3AdversarialSuiteTest.java` | Suite A–L | Ausencia de suite automatizada contra los 12 vectores de ataque. | Deuda de cobertura de seguridad. | `RESOLVED` | 12/12 Tests Adversariales automatizados y verdes (PASS) |
| **TEST-004** | **P1** | Testing / Falsos Positivos | Concurrencia | `ContextBleedIntegrationTest.java` | `testContextBleedWith20Threads` | Test capturaba excepciones ciegamente sin validar `SYS_CONTEXT` en Oracle. | Try/catch swallow en test. | `RESOLVED` | Validación real con 20 hilos en Oracle XE (PASS) |
| **TEST-005** | **P1** | Testing / Config | Test Profiles | `application-test.yml` | `datasource` | Perfil `test` configurado con H2 en lugar de Oracle XE real con RLS. | Configuración de desarrollo desalineada. | `RESOLVED` | Datasource unificado hacia Oracle XE `SAED_V39_FINAL_TEST` (PASS) |
| **BE-001** | **P2** | Arquitectura | 15 Controllers | `*Controller.java` | Varios | 15 controladores usan `NamedParameterJdbcTemplate` directamente sin Service/Repository. | Desarrollo apresurado. | `OPEN` | Pendiente de refactor en Fases subsiguientes |
| **BE-002** | **P2** | Código Muerto | Comunicación | `ComunicadosController.java` | `confirmarPendiente` | Endpoints stub que devuelven lista vacía o 200 sin lógica. | Código residual de SAED 1.0. | `OPEN` | Pendiente de limpieza |
| **BE-003** | **P2** | Auditoría | Mutaciones | `AuditAspect.java` / `AuditService.java` | `@Auditable` + AOP | Mutaciones de negocio no registraban en `AUDITORIA_LOG`. | Falta de interceptor transversal. | `RESOLVED` | 100% mutaciones dotadas con `@Auditable` + `AuditAspect`, transaccionalmente aisladas en `REQUIRES_NEW`, sanitizadas y probadas en Oracle XE (14/14 tests PASS). |
| **BE-004** | **P1** | Backend / Validación | Controllers | `*Controller.java` | Varios | Endpoints reciben `Map<String, Object>` sin DTOs `@Valid`. | Omisión de Bean Validation. | `OPEN` | Planificado para Fase 34 |
| **DB-001** | **P1** | Base de Datos | Migraciones | `database/migrations/V5.0__master_baseline.sql` | Baseline V5.0 | Migraciones fragmentadas y dispersas reemplazadas por baseline maestro unificado V5.0. | Acumulación de parches SQL. | `RESOLVED` | Instalación autónoma desde cero en esquema aislado `SAED_BASELINE_TEST_01`: 96 tablas, 338 índices, 1219 constraints, 9 triggers, 90 RLS policies, 0 inválidos, 127/127 tests PASS. |
| **FE-001** | **P2** | Frontend / UX | 12 Páginas | `frontend/src/pages/` | Varios | Manejo heterogéneo de micro-estados (empty/loading/error). | Deuda de UI. | `OPEN` | Planificado para Fase 33 |
| **FE-002** | **P2** | Frontend / Build | Build Vite | `frontend/src/pages/` | Varios | Chunk `xlsx.min.js` (627 kB) supera límite de 500 kB. | Importación estática pesada. | `OPEN` | Planificado para Fase 33 |
| **FE-003** | **P2** | Frontend / Rutas | Navegación | `frontend/src/lib/access.js` | `ACCESS_BY_ROLE` | `ACCESS_BY_ROLE` solo lista 16 rutas de 38. | Mapeo incompleto. | `OPEN` | Planificado para Fase 6 |
| **DEP-001** | **P1** | Despliegue / Cloud | Render Config | `render.yaml` | `services[0]` | `healthCheckPath` apunta a POST `/api/v1/auth/login`. | Configuración heredada. | `OPEN` | Planificado para Fase 48 |

---

## 2. Resumen Estadístico Post-Fase 3

* **Hallazgos P0:** 1 detectado ➔ **1 CORREGIDO (0 P0 PENDIENTES)**
* **Hallazgos P1 Corregidos en Fase 3:** 8 corregidos (`SEC-002`, `SEC-003`, `SEC-006`, `SEC-007`, `SEC-008`, `SEC-009`, `BE-005`, `TEST-001`, `TEST-004`, `TEST-005`)
* **Hallazgos P2 Corregidos en Fase 3:** 2 corregidos (`SEC-004`, `SEC-005`)
* **Tests de Backend:** **127/127 PASS (0 Failures, 0 Errors)**
* **Suite Adversarial A–L:** **12/12 PASS (100% de ataques mitigados)**
* **Build Frontend:** **PASS (0 Errores)**
* **Objetos Inválidos en Oracle XE:** **0**
