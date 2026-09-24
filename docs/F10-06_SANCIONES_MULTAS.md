# F10-06 — Sanciones y Multas

## Overview

The **F10-06 Sanciones y Multas** module governs disciplinary due process (*debido proceso disciplinario*) and economic fines within SAED 2.0, complying with Colombian Property Horizontal Law (Ley 675 de 2001).

## Fundamental Business Rule & Separation

```
INFRACCIÓN (Incidentes / Convivencia)
    ↓
PROCESO DISCIPLINARIO (Pliego de Cargos)
    ↓
DESCARGOS (Derecho a la defensa)
    ↓
RESOLUCIÓN MOTIVADA
    ├── ABSUELTA
    └── APLICADA
          ↓ (if tipoSancionPropuesta == 'MULTA_ECONOMICA')
        MULTA ECONÓMICA (Obligación sancionatoria)
```

- An infraction is **not** automatically a fine.
- A sanction does **not** automatically create an economic obligation unless the resolution decision is `APLICADA` with `tipoSancionPropuesta = MULTA_ECONOMICA`.
- **Zero Cartera Pollution:** Economic fines are distinct from common maintenance fee accounts receivable (*cuotas de administración*). Sanciones and multas do **not** automatically create general debt in Cartera, Cuotas, or alter Paz y Salvo calculations.

---

## State Machines

### 1. Sanciones (Procesos Disciplinarios)

```
NOTIFICADA ──[radicarDescargos]──> EN_DESCARGOS ──[emitirResolucion]──> APLICADA / ABSUELTA
     │                                    │
     └──[anularSancion]───> ANULADA <─────┘
```

| State | Allowed Next Transitions | Trigger |
|---|---|---|
| `NOTIFICADA` | `EN_DESCARGOS`, `ANULADA` | Residente radicates descargos, or Admin annuls |
| `EN_DESCARGOS` | `APLICADA`, `ABSUELTA`, `ANULADA` | Admin issues resolution, or Admin annuls |
| `APLICADA` | Terminal | Sanción confirmed and executed |
| `ABSUELTA` | Terminal | Accused exonerated |
| `ANULADA` | Terminal | Disciplinary case annulled |

> **Note on `EN_DELIBERACION_CONSEJO`:** This state exists in the Oracle check constraint `CK_SANCIONES_ESTADO` for backward schema compatibility, but is not exposed in operational transitions per current business rules.

### 2. Multas (Sanciones Económicas)

```
IMPUESTA ──[marcarPagada]──> PAGADA
    │
    └──[anular]────────────> ANULADA
```

| State | Allowed Transitions | Guard Rules |
|---|---|---|
| `IMPUESTA` | `PAGADA`, `ANULADA` | Can be paid or annulled |
| `RATIFICADA` | `PAGADA`, `ANULADA` | Can be paid or annulled |
| `PAGADA` | Terminal | Cannot be annulled (throws 409 Conflict) |
| `ANULADA` | Terminal | Cannot be paid (throws 409 Conflict) |

---

## API Endpoints

### Sanciones (`/api/v1/sanciones`)

| Method | Path | Allowed Roles | Description |
|---|---|---|---|
| `GET` | `/todas` | `ADMIN_ORGANIZACION`, `ADMIN_PROPIEDAD` | List all disciplinary cases in property |
| `GET` | `/{id}` | Authenticated | View case details and attached descargos |
| `POST` | `/` | `ADMIN_ORGANIZACION`, `ADMIN_PROPIEDAD` | Open formal pliego de cargos |
| `POST` | `/{id}/descargos` | `RESIDENTE`, `PROPIETARIO` | File defense arguments and evidence |
| `POST` | `/{id}/resolucion` | `ADMIN_ORGANIZACION`, `ADMIN_PROPIEDAD` | Issue final resolution (`APLICADA`, `ABSUELTA`) |
| `PUT` | `/{id}/anular` | `ADMIN_ORGANIZACION`, `ADMIN_PROPIEDAD` | Controlled annulment of active case |
| `GET` | `/mis-sanciones` | `RESIDENTE`, `PROPIETARIO`, `RESIDENTE_CONVIVENCIA` | List cases affecting the authenticated user |

### Multas (`/api/v1/multas`)

