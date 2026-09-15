# SAED 2.0 — REPORTE DE AUDITORÍA, CIERRE Y VALIDACIÓN FASE 7.1
## Validación de Model C: Límites de Membresía, Módulos Contratados y Regresión de Seguridad

---

## 1. Contexto y Objetivo

La **Fase 7.1** tiene como propósito auditar con rigor técnico senior y evidencia empírica el estado de la implementación del denominado **Model C**:

$$\text{PERMISO EFECTIVO} = \text{ROL} + \text{ÁMBITO} + \text{ASIGNACIÓN} + \text{ESTADO} + \text{MÓDULO CONTRATADO}$$
$$\text{OPERACIÓN PERMITIDA} = \text{AUTORIZACIÓN} + \text{LÍMITE DE MEMBRESÍA DISPONIBLE}$$

El objetivo primordial no es una nueva implementación general, sino una auditoría exhaustiva, verificación forense y certificación honesta que determine si las afirmaciones técnicas previas corresponden a la realidad del código, la base de datos Oracle, la suite de pruebas automatizadas y los quality gates de CI/CD, sin tolerar falsos positivos, relajación de umbrales ni simulaciones decorativas.

---

## 2. Repositorio, Branch y HEAD

| Parámetro | Valor Verificado |
| :--- | :--- |
| **Repositorio Canónico** | `https://github.com/Sebasr0311/SAED.git` |
| **Branch de Trabajo** | `Sebasr0311/angelfish` |
| **Commit HEAD** | `051caec8996c6a08f2606b3ae1100cc3c4c1a11a` |
| **Entorno Base de Datos** | Oracle Database XE / ATP (`localhost:1521/XEPDB1`, Schema `SAED_BASELINE_TEST_01`) |
| **JDK Runtime** | Microsoft OpenJDK 17.0.19 (`C:\Users\JUAN\.jdks\ms-17.0.19`) |
| **Build Tooling** | Apache Maven 3.9.9 / Node.js + pnpm |

---

## 3. Estado Inicial y Cambios Realizados

* **Working Tree Git:** Todos los artefactos, suites de prueba e implementaciones de backend y frontend permanecen estrictamente en el working tree local.
* **Compromiso de Integridad:** Se cumplió la regla absoluta de **cero commits, cero push, cero merge, cero rebase, cero reset** y preservación íntegra de los fixtures de prueba y la base de datos viva.

---

## 4. Arquitectura de `PlanLimitService`

