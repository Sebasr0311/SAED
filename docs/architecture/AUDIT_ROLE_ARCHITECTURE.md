# AUDITORÍA DE ARQUITECTURA DE ROLES Y RUTAS — SAED 2.0
**Fase 0: Congelar y Auditar**  
**Fecha:** 2026-09-10  
**Autor:** Senior Software & Security Architect  
**Estado:** Finalizado / Base de Certificación

---

## 1. Resumen Ejecutivo

Esta auditoría técnica examina el estado real del repositorio respecto a la separación de responsabilidades y modelos de autorización para los tres roles administrativos de SAED 2.0:
1. **SUPERADMIN** (Ámbito `GLOBAL` — Operador y Dueño de la Plataforma SaaS).
2. **ADMIN_ORGANIZACION** (Ámbito `ORGANIZACION` — Administrador Gerencial de Cartera de Propiedades).
3. **ADMIN_PROPIEDAD** (Ámbito `PROPIEDAD` — Administrador Operativo de Copropiedad).

### Hallazgos Principales
* **Duplicidad de Rutas Administrativas:** Existen al menos 6 pares de rutas redundantes que renderizan exactamente el mismo componente (ej. `/mantenimiento-admin` vs `/mantenimientos`, `/asambleas-admin` vs `/asambleas`, `/polizas-admin` vs `/polizas`, `/emergencias-admin` vs `/emergencias`, `/porterias` vs `/porterias-admin`, `/organizaciones` vs `/superadmin/organizaciones`).
* **Rutas Huérfanas / No Registradas:** `DocumentosAdminPage.jsx` existe en el frontend y tiene backend funcional en `DocumentoController.java` (`/api/v1/documentos/admin`), pero nunca fue vinculada en `App.jsx` ni en la barra de navegación.
* **Inconsistencia de Rol en Catálogo de Propiedades:** La ruta `/propiedades` permitía a `ADMIN_PROPIEDAD`, `ADMIN_ORGANIZACION` y `SUPERADMIN`. Un `ADMIN_PROPIEDAD` no administra una cartera multiedificio; debe estar anclado a su propiedad activa (`ID_PROPIEDAD`).
* **Consola de ADMIN_ORGANIZACION Reducida:** Aunque el backend cuenta con agregación financiera y operativa (`OrgDashboardController`), la UI solo expone 3 secciones básicas, careciendo de las vistas de supervisión consolidada (`/org/cartera`, `/org/reportes`, `/org/analitica`).
* **Concepto Erróneo de Negocio:** La vista `/ganancias` modela utilidades empresariales dentro de un contexto de propiedad horizontal (régimen Ley 675 sin ánimo de lucro), cuando lo correcto es ejecución presupuestal y flujo de caja.

---

## 2. Inventario Completo de Rutas Actuales

