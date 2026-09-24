# F10-03 — Asambleas (Horizontal Property Assemblies & Governance)
**SAED 2.0 — Property Management & Governance Platform**  
*Phase 10: Documentation, Regulations & Property Governance — Module 03*

---

## 1. Executive Summary

Module **F10-03 Asambleas** delivers enterprise-grade statutory governance, assembly lifecycle management, real-time quorum calculation, legal representation proxies, and multi-point deliberative voting for horizontal property co-ownership communities managed under SAED 2.0 in compliance with **Colombian Law 675 of 2001 (Régimen de Propiedad Horizontal)**.

Key capabilities provided:
- **Full Assembly Lifecycle Management:** State machine transitions strictly enforcing valid pathways across `BORRADOR`, `CONVOCADA`, `EN_CURSO`, `EN_RECESO`, `FINALIZADA`, and `CANCELADA`.
- **First & Second Call Scheduling:** Native management of formal assembly calls (`FECHA_HORA_PRIMERA_CONV` and `FECHA_HORA_SEGUNDA_CONV`) with location/video link dispatch, agenda breakdown, and direct integration with F10-01 convocatorias (`ID_DOCUMENTO`).
- **Dynamic Quorum Calculation with Co-ownership Coefficients:** Real-time calculation of active assembly quorum based on exact private property co-ownership coefficients (`COEFICIENTE_COPROPIEDAD` / `COEFICIENTE_PONDERADO`) registered in the property's cadastral registry, accounting for both decimal unit scale (`0.500000`) and percentage scale (`50.00%`) up to precision `NUMBER(5,2)`.
- **Voluntary Attendance Withdrawal:** Instant attendance withdrawal (`HORA_RETIRO`) with immediate quorum recalculation.
- **Law 675 Legal Representation Proxies (`PODERES_REPRESENTACION`):** Unit owners can delegate attendance and voting power to authorized proxies with digital power-of-attorney documents (`DOCUMENTO_PODER_URL`), strict prohibition against self-delegation, anti-duplication constraints per unit, and board review/approval workflow (`PENDIENTE_REVISION`, `APROBADO`, `RECHAZADO`).
- **Deliberative Voting Agenda (`VOTACIONES`, `VOTOS`):** Real-time creation of voting agenda points with required statutory majorities (`SIMPLE`, `CALIFICADA_70`), single-vote per unit constraint (`UQ_VOTOS_VOTACION_UNIDAD`), coefficient weighting, options (`SI`, `NO`, `BLANCO`, `ABSTENCION`), and automatic approval computation upon closing.
- **Multitenant Isolation & Role Confinement:** Enforced via Oracle Virtual Private Database (VPD/RLS) policies on `ASAMBLEAS`, `ASISTENCIAS_ASAMBLEA`, and `PODERES_REPRESENTACION`, Spring Security `@PreAuthorize` scopes, commercial plan gating (`ModuleNotEntitledException`), and strict 403 Forbidden confinement for `PORTERO`.
- **Concurrency & Pessimistic Locking:** `SELECT ... FOR UPDATE` row locks preventing lost updates during concurrent attendee sign-ins, vote tallies, and state transitions.

---

## 2. Relational Schema & Integrity Constraints

### 2.1 Schema Architecture

1. **`ASAMBLEAS`:** Assembly convocations, date/time, agenda, required & reached quorum, lifecycle state, and property mapping.
2. **`ASISTENCIAS_ASAMBLEA`:** Registered attendance log per unit and person with check-in timestamp (`HORA_LLEGADA`), departure timestamp (`HORA_RETIRO`), direct owner flag (`ES_PROPIETARIO_DIRECTO`), and weighted coefficient.
3. **`PODERES_REPRESENTACION`:** Power-of-attorney representation requests, delegating unit owner, designated proxy, attached legal document, review state (`PENDIENTE_REVISION`, `APROBADO`, `RECHAZADO`), and validator audit.
4. **`VOTACIONES`:** Agenda voting items, required majority type (`SIMPLE`, `CALIFICADA_70`), opening/closing timestamps, and state (`CREADA`, `EN_CURSO`, `CERRADA`, `CANCELADA`).
5. **`VOTOS`:** Individual unit ballots cast per voting item, voter identity, selected option (`SI`, `NO`, `BLANCO`, `ABSTENCION`), and cast coefficient.

### 2.2 Canonical Check Constraints & Catalogs

