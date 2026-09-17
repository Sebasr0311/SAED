# SAED 2.0 — REPORTE DE IMPLEMENTACIÓN BLOQUE 1
## CIERRE P0 DE PLANES/MEMBRESÍAS + INTEGRIDAD HISTÓRICA

**Fecha:** 16 de Septiembre de 2026  
**Rama:** `Sebasr0311/angelfish`  
**Commit Base:** `7370c48` (posterior a `d8c8eb8`)  
**Autor:** Antigravity Architect  

---

## 1. RESUMEN EJECUTIVO

El presente bloque de trabajo tuvo como objetivo resolver de forma prioritaria e intransigente las dos vulnerabilidades **P0** identificadas en el subsistema SaaS de SAED 2.0, así como el defecto **P2** que impedía el registro atómico y fidedigno de auditoría en el historial de membresías.

### Matriz de Estado de GAPs SaaS: Antes vs Después de Bloque 1

| Identificador | Severidad | Descripción del Hallazgo | Estado Previo | Estado Posterior |
| :--- | :---: | :--- | :---: | :---: |
| **GAP-ENT-01** | **P0** | Mutaciones no autorizadas en `PlanesController` expuestas a administradores de copropiedad y orfandad de control central | 🔴 Vulnerable | 🟢 **Cerrado y Blindado** |
| **GAP-ENT-02** | **P0** | Riesgo de alteración directa de `MEMBRESIAS.ID_PLAN` sin mediación de pago o proceso transaccional de plataforma | 🔴 Riesgo P0 | 🟢 **Verificado y Blindado** |
| **GAP-ENT-05** | **P2** | Inserciones en `MEMBRESIAS_HISTORIAL` rotas por discrepancia de columnas, violación de check constraints y ORA-01722 | 🔴 Roto / Erróneo | 🟢 **Corregido y Atómico** |
| **GAP-ENT-03** | P1 | Entitlements de módulos y flags en backend/frontend | 🟡 Pendiente | 🟡 Pendiente (Bloque 2) |
| **GAP-ENT-04** | P1 | Pasarela Wompi para renovación y upgrade recurrente | 🟡 Pendiente | 🟡 Pendiente (Bloque 3) |
| **GAP-ENT-06** | P2 | Cuota global de almacenamiento SaaS agregada por tenant | 🟡 Pendiente | 🟡 Pendiente (Bloque 4) |
| **GAP-ENT-07** | P3 | Job recurrente de expiración automática de membresías | 🟡 Pendiente | 🟡 Pendiente (Bloque 5) |

**Conclusión Ejecutiva:** Los 2 GAPs P0 críticos han sido eliminados por completo. El backend y frontend compilan limpiamente (0 errores). Se crearon 14 pruebas de integración dedicadas y se verificaron 36 pruebas de regresión existentes, logrando **50/50 tests en verde (100% de éxito)**.

---

## 2. RESOLUCIÓN DETALLADA: GAP-ENT-01 (PLANES Y AUTORIZACIÓN)

### Vulnerabilidad Original
En `backend/src/main/java/com/saed/backend/finanzas/controller/PlanesController.java`:
1. La anotación de clase `@PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')")` permitía que un administrador de propiedad tuviera acceso a mutaciones operativas.
2. Contenía métodos mutadores (`POST /`, `PUT /{id}`, `PATCH /{id}/status`) que permitían alterar el catálogo global de planes SaaS desde el ámbito de la copropiedad, eludiendo la separación de plataforma.
3. El controlador legítimo de plataforma (`PlatformPlansController.java`) carecía de un endpoint explícito para conmutación de estado (`PATCH /{id}/status`).

### Acciones de Remediación Ejecutadas

1. **Purga de mutaciones en `PlanesController.java`**:
   - Se eliminaron íntegramente los endpoints mutadores `POST /api/v1/planes`, `PUT /api/v1/planes/{id}` y `PATCH /api/v1/planes/{id}/status`.
   - Se redefinió la autorización de clase a:
     ```java
     @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD')")
     ```
   - El controlador quedó restringido estrictamente a operaciones de consulta (`GET /`, `GET /catalogo`, `GET /{id}`), permitiendo a las organizaciones y administradores visualizar los planes disponibles sin capacidad de alteración.

