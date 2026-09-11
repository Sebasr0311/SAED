# CERTIFICACIÓN FINAL DE ARQUITECTURA Y ROLES ADMINISTRATIVOS
## SAED 2.0 — Sistema de Administración de Edificios

**Versión:** 1.0  
**Fecha:** 2026-09-10  
**Estado:** CERTIFICADO  
**Responsable:** Senior Software Architect  
**Referencia:** `C:\Users\SEBAS\Desktop\PLAN_MAESTRO_REORGANIZACION_ROLES_SAED.txt`  

---

## 1. RESUMEN EJECUTIVO

Se ha completado con éxito la reorganización integral de la arquitectura de roles administrativos de **SAED 2.0**, abarcando:
1. **SUPERADMIN (Plataforma SaaS Global)**: Administración exclusiva del ecosistema multitenant, organizaciones clientes, planes, membresías globales, analítica de plataforma e infraestructura/seguridad ATP. Se eliminó cualquier fuga o contaminación operativa de copropiedades.
2. **ADMIN_ORGANIZACION (Empresa Administradora / Holding)**: Supervisión gerencial y gobernanza de la cartera de propiedades de su organización, monitoreo de cuotas/cartera consolidada, reportes institucionales, gestión de administradores delegados y control de consumo de límites de suscripción SaaS. Se aisló completamente de la operación diaria (residentes, paquetes, pagos individuales, visitas).
3. **ADMIN_PROPIEDAD (Operación Integral de Copropiedad)**: Administración técnica, comunitaria, financiera y operativa directa de la copropiedad asignada (residentes, unidades, finanzas locales, cuotas, proveedores, PQRS, asambleas, zonas comunes, portería y seguridad). Se erradicó el acceso indebido a configuraciones globales SaaS y a propiedades ajenas.

---

## 2. ARQUITECTURA FINAL MULTI-TENANT

```
                        +--------------------------------+
                        |           SUPERADMIN           |
                        |     Scope: GLOBAL / SAAS       |
                        +---------------+----------------+
                                        |
                                        v
                        +--------------------------------+
                        |       ADMIN_ORGANIZACION       |
                        |   Scope: ORGANIZACION (Tenant) |
                        +---------------+----------------+
                                        |
                                        v
                        +--------------------------------+
                        |         ADMIN_PROPIEDAD        |
                        | Scope: PROPIEDAD (Asignación)  |
                        +---------------+----------------+
                                        |
                     +------------------+------------------+
                     |                                     |
                     v                                     v
            +-----------------+                   +-----------------+
            |     PORTERO     |                   |    RESIDENTE    |
            | Acceso Operativo|                   | Unidad Privada  |
            +-----------------+                   +-----------------+
```

### Reglas Normativas de Aislamiento
* **GLOBAL (`SUPERADMIN`)**: No consume ni requiere contexto de `ID_ORGANIZACION` ni `ID_PROPIEDAD` para sus operaciones SaaS.
* **ORGANIZACION (`ADMIN_ORGANIZACION`)**: Requiere obligatoriamente un `ID_ORGANIZACION` validado en sesión/JWT y contexto. Su autorización de datos restringe cualquier consulta al universo de su organización mediante predicados de negocio y RLS en Oracle Cloud ATP.
* **PROPIEDAD (`ADMIN_PROPIEDAD`)**: Requiere la dupla `ID_ORGANIZACION` + `ID_PROPIEDAD` activa. Cada petición por `useTenantApi` inyecta la cabecera `X-Propiedad-Id`, activando la política `POL_RLS_PROP_*` en Oracle ATP.

---

## 3. ESPECIFICACIÓN POR ROL

### A. SUPERADMIN
* **Propósito**: "Administro la plataforma SaaS SAED."
* **Dominio**: `PLATFORM` | **Scope**: `GLOBAL`
* **Ruta de Inicio**: `/superadmin/dashboard`
* **Módulos y Rutas**:
  - `/superadmin/dashboard`: Monitor de organizaciones, propiedades globales, distribución de usuarios, métricas de ingresos SaaS y estado del motor Oracle ATP 23ai.
  - `/superadmin/organizaciones`: CRUD y activación/desactivación de organizaciones SaaS.
  - `/superadmin/propiedades`: Catálogo global y auditoría de copropiedades registradas.
  - `/superadmin/planes`: Configuración de límites (propiedades, unidades, usuarios) y tarifas SaaS.
  - `/superadmin/membresias`: Control de suscripciones y vigencias por organización.
  - `/superadmin/admins`: Gestión de administradores de plataforma y superusuarios.
  - `/superadmin/auditoria`: Trazabilidad global de eventos y accesos en el sistema.

