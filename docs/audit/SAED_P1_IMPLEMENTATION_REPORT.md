# SAED 2.0 — REPORTE DE IMPLEMENTACIÓN CONTROLADA DE GAP P1

---

## 1. Repository State

* **Repositorio**: `https://github.com/Sebasr0311/SAED`
* **Rama Activa**: `Sebasr0311/angelfish`
* **Commit Base**: `f7767f07595b302105a4cb4d381acf76501dd0fd` (`chore(repo): clean and reorganize repository for SAED 2.0`)
* **Commit Creado**: **NO** (Conforme a la regla de no realizar commits automáticos).
* **Push Realizado**: **NO**.
* **Estado del Working Tree**: 5 archivos rastreados modificados quirúrgicamente, 2 reportes de auditoría generados en `docs/audit/`.

---

## 2. Implemented Changes

### GAP-01 — Render Health Check
* **Archivo**: `render.yaml`
* **Línea**: 11
* **Problema**: `healthCheckPath` apuntaba a `/api/v1/auth/login`. Al ser un endpoint que exige HTTP POST con credenciales, la sonda de liveness de Render (que emite HTTP GET) recibía sistemáticamente un código `405 Method Not Allowed`, provocando potenciales fallos de despliegue y reinicios en bucle del servicio web.
* **Solución**: Se actualizó `healthCheckPath` para apuntar al endpoint canónico de liveness `/api/v1/health` expuesto por `HealthController.java`.
* **Impacto**: Despliegues continuos estables en Render. Respuesta garantizada `HTTP 200 OK` (`{"status":"UP"}`) sin dependencia de la base de datos ni requerimientos de autenticación (`permitAll()`).

### GAP-06 — Paz y Salvo
* **Archivo**: `frontend/src/pages/PazYSalvoPage.jsx`
* **Línea**: 22
* **Problema**: El hook de consulta invocaba `api.get('/unidades')`. Al anteponer el cliente central `api.js` la base `/api/v1`, la petición salía como `/api/v1/unidades`. Dicho endpoint en español no existe en el backend moderno, respondiendo `404 Not Found` e impidiendo poblar la lista de apartamentos para emitir paz y salvos.
* **Solución**: Se actualizó la llamada a `api.get('/units')`, alineándola con el contrato canónico expuesto por `UnitController.java` (`@RequestMapping("/api/v1/units")`).
* **Impacto**: Carga inmediata y exitosa de las unidades habitacionales en el selector del modal de emisión de certificados de Paz y Salvo.

### GAP-02 — Mantenimiento
* **Archivo**: `frontend/src/pages/MantenimientoAdminPage.jsx`
* **Líneas**: 11, 12
* **Problema**: El componente ejecutaba `api.get('/api/mantenimiento')` y `api.get('/api/mantenimiento/activos')`. Como `api.js` ya incluye `/api/v1` en su `BASE_URL`, se generaban URLs malformadas con doble prefijo: `/api/v1/api/mantenimiento` (error HTTP 404).
* **Solución**: Se eliminó el prefijo redundante `/api`, quedando como `api.get('/mantenimiento')` y `api.get('/mantenimiento/activos')`.
* **Impacto**: Corrección sintáctica del contrato frontend. Se preservó el estado de "Módulo Fantasma" claramente documentado en la UI, sin implementar controladores ni servicios backend prematuros fuera del alcance de este ciclo.

### GAP-03 — Contratos UI ↔ API
* **Archivo**: `frontend/src/pages/ContratosPage.jsx`
* **Líneas afectadas**: 65-76, 114-230, 256-298, 466-531
* **Problema**: El componente contenía llamadas huérfanas hacia `/sugerir-tipo`, `/renovar` y `/reenviar-correo`, las cuales pertenecían a la arquitectura monolítica antigua (`backend_legacy/.../ContratoHandler.java`) y no existen en `ContratosController.java`. Al interactuar con ellas, la interfaz arrojaba errores 404.
* **Solución**: Se eliminaron las funciones y botones huérfanos que disparaban los endpoints inexistentes:
  1. Se removió la función `sugerirTipo` y su invocación en `onApartamentoChange`, dejando la selección manual del tipo de contrato (`tipoContrato`) o vinculación de plantilla institucional (`idPlantilla`).
  2. Se removieron la acción de tabla y el modal de renovación legacy (`/renovar`), canalizando cualquier extensión contractual a través de la creación canónica (`POST /api/v1/contratos`) con `tipoContrato = 'RENOVACION'`.
  3. Se removieron el botón de tabla y diálogo de reenvío manual de correo (`/reenviar-correo`), preservando intacto el envío automático de notificaciones por email/PDF al momento de crear el contrato en `FinanzasServiceImpl.java`.
* **Impacto**: La pantalla de contratos opera al 100% sobre endpoints reales y vigentes de Spring Boot (`/api/v1/contratos`, `/api/v1/contratos/{id}/activar`, `/api/v1/contratos/{id}/cancelar`, `/api/v1/contratos/plantillas/activas`). Cero llamadas 404.

---

## 3. GAP-03 Decision & Architecture Alignment