2. **Centralización en `PlatformPlansController.java`**:
   - Todas las mutaciones del catálogo de planes residen de manera exclusiva bajo `/api/v1/platform/plans`, protegido a nivel de clase con `@PreAuthorize("hasAuthority('SCOPE_SUPERADMIN')")`.
   - Se incorporó el endpoint `@PatchMapping("/{id}/status")` con soporte transaccional `@Transactional` y auditoría financiera/administrativa `@Auditable`.

### Matriz Comparativa de Endpoints de Planes

| Endpoint | Método | Rol Autorizado (Antes) | Rol Autorizado (Ahora) | Comportamiento Actual |
| :--- | :---: | :---: | :---: | :--- |
| `/api/v1/planes` | GET | `ADMIN_PROPIEDAD` | `SUPERADMIN`, `ADMIN_ORG`, `ADMIN_PROP` | Consulta de catálogo |
| `/api/v1/planes/catalogo` | GET | `ADMIN_PROPIEDAD` | `SUPERADMIN`, `ADMIN_ORG`, `ADMIN_PROP` | Consulta selectiva activos |
| `/api/v1/planes/{id}` | GET | `ADMIN_PROPIEDAD` | `SUPERADMIN`, `ADMIN_ORG`, `ADMIN_PROP` | Detalle de plan |
| `/api/v1/planes` | POST | `ADMIN_PROPIEDAD` | **ELIMINADO** | `405 Method Not Allowed` |
| `/api/v1/planes/{id}` | PUT | `ADMIN_PROPIEDAD` | **ELIMINADO** | `405 Method Not Allowed` |
| `/api/v1/planes/{id}/status` | PATCH | `ADMIN_PROPIEDAD` | **ELIMINADO** | `404 Not Found` |
| `/api/v1/platform/plans` | POST/PUT | `SUPERADMIN` | `SUPERADMIN` | Alta/edición canónica SaaS |
| `/api/v1/platform/plans/{id}/status` | PATCH | *Inexistente* | `SUPERADMIN` | Activación/desactivación |

---

## 3. RESOLUCIÓN DETALLADA: GAP-ENT-02 (BYPASS Y CAMBIO DE PLAN SIN PAGO)

### Análisis de Amenazas y Verificación de Repositorio
Se realizó una inspección exhaustiva de todo el árbol de código backend para determinar si existían rutas o métodos que permitieran modificar `MEMBRESIAS.ID_PLAN` sin validar pago previo:
1. **Inexistencia de endpoints espurios**: Se confirmó que no existen endpoints tipo `POST /api/v1/membresias/cambiar-plan` ni implementaciones en servicios que ejecuten updates directos a `ID_PLAN` invocables por usuarios de tenant (`ADMIN_ORGANIZACION`, `ADMIN_PROPIEDAD`).
2. **Barrera de Autorización en `MembresiasController.java`**:
   - `POST /api/v1/membresias` exige estrictamente `@PreAuthorize("hasAuthority('SCOPE_SUPERADMIN')")`. Un intento por parte de un admin de organización o de propiedad recibe de inmediato un `403 Forbidden`.
   - `PATCH /api/v1/membresias/{id}/status` exige `@PreAuthorize("hasAuthority('SCOPE_SUPERADMIN')")`.
   - `DELETE /api/v1/membresias/{id}` exige `@PreAuthorize("hasAuthority('SCOPE_SUPERADMIN')")`.
3. **Barrera en `PlatformMembershipsController.java`**:
   - El controlador de plataforma está sellado con `@PreAuthorize("hasAuthority('SCOPE_SUPERADMIN')")`.
   - Las transiciones de membresía manuales solo son factibles mediante el operador de plataforma autenticado.

---

## 4. RESOLUCIÓN DETALLADA: GAP-ENT-05 (HISTORIAL DE MEMBRESÍAS ATÓMICO)

