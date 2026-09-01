# SAED 2.0 — Matriz Definitiva de Bugs, Vulnerabilidades y Deuda Técnica (BUG_LEDGER)

**Fecha:** 01 de Septiembre de 2026  
**Plan Maestro:** `Versión 4.0 — Definitiva`  
**Fase:** `Fase 1 — Auditoría Definitiva`  
**Regla:** Ningún bug se marca como solucionado durante la Fase 1. Todos los hallazgos permanecen en estado `OPEN` o `REQUIRES VERIFICATION`.

---

## 1. Registro Consolidado de Hallazgos

| ID | Categoría | Severidad | Módulo | Archivo(s) | Línea(s) | Descripción del Problema | Comportamiento Actual | Comportamiento Esperado | Evidencia / Reproducción | Estado |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **SEC-001** | `Seguridad / RLS` | **P0** | `RLS Predicate` | `database/modelo_relacional_v4_atp.sql` / `PKG_SAED_SECURITY_RLS` | L55-75 | `FN_FILTRO_UNIDAD` filtra por propiedad para todos los roles, omitiendo aislar por unidad específica a residentes. | Residente puede ver cuotas de toda la copropiedad si Java no agrega `WHERE id_unidad = :u`. | Debe retornar `id_unidad IN (SELECT id_unidad FROM RESIDENTES_UNIDAD WHERE id_persona = ...)` para `RESIDENTE`. | Consulta SQL a `PKG_SAED_SECURITY_RLS` en Oracle XE. | `OPEN` |
| **SEC-002** | `Seguridad / Finanzas` | **P1** | `Wompi Webhook` | `WompiServiceImpl.java` | L73-120 | Falta de validación estricta de monto y asignación de tenant en webhook de Wompi. | `idUnidad` hace fallback a `idPropiedad`; `concepto` se guarda en campo `METODO_ORIGEN`. | Validación atómica de monto exacto en centavos, matching de unidad y método de pago real ('CARD','PSE'). | Código fuente de `WompiServiceImpl.java:73`. | `OPEN` |
| **SEC-003** | `Seguridad / Auth` | **P1** | `JWT Token` | `JwtAuthenticationFilter.java` | L40-80 | Inexistencia de mecanismo de revocación de JWT tras cambio de rol o bloqueo de usuario. | JWT sigue siendo válido hasta expirar a las 24 horas. | Validación contra blacklist o verificación de estado de usuario activo en cache/DB. | Análisis de flujo de autenticación stateless. | `OPEN` |
| **SEC-004** | `Seguridad / Logging` | **P2** | `DataSource` | `SaedDataSourceProxy.java` | L52 | Impresión de datos de contexto en `System.out` en cada checkout de conexión del pool. | Imprime `SAED CONTEXT TO ORACLE: userId=...` en consola estándar. | Registro con SLF4J a nivel `DEBUG` desactivado en producción. | `SaedDataSourceProxy.java:52`. | `OPEN` |
| **SEC-005** | `Seguridad / API` | **P2** | `Global Exception` | `GlobalExceptionHandler.java` | L45-90 | Posibilidad de fuga de detalles internos de base de datos en mensajes de error HTTP. | Mensaje de excepción puede incluir nombres de constraints o columnas Oracle. | Sanitización universal de respuestas de error con códigos de negocio controlados. | Inspección de `GlobalExceptionHandler.java`. | `OPEN` |
| **BE-001** | `Backend / Arquitectura`| **P2** | 15 Controladores | `*Controller.java` (Cartera, Gastos, Alertas, Wompi, etc.) | Varios | Evasión de la capa de servicio/repositorio con `NamedParameterJdbcTemplate` en controladores. | Controladores contienen SQL embebido y mapeos directos. | Controladores desacoplados que delegan en Services y Repositories tipados. | Búsqueda estática de `NamedParameterJdbcTemplate` en paquetes `controller`. | `OPEN` |
| **BE-002** | `Backend / Dead Code` | **P2** | `Comunicación` | `ComunicadosController.java` | L78-88 | Endpoints stub heredados de V1 (`/confirmar-pendiente`, `/confirmar`). | Retornan lista vacía o 200 sin lógica con comentario `Feature removed in V4 schema`. | Eliminar endpoints obsoletos y limpiar rutas frontend asociadas. | `ComunicadosController.java:78-88`. | `OPEN` |
| **BE-003** | `Backend / Auditoría` | **P2** | `Auditoría` | `*Service.java` / `*Controller.java` | Varios | Mutaciones de negocio estándar no registran trazas en `AUDITORIA_LOG`. | Solo los errores 403/401 llaman a `SP_REGISTRAR_AUDITORIA`. | Registrar todas las operaciones sensibles (creación, edición, eliminación, cobros). | Análisis de llamadas a `SP_REGISTRAR_AUDITORIA`. | `OPEN` |
| **BE-004** | `Backend / Validación`| **P1** | 10 Controladores | `*Controller.java` | Varios | Uso de `Map<String, Object> payload` sin validaciones de Bean Validation (`@Valid`). | Parámetros nulos o inválidos llegan hasta la base de datos provocando excepciones de runtime. | DTOs fuertemente tipados con anotaciones `@NotNull`, `@Size`, `@Positive`, etc. | Inspección de firmas de métodos en controladores. | `OPEN` |
| **DB-001** | `BD / Migraciones` | **P1** | `Migraciones SQL` | `database/migrations/` | 17 archivos | Cadena de migraciones fragmentada con versiones superpuestas y parches manuales. | No es posible levantar una base de datos nueva desde cero de manera desatendida con Flyway. | Consolidar en `database/schema/V5.0__master_baseline.sql` reproducible. | Inspección de directorio `database/migrations/`. | `OPEN` |
| **FE-001** | `Frontend / UX` | **P2** | 12 Páginas | `frontend/src/pages/` | Varios | Manejo heterogéneo de estados de carga, error y vacío (`empty states`). | Algunas páginas muestran pantallas en blanco o mensajes de texto planos. | Uso estandarizado de componentes `DataTable` y estados vacíos con ilustraciones/iconos. | Inspección visual de componentes de página. | `OPEN` |
| **FE-002** | `Frontend / Bundle` | **P2** | `Build` | `frontend/src/pages/` | Varios | Chunks pesados superan 500 kB debido a importación estática de `xlsx`. | Advertencia de bundle size en `npm run build` (`xlsx.min.js` 627 kB). | Carga dinámica con `import('xlsx')` y `React.lazy` para páginas poco frecuentes. | Output de `npm run build`. | `OPEN` |
| **TEST-001** | `Testing / Cobertura` | **P1** | `Suite de Pruebas` | `backend/src/test/` | Varios | Ausencia de tests automatizados para Ataques Adversariales E a L. | Solo existen tests para Ataques A a D. | Implementar clases de prueba para los 12 ataques formales del Plan v4.0. | Inventario de 39 clases de prueba. | `OPEN` |
| **TEST-002** | `Testing / E2E` | **P2** | `End-to-End` | N/A | N/A | Falta de suite automatizada de pruebas End-to-End en navegador con Playwright. | No se prueban interacciones reales de interfaz contra el backend en CI/CD. | Suite de smoke tests E2E para los 4 roles principales. | Inspección de árbol de proyecto. | `OPEN` |
| **FUNC-001** | `Funcionalidad` | **P2** | `Automatizaciones` | N/A | N/A | Motor de automatizaciones (Evento ➔ Condición ➔ Acción) pendiente de implementación. | Tablas creadas en base de datos pero sin motor de ejecución ni UI. | Desarrollar motor en Fase 30 según Documento Maestro. | Comparación contra Documento Maestro. | `OPEN` |
| **FUNC-002** | `Funcionalidad` | **P2** | `Consumos` | N/A | N/A | Medición de servicios públicos y consumos por unidad pendiente de desarrollo. | Tabla `MEDICIONES_CONSUMO` sin API ni vista de lecturas. | Desarrollar módulo de consumos en Fase 28. | Comparación contra Documento Maestro. | `OPEN` |
| **TECH-003** | `Deuda / Legacy` | **P3** | `Repositorio` | `backend_legacy/` | 164 archivos | Código antiguo de SAED 1.0 presente en el repositorio. | Archivos obsoletos consumen espacio y generan ruido en búsquedas. | Archivar o excluir de la rama principal en Fase 42. | `git ls-files backend_legacy`. | `OPEN` |

---

## 2. Resumen Estadístico de Severidades

| Severidad | Abiertos | En Verificación | Resueltos |
| :--- | :--- | :--- | :--- |
| **P0 (Bloqueante)** | **1** (`SEC-001`) | 0 | 0 |
| **P1 (Crítico)** | **5** (`SEC-002`, `SEC-003`, `BE-004`, `DB-001`, `TEST-001`) | 0 | 0 |
| **P2 (Importante)** | **10** (`SEC-004`, `SEC-005`, `BE-001`, `BE-002`, `BE-003`, `FE-001`, `FE-002`, `TEST-002`, `FUNC-001`, `FUNC-002`) | 0 | 0 |
| **P3 (Menor / Deuda)** | **1** (`TECH-003`) | 0 | 0 |
| **TOTAL HALLAZGOS** | **17** | **0** | **0** |
