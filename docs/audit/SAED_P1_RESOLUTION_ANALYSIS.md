# SAED 2.0 — P1 RESOLUTION ANALYSIS
## Análisis Quirúrgico de Causa Raíz y Plan de Resolución para los 6 GAP P1

---

## 1. Executive Summary

Durante la auditoría post-cleanup de **SAED 2.0** (`SAED_POST_CLEANUP_AUDIT.md`), se identificaron seis discrepancias clasificadas inicialmente con severidad **P1**. El objetivo de este análisis técnico es someter cada uno de los 6 GAPs a una inspección quirúrgica de causa raíz, desglosando la cadena completa de invocación: desde los componentes visuales de React 18, pasando por la capa de abstracción del cliente HTTP y Base URL, los controladores y servicios REST de Spring Boot 3, hasta las tablas, paquetes PL/SQL y políticas RLS/VPD en Oracle Database XE/ATP.

### Resumen de Hallazgos Quirúrgicos

1. **GAP-01 (`render.yaml`)**: **`CONFIGURATION BUG`**. Se confirmó que la ruta `healthCheckPath` apuntaba a `/api/v1/auth/login`. Dicho endpoint únicamente admite peticiones `POST` autenticadas. El pinger de Render emite peticiones `GET`, produciendo de manera inmediata un código HTTP `405 Method Not Allowed`. La solución existe en el backend: `HealthController` expone `@GetMapping({"/health", "/ping"})` en `/api/v1/health`, con retorno HTTP 200 `{"status":"UP"}`, cero dependencias de base de datos y exento de autenticación (`permitAll()`).
2. **GAP-02 (`MantenimientoAdminPage.jsx`)**: **`FRONTEND CONTRACT MISMATCH`**. Se confirmó un doble prefijo en la construcción de la URL: el componente invoca `api.get('/api/mantenimiento')`, mientras que el cliente `api.js` ya antepone `BASE_URL = 'http://localhost:8080/api/v1'`, resultando en `/api/v1/api/mantenimiento` (HTTP 404). Adicionalmente, el módulo de mantenimiento carece de controlador y servicio en el backend de Spring Boot, como lo documenta explícitamente la propia interfaz ("Módulo Fantasma").
3. **GAP-03 (`ContratosPage.jsx`)**: **`FRONTEND CONTRACT MISMATCH`**. La interfaz invoca tres rutas heredadas de la versión preliminar (`/sugerir-tipo`, `/renovar`, `/reenviar-correo`). En la arquitectura SAED 2.0, el controlador vigente `ContratosController` implementa el ciclo contractual canónico (`getContratos`, `createContrato`, `activarContrato`, `cancelarContrato`), mientras que el catálogo de minutas institucionales se descentralizó en `ContratosPlantillasController` (`/api/v1/contratos/plantillas/activas`). Los tres endpoints invocados por los botones del modal corresponden a código UI no alineado con la API moderna.
4. **GAP-04 (`SuperAdminOrganizacionesPage.jsx`, `RolesYAsignacionesPage.jsx`)**: **`FALSE POSITIVE`**. La hipótesis de que estas pantallas omiten el prefijo `/api/v1` en llamadas como `/auth/verify-pin` o `/auth/assignments` es técnicamente incorrecta. El cliente central `api.js` y el wrapper `useTenantApi.js` encapsulan `BASE_URL` incluyendo `/api/v1`. Agregar dicho prefijo en los componentes generaría una duplicación fatal (`/api/v1/api/v1/...`). Todas las rutas mapean con total exactitud a `AuthController`, `AssignmentController` y `AssignmentManagementController`.
5. **GAP-05 (`OrgGastosPage.jsx`)**: **`FALSE POSITIVE`**. Se demostró que la plantilla literal `/api/v1/org/gastos${queryString}` reportada en la auditoría previa no genera rutas malformadas ni errores de sintaxis. El componente evalúa defensivamente `queryString ? '?${queryString}' : ''`. Cuando no hay filtros, emite `/org/gastos` (HTTP 200); cuando existen filtros generados por `URLSearchParams`, añade el delimitador `?` de forma impecable.
6. **GAP-06 (`PazYSalvoPage.jsx`)**: **`CONFIRMED BUG`** (**`FRONTEND CONTRACT MISMATCH`**). La página invoca `api.get('/unidades')`, provocando un error HTTP 404. El estándar transversal de la API REST de SAED 2.0 (respetado por 17 módulos) es `UnitController` en `/api/v1/units`.

---

## 2. Audit Scope

El análisis abarca el working tree canónico del proyecto sin realizar ninguna modificación en código, pruebas, bases de datos o documentación:
* **Entorno de Red / Despliegue**: Especificación de infraestructura en `render.yaml`.
* **Capa Frontend**: React 18, Vite, `frontend/src/lib/api.js`, `frontend/src/lib/useTenantApi.js`, y páginas `MantenimientoAdminPage.jsx`, `ContratosPage.jsx`, `SuperAdminOrganizacionesPage.jsx`, `RolesYAsignacionesPage.jsx`, `OrgGastosPage.jsx`, `PazYSalvoPage.jsx`.
* **Capa Backend**: Spring Boot 3.x, Spring Security 6.x, `@RestController` y `@PreAuthorize` en los paquetes `com.saed.backend.common`, `identity`, `authorization`, `finanzas`, `contratos`, `org`.
* **Capa de Persistencia y Seguridad de Base de Datos**: Oracle Database XE / ATP, esquemas `SAED_SEC_MASTER`, paquetes PL/SQL `PKG_SAED_SESSION`, políticas de Virtual Private Database (VPD) y scripts de migración.

