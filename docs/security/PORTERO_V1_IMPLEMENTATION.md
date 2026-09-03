# SAED — PORTERO V1 ARCHITECTURAL IMPLEMENTATION SPECIFICATION

> **Status:** 🟢 CERTIFIED  
> **Release Target:** PORTERO V1  
> **Hierarchy Order:** SUPERADMIN (Global) $\longrightarrow$ ADMIN_ORGANIZACION (Org) $\longrightarrow$ ADMIN_PROPIEDAD (Property) $\longrightarrow$ **PORTERO (Property Operator)** $\longrightarrow$ RESIDENTE  
> **Repository:** `SAED` (Spring Boot 3 + Oracle Autonomous Database + React 18 / Vite / TailwindCSS / Radix UI)

---

## 1. Executive Architecture Summary

`PORTERO V1` represents the operational frontline security and reception role in SAED's multi-tenant SaaS architecture.

```text
SAED PLATFORM
      │
      ├── SUPERADMIN (Global / Platform Scope - FROZEN)
      │
      └── CLIENTES SAED (Organizaciones)
             │
             ├── ADMIN_ORGANIZACION (Organization Scope - CERTIFIED)
             │
             └── PROPIEDADES / COPROPIEDADES
                    │
                    ├── ADMIN_PROPIEDAD (Property Scope - CERTIFIED)
                    │
                    ├── PORTERO (Frontline Security Operator - CERTIFIED)
                    │
                    └── RESIDENTES / UNIDADES
```

### Core Security Principle
**PORTERO is an operational security and reception operator, NOT a property administrator.**
- **Granted (Frontline Operations):** Check-in/Check-out of visitors, QR access code validation, package/mail management with PIN verification, visitor vehicle entry/exit tracking, read-only inspection of units and inhabitants to announce arrivals, query visitor history.
- **Denied by Default (Administrative Separation):** Platform console (`/api/v1/platform/*`), organization console (`/api/v1/org/*`), unit/property mutations (`POST/PUT /api/v1/units`, `/properties`), resident/user/role administration (`/api/v1/assignments`, `/usuarios`), finances and accounting (`/cuotas`, `/cartera`, `/pagos`), penalties and fines (`/multas`), administrative complaints (`/pqrs`, `/quejas/todas`), contracts and insurance policies (`/contratos`, `/seguros`).

---

## 2. Authorization & Context Flow

The multi-property assignment context flow operates under a zero-trust model:

$$\text{Bearer JWT} \xrightarrow{\text{X-Assignment-Id}} \text{JwtAuthenticationFilter} \xrightarrow{\text{validateAssignment}} \text{SaedContext} \xrightarrow{\text{ThreadLocal}} \text{SaedDataSourceProxy} \xrightarrow{\text{SET\_CONTEXT}} \text{Oracle VPD (RLS)}$$

1. **Token Authentication:** Client provides `Authorization: Bearer <token>` and `X-Assignment-Id: <id>`.
2. **Assignment Validation:** `AssignmentService.validateAssignment` ensures the assignment is active, belongs to the authenticated user (`ID_USUARIO = 3`), and carries role `PORTERO` (`ID_ROL = 4`, `alcance = 'PROPIEDAD'`).
3. **Context Population:** `SaedContext` is loaded with `userId=3`, `organizationId=1`, `propertyId=1`, `roleCode='PORTERO'`, `roleScope='PROPIEDAD'`.
4. **Oracle RLS Binding:** `SaedDataSourceProxy` executes `PKG_SAED_SESSION.SET_CONTEXT(v_usr, v_org, v_prop, 'PORTERO')` before executing any query on the pooled connection.
5. **Context Cleanup:** `SaedDataSourceProxy` clears context in a `finally` block preventing connection pool contamination.

---

## 3. Oracle VPD / Row-Level Security Matrix

| Table | RLS Policy | Predicate Applied for PORTERO |
| :--- | :--- | :--- |
| `ORGANIZACIONES` | `POL_RLS_GLOBAL` | Read allowed for assigned org (`id_organizacion = v_org`), mutations return `1=0`. |
| `PROPIEDADES` | `POL_RLS_PROP_PROPIEDADES` | `id_propiedad = v_prop` (isolated to active assigned guardhouse). |
| `UNIDADES` | `POL_RLS_PROP_UNIDADES` | `id_propiedad = v_prop` (can inspect units to direct visitors/packages). |
| `VISITAS` | `POL_RLS_PROP_VISITAS` | `id_unidad IN (SELECT id_unidad FROM UNIDADES WHERE id_propiedad = v_prop)`. |
| `QR_ACCESOS` | `POL_RLS_UNI_QR_ACCESOS` | `id_visita IN (SELECT id_visita FROM VISITAS JOIN UNIDADES ON VISITAS.id_unidad = UNIDADES.id_unidad WHERE UNIDADES.id_propiedad = v_prop)`. |
| `REGISTROS_ACCESO` | `POL_RLS_PROP_REGISTROS` | `id_propiedad = v_prop`. |
| `VEHICULOS_VISITA` | `POL_RLS_PROP_VEHICULOS` | `id_visita IN (SELECT id_visita FROM VISITAS JOIN UNIDADES ON VISITAS.id_unidad = UNIDADES.id_unidad WHERE UNIDADES.id_propiedad = v_prop)`. |
| `PAQUETES` | `POL_RLS_PROP_PAQUETES` | `id_propiedad = v_prop`. |
| `PARQUEADEROS` | `POL_RLS_PROP_PARQUEADEROS` | `id_propiedad = v_prop`. |
| `CUOTAS` / `CARTERA` | `POL_RLS_PROP_FINANZAS` | Controller-level `403 Forbidden` (`SCOPE_ADMIN_PROPIEDAD`). |
| `CONTRATOS` / `POLIZAS` | `POL_RLS_PROP_LEGAL` | Controller-level `403 Forbidden` (`SCOPE_ADMIN_PROPIEDAD`). |