- **Assembly Type (`CK_ASAMBLEAS_TIPO`):** `ORDINARIA`, `EXTRAORDINARIA`, `SEGUNDA_CONVOCATORIA`, `UNIVERSAL`.
- **Assembly Modality (`CK_ASAMBLEAS_MODALIDAD`):** `PRESENCIAL`, `VIRTUAL`, `MIXTA`.
- **Assembly Lifecycle State (`CK_ASAMBLEAS_ESTADO`):** `BORRADOR`, `CONVOCADA`, `EN_CURSO`, `EN_RECESO`, `FINALIZADA`, `CANCELADA`.
- **Proxy Status (`CK_PODERES_ESTADO`):** `PENDIENTE_REVISION`, `APROBADO`, `RECHAZADO`.
- **Voting Item State (`CK_VOTACIONES_ESTADO`):** `CREADA`, `EN_CURSO`, `CERRADA`, `CANCELADA`.
- **Voting Majority Type (`CK_VOTACIONES_MAYORIA`):** `SIMPLE`, `CALIFICADA_70`.
- **Ballot Option (`CK_VOTOS_OPCION`):** `SI`, `NO`, `BLANCO`, `ABSTENCION`.

### 2.3 Uniqueness & Integrity Constraints

```sql
-- Single vote per unit in any voting point
ALTER TABLE VOTOS ADD CONSTRAINT UQ_VOTOS_VOTACION_UNIDAD UNIQUE (ID_VOTACION, ID_UNIDAD);

-- Performance & isolation indexes
CREATE INDEX IX_ASAMBLEAS_PROP ON ASAMBLEAS (ID_PROPIEDAD, ESTADO);
CREATE INDEX IX_ASAMBLEAS_ORG ON ASAMBLEAS (ID_ORGANIZACION);
CREATE INDEX IX_ASISTENCIAS_ASAMBLEA ON ASISTENCIAS_ASAMBLEA (ID_ASAMBLEA, ID_UNIDAD);
CREATE INDEX IX_PODERES_ASAMBLEA ON PODERES_REPRESENTACION (ID_ASAMBLEA, ID_UNIDAD);
CREATE INDEX IX_VOTACIONES_ASAMBLEA ON VOTACIONES (ID_ASAMBLEA, ESTADO);
CREATE INDEX IX_VOTOS_VOTACION ON VOTOS (ID_VOTACION, ID_UNIDAD);
```

---

## 3. Security & Multitenancy Architecture

### 3.1 Oracle Virtual Private Database (VPD / RLS)
- Policies registered:
  - `POL_RLS_PROP_ASAMBLEAS` on `ASAMBLEAS`.
  - `POL_RLS_PROP_ASISTENCIAS` on `ASISTENCIAS_ASAMBLEA` (subquery filter via parent assembly).
  - `POL_RLS_PROP_PODERES` on `PODERES_REPRESENTACION` (subquery filter via parent assembly).
- Commercial module entitlement enforced via `ModuleEntitlementService.checkEntitlement("ASAMBLEAS")`:
  - `FREE` (Plan 1): Blocked with `ModuleNotEntitledException` (HTTP 403 Forbidden).
  - `PRO` (Plan 2): Blocked with `ModuleNotEntitledException` (HTTP 403 Forbidden).
  - `ENTERPRISE` (Plan 3): Permitted with full statutory governance capabilities.

### 3.2 Role Matrix

| Endpoint / Operation | SUPERADMIN | ADMIN_ORGANIZACION | ADMIN_PROPIEDAD | RESIDENTE / PROPIETARIO | PORTERO |
| :--- | :---: | :---: | :---: | :---: | :---: |
| `GET /api/v1/asambleas` | Yes | Yes (Org props) | Yes (Own prop) | Yes (Own prop) | No (403) |
| `GET /api/v1/asambleas/{id}` | Yes | Yes | Yes | Yes (Own prop) | No (403) |
| `POST /api/v1/asambleas` | Yes | Yes | Yes | No (403) | No (403) |
| `PUT /api/v1/asambleas/{id}` | Yes | Yes | Yes | No (403) | No (403) |
| `PUT /api/v1/asambleas/{id}/estado` | Yes | Yes | Yes | No (403) | No (403) |
| `POST /api/v1/asambleas/{id}/asistencias` | Yes | Yes | Yes | No (403) | No (403) |
| `PUT /api/v1/asambleas/{id}/asistencias/{u}/retiro` | Yes | Yes | Yes | No (403) | No (403) |
| `GET /api/v1/asambleas/{id}/quorum` | Yes | Yes | Yes | Yes (Own prop) | No (403) |
| `POST /api/v1/asambleas/{id}/poderes` | Yes | Yes | Yes | Yes (Radica) | No (403) |
| `PUT /api/v1/asambleas/{id}/poderes/{p}/decision` | Yes | Yes | Yes | No (403) | No (403) |
| `POST /api/v1/asambleas/{id}/votaciones` | Yes | Yes | Yes | No (403) | No (403) |
| `POST /api/v1/asambleas/{id}/votaciones/{v}/votar`| Yes | Yes | Yes | Yes (Own unit) | No (403) |
| `PUT /api/v1/asambleas/{id}/votaciones/{v}/cerrar`| Yes | Yes | Yes | No (403) | No (403) |

