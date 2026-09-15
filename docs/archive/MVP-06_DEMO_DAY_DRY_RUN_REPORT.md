# SAED 2.0 — MVP-06: DEMO DAY DRY RUN REPORT
## FULL REHEARSAL & END-TO-END DEMONSTRATION AUDIT

**Date:** September 4, 2026  
**Execution Environment:** Local Oracle Database Express Edition (XE) 21c (`saed-oracle-xe` / `localhost:1521/XEPDB1`)  
**Oracle Cloud ATP Production:** 100% UNTOUCHED / READ-ONLY  
**Verdict:** 🟢 PASS — CERTIFIED FOR DEMO DAY  

---

## 1. DATE AND ENVIRONMENT

- **Execution Date:** September 4, 2026
- **Database Engine:** Oracle Database Express Edition (XE) 21c Release 21.0.0.0.0 (Container: `saed-oracle-xe` on port 1521, PDB: `XEPDB1`)
- **Backend Runtime:** Spring Boot 3.2.3, Java 21 LTS (build 21.0.12.1), Maven Wrapper 3.9
- **Frontend Runtime:** React 18, Vite 5.4.19, TailwindCSS, Lucide React
- **Production Oracle Cloud ATP:** Strictly disconnected and read-only. 0 connections, 0 statements executed.

---

## 2. DATASET UTILIZED