---

## 4. API Endpoints & Permission Matrix

### 4.1 Frontline Security & Reception (Permitted)
- `GET /api/v1/units` & `GET /api/v1/units/{id}`: `@PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_PORTERO', 'SCOPE_RESIDENTE')")`
- `GET /api/v1/units/{id}/residents` & `/owners`: `@PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_PORTERO', 'SCOPE_RESIDENTE')")`
- `GET /api/v1/personas` & `GET /api/v1/personas/{id}`: `@PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD', 'SCOPE_PORTERO')")`
- `POST /api/v1/visitantes`: `@PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_PORTERO', 'SCOPE_RESIDENTE')")`
- `POST /api/v1/porteria/visitas`: `@PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_PORTERO', 'SCOPE_RESIDENTE')")`
- `GET /api/v1/porteria/visitas/{id}`: `@PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_PORTERO', 'SCOPE_RESIDENTE')")`
- `GET /api/v1/porteria/visitas-resumen`: `@PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_PORTERO')")`
- `GET /api/v1/porteria/visitas/historial`: `@PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_PORTERO')")`
- `PUT /api/v1/porteria/visitas/{id}/salida`: `@PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_PORTERO')")`
- `POST /api/v1/porteria/registros/entrada` & `/salida`: `@PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_PORTERO')")`
- `GET /api/v1/porteria/propiedades/{id}/registros`: `@PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_PORTERO')")`
- `POST /api/v1/porteria/qr/validar`: `@PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_PORTERO')")`
- `POST /api/v1/porteria/vehiculos`: `@PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_PORTERO')")`
- `POST /api/v1/porteria/vehiculos/{id}/salida`: `@PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_PORTERO')")`
- `GET /api/v1/paquetes` & `GET /api/v1/paquetes/{id}`: `@PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_PORTERO', 'SCOPE_RESIDENTE')")`
- `POST /api/v1/paquetes`: `@PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_PORTERO')")`
- `POST /api/v1/paquetes/{id}/entrega`: `@PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_PORTERO')")`
- `GET /api/v1/parqueaderos`: `@PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_PORTERO', 'SCOPE_RESIDENTE')")`
- `GET /api/v1/parqueaderos/asignaciones`: `@PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_PORTERO', 'SCOPE_RESIDENTE')")`

### 4.2 Administrative Guardrails (Strict 403 Forbidden)
- Platform management (`/api/v1/platform/*`): Denied
- Organization management (`/api/v1/org/*`, `/organizations`): Denied
- Property mutations (`POST /api/v1/properties`, status toggle): Denied
- Unit mutations (`POST /api/v1/units`, `PUT /api/v1/units/{id}`): Denied
- Resident/Owner mutations (`POST /api/v1/units/{id}/residents`, `/owners`): Denied
- Persona mutations (`POST/PUT/DELETE /api/v1/personas`): Denied
- Finances (`/api/v1/cuotas`, `/api/v1/cartera`, `/api/v1/pagos`): Denied
- Sanctions & Fines (`/api/v1/multas/*`): Denied
- Complaints & PQRS administrative resolution (`/api/v1/quejas/todas`, `/api/v1/pqrs/todos`): Denied
- Contracts & Insurance (`/api/v1/contratos`, `/api/v1/seguros/polizas`): Denied
- Role assignments (`POST /api/v1/assignments`, `PATCH /api/v1/assignments/{id}/status`): Denied

---

## 5. Frontend Operator Experience

- **Entry Point:** `ROLE_HOME.PORTERO = '/portero-dashboard'`
- **Allowed Routes (`access.js`):**
  - `/portero-dashboard` (Real-time monitoring of shifts, visitors, packages, vehicles)
  - `/visitas` (Visitor registry, check-in, check-out, history)
  - `/paquetes` (Package intake, PIN-protected resident delivery)
  - `/parqueaderos` (Visitor parking spot lookup)
  - `/escanner-qr` (Real-time camera and QR scanner validation)
- **Navigation Layout (`AppShell.jsx`):** Focused on operational tools; administrative settings, financial portals, and platform tabs are omitted from the UI.