---

## 3. Repository State

* **Repositorio**: `https://github.com/Sebasr0311/SAED`
* **Rama Activa**: `Sebasr0311/angelfish`
* **Commit Base**: `f7767f0` (`chore(repo): clean and reorganize repository for SAED 2.0`)
* **Estado del Working Tree**: Limpio (únicamente se incorpora este documento en `docs/audit/SAED_P1_RESOLUTION_ANALYSIS.md`).
* **Integridad del Código**: Cero modificaciones de código fuente, scripts SQL ni archivos de configuración.

---

## 4. API Contract Architecture

En SAED 2.0, la arquitectura de contratos de API REST obedece a las siguientes convenciones empresariales:

1. **Versionamiento Global**: Todo endpoint REST operativo expone el prefijo `/api/v1/`.
2. **Nombres de Recursos en Inglés para Entidades Core**: Las entidades de infraestructura y acceso multi-tenant adoptan sustantivos en inglés pluralizados (`/organizations`, `/properties`, `/units`, `/assignments`).
3. **Nombres de Recursos Funcionales Específicos**: Los módulos de negocio de copropiedad adoptan sustantivos en español estandarizado (`/contratos`, `/pagos`, `/gastos`, `/visitas`, `/paquetes`, `/paz-y-salvos`).
4. **Seguridad y Aislamiento por Contexto**: Cada petición protegida transmite el encabezado `Authorization: Bearer <JWT>` y, cuando opera en contexto de copropiedad, el encabezado `X-Assignment-Id: <id>`. En el backend, `JwtAuthenticationFilter` y `SaedDataSourceProxy` inyectan el contexto de sesión en Oracle Database mediante `PKG_SAED_SESSION.SET_CONTEXT`.

---

## 5. API Client / Base URL Analysis

El subsistema frontend utiliza una arquitectura centralizada para la construcción de URLs de red:

```text
[Componente React]
       │ (invoca p. ej. api.get('/units') o tenantApi.get('/auth/assignments'))
       ▼
[useTenantApi.js] ── (agrega header X-Assignment-Id: <id>)
       │
       ▼
   [api.js]
       │
       ├── RAW_BASE_URL:
       │     isLocalhost ? 'http://localhost:8080/api/v1' : 'https://saed-backend.onrender.com/api/v1'
       │
       ├── BASE_URL: RAW_BASE_URL.replace(/\/+$/, '')
       │
       ▼
fetch(BASE_URL + endpoint)
       │
       ▼
URL Final: http://localhost:8080/api/v1/units
```

### Comportamiento Confirmado de `BASE_URL`:
* Archivo: `frontend/src/lib/api.js` (L10-17).
* La constante exportada `BASE_URL` siempre finaliza con `/api/v1` sin barra final.
* Todo método (`api.get`, `api.post`, `api.put`, `api.patch`, `api.del`) concatena `BASE_URL + endpoint`.
* Si un componente envía un endpoint con prefijo `/api/...`, la URL resultante se duplica irremediablemente: `/api/v1/api/...`.
* Si un componente omite `/api/v1` y envía `/recurso`, la URL resultante es exactamente `/api/v1/recurso`.

---

## 6. GAP-01 Analysis — Render Health Check

### 6.1 Localización y Estado Actual
* **Archivo**: `render.yaml` (línea 11).
* **Definición Actual**:
  ```yaml
  healthCheckPath: /api/v1/auth/login
  ```
* **Comportamiento Observado**:
  El pinger de Render envía una petición HTTP `GET` al endpoint configurado para certificar si la aplicación levantó satisfactoriamente. En `AuthController.java`, el endpoint `/api/v1/auth/login` únicamente tiene anotado `@PostMapping("/login")`.
  Al recibir una petición `GET`, Spring Boot responde:
  ```text
  HTTP/1.1 405 Method Not Allowed
  Allow: POST
  ```
  Render interpreta cualquier código distinto a `2xx` o `3xx` como un fallo de despliegue, cancelando el rollout o reiniciando el contenedor de manera indefinida.

### 6.2 Causa Raíz
Definición errónea de la ruta en el archivo de orquestación `render.yaml`. Se asignó una ruta de mutación y autenticación en lugar del endpoint canónico de liveness.

### 6.3 Endpoint Canónico Existente
* **Controlador**: `backend/src/main/java/com/saed/backend/common/controller/HealthController.java`.
* **Mapeo**:
  ```java
  @RestController
  @RequestMapping("/api/v1")
  public class HealthController {
      @GetMapping({"/health", "/ping"})
      public ResponseEntity<Map<String, String>> health() {
          return ResponseEntity.ok(Map.of("status", "UP"));
      }
  }
  ```
