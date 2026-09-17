# SAED 2.0 — ANÁLISIS TÉCNICO DIRIGIDO
# PLANES, MEMBRESÍAS, ENTITLEMENTS Y FACTURACIÓN SAAS

**Fecha de Ejecución:** 16 de Septiembre de 2026  
**Repositorio Oficial:** `https://github.com/Sebasr0311/SAED`  
**Rama:** `Sebasr0311/angelfish`  
**Commit Base:** `7370c48` (`feat(config): implement configurable property architecture`)  
**Tipo de Análisis:** Auditoría Estricta de Solo Lectura (Inspección Técnica de Código, DDL y Flujos)

---

## 1. RESUMEN EJECUTIVO

El presente análisis evalúa de forma exhaustiva el subsistema comercial, de suscripciones, control de cuotas (*limits*) y licenciamiento funcional (*entitlements*) de **SAED 2.0**.

### Conclusiones Principales:
1. **Límites de Infraestructura Operativa (Propiedades, Unidades, Usuarios):**
   * **Implementados y Robustos en Backend:** El servicio `PlanLimitServiceImpl` implementa un control transaccional estricto con bloqueo pesimista en Oracle (`FOR UPDATE OF m.ID_MEMBRESIA`) sobre la fila de membresía activa. Verifica vigencia temporal (`FECHA_FIN >= TRUNC(SYSDATE)`) y estado (`ACTIVA`, `PRUEBA`). El intento de exceder los topes estipulados en `PLANES` (`LIMITE_PROPIEDADES`, `LIMITE_UNIDADES`, `LIMITE_USUARIOS`) es bloqueado de raíz en `PropertyService.create`, `UnitService.create`, `AssignmentManagementService.createAssignment` y `UsuarioController.crearUsuario`.
2. **Entitlements de Módulos Funcionales (Módulo gating):**
   * **Inexistente en Ejecución:** Aunque las tablas `MODULOS` y `PLAN_MODULOS` existen en el esquema relacional (`V5.0__master_baseline.sql`), **no contienen datos semilla** y **ninguna clase Java del backend las consulta**. La navegación o consumo de APIs de módulos avanzados (ej. Asambleas, Obras, Pólizas, Reservas) no valida si el plan contratado incluye o no el módulo; el acceso está regulado exclusivamente por el rol de seguridad (`SCOPE_*`), permitiendo que un plan gratuito o básico acceda a funciones empresariales si el usuario posee el rol respectivo.
3. **Onboarding Comercial e Integración con Wompi:**
   * **Excelente en Registro Inicial:** El flujo de alta para nuevas organizaciones (`PublicOnboardingController`, `OnboardingServiceImpl`, `WompiServiceImpl`) utiliza un patrón de staging desacoplado (`ONBOARDING_INTENCIONES` y `TRANSACCIONES_PAGO`). El precio **nunca se toma del frontend**, sino de la tabla `PLANES`. La firma de integridad de Wompi se calcula con secreto del servidor y el webhook valida firma criptográfica HMAC SHA-256, verificando monto en centavos y moneda `COP`, con control de idempotencia y materialización atómica post-pago.
   * **Ausente en Autoservicio de Clientes Existentes:** No existe flujo ni endpoint para que una organización existente renueve o actualice su plan con Wompi. `WompiServiceImpl.crearIntencion` solo acepta tipo `"CUOTA"` o `"MULTA"`.
4. **Brecha Crítica de Autorización y Gobernanza de Planes:**
   * Existe un controlador legado/duplicado (`PlanesController.java`, ruta `/api/v1/planes`) anotado con `@PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')")` que expone métodos `POST`, `PUT` y `PATCH /estado`. **Cualquier administrador de propiedad puede modificar, alterar precios, límites o dar de baja los planes comerciales globales de la plataforma SaaS.**
   * Adicionalmente, `MembresiasController.java` (`POST /api/v1/membresias/cambiar-plan`) permite a un administrador de organización cambiar su plan directamente en base de datos sin mediar pasarela de pago, además de fallar al intentar registrar en `MEMBRESIAS_HISTORIAL` por incompatibilidad de columnas SQL.

---

## 2. INVENTARIO REAL DE COMPONENTES

### 2.1 Tablas y Objetos de Base de Datos
* **`PLANES`** (`database/migrations/V5.0__master_baseline.sql:27-52`):
  * Catálogo maestro de planes SaaS. Columnas: `ID_PLAN`, `CODIGO`, `NOMBRE`, `DESCRIPCION`, `PRECIO_MENSUAL`, `PRECIO_ANUAL`, `LIMITE_PROPIEDADES`, `LIMITE_UNIDADES`, `LIMITE_USUARIOS`, `LIMITE_ALMACENAMIENTO_GB`, `CONFIG_JSON`, `ESTADO`, `FECHA_CREACION`.
  * Restricciones Check: `CK_PLANES_ESTADO` (`ACTIVO`, `INACTIVO`), `CK_PLANES_PRECIO` (`>= 0`), límites (`> 0`), `CK_PLANES_CONFIG_JSON` (`IS JSON`).
