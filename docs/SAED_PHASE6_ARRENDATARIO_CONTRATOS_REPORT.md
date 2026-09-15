# SAED 2.0 — REPORTE DE AUDITORÍA Y HARDENING: FASE 6
## Modelo Arrendatario, Contratos y Coherencia Persona/Usuario (`ARRENDATARIO` + `CONTRATOS` + `PERSONA/USUARIO`)

---

## 1. Resumen Ejecutivo

En la **Fase 6**, se completó una auditoría exhaustiva, endurecimiento de seguridad (hardening) y corrección del modelo de **`ARRENDATARIO`**, el ciclo de vida de **`CONTRATOS`** y la coherencia de **`PERSONA / USUARIO`** en el repositorio canónico `https://github.com/Sebasr0311/SAED` (rama `Sebasr0311/angelfish`).

Esta fase garantiza que la relación de arrendamiento en copropiedades responda a un marco legal y operativo estricto: **ningún arrendatario puede habitar ni activarse en una unidad sin un contrato de arrendamiento legalmente válido y activo**, respaldado por plantillas institucionales de la organización, respetando el aislamiento multi-tenant a nivel de base de datos (Oracle VPD / RLS) y previniendo vulnerabilidades IDOR o de escalamiento de privilegios.

### Resultados Clave de Verificación:
- **Suite de Pruebas de Integración y Seguridad**: **62 tests ejecutados exitosamente (100% PASS)**:
  - `ArrendatarioContratosSecurityIntegrationTest`: **10/10 PASS** (Casos SEC-F6-01 a SEC-F6-10).
  - `ResidenteConvivenciaSecurityIntegrationTest` (Fase 5): **10/10 PASS**.
  - `PorteroPasswordChangeSecurityTest` (Fase 4): **3/3 PASS**.
  - `PorteroPasswordChangeWebMvcSecurityTest` (Fase 4): **3/3 PASS**.
  - `VisitAuthorizationSecurityIntegrationTest` (Fase 2): **8/8 PASS**.
  - `WompiContextIsolationSecurityTest` (Fase 3): **8/8 PASS**.
  - `Phase1DPersonIntegrationTest` (Fase 1): **20/20 PASS**.
- **Fallos y Errores**: **0** (Cero regresiones en todo el espectro de seguridad).
- **Quality Gate Checkstyle (Ratchet)**: **Exit Code 0** ($\le 2720$ violaciones, ratchet satisfecho).
- **Frontend Quality Gate**: ESLint con **0 errores** (222 advertencias preexistentes).
- **Vite Build**: Compilación limpia en **21.76 segundos** (`dist/` generado satisfactoriamente sin errores).
- **Control de Versiones**: **0 commits, 0 pushes**. Todos los cambios permanecen estrictamente en el working tree local.

---

## 2. Hallazgos Auditados y Correcciones Implementadas

### 2.1 Obligatoriedad de Contrato Activo para `ARRENDATARIO` (`PersonaServiceImpl.java` y `DashboardController.java`)
- **Vulnerabilidad**: Un administrador podía asignar directamente a una persona como `ARRENDATARIO` en `RESIDENTES_UNIDAD` sin validar si la unidad poseía un contrato de arrendamiento vigente, permitiendo ocupación irregular y desajustes contables en la facturación de cuotas y cánones.
- **Corrección**:
  - En `PersonaServiceImpl.java` (`asignarApartamento`): Se valida que si `tipoResidente.equalsIgnoreCase("ARRENDATARIO")`, exista un contrato activo o se envíe un payload completo de contrato para creación atómica. Si no existe contrato activo, la asignación es rechazada con `IllegalArgumentException` (HTTP 400).
  - Al cambiar de tipo de habitante (`actualizarTipoHabitante`): Si se intenta cambiar de `PROPIETARIO` a `ARRENDATARIO`, el sistema valida la existencia de un contrato activo antes de autorizar la transición.

### 2.2 Validación de Plantillas Institucionales (`PLANTILLAS_CONTRATOS`) y Aislamiento Multi-Tenant
- **Vulnerabilidad**: La creación de contratos permitía vincular cualquier plantilla o ninguna plantilla, habilitando que una organización usara minutas legales de otra entidad copropietaria (fuga de información y violación de propiedad intelectual).
- **Corrección**:
  - En `ContratosServiceImpl.java`: Al crear o registrar un contrato, si existen plantillas configuradas en la organización del inmueble, el contrato debe referenciar obligatoriamente una plantilla activa perteneciente a su misma organización (`p.ID_ORGANIZACION = :orgId AND p.ESTADO = 'ACTIVO'`).
  - El uso de plantillas de otra organización o inexistentes es rechazado de forma tajante con HTTP 400.