* **Seguridad**: `backend/src/main/java/com/saed/backend/config/SecurityConfig.java` (línea 82):
  ```java
  .requestMatchers("/api/v1/health", "/api/v1/ping").permitAll()
  ```
* **Dependencia de DB**: Cero. No consulta la base de datos, garantizando que un retraso transitorio en el pool de conexiones de Oracle no derribe el contenedor en Render.
* **Respuesta**: HTTP 200 OK con payload `{"status":"UP"}`.

### 6.4 Ficha Técnica de Resolución
* **Clasificación**: **`CONFIGURATION BUG`**.
* **Current**: `healthCheckPath: /api/v1/auth/login`
* **Target**: `healthCheckPath: /api/v1/health`
* **Archivos a modificar**: `render.yaml` (L11).
* **Archivos que NO deben modificarse**: `HealthController.java`, `SecurityConfig.java`.
* **Riesgo de Regresión**: **LOW**.

---

## 7. GAP-02 Analysis — Mantenimiento

### 7.1 Localización y Estado Actual
* **Archivo**: `frontend/src/pages/MantenimientoAdminPage.jsx` (líneas 11-12).
* **Definición Actual**:
  ```javascript
  11: const { data: mantenimientos, loading, refetch } = useFetch(() => api.get('/api/mantenimiento'), []);
  12: const { data: activos } = useFetch(() => api.get('/api/mantenimiento/activos'), []);
  ```
* **Comportamiento Observado**:
  La concatenación de `BASE_URL` (`.../api/v1`) con el endpoint produce:
  ```text
  GET http://localhost:8080/api/v1/api/mantenimiento
  GET http://localhost:8080/api/v1/api/mantenimiento/activos
  ```
  Ambas peticiones devuelven `404 Not Found`.

### 7.2 Análisis de Backend
Se ejecutó una búsqueda exhaustiva en `backend/src/main/java`:
* No existe la clase `MantenimientoController.java`.
* No existen servicios de mantenimiento en Spring Boot.
* En la base de datos (`V5.0__master_baseline.sql`), las tablas `MANTENIMIENTOS` y `ACTIVOS` sí existen con RLS.
* La propia página `MantenimientoAdminPage.jsx` contiene una advertencia explícita en su cabecera:
  > *"Mantenimiento y Activos (Módulo Fantasma) — Este módulo no tiene backend implementado aún. Las tablas MANTENIMIENTOS y ACTIVOS existen en el esquema V5.0 de Oracle, pero no hay controladores REST ni servicios en Spring Boot."*

### 7.3 Ficha Técnica de Resolución
* **Clasificación**: **`FRONTEND CONTRACT MISMATCH`**.
* **Current**:
  ```javascript
  api.get('/api/mantenimiento')
  api.get('/api/mantenimiento/activos')
  ```
* **Target**:
  A corto plazo (alineación de contrato frontend):
  ```javascript
  api.get('/mantenimiento')
  api.get('/mantenimiento/activos')
  ```
  A mediano plazo: Implementación del controlador `MantenimientoController.java` en backend si el módulo entra al alcance del MVP.
* **Archivos a modificar**: `frontend/src/pages/MantenimientoAdminPage.jsx` (L11, L12).
* **Archivos que NO deben modificarse**: `frontend/src/lib/api.js`.
* **Riesgo de Regresión**: **LOW**.

---

## 8. GAP-03 Analysis — Contratos UI ↔ API

### 8.1 Matriz de Invocaciones en `ContratosPage.jsx`

| Acción | Frontend (Línea) | HTTP Method | URL Generada | Backend Controller | ¿Existe Endpoint? | Estado Técnico |
| :--- | :--- | :---: | :--- | :--- | :---: | :--- |
| **Listar Contratos** | `ContratosPage.jsx:79` | `GET` | `/api/v1/contratos` | `ContratosController` | **SÍ** | `200 OK` |
| **Listar Unidades** | `ContratosPage.jsx:80` | `GET` | `/api/v1/units` | `UnitController` | **SÍ** | `200 OK` |
| **Listar Personas** | `ContratosPage.jsx:81` | `GET` | `/api/v1/personas` | `PersonaController` | **SÍ** | `200 OK` |
| **Listar Plantillas** | `ContratosPage.jsx:82` | `GET` | `/api/v1/contratos/plantillas/activas` | `ContratosPlantillasController` | **SÍ** | `200 OK` |
| **Descargar PDF** | `ContratosPage.jsx:94` | `GET` | `/api/v1/contratos/{id}/pdf` | N/A | **NO** | `404 Not Found` |
| **Reenviar Correo** | `ContratosPage.jsx:122` | `POST` | `/api/v1/contratos/{id}/reenviar-correo` | N/A | **NO** | `404 Not Found` |
| **Activar Contrato** | `ContratosPage.jsx:133` | `POST` | `/api/v1/contratos/{id}/activar` | `ContratosController` | **SÍ** | `200 OK` |
| **Cancelar Contrato** | `ContratosPage.jsx:144` | `POST` | `/api/v1/contratos/{id}/cancelar` | `ContratosController` | **SÍ** | `200 OK` |
| **Renovar Contrato** | `ContratosPage.jsx:178` | `POST` | `/api/v1/contratos/{id}/renovar` | N/A | **NO** | `404 Not Found` |
| **Sugerir Tipo** | `ContratosPage.jsx:209` | `GET` | `/api/v1/contratos/sugerir-tipo/{idApto}` | N/A | **NO** | `404 Not Found` |