* **`MEMBRESIAS`** (`V5.0__master_baseline.sql:54-71`):
  * Suscripción por organización. Columnas: `ID_MEMBRESIA`, `ID_ORGANIZACION`, `ID_PLAN`, `FECHA_INICIO`, `FECHA_FIN`, `ESTADO`, `TIPO_FACTURACION`, `PRECIO_ACORDADO`, `FECHA_CREACION`.
  * Restricciones Check: `CK_MEMBRESIAS_ESTADO` (`ACTIVA`, `EXPIRADA`, `CANCELADA`, `PRUEBA`, `SUSPENDIDA`), `CK_MEMBRESIAS_FACTURACION` (`MENSUAL`, `ANUAL`, `PERSONALIZADO`), `CK_MEMBRESIAS_FECHAS`.
  * Restricción de Unicidad Activa: Índice/Check `UIX_MEMBRESIAS_VIGENTE` sobre `(ID_ORGANIZACION, CASE WHEN ESTADO IN ('ACTIVA', 'PRUEBA') THEN ESTADO ELSE NULL END)` que garantiza matemáticamente que una organización **solo puede tener a lo sumo una membresía vigente** a la vez.
* **`MEMBRESIAS_HISTORIAL`** (`V5.0__master_baseline.sql:73-88`):
  * Bitácora de cambios de membresía. Columnas: `ID_HISTORIAL`, `ID_MEMBRESIA`, `ID_PLAN_ANTERIOR`, `ID_PLAN_NUEVO`, `TIPO_CAMBIO`, `OBSERVACIONES`, `FECHA_CAMBIO`, `REALIZADO_POR` (FK a `USUARIOS.ID_USUARIO`).
  * Trigger de inmutabilidad: `TRG_MEMBHIST_IMMUTABLE` (prohíbe `UPDATE` y `DELETE`).
  * Restricción Check: `CK_MEMBHIST_TIPO` (`INICIO`, `UPGRADE`, `DOWNGRADE`, `RENOVACION`, `CANCELACION`, `SUSPENSION`, `REACTIVACION`).
* **`MODULOS` & `PLAN_MODULOS`** (`V5.0__master_baseline.sql:97-120`):
  * `MODULOS`: `ID_MODULO`, `CODIGO`, `NOMBRE`, `DESCRIPCION`, `ESTADO`.
  * `PLAN_MODULOS`: `ID_PLAN`, `ID_MODULO`, `ESTADO`.
  * Estado: Tablas creadas en DDL pero **sin semillas y sin referencias en el backend**.
* **`ONBOARDING_INTENCIONES`** (`V5.0__master_baseline.sql:1286-1309`):
  * Staging temporal de registro previo a confirmación de pago de Wompi.

### 2.2 Datos Semilla (Seeds)
* **`database/migrations/V4.13__seed_planes.sql`**:
  * `GRATUITO` (0 COP/mes, 1 prop, 20 unid, 3 usr, 1 GB).
  * `BASICO` (99.000 COP/mes, 990.000 COP/año, 1 prop, 50 unid, 5 usr, 5 GB).
  * `PRO` (299.000 COP/mes, 2.990.000 COP/año, 3 prop, 200 unid, 15 usr, 20 GB).
  * `ENTERPRISE` (799.000 COP/mes, 7.990.000 COP/año, 10 prop, 1000 unid, 50 usr, 100 GB).

### 2.3 Controladores y Servicios Backend
* **`PlatformPlansController.java`** (`/api/v1/platform/plans`):
  * Autorización: `@PreAuthorize("hasAuthority('SCOPE_SUPERADMIN')")`.
  * Métodos: `GET /` (listar todos), `GET /{id}`, `POST /` (crear), `PUT /{id}` (actualizar), `PATCH /{id}/toggle-status`.
* **`PlanesController.java`** (`/api/v1/planes`):
  * Autorización: `@PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')")` (vulnerable).
  * Métodos: `GET /`, `GET /{id}`, `POST /`, `PUT /{id}`, `PATCH /{id}/estado`.
* **`PlatformMembershipsController.java`** (`/api/v1/platform/memberships`):
  * Autorización: `@PreAuthorize("hasAuthority('SCOPE_SUPERADMIN')")`.
  * Métodos: `GET /` (todas las membresías), `GET /organizations/{id}`, `POST /assign`, `PATCH /{id}/toggle-status`.
* **`OrgSubscriptionController.java`** (`/api/v1/org/subscription`):
  * Autorización: `@PreAuthorize("hasAuthority('SCOPE_ADMIN_ORGANIZACION')")`.
  * Métodos: `GET /` (obtiene `SubscriptionOverviewDTO` con cuotas y consumos actuales).
* **`MembresiasController.java`** (`/api/v1/membresias`):
  * Autorización: `@PreAuthorize("hasAuthority('SCOPE_ADMIN_ORGANIZACION')")`.
  * Métodos: `GET /activa`, `POST /cambiar-plan` (controlador anómalo).