---

## 4. REST API Contract Specifications

### 4.1 Assemblies Management
- `GET /api/v1/asambleas`: Lists assemblies for the caller's property context.
- `GET /api/v1/asambleas/{id}`: Full assembly detail including attendees count and voting points.
- `POST /api/v1/asambleas`: Creates an assembly (`BORRADOR` or `CONVOCADA`). Returns HTTP 201 Created.
- `PUT /api/v1/asambleas/{id}`: Updates assembly metadata (permitted only in `BORRADOR` or `CONVOCADA`).
- `PUT /api/v1/asambleas/{id}/estado`: Triggers a lifecycle transition (e.g. `CONVOCADA` -> `EN_CURSO` -> `FINALIZADA`).

### 4.2 Attendance & Quorum
- `POST /api/v1/asambleas/{id}/asistencias`: Registers unit attendance, dynamically updating reached quorum.
- `PUT /api/v1/asambleas/{id}/asistencias/{idUnidad}/retiro`: Marks departure time and updates reached quorum.
- `GET /api/v1/asambleas/{id}/quorum`: Returns live quorum metrics:
  ```json
  {
    "idAsamblea": 154,
    "quorumRequeridoPct": 50.01,
    "quorumAlcanzadoPct": 55.00,
    "tieneQuorum": true,
    "totalUnidadesRegistradas": 2,
    "estadoAsamblea": "EN_CURSO"
  }
  ```

### 4.3 Powers of Attorney (Ley 675)
- `GET /api/v1/asambleas/{id}/poderes`: Lists submitted representation proxies.
- `POST /api/v1/asambleas/{id}/poderes`: Submits a representation proxy (`PENDIENTE_REVISION`).
- `PUT /api/v1/asambleas/{id}/poderes/{idPoder}/decision`: Approves or rejects representation proxy (`APROBADO` / `RECHAZADO`).

### 4.4 Voting Items & Deliberations
- `GET /api/v1/asambleas/{id}/votaciones`: Lists voting items with aggregate tallies (`VOTOS_SI`, `VOTOS_NO`, `VOTOS_BLANCO`, `VOTOS_ABSTENCION`).
- `POST /api/v1/asambleas/{id}/votaciones`: Adds a voting item to the assembly agenda.
- `POST /api/v1/asambleas/{id}/votaciones/{idVotacion}/votar`: Casts an atomic vote for a unit.
- `PUT /api/v1/asambleas/{id}/votaciones/{idVotacion}/cerrar`: Closes the voting item and evaluates statutory majority.

---

## 5. Frontend Interfaces

1. **Administration Dashboard (`AsambleasAdminPage.jsx`):**
   - Live assembly status cards with badges.
   - Real-time quorum gauge progress bar.
   - Attendance check-in and withdrawal table.
   - Powers of attorney validation modal.
   - Voting point creation and live tally monitors.
2. **Resident Portal (`ResAsambleasPage.jsx`):**
   - Active and scheduled convocations tab.
   - Assembly convocation PDF download link (powered by F10-01).
   - Real-time quorum indicator.
   - Representation proxy delegation modal with PDF upload.
   - Historical assembly minutes and voting outcomes tab.

---

## 6. Automated Verification Matrix

| Suite | Tests Run | Result | Coverage Scope |
| :--- | :---: | :---: | :--- |
| `F10AsambleasIntegrationTest` | 13 | **BUILD SUCCESS** | Full lifecycle, attendance, quorum decimal/percentage, proxies, voting, multitenant isolation, portero confinement, audit logging, concurrency. |
| `AsambleasIntegrationTest` | 2 | **BUILD SUCCESS** | Baseline workflow verification. |
| `ModuleEntitlementsSecurityTest` | 18 | **BUILD SUCCESS** | Commercial plan gating (`FREE`, `PRO` vs `ENTERPRISE`). |
| `F10ReglamentosIntegrationTest` | 27 | **BUILD SUCCESS** | F10-02 zero-regression certification. |
| `F10DocumentosIntegrationTest` | 16 | **BUILD SUCCESS** | F10-01 zero-regression certification. |
| Frontend `npm run build` | - | **SUCCESS** | Vite bundle compiled cleanly in 8.71s (`ResAsambleasPage` included). |