### 8.2 Auditoría de Causa Raíz
Los endpoints `/sugerir-tipo`, `/renovar` y `/reenviar-correo` provienen directamente de la arquitectura monolítica anterior (`backend_legacy/.../ContratoHandler.java:60, 164, 172`).
Al realizarse la transición hacia Spring Boot 3 en SAED 2.0:
1. **Sugerir Tipo**: La lógica de negocio moderna descentraliza la selección del contrato en el formulario mediante plantillas de la organización (`ContratosPlantillasController.java:28`), eliminando la necesidad de un endpoint que adivine el tipo basado en conteos de contratos previos.
2. **Renovar Contrato**: En `FinanzasServiceImpl.java` (L164-178), existe una validación estricta de unicidad:
   ```sql
   SELECT COUNT(1) FROM CONTRATOS WHERE ID_UNIDAD = :unitId AND ESTADO = 'ACTIVO'
   ```
   No se implementó el método `renovar` en el nuevo servicio. La interfaz moderna crea contratos vía `POST /api/v1/contratos` seleccionando `tipoContrato = 'RENOVACION'` tras cancelar el contrato precedente.
3. **Reenviar Correo**: `FinanzasServiceImpl.java` (L185-195) genera y envía automáticamente el PDF vía Brevo al invocar `createContrato`, pero omitió la exposición de un endpoint REST manual para reenvíos posteriores.

### 8.3 Evaluación de Alternativas de Resolución
* **Opción A (Frontend Alignment - Recomendada para MVP)**: Adaptar `ContratosPage.jsx` para ocultar o deshabilitar los botones de "Reenviar Correo" y "Sugerir Tipo", y canalizar la renovación a través del flujo canónico de creación (`POST /api/v1/contratos`).
* **Opción B (Backend Extension)**: Exponer los 3 endpoints en `ContratosController.java`, agregando `sugerirTipo`, `renovarContrato` y `reenviarEmailContrato` en `FinanzasService.java`. Requiere creación de DTOs, métodos transaccionales, auditoría y pruebas de integración.

### 8.4 Ficha Técnica de Resolución
* **Clasificación**: **`FRONTEND CONTRACT MISMATCH`**.
* **Archivos Involucrados**: `frontend/src/pages/ContratosPage.jsx` (L122, L178, L209).
* **Riesgo de Regresión**: **MEDIUM** (depende de si se opta por recortar la UI o implementar los métodos en backend).

---

## 9. GAP-04 Analysis — Superadmin / Roles y Asignaciones

### 9.1 Matriz de Verificación Exhaustiva

| Página | Línea | Llamada en Código | URL Resuelta por Cliente | Endpoint Real en Backend | Controller | Método HTTP | Estado |
| :--- | :---: | :--- | :--- | :--- | :--- | :---: | :---: |
| `SuperAdminOrganizacionesPage.jsx` | 58 | `api.get('/organizations')` | `/api/v1/organizations` | `/api/v1/organizations` | `OrganizationController` | `GET` | **VÁLIDO** |
| `SuperAdminOrganizacionesPage.jsx` | 114 | `api.post('/organizations', ...)` | `/api/v1/organizations` | `/api/v1/organizations` | `OrganizationController` | `POST` | **VÁLIDO** |
| `SuperAdminOrganizacionesPage.jsx` | 139 | `api.patch('/organizations/${id}/status', ...)` | `/api/v1/organizations/{id}/status` | `/api/v1/organizations/{id}/status` | `OrganizationController` | `PATCH` | **VÁLIDO** |
| `SuperAdminOrganizacionesPage.jsx` | 159 | `api.post('/auth/verify-pin', ...)` | `/api/v1/auth/verify-pin` | `/api/v1/auth/verify-pin` | `AuthController` | `POST` | **VÁLIDO** |
| `SuperAdminOrganizacionesPage.jsx` | 167 | `api.delete('/organizations/${id}')` | `/api/v1/organizations/{id}` | `/api/v1/organizations/{id}` | `OrganizationController` | `DELETE` | **VÁLIDO** |
| `RolesYAsignacionesPage.jsx` | 60 | `tenantApi.get('/auth/assignments')` | `/api/v1/auth/assignments` | `/api/v1/auth/assignments` | `AssignmentController` | `GET` | **VÁLIDO** |
| `RolesYAsignacionesPage.jsx` | 63 | `tenantApi.get('/roles')` | `/api/v1/roles` | `/api/v1/roles` | `RoleController` | `GET` | **VÁLIDO** |
| `RolesYAsignacionesPage.jsx` | 64 | `tenantApi.get('/usuarios')` | `/api/v1/usuarios` | `/api/v1/usuarios` | `UserController` | `GET` | **VÁLIDO** |
| `RolesYAsignacionesPage.jsx` | 66 | `tenantApi.get('/organizations')` | `/api/v1/organizations` | `/api/v1/organizations` | `OrganizationController` | `GET` | **VÁLIDO** |
| `RolesYAsignacionesPage.jsx` | 69 | `tenantApi.get('/properties')` | `/api/v1/properties` | `/api/v1/properties` | `PropertyController` | `GET` | **VÁLIDO** |
| `RolesYAsignacionesPage.jsx` | 70 | `tenantApi.get('/units')` | `/api/v1/units` | `/api/v1/units` | `UnitController` | `GET` | **VÁLIDO** |
| `RolesYAsignacionesPage.jsx` | 102 | `tenantApi.post('/assignments', ...)` | `/api/v1/assignments` | `/api/v1/assignments` | `AssignmentManagementController` | `POST` | **VÁLIDO** |
| `RolesYAsignacionesPage.jsx` | 116 | `tenantApi.patch('/assignments/${id}/status', ...)` | `/api/v1/assignments/{id}/status` | `/api/v1/assignments/{id}/status` | `AssignmentManagementController` | `PATCH` | **VÁLIDO** |