| Ruta | Componente / Página | Rol Declarado (App.jsx) | Scope Esperado | Endpoint Principal Backend | Estado / Diagnóstico |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `/` | `LandingPage.jsx` | Público | N/A | N/A | ✅ Canónico |
| `/login` | `LoginPage.jsx` | Público | N/A | `/api/v1/auth/login` | ✅ Canónico |
| `/suscripciones` | `SuscripcionesPage.jsx` | Público | N/A | `/api/v1/catalogo/planes` | ✅ Canónico |
| `/superadmin/dashboard` | `SuperAdminDashboardPage.jsx` | `SUPERADMIN` | `GLOBAL` | `/api/v1/platform/dashboard` | ✅ Canónico |
| `/superadmin/organizaciones` | `SuperAdminOrganizacionesPage.jsx` | `SUPERADMIN` | `GLOBAL` | `/api/v1/organizations` | ✅ Canónico |
| `/superadmin/propiedades` | `PropiedadesPage.jsx` | `SUPERADMIN` | `GLOBAL` | `/api/v1/properties` | ⚠️ Vista compartida; debe ser catálogo global |
| `/superadmin/planes` | `SuperAdminPlanesPage.jsx` | `SUPERADMIN` | `GLOBAL` | `/api/v1/platform/planes` | ✅ Canónico |
| `/superadmin/membresias` | `SuperAdminMembresiasPage.jsx` | `SUPERADMIN` | `GLOBAL` | `/api/v1/platform/memberships` | ✅ Canónico |
| `/superadmin/administradores` | `SuperAdminAdminsPage.jsx` | `SUPERADMIN` | `GLOBAL` | `/api/v1/platform/admins` | ✅ Canónico |
| `/superadmin/auditoria` | `SuperAdminAuditoriaPage.jsx` | `SUPERADMIN` | `GLOBAL` | `/api/v1/audit` | ✅ Canónico |
| `/superadmin/metricas` | `SuperAdminDashboardPage.jsx` | `SUPERADMIN` | `GLOBAL` | `/api/v1/platform/dashboard` | ⚠️ Stub (renderiza dashboard) |
| `/superadmin/configuracion` | `SuperAdminDashboardPage.jsx` | `SUPERADMIN` | `GLOBAL` | `/api/v1/platform/dashboard` | ⚠️ Stub (renderiza dashboard) |
| `/org/dashboard` | `OrgDashboardPage.jsx` | `ADMIN_ORGANIZACION`, `SUPERADMIN` | `ORGANIZACION` | `/api/v1/org/dashboard` | ✅ Canónico |
| `/org/organizacion` | `OrgOrganizacionPage.jsx` | `ADMIN_ORGANIZACION`, `SUPERADMIN` | `ORGANIZACION` | `/api/v1/org/profile` | ✅ Canónico |
| `/org/propiedades` | `OrgPropiedadesPage.jsx` | `ADMIN_ORGANIZACION`, `SUPERADMIN` | `ORGANIZACION` | `/api/v1/properties` (RLS) | ✅ Canónico |
| `/org/admins` | `OrgAdminsPage.jsx` | `ADMIN_ORGANIZACION`, `SUPERADMIN` | `ORGANIZACION` | `/api/v1/org/admins` | ✅ Canónico |
| `/org/plan` | `OrgPlanPage.jsx` | `ADMIN_ORGANIZACION`, `SUPERADMIN` | `ORGANIZACION` | `/api/v1/org/subscription` | ✅ Canónico |
| `/org/auditoria` | `OrgAuditoriaPage.jsx` | `ADMIN_ORGANIZACION`, `SUPERADMIN` | `ORGANIZACION` | `/api/v1/audit` (RLS) | ✅ Canónico |
| `/dashboard` | `DashboardPage.jsx` | `ADMIN_PROPIEDAD` | `PROPIEDAD` | `/api/v1/dashboard/*` | ✅ Canónico |
| `/personas` | `PersonasPage.jsx` | `ADMIN_PROPIEDAD` | `PROPIEDAD` | `/api/v1/personas` | ✅ Canónico |
| `/residentes` | `ResidentesPage.jsx` | `ADMIN_PROPIEDAD` | `PROPIEDAD` | `/api/v1/residentes` | ✅ Canónico |
| `/unidades` | `UnidadesPage.jsx` | `ADMIN_PROPIEDAD` | `PROPIEDAD` | `/api/v1/unidades` | ✅ Canónico |
| `/contratos` | `ContratosPage.jsx` | `ADMIN_PROPIEDAD` | `PROPIEDAD` | `/api/v1/contratos` | ✅ Canónico |
| `/contratos-proveedor` | `ContratosProveedorPage.jsx` | `ADMIN_PROPIEDAD` | `PROPIEDAD` | `/api/v1/contratos-proveedor` | ✅ Canónico |
| `/organizaciones` | `OrganizacionesPage.jsx` | `SUPERADMIN` | `GLOBAL` | `/api/v1/organizations` | ❌ Duplicada de `/superadmin/organizaciones` |
| `/propiedades` | `PropiedadesPage.jsx` | `SUPERADMIN`, `ADMIN_ORG`, `ADMIN_PROP` | `GLOBAL` / `ORG` | `/api/v1/properties` | ❌ Inconsistente (multi-rol indiscriminado) |
| `/roles-asignaciones` | `RolesYAsignacionesPage.jsx` | `SUPERADMIN`, `ADMIN_ORG`, `ADMIN_PROP` | Depende de contexto | `/api/v1/assignments` | ⚠️ Requiere aislamiento estricto por scope |
| `/planes` (protegida) | `PlanesPage.jsx` | `SUPERADMIN` | `GLOBAL` | `/api/v1/platform/plans` | ❌ Duplicada de `/superadmin/planes` |
| `/membresias` | `MembresiasPage.jsx` | `SUPERADMIN` | `GLOBAL` | `/api/v1/platform/memberships` | ❌ Duplicada de `/superadmin/membresias` |
| `/reportes` | `ReportesPage.jsx` | `ADMIN_PROPIEDAD` | `PROPIEDAD` | `/api/v1/reportes` | ✅ Canónico |
| `/usuarios` | `UsuariosPage.jsx` | `ADMIN_PROPIEDAD` | `PROPIEDAD` | `/api/v1/usuarios` | ✅ Canónico |
| `/porterias` | `PorteriasPage.jsx` | `ADMIN_PROPIEDAD` | `PROPIEDAD` | `/api/v1/porterias` | ✅ Canónico |
| `/porterias-admin` | `PorteriasPage.jsx` | `ADMIN_PROPIEDAD` | `PROPIEDAD` | `/api/v1/porterias` | ❌ Duplicada de `/porterias` |
| `/visitas` | `VisitasPage.jsx` | `ADMIN_PROPIEDAD`, `PORTERO` | `PROPIEDAD` | `/api/v1/porteria/visitas` | ✅ Canónico compartido |
| `/historial-visitas` | `HistorialVisitasPage.jsx` | `ADMIN_PROPIEDAD` | `PROPIEDAD` | `/api/v1/porteria/visitas/historial` | ✅ Canónico |
| `/paquetes-admin` | `PaquetesAdminPage.jsx` | `ADMIN_PROPIEDAD` | `PROPIEDAD` | `/api/v1/paquetes` | ✅ Canónico |
| `/parqueaderos` | `ParqueaderosPage.jsx` | `ADMIN_PROPIEDAD`, `PORTERO` | `PROPIEDAD` | `/api/v1/parqueaderos` | ✅ Canónico |
| `/escanner-qr` | `EscannerQRPage.jsx` | `ADMIN_PROPIEDAD`, `PORTERO` | `PROPIEDAD` | `/api/v1/porteria/qr` | ⚠️ Operación principal de Portero |
| `/pagos` | `PagosPage.jsx` | `ADMIN_PROPIEDAD` | `PROPIEDAD` | `/api/v1/pagos` | ✅ Canónico |
| `/cartera` | `CarteraPage.jsx` | `ADMIN_PROPIEDAD` | `PROPIEDAD` | `/api/v1/cartera` | ✅ Canónico |
| `/presupuestos` | `PresupuestoPage.jsx` | `ADMIN_PROPIEDAD` | `PROPIEDAD` | `/api/v1/presupuestos` | ✅ Canónico |
| `/gastos` | `GastosPage.jsx` | `ADMIN_PROPIEDAD` | `PROPIEDAD` | `/api/v1/gastos` | ✅ Canónico |
| `/flujo-caja` | `FlujoCajaPage.jsx` | `ADMIN_PROPIEDAD` | `PROPIEDAD` | `/api/v1/flujo-caja` | ✅ Canónico |
| `/conciliaciones` | `ConciliacionPage.jsx` | `ADMIN_PROPIEDAD` | `PROPIEDAD` | `/api/v1/conciliaciones` | ✅ Canónico |
| `/paz-y-salvos` | `PazYSalvoPage.jsx` | `ADMIN_PROPIEDAD` | `PROPIEDAD` | `/api/v1/paz-y-salvos` | ✅ Canónico |
| `/ganancias` | `GananciasPage.jsx` | `ADMIN_PROPIEDAD` | `PROPIEDAD` | `/api/v1/finanzas/ganancias` | ❌ Deprecar (reemplazar por Flujo/Presupuesto) |
| `/multas` | `MultasPage.jsx` | `ADMIN_PROPIEDAD` | `PROPIEDAD` | `/api/v1/multas` | ✅ Canónico |
| `/sanciones-admin` | `SancionesAdminPage.jsx` | `ADMIN_PROPIEDAD` | `PROPIEDAD` | `/api/v1/sanciones` | ✅ Canónico |
| `/obras-admin` | `ObrasAdminPage.jsx` | `ADMIN_PROPIEDAD` | `PROPIEDAD` | `/api/v1/obras` | ✅ Canónico |
| `/mantenimiento-admin` | `MantenimientoAdminPage.jsx` | `ADMIN_PROPIEDAD` | `PROPIEDAD` | `/api/v1/mantenimientos` | ❌ Duplicada de `/mantenimientos` |
| `/mantenimientos` | `MantenimientoAdminPage.jsx` | `ADMIN_PROPIEDAD` | `PROPIEDAD` | `/api/v1/mantenimientos` | ✅ Canónico |
| `/asambleas-admin` | `AsambleasAdminPage.jsx` | `ADMIN_PROPIEDAD` | `PROPIEDAD` | `/api/v1/asambleas` | ❌ Duplicada de `/asambleas` |
| `/asambleas` | `AsambleasAdminPage.jsx` | `ADMIN_PROPIEDAD` | `PROPIEDAD` | `/api/v1/asambleas` | ✅ Canónico |
| `/polizas-admin` | `PolizasAdminPage.jsx` | `ADMIN_PROPIEDAD` | `PROPIEDAD` | `/api/v1/polizas` | ❌ Duplicada de `/polizas` |
| `/polizas` | `PolizasAdminPage.jsx` | `ADMIN_PROPIEDAD` | `PROPIEDAD` | `/api/v1/polizas` | ✅ Canónico |
| `/emergencias-admin` | `EmergenciasAdminPage.jsx` | `ADMIN_PROPIEDAD` | `PROPIEDAD` | `/api/v1/emergencias` | ❌ Duplicada de `/emergencias` |
| `/emergencias` | `EmergenciasAdminPage.jsx` | `ADMIN_PROPIEDAD` | `PROPIEDAD` | `/api/v1/emergencias` | ✅ Canónico |
| `/incidentes-admin` | `IncidentesAdminPage.jsx` | `ADMIN_PROPIEDAD`, `PORTERO` | `PROPIEDAD` | `/api/v1/incidentes` | ✅ Canónico |
| `/alertas` | `AlertasPage.jsx` | `ADMIN_PROPIEDAD` | `PROPIEDAD` | `/api/v1/alertas` | ✅ Canónico |
| `/avisos` | `AvisosPage.jsx` | `ADMIN_PROPIEDAD` | `PROPIEDAD` | `/api/v1/comunicados` | ✅ Canónico |
| `/quejas-admin` | `QuejasAdminPage.jsx` | `ADMIN_PROPIEDAD` | `PROPIEDAD` | `/api/v1/pqrs` | ✅ Canónico (PQRS) |
| `/reservas-admin` | `ReservasAdminPage.jsx` | `ADMIN_PROPIEDAD` | `PROPIEDAD` | `/api/v1/reservas` | ✅ Canónico |
| `/coarrendatarios` | `CoarrendatariosPage.jsx` | `ADMIN_PROPIEDAD` | `PROPIEDAD` | `/api/v1/coarrendatarios` | ✅ Canónico |
| *No registrada* | `DocumentosAdminPage.jsx` | N/A | `PROPIEDAD` | `/api/v1/documentos/admin` | ⚠️ Huérfana (debe ser `/documentos`) |