### Diagnóstico de Fallas Previas
La auditoría de base de datos reveló que el código existente en `MembresiasController.java` y `PlatformMembershipsController.java` producía excepciones críticas al interactuar con `MEMBRESIAS_HISTORIAL`:
1. **Discrepancia severa de columnas**:
   - Código anterior intentaba insertar en columnas inexistentes: `PLAN_ANTERIOR`, `PLAN_NUEVO`, `NOTAS`.
   - Columnas reales en Oracle ATP: `ID_PLAN_ANTERIOR` (NUMBER), `ID_PLAN_NUEVO` (NUMBER), `TIPO_CAMBIO` (VARCHAR2), `OBSERVACIONES` (VARCHAR2), `FECHA_CAMBIO` (TIMESTAMP), `REALIZADO_POR` (NUMBER FK a `USUARIOS.ID_USUARIO`).
2. **Violación de Check Constraint `CK_MEMBHIST_TIPO`**:
   - Valores anteriores: `'CREACION'`, `'CAMBIO_ESTADO'`.
   - Valores permitidos por DDL Oracle: `'INICIO'`, `'UPGRADE'`, `'DOWNGRADE'`, `'RENOVACION'`, `'CANCELACION'`, `'SUSPENSION'`, `'REACTIVACION'`.
3. **Error ORA-01722 (Número no válido)**:
   - El código insertaba el literal `'SISTEMA'` en la columna `REALIZADO_POR`. Siendo una clave foránea numérica hacia `USUARIOS(ID_USUARIO)`, el driver Oracle lanzaba `ORA-01722: invalid number`.
4. **Fuga Transaccional**:
   - Los métodos no eran `@Transactional` y envolvían los inserts en bloques `catch (Exception e) { log.error(...); }`, silenciando las fallas y dejando la membresía actualizada pero sin registro en el historial.

### Implementación Realizada

1. **Extracción de Identidad del Invocador**:
   ```java
   Long callerUserId = (SaedContextHolder.getContext() != null) 
           ? SaedContextHolder.getContext().getUserId() 
           : null;
   ```
2. **Mapeo Riguroso de Tipos de Cambio**:
   - Creación de membresía inicial: `TIPO_CAMBIO = 'INICIO'`, `ID_PLAN_ANTERIOR = null`.
   - Creación sobre membresía previa (reemplazo):
     - Si `idPlan > idPlanAnterior` → `'UPGRADE'`
     - Si `idPlan < idPlanAnterior` → `'DOWNGRADE'`
     - Si `idPlan == idPlanAnterior` → `'RENOVACION'`
   - Cambio de estado a `SUSPENDIDA`: `TIPO_CAMBIO = 'SUSPENSION'`.
   - Cambio de estado a `CANCELADA`: `TIPO_CAMBIO = 'CANCELACION'`.
   - Cambio de estado a `ACTIVA` (viniendo de suspendida): `TIPO_CAMBIO = 'REACTIVACION'`.
3. **Transaccionalidad Completa y Eliminación de Bloques Silenciadores**:
   - Se decoraron los métodos con `@Transactional` (Spring Framework).
   - Se eliminaron los bloques `try-catch` que silenciaban excepciones. Cualquier fallo en la inserción del historial provoca el rollback automático e inmediato del cambio en la tabla `MEMBRESIAS`.

---

## 5. EVIDENCIA DE PRUEBAS Y VERIFICACIÓN

### 5.1 Nueva Suite de Integración: `P0PlansAndMembershipsSecurityTest`
Se implementó la suite completa en `backend/src/test/java/com/saed/backend/platform/P0PlansAndMembershipsSecurityTest.java` validando 14 escenarios directos:

```
[INFO] Running com.saed.backend.platform.P0PlansAndMembershipsSecurityTest
[INFO] Tests run: 14, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 10.97 s
[INFO] BUILD SUCCESS
```