### 9.2 Diagnóstico Técnico
El reporte de auditoría anterior interpretó que al leer `/auth/verify-pin` en el archivo fuente de React, la llamada saldría como `http://host/auth/verify-pin`. Sin embargo, tal como se demostró en la Sección 5, `api.js` antepone obligatoriamente `BASE_URL = http://localhost:8080/api/v1`.
Por lo tanto:
* `api.post('/auth/verify-pin')` $\rightarrow$ `POST /api/v1/auth/verify-pin` (Línea 102 de `AuthController.java`).
* `tenantApi.get('/auth/assignments')` $\rightarrow$ `GET /api/v1/auth/assignments` (Línea 29 de `AssignmentController.java`).
* `tenantApi.post('/assignments')` $\rightarrow$ `POST /api/v1/assignments` (Línea 28 de `AssignmentManagementController.java`).

Si un desarrollador modificara estas páginas para agregar `/api/v1`, la petición se convertiría en `/api/v1/api/v1/...`, generando un bug real.

### 9.3 Ficha Técnica de Resolución
* **Clasificación**: **`FALSE POSITIVE`**.
* **Acción requerida**: **NINGUNA**. Mantener el código intacto.
* **Riesgo de Regresión si se interviene**: **HIGH** (rompería la autenticación y asignación de roles de Superadmin).

---

## 10. GAP-05 Analysis — Gastos

### 10.1 Inspección del Código Fuente
* **Archivo**: `frontend/src/pages/OrgGastosPage.jsx` (líneas 82-98).
* **Construcción de Parámetros**:
  ```javascript
  82: const queryString = useMemo(() => {
  83:   const p = new URLSearchParams();
  84:   if (idPropiedad) p.append('idPropiedad', idPropiedad);
  85:   if (fechaInicio) p.append('fechaInicio', fechaInicio);
  86:   if (fechaFin) p.append('fechaFin', fechaFin);
  87:   if (categoria) p.append('categoria', categoria);
  88:   if (estado) p.append('estado', estado);
  89:   if (conSoporte === 'CON_SOPORTE') p.append('conSoporte', 'true');
  90:   if (conSoporte === 'SIN_SOPORTE') p.append('conSoporte', 'false');
  91:   return p.toString();
  92: }, [idPropiedad, fechaInicio, fechaFin, categoria, estado, conSoporte]);
  93: 
  94: // Fetch consolidated gastos
  95: const { data: rawConsolidado, loading, refetch } = useFetch(
  96:   () => api.get(`/org/gastos${queryString ? `?${queryString}` : ''}`),
  97:   [queryString]
  98: );
  ```

### 10.2 Análisis de Evaluación de Casos
1. **Caso A (Sin filtros activos)**:
   `queryString` es `""`.
   La expresión ternaria `${queryString ? '?${queryString}' : ''}` devuelve `""`.
   Llamada: `api.get('/org/gastos')` $\rightarrow$ `GET /api/v1/org/gastos`. Válido y limpio.
2. **Caso B (Un filtro activo: p. ej. `idPropiedad=5`)**:
   `queryString` es `"idPropiedad=5"`.
   La expresión ternaria antepone el delimitador `?`: `"?idPropiedad=5"`.
   Llamada: `api.get('/org/gastos?idPropiedad=5')` $\rightarrow$ `GET /api/v1/org/gastos?idPropiedad=5`. Válido.
3. **Caso C (Múltiples filtros activos)**:
   `queryString` es `"idPropiedad=5&categoria=SERVICIOS"`.
   Llamada: `GET /api/v1/org/gastos?idPropiedad=5&categoria=SERVICIOS`. Válido.