---

## 3. Matriz de Duplicidades e Inconsistencias

### A. Duplicidades de Rutas (Mismo componente registrado dos veces)
1. `/mantenimiento-admin` ⟷ `/mantenimientos` ➔ Canónica: `/mantenimientos`.
2. `/asambleas-admin` ⟷ `/asambleas` ➔ Canónica: `/asambleas`.
3. `/polizas-admin` ⟷ `/polizas` ➔ Canónica: `/polizas`.
4. `/emergencias-admin` ⟷ `/emergencias` ➔ Canónica: `/emergencias`.
5. `/porterias-admin` ⟷ `/porterias` ➔ Canónica: `/porterias`.
6. `/organizaciones` ⟷ `/superadmin/organizaciones` ➔ Canónica: `/superadmin/organizaciones`.
7. `/planes` (protegida) ⟷ `/superadmin/planes` ➔ Canónica: `/superadmin/planes`.
8. `/membresias` ⟷ `/superadmin/membresias` ➔ Canónica: `/superadmin/membresias`.

### B. Inconsistencias de Autorización y Rol
1. **Ruta `/propiedades` en multi-rol:** Permitía a `ADMIN_PROPIEDAD`. Un Administrador de Propiedad solo debe consultar la propiedad a la que está asignado, no un listado de propiedades de la plataforma. La ruta canónica para la organización es `/org/propiedades`, y para plataforma es `/superadmin/propiedades`.
2. **SUPERADMIN en rutas de `ADMIN_ORGANIZACION`:** En `App.jsx`, todas las rutas `/org/*` tienen `roles={['ADMIN_ORGANIZACION', 'SUPERADMIN']}`. Un Superadmin no debe actuar operativamente como organización a menos que simule un assignment explícito en `TenantContext`.
3. **Falta de vistas de Supervisión en `ADMIN_ORGANIZACION`:** Los endpoints de cartera y métricas consolidadas existen en backend, pero faltaban las páginas `/org/cartera`, `/org/reportes` y `/org/analitica`.