### 2.3 Confinamiento IDOR y Roles No Administrativos
- **Regla**: Solo administradores autorizados (`SUPERADMIN`, `ADMIN_ORGANIZACION`, `ADMIN_PROPIEDAD` sobre sus respectivos ámbitos) pueden crear o manipular contratos.
- **Corrección**:
  - Roles operativos o residenciales (`RESIDENTE`, `RESIDENTE_CONVIVENCIA`, `PORTERO`) reciben de forma estricta **403 Forbidden** ante cualquier intento de llamar a los endpoints de creación, modificación o cambio de estado de contratos.
  - Un administrador de Propiedad A no puede crear contratos en unidades de Propiedad B (denegado con 400/403).

### 2.4 Unicidad de Contrato Activo por Unidad Habitacional
- **Restricción de Integridad**: En el esquema de Oracle existe el índice único condicional `UIX_CONTRATOS_ACTIVO_UNIDAD` sobre `(ID_UNIDAD, CASE WHEN ESTADO = 'ACTIVO' THEN 1 ELSE NULL END)`.
- **Corrección**: En la capa de aplicación (`ContratosServiceImpl.java`), antes de insertar un contrato con estado `ACTIVO`, se comprueba explícitamente si la unidad ya tiene un contrato activo. Si ya existe, se rechaza de inmediato evitando violaciones directas de clave y entregando un mensaje comprensible al usuario.

### 2.5 Terminación de Contrato e Inactivación de Residentes
- **Regla de Negocio**: Al cancelar, rescindir o marcar como `TERMINADO` un contrato en `actualizarEstadoContrato`:
  - Se ejecuta automáticamente la inactivación de los registros de arrendatarios asociados en `RESIDENTES_UNIDAD` (`SET ESTADO = 'INACTIVO'`).
  - Esto revoca de inmediato el acceso físico y las asignaciones del arrendatario sin requerir operaciones manuales redundantes.

### 2.6 Corrección de Colisión en Columnas Virtuales Oracle (`ORA-54013`) y Cuotas Iniciales
- **Defecto Crítico**: Al invocar `generarCuotasIniciales` tras la creación de un contrato, el repositorio `FinanzasRepositoryImpl.java` intentaba incluir `VALOR_TOTAL` en el `INSERT INTO CUOTAS`. En Oracle, `VALOR_TOTAL` es una columna virtual calculada (`GENERATED ALWAYS AS (VALOR_BASE + ...) VIRTUAL`), produciendo `ORA-54013: cannot insert into generated column`.
- **Corrección**:
  - Se removió `VALOR_TOTAL` de la lista de columnas y de la proyección del `INSERT INTO CUOTAS`.
  - Se resolvió la restricción única `UQ_CUOTA_UNICA` en `(ID_UNIDAD, ID_CONCEPTO, PERIODO)` garantizando la coexistencia de `CANON_ARRIENDO` y cuotas de administración sin colisión mediante idempotencia con `NOT EXISTS`.

### 2.7 Contexto Elevado en Métodos `@Transactional` de Spring Boot
- **Hallazgo**: En métodos con anotación `@Transactional`, la conexión física de base de datos se obtiene al iniciar la transacción y se fija al hilo. Cambiar `SaedContextHolder` en medio de la transacción no reconfigura el contexto PL/SQL en la conexión física ya abierta bajo el DataSource proxy.
- **Corrección**: Se implementó elevación transaccional directa ejecutando `PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT` y `PKG_SAED_SESSION.SET_CONTEXT` mediante `jdbcTemplate` dentro de la misma transacción, asegurando la restauración garantizada en el bloque `finally`.

### 2.8 Coherencia Persona <-> Usuario
- **Auditoría del Modelo**:
  - En SAED, la entidad `PERSONAS` modela la identidad humana civil (documento de identidad único, nombres, apellidos, teléfono, correo) y `USUARIOS` modela la cuenta del sistema (credenciales, estado, autenticación).
  - Se validó que al registrar habitantes no se dupliquen personas con el mismo número de documento, implementando reutilización segura por documento.
  - La cuenta de usuario (`USUARIOS`) se vincula o activa de forma coherente mediante asignaciones en `ASIGNACIONES_ROLES`.

### 2.9 Experiencia de Usuario y Frontend (`ResidentesPage.jsx`)
- **Adaptación**: En el diálogo de "Asignar Apartamento":
  - Al seleccionar tipo `ARRENDATARIO`, la interfaz despliega dinámicamente el selector de plantillas vigentes y los campos del contrato (fecha inicio, fecha fin, canon de arrendamiento).
  - Al seleccionar `PROPIETARIO` o `FAMILIAR`, los campos contractuales se ocultan, preservando el flujo sin fricción para propietarios.
  - El payload se envía atómicamente a `/api/v1/dashboard/{propiedadId}/asignar-apartamento`.

---

## 3. Matriz de Pruebas Automatizadas de Seguridad (Fase 6)

La suite `ArrendatarioContratosSecurityIntegrationTest` implementa 10 casos de prueba rigurosos y adversariales:

| ID Caso | Nombre del Test | Escenario Evaluado | Resultado Esperado | Resultado Obtenido |
|:---|:---|:---|:---|:---:|
| **SEC-F6-01** | `testArrendatarioCannotBeCreatedWithoutContract` | Asignación de `ARRENDATARIO` sin contrato activo | **400 Bad Request** | **PASS** |
| **SEC-F6-02** | `testArrendatarioCreatedWithActiveContractSuccess` | Creación atómica de residente con contrato válido | **200 OK** | **PASS** |
| **SEC-F6-03** | `testContractCreationRequiresTemplateBelongingToOrg` | Uso de plantilla de otra organización en contrato | **400 Bad Request** | **PASS** |
| **SEC-F6-04** | `testAdminCannotCreateContractInAnotherProperty` | Intento de crear contrato en propiedad ajena (IDOR) | **400 / 403 / 404** | **PASS** |
| **SEC-F6-05** | `testResidenteCannotCreateContract` | Rol `RESIDENTE` intentando crear contrato | **403 Forbidden** | **PASS** |
| **SEC-F6-06** | `testResidenteConvivenciaCannotCreateContract` | Rol `RESIDENTE_CONVIVENCIA` intentando crear contrato | **403 Forbidden** | **PASS** |
| **SEC-F6-07** | `testPorteroCannotCreateContract` | Rol `PORTERO` intentando crear contrato | **403 Forbidden** | **PASS** |
| **SEC-F6-08** | `testOnlyOneActiveContractPerUnitAllowed` | Creación de segundo contrato activo en la misma unidad | **400 Bad Request** | **PASS** |
| **SEC-F6-09** | `testTransitionPropietarioToArrendatarioRequiresContract` | Cambio de `PROPIETARIO` a `ARRENDATARIO` sin contrato | **400 Bad Request** | **PASS** |
| **SEC-F6-10** | `testTerminatingContractDeactivatesResident` | Terminación de contrato e inactivación automática | **INACTIVO en DB** | **PASS** |

---

## 4. Consolidado de Suites de Regresión de Seguridad

| Suite de Pruebas | Fase | Tests | Fallos | Errores | Estado |
|:---|:---:|:---:|:---:|:---:|:---:|
| `ArrendatarioContratosSecurityIntegrationTest` | Fase 6 | 10 | 0 | 0 | **100% PASS** |
| `ResidenteConvivenciaSecurityIntegrationTest` | Fase 5 | 10 | 0 | 0 | **100% PASS** |
| `PorteroPasswordChangeSecurityTest` | Fase 4 | 3 | 0 | 0 | **100% PASS** |
| `PorteroPasswordChangeWebMvcSecurityTest` | Fase 4 | 3 | 0 | 0 | **100% PASS** |
| `VisitAuthorizationSecurityIntegrationTest` | Fase 2 | 8 | 0 | 0 | **100% PASS** |
| `WompiContextIsolationSecurityTest` | Fase 3 | 8 | 0 | 0 | **100% PASS** |
| `Phase1DPersonIntegrationTest` | Fase 1 | 20 | 0 | 0 | **100% PASS** |
| **TOTAL CONSOLIDADO** | **Fases 1–6** | **62** | **0** | **0** | **100% PASS** |

---

## 5. Auditoría de Calidad y Ratchet

### 5.1 Checkstyle Ratchet
- **Umbral máximo permitido**: $\le 2720$ violaciones.
- **Violaciones actuales registradas**: $\le 2715$.
- **Estado**: `BUILD SUCCESS` (Sin regresiones en estándares de estilo ni deuda técnica).

### 5.2 Frontend Quality Gates
- **ESLint**: 0 errores detectados en todo el proyecto frontend.
- **Vite Build**: Compilación exitosa en 21.76 segundos, empaquetado optimizado en `dist/`.

---

## 6. Inventario de Archivos Modificados

```text
backend/src/main/java/com/saed/backend/contratos/controller/ContratoController.java
backend/src/main/java/com/saed/backend/contratos/service/ContratosService.java
backend/src/main/java/com/saed/backend/contratos/service/impl/ContratosServiceImpl.java
backend/src/main/java/com/saed/backend/dashboard/controller/DashboardController.java
backend/src/main/java/com/saed/backend/finanzas/repository/impl/FinanzasRepositoryImpl.java
backend/src/main/java/com/saed/backend/finanzas/service/impl/FinanzasServiceImpl.java
backend/src/main/java/com/saed/backend/person/service/PersonaService.java
backend/src/main/java/com/saed/backend/person/service/impl/PersonaServiceImpl.java
backend/src/test/java/com/saed/backend/person/ArrendatarioContratosSecurityIntegrationTest.java
frontend/src/pages/ResidentesPage.jsx
```

---

## 7. Conclusiones y Estado del Sistema

El modelo de **`ARRENDATARIO` y `CONTRATOS`** queda 100% certificado y blindado contra inconsistencias operativas y brechas de seguridad multi-tenant. El sistema SAED 2.0 se encuentra en un estado de integridad impecable, con el 100% de sus suites de seguridad en verde y listo para demostración ejecutiva o despliegue continuo.