* **`PlanLimitServiceImpl.java`**:
  * Implementa `PlanLimitService`. Métodos: `validateAndLockPropertyLimit(orgId)`, `validateAndLockUnitLimit(orgId)`, `validateAndLockUserLimit(orgId)`.

### 2.4 Frontend
* `frontend/src/pages/SuperAdminPlanesPage.jsx`: Gestión de catálogo por SuperAdmin (`/api/v1/platform/plans`).
* `frontend/src/pages/SuperAdminMembresiasPage.jsx`: Asignación y suspensión de membresías (`/api/v1/platform/memberships`).
* `frontend/src/pages/OrgPlanPage.jsx`: Vista de suscripción para Administrador de Organización (`/api/v1/org/subscription`).
* `frontend/src/pages/RegistroOrganizacionPage.jsx`: Registro público con selección de plan y checkout de Wompi.
* `frontend/src/components/landing/LandingPricing.jsx`: Tabla de precios pública en Landing Page.
* `frontend/src/pages/PlanesPage.jsx` y `frontend/src/pages/MembresiasPage.jsx`: Páginas huérfanas desvinculadas de la navegación de `App.jsx`.

---

## 3. ARQUITECTURA ACTUAL

```
                     ┌──────────────────────────────┐
                     │          SUPERADMIN          │
                     └──────────────┬───────────────┘
                                    │ Administra catálogo
                                    ▼
                          ┌──────────────────┐
                          │      PLANES      │
                          └─────────┬────────┘
                                    │
                                    │ Asigna / Contrata
                                    ▼
┌──────────────────┐      ┌──────────────────┐
│  ORGANIZACIONES  │◄────►│    MEMBRESIAS    │ (Máx. 1 ACTIVA o PRUEBA)
└────────┬─────────┘      └─────────┬────────┘
         │                          │
         │                          │ Historial (Append-only)
         │                          ▼
         │                ┌──────────────────┐
         │                │MEMBRESIAS_HISTOR.│
         │                └──────────────────┘
         │
         ├───────────────────────────────────────────────────────┐
         │                                                       │
         ▼ (Hasta LIMITE_PROPIEDADES)                            ▼ (Hasta LIMITE_USUARIOS)
┌──────────────────┐                                    ┌──────────────────┐
│   PROPIEDADES    │                                    │     USUARIOS     │
└────────┬─────────┘                                    └──────────────────┘
         │
         ▼ (Hasta LIMITE_UNIDADES)
┌──────────────────┐
│     UNIDADES     │
└──────────────────┘
```

### 3.1 Relaciones y Cardinalidades
* **Organización → Membresía:** `1 : N` histórico, pero exactamente `1 : 1` en estado vigente (`ACTIVA` o `PRUEBA`) forzado por el índice único condicional de Oracle `UIX_MEMBRESIAS_VIGENTE`.
* **Membresía → Plan:** `N : 1`. Una membresía fija el plan contratado, la periodicidad de facturación (`MENSUAL` o `ANUAL`) y el `PRECIO_ACORDADO`.
* **Membresía → Historial:** `1 : N`. Auditoría de transiciones de planes y estados.

---

## 4. MATRIZ DE CAPACIDADES SAAS

| Capacidad | Implementado | Parcial | Faltante | Enforcement Backend | Evidencia en Código |
| :--- | :---: | :---: | :---: | :--- | :--- |
| **Catálogo de Planes** | X | | | Sí (SuperAdmin) / Brecha (AdminProp) | `PlatformPlansController.java:31-89`, `PlanesController.java:27-77` |
| **Membresías por Organización** | X | | | Sí (Regla 1 vigente por Org) | `V5.0__master_baseline.sql:68`, `PlatformMembershipsController.java` |
| **Límite de Propiedades** | X | | | **Pessimistic Lock (Row Lock)** | `PlanLimitServiceImpl.java:39-65`, `PropertyService.java:65` |
| **Límite de Unidades** | X | | | **Pessimistic Lock (Row Lock)** | `PlanLimitServiceImpl.java:67-93`, `UnitService.java:82` |
| **Límite de Usuarios** | X | | | **Pessimistic Lock (Row Lock)** | `PlanLimitServiceImpl.java:95-121`, `AssignmentManagementService.java:70` |
| **Límite de Almacenamiento (GB)**| | | X | Inexistente (Solo valida 10MB/archivo) | `FileStorageServiceImpl.java:43` (no suma cuota total) |
| **Entitlements de Módulos** | | | X | Inexistente (Módulos sin filtro de plan) | `V5.0__master_baseline.sql:97-120` (tablas vacías, sin Java code) |
| **Vencimiento de Membresía** | X | | | Sí (`FECHA_FIN >= TRUNC(SYSDATE)`) | `PlanLimitServiceImpl.java:169`, `InactiveMembershipException` |
| **Suspensión de Membresía** | X | | | Sí (Bloquea todo consumo operativo) | `PlatformMembershipsController.java:95-107`, `PlanLimitServiceImpl.java` |
| **Historial de Membresía** | | X | | Roto / Incompleto | `TRG_MEMBHIST_IMMUTABLE` existe, pero `MembresiasServiceImpl` falla |
| **Checkout Wompi Onboarding**| X | | | Sí (Firma, Centavos, Precios desde DB)| `PublicOnboardingController.java`, `WompiServiceImpl.java:80-145` |
| **Webhook e Idempotencia** | X | | | Sí (HMAC SHA-256, Replay-Safe) | `WompiServiceImpl.java:220-310`, `WompiWebhookController.java` |
| **Activación Post-Pago** | X | | | Sí (Materializa Org, User, Membresía)| `WompiServiceImpl.java:270-340`, `OnboardingServiceImpl.java` |
| **Upgrade / Renovación Wompi**| | | X | Inexistente para Orgs existentes | `WompiServiceImpl.java:150` (rechaza tipos distintos a CUOTA/MULTA) |
| **SuperAdmin Panel** | X | | | Sí (Planes y Membresías funcionales) | `SuperAdminPlanesPage.jsx`, `SuperAdminMembresiasPage.jsx` |

