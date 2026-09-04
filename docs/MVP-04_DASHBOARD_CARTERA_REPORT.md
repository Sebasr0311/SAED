# SAED 2.0 — MVP-04: CIERRE DEL FLUJO FINANCIERO MÍNIMO
## DASHBOARD + CARTERA REAL

**Fecha:** 4 de Septiembre de 2026  
**Ambiente de Ejecución:** Oracle Database Express Edition (XE) Local (`SAED_BASELINE_TEST_01`)  
**Oracle Cloud ATP:** 100% INTOCADO / READ-ONLY  
**Estado:** 🟢 COMPLETADO Y CERTIFICADO  

---

## 1. RESUMEN EJECUTIVO

En MVP-04 se corrigió y certificó el flujo financiero mínimo para el rol `ADMIN_PROPIEDAD`:

```
ADMIN_PROPIEDAD
      ↓
Dashboard (/api/v1/cuotas & /api/v1/cartera/resumen)
      ↓
Cartera Real (/api/v1/cartera)
      ↓
Obligaciones y Estados de Pago (Dataset V5.99)
```

### Problema Resuelto
Anteriormente, el `DashboardPage.jsx` utilizaba los contratos de arrendamiento (`/contratos`) como un proxy ficticio para los "próximos cobros", debido a la ausencia de integración con las cuotas reales. Asimismo, `CarteraPage.jsx` realizaba peticiones directas sin pasar el header `X-Assignment-Id`, lo que en entornos con RLS/VPD causaba que las consultas no estuvieran aisladas por tenant o mostraran identificadores crudos de unidad sin número de apartamento.

---

## 2. CAMBIOS IMPLEMENTADOS

### A. Frontend (`DashboardPage.jsx`)
- **Eliminación del proxy de contratos:** Se reemplazó la llamada a `/contratos` por consultas directas a `/api/v1/cuotas` y `/api/v1/cartera/resumen` usando `tenantApi`.
- **StatCard Financiero Coherente:** Se sustituyó el indicador "Contratos Activos" por **"Cartera Pendiente"**, formateando en pesos colombianos el valor total de deuda ($250.000 COP para la propiedad demo).
- **Lista de Cobros Pendientes:** La tarjeta ahora lista las cuotas pendientes reales del tenant (`c.concepto`, `c.numeroApartamento`, `c.nombreResidente`, `c.periodo`, `c.saldoPendiente`), mostrando claramente la cuota del Apto 101.

### B. Frontend (`CarteraPage.jsx`)
- **Inyección de Tenant Context:** Se migró el cliente API de `api` a `useTenantApi` (`tenantApi`), asegurando que cada petición a `/cartera`, `/cartera/resumen`, `/cartera/antiguedad` y `/cartera/recalcular` envíe `X-Assignment-Id`.
- **Identificación Amigable de Unidades:** La tabla ahora muestra prioritariamente `NUMERO_APARTAMENTO` ("Apto 101", "Apto 102", etc.) en lugar del ID numérico interno.
- **Auto-refetch tras Recálculo:** Al presionar "Recalcular Cartera", se refrescan de inmediato los datos de las tres pestañas (Unidades, Resumen y Antigüedad).

### C. Backend (`CarteraController.java`)
- **Enriquecimiento con `UNIDADES`:** Las consultas `listar()`, `porUnidad()` y `resumen()` hacen `JOIN UNIDADES u ON c.ID_UNIDAD = u.ID_UNIDAD`, inyectando `u.IDENTIFICADOR AS NUMERO_APARTAMENTO` y filtrando por `u.ID_PROPIEDAD = :propId`.
- **Recálculo No Destructivo:** Se reformuló el método `recalcular()` con `MERGE INTO CARTERA` usando un `LEFT JOIN` desde `UNIDADES`, garantizando que todas las unidades de la propiedad queden registradas en cartera con saldo 0 y estado `AL_DIA` si no adeudan, eliminando borrados destructivos.

### D. Dataset de Demostración (`database/demo/V5.99__demo_seeds.sql`)
- **Corrección de Columna Virtual:** Se agregó la redefinición DDL de `CARTERA.SALDO_TOTAL` para calcular la suma exacta: `(SALDO_CORRIENTE + SALDO_MORA_30 + SALDO_MORA_60 + SALDO_MORA_90_MAS)` en lugar de devolver valores estáticos heredados de exportaciones anteriores.
- **Sembrado Inicial de Cartera:** Se incorporaron registros iniciales para las unidades 1, 2, 3 y 4 de la propiedad demo, garantizando coherencia inmediata tras la ejecución de las migraciones.

---

## 3. RESULTADOS DE CERTIFICACIÓN

| Suite de Pruebas | Pruebas | Resultado | Observaciones |
| :--- | :---: | :---: | :--- |
| `DemoDatasetRunnerTest` (Ejecución Inicial) | 8/8 | 🟢 PASS | Sembrado limpio de V5.99 en Oracle XE |
| `DemoDatasetRunnerTest` (Reejecución Idempotente) | 8/8 | 🟢 PASS | Sin colisiones de IDs, secuencias ni triggers |
| `Mvp04CarteraDashboardTest` | 1/1 | 🟢 PASS | Verifica `recalcular()`, `listar()`, `resumen()` y `cuotas` |
| `PorteroAdversarialAuthorizationTest` | 42/42 | 🟢 PASS | Cero regresiones en módulo de portería |
| `ResidenteAdversarialAuthorizationTest` | 44/44 | 🟢 PASS | Cero regresiones en permisos de residentes |
| `ContextBleedIntegrationTest` | 1/1 | 🟢 PASS | Aislamiento estricto de hilos en pool Hikari |
| `WompiPaymentFlowAdversarialTest` | 10/10 | 🟢 PASS | Pasarela Wompi C4 y webhook intactos |
| **Total Tests Backend** | **106/106** | 🟢 **PASS** | **100% Exitoso** |
| **Frontend Build (`npm run build`)** | Vite 5 | 🟢 **PASS** | Bundle limpio generado en 5.95s |

---

## 4. COHERENCIA DE DATOS PARA LA DEMO

Con las credenciales del dataset de demostración:
- **`admin` / `admin123` (Admin Propiedad):**
  - Dashboard muestra: **4 Unidades**, **Personas**, **$250.000 COP Cartera Pendiente**, **0 Multas Pendientes**.
  - Cobros Pendientes lista: **Cuota Administración Septiembre 2026** — **Apto 101 · Carlos Martinez** por **$250.000 COP**.
  - Módulo Cartera lista:
    - `Apto 101`: Saldo Corriente $250.000, Saldo Total $250.000, Estado `AL_DIA`.
    - `Apto 102`: Saldo Total $0, Estado `AL_DIA` (pagado vía Wompi).
    - `Apto 201`: Saldo Total $0, Estado `AL_DIA`.
    - `Apto 202`: Saldo Total $0, Estado `AL_DIA`.
- **`camartinez` / `admin123` (Residente Apto 101):**
  - Visualiza su cuota pendiente de $250.000 COP y su código QR activo de visita.
- **`anagomez` / `admin123` (Residente Apto 102):**
  - Visualiza su cuota pagada ($0 pendiente) con recibo Wompi asociado.

---

## 5. CONCLUSIÓN

El flujo financiero mínimo de SAED 2.0 ha quedado cerrado, coherente y robusto. La aplicación está lista para demostración académica y comercial en lo relativo a gestión de cartera, cuotas, visualización en dashboard y pagos.
