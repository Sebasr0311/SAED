# CANONICALIZACIÓN DE RUTAS — SAED 2.0
**Documento Técnico de Consolidación de Rutas y Redirecciones**  
**Fecha:** 2026-09-10  
**Versión:** 1.0  
**Referencia:** Fase 9 y Fase 3 del Plan Maestro de Reorganización de Roles

---

## 1. Principio Rector

Cada módulo funcional del negocio en SAED 2.0 posee **UNA Y SOLO UNA RUTA CANÓNICA**.  
Las rutas duplicadas, variantes históricas con sufijo `-admin` o nombres ambiguos quedan deprecadas y redirigen mediante HTTP 301 client-side (`<Navigate replace />`) para garantizar cero enlaces rotos hacia marcadores o flujos previos.

---

## 2. Matriz de Canonicalización y Redirecciones

| Ruta Histórica / Alias | Ruta Canónica Oficial | Rol Destino | Justificación Arquitectónica |
| :--- | :--- | :--- | :--- |
| `/organizaciones` | `/superadmin/organizaciones` | `SUPERADMIN` | Las organizaciones son una entidad de nivel SaaS Plataforma. |
| `/planes` (autenticado) | `/superadmin/planes` | `SUPERADMIN` | El catálogo comercial SaaS le pertenece a la consola global. |
| `/membresias` | `/superadmin/membresias` | `SUPERADMIN` | La gestión de licencias es responsabilidad exclusiva de plataforma. |
| `/propiedades` | `/org/propiedades` (o `/superadmin/propiedades`) | `ADMIN_ORG` / `SUPERADMIN` | Una ruta promiscua `/propiedades` permitía a `ADMIN_PROPIEDAD` ver un listado que viola el aislamiento de copropiedad. Se utiliza redirección contextual. |
| `/apartamentos` | `/unidades` | `ADMIN_PROPIEDAD` | El término canónico del dominio según el modelo relacional es `UNIDADES`. |
| `/mantenimiento-admin` | `/mantenimientos` | `ADMIN_PROPIEDAD` | Estandarización a plural sustantivo sin sufijo `-admin`. |
| `/asambleas-admin` | `/asambleas` | `ADMIN_PROPIEDAD` | Estandarización a plural sustantivo sin sufijo `-admin`. |
| `/polizas-admin` | `/polizas` | `ADMIN_PROPIEDAD` | Estandarización a plural sustantivo sin sufijo `-admin`. |
| `/emergencias-admin` | `/emergencias` | `ADMIN_PROPIEDAD` | Estandarización a plural sustantivo sin sufijo `-admin`. |
| `/porterias-admin` | `/porterias` | `ADMIN_PROPIEDAD` | Estandarización a plural sustantivo sin sufijo `-admin`. |
| `/ganancias` | `/flujo-caja` | `ADMIN_PROPIEDAD` | En copropiedades bajo régimen de PH (Ley 675) no existen "utilidades comerciales"; el concepto correcto es flujo de caja y ejecución presupuestal. |
| *(Sin ruta previa)* | `/documentos` | `ADMIN_PROPIEDAD` | Habilitación de la vista huérfana `DocumentosAdminPage.jsx` respaldada por `/api/v1/documentos/admin`. |
| *(Nuevas)* | `/org/cartera`, `/org/reportes`, `/org/analitica` | `ADMIN_ORGANIZACION` | Módulos de supervisión gerencial consolidados consumiendo el backend existente de organización. |

---

## 3. Comportamiento de Redirección Contextual de `/propiedades`

Al ingresar a `/propiedades`:
- Si el usuario activo es `SUPERADMIN` ➔ Redirige a `/superadmin/propiedades`.
- Si el usuario activo es `ADMIN_ORGANIZACION` ➔ Redirige a `/org/propiedades`.
- Si el usuario activo es `ADMIN_PROPIEDAD` ➔ Redirige a `/dashboard` (su consola operativa local).
- Otros roles ➔ Redirigen a su correspondiente `ROLE_HOME`.