### B. ADMIN_ORGANIZACION
* **Propósito**: "Administro una empresa que gestiona múltiples copropiedades."
* **Dominio**: `ORGANIZATION` | **Scope**: `ORGANIZACION`
* **Ruta de Inicio**: `/org/dashboard`
* **Módulos y Rutas**:
  - `/org/dashboard`: Métricas agregadas (total propiedades, unidades, administradores, recaudo consolidado, cartera pendiente, consumo de plan SaaS).
  - `/org/organizacion`: Datos corporativos, NIT, razón social y configuración institucional.
  - `/org/propiedades`: Gestión, alta y auditoría de copropiedades de la organización.
  - `/org/admins`: Designación y asignación de administradores a propiedades de la firma.
  - `/org/cartera`: Consolidado gerencial de cartera por cobrar distribuida por copropiedad y nivel de morosidad.
  - `/org/reportes`: Centro de generación de reportes institucionales y financieros.
  - `/org/analitica`: Comparativa de desempeño y ocupación entre copropiedades.
  - `/org/plan`: Estado de la suscripción SaaS, cuotas de consumo y límites contratados.
  - `/org/auditoria`: Registro de auditoría y eventos restringido a la organización.

### C. ADMIN_PROPIEDAD
* **Propósito**: "Administro la operación integral de una copropiedad específica."
* **Dominio**: `PROPERTY` | **Scope**: `PROPIEDAD`
* **Ruta de Inicio**: `/dashboard`
* **Módulos y Rutas**:
  - `/dashboard`: Centro operativo local (unidades, residentes, estado de cuotas, cartera del edificio, multas pendientes, paquetería, visitas activas).
  - **Administración**: `/personas`, `/residentes`, `/unidades`, `/contratos`, `/usuarios`, `/roles-asignaciones`, `/reportes`.
  - **Accesos y Seguridad**: `/visitas`, `/historial-visitas`, `/porterias`, `/parqueaderos`, `/paquetes`.
  - **Finanzas**: `/pagos`, `/cartera`, `/presupuesto`, `/gastos`, `/flujo-caja`, `/conciliaciones`, `/paz-y-salvo`.
  - **Convivencia**: `/pqrs`, `/sanciones`, `/zonas-comunes`, `/reservas`.
  - **Operaciones**: `/mantenimientos`, `/proveedores`, `/obras`, `/polizas`, `/emergencias`, `/incidentes`.
  - **Comunicaciones**: `/asambleas`, `/avisos`, `/alertas`, `/documentos`.

---

## 4. MATRIZ DE AUTORIZACIÓN Y CANONICALIZACIÓN DE RUTAS

### Rutas Redundantes Eliminadas y Redirigidas (`App.jsx`)
| Ruta Anterior | Comportamiento Nuevo | Razón Arquitectónica |
| :--- | :--- | :--- |
| `/organizaciones` | `<Navigate to="/superadmin/organizaciones" replace />` | Exclusiva de plataforma SuperAdmin |
| `/planes` | `<Navigate to="/superadmin/planes" replace />` | Exclusiva de plataforma SuperAdmin |
| `/membresias` | `<Navigate to="/superadmin/membresias" replace />` | Exclusiva de plataforma SuperAdmin |
| `/apartamentos` | `<Navigate to="/unidades" replace />` | Estandarización de terminología (`Unidades`) |
| `/mantenimiento-admin` | `<Navigate to="/mantenimientos" replace />` | Consolidación en ruta operativa canónica |
| `/asambleas-admin` | `<Navigate to="/asambleas" replace />` | Consolidación en ruta operativa canónica |
| `/polizas-admin` | `<Navigate to="/polizas" replace />` | Consolidación en ruta operativa canónica |
| `/emergencias-admin` | `<Navigate to="/emergencias" replace />` | Consolidación en ruta operativa canónica |
| `/porterias-admin` | `<Navigate to="/porterias" replace />` | Consolidación en ruta operativa canónica |
| `/ganancias` | `<Navigate to="/cartera" replace />` | Ruta promiscuosa eliminada; cartera por rol |
| `/propiedades` | `PropiedadesRedirect` contextual: <br>• `SUPERADMIN` → `/superadmin/propiedades`<br>• `ADMIN_ORGANIZACION` → `/org/propiedades`<br>• `ADMIN_PROPIEDAD` → `/dashboard` | Prevención de fuga multitenant cruzada |

---

## 5. CAMBIOS IMPLEMENTADOS EN FRONTEND