---

## 5. ENFORCEMENT BACKEND DETALLADO

### 5.1 Mecanismo de Bloqueo Concurrente y Reglas de Negocio
El control de consumos está centralizado en `com.saed.backend.platform.service.impl.PlanLimitServiceImpl`.
Para cada operación sensible (`CREAR_PROPIEDAD`, `CREAR_UNIDAD`, `CREAR_USUARIO`), ejecuta:
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

* **Vigencia y Estados:** Si el query no retorna registros, consulta si existe una membresía no vigente y lanza `InactiveMembershipException("La membresía de la organización no está vigente (estado: %s)")`.
* **Evaluación de Topes:**
  * **Propiedades:** `SELECT COUNT(*) FROM PROPIEDADES WHERE ID_ORGANIZACION = :orgId AND ESTADO = 'ACTIVO'`. Si `actualCount >= maxLimit`, lanza `PlanLimitExceededException`.
  * **Unidades:** `SELECT COUNT(*) FROM UNIDADES u JOIN PROPIEDADES p ON u.ID_PROPIEDAD = p.ID_PROPIEDAD WHERE p.ID_ORGANIZACION = :orgId AND u.ESTADO = 'ACTIVA'`.
  * **Usuarios:** `SELECT COUNT(DISTINCT ua.ID_USUARIO) FROM USUARIO_ASIGNACIONES ua JOIN PROPIEDADES p ON ua.ID_PROPIEDAD = p.ID_PROPIEDAD WHERE p.ID_ORGANIZACION = :orgId AND ua.ESTADO = 'ACTIVO'`.
* **Puntos de Invocación:**
  * `PropertyService.create()` invoca `planLimitService.validateAndLockPropertyLimit(organizationId)`.
  * `UnitService.create()` invoca `planLimitService.validateAndLockUnitLimit(orgId)`.
  * `AssignmentManagementService.createAssignment()` invoca `planLimitService.validateAndLockUserLimit(orgId)` si el usuario a asignar es nuevo en la organización.
  * `UsuarioController.crearUsuario()` también verifica `validateAndLockUserLimit(orgId)`.

### 5.2 Lo que NO se valida en Backend
1. **Almacenamiento Total:** Aunque `PLANES` define `LIMITE_ALMACENAMIENTO_GB`, la clase `FileStorageServiceImpl` únicamente valida que ningún archivo supere individualmente 10 MB (`MAX_FILE_SIZE = 10 * 1024 * 1024L`). No existe cómputo de bytes consumidos por organización ni rechazo de subida por exceder la cuota global de GB.
2. **Entitlements de Módulos:** No existe un interceptor, filter, aspect o método en `SecurityService` que verifique si el `ID_ORGANIZACION` actual tiene contratado el módulo `ASAMBLEAS`, `RESERVAS`, `POLIZAS` o `OBRAS`. Cualquier usuario con el rol respectivo puede interactuar con las APIs de estos módulos independientemente de estar en plan Gratuito, Básico o Enterprise.

---

## 6. AISLAMIENTO MULTI-TENANT, BYPASS E IDOR

1. **Contexto de Organización:**
   * En los flujos operativos (`OrgSubscriptionController.java:29`), el `organizationId` se extrae directamente de `SaedContextHolder.getContext().getOrganizationId()`, derivado criptográficamente del JWT firmado. No proviene de parámetros manipulables por URL (`@PathVariable` o `@RequestParam`), garantizando protección completa contra IDOR en la consulta de suscripción.
2. **Brecha IDOR en Planes (Crítica - `GAP-ENT-01`):**
   * El controlador legado `com.saed.backend.authorization.controller.PlanesController` permite a usuarios con `SCOPE_ADMIN_PROPIEDAD` ejecutar `POST /api/v1/planes` (crear plan), `PUT /api/v1/planes/{id}` (modificar límites y precio de un plan existente) y `PATCH /api/v1/planes/{id}/estado`. Un administrador de propiedad malicioso puede alterar las tarifas y cuotas globales de toda la plataforma SAED.
