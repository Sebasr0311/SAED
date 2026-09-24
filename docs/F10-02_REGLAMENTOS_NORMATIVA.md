# F10-02 — Reglamentos y Normativa (Property Regulations & Bylaws)
**SAED 2.0 — Property Management & Governance Platform**  
*Phase 10: Documentation, Regulations & Property Governance — Module 02*

---

## 1. Executive Summary

Module **F10-02 Reglamentos y Normativa** provides enterprise-grade regulatory lifecycle management, bylaw governance, and resident access control for residential properties and horizontal property communities managed under SAED 2.0.

Built natively on top of the hardened **F10-01 Gestión Documental** core:
- **Zero Parallel Storage:** Completely reuses F10-01's file storage infrastructure (`DOCUMENTOS`, `VERSIONES_DOCUMENTO`, and `FileStorageService`). Binary attachments, SHA-256 integrity verification, storage quotas, and byte-level streaming downloads are delegated to F10-01.
- **Engine-Level Single Active Publication Rule:** Enforces that at most **one** regulation of each normative type can be in `PUBLICADO` state per property at any given time. This invariant is enforced simultaneously at three tiers:
  1. **Database Engine:** Via conditional unique index `UQ_REGLAM_PROP_TIPO_VIG`.
  2. **Concurrency Control:** Via pessimistic locking (`SELECT ID_PROPIEDAD FROM PROPIEDADES WHERE ID_PROPIEDAD = ? FOR UPDATE` and `SELECT ID_REGLAMENTO ... FOR UPDATE`).
  3. **Application Service:** Via atomic transaction orchestration (`ReglamentoServiceImpl.publicar()`).
- **Atomic State Transitions:** Publishing a new regulation automatically archives existing active versions as `REEMPLAZADO`, updates document visibility flags (`DOCUMENTOS.ES_PUBLICO_RESIDENTES = 'S'`), and writes immutable audit entries in `AUDITORIA_LOG`.
- **Multitenant Isolation & Role Confinement:** Enforced via Oracle Virtual Private Database (RLS/VPD) policies (`POL_RLS_PROP_REGLAMENTOS`), Spring Security authorities (`@PreAuthorize`), and strict tenant checks. Residents only access published regulations for their property; portiers (`PORTERO`) face strict 403 Forbidden confinement across all endpoints.

---

## 2. Relational Schema & Integrity Constraints

### 2.1 Table `REGLAMENTOS_NORMATIVA`
Stores regulatory metadata, lifecycle states, and normative classifications per property.

| Column | Type | Constraints / Purpose |
| :--- | :--- | :--- |
| `ID_REGLAMENTO` | `NUMBER(19)` | Primary Key (`PK_REGLAMENTOS_NORMATIVA`, Identity) |
| `ID_ORGANIZACION` | `NUMBER(19)` | Mandatory Organization Tenant FK (`FK_REGLAM_ORG`) |
| `ID_PROPIEDAD` | `NUMBER(19)` | Mandatory Property FK (`FK_REGLAM_PROP`) |
| `TIPO_NORMATIVA` | `VARCHAR2(50 CHAR)` | Check Constraint `CK_REGLAM_TIPO` |
| `TITULO` | `VARCHAR2(255 CHAR)` | Non-null official title |
| `DESCRIPCION` | `VARCHAR2(1000 CHAR)`| Summary of bylaw scope and clauses |
| `ID_DOCUMENTO` | `NUMBER(19)` | Foreign Key -> `DOCUMENTOS(ID_DOCUMENTO)` (`FK_REGLAM_DOC`) |
| `ESTADO` | `VARCHAR2(25 CHAR)` | Check Constraint `CK_REGLAM_ESTADO` (Default `'BORRADOR'`) |
| `FECHA_ENTRADA_EN_VIGOR` | `DATE` | Formal enforcement start date |
| `FECHA_PUBLICACION` | `TIMESTAMP(6) WITH TIME ZONE` | Promulgation timestamp |
| `CREADO_POR` | `NUMBER(19)` | User ID who drafted the regulation (`FK_REGLAM_CREADOR`) |
| `PUBLICADO_POR` | `NUMBER(19)` | User ID who promulgated the regulation (`FK_REGLAM_PUB`) |
| `FECHA_CREACION` | `TIMESTAMP(6) WITH TIME ZONE` | Record creation timestamp (default `SYSTIMESTAMP`) |
| `FECHA_MODIFICACION` | `TIMESTAMP(6) WITH TIME ZONE` | Last modification timestamp |

