# SAED 2.0 — Informe Formal de Auditoría de Bugs, Errores y Deuda Técnica (Fase 1)

**Fecha de Auditoría:** 01 de Septiembre de 2026  
**Documento Base:** `SAED 2.0 — Plan Maestro Definitivo de Finalización, Hardening, Corrección y Producción (Versión 4.0 — Definitiva)`  
**Fase:** `FASE 1 — MATRIZ DEFINITIVA DE BUGS, ERRORES Y DEUDA TÉCNICA`  
**Responsable:** Senior Architect & Lead Security Auditor  
**Veredicto de Gate Fase 1:** **APROBADO (LÍNEA DE BASE DE DEFECTOS ESTABLECIDA Y VERIFICADA)**  

---

## 1. Resumen Ejecutivo
Se ha llevado a cabo una inspección exhaustiva de 360 grados sobre el código fuente, la base de datos viva Oracle XE (`SAED_V39_FINAL_TEST`), los mecanismos de seguridad multi-tenant (RLS), las integraciones externas (Wompi, Brevo, PDF), la cadena de migraciones, la suite de 115 pruebas automatizadas y las 58 pantallas del frontend.

El sistema presenta una arquitectura funcional y una suite de pruebas 100% en verde, pero **no se encuentra libre de defectos**. Se han identificado y clasificado **17 hallazgos reales con evidencia empírica**, incluyendo 1 problema bloqueante P0 en el predicado RLS de unidad, 5 problemas críticos P1 en integraciones y migraciones, 10 problemas importantes P2 en arquitectura de controladores y observabilidad, y 1 ítem P3 de limpieza legacy.

---

## 2. Metodología
La auditoría combinó:
1. **Inspección estática de código:** Análisis de 43 controladores, servicios, repositorios y 58 componentes de página.
2. **Consultas dinámicas al catálogo Oracle:** Inspección de `USER_TABLES`, `ALL_POLICIES`, `USER_TRIGGERS`, `USER_SOURCE` y `USER_CONSTRAINTS`.
3. **Ejecución de suites de prueba:** `mvn clean test` sobre los 115 tests unitarios e integrados.
4. **Validación de compilación frontend:** `npm run build` con Vite.
5. **Secret Scanning:** Búsqueda regex exhaustiva de credenciales y tokens en el repositorio.
6. **Mapeo de requerimientos:** Comparación contra el `Documento Maestro SAED 2.0`.

---

## 3. Evidencia Utilizada
* **Catálogo Oracle XE:** 96 tablas, 336 índices, 1.228 constraints, 9 triggers, 90 políticas RLS.
* **Logs de Maven Surefire:** 115 pruebas ejecutadas en 52.358s con 0 fallos.
* **Logs de Vite Build:** 2.000 módulos empaquetados en 24.80s.
* **Git Status:** Árbol limpio sobre commit `db7d3d4` / `16904a5`.

---

## 4. Estado Backend
* **43 Controladores REST:** Todos protegidos con `@PreAuthorize`.
* **Hallazgo Principal:** 15 controladores acceden directamente a la base de datos usando `NamedParameterJdbcTemplate` en lugar de delegar en servicios y repositorios (`BE-001`).
* **Validación de Entrada:** Uso frecuente de `Map<String, Object>` sin DTOs validados con `@Valid` (`BE-004`).

---

## 5. Estado Frontend
* **58 Páginas React:** Rutas declaradas y protegidas por rol en `App.jsx`.
* **Manejo de Estado:** `DataTable` y componentes unificados en pantallas principales.
* **Oportunidad de Hardening:** Pantallas secundarias (`GananciasPage`, `FlujoCajaPage`) requieren estandarización de `empty states` y carga diferida (`React.lazy`) para chunks grandes como `xlsx` (`FE-001`, `FE-002`).

---

## 6. Estado Base de Datos
* **Integridad Relacional:** 100% de tablas y restricciones activas.
* **Inmutabilidad de Auditoría:** Trigger `TRG_AUDITORIA_INMUTABLE` bloquea `UPDATE` y `DELETE` sobre `AUDITORIA_LOG`.
* **Secuencias Identity:** Corregidas con `START WITH LIMIT VALUE` para evitar colisiones `ORA-00001`.

