# F10-07 — Pólizas de Seguro de Áreas Comunes

## Overview

The **F10-07 Pólizas de Seguro de Áreas Comunes** module provides comprehensive lifecycle tracking, operational management, and resident transparency for insurance policies protecting common property in residential and commercial complexes under SAED 2.0.

The module incorporates domain business rules modeled after common horizontal property administrative practices (including reference guidelines from Colombian **Ley 675 de 2001, Artículo 15**, regarding common area coverage against fire, earthquake, and civil liability).

> [!NOTE]
> **Technical Disclaimer:** SAED 2.0 is an administrative management platform. It does not provide legal advice, insurance brokerage services, or statutory compliance certifications. All policy validity, coverage sufficiency, and renewal decisions remain the sole responsibility of the co-ownership administration and its insurance advisors.

---

## Architectural Principles & Scope Boundaries

### 1. Mandatory Scope (Certified)
* **Policy Lifecycle Management:** Tracking insurer, policy number, line of coverage (*ramo de cobertura*), insured value, annual premium, effective validity dates (*vigencia*), alert horizons, and agent contact.
* **Deductibles (GAP 01):** Full support for deductibles (`DEDUCIBLE`), accepting percentage/minimum-wage structures (e.g., `"10% del siniestro, mín. 3 SMMLV"`) or fixed monetary amounts.
* **Document Management Pipeline (GAP 02):** Native integration with certified module **F10-01 (Documentos)** via foreign key `ID_DOCUMENTO -> DOCUMENTOS(ID_DOCUMENTO)` (`POLIZA_SEGURO` category), guaranteeing SHA-256 verification and secure streaming downloads at `/api/v1/documentos/{id}/descargar`, while preserving `DOCUMENTO_CARATULA_URL` for legacy external attachments.
* **Resident Transparency (GAP 03 & 04):** Public read-only endpoint (`GET /api/v1/seguros/polizas/vigentes`) and resident interface (`/res-seguros`) for `RESIDENTE`, `PROPIETARIO`, and `RESIDENTE_CONVIVENCIA`, ensuring co-owners can inspect coverage and deductibles without accessing administrative controls.
* **Proactive Expiration Alarms (GAP 05 & OBS-02):** Daily automated cron scheduler (`PolizaVencimientoScheduler`) with atomic `MERGE` structured entity deduplication by `ID_POLIZA` in `ALERTAS_ADMIN` (15-day window) and proactive email notifications to property managers.
* **Supervisory Role Alignment (GAP 06):** Read-only supervisory access for `ADMIN_ORGANIZACION` across property policies, with strict server-side and client-side mutation locks.

### 2. Strict Out-of-Scope (Non-Pollution Boundaries)
Per the Master Document and READ-ONLY audit:
* **No Claims / Siniestros Module:** Incident tracking and emergency response are handled in F10-08/F10-09. F10-07 does not implement claim indemnification ledgers.
* **No Independent Catalog Tables:** Insurer names and coverage branches are maintained within normalized business fields, avoiding unnecessary relational sprawl.
* **No Private Unit Policies:** Restrained exclusively to common areas and co-ownership assets.
* **Zero Cartera / Finance Pollution:** Policy premiums do not create automated accounts receivable in Cartera or alter Paz y Salvo calculations.

---

## State Machine & Canonical Dynamic Engine (OBS-01)

Policy states are evaluated through a centralized domain engine (`PolizaSeguroService.calcularEstadoDinamico`), guaranteeing synchronized real-time state independent of batch scheduler timing:

```
                  ┌──────────────┐
                  │   VIGENTE    │
                  └──────┬───────┘
                         │
        (ref >= fechaFin - diasAlerta)
                         │
                         ▼
                  ┌──────────────┐
                  │  POR_VENCER  │
                  └──────┬───────┘
                         │
                 (ref > fechaFin)
                         │
                         ▼
                  ┌──────────────┐
                  │   VENCIDA    │
                  └──────────────┘
                         ▲
                         │ (Manual cancellation override)
                  ┌──────────────┐
                  │  CANCELADA   │
                  └──────────────┘
```

| State | Calculation / Condition | Description |
|---|---|---|
| `CANCELADA` | Persistent state `CANCELADA` | Absolute priority; manual administrative termination. |
| `VENCIDA` | `ref > fechaFin` | Policy validity period has elapsed. |
| `POR_VENCER` | `ref >= (fechaFin - diasAlerta)` AND `ref <= fechaFin` | Policy within warning threshold (default 45 days). |
| `VIGENTE` | All other cases (including `ref < (fechaFin - diasAlerta)` and `ref < fechaInicio`) | Policy active; no intermediate `PENDIENTE` state is invented. |

---

## Database Architecture (Oracle XE / ATP)