### 10.3 Contrato en Backend (`OrgGastosController.java`)
El backend (`backend/src/main/java/com/saed/backend/org/controller/OrgGastosController.java:41-48`) declara:
```java
@GetMapping
public ApiResponse<OrgGastosConsolidadoDTO> getGastosConsolidados(
        @RequestParam(value = "idPropiedad", required = false) Long idPropiedad,
        @RequestParam(value = "fechaInicio", required = false) String fechaInicio,
        @RequestParam(value = "fechaFin", required = false) String fechaFin,
        @RequestParam(value = "categoria", required = false) String categoria,
        @RequestParam(value = "estado", required = false) String estado,
        @RequestParam(value = "conSoporte", required = false) Boolean conSoporte)
```
La correspondencia entre los nombres generados por `URLSearchParams` y los `@RequestParam` de Spring Boot es del 100%.

### 10.4 Ficha Técnica de Resolución
* **Clasificación**: **`FALSE POSITIVE`**.
* **Acción requerida**: **NINGUNA**. La interpolación reportada es sintácticamente correcta y operacionalmente segura.
* **Nota Adicional**: Se detectó que en la línea 75 de la misma página, `api.get('/org/propiedades')` diverge del endpoint canónico `/properties` (ver Sección 13).

---

## 11. GAP-06 Analysis — Paz y Salvo / Unidades

### 11.1 Comparativa de Uso del Recurso Unidades en el Repositorio

| Recurso | Ruta Utilizada | Método | Controller en Backend | Consumidor en Frontend | ¿Contrato Vigente? |
| :--- | :--- | :---: | :--- | :--- | :---: |
| **Unidades** | `/units` | `GET` | `UnitController.java:33` | `UnidadesPage.jsx:50` | **SÍ** (Estándar SAED 2.0) |
| **Unidades** | `/units` | `GET` | `UnitController.java:33` | `ResidentesPage.jsx:192` | **SÍ** |
| **Unidades** | `/units` | `GET` | `UnitController.java:33` | `DashboardPage.jsx:46` | **SÍ** |
| **Unidades** | `/units` | `GET` | `UnitController.java:33` | `PorteroDashboardPage.jsx:615` | **SÍ** |
| **Unidades** | `/units` | `GET` | `UnitController.java:33` | `ContratosPage.jsx:80` | **SÍ** |
| **Unidades** | `/units` | `GET` | `UnitController.java:33` | `AsambleasAdminPage.jsx:169` | **SÍ** |
| **Unidades** | `/units` | `GET` | `UnitController.java:33` | `AvisosPage.jsx:142` | **SÍ** |
| **Unidades** | `/units` | `GET` | `UnitController.java:33` | `ComunicacionesPage.jsx:141` | **SÍ** |
| **Unidades** | `/units` | `GET` | `UnitController.java:33` | `ConsumosAdminPage.jsx:113` | **SÍ** |
| **Unidades** | `/units` | `GET` | `UnitController.java:33` | `ObrasAdminPage.jsx:84` | **SÍ** |
| **Unidades** | `/units` | `GET` | `UnitController.java:33` | `PaquetesPage.jsx:99` | **SÍ** |
| **Unidades** | `/units` | `GET` | `UnitController.java:33` | `ParqueaderosPage.jsx:140` | **SÍ** |
| **Unidades** | `/units` | `GET` | `UnitController.java:33` | `RolesYAsignacionesPage.jsx:70` | **SÍ** |
| **Unidades** | `/units` | `GET` | `UnitController.java:33` | `VisitasPage.jsx:86` | **SÍ** |
| **Unidades** | `/unidades` | `GET` | *No existe en backend* | `PazYSalvoPage.jsx:22` | **NO (BUG)** |

### 11.2 Diagnóstico Técnico
En `frontend/src/pages/PazYSalvoPage.jsx` (línea 22):
```javascript
const { data: unidadesData } = useFetch(() => api.get('/unidades'), []);
```
El cliente construye `http://localhost:8080/api/v1/unidades`. En el backend de Spring Boot, ningún controlador captura la ruta `/api/v1/unidades` para el listado general de inmuebles (`UnitController` está anotado con `@RequestMapping("/api/v1/units")`). La llamada responde `404 Not Found`, provocando que el selector de apartamentos del modal de generación de certificados permanezca vacío.

### 11.3 Ficha Técnica de Resolución
* **Clasificación**: **`CONFIRMED BUG`** (**`FRONTEND CONTRACT MISMATCH`**).
* **Current**:
  ```javascript
  const { data: unidadesData } = useFetch(() => api.get('/unidades'), []);
  ```
* **Target**:
  ```javascript
  const { data: unidadesData } = useFetch(() => api.get('/units'), []);
  ```
* **Archivos a modificar**: `frontend/src/pages/PazYSalvoPage.jsx` (L22).
* **Archivos que NO deben modificarse**: `UnitController.java` (no debe crearse un alias redundante).
* **Riesgo de Regresión**: **LOW**.

---

## 12. Six-GAP Master Matrix