3. **Brecha de Auto-Upgrade Gratuito (`GAP-ENT-02`):**
   * `com.saed.backend.authorization.controller.MembresiasController.cambiarPlan` (`POST /api/v1/membresias/cambiar-plan`): Un administrador de organización (`SCOPE_ADMIN_ORGANIZACION`) envía `{ "idPlanNuevo": 4 }` (Enterprise) y el backend ejecuta directamente el update en `MEMBRESIAS` sin pasar por pasarela de pago ni requerir autorización de SuperAdmin.

---

## 7. INTEGRACIÓN CON WOMPI Y ACTIVACIÓN POST-PAGO

### 7.1 Flujo de Registro Inicial (Onboarding)
1. **Selección e Inicio:** El usuario selecciona un plan en `RegistroOrganizacionPage.jsx` y envía `POST /api/v1/public/onboarding/register`.
2. **Integridad de Precios:** `OnboardingServiceImpl.java` consulta en base de datos el plan vía `idPlan`. **Descarta cualquier precio enviado desde el cliente**.
3. **Planes Gratuitos / Trial:** Se aprovisiona inmediatamente la organización (`ACTIVA`), persona administradora, usuario (`ACTIVO`) y membresía en estado `PRUEBA` (14 días).
4. **Planes Comerciales:**
   * Se inserta un borrador en `ONBOARDING_INTENCIONES` (`ESTADO = 'PENDIENTE_PAGO'`).
   * Se crea una transacción en `TRANSACCIONES_PAGO` con referencia única generada `ONB-UUID-TIMESTAMP`.
   * Se calcula la firma de integridad de Wompi en backend:
     $$\text{hash} = \text{SHA256}(\text{referencia} + \text{montoEnCentavos} + \text{"COP"} + \text{WOMPI\_INTEGRITY\_SECRET})$$
   * Retorna al frontend `WompiCheckoutDTO` para levantar el widget oficial de Wompi.

### 7.2 Procesamiento del Webhook de Wompi
* Endpoint: `POST /api/v1/public/wompi/webhook` (`WompiWebhookController.java`).
* **Seguridad Criptográfica:** Valida el header/payload contra `WOMPI_EVENTS_SECRET` mediante HMAC SHA-256.
* **Consistencia de Datos:** Comprueba que la moneda sea `COP` y que el monto en centavos coincida exactamente con el registrado en `TRANSACCIONES_PAGO`.
* **Idempotencia:** Verifica el estado actual de la transacción. Si ya fue procesada (`APROBADO` o `RECHAZADO`), responde `200 OK` inmediatamente evitando doble provisión.
* **Materialización:** En caso de `APPROVED`:
  * Actualiza la transacción a `APROBADO`.
  * Transiciona la intención en `ONBOARDING_INTENCIONES` a `PROCESADO`.
  * Aprovisiona la organización (`ACTIVA`), usuario administrador (`ACTIVO`) y membresía (`ACTIVA`, con `FECHA_FIN = +1 mes` o `+1 año`).
  * Emite correos transaccionales con credenciales de acceso y recibo de pago.

### 7.3 Limitación en Ciclo de Vida de Clientes Existentes
* `WompiServiceImpl.crearIntencion(request)` (`WompiServiceImpl.java:150`) valida explícitamente:
  ```java
  if (!"CUOTA".equalsIgnoreCase(request.getTipo()) && !"MULTA".equalsIgnoreCase(request.getTipo())) {
      throw new IllegalArgumentException("Tipo de transacción no soportado para Wompi: " + request.getTipo());
  }
  ```
* **No existe soporte para tipo `"MEMBRESIA"` ni `"UPGRADE"`**. Si una organización activa desea renovar su suscripción anual o hacer upgrade a un plan superior, no existe endpoint de pago ni webhook que actualice la membresía existente.

---

## 8. ESTADOS DE MEMBRESÍA Y REGLAS DE CICLO DE VIDA

El esquema de base de datos (`CK_MEMBRESIAS_ESTADO`) soporta los siguientes 5 estados:

```
                  ┌──────────────┐
                  │    PRUEBA    │ (14 días gratis)
                  └──────┬───────┘
                         │
     [Pago Wompi OK]     │ [Expira tiempo sin pago]
            ▼            ▼
┌──────────────────┐   ┌──────────────────┐
│      ACTIVA      │   │     EXPIRADA     │◄── (FECHA_FIN < SYSDATE)
└────────┬─────────┘   └────────┬─────────┘
         │                      │
         ├──────────────────────┼──────────────────────┐
         ▼                      ▼                      ▼
┌──────────────────┐   ┌──────────────────┐   ┌──────────────────┐
│    SUSPENDIDA    │   │    CANCELADA     │   │   REACTIVADA     │
└──────────────────┘   └──────────────────┘   └──────────────────┘
```