#### Canonical Normative Types (`CK_REGLAM_TIPO`)
1. `REGLAMENTO_INTERNO`: Horizontal property co-ownership bylaw and statutory charter.
2. `MANUAL_CONVIVENCIA`: Daily community coexistence rules, noise regulations, and dispute procedures.
3. `MANUAL_ZONAS_COMUNES`: Reservation and usage policies for clubhouse, BBQ, pool, and sports courts.
4. `MANUAL_POLITICA_MASCOTAS`: Domestic pet rules, breed restrictions, and common area transit policies.
5. `ESTATUTO_COPROPIEDAD`: Formal corporate bylaws of the co-ownership assembly.
6. `OTRO`: General administrative normative guidelines.

#### Canonical Lifecycle States (`CK_REGLAM_ESTADO`)
1. `BORRADOR`: Preliminary draft undergoing administrative review. Not visible to residents.
2. `PUBLICADO`: Officially promulgated and active bylaw. Exactly one active per property/type. Visible to residents.
3. `REEMPLAZADO`: Superseded by a newer publication. Retained for historical audit.
4. `INACTIVO`: Formally inactivated or decommissioned by administration.

### 2.2 Uniqueness & Performance Indexes

```sql
-- Deterministic single published regulation per property & normative type
CREATE UNIQUE INDEX UQ_REGLAM_PROP_TIPO_VIG ON REGLAMENTOS_NORMATIVA (
    CASE WHEN ESTADO = 'PUBLICADO' THEN ID_PROPIEDAD ELSE NULL END,
    CASE WHEN ESTADO = 'PUBLICADO' THEN TIPO_NORMATIVA ELSE NULL END
);

-- Performance filtering indexes
CREATE INDEX IX_REGLAM_PROP_EST ON REGLAMENTOS_NORMATIVA (ID_PROPIEDAD, ESTADO);
CREATE INDEX IX_REGLAM_DOC ON REGLAMENTOS_NORMATIVA (ID_DOCUMENTO);
CREATE INDEX IX_REGLAM_ORG ON REGLAMENTOS_NORMATIVA (ID_ORGANIZACION);
```

---

## 3. Security & Multitenancy Architecture

### 3.1 Oracle Virtual Private Database (VPD / RLS)
- Registered policy `POL_RLS_PROP_REGLAMENTOS` on table `REGLAMENTOS_NORMATIVA`.
- Governed by `PKG_SAED_SECURITY_RLS.FN_FILTRO_PROPIEDAD`.
- When an administrator or resident authenticates, `SaedDataSourceProxy` activates the session context via `PKG_SAED_SESSION.SET_CONTEXT(p_user_id, p_org_id, p_prop_id, p_rol)`.
- SQL statements are transparently rewritten by the Oracle kernel:
  - Global Administrators (`SUPERADMIN`): Unrestricted cross-property view (`1=1`).
  - Organization Admins (`ADMIN_ORGANIZACION`): Restricted to properties belonging to their organization (`id_propiedad IN (SELECT id_propiedad FROM PROPIEDADES WHERE id_organizacion = v_org)`).
  - Property Admins (`ADMIN_PROPIEDAD`): Strictly confined to `id_propiedad = v_prop`.
  - Residents (`RESIDENTE`, `PROPIETARIO_UNIDAD`): Strictly confined to `id_propiedad IN (SELECT id_propiedad FROM USUARIO_ASIGNACIONES WHERE id_usuario = v_usr AND estado='ACTIVA') AND estado = 'PUBLICADO'`.
- Portiers (`PORTERO`): Explicitly denied access via `@PreAuthorize` on all endpoints (HTTP 403 Forbidden).

### 3.2 Role Matrix