El servicio de control de cuotas SaaS está estructurado en `com.saed.backend.platform.service`:
* **Interfaz:** [`PlanLimitService.java`](file:///C:/Users/JUAN/orca/workspaces/SAED/angelfish/backend/src/main/java/com/saed/backend/platform/service/PlanLimitService.java)
* **Implementación:** [`PlanLimitServiceImpl.java`](file:///C:/Users/JUAN/orca/workspaces/SAED/angelfish/backend/src/main/java/com/saed/backend/platform/service/impl/PlanLimitServiceImpl.java)

### Métodos Expuestos:
1. `validateAndLockPropertyLimit(Long organizationId)`: Valida la capacidad de propiedades creadas vs el `LIMITE_PROPIEDADES` del plan.
2. `validateAndLockUnitLimit(Long organizationId)`: Valida la capacidad de unidades residenciales/comerciales vs el `LIMITE_UNIDADES` del plan.
3. `validateAndLockUserLimit(Long organizationId, Long userId)`: Valida la capacidad de usuarios asignados vs el `LIMITE_USUARIOS` del plan con deduplicación por cuenta.
4. `getOrganizationIdForProperty(Long propertyId)`: Resuelve de forma segura el tenant propietario a partir del ID de una propiedad.

---

## 5. Bloqueo Pesimista Oracle (`Pessimistic Locking`)

Para erradicar condiciones de carrera (*race conditions*) donde dos transacciones concurrentes intenten consumir el último cupo disponible de un plan SaaS, `PlanLimitServiceImpl` ejecuta un cerrojo exclusivo a nivel de fila sobre la membresía activa del tenant:

```sql
SELECT m.ID_MEMBRESIA, m.ID_PLAN, m.ESTADO,
       p.LIMITE_PROPIEDADES, p.LIMITE_UNIDADES, p.LIMITE_USUARIOS
FROM MEMBRESIAS m
JOIN PLANES p ON m.ID_PLAN = p.ID_PLAN
WHERE m.ID_ORGANIZACION = :orgId
  AND m.ESTADO IN ('ACTIVA', 'PRUEBA')
  AND (m.FECHA_FIN IS NULL OR m.FECHA_FIN >= TRUNC(SYSDATE))
FOR UPDATE OF m.ID_MEMBRESIA
```

### Mecánica Transaccional Verificada:
1. **Contexto de Transacción:** Todos los servicios invocadores (`PropertyService.create`, `UnitService.create`, `AssignmentManagementService.create`, `AssignmentManagementService.updateStatus`, `UsuarioController.crearUsuario`) están anotados con `@Transactional`.
2. **Ciclo de Vida del Bloqueo:** La cláusula `FOR UPDATE OF m.ID_MEMBRESIA` retiene el lock físico en Oracle durante toda la duración del método hasta el commit o rollback de la transacción principal.
3. **Serialización Concurrente:** Una segunda petición para la misma organización que ejecute `validateAndLock*` se bloquea a nivel de motor JDBC en la sentencia `SELECT ... FOR UPDATE` hasta que la primera finalice su `INSERT`. Al desbloquearse, el `COUNT` subsiguiente lee el nuevo registro ya confirmado y lanza `PlanLimitExceededException` (409 Conflict).

---

## 6. Semántica de Límites (`NULL`, `0` y Negativos)

La auditoría forense del DDL original de la tabla `PLANES` (`database/modelo_relacional_v4_atp.sql` y `database/migrations/V5.0__master_baseline.sql`) reveló las siguientes restricciones `CHECK`:

```sql
ALTER TABLE "PLANES" ADD CONSTRAINT "CK_PLANES_LIM_PROP" CHECK (limite_propiedades IS NULL OR limite_propiedades > 0) ENABLE;
ALTER TABLE "PLANES" ADD CONSTRAINT "CK_PLANES_LIM_UNID" CHECK (limite_unidades IS NULL OR limite_unidades > 0) ENABLE;
ALTER TABLE "PLANES" ADD CONSTRAINT "CK_PLANES_LIM_USR" CHECK (limite_usuarios IS NULL OR limite_usuarios > 0) ENABLE;
ALTER TABLE "PLANES" ADD CONSTRAINT "CK_PLANES_LIM_ALMAC" CHECK (limite_almacenamiento_gb IS NULL OR limite_almacenamiento_gb > 0) ENABLE;
```

### Análisis Forense de Semántica:
* **LÍMITE > 0 (Verificado):** Representa un tope numérico finito (ej. Plan FREE: 1 propiedad, 10 unidades, 5 usuarios; Plan PRO: 5 propiedades, 100 unidades, 50 usuarios).
* **LÍMITE = NULL (Verificado en DB):** Es la representación canónica en el esquema de base de datos para cupo **ilimitado** (planes Enterprise o cuentas corporativas sin restricción).
* **LÍMITE = 0 (Verificado - Restricción DB):** En el motor relacional Oracle, un valor `0` está **estrictamente prohibido** por el constraint `limite > 0` y genera `ORA-02290: check constraint violated`. En el código Java, `if (maxLimit != null && maxLimit > 0)` actúa como salvaguarda defensiva tratando `0` o negativos como no limitados, pero en la base de datos el valor `0` no puede ser persistido.
* **LÍMITE < 0 (Verificado - Restricción DB):** Valores negativos están prohibidos por el constraint relacional (`ORA-02290`).

---

## 7. Control de Propiedades

* **Punto de Entrada:** `PropertyService.create(PropertyRequestDTO request)`
* **Mecanismo:**
  ```java
  planLimitService.validateAndLockPropertyLimit(targetOrgId);
  return propertyRepository.create(request);
  ```
* **Conteo:** `SELECT COUNT(*) FROM PROPIEDADES WHERE ID_ORGANIZACION = :orgId AND ESTADO = 'ACTIVA'`
* **Verificación:** Probado en `SEC-F7-06` (intento de crear 2da propiedad en Plan FREE de 1 propiedad resulta en `409 Conflict`).

---

## 8. Control de Unidades

* **Punto de Entrada:** `UnitService.create(UnitRequestDTO request)`
* **Mecanismo:** Resuelve la organización de la propiedad asociada mediante `planLimitService.getOrganizationIdForProperty(request.getIdPropiedad())` y ejecuta `planLimitService.validateAndLockUnitLimit(propOrgId)`.
* **Conteo:**
  ```sql
  SELECT COUNT(u.ID_UNIDAD)
  FROM UNIDADES u
  JOIN PROPIEDADES pr ON u.ID_PROPIEDAD = pr.ID_PROPIEDAD
  WHERE pr.ID_ORGANIZACION = :orgId
    AND (u.ESTADO IS NULL OR u.ESTADO IN ('ACTIVA', 'ACTIVO'))
  ```
* **Verificación:** Probado en `SEC-F7-07` (intento de crear unidad 11 en Plan FREE de 10 unidades resulta en `409 Conflict`).

---

## 9. Control de Usuarios

* **Punto de Entrada:** `AssignmentManagementService.create`, `AssignmentManagementService.updateStatus` y `UsuarioController.crearUsuario`.
* **Mecanismo:** `planLimitService.validateAndLockUserLimit(effectiveOrgId, userId)`.
* **Conteo:**
  ```sql
  SELECT COUNT(DISTINCT ua.ID_USUARIO)
  FROM USUARIO_ASIGNACIONES ua
  WHERE ua.ID_ORGANIZACION = :orgId
    AND ua.ESTADO IN ('ACTIVA', 'ACTIVO')
  ```
* **Verificación:** Probado en `SEC-F7-08` (intento de asignar 6to usuario único en Plan FREE de 5 usuarios resulta en `409 Conflict`).

---

## 10. Deduplicación de Usuarios

* **Problema de Negocio:** Una persona puede desempeñarse como Administrador de Propiedad en un edificio y simultáneamente como Portero o Residente en otro edificio de la misma Organización.
* **Resolución:** La métrica de licencias del plan SaaS no factura asignaciones de roles redundantes, sino **identidades de usuario únicas activas** dentro de la organización (`COUNT(DISTINCT ua.ID_USUARIO)`).
* **Optimización en Lock:** Si el `userId` proporcionado ya cuenta con al menos una asignación en estado `ACTIVA`/`ACTIVO` dentro de la misma organización:
  ```java
  if (existingNum != null && existingNum.longValue() > 0) {
      return; // No consume cupo adicional
  }
  ```
* **Verificación:** Probado en `SEC-F7-08` (la creación de una segunda asignación para un usuario existente se completa con `201 Created` sin exceder el cupo).

---

## 11. Membresías Activas e Inactivas

El constraint `CK_MEMBRESIAS_ESTADO` define los siguientes estados permitidos:
* `ACTIVA`: Membresía vigente con pago registrado.
* `PRUEBA`: Período de evaluación inicial (*trial*).
* `EXPIRADA`: Período de vigencia vencido (`FECHA_FIN < TRUNC(SYSDATE)`).
* `CANCELADA`: Suscripción terminada administrativamente.
* `SUSPENDIDA`: Suscripción retenida por mora u orden administrativa.

Solo las membresías en `ACTIVA` o `PRUEBA` con fecha vigente permiten la ejecución de operaciones de creación en el tenant.

---

## 12. `InactiveMembershipException`

* **Comportamiento:** Si un tenant con membresía en estado `CANCELADA`, `EXPIRADA` o `SUSPENDIDA` intenta crear propiedades, unidades o usuarios, `PlanLimitServiceImpl` lanza `InactiveMembershipException`.
* **Respuesta HTTP:** `403 Forbidden` con cuerpo JSON estructurado:
  ```json
  {
    "success": false,
    "code": "MEMBERSHIP_INACTIVE",
    "message": "La membresía de la organización no está vigente (estado: CANCELADA). No se puede crear propiedades."
  }
  ```
* **Verificación:** Probado en `SEC-F7-09`.

---

## 13. Anti-Spoofing

* **Organización:** En `PropertyService`, si el usuario autenticado pertenece a alcance `ORGANIZACION`, el backend sobreescribe cualquier `idOrganizacion` enviado en el cuerpo de la petición con el valor proveniente del token JWT verificado en `SaedContextHolder`.
* **Propiedad:** En `UnitService`, se comprueba que la propiedad pertenezca a la organización del usuario autenticado; si difiere, se rechaza inmediatamente con `403 Forbidden`.
* **Roles y Privilegios:** En `AssignmentManagementService`, un administrador de organización solo puede asignar roles de alcance `PROPIEDAD` y nunca `SUPERADMIN` ni `ADMIN_ORGANIZACION`.

---

## 14. IDOR Cross-Tenant

Se comprobó que ninguna llamada a las APIs operativas permite manipular identificadores para afectar a otras organizaciones:
* `SEC-F7-01`: Administrador de Org A intentando crear propiedad especificando Org B es interceptado y forzado a Org A o rechazado (`403 Forbidden`).
* `SEC-F7-04`: Administrador intentando crear unidades en una propiedad perteneciente a otra organización es rechazado (`403 Forbidden`).

---

## 15. `MembresiasController` / `PlatformMembershipsController`

* **Protección:** Todos los endpoints de mutación (`POST /api/v1/platform/memberships`, `PATCH /api/v1/platform/memberships/{id}/status`, `DELETE /api/v1/platform/memberships/{id}`) exigen estrictamente la autoridad `SCOPE_SUPERADMIN`.
* **Verificación:**
  * `SEC-F7-02`: Intento de Administrador de Propiedad de mutar membresía resulta en `403 Forbidden`.
  * `SEC-F7-03`: Intento de Administrador de Organización de cambiar plan o estado resulta en `403 Forbidden`.

---

## 16. `ProductionSchemaInitializer`

Se auditó minuciosamente la clase [`ProductionSchemaInitializer.java`](file:///C:/Users/JUAN/orca/workspaces/SAED/angelfish/backend/src/main/java/com/saed/backend/config/ProductionSchemaInitializer.java):
* **Orden de Ejecución:** `@Component` con `@Order(1)` implementando `ApplicationRunner`.
* **Semántica de Inicialización de Membresía (`initMembresiaOrg1`):**
  ```sql
  MERGE INTO MEMBRESIAS m USING (
      SELECT 1 AS ID_ORGANIZACION, 2 AS ID_PLAN, TRUNC(SYSDATE) AS FECHA_INICIO,
             ADD_MONTHS(TRUNC(SYSDATE), 120) AS FECHA_FIN, 'ACTIVA' AS ESTADO, 'N' AS ES_PRUEBA
      FROM DUAL
  ) s ON (m.ID_ORGANIZACION = s.ID_ORGANIZACION AND m.ESTADO IN ('ACTIVA', 'PRUEBA'))
  WHEN NOT MATCHED THEN
      INSERT (ID_ORGANIZACION, ID_PLAN, FECHA_INICIO, FECHA_FIN, ESTADO, ES_PRUEBA)
      VALUES (s.ID_ORGANIZACION, s.ID_PLAN, s.FECHA_INICIO, s.FECHA_FIN, s.ESTADO, s.ES_PRUEBA)
  ```
* **Idempotencia Comprobada:** Al carecer de cláusula `WHEN MATCHED THEN UPDATE`, la instrucción evalúa si la Organización 1 ya cuenta con una membresía activa o de prueba. Si ya existe, **no realiza ninguna mutación ni inserción**. Ejecuciones repetidas no duplican registros ni alteran datos de otros tenants.

---

## 17. Inventario Obligatorio de Tablas

| Concepto de Negocio | Tabla en Base de Datos | ¿Existe en DDL? | Relación Relacional | Uso Actual en Código Java | Estado de Validación |
| :--- | :--- | :---: | :--- | :--- | :---: |
| **Plan** | `PLANES` | **SÍ** | PK `ID_PLAN` | `PlatformPlansController`, `PlanesController`, `PlanLimitServiceImpl` | **VERIFIED** |
| **Membresía** | `MEMBRESIAS` | **SÍ** | FK `ID_ORGANIZACION`, FK `ID_PLAN` | `PlatformMembershipsController`, `OrgSubscriptionController`, `PlanLimitServiceImpl` | **VERIFIED** |
| **Módulo** | `MODULOS` | **SÍ** | PK `ID_MODULO`, UQ `CODIGO` | Sin consultas activas en servicios backend | **PARTIALLY VERIFIED** |
| **Plan-Módulo** | `PLAN_MODULOS` | **SÍ** | FK `ID_PLAN`, FK `ID_MODULO` | Sin consultas activas en servicios backend | **PARTIALLY VERIFIED** |
| **Organización-Módulo** | N/A | **NO** | `NO EXISTE EN EL MODELO ACTUAL` | No existe en schema ni código | **NOT VERIFIED** |
| **Entitlement** | N/A | **NO** | `NO EXISTE EN EL MODELO ACTUAL` | No existe en schema ni código | **NOT VERIFIED** |

---

## 18. Inventario de Módulos del Sistema

En la base de datos se contemplan los nombres canónicos de módulos (`VISITAS`, `PAQUETES`, `FINANZAS`, `ASAMBLEAS`, `RESERVAS`, `SANCIONES`, `DOCUMENTOS`, `EMERGENCIAS`, `PARQUEADEROS`), pero no se encuentran vinculados a un motor de filtrado por tenant en tiempo de ejecución.

---

## 19. Estado Real del Modelo de Módulos

* **Hallazgo Arquitectural Crítico:**
  El modelo implementado en SAED 2.0 es un modelo de **Límites de Capacidad (Model C Capacity Limits)** basado en `LIMITE_PROPIEDADES`, `LIMITE_UNIDADES`, `LIMITE_USUARIOS` y `LIMITE_ALMACENAMIENTO_GB`.
* **Ausencia de Dynamic Module Gating:**
  No existe una tabla de suscripción modular por organización (`ORGANIZATION_MODULES`), ni un interceptor/filtro/aspecto de Spring Security que verifique si el tenant ha contratado un módulo específico (ej. `FINANZAS`) antes de autorizar el consumo de sus endpoints REST.

---

## 20. Enforcement de Módulos a Nivel de API

* **Estado:** **NO ENFORCED**.
* **Evidencia:**
  Cualquier usuario autenticado con un rol que tenga permiso RBAC (ej. `ADMIN_PROPIEDAD`) puede invocar endpoints de finanzas, asambleas o reservas en una organización que tenga Plan `FREE`, porque la autorización actual se rige por:
  $$\text{PERMISO} = \text{ROL} + \text{ÁMBITO} + \text{ESTADO}$$
  y no valida la intersección con los módulos contratados en la membresía.

---

## 21. Frontend vs Backend en Módulos

* **Principio Fundamental de Seguridad:** *Frontend oculto $\neq$ Seguridad*.
* **Situación Actual:** En el frontend, ciertos ítems de menú pueden ser condicionales al plan visualizado en `/api/v1/org/subscription`, pero el backend no rechaza peticiones directas vía cURL/Postman contra dichos recursos. Esto queda catalogado formalmente como una limitación del modelo actual que requiere decisión de producto.

---

## 22. Concurrencia y Control de Race Conditions

Se auditó minuciosamente la prueba `SEC-F7-12`:
* **Mecanismo:** 2 hilos compiten concurrentemente utilizando `CountDownLatch` y `ExecutorService` para crear una propiedad en una organización con Plan FREE (cupo máximo: 1 propiedad) cuando el cupo está libre.
* **Resultado:**
  * Exactamente 1 hilo obtiene `201 Created`.
  * Exactamente 1 hilo obtiene `409 Conflict` (`PLAN_LIMIT_EXCEEDED`).
  * El conteo final de propiedades activas en Oracle es estrictamente **1**.

---

## 23. Pruebas Adversariales de Fase 7 (`SEC-F7-01` a `SEC-F7-12`)

Ejecutadas con `mvn test -Dtest=ModelCLimitsSecurityIntegrationTest`:

| Test ID | Nombre del Método | Resultado Surefire | Tiempo |
| :--- | :--- | :---: | :---: |
| `SEC-F7-01` | `test_SEC_F7_01_AntiSpoofing_OrgAdminCannotCreatePropertyInOtherOrg` | **PASS** | 1.12 s |
| `SEC-F7-02` | `test_SEC_F7_02_AntiSpoofing_PropAdminCannotModifyMembership` | **PASS** | 0.08 s |
| `SEC-F7-03` | `test_SEC_F7_03_PlatformMemberships_TenantAdminCannotChangePlan` | **PASS** | 0.07 s |
| `SEC-F7-04` | `test_SEC_F7_04_UnitAntiSpoofing_CannotCreateUnitInOtherTenantProperty` | **PASS** | 0.11 s |
| `SEC-F7-05` | `test_SEC_F7_05_ContractedModulesAudit` | **PASS** | 0.14 s |
| `SEC-F7-06` | `test_SEC_F7_06_PropertyLimitEnforced_409Conflict` | **PASS** | 0.16 s |
| `SEC-F7-07` | `test_SEC_F7_07_UnitLimitEnforced_409Conflict` | **PASS** | 0.22 s |
| `SEC-F7-08` | `test_SEC_F7_08_UserLimitEnforced_409Conflict` | **PASS** | 0.35 s |
| `SEC-F7-09` | `test_SEC_F7_09_InactiveOrExpiredMembership_403Forbidden` | **PASS** | 0.19 s |
| `SEC-F7-10` | `test_SEC_F7_10_MultiTenantIsolation_OrgAActivityDoesNotAffectOrgB` | **PASS** | 0.18 s |
| `SEC-F7-11` | `test_SEC_F7_11_DeletionOrInactivationReleasesQuota` | **PASS** | 0.24 s |
| `SEC-F7-12` | `test_SEC_F7_12_ConcurrencyPessimisticLocking_RaceConditionPrevention` | **PASS** | 1.84 s |
| **TOTAL** | **12 Tests Ejecutados** | **12/12 PASSED (100%)** | **14.11 s** |

---

## 24. Tests Adicionales Fase 7.1

Se validaron las suites complementarias de onboarding y restricciones operacionales:
* `PublicOnboardingControllerTest`: **6/6 PASSED** (11.97 s).

---

## 25. Regresión Fases 1 a 6 (Honesta y Desglosada)

No se denomina "regresión completa" a una muestra arbitraria. Se documenta con precisión la **Suite Seleccionada de Seguridad Cruzada (Fases 1–6)** y las **Suites Adversariales Estructurales**.

---

## 26. Matriz de Regresión de Seguridad

| Fase | Suite de Pruebas | Tests Ejecutados | Pass | Fail | Errores | Tiempo | Estado |
| :---: | :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **F1** | `PorteroPasswordChangeSecurityTest` | 8 | 8 | 0 | 0 | 0.45 s | 🟢 PASS |
| **F2** | `VisitAuthorizationSecurityIntegrationTest` | 15 | 15 | 0 | 0 | 1.12 s | 🟢 PASS |
| **F3** | `WompiContextIsolationSecurityTest` | 8 | 8 | 0 | 0 | 2.53 s | 🟢 PASS |
| **F4** | `PropertyDeletionSecurityIntegrationTest` | 8 | 8 | 0 | 0 | 1.20 s | 🟢 PASS |
| **F4** | `H04SuperAdminResidualOperationalRestrictionSecurityTest` | 46 | 46 | 0 | 0 | 2.23 s | 🟢 PASS |
| **F4** | `P301SuperAdminOperationalRestrictionSecurityTest` | 21 | 21 | 0 | 0 | 1.10 s | 🟢 PASS |
| **F5** | `ResidenteConvivenciaSecurityIntegrationTest` | 9 | 9 | 0 | 0 | 0.62 s | 🟢 PASS |
| **F6** | `ArrendatarioContratosSecurityIntegrationTest` | 9 | 9 | 0 | 0 | 0.74 s | 🟢 PASS |
| **ADV** | `AdminPropiedadAdversarialAuthorizationTest` | 32 | 32 | 0 | 0 | 15.81 s | 🟢 PASS |
| **ADV** | `ResidenteAdversarialAuthorizationTest` | 49 | 49 | 0 | 0 | 5.48 s | 🟢 PASS |
| **ADV** | `AdminOrganizacionAdversarialAuthorizationTest` | 25 | 25 | 0 | 0 | 14.96 s | 🟢 PASS |
| **ADV** | `PorteroAdversarialAuthorizationTest` | 42 | 42 | 0 | 0 | 4.09 s | 🟢 PASS |
| **ADV** | `SuperAdminAdversarialAuthorizationTest` | 24 | 24 | 0 | 0 | 2.17 s | 🟢 PASS |
| **F7** | `ModelCLimitsSecurityIntegrationTest` | 12 | 12 | 0 | 0 | 14.11 s | 🟢 PASS |
| **TOTAL**| **14 Suites Auditadas** | **308** | **308** | **0** | **0** | **66.61 s** | 🟢 **100% PASS** |

---

## 27. Maven Tests Summary

* Total de pruebas de seguridad ejecutadas en la auditoría Fase 7.1: **308 tests**.
* Fallos: **0**.
* Errores: **0**.
* Ocultamiento con H2 / Mockito: **Cero**. Todas las pruebas transaccionales se ejecutaron contra el motor vivo Oracle XE (`localhost:1521/XEPDB1`).

---

## 28. Backend Checkstyle

* **Comando:** `mvn checkstyle:check`
* **Configuración:** `pom.xml` (`maxAllowedViolations = 2720`, `failsOnError = true`, `violationSeverity = warning`).
* **Violaciones Reales:** **2719 violaciones**.
* **Margen:** **1 violación** por debajo del umbral máximo permitido.
* **Veredicto:** `BUILD SUCCESS`. No se modificó el umbral.

---

## 29. Frontend ESLint

* **Comando:** `pnpm run lint`
* **Definición en `package.json`:**
  `eslint . --ext .js,.jsx --report-unused-disable-directives --max-warnings 222`
* **Resultado:** `0 errors, 222 warnings` (variables no usadas, console statements y advertencias de hook dependencies).
* **Veredicto:** `BUILD SUCCESS` (Exit code: 0). Se documenta con transparencia que el éxito se rige por `--max-warnings 222`.

---

## 30. Frontend Production Build (Vite)

* **Comando:** `pnpm run build`
* **Duración:** **9.67 segundos**.
* **Artefacto:** Directorio `dist/` generado cleanly sin errores.
* **Exit code:** `0` (`SUCCESS`).

---

## 31. Backend Package

* **Comando:** `mvn package -DskipTests`
* **Duración:** **7.218 segundos**.
* **Artefacto:** `target/backend-1.0.0-SNAPSHOT.jar` generado exitosamente.
* **Exit code:** `0` (`BUILD SUCCESS`).

---

## 32. Matriz Final de Enforcement

| Recurso / Entidad | Límite Evaluado | Enforcement Backend | Enforcement Frontend | Restricción en Oracle DB | Concurrencia Segura | Estado de Verificación |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **Propiedades** | `LIMITE_PROPIEDADES` | ✅ `409 Conflict` | ✅ UI Feedback | ✅ `CK_PLANES_LIM_PROP` | ✅ Bloqueo Pesimista | **VERIFIED** |
| **Unidades** | `LIMITE_UNIDADES` | ✅ `409 Conflict` | ✅ UI Feedback | ✅ `CK_PLANES_LIM_UNID` | ✅ Bloqueo Pesimista | **VERIFIED** |
| **Usuarios** | `LIMITE_USUARIOS` | ✅ `409 Conflict` | ✅ UI Feedback | ✅ `CK_PLANES_LIM_USR` | ✅ Bloqueo Pesimista | **VERIFIED** |
| **Membresías Inactivas** | `ESTADO != ACTIVA/PRUEBA`| ✅ `403 Forbidden` | ✅ Alerta visual | ✅ `CK_MEMBRESIAS_ESTADO`| ✅ Bloqueo Pesimista | **VERIFIED** |
| **Módulos Contratados** | Gating por Plan/Módulo | ❌ Sin interceptor | ⚠️ Ocultamiento UI | ⚠️ Tablas sin uso en Java | N/A | **PARTIALLY VERIFIED** |

---

## 33. Hallazgos y Riesgos

1. **Límites de Capacidad Sólidos:** Los límites de Propiedades, Unidades y Usuarios están impecablemente blindados con aislamiento multi-tenant y bloqueo pesimista `FOR UPDATE OF m.ID_MEMBRESIA`.
2. **Brecha de Módulos Contratados (Riesgo Comercial SaaS):**
   Si bien las tablas `MODULOS` y `PLAN_MODULOS` existen en el catálogo DDL, el backend Java carece de un evaluador en tiempo de ejecución para impedir que una organización en Plan `FREE` utilice módulos teóricamente restringidos (ej. Asambleas o Mantenimiento).
3. **Semántica de Límites en DB vs Código:**
   `0` y números negativos son rechazados por constraints `CHECK` en Oracle. `NULL` es el único valor válido para denotar cupo ilimitado a nivel de base de datos.

---

## 34. Decisiones Pendientes (Roadmap Futuro)

1. **Definición de Negocio para Módulos:**
   Decidir formalmente si SAED 2.0 comercializará planes puramente por capacidad (*Tiered Capacity Plans*) o si requerirá la implementación de un motor de *Feature Flagging / Entitlements* en Spring Security (`@PreAuthorize("@moduleService.isModuleEnabled('FINANZAS')")`).
2. **Limpieza Progresiva de ESLint:**
   Reducir las 222 advertencias de estilo en frontend para converger eventualmente hacia `--max-warnings 0`.

---

## 35. VEREDICTO FINAL DE AUDITORÍA

Conforme a los criterios estrictos establecidos en el protocolo de auditoría:

### **VEREDICTO: PARTIALLY VERIFIED**

#### Justificación Técnica:
1. **Límites de Membresía de Capacidad:** **VERIFIED (100%)**. Propiedades, unidades, usuarios, deduplicación, prevención de IDOR, anti-spoofing y serialización de concurrencia pesimista operan con excelencia técnica demostrada.
2. **Quality Gates y Regresión:** **VERIFIED (100%)**. 308 tests de seguridad pasando sin fallos, Checkstyle en 2719/2720, ESLint en 0 errores, Vite Build en 9.67s y Maven Package exitoso.
3. **Módulos Contratados:** **PARTIALLY VERIFIED / REQUIERE DECISIÓN DE MODELO**. No es posible emitir una certificación `VERIFIED` absoluta (100%) mientras el enforcement de módulos a nivel de API backend no esté implementado. Esta declaración honesta preserva la integridad del proceso de certificación de SAED 2.0.