| Method | Path | Allowed Roles | Description |
|---|---|---|---|
| `GET` | `/todas` | `ADMIN_ORGANIZACION`, `ADMIN_PROPIEDAD` | List all economic fines in property |
| `GET` | `/{id}` | Authenticated | View fine details |
| `POST` | `/` | `ADMIN_ORGANIZACION`, `ADMIN_PROPIEDAD` | Create fine directly (manual sanction) |
| `PUT` | `/{id}/pagar` | `ADMIN_ORGANIZACION`, `ADMIN_PROPIEDAD` | Mark fine as paid |
| `PUT` | `/{id}/anular` | `ADMIN_ORGANIZACION`, `ADMIN_PROPIEDAD` | Annul fine |
| `GET` | `/mis-multas` | `RESIDENTE`, `PROPIETARIO`, `RESIDENTE_CONVIVENCIA` | List fines affecting authenticated user (anti-IDOR) |

---

## Architecture & Implementation Details

```
SancionesController / MultasController
    ├── SancionService (SancionServiceImpl)
    │       ├── SancionRepository (SancionRepositoryImpl) → SANCIONES, SANCION_DESCARGOS
    │       ├── EmailService (notification of pliego & resolucion)
    │       └── AuditService (AUDITORIA_LOG tracking)
    └── MultaService (MultaServiceImpl)
            └── MultaRepository (MultaRepositoryImpl) → MULTAS, CONCEPTOS_COBRO
```

### Key Technical Closures & Hardening

1. **GAP-06 (Mandatory `montoMulta` in Resolution):**
   - When issuing an `APLICADA` resolution proposing `MULTA_ECONOMICA`, `montoMulta` must be provided and greater than 0.
   - Frontend (`SancionesAdminPage.jsx`) provides currency formatting (COP) and strict numeric validation (`> 0`), blocking invalid submission.
   - Backend (`SancionServiceImpl.java`) enforces defensive validation; missing or non-positive amount throws a `400 Bad Request` (`IllegalArgumentException`).

2. **GAP INC-01 (Canonical Gravity Enum):**
   - DB constraint `CK_SANCIONES_GRAVEDAD` permits only `LEVE`, `GRAVE`, `GRAVISIMA`.
   - Frontend and backend catalogs align strictly to this contract; legacy `MODERADA` is rejected with HTTP 400.

3. **GAP-01 (Deterministic Concept Lookup):**
   - Fine creation requires a valid concept in `CONCEPTOS_COBRO`.
   - `SancionRepositoryImpl.findConceptoMulta` deterministically searches active concepts with code or type matching `MULTA`.
   - If missing, throws `ConceptoMultaNoConfiguradoException` mapped to HTTP 422 Unprocessable Entity with error code `"CONCEPTO_MULTA_NO_CONFIGURADO"`.

4. **GAP-03 (Controlled Disciplinary Annulment):**
   - Endpoint `PUT /api/v1/sanciones/{id}/anular` accepts an optional reason (`motivo`).
   - Allowed only for cases in `NOTIFICADA` or `EN_DESCARGOS`.
   - Attempting to annul a resolved case (`APLICADA`, `ABSUELTA`) yields `409 Conflict`.
   - Action recorded in `AUDITORIA_LOG` with metadata `MOTIVO_ANULACION`.

5. **GAP-04 (Role Matrix Authorization):**
   - `ADMIN_ORGANIZACION` added to administrative endpoints alongside `ADMIN_PROPIEDAD`.
   - `SUPERADMIN` remains strictly barred from operational copropiedad disciplinary records (enforced via `@PreAuthorize` and adversarial tests).

6. **GAP-05 & GAP-14 (Resident Fines Consultation):**
   - `GET /api/v1/multas/mis-multas` resolves identity strictly from authenticated `SaedContext` (preventing IDOR).
   - `RESIDENTE_CONVIVENCIA` scope restricted to individual persona violations; titular residents and owners also see assigned unit fines.
   - `ResSancionesPage.jsx` provides tabbed navigation separating **Procesos Disciplinarios** from **Multas Económicas**, complete with stats, search, badges, and detail modals.

7. **Oracle JDBC Type Safety (ORA-17004 Resolution):**
   - Nullable query parameters (`propId`, `idPersona`) bound with explicit `java.sql.Types.NUMERIC` in `MapSqlParameterSource` to eliminate Oracle driver type-unknown errors.

---

## Verification & Test Suites

- `com.saed.backend.sanciones.DebidoProcesoSancionesIntegrationTest` (9 tests, 100% pass)
- `com.saed.backend.sanciones.PhaseDSancionesAdversarialTest` (1 test, 100% pass)
- `com.saed.backend.security.ResidenteConvivenciaSecurityIntegrationTest` (9 tests, 100% pass)
- Frontend Vite production build (`npm run build`, 0 errors)