| Action | SUPERADMIN | ADMIN_ORGANIZACION | ADMIN_PROPIEDAD | RESIDENTE / PROPIETARIO | PORTERO |
| :--- | :---: | :---: | :---: | :---: | :---: |
| List All (inc. Drafts) | Yes | Yes (Org props) | Yes (Own prop) | No (403) | No (403) |
| List Published (`/residente`) | Yes | Yes | Yes | Yes (Own prop) | No (403) |
| Get Active (`/vigente`) | Yes | Yes | Yes | Yes (Own prop) | No (403) |
| Get by ID (`/{id}`) | Yes | Yes | Yes | Yes (if PUBLICADO) | No (403) |
| Create Draft (`POST /`) | Yes | Yes | Yes | No (403) | No (403) |
| Update Draft (`PUT /{id}`) | Yes | Yes | Yes | No (403) | No (403) |
| Publish (`POST /{id}/publicar`) | Yes | Yes | Yes | No (403) | No (403) |
| Inactivate (`POST /{id}/inactivar`) | Yes | Yes | Yes | No (403) | No (403) |
| Download Binary File | Yes | Yes | Yes | Yes (if PUBLICADO) | No (403) |

---

## 4. API Endpoints Specification

Base path: `/api/v1/reglamentos`

All endpoints return uniform responses conforming to `ApiResponse<T>`:

