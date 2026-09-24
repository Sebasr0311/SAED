# F10-01 — Gestión Documental (Document Management)
**SAED 2.0 — Property Management & Governance Platform**  
*Phase 10: Documentation, Regulations & Property Governance — Module 01*

---

## 1. Executive Summary

Module **F10-01 Gestión Documental** provides enterprise-grade, multi-tenant document custody, version control, and access governance for condominiums and real estate properties managed under SAED 2.0.

The implementation strictly eliminates simulated or mock data, deploying:
- Physical multipart file ingestion with SHA-256 cryptographic fingerprinting.
- Direct binary streaming downloads with authenticated byte-level streaming via Spring `Resource`.
- Relational version tracking linked to Oracle ATP/XE relational tables (`DOCUMENTOS` and `VERSIONES_DOCUMENTO`).
- Strict Oracle Row-Level Security (RLS / VPD) tenancy isolation (`POL_RLS_ORG_DOCUMENTOS`).
- Role-based and resident visibility boundaries conforming to the SAED Transversal API Contract v1.

---

## 2. Relational Schema & Integrity Constraints

### 2.1 Table `DOCUMENTOS` (Document Master)
Stores metadata and access governance rules per document entity.

| Column | Type | Constraints / Purpose |
| :--- | :--- | :--- |
| `ID_DOCUMENTO` | `NUMBER(19)` | Primary Key (Identity / Sequence) |
| `ID_ORGANIZACION` | `NUMBER(19)` | Mandatory Organization Tenant FK |
| `ID_PROPIEDAD` | `NUMBER(19)` | Property Scoping FK |
| `ID_UNIDAD` | `NUMBER(19)` | Optional specific Unit FK |
| `CATEGORIA` | `VARCHAR2(50)` | Check Constraint `CK_DOCUMENTOS_CAT` |
| `TITULO` | `VARCHAR2(255)` | Non-null document title |
| `DESCRIPCION` | `VARCHAR2(1000)`| Optional contextual summary |
| `ES_PUBLICO_RESIDENTES` | `CHAR(1)` | Check `CK_DOCUMENTOS_PUB` ('S' or 'N') |
| `ROL_MINIMO_ACCESO` | `VARCHAR2(50)` | Access threshold when private (default `ADMIN_PROPIEDAD`) |
| `ESTADO` | `VARCHAR2(20)` | Check `CK_DOCUMENTOS_EST` (`ACTIVO`, `ARCHIVADO`, `ELIMINADO`) |
| `CREADO_POR` | `NUMBER(19)` | Audit user reference |
| `FECHA_CREACION` | `TIMESTAMP` | Record timestamp (default `SYSDATE`) |

#### Canonical Categories (`CK_DOCUMENTOS_CAT`)
1. `REGLAMENTO_INTERNO`: Internal condominium regulations & statutes.
2. `RUT_MATRICULA`: Tax certificates and property registry certificates.
3. `ACTA_ASAMBLEA`: Formal minutes from general owner assemblies.
4. `CONTRATO_PROVEEDOR`: Vendor service agreements and procurement contracts.
5. `POLIZA_SEGURO`: Insurance policies and coverage certificates.
6. `ESTADO_FINANCIERO`: Financial audits, balance sheets, and budget reports.
7. `PLANOS`: Architectural, hydraulic, and electrical building blueprints.
8. `MANUAL_CONVIVENCIA`: Resident coexistence guidelines and pet policies.
9. `OTRO`: General administrative records.

### 2.2 Table `VERSIONES_DOCUMENTO` (Version Ledger)
Maintains an immutable historical record of all uploaded binary revisions.

| Column | Type | Description |
| :--- | :--- | :--- |
| `ID_VERSION` | `NUMBER(19)` | Primary Key |
| `ID_DOCUMENTO` | `NUMBER(19)` | Foreign Key -> `DOCUMENTOS(ID_DOCUMENTO)` |
| `NUMERO_VERSION` | `NUMBER(5)` | Sequential integer (1, 2, 3, ...) |
| `ARCHIVO_URL` | `VARCHAR2(500)`| Physical storage relative URI |
| `ARCHIVO_NOMBRE_ORIG` | `VARCHAR2(255)`| Original client filename (e.g. `reglamento_v2.pdf`) |
| `ARCHIVO_TAMANO_BYTES` | `NUMBER(19)` | Physical file size in bytes |
| `ARCHIVO_MIME_TYPE` | `VARCHAR2(100)`| MIME classification (e.g. `application/pdf`) |
| `ARCHIVO_SHA256` | `VARCHAR2(64)` | Cryptographic SHA-256 integrity hash |
| `NOTAS_CAMBIO` | `VARCHAR2(1000)`| Revision changelog / audit notes |
| `FECHA_SUBIDA` | `TIMESTAMP` | Upload timestamp |
| `SUBIDO_POR` | `NUMBER(19)` | User ID who committed the revision |

---

## 3. Security & Multitenancy Architecture