---

## 7. Estado RLS (Virtual Private Database)
* **Políticas Activas:** 90 políticas RLS en `SAED_V39_FINAL_TEST`.
* **Vulnerabilidad Crítica (`SEC-001` - P0):** `FN_FILTRO_UNIDAD` no aísla por unidad específica al rol `RESIDENTE` a nivel de base de datos; devuelve todas las unidades de la copropiedad delegando el filtrado a Java.

---

## 8. Estado Seguridad
* **Autenticación:** JWT stateless sin privilegios en payload.
* **Aislamiento de Sesión:** `SaedDataSourceProxy` aplica `SET_BOOTSTRAP_CONTEXT` y `SET_CONTEXT` antes de ejecutar transacciones.
* **Revocación:** No existe blacklist para revocar JWTs antes de su expiración de 24h (`SEC-003`).

---

## 9. Estado Testing
* **66 Tests de Integración:** Ejecutados contra Oracle XE real.
* **18 Tests Adversariales:** Cubren aislamiento para Fases B, C y D.
* **Brecha Identificada (`TEST-001` - P1):** Faltan tests automatizados para Ataques E a L (mutaciones cross-tenant, escalada vertical y concurrencia).

---

## 10. Estado Integraciones
* **Wompi (`SEC-002` - P1):** Servicio implementado con intenciones y firma SHA-256; requiere validación estricta de webhook, montos en centavos y pruebas en sandbox oficial.
* **Brevo / Email (`EmailService`):** Envío vía API HTTP v3 funcional; emite advertencias controladas si falta `BREVO_API_KEY`.
* **PDF (`PdfServiceImpl`):** Motor `openhtmltopdf` (PDFBox) funcional para XHTML.

---

## 11. Estado Migraciones
* **Deuda Crítica (`DB-001` - P1):** 17 archivos en `database/migrations/` con parches fragmentados y scripts de rollback. Se requiere unificar en `V5.0__master_baseline.sql`.

---

## 12. Bugs P0 (Bloqueantes)
* **`SEC-001`:** `FN_FILTRO_UNIDAD` en `PKG_SAED_SECURITY_RLS` no aísla por `id_unidad` a residentes.

---

## 13. Bugs P1 (Críticos)
* **`SEC-002`:** Validación incompleta de montos/idempotencia en webhook Wompi.
* **`SEC-003`:** Falta de revocación inmediata de JWT ante desactivación de usuarios.
* **`BE-004`:** Ausencia de validación con Bean Validation (`@Valid`) en controladores que usan `Map<String, Object>`.
* **`DB-001`:** Cadena de migraciones fragmentada no reproducible desde cero.
* **`TEST-001`:** Ausencia de tests adversariales para Ataques E a L.

---

## 14. Bugs P2 (Importantes)
* **`BE-001`:** 15 controladores con SQL embebido y `NamedParameterJdbcTemplate` directo.
* **`BE-002`:** Endpoints stub obsoletos en `ComunicadosController`.
* **`BE-003`:** Falta de registro de mutaciones operativas en `AUDITORIA_LOG`.
* **`SEC-004`:** `System.out.println` en `SaedDataSourceProxy.java:52`.
* **`SEC-005`:** Posible fuga de detalles SQL en `GlobalExceptionHandler`.
* **`FE-001`:** Manejo heterogéneo de empty states en frontend.
* **`FE-002`:** Chunks pesados (`xlsx` 627 kB) en compilación de frontend.
* **`TEST-002`:** Falta de suite E2E con Playwright.
* **`FUNC-001`:** Motor de automatizaciones pendiente de desarrollo.
* **`FUNC-002`:** Módulo de medición de consumos pendiente de desarrollo.

---

## 15. Bugs P3 (Menores / Deuda)
* **`TECH-003`:** 164 archivos obsoletos de SAED 1.0 en `backend_legacy/`.

---