---

## 4. Clasificación de Riesgos Técnicos

| ID | Riesgo | Severidad | Causa Raíz | Mitigación |
| :--- | :--- | :--- | :--- | :--- |
| **R-01** | Fuga de contexto cross-tenant en rutas multi-rol | ALTA | Rutas genéricas como `/propiedades` que no forzaban el scope del assignment en el frontend | Eliminar rutas globales promiscuas y exigir prefijo de rol (`/superadmin/`, `/org/`) |
| **R-02** | Rutas duplicadas con desincronización de estado | MEDIA | Coexistencia histórica de sufijos `-admin` con nombres en plural | Definir rutas canónicas y usar `<Navigate replace />` para compatibilidad |
| **R-03** | Módulo de Documentación inaccesible en UI | MEDIA | `DocumentosAdminPage.jsx` existía sin entrada en router ni en AppShell | Enrutar bajo `/documentos` con validación de scope `PROPIEDAD` |
| **R-04** | Confusión semántica de ganancias en PH | BAJA | Módulo `/ganancias` diseñado para empresas comerciales | Ocultar del menú oficial de PH y redirigir hacia `/flujo-caja` o `/presupuestos` |

---

## 5. Recomendaciones para las Siguientes Fases

1. **Fase 1 (Matriz Técnica):** Formalizar `docs/security/SAED_ROLE_PERMISSION_MATRIX.md` estableciendo la relación exacta Rol ⟷ Scope ⟷ Ruta Canónica ⟷ Endpoint.
2. **Fase 2 (Frontend Auth Access Model):** Refactorizar `frontend/src/lib/access.js` con las listas canónicas estrictas por rol, eliminando rutas duplicadas y aplicando `ROLE_DEFINITIONS` con scopes (`GLOBAL`, `ORGANIZACION`, `PROPIEDAD`).
3. **Fase 3 (App.jsx):** Actualizar las rutas protegidas, eliminar duplicados mediante redirects controlados, y agregar `/documentos`.
4. **Fase 4 (AppShell Navigation):** Reestructurar la barra lateral por rol con taxonomías limpias (Superadmin = Plataforma SaaS, Admin Org = Consola Gerencial, Admin Prop = Operación de Copropiedad).
5. **Fase 5 (Vistas de Supervisión Org):** Desarrollar `/org/cartera`, `/org/reportes` y `/org/analitica` consumiendo los datos consolidados reales del backend sin falsear información.