| Method | Endpoint | Allowed Roles | Description | HTTP Statuses |
| :--- | :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/reglamentos/admin` | `SUPERADMIN`, `ADMIN_ORGANIZACION`, `ADMIN_PROPIEDAD` | List all regulations with optional filters (`tipoNormativa`, `estado`, `idPropiedad`) | 200, 401, 403 |
| `GET` | `/api/v1/reglamentos/residente` | `RESIDENTE`, `PROPIETARIO`, `PROPIETARIO_UNIDAD`, `RESIDENTE_CONVIVENCIA` | List only published regulations for resident's active property | 200, 401, 403 |
| `GET` | `/api/v1/reglamentos/vigente` | `SUPERADMIN`, `ADMIN_ORGANIZACION`, `ADMIN_PROPIEDAD`, `RESIDENTE`, `PROPIETARIO` | Get active published regulation by `tipoNormativa` | 200, 400, 404 |
| `GET` | `/api/v1/reglamentos/{id}` | Authenticated with property access | Get regulation detail by ID (residents restricted to `PUBLICADO`) | 200, 403, 404 |
| `GET` | `/api/v1/reglamentos/{id}/descargar` | Authenticated (residents restricted to `PUBLICADO`) | Stream binary file with `Content-Disposition` and SHA-256 header | 200, 403, 404 |
| `POST` | `/api/v1/reglamentos` | `SUPERADMIN`, `ADMIN_ORGANIZACION`, `ADMIN_PROPIEDAD` | Create new regulation draft linked to F10-01 document | 201, 400, 403 |
| `PUT` | `/api/v1/reglamentos/{id}` | `SUPERADMIN`, `ADMIN_ORGANIZACION`, `ADMIN_PROPIEDAD` | Update draft regulation metadata | 200, 400, 403, 404 |
| `POST` | `/api/v1/reglamentos/{id}/publicar` | `SUPERADMIN`, `ADMIN_ORGANIZACION`, `ADMIN_PROPIEDAD` | Atomically publish regulation, replacing previous active version | 200, 400, 403, 404, 409 |
| `POST` | `/api/v1/reglamentos/{id}/inactivar` | `SUPERADMIN`, `ADMIN_ORGANIZACION`, `ADMIN_PROPIEDAD` | Inactivate regulation, withdrawing its active state | 200, 403, 404, 409 |

---

## 5. Frontend Architecture

### 5.1 Administrator View (`ReglamentosAdminPage.jsx`)
- **Route:** `/reglamentos` (alias `/reglamentos-admin`)
- **Features:**
  - KPI Metrics Cards: Active regulations count, total published, pending drafts, superseded history.
  - Dynamic Filters: Filter by normative type pill selector and state dropdown.
  - Creation & Edition Modals: Full form validation with document selector linked to F10-01 records.
  - Publication Modal: Explains atomic replacement warning before confirming promulgation.
  - Inactivation Action: Formally inactivate active regulations.
  - Direct File Action: In-app PDF download and viewing.

### 5.2 Resident View (`ResReglamentosPage.jsx`)
- **Route:** `/res-reglamentos` (aliases `/mis-reglamentos`, `/normativa`)
- **Features:**
  - Hero Cards: Prominently showcases the active *Reglamento Interno* and *Manual de Convivencia*.
  - Comprehensive Normative Directory: Accordion and grid views of pet policies, common area guidelines, and bylaws.
  - Cryptographic Verification: Displays SHA-256 checksum with one-click clipboard copy for legal transparency.
  - Instant Download: Authenticated direct download with real-time feedback.

---

## 6. Verification & Test Suite

Automated integration test suite: `com.saed.backend.reglamentos.F10ReglamentosIntegrationTest` (27 test cases):

| # | Test Method Name | Description / Scenario | Result |
| :-: | :--- | :--- | :-: |
| 1 | `test01_crearBorrador_adminPropiedad_exitoso` | Create draft regulation by property admin | **PASSED** |
| 2 | `test02_crearBorrador_validarDocumentoInexistente_falla400` | Reject draft creation with non-existent document ID | **PASSED** |
| 3 | `test03_crearBorrador_documentoOtraPropiedad_falla400` | Reject draft creation with document from different property | **PASSED** |
| 4 | `test04_crearBorrador_tipoNormativaInvalido_falla400` | Reject invalid normative type not in canonical catalog | **PASSED** |
| 5 | `test05_crearBorrador_tituloVacio_falla400` | Reject draft creation with empty title | **PASSED** |
| 6 | `test06_actualizarBorrador_exitoso` | Update draft metadata | **PASSED** |
| 7 | `test07_actualizarBorrador_yaPublicado_falla400` | Reject modification of published regulation | **PASSED** |
| 8 | `test08_publicarReglamento_primerReglamento_exitoso` | Publish first regulation of a type | **PASSED** |
| 9 | `test09_publicarReglamento_yaPublicado_falla409` | Reject re-publishing already published regulation | **PASSED** |
| 10 | `test10_publicarReglamento_reemplazaAnteriorVigente_atomico` | Atomically replace previous active regulation with new one | **PASSED** |
| 11 | `test11_inactivarReglamento_desdePublicado_exitoso` | Inactivate published regulation | **PASSED** |
| 12 | `test12_inactivarReglamento_yaInactivo_falla409` | Reject inactivating an already inactive regulation | **PASSED** |
| 13 | `test13_consultarVigente_residente_exitoso` | Resident queries active published regulation | **PASSED** |
| 14 | `test14_consultarVigente_sinNormativaVigente_retorna404` | Query active regulation when none exists returns 404 | **PASSED** |
| 15 | `test15_listarReglamentos_admin_veTodosEstados` | Admin lists all states (draft, published, replaced, inactivo) | **PASSED** |
| 16 | `test16_listarReglamentos_residente_soloVePublicados` | Resident lists only published regulations | **PASSED** |
| 17 | `test17_descargarDocumento_residente_reglamentoPublicado_exitoso` | Resident downloads published regulation file | **PASSED** |
| 18 | `test18_descargarDocumento_residente_reglamentoBorrador_falla403` | Resident denied download of draft regulation (403/404 under RLS) | **PASSED** |
| 19 | `test19_descargarDocumento_portero_falla403` | Strict 403 Forbidden across all 9 endpoints for PORTERO | **PASSED** |
| 20 | `test20_aislamientoMultiTenant_adminPropiedadA_noVeReglamentosPropiedadB` | Property admin isolated from other properties (same org) | **PASSED** |
| 21 | `test21_aislamientoMultiTenant_residentePropiedadA_noVeReglamentosPropiedadB` | Resident isolated from other properties | **PASSED** |
| 22 | `test22_aislamientoOrganizacion_adminOrgA_noVeReglamentosOrgB` | Cross-organization isolation (Org 1 vs Org 2) | **PASSED** |
| 23 | `test23_indiceUnicoCondicional_evitaDosVigentesEnBaseDeDatos` | Database engine conditional unique index violation test | **PASSED** |
| 24 | `test24_concurrencia_publicacionSimultanea_serializada` | Multi-threaded concurrent publication serialized via row lock | **PASSED** |
| 25 | `test25_descargaDocumento_hashSha256_e_integridad` | SHA-256 cryptographic checksum and byte integrity check | **PASSED** |
| 26 | `test26_publicacion_auditoriaRegistrada` | Audit log record verified in `AUDITORIA_LOG` | **PASSED** |
| 27 | `test27_residente_accesoDirectoPorId_borrador_retorna403` | Resident direct ID lookup of draft rejected (403/404 under RLS) | **PASSED** |