### Table: `POLIZAS_SEGURO`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `ID_POLIZA` | `NUMBER(19)` | `PRIMARY KEY` (Identity) | Unique identifier |
| `ID_PROPIEDAD` | `NUMBER(19)` | `NOT NULL`, `FK -> PROPIEDADES` | Multi-tenant property partition |
| `COMPANIA_ASEGURADORA` | `VARCHAR2(150 CHAR)` | `NOT NULL` | Insurance company name |
| `NUMERO_POLIZA` | `VARCHAR2(100 CHAR)` | `NOT NULL` | Policy contract number |
| `RAMO_COBERTURA` | `VARCHAR2(100 CHAR)` | `NOT NULL`, `CK_POLIZAS_RAMO` | Coverage line |
| `VALOR_ASEGURADO` | `NUMBER(16,2)` | `NOT NULL`, `CK_POLIZAS_VAL_POS (> 0)` | Total insured amount |
| `VALOR_PRIMA_ANUAL` | `NUMBER(14,2)` | `NOT NULL`, `CK_POLIZAS_PRIMA_POS (>= 0)`| Annual premium cost |
| `FECHA_INICIO` | `DATE` | `NOT NULL` | Policy start date |
| `FECHA_FIN` | `DATE` | `NOT NULL` | Policy expiration date |
| `DIAS_ALERTA_VENCIMIENTO` | `NUMBER(3)` | `DEFAULT 45`, `NOT NULL` | Threshold days for `POR_VENCER` (canonical default: 45) |
| `DEDUCIBLE` | `VARCHAR2(255 CHAR)` | `NULL` | Deductible terms or percentage |
| `ID_DOCUMENTO` | `NUMBER(19)` | `NULL`, `FK_POLIZAS_DOC -> DOCUMENTOS` | F10-01 document link |
| `NOMBRE_CORREDOR_AGENTE` | `VARCHAR2(150 CHAR)` | `NULL` | Agent/broker name |
| `TELEFONO_CONTACTO_AGENTE`| `VARCHAR2(50 CHAR)` | `NULL` | Agent telephone |
| `DOCUMENTO_CARATULA_URL` | `VARCHAR2(500 CHAR)` | `NULL` | Legacy external document URL |
| `ESTADO` | `VARCHAR2(30 CHAR)` | `DEFAULT 'VIGENTE'`, `CK_POLIZAS_ESTADO` | Policy operational state |
| `FECHA_CREACION` | `TIMESTAMP` | `DEFAULT SYSTIMESTAMP` | Audit creation timestamp |
| `FECHA_MODIFICACION` | `TIMESTAMP` | `NULL` | Audit update timestamp |

### Multi-Tenant Virtual Private Database (VPD / RLS)
* Protected by policy `POL_RLS_PROP_POLIZAS_SEGURO` using predicate function `PKG_SAED_SECURITY_RLS.FN_FILTRO_PROPIEDAD`.
* Session context `SAED_CTX` is populated per request via `SaedDataSourceProxy` based on JWT and `X-Assignment-Id`.

---

## REST API Contract

Base path: `/api/v1/seguros/polizas`

| HTTP Method | Path | Allowed Roles | Description |
|---|---|---|---|
| `GET` | `/` | `ADMIN_PROPIEDAD`, `ADMIN_ORGANIZACION` | List all policies of the property |
| `GET` | `/{id}` | `ADMIN_PROPIEDAD`, `ADMIN_ORGANIZACION` | Get detailed policy by ID |
| `POST` | `/` | `ADMIN_PROPIEDAD` | Create new policy |
| `PUT` | `/{id}` | `ADMIN_PROPIEDAD` | Update existing policy |
| `DELETE` | `/{id}` | `ADMIN_PROPIEDAD` | Delete policy |
| `GET` | `/resumen` | `ADMIN_PROPIEDAD`, `ADMIN_ORGANIZACION` | KPI summary (counts, sums, alerts) |
| `GET` | `/vigentes` | `RESIDENTE`, `PROPIETARIO`, `RESIDENTE_CONVIVENCIA`, `ADMIN_PROPIEDAD`, `ADMIN_ORGANIZACION` | Co-owner read-only view of active policies |

---

## Proactive Notification Engine (OBS-02)

```
[Cron 03:00 AM] ──> PolizaVencimientoScheduler
                         │
                         ▼
             findPolizasProximasAVencerGlobal()
                         │
                         ▼
          Atomic MERGE INTO ALERTAS_ADMIN
            (ID_PROPIEDAD, TIPO_ALERTA, ID_POLIZA, 15-day window)
                         │
             ┌───────────┴───────────┐
        [rowsAffected == 0]     [rowsAffected == 1]
        (Already alerted)       (New alert inserted)
             │                       │
           Ignore                    └──> EmailService.sendEmail(...)
                                     └──> EmailService.sendEmail(...)
```

---

## Verification & Test Suite

The module is verified through:
1. `EmergenciasYSegurosIntegrationTest`: End-to-end integration and KPI evaluation.
2. `PolizasSeguroIntegrationTest`: Dedicated comprehensive test suite covering CRUD, deductible persistence, F10-01 document linkage, cross-property validation, dynamic states, resident endpoint security, role permissions, and scheduler deduplication.
3. `ResidenteAdversarialAuthorizationTest`: Adversarial validation confirming that residents are strictly forbidden (`403 Forbidden`) from accessing administrative endpoints (`/api/v1/seguros/polizas`).