### 3.1 Oracle Virtual Private Database (VPD / RLS)
- All queries against `DOCUMENTOS` are governed by `POL_RLS_ORG_DOCUMENTOS`.
- When an administrator or resident executes a query, the connection proxy (`SaedDataSourceProxy`) binds their session tenant context via `PKG_SAED_SESSION.SET_CONTEXT(p_user_id, p_org_id, p_prop_id, p_rol)`.
- Oracle enforces transparent SQL predicate filtering at the database kernel level:
  `ID_ORGANIZACION = SYS_CONTEXT('SAED_CTX', 'ID_ORGANIZACION')`
- Cross-tenant requests produce zero rows in Oracle, guaranteeing Anti-IDOR and complete tenant segregation.

### 3.2 Role-Based Access Control (RBAC) Confinement
| Role | Permitted Actions | Restrictions |
| :--- | :--- | :--- |
| `SUPERADMIN` | Read, Upload, Update, Add Version, Delete | Platform-wide audit governance. |
| `ADMIN_ORGANIZACION` | Read, Upload, Update, Add Version, Delete | Restricted to properties in their organization. |
| `ADMIN_PROPIEDAD` | Read, Upload, Update, Add Version, Delete | Restricted to their assigned property. |
| `RESIDENTE` / `PROPIETARIO` | Read, Download | Restricted to their property AND documents where `ES_PUBLICO_RESIDENTES = 'S'`. Private documents return 403 Forbidden. |
| `PORTERO` | None | Strict role confinement: 403 Forbidden on all document endpoints. |

---

## 4. API Endpoints Specification

All endpoints conform to the **SAED Transversal API Contract v1** (`ApiResponse<T>` / `ApiErrorResponse`).

### 4.1 Upload Document (`POST /api/v1/documentos/upload`)
- **Content-Type:** `multipart/form-data`
- **Security:** `@PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD')")`
- **Parameters:**
  - `archivo` (or `file`): `MultipartFile` (Binary payload)
  - `titulo`: `String` (Required)
  - `categoria`: `String` (Required, validated against `CK_DOCUMENTOS_CAT`)
  - `descripcion`: `String` (Optional)
  - `esPublicoResidentes`: `String` ('S' or 'N')
  - `rolMinimoAcceso`: `String` (Optional, defaults to `ADMIN_PROPIEDAD`)
  - `idUnidad`: `Long` (Optional)
- **Response (201 Created):**
```json
{
  "success": true,
  "status": "success",
  "data": {
    "idDocumento": 101,
    "titulo": "Reglamento Interno Copropiedad 2026",
    "categoria": "REGLAMENTO_INTERNO",
    "nombreArchivo": "reglamento_2026.pdf",
    "tamanoBytes": 524288,
    "mimeType": "application/pdf",
    "numeroVersion": 1,
    "archivoSha256": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
  },
  "traceId": "7b0a8806-3841-4566-a365-5c1cfadfebe3",
  "timestamp": "2026-09-19T18:05:32Z"
}
```

### 4.2 Stream Binary Download (`GET /api/v1/documentos/{id}/descargar`)
- **Alias:** `GET /api/v1/documentos/{id}/archivo`
- **Security:** Authenticated (`SUPERADMIN`, `ADMIN_ORGANIZACION`, `ADMIN_PROPIEDAD`, or `RESIDENTE` if public).
- **Headers Returned:**
  - `Content-Disposition: attachment; filename="reglamento_2026.pdf"`
  - `Content-Type: application/pdf`
  - `Content-Length: 524288`
  - `Cache-Control: no-cache, no-store, must-revalidate`
- **Payload:** Raw binary octet stream transferred via Spring `UrlResource` using chunked streaming I/O buffers (8 KB), avoiding whole-file heap buffering (O(1) memory overhead) while maintaining high-throughput binary transfer.

### 4.3 Add Version (`POST /api/v1/documentos/{id}/versiones`)
- **Content-Type:** `multipart/form-data`
- **Security:** Admin roles only.
- **Parameters:**
  - `archivo` (or `file`): `MultipartFile`
  - `notasCambio`: `String` (Reason / changelog for revision)
- **Concurrency & Locking:** Employs pessimistic row-level locking (`SELECT ID_DOCUMENTO FROM DOCUMENTOS WHERE ID_DOCUMENTO = ? AND ESTADO <> 'ELIMINADO' FOR UPDATE`) within an atomic `@Transactional` boundary. This serializes concurrent version additions, guaranteeing deterministic sequential numbering (v2, v3, ...) and preventing `ORA-00001` collisions on `UQ_VERSDOC_NUM`.
- **Response (201 Created):** Returns the updated `DocumentoDTO` with incremented `numeroVersion`.

### 4.4 Update Metadata (`PUT /api/v1/documentos/{id}`)
- **Content-Type:** `application/json`
- **Security:** Admin roles only (`SUPERADMIN`, `ADMIN_ORGANIZACION`, or assigned `ADMIN_PROPIEDAD`).
- **Body:** `{ "titulo": "...", "categoria": "...", "descripcion": "...", "esPublicoResidentes": "S", "rolMinimoAcceso": "ADMIN_PROPIEDAD" }`
- **Response (200 OK):** Confirmation with updated record.