1. **`PRUEBA`:** Permite operaciones normales hasta que transcurren los días de gracia. Validado por `PlanLimitServiceImpl` (`m.ESTADO IN ('ACTIVA', 'PRUEBA') AND (FECHA_FIN IS NULL OR FECHA_FIN >= TRUNC(SYSDATE))`).
2. **`ACTIVA`:** Estado regular de cliente al día.
3. **`EXPIRADA`:** Transición automática o por cron cuando `FECHA_FIN < TRUNC(SYSDATE)`. Bloquea inmediatamente la creación de propiedades, unidades y usuarios.
4. **`SUSPENDIDA`:** Ejecutada manualmente por SuperAdmin (`PlatformMembershipsController.toggleStatus`). Bloquea inmediatamente todas las mutaciones operativas.
5. **`CANCELADA`:** Estado terminal por baja definitiva de la organización.
* *Nota Técnica:* La base de datos prohíbe terminantemente valores no tipificados como `'INACTIVA'`, `'VENCIDA'` o `'PENDIENTE'` en la tabla `MEMBRESIAS`.

---

## 9. CAPACIDADES: SUPERADMIN VS ADMINISTRADOR DE ORGANIZACIÓN

| Acción / Funcionalidad | SuperAdmin | Admin Organización | Admin Propiedad |
| :--- | :---: | :---: | :---: |
| Crear planes SaaS globales | **Autorizado** (`/platform/plans`) | No permitido | **Vulnerabilidad** (`/api/v1/planes`) |
| Modificar precios / límites de plan | **Autorizado** (`/platform/plans`) | No permitido | **Vulnerabilidad** (`/api/v1/planes`) |
| Activar / Desactivar plan | **Autorizado** (`/platform/plans`) | No permitido | **Vulnerabilidad** (`/api/v1/planes`) |
| Listar todas las membresías | **Autorizado** (`/platform/memberships`) | Denegado (403) | Denegado (403) |
| Asignar plan manualmente a Org | **Autorizado** (`/platform/memberships/assign`) | Denegado (403) | Denegado (403) |
| Suspender / Reactivar membresía | **Autorizado** (`/platform/memberships/{id}`) | Denegado (403) | Denegado (403) |
| Ver límites y consumos de su Org | Autorizado (vía catálogo global) | **Autorizado** (`/org/subscription`)| No permitido |
| Solicitar Upgrade con pago Wompi | N/A | **Faltante** | Denegado (403) |
| Auto-Upgrade sin pago | N/A | **Exploit activo** (`/membresias`) | Denegado (403) |

---

## 10. ANÁLISIS DE FRONTEND

1. **`SuperAdminPlanesPage.jsx` (`frontend/src/pages/SuperAdminPlanesPage.jsx`):**
   * Consume correctamente `/api/v1/platform/plans`.
   * Permite crear planes con modal y alternar estado activo/inactivo con `toggleStatus`.
   * *Limitación:* La UI no expone botón ni modal para invocar el endpoint `PUT` (edición de límites existentes), aunque el backend sí lo soporta.
2. **`SuperAdminMembresiasPage.jsx` (`frontend/src/pages/SuperAdminMembresiasPage.jsx`):**
   * Consume `/api/v1/platform/memberships`.
   * Permite asignar membresías a organizaciones y suspender/reactivar. Funcional y alineado con el backend.
3. **`OrgPlanPage.jsx` (`frontend/src/pages/OrgPlanPage.jsx`):**
   * Consume `/api/v1/org/subscription`.
   * Muestra barras de progreso de consumo de Propiedades, Unidades, Usuarios y Almacenamiento.
   * Totalmente de solo lectura. No ofrece botones de acción para renovar o actualizar plan.
4. **Discrepancias de Precios y Semillas (`LandingPricing.jsx` vs `RegistroOrganizacionPage.jsx` vs SQL):**
   * En `LandingPricing.jsx`: Tarifa `PRO` figura en **$149.000 COP** y `ENTERPRISE` en **$399.000 COP**.
   * En `V4.13__seed_planes.sql`: Tarifa `PRO` es **$299.000 COP** y `ENTERPRISE` es **$799.000 COP**.
   * En `RegistroOrganizacionPage.jsx`: Las tarifas provienen dinámicamente de `/api/v1/public/onboarding/plans`, pero en caso de fallback estático, contiene montos desfasados.
5. **Páginas Huérfanas:**
   * `frontend/src/pages/PlanesPage.jsx` y `frontend/src/pages/MembresiasPage.jsx` están importadas en `App.jsx` pero desprovistas de rutas accesibles en la barra lateral. Deben ser eliminadas o redirigidas formalmente.

---

## 11. BASE DE DATOS Y CAPACIDAD ESTRUCTURAL

El modelo relacional actual en Oracle ATP es **completamente suficiente y de alta calidad** para soportar el ciclo de vida SaaS sin requerir modificaciones estructurales mayores:
* El modelo `ORGANIZACIONES → MEMBRESIAS → PLANES` está normalizado y protegido por restricciones check y claves foráneas.
* La restricción de unicidad activa `UIX_MEMBRESIAS_VIGENTE` previene inconsistencias de doble plan activo.
* La tabla `MEMBRESIAS_HISTORIAL` con su trigger `TRG_MEMBHIST_IMMUTABLE` proporciona la pista de auditoría inmutable necesaria para cumplimiento y facturación.
* Las tablas `MODULOS` y `PLAN_MODULOS` ya están creadas con sus claves foráneas; solo requieren ser pobladas mediante semillas SQL y consumidas en la lógica de aplicación.