Detalle de aserciones verificadas:
1. `adminPropiedad_cannotCreatePlatformPlan`: `POST /api/v1/platform/plans` → `403 Forbidden`
2. `adminOrg_cannotUpdatePlatformPlan`: `PUT /api/v1/platform/plans/1` → `403 Forbidden`
3. `adminPropiedad_cannotTogglePlatformPlanStatus`: `PATCH /api/v1/platform/plans/1/status` → `403 Forbidden`
4. `residente_cannotAccessPlatformPlans`: `GET` y `POST` en `/api/v1/platform/plans` → `403 Forbidden`
5. `planesController_postNoLongerExists`: `POST /api/v1/planes` → `405 Method Not Allowed`
6. `planesController_putNoLongerExists`: `PUT /api/v1/planes/1` → `405 Method Not Allowed`
7. `planesController_patchStatusNoLongerExists`: `PATCH /api/v1/planes/1/status` → `404 Not Found`
8. `planesController_readCatalogAllowedForTenantAdmins`: `GET /api/v1/planes` y `/catalogo` → `200 OK`
9. `tenantAdmin_cannotInvokeSelfUpgradeEndpoint`: `POST /api/v1/membresias/cambiar-plan` → `405 Method Not Allowed`
10. `adminOrg_cannotMutateMembershipViaMembresiasController`: `POST`, `PATCH`, `DELETE` → `403 Forbidden`
11. `adminPropiedad_cannotMutatePlatformMemberships`: `POST` y `PUT` en `/platform/memberships` → `403 Forbidden`
12. `superAdmin_updateStatusGeneratesValidHistorialRecord`: Transición a `SUSPENDIDA` genera fila con `TIPO_CAMBIO = 'SUSPENSION'` y `ID_PLAN_NUEVO` válido.
13. `superAdmin_reactivateGeneratesReactivacionHistorial`: Reactivación genera fila con `TIPO_CAMBIO = 'REACTIVACION'`.
14. `superAdmin_cancelGeneratesCancelacionHistorial`: Cancelación genera fila con `TIPO_CAMBIO = 'CANCELACION'`.

### 5.2 Suites de Regresión Ejecutadas
- **`SuperAdminAdversarialAuthorizationTest`**: 24 tests ejecutados, 0 fallos, 0 errores (12.84 s).
- **`ModelCLimitsSecurityIntegrationTest`**: 12 tests ejecutados, 0 fallos, 0 errores (14.59 s).
- **Total Backend Tests en Verde**: **50 tests ejecutados con 100% de éxito**.

### 5.3 Compilación de Frontend
- Se ejecutó `npm run build` en `frontend/`:
```
✓ built in 8.96s
0 build errors.
```

---

## 6. ARCHIVOS MODIFICADOS Y CREADOS

```
M  backend/src/main/java/com/saed/backend/finanzas/controller/MembresiasController.java
M  backend/src/main/java/com/saed/backend/finanzas/controller/PlanesController.java
M  backend/src/main/java/com/saed/backend/platform/controller/PlatformMembershipsController.java
M  backend/src/main/java/com/saed/backend/platform/controller/PlatformPlansController.java
A  backend/src/test/java/com/saed/backend/platform/P0PlansAndMembershipsSecurityTest.java
A  docs/audit/SAED_P0_MEMBERSHIP_SECURITY_IMPLEMENTATION_REPORT.md
```

---

## 7. PRÓXIMOS PASOS (BLOQUES SUBSECUENTES)

Con los GAPs P0 resueltos de raíz, el sistema queda listo para los siguientes bloques de arquitectura SaaS:

1. **Bloque 2 (P1) — Entitlements de Módulos (GAP-ENT-03)**:
   - Definir estructura canónica de flags por plan (`MODULO_PQRS`, `MODULO_VISITAS_QR`, `MODULO_CARTERA`, `MODULO_ASAMBLEAS`, etc.).
   - Implementar validación en filtros/interceptores backend y directivas de navegación en frontend.
2. **Bloque 3 (P1) — Flujo Wompi de Upgrade y Renovación (GAP-ENT-04)**:
   - Orquestar webhook de confirmación de pago para transición automática de planes sin requerir intervención manual de SUPERADMIN.
3. **Bloque 4 (P2) — Cuota Global de Almacenamiento (GAP-ENT-06)**:
   - Sumatoria de espacio consumido por documentos y fotos frente al `LIMITE_ALMACENAMIENTO_GB` del plan.
4. **Bloque 5 (P3) — Expiración Automática de Membresías (GAP-ENT-07)**:
   - Scheduler nocturno `@Scheduled` que marque membresías vencidas como `EXPIRADA`.