### 4.5 Soft Delete (`DELETE /api/v1/documentos/{id}`)
- **Security:** Admin roles only.
- **Action:** Sets `ESTADO = 'ELIMINADO'`. Preserves all database rows in `DOCUMENTOS` and `VERSIONES_DOCUMENTO` for strict audit lineage, while immediately blocking all subsequent downloads (404), updates (404), new versions (404), and listings.
- **Response (200 OK):** Standard success envelope.

---

## 5. Frontend Architecture

### 5.1 Admin Console (`DocumentosAdminPage.jsx`)
- **Location:** `frontend/src/pages/DocumentosAdminPage.jsx`
- **Features:**
  - Zero simulation: connects real `FormData` to `/api/v1/documentos/upload`.
  - Authenticated binary download via `fetch` streaming blob and `URL.createObjectURL(blob)`.
  - Multi-version upload modal (`POST /api/v1/documentos/{id}/versiones`) with `notasCambio`.
  - Metadata edit modal (`PUT /api/v1/documentos/{id}`).
  - Soft delete with immediate UI feedback and confirmation dialog.
  - KPI cards: Total Documents, Public for Residents, Confidential.
  - Interactive search and category filter conforming to `CK_DOCUMENTOS_CAT`.
  - Safe unpacking of `ApiResponse<T>` contract.

### 5.2 Resident Library (`ResDocumentosPage.jsx`)
- **Location:** `frontend/src/pages/ResDocumentosPage.jsx`
- **Features:**
  - Displays approved public documents (`GET /api/v1/documentos/residente`).
  - Authenticated binary blob download with security headers.
  - Categorization matching Oracle check constraints.
  - Responsive cards with file sizes, dates, and version indicators.

---

## 6. Automated Test Suite & Certification Evidence

The hardened test suite `F10DocumentosIntegrationTest.java` executes 16 comprehensive integration, security, and functional tests:

```
[INFO] Running com.saed.backend.documentos.F10DocumentosIntegrationTest
[INFO] Tests run: 16, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 19.16 s -- in com.saed.backend.documentos.F10DocumentosIntegrationTest
[INFO] 
[INFO] Results:
[INFO] 
[INFO] Tests run: 16, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  37.713 s
```

### Verified Test Cases:
1. `test01_uploadMultipart_adminPropiedad_success`: Valid multipart file ingestion, SHA-256 hash generation, and Oracle persistence (201 Created).
2. `test02_download_adminPropiedad_success`: Streaming binary download with headers (`Content-Disposition`, `Content-Type`) and exact byte fidelity (200 OK).
3. `test03_residente_publicDocument_download_success`: Resident can list and download documents with `ES_PUBLICO_RESIDENTES = 'S'` (200 OK).
4. `test04_residente_privateDocument_download_forbidden403`: Resident is strictly blocked (403 Forbidden) when requesting private administrative documents.
5. `test05_crossTenant_idor_download_forbidden403`: Cross-tenant anti-IDOR isolation verified; Org 2 admin cannot download Org 1 documents.
6. `test06_portero_documentAccess_forbidden403`: Role confinement ensures `PORTERO` receives 403 Forbidden across all 8 document endpoints.
7. `test07_invalidCategory_rejected400`: Upload with invalid category rejected (400 Bad Request), enforcing `CK_DOCUMENTOS_CAT`.
8. `test08_updateDocumentMetadata_success`: Admin can update title, description, category, and resident visibility (200 OK).
9. `test09_softDeleteDocument_success`: Soft delete transitions state to `ELIMINADO` and excludes document from active listings and downloads.
10. `test10_addVersion_success`: Multi-versioning increments `NUMERO_VERSION` to 2 and updates active download payload.
11. `test11_sameOrganization_propertyToPropertyIsolation`: Property-to-property isolation within the SAME organization (Prop 1 vs Prop 1B). Admin Prop 1B cannot list, get, download, update, version, or delete Prop 1 documents.
12. `test12_crossTenantVersionIsolation_adversarial`: Multi-tenant adversarial isolation on `VERSIONES_DOCUMENTO`. Verifies Oracle VPD predicate from `PKG_SAED_SECURITY_RLS.FN_FILTRO_PROPIEDAD` renders foreign versions strictly inaccessible.
13. `test13_storageSecurity_pathTraversalRejected`: Filename path traversal rejection (`../` or `..\`), preventing directory escape (400 Bad Request).
14. `test14_concurrentVersionUploads_serializedPessimisticLock`: Simultaneous concurrent version uploads serialized via Oracle pessimistic row-level locking (`FOR UPDATE`), yielding clean `[1, 2, 3]` version sequence without `ORA-00001`.
15. `test15_softDelete_comprehensiveSafeguards`: Document marked `ELIMINADO` blocks download (404), update (404), and versioning (404), while preserving historical Oracle audit rows.
16. `test16_residentSecurityMatrix`: Full resident security matrix: public doc (200 OK), private doc (403 Forbidden), foreign doc (403/404), deleted doc (404 Not Found), and manipulated ID (404 Not Found).