## 16. Deuda Técnica Consolidada
* Reorganización en capas limpias (Controller ➔ Service ➔ Repository).
* Consolidación de migraciones en baseline único `V5.0`.
* Sustitución de `System.out` por SLF4J `DEBUG`.
* AOP para auditoría automática de mutaciones.

---

## 17. Código Muerto
* Métodos `/confirmar-pendiente` y `/confirmar` en `ComunicadosController.java`.

---

## 18. Código Duplicado
* Consultas SQL de extracción de asignaciones repetidas en controladores financieros.

---

## 19. Módulos Incompletos
* Wompi (validación webhook), Cartera (desacoplamiento de capas), Presupuestos/Gastos, Conciliaciones, Paz y Salvos, Mascotas extendido, Poderes de Asambleas.

---

## 20. Módulos Inexistentes
* Motor de Automatizaciones (Fase 30) y Mediciones de Consumo (Fase 28).

---

## 21. Dependencias entre Módulos y Defectos
```text
RLS Fix (SEC-001) ──► Ataques Adversariales (TEST-001) ──► Hardening Backend (BE-001/BE-004)
        │
        ▼
Wompi Fix (SEC-002) ──► Finanzas (Cuotas / Cartera) ──► Conciliación y Dashboards
```

---

## 22. Riesgos Principales
1. **Fuga de datos horizontal:** Si se omite `WHERE id_unidad = :u` en Java mientras `SEC-001` siga abierto.
2. **Inconsistencia en despliegue Cloud:** Si se intenta migrar a Oracle ATP sin consolidar `V5.0__master_baseline.sql`.

---

## 23. Bloqueadores para Producción
* `SEC-001` (Aislamiento de unidad en RLS).
* `SEC-002` (Seguridad en pagos Wompi).
* `DB-001` (Baseline reproducible de BD).
* `TEST-001` (Demostración de los 12 ataques adversariales).

---

## 24. Recomendación de Orden de Reparación
1. **Fase 2:** Auditoría de Arquitectura (Eliminar bypasses de controladores a JDBC).
2. **Fase 3:** Corrección de RLS (`SEC-001`) y ejecución de la suite de 12 Ataques Adversariales.
3. **Fase 4:** Normalización del Baseline Maestro de Oracle `V5.0` (`DB-001`).
4. **Fase 5:** Auditoría unificada con AOP sobre `AUDITORIA_LOG`.
5. **Fases 6 a 30:** Estabilización funcional y desarrollo de módulos faltantes.

---

## 25. Evidencia de Comandos Ejecutados
* `mvn clean test` ➔ **BUILD SUCCESS** (115 tests en 52.358s).
* `npm run build` ➔ **BUILD SUCCESS** (2.000 módulos en 24.80s).
* `git status` ➔ **Clean** (0 modificaciones pendientes).

---

## 26. Evidencia de Queries Oracle
* `SELECT COUNT(*) FROM user_tables;` ➔ `96`
* `SELECT COUNT(*) FROM all_policies WHERE object_owner = 'SAED_V39_FINAL_TEST';` ➔ `90`
* `SELECT COUNT(*) FROM user_objects WHERE status = 'INVALID';` ➔ `0`

---

## 27. Evidencia de Pruebas HTTP / API
* Pruebas de integración MockMvc en `Phase1AAuthIntegrationTest` y `Phase1BAuthorizationIntegrationTest` demostraron respuestas HTTP 200/201 correctas bajo contextos válidos y 401/403 ante credenciales/asignaciones no autorizadas.

---

## 28. Estado Final de Cada Criterio
* Backend Auditado: ✅
* Frontend Auditado: ✅
* Base de Datos Auditada: ✅
* RLS Auditado: ✅
* Testing Auditado: ✅
* Integraciones Auditadas: ✅
* Deuda Técnica Clasificada: ✅

---

## 29. Conclusión
La **Fase 1** ha establecido una fotografía exhaustiva, objetiva y respaldada por evidencia empírica de todos los defectos y oportunidades de mejora de SAED 2.0. El proyecto cuenta con una base sólida, pero requiere resolver los 6 defectos bloqueantes/críticos (P0 y P1) en las fases subsecuentes del Plan Maestro v4.0.
