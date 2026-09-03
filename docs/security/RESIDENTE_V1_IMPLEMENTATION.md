# RESIDENTE V1 — TECHNICAL IMPLEMENTATION SPECIFICATION

## 1. Executive Summary

`RESIDENTE V1` defines the terminal tenant role in the SAED multi-tenant hierarchy:

```
SUPERADMIN (Platform / Global)
     ↓
ADMIN_ORGANIZACION (Organization)
     ↓
ADMIN_PROPIEDAD (Property / Condominio)
     ↓
PORTERO (Physical Security / Entry-Exit Operator)
     ↓
RESIDENTE (Unit & Personal Identity Boundary)
```

The `RESIDENTE` role represents the resident, tenant, or co-owner occupying a specific residential or commercial unit. It enforces a strict **Unit & Identity Boundary**, guaranteeing that residents have access only to their own personal data, their assigned unit, their visitors, their registered vehicles/pets, and their payment/PQRS obligations, while preventing access to administrative property data, cross-unit details, and global SaaS management.

---

## 2. Architectural Principles & Security Boundary

### 2.1 Scope & Context
* **Role Code:** `RESIDENTE`
* **Role Scope:** `UNIDAD`
* **Multi-Tenant Context:** `(ID_USUARIO, ID_ORGANIZACION, ID_PROPIEDAD, ID_UNIDAD, ROLE_RESIDENTE)`
* **Active Token Headers:**
  - `Authorization: Bearer <jwt_token>` (Identity validation)
  - `X-Assignment-Id: <id_asignacion>` (Context resolution verified against `USUARIO_ASIGNACIONES`)

### 2.2 Inviolable Access Matrix

| Domain | Allowed Capabilities (Unit & Identity Scope) | Strictly Prohibited Capabilities (403 Forbidden) |
| :--- | :--- | :--- |
| **Platform** | None | `/api/v1/platform/*` |
| **Organization** | None | `/api/v1/org/*` |
| **Property Admin** | None | `/api/v1/properties`, `/api/v1/assignments`, `/api/v1/units` (Mutate) |
| **Financials** | Own account balance, pay own dues (`/api/v1/residentes/{id}/dashboard`, `/api/v1/pagos`) | General ledgers, cartera, budgets (`/api/v1/cartera`, `/api/v1/presupuestos`, `/api/v1/gastos`, `/api/v1/reportes/*`) |
| **Unit Management** | View own unit details (`GET /api/v1/units/{id}`), view inhabitants | Cross-unit inspection, creating/editing units, assigning roles |
| **Visits & Access** | Program visits for own unit (`POST /api/v1/porteria/visitas`), list unit visits, generate QR | Validate entrance/exit, scan QRs at gate, modify other units' visits |
| **Parcels** | View own parcels (`GET /api/v1/paquetes`) | Receive parcels at gate (`POST /api/v1/paquetes`), deliver parcels (`PATCH /api/v1/paquetes/{id}/entregar`) |
| **Dependents & Assets** | View & register pets (`/api/v1/mascotas`) and vehicles (`/api/v1/vehiculos`) of own unit | View/modify pets/vehicles of other units, assign parking spots (`/api/v1/parqueaderos/*`) |
| **PQRS & Community** | Submit PQRS tickets, view own tickets, report noise | Modify ticket state, view all property tickets (`/api/v1/pqrs/todos`), publish broadcast alerts |

---

## 3. Implementation Details

### 3.1 Backend Controllers & Security Annotations
* **`DependentController.java`:** Added explicit `@PreAuthorize` rules allowing `RESIDENTE` to manage pets, vehicles, tutors, and visitors within their unit context while restricting cross-unit access.
* **`DependentServiceImpl.java` & `DependentRepositoryImpl.java`:** Fixed record getter calls (`request.unidadId()`) and ensured Oracle column key holders (`"ID_MASCOTA"`, `"ID_VEHICULO"`, `"ID_TUTOR"`, `"ID_VISITANTE"`) conform to uppercase SQL standards.
* **`PorteriaController.java`:** Allows residents to program visits (`POST /api/v1/porteria/visitas`) and inspect visits for their unit (`GET /api/v1/porteria/unidades/{unidadId}/visitas`) while rejecting cross-unit operations via anti-IDOR validation.
* **`TicketController.java`:** Enhanced `@PreAuthorize` to ensure residents can view their tickets (`/api/v1/pqrs/mis-tickets`) and submit tickets (`POST /api/v1/pqrs`), while denying status change endpoints (`PUT /api/v1/pqrs/{id}/estado`) and global listing (`GET /api/v1/pqrs/todos`).
* **`DashboardController.java`:** Verified resident financial overview (`GET /api/v1/residentes/{id}/dashboard`) with IDOR checks ensuring `id == currentUserId`.

### 3.2 Database Layer & RLS
* Enforces `PKG_SAED_SECURITY_RLS.FN_FILTRO_UNIDAD` across `MASCOTAS`, `VEHICULOS`, `RESIDENTES_UNIDAD`, and `VISITAS`.
* Context setup via `PKG_SAED_SESSION.SET_CONTEXT(v_usr, v_org, v_prop, 'RESIDENTE')`.

---

## 4. Verification Suite

### 4.1 Adversarial Test Matrix (`ResidenteAdversarialAuthorizationTest.java`)
* **Total Tests:** 44
* **Pass Rate:** 100% (44/44 in GREEN)
* **Categories Tested:**
  1. Unit & Identity Scope operations (unit info, inhabitants, visit scheduling, QR generation, pets, vehicles, parcels, PQRS, notifications).
  2. Cross-Unit Isolation & Anti-IDOR (foreign unit inspection, foreign visit listing, foreign pet/vehicle access, foreign ticket access, foreign financial dashboard).
  3. Gate Operation Restrictions (gate QR validation, entry/exit registration, parcel reception/dispatch).
  4. Global Financial & Operational Restrictions (cartera, reportes morosos, presupuestos, gastos, multas globales, auditoría).
  5. Multi-Tenant Cross-Contamination & Context Switching (foreign assignment hijacking, elevation of privilege).

### 4.2 Full Backend Regression
* **Total Test Suite:** 310 tests
* **Result:** 310 Passing (0 failures, 0 errors, 0 skipped).

### 4.3 Frontend Verification
* **Build Command:** `npm run build`
* **Result:** Succeeded cleanly with zero errors.