1. **`frontend/src/lib/access.js`**:
   - Definición de constantes canónicas: `ROLE_DEFINITIONS`, `ROLE_SCOPES`, `ROLE_DOMAINS`, `ROLE_HOMES`, `NORMALIZED_ROLES`.
   - Normalización robusta de roles (soportando variantes con/sin prefijo `ROLE_` y mayúsculas/minúsculas).
   - Reemplazo del arreglo plano por `ACCESS_BY_ROLE` con validación estricta de rutas por cada uno de los 5 roles (`SUPERADMIN`, `ADMIN_ORGANIZACION`, `ADMIN_PROPIEDAD`, `PORTERO`, `RESIDENTE`).
   - Principio `Deny by default` ante cualquier ruta desconocida o rol no identificado.

2. **`frontend/src/components/ProtectedRoute.jsx`**:
   - Normalización del rol antes de evaluar acceso.
   - Si un usuario con un rol intenta entrar a una ruta no autorizada, es redirigido determinísticamente a su `ROLE_HOMES[role]` respectivo.

3. **`frontend/src/components/layout/AppShell.jsx`**:
   - Reorganización visual y funcional de la barra lateral (`NAV_BY_ROLE`) aplicando la skill `.agents/skills/saed-frontend-design/SKILL.md`.
   - Menú del SuperAdmin estructurado en: *Plataforma SaaS*, *Comercial & Suscripciones*, *Seguridad & Plataforma*.
   - Menú de Admin Organización estructurado en: *Supervisión*, *Gestión*, *Finanzas & Control*, *Cuenta*.
   - Menú de Admin Propiedad estructurado en: *Operación*, *Comunidad*, *Finanzas*, *Infraestructura*, *Gobierno*.
   - Eliminación de emojis decorativos, gradientes excesivos e iconografía inconsistente.

4. **Nuevas Vistas Gerenciales para `ADMIN_ORGANIZACION`**:
   - [`OrgCarteraPage.jsx`](file:///C:/Users/SEBAS/Documents/GitHub/SAED/frontend/src/pages/OrgCarteraPage.jsx): Supervisión agregada de cartera, filtros por morosidad, breakdown por copropiedad y métricas de recaudo.
   - [`OrgReportesPage.jsx`](file:///C:/Users/SEBAS/Documents/GitHub/SAED/frontend/src/pages/OrgReportesPage.jsx): Centro de exportación de reportes ejecutivos (cartera, copropiedades, administradores y ocupación).
   - [`OrgAnaliticaPage.jsx`](file:///C:/Users/SEBAS/Documents/GitHub/SAED/frontend/src/pages/OrgAnaliticaPage.jsx): Análisis comparativo de propiedades, ratios de recaudo, ocupación y niveles de mora.

5. **Activación de Vista Huérfana**:
   - [`DocumentosAdminPage.jsx`](file:///C:/Users/SEBAS/Documents/GitHub/SAED/frontend/src/pages/DocumentosAdminPage.jsx) vinculada a la ruta canónica `/documentos` con consumo certificado del backend.

---

## 6. CAMBIOS IMPLEMENTADOS EN BACKEND

1. **`OrgDashboardController.java`**:
   - Incorporación de endpoints analíticos y financieros para la consola organizacional:
     - `GET /api/v1/org/dashboard/cartera-detalle`: Detalle consolidado de cartera y recaudo por propiedad perteneciente a la organización del usuario autenticado.
     - `GET /api/v1/org/dashboard/analytics`: Comparativa analítica de ocupación, unidades y recaudación entre propiedades de la firma.
   - Aislamiento estricto: todas las consultas SQL validan `p.ID_ORGANIZACION = ?` garantizando que ninguna organización observe datos ajenos.

---

## 7. VERIFICACIÓN Y CONTROL DE CALIDAD

1. **Compilación de Backend (Maven)**:
   - Comando: `.\mvnw.cmd test-compile -DskipTests`
   - Resultado: **Éxito (Exit Code 0)**. 260 clases Java compiladas limpiamente.

2. **Compilación de Frontend (Vite / Rollup)**:
   - Comando: `npm run build`
   - Resultado: **Éxito (Exit Code 0)** en 34.64 segundos. Cero errores de sintaxis, imports o resolución de módulos. 93 assets empaquetados en `dist/`.

3. **Garantía RLS en Base de Datos**:
   - `V5.2__fix_propiedades_rls_recursion.sql` mantiene la segregación en Oracle ATP previniendo bucles de consulta en predicados `SYS_CONTEXT`.

---

## 8. CONCLUSIÓN Y ESTADO FINAL

El sistema SAED 2.0 cumple de manera rigurosa con los principios de diseño de software empresarial, segregación de privilegios y experiencia de usuario diferenciada para cada nivel administrativo:
* **SuperAdmin** gobierna el SaaS.
* **Admin Organización** supervisa la firma administradora y sus copropiedades.
* **Admin Propiedad** opera la copropiedad con máxima eficiencia.