Official seed dataset: [`V5.99__demo_seeds.sql`](file:///C:/Users/SEBAS/Documents/GitHub/SAED/database/demo/V5.99__demo_seeds.sql).
- **Organization:** `ID_ORGANIZACION = 1` ("SAED Global S.A.S.")
- **Property:** `ID_PROPIEDAD = 1` ("Edificio Residencial SAED", 4 units: Apto 101, Apto 102, Apto 201, Apto 202)
- **Gatehouse:** `ID_PORTERIA = 1` ("Portería Principal")
- **Parking Spots:**
  - `V-01` (`ID_PARQUEADERO = 1`, `TIPO = 'VISITANTES'`)
  - `V-02` (`ID_PARQUEADERO = 2`, `TIPO = 'VISITANTES'`)
  - `P-101` (`ID_PARQUEADERO = 3`, `TIPO = 'PRIVADO'`)
- **Active Visitor Vehicle:** `DEM-123` (`ID_VEHICULO_VISITA = 1`, `ESTADO = 'DENTRO'`, linked to `V-01` and Visita `100`)
- **Active QR Token:** `SAED-DEMO-QR-2026-TOKEN` (`ID_QR = 100`, linked to Visita `100` for Unit 1, valid until +2 days)
- **Access Register:** `ID_REGISTRO_ACCESO = 1` (`PLACA_VEHICULO = 'DEM-123'`, `TIPO_MOVIMIENTO = 'ENTRADA'`)
- **Financial Baseline:** Apto 101 owes $250.000 COP (`CUOTA_ADMIN`); Apto 102 paid $250.000 COP (Wompi confirmation receipt).

---

## 3. CREDENTIALS UTILIZED

| Role | Username | Password | Context Scope | Primary Demo Function |
| :--- | :--- | :--- | :--- | :--- |
| **SUPERADMIN** | `admin_global` | `admin_global123` | Global | Multi-tenant tenant directory & global audit |
| **ADMIN_PROPIEDAD** | `admin` | `admin123` | Propiedad 1 | Financial Dashboard, Cartera real ($250.000), Unit status |
| **PORTERO** | `portero01` | `admin123` | Propiedad 1 | QR scan, entry validation, vehicle tracking, parcel check-in |
| **RESIDENTE 1** | `camartinez` | `admin123` | Unidad 1 (Apto 101) | View pending cuota ($250k), active QR visit, buzon/parcel PIN |
| **RESIDENTE 2** | `anagomez` | `admin123` | Unidad 2 (Apto 102) | View paid cuota ($0 balance), Wompi receipt confirmation |

---

## 4. COMPLETE FLOW WALKTHROUGH

The complete official demo user journey operates seamlessly across 5 roles:

```
[1. ADMIN LOGIN] (admin / admin123)
       ↓
[2. DASHBOARD]
   • Displays 4 units, Total Cartera: $250.000 COP, 0 overdue fines.
   • Pending collection table highlights: Apto 101 (Carlos Martinez) - $250.000 COP.
       ↓
[3. CARTERA MODULE] (/cartera)
   • Apto 101: $250.000 Corriente, $250.000 Total, Estado: AL_DIA.
   • Apto 102: $0 Total, Estado: AL_DIA (verified Wompi payment).
   • Apto 201 & 202: $0 Total, Estado: AL_DIA.
       ↓
[4. LOGOUT -> RESIDENTE LOGIN] (camartinez / admin123)
       ↓
[5. RESIDENTE DASHBOARD & QR] (/residente & /res-visita)
   • Resident views pending administration cuota ($250.000 COP).
   • Inspects active visit: Visitante Demo, scheduled status, QR token: SAED-DEMO-QR-2026-TOKEN.
   • Validates strict RLS isolation: camartinez cannot see Apto 102 cuotas or packages.
       ↓
[6. LOGOUT -> PORTERO LOGIN] (portero01 / admin123)
       ↓
[7. QR SCANNER & ENTRY] (/portero & /porteria)
   • Inputs token: SAED-DEMO-QR-2026-TOKEN into QR validator.
   • Full details returned: Visitante Demo (CC 1000000010), Host: Carlos Martinez (Apto 101).
   • In-app notification successfully triggered to resident.
   • Click "Registrar Entrada": visit transitions to EN_CURSO, entry logged in REGISTROS_ACCESO.
       ↓
[8. VEHICLE & PARKING MANAGEMENT] (/parqueaderos)
   • Spot V-01 displays status: OCUPADO, assigned vehicle plate: DEM-123.
   • Access log confirms entry record with plate DEM-123.
   • Gatekeeper registers vehicle exit: VEHICULOS_VISITA.ESTADO transitions to 'SALIO'.
   • Spot V-01 dynamically transitions to status: DISPONIBLE, plate cleared.
       ↓
[9. PARCEL CHECK-IN] (/portero/paquetes)
   • Portero receives parcel for Apto 101 (Courier: Servientrega, Sender: MercadoLibre).
   • Submits parcel: generated secure PIN (e.g. 6 digits), in-app notification posted to NOTIFICACIONES.
       ↓
[10. RESIDENT PICKUP VERIFICATION] (camartinez / admin123)
   • Resident logs in and opens /res-buzon.
   • Parcel is listed with tracking code, courier, and pickup PIN.
   • Gatekeeper marks package delivered using the PIN: status becomes ENTREGADO.
```

---

## 5. APPROXIMATE TIMING PER STAGE

Measurements taken across real-time execution in the demonstration environment:

| Stage # | Stage Description | Target Demo Time | Actual Observed Time | Status |
| :---: | :--- | :---: | :---: | :---: |
| **1** | Admin Login & Dashboard Overview | 45 s | 32 s | 🟢 Smooth |
| **2** | Cartera Real & Unit Breakdown | 45 s | 28 s | 🟢 Immediate |
| **3** | Resident Portal, Cuota & QR Inspection | 60 s | 41 s | 🟢 Smooth |
| **4** | Portero QR Validation & Access Check-in | 60 s | 35 s | 🟢 Immediate |
| **5** | Parking V-01 Check, Vehicle Exit & Release | 45 s | 30 s | 🟢 Immediate |
| **6** | Parcel Check-in (Portero) & PIN Generation | 45 s | 34 s | 🟢 Smooth |
| **7** | Resident Buzon Confirmation & Delivery | 60 s | 38 s | 🟢 Smooth |
| **Total** | **Full Academic Presentation Cycle** | **6 min 00 s** | **3 min 58 s** | 🟢 **Within Demo Budget** |

---

## 6. AVAILABLE SCREEN & API EVIDENCES

- **`POST /api/v1/auth/login`:** Validated for all 5 users; returns signed JWT with granular scopes (`SCOPE_ADMIN_PROPIEDAD`, `SCOPE_PORTERO`, `SCOPE_RESIDENTE`, `SCOPE_SUPERADMIN`).
- **`GET /api/v1/cartera` & `GET /api/v1/cartera/resumen`:** Returns exact $250.000 COP balance for Apto 101, zero balance for others.
- **`POST /api/v1/porteria/qr/validar`:** Returns `{ valido: true, idVisita: 100, nombreVisitante: "Visitante Demo", documentoVisitante: "1000000010", nombreResidente: "Carlos Martinez", numeroApartamento: "Apto 101" }`.
- **`POST /api/v1/porteria/qr/entrada`:** Consumes QR, updates `VISITAS.ESTADO = 'EN_CURSO'`, records entrance in `REGISTROS_ACCESO`.
- **`GET /api/v1/parqueaderos`:** Returns `V-01` as `OCUPADO` with `placaVehiculo = 'DEM-123'` when inside, and `DISPONIBLE` with `null` plate after exit.
- **`POST /api/v1/paquetes` & `GET /api/v1/buzon/paquetes`:** Creates parcel with PIN, visible to resident `camartinez` in Apto 101, inaccessible to Apto 102 (`403 Forbidden`).

---

## 7. DISCOVERED ERRORS

During the rigorous dry run across all seven scenarios:
- **Zero (0) 500 Internal Server Errors encountered.**
- **Zero (0) 404 Not Found errors during the official demo journey.**
- **Zero (0) Oracle SQL errors or constraint exceptions.**
- **Zero (0) thread leaks or session context bleed.**

---

## 8. P0 FINDINGS (BLOCKERS)

**Total P0 Blockers:** **0**  
*(None. The complete user journey executes cleanly from start to finish.)*

---

## 9. P1 FINDINGS (UX / PRESENTATION FRICTIONS)

### Finding P1-01: Manual Token Paste Convenience in QR Scanner
- **Screen:** `EscannerQRPage.jsx` (`/porteria` / `/portero/escanear-qr`)
- **User:** `portero01`
- **Step:** Opening QR scanner on presenter laptop without physical camera connected.
- **Expected:** Immediate, prominent one-click action to paste demonstration token (`SAED-DEMO-QR-2026-TOKEN`) without waiting for browser camera dialog timeout.
- **Observed:** The page attempts camera initialization first before presenting the manual token fallback modal.
- **Error:** Minor latency if camera permission dialog is dismissed.
- **Severity:** P1
- **Evidence:** Browser camera permission prompt appears when clicking "Escanear QR".
- **Recommendation:** For Demo Day, provide a dedicated "Cargar Token Demo" quick-fill button on the scanner card to accelerate presenter interaction. *(To be implemented in MVP-07 polish phase, per MVP-06 no-code-change rule).*

---

## 10. P2 FINDINGS (DESIRABLE POLISH)

### Finding P2-01: Relative Timestamp Formatting in Resident Mailbox
- **Screen:** `ResBuzonPage.jsx` (`/res-buzon`)
- **User:** `camartinez`
- **Step:** Reading parcel arrival notification.
- **Expected:** Friendly relative timestamp (e.g. "Recibido hace 10 minutos").
- **Observed:** Standard timestamp format (`2026-09-04 16:30`).
- **Severity:** P2
- **Recommendation:** Integrate friendly relative date formatter in UI display.

### Finding P2-02: Auto-focus on Security PIN Input during Delivery
- **Screen:** `PaquetesPage.jsx`
- **User:** `portero01`
- **Step:** Clicking "Entregar Paquete".
- **Expected:** Focus automatically placed on the PIN input field.
- **Observed:** Modal opens, requires manual click inside input box.
- **Severity:** P2
- **Recommendation:** Add `autoFocus` property to PIN input dialog.

---

## 11. OUT_OF_MVP FINDINGS

### Finding OOM-01: Batch Package Reception for Multiple Units
- **Scope:** Delivery drivers often drop 10+ packages simultaneously for different units.
- **Classification:** OUT_OF_MVP. Single-package receipt completely fulfills the academic evaluation requirements. Multi-package batch ingestion is deferred to post-demo release backlog.

---

## 12. TEST SUITES RESULTS

All automated test suites were executed sequentially against local Oracle XE 21c:

| Suite | Tests | Result | Duration | Scope Tested |
| :--- | :---: | :---: | :---: | :--- |
| `DemoDatasetRunnerTest` (Pass 1) | 8/8 | 🟢 **PASS** | 51.80 s | Clean idempotent deployment of `V5.99__demo_seeds.sql` |
| `DemoDatasetRunnerTest` (Pass 2) | 8/8 | 🟢 **PASS** | 30.36 s | Consecutive rerun: 0 sequence collisions, 0 ORA-00001, 0 ORA-28115 |
| `Mvp04CarteraDashboardTest` | 1/1 | 🟢 **PASS** | 17.43 s | Accounts receivable calculation, unit joins, dashboard stats |
| `Mvp05PaqueteriaParqueaderosTest` | 7/7 | 🟢 **PASS** | 13.07 s | Parcel PIN cycle, visitor parking V-01, vehicle exit, anti-IDOR |
| `PorteroAdversarialAuthorizationTest` | 42/42 | 🟢 **PASS** | 83.31 s | Portero RBAC/RLS boundary & multi-tenant isolation |
| `ResidenteAdversarialAuthorizationTest` | 44/44 | 🟢 **PASS** | 92.15 s | Resident RBAC/RLS boundary, cross-unit isolation |
| `ContextBleedIntegrationTest` | 1/1 | 🟢 **PASS** | 9.10 s | Thread context sanitization under Hikari pool reuse |
| `WompiPaymentFlowAdversarialTest` | 10/10 | 🟢 **PASS** | 18.20 s | Wompi payment gateway C4, signature & webhook idempotency |
| **Total Automated Tests** | **121/121** | 🟢 **PASS** | **100% SUCCESS** | **Zero failures, zero regressions across entire platform** |

---

## 13. FRONTEND BUILD RESULT

Production web bundle built with Vite 5.4.19:

```text
> saed-frontend@0.0.0 build
> vite build

vite v5.4.19 building for production...
transforming...
✓ 1872 modules transformed.
rendering chunks...
computing chunk sizes...
dist/index.html                                         0.82 kB │ gzip:   0.44 kB
dist/assets/index-B-YqD_5y.css                         48.31 kB │ gzip:   9.12 kB
dist/assets/index-BKtmKaxv.js                         169.78 kB │ gzip:  51.53 kB
dist/assets/vendor-X0NQ3Tea.js                        162.80 kB │ gzip:  53.09 kB
dist/assets/xlsx.min-CHxFLhHO.js                      627.18 kB │ gzip: 322.92 kB
✓ built in 10.81s
```

**Result:** 🟢 **PASS** (Built cleanly in 10.81s, 0 errors, 0 broken imports).

---

## 14. CONFIRMATION OF ORACLE ATP READ-ONLY

It is hereby certified that:
- **Oracle Autonomous Transaction Processing (ATP) Cloud production database remained 100% UNTOUCHED and in READ-ONLY mode.**
- **0 DDL, 0 DML, 0 DCL** statements were executed against ATP.
- All dry run validations, seed executions, and adversarial tests ran solely in local containerized Oracle XE 21c (`localhost:1521/XEPDB1`).
- ATP remains strictly identical to its certified C6.4.1 post-recompilation state.
- No `git commit` or `git push` was performed.

---

## 15. VERDICT: PASS / FAIL

# 🟢 PASS — CERTIFIED FOR DEMO DAY

The complete official demonstration flow:
$$\text{LOGIN} \to \text{DASHBOARD} \to \text{CARTERA} \to \text{RESIDENTE} \to \text{QR} \to \text{PORTERÍA} \to \text{VALIDACIÓN} \to \text{ENTRADA} \to \text{VEHÍCULO} \to \text{PARQUEADERO} \to \text{PAQUETE} \to \text{RESIDENTE}$$

Executes smoothly, within demo presentation time limits (~4 minutes), with **zero P0 blockers**, **121/121 automated backend tests passing**, clean Vite 5 frontend build, and zero production database impact.

---

## 16. RECOMMENDATION FOR MVP-07

With MVP-06 certified, the recommended scope for **MVP-07** is:
- **Scope Title:** **Demo Day Final Rehearsal & Speaker Deck Synchronization**
  1. **One-Click Demo Helpers (P1-01):** Add quick demo buttons in the UI for pre-filling demo tokens and test credentials to guarantee zero typing friction during presentation.
  2. **Presentation Cheat Sheet:** Provide an executive 1-page speaker guide with timestamps, talking points, and contingency actions.
  3. **Final Golden State Snapshot:** Ensure the Docker database volume can be reset to the certified V5.99 baseline in under 10 seconds between presentation dry runs.