---

## 12. CLASIFICACIÓN DE GAPs

### GAP-ENT-01: Brecha Crítica de Autorización en PlanesController
* **Severidad:** **P0 (Crítico - Seguridad)**
* **Descripción:** `PlanesController.java` (`/api/v1/planes`) está protegido con `SCOPE_ADMIN_PROPIEDAD` y expone endpoints `POST`, `PUT` y `PATCH` que permiten a administradores de propiedades modificar el catálogo global de planes SaaS, alterar precios y manipular límites.
* **Archivos Afectados:** `backend/src/main/java/com/saed/backend/authorization/controller/PlanesController.java`.
* **Tablas Afectadas:** `PLANES`.
* **Complejidad:** Baja (Eliminar endpoints de mutación en `PlanesController` o restringir el controlador exclusivamente a lectura pública/autenticada, derivando la administración a `PlatformPlansController`).
* **Dependencias:** Ninguna.

### GAP-ENT-02: Auto-Upgrade no Autorizado sin Pago en MembresiasController
* **Severidad:** **P0 (Crítico - Integridad Financiera)**
* **Descripción:** `MembresiasController.java` permite a un administrador de organización ejecutar `POST /api/v1/membresias/cambiar-plan` y cambiarse libremente a un plan Enterprise sin pagar ni mediar validación de SuperAdmin.
* **Archivos Afectados:** `backend/src/main/java/com/saed/backend/authorization/controller/MembresiasController.java`, `com.saed.backend.authorization.service.impl.MembresiasServiceImpl`.
* **Tablas Afectadas:** `MEMBRESIAS`.
* **Complejidad:** Baja (Eliminar el método de auto-upgrade directo sin pasarela y exigir flujo Wompi o asignación manual por SuperAdmin).
* **Dependencias:** Relacionado con GAP-ENT-04.

### GAP-ENT-03: Ausencia Total de Enforcement de Entitlements de Módulos
* **Severidad:** **P1 (Alto - Arquitectura SaaS)**
* **Descripción:** Las tablas `MODULOS` y `PLAN_MODULOS` están vacías y no son consultadas por el backend. No existe validación que impida a una organización en plan Básico utilizar módulos exclusivos de planes superiores (Asambleas, Obras, Pólizas, Reservas).
* **Archivos Afectados:** `backend/src/main/java/com/saed/backend/security/**`, DDL `MODULOS`, `PLAN_MODULOS`.
* **Tablas Afectadas:** `MODULOS`, `PLAN_MODULOS`.
* **Complejidad:** Media (Crear semilla de módulos, vincularlos a los planes en `PLAN_MODULOS`, y agregar verificación de módulo en `SecurityService` o un interceptor `@RequireModule("ASAMBLEAS")`).
* **Dependencias:** Requiere poblar datos de `MODULOS` y `PLAN_MODULOS`.

### GAP-ENT-04: Ausencia de Flujo de Renovación y Upgrade con Wompi para Clientes Existentes
* **Severidad:** **P1 (Alto - Negocio SaaS)**
* **Descripción:** Las organizaciones activas no pueden renovar su membresía anual ni hacer upgrade a un plan superior mediante pasarela de pago. `WompiServiceImpl.crearIntencion` rechaza cualquier tipo distinto a `CUOTA` o `MULTA`.
* **Archivos Afectados:** `backend/src/main/java/com/saed/backend/finanzas/service/impl/WompiServiceImpl.java`, `OrgPlanPage.jsx`.
* **Tablas Afectadas:** `TRANSACCIONES_PAGO`, `MEMBRESIAS`, `MEMBRESIAS_HISTORIAL`.
* **Complejidad:** Media-Alta (Habilitar tipo `MEMBRESIA` en `WompiService`, soportar intención de renovación/upgrade, webhook para actualizar `MEMBRESIAS` y generar registro en `MEMBRESIAS_HISTORIAL`).
* **Dependencias:** GAP-ENT-05.

### GAP-ENT-05: Inconsistencia y Fallo en Registro de Historial de Membresías
* **Severidad:** **P2 (Medio - Integridad y Auditoría)**
* **Descripción:** `MembresiasServiceImpl.cambiarPlan` intenta insertar en `MEMBRESIAS_HISTORIAL` con nombres de columnas erróneos (`plan_anterior`, `plan_nuevo`, `notas`) y valor de texto para una FK numérica (`REALIZADO_POR = 'SISTEMA'`). Además, `PlatformMembershipsController` no registra entradas en el historial al suspender, reactivar o asignar membresías.
* **Archivos Afectados:** `MembresiasServiceImpl.java`, `PlatformMembershipsController.java`.
* **Tablas Afectadas:** `MEMBRESIAS_HISTORIAL`.
* **Complejidad:** Baja (Corregir el SQL de inserción utilizando las columnas reales `ID_PLAN_ANTERIOR`, `ID_PLAN_NUEVO`, `OBSERVACIONES` y el `ID_USUARIO` de la sesión).
* **Dependencias:** Ninguna.