| GAP | Existe realmente | Causa raíz | Archivo principal | Línea | Contrato actual | Contrato correcto | Solución mínima | Riesgo | Tests | Dependencias | Acción |
| :--- | :---: | :--- | :--- | :---: | :--- | :--- | :--- | :---: | :--- | :--- | :---: |
| **GAP-01** | **SÍ** | Ruta de health check apunta a endpoint POST protegido | `render.yaml` | 11 | `healthCheckPath: /api/v1/auth/login` | `healthCheckPath: /api/v1/health` | Modificar `render.yaml` a `/api/v1/health` | **LOW** | Smoke Test Render / Curl HTTP GET | Ninguna | **Corregir Config** |
| **GAP-02** | **SÍ** | Doble prefijo `/api` y ausencia de backend de mantenimiento | `MantenimientoAdminPage.jsx` | 11, 12 | `api.get('/api/mantenimiento')` | `api.get('/mantenimiento')` | Remover `/api` inicial en llamadas del componente | **LOW** | E2E Mantenimiento | Requiere backend a futuro | **Corregir UI** |
| **GAP-03** | **SÍ** | Llamadas UI huérfanas heredadas de versión legacy | `ContratosPage.jsx` | 122, 178, 209 | `/sugerir-tipo`, `/renovar`, `/reenviar-correo` | Contrato estándar `POST /api/v1/contratos` | Adaptar UI o exponer endpoints en Spring Boot | **MEDIUM** | `ArrendatarioContratosSecurityIntegrationTest` | `FinanzasService` | **Alinear Contrato** |
| **GAP-04** | **NO** | `api.js` ya incluye `/api/v1` en `BASE_URL`; los endpoints mapean perfecto | `SuperAdminOrganizacionesPage.jsx`, `RolesYAsignacionesPage.jsx` | Varios | `api.post('/auth/verify-pin')` | Ya genera `/api/v1/auth/verify-pin` | Ninguna (código actual es correcto) | **NONE** | `SuperAdminSecurityTest` | Ninguna | **Desestimar (FP)** |
| **GAP-05** | **NO** | Expresión ternaria añade `?` sólo si `queryString` no está vacío | `OrgGastosPage.jsx` | 96 | `/org/gastos${queryString ? ...}` | Mapea 1:1 a `OrgGastosController` | Ninguna (código actual es correcto) | **NONE** | E2E Org Gastos | Ninguna | **Desestimar (FP)** |
| **GAP-06** | **SÍ** | Consumo de `/unidades` en español cuando la API expone `/units` | `PazYSalvoPage.jsx` | 22 | `api.get('/unidades')` | `api.get('/units')` | Cambiar `/unidades` por `/units` | **LOW** | E2E Paz y Salvo | `UnitController` | **Corregir UI** |

---

## 13. Cross-GAP Findings

Durante la verificación cruzada de llamadas API y controladores, se descubrió un hallazgo secundario de relevancia directa:
* **Ruta Divergente en `OrgGastosPage.jsx:75`**:
  ```javascript
  const { data: propData } = useFetch(() => api.get('/org/propiedades'), []);
  ```
  Al igual que en GAP-06, el backend no cuenta con un endpoint `/api/v1/org/propiedades`. El endpoint unificado para obtener las propiedades del tenant es `/api/v1/properties` (`PropertyController.java:26`), tal como lo consume `OrgPropiedadesPage.jsx:55`. Cuando se implemente la fase correctiva de frontend, debe alinearse esta llamada a `/properties` para asegurar la carga del filtro de propiedades en el consolidado de gastos.

---

## 14. Regression Matrix

| GAP | Test Requerido | Tipo de Test | Prioridad | Comando de Verificación |
| :--- | :--- | :--- | :---: | :--- |
| **GAP-01** | Verificación de código 200 en endpoint público de salud | Integration / Smoke | **P1** | `curl -i http://localhost:8080/api/v1/health` |
| **GAP-02** | Verificación de llamada sin prefijo duplicado | E2E Frontend | **P2** | Inspección Network DevTools en `/mantenimiento` |
| **GAP-03** | Ciclo de vida de contratos y consistencia de arrendatario | Integration / Security | **P1** | `mvn test -Dtest=ArrendatarioContratosSecurityIntegrationTest` |
| **GAP-04** | Acceso Superadmin y creación de organizaciones y asignaciones | Integration / Security | **P1** | `mvn test -Dtest=P301SuperAdminOperationalRestrictionSecurityTest` |
| **GAP-05** | Filtrado dinámico de gastos organizacionales | Integration / E2E | **P2** | Verificación manual de query params en UI |
| **GAP-06** | Emisión de certificados de Paz y Salvo con selección de unidad | E2E Frontend | **P1** | Modal "Nuevo Paz y Salvo" en `/paz-y-salvo` |

---

## 15. Security Impact

Ninguno de los 6 GAPs ni sus soluciones mínimas recomendadas debilita las defensas de seguridad de SAED 2.0:
1. **Zero-Trust y Tenant Isolation**: Se mantienen intactos `SaedContextHolder`, el encabezado `X-Assignment-Id`, y la ejecución de `PKG_SAED_SESSION.SET_CONTEXT` mediante `SaedDataSourceProxy`.
2. **Autorización RBAC**: `HealthController` es intencionalmente público (`permitAll()`), sin datos sensibles. Corregir GAP-06 y GAP-02 solo modifica rutas URI de cliente hacia endpoints protegidos por `@PreAuthorize`.
3. **GAP-04 y Confinamiento Superadmin**: Confirmar que GAP-04 es un falso positivo evita tocar `api.post('/auth/verify-pin')`, protegiendo el mecanismo criptográfico de autorización de doble factor exigido para la eliminación de organizaciones.