### 3.1 Análisis de Acciones Legacy vs Modernas
1. **Sugerencia de Tipo (`/sugerir-tipo`)**: En el sistema anterior, una heurística simple examinaba contratos históricos de la unidad. En SAED 2.0, el modelo corporativo delega la gobernanza legal en **Plantillas Institucionales** (`PLANTILLAS_CONTRATOS` expuestas vía `ContratosPlantillasController.java:28`), permitiendo a la administración elegir la minuta exacta configurada para la copropiedad o seleccionar directamente el tipo (`INICIAL`, `RENOVACION`, `PERMANENCIA`, etc.). La sugerencia legacy era código muerto que entorpecía la UX con un error 404 por cada apartamento seleccionado.
2. **Renovación (`/renovar`)**: El endpoint antiguo realizaba una mutación directa sin pasar por las validaciones multi-tenant de plantillas ni los controles de unicidad de contratos activos introducidos en la Fase 6 (`ArrendatarioContratosSecurityIntegrationTest`). En SAED 2.0, un contrato nuevo de tipo renovación se emite de forma transparente a través de `POST /api/v1/contratos` una vez cancelado o concluido el contrato anterior.
3. **Reenvío de Correo (`/reenviar-correo`)**: En el backend moderno, `FinanzasServiceImpl.java` renderiza el HTML mediante `TemplateRenderService`, compila el binario con `PdfService` y despacha el correo mediante Brevo durante la creación del contrato (`POST /api/v1/contratos`). No existe justificación para reabrir endpoints manuales sin un diseño formal de auditoría y cuotas de envío.

### 3.2 ¿Por qué NO se crearon endpoints legacy en el backend?
Crear endpoints artificiales en el backend para complacer llamadas de una interfaz obsoleta violaría el principio de arquitectura limpia y aumentaría la deuda técnica:
* Habría duplicado la lógica de creación contractual.
* Habría eludido las políticas de validación de plantillas institucionales (`PLANTILLAS_CONTRATOS`) garantizadas en la Fase 6.
* Habría creado una falsa sensación de soporte sin contar con pruebas de integración ni auditoría transaccional.

---

## 4. Cross-GAP Finding — Org Gastos Properties Endpoint

* **Archivo**: `frontend/src/pages/OrgGastosPage.jsx`
* **Línea**: 75
* **Problema detectado**:
  ```javascript
  const { data: propData } = useFetch(() => api.get('/org/propiedades'), []);
  ```
  La ruta `/api/v1/org/propiedades` no existe en el backend. El recurso unificado de propiedades en SAED 2.0 es `/api/v1/properties`, expuesto por `PropertyController.java` y consumido correctamente por `OrgPropiedadesPage.jsx:55`.
* **Solución aplicada**: Se cambió la llamada a:
  ```javascript
  const { data: propData } = useFetch(() => api.get('/properties'), []);
  ```
* **Justificación**: Se corrigió en esta misma intervención por tratarse de un hallazgo directo durante la auditoría de contratos API, equivalente a GAP-06.
* **Nota sobre GAP-05**: GAP-05 continúa clasificado como **`FALSE POSITIVE`**. La interpolación `${queryString ? `?${queryString}` : ''}` en la línea 96 de `OrgGastosPage.jsx` es totalmente válida, limpia y no fue modificada.

---

## 5. Verification & Validation

### 5.1 Compilación de Frontend (`npm run build`)
* **Directorio**: `frontend`
* **Resultado**: `✓ built in 16.90s`
* **Estado**: **PASS (Exit Code 0)**.
* **Evidencia**: Los bundles de producción (`dist/assets/ContratosPage-*.js`, `dist/assets/PazYSalvoPage-*.js`, `dist/assets/MantenimientoAdminPage-*.js`, `dist/assets/OrgGastosPage-*.js`) se generaron satisfactoriamente sin advertencias críticas ni errores de sintaxis.

### 5.2 Compilación de Backend (`mvn test-compile`)
* **Directorio**: `backend`
* **Resultado**: `BUILD SUCCESS` (Total time: 3.690 s).
* **Estado**: **PASS (Exit Code 0)**.

### 5.3 Pruebas de Integración y Seguridad
* **Comando**: `mvn test -Dtest=ArrendatarioContratosSecurityIntegrationTest -Djvm=...`
* **Suite ejecutada**: `com.saed.backend.security.ArrendatarioContratosSecurityIntegrationTest`
* **Resultados**:
  * Tests run: **10**
  * Failures: **0**
  * Errors: **0**
  * Skipped: **0**
  * Tiempo de ejecución: 29.85 s
  * Estado general: **BUILD SUCCESS (Exit Code 0)**.

---

## 6. Security & Database Posture

```text
DB Impact: NONE
Security Impact: NONE
Tenant Isolation: PRESERVED
RBAC: PRESERVED
VPD/RLS: PRESERVED
Zero-Trust Context: PRESERVED
```

* **Oracle Database**: Cero modificaciones en tablas, columnas, restricciones, triggers, secuencias o paquetes PL/SQL.
* **Seguridad Spring Boot**: No se alteraron `SecurityConfig`, `JwtAuthenticationFilter`, `SaedDataSourceProxy`, ni anotaciones `@PreAuthorize`.
* **Aislamiento Multi-Tenant**: Las llamadas a `/units`, `/properties` y `/contratos` continúan inyectando `X-Assignment-Id` y ejecutando bajo `PKG_SAED_SESSION.SET_CONTEXT`.

---

## 7. Final Diff Summary

```text
 frontend/src/pages/ContratosPage.jsx          | 166 +-------------------------
 frontend/src/pages/MantenimientoAdminPage.jsx |   4 +-
 frontend/src/pages/OrgGastosPage.jsx          |   2 +-
 frontend/src/pages/PazYSalvoPage.jsx          |   2 +-
 render.yaml                                   |   2 +-
 5 files changed, 7 insertions(+), 169 deletions(-)
```

### Lista Exacta de Archivos Modificados:
1. `render.yaml`
2. `frontend/src/pages/PazYSalvoPage.jsx`
3. `frontend/src/pages/MantenimientoAdminPage.jsx`
4. `frontend/src/pages/ContratosPage.jsx`
5. `frontend/src/pages/OrgGastosPage.jsx`