### GAP-ENT-06: Falta de Control de Cuota Global de Almacenamiento
* **Severidad:** **P2 (Medio - Control de Recursos)**
* **Descripción:** La columna `LIMITE_ALMACENAMIENTO_GB` de la tabla `PLANES` se expone en la UI pero nunca se computa ni valida en el servicio de subida de archivos (`FileStorageServiceImpl`).
* **Archivos Afectados:** `backend/src/main/java/com/saed/backend/documentos/service/impl/FileStorageServiceImpl.java`.
* **Tablas Afectadas:** `DOCUMENTOS`, `PLANES`.
* **Complejidad:** Media (Consultar la suma de bytes de los documentos activos de la organización y comparar contra el límite en GB del plan).
* **Dependencias:** Ninguna.

### GAP-ENT-07: Inconsistencia de Precios y Páginas Huérfanas en Frontend
* **Severidad:** **P3 (Menor - Higiene y UX)**
* **Descripción:** Discrepancias entre las tarifas mostradas en `LandingPricing.jsx` ($149k/$399k) vs `V4.13__seed_planes.sql` ($299k/$799k). Presencia de componentes huérfanos `PlanesPage.jsx` y `MembresiasPage.jsx`.
* **Archivos Afectados:** `frontend/src/components/landing/LandingPricing.jsx`, `frontend/src/pages/PlanesPage.jsx`, `frontend/src/pages/MembresiasPage.jsx`, `App.jsx`.
* **Tablas Afectadas:** Ninguna.
* **Complejidad:** Baja (Alinear precios con la semilla SQL y remover imports/páginas muertas).
* **Dependencias:** Ninguna.

---

## 13. ESTRATEGIA Y RECOMENDACIONES DE IMPLEMENTACIÓN

### 13.1 Qué se puede Reutilizar (Aprobado y Probado)
1. **Mecanismo de Bloqueo Pesimista en `PlanLimitServiceImpl`:** El patrón `FOR UPDATE OF m.ID_MEMBRESIA` y las validaciones de límites de propiedades, unidades y usuarios son ejemplares; deben mantenerse exactamente como están.
2. **Esquema Relacional Existente:** No se requiere ningún cambio DDL en las tablas principales (`PLANES`, `MEMBRESIAS`, `MEMBRESIAS_HISTORIAL`, `MODULOS`, `PLAN_MODULOS`). La estructura actual es completamente sólida.
3. **Flujo Criptográfico de Wompi para Onboarding:** La generación de firmas SHA-256 y la verificación HMAC en el webhook funcionan de forma impecable y segura.

### 13.2 Qué debe Refactorizarse
1. **Sanitización de Controladores de Planes:**
   * Cerrar la brecha `GAP-ENT-01`: Eliminar los métodos de escritura en `PlanesController.java` (`/api/v1/planes`), dejándolo como endpoint de lectura pública o catálogo para usuarios autenticados, y centralizar la gestión de planes exclusivamente en `PlatformPlansController.java` para `SUPERADMIN`.
   * Desactivar el endpoint inseguro `POST /api/v1/membresias/cambiar-plan` (`GAP-ENT-02`).
2. **Corrección de Bitácora de Historial (`GAP-ENT-05`):**
   * Ajustar las consultas SQL hacia `MEMBRESIAS_HISTORIAL` para respetar el esquema real de Oracle y registrar eventos en suspensiones, reactivaciones y cambios de plan.
3. **Sincronización de Frontend:**
   * Alinear `LandingPricing.jsx` con los precios oficiales de la base de datos y eliminar páginas huérfanas.

### 13.3 Qué NO debe Tocarse
* El núcleo de seguridad Oracle VPD/RLS (`PKG_SAED_SESSION`, `SAED_CTX`).
* El flujo de onboarding post-pago existente en `OnboardingServiceImpl` y `WompiServiceImpl`.
* Las restricciones check en base de datos (`UIX_MEMBRESIAS_VIGENTE`, `TRG_MEMBHIST_IMMUTABLE`).

### 13.4 Hoja de Ruta Sugerida para Fase de Implementación
* **Paso 1 (P0):** Clausurar brechas de autorización en `PlanesController` y `MembresiasController`.
* **Paso 2 (P2):** Corregir el registro de auditoría en `MEMBRESIAS_HISTORIAL`.
* **Paso 3 (P1):** Poblar semillas de `MODULOS` y `PLAN_MODULOS` e implementar la verificación de entitlements funcionales en backend.
* **Paso 4 (P1):** Desarrollar el flujo de renovación y upgrade vía Wompi para organizaciones existentes.
* **Paso 5 (P2/P3):** Implementar control de cuota de almacenamiento en subidas y pulir consistencia visual en el frontend.