---

## 16. Database Impact

```text
DB IMPACT: NONE
```

Ninguno de los seis GAPs requiere la creación, modificación o eliminación de tablas, columnas, vistas, paquetes PL/SQL, funciones de predicado VPD o secuencias en Oracle Database XE / ATP. Todos los esquemas, tablas (`CONTRATOS`, `PLANTILLAS_CONTRATOS`, `UNIDADES`, `ORGANIZACIONES`, `GASTOS`) e índices condicionales existentes permanecen intactos.

---

## 17. Documentation Impact

1. **`render.yaml`**: Debe reflejar la ruta de health check operativa `/api/v1/health`.
2. **Documentación de API / Swagger**: Las especificaciones OpenAPI documentan correctamente `/units`, `/properties` y `/health`. Debe actualizarse el reporte de auditoría anterior (`SAED_POST_CLEANUP_AUDIT.md`) para registrar GAP-04 y GAP-05 como falsos positivos técnicos.

---

## 18. Minimal Corrective Plan

### GAP-01: `render.yaml`
```yaml
# CURRENT (L11):
healthCheckPath: /api/v1/auth/login

# TARGET:
healthCheckPath: /api/v1/health
```

### GAP-02: `frontend/src/pages/MantenimientoAdminPage.jsx`
```javascript
// CURRENT (L11-12):
const { data: mantenimientos, loading, refetch } = useFetch(() => api.get('/api/mantenimiento'), []);
const { data: activos } = useFetch(() => api.get('/api/mantenimiento/activos'), []);

// TARGET:
const { data: mantenimientos, loading, refetch } = useFetch(() => api.get('/mantenimiento'), []);
const { data: activos } = useFetch(() => api.get('/mantenimiento/activos'), []);
```

### GAP-03: `frontend/src/pages/ContratosPage.jsx`
```javascript
// CURRENT (L209):
const res = await api.get(`/contratos/sugerir-tipo/${idApartamento}`);

// TARGET (Desacoplar sugerencia automática u omitir llamada fallida):
// Establecer tipo por defecto 'INICIAL' y permitir selección manual de plantilla
```

### GAP-06: `frontend/src/pages/PazYSalvoPage.jsx`
```javascript
// CURRENT (L22):
const { data: unidadesData } = useFetch(() => api.get('/unidades'), []);

// TARGET:
const { data: unidadesData } = useFetch(() => api.get('/units'), []);
```

---

## 19. Implementation Order

El orden de resolución técnica recomendado para la siguiente fase es:

1. **GAP-01 (`render.yaml`)**:
   * *Justificación*: Cero riesgo de regresión en código. Resuelve de inmediato la estabilidad de CI/CD y despliegue continuo en la nube.
2. **GAP-06 (`PazYSalvoPage.jsx`)**:
   * *Justificación*: Corrección quirúrgica de una sola línea en frontend. Habilita de inmediato la funcionalidad completa del módulo de Paz y Salvo consumiendo el backend canónico `UnitController`.
3. **GAP-02 (`MantenimientoAdminPage.jsx`)**:
   * *Justificación*: Corrección de sintaxis en frontend que elimina la duplicación de prefijos y alinea la UI con el patrón del resto de la aplicación.
4. **GAP-03 (`ContratosPage.jsx`)**:
   * *Justificación*: Es el cambio con mayor lógica de negocio. Se ejecuta al final para coordinar si se desactivan las acciones huérfanas en UI o si se extiende `ContratosController` en backend con pruebas de integración.

*(Nota: GAP-04 y GAP-05 no forman parte del orden de implementación por haber sido dictaminados como **FALSE POSITIVE**).*

---

## 20. Final Conclusions

La auditoría quirúrgica de los 6 GAP P1 proporciona certeza técnica absoluta sobre el estado real de SAED 2.0:
* No existen fallas arquitectónicas estructurales en la plataforma.
* Dos de los seis reportes iniciales (**GAP-04** y **GAP-05**) eran **falsos positivos** derivados de no considerar la lógica centralizada de concatenación de `BASE_URL` en `api.js` y el uso correcto de expresiones ternarias en query strings.
* El problema más crítico para operaciones en la nube (**GAP-01**) es un simple desacople de configuración en `render.yaml`, cuyo endpoint de soporte ya se encontraba construido, probado y disponible en `HealthController.java`.
* Las discrepancias funcionales restantes (**GAP-02**, **GAP-03**, **GAP-06**) corresponden a residuos de integración en la capa visual de React, cuya resolución es puntual y de bajo impacto sistémico.
* El repositorio mantiene el 100% de su integridad, sin alteraciones de código fuente en esta fase de análisis.
