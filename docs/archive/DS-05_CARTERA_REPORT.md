# SAED 2.0 — REPORTE TÉCNICO DS-05
## REDISEÑO GESTIÓN DE CARTERA Y COBROS (ADMIN_PROPIEDAD)

**Fecha:** 4 de Septiembre de 2026  
**Fase:** Design System Interno — Hito DS-05  
**Alcance:** Exclusivo a `frontend/src/pages/CarteraPage.jsx`  
**Referencia Visual:** Congelada desde Landing, Login, AppShell (DS-02), Dashboard (DS-03) y Residentes (DS-04)  
**Estado:** ✅ **CERTIFICADO / LISTO PARA PRODUCCIÓN**

---

## 1. RESUMEN EJECUTIVO

El hito **DS-05** modernizó integralmente la interfaz de **Gestión de Cartera y Cobros** (`CarteraPage.jsx`) para el rol de `ADMIN_PROPIEDAD`, elevándola al estándar **Modern Enterprise SaaS / PropTech Premium** establecido en las fases previas de SAED 2.0.

La vista preserva con rigurosidad matemática el 100% de los modelos financieros, endpoints y procedimientos almacenados de Oracle ATP, así como el aislamiento RLS por copropiedad mediante el header `X-Assignment-Id`. Se eliminaron totalmente los iconos heredados de Material Symbols en favor de `lucide-react`, y se incorporó una navegación por pestañas corporativas segmentadas con doble presentación adaptativa (tabla analítica en desktop/tablet y tarjetas con balance segmentado en móviles sin desbordamiento horizontal).

---

## 2. INVARIANTES ABSOLUTAS CERTIFICADAS (10/10)

| # | Invariante Requerida | Estado | Evidencia de Control |
|---|----------------------|:------:|----------------------|
| 1 | **Backend Spring Boot** | 🔒 INTOCADO | 0 archivos modificados en `backend/`. Git status limpio. |
| 2 | **Oracle ATP / Base de Datos** | 🔒 READ-ONLY | Sin DDL, DML ni alteraciones en esquemas financieros. |
| 3 | **RLS / VPD** | 🔒 PRESERVADO | Inyección estricta de `X-Assignment-Id` vía `useTenantApi()`. |
| 4 | **JWT / AuthContext / AuthProvider** | 🔒 INTOCADO | Autenticación, claims y ciclo de vida de tokens inalterados. |
| 5 | **ProtectedRoute** | 🔒 INTOCADO | Guard para `ADMIN_PROPIEDAD` en `/cartera` intacto. |
| 6 | **TenantProvider** | 🔒 INTOCADO | Contexto de asignación y copropiedad activa preservados. |
| 7 | **Roles y Permisos (RBAC)** | 🔒 INTACTO | Matriz de seguridad y permisos inalterada. |
| 8 | **ROLE_HOME / ACCESS_BY_ROLE** | 🔒 INTACTO | Enrutamiento raíz inalterado. |
| 9 | **APIs y Contratos REST** | 🔒 INTOCADO | Cero endpoints nuevos; consumo exacto de `CarteraController` y `PagosController`. |
| 10 | **Landing, Login, AppShell, DS-03 y DS-04** | 🔒 CONGELADOS | Componentes previos inalterados y protegidos de regresiones. |

---

## 3. ARCHIVOS MODIFICADOS Y ARTEFACTOS GENERADOS

- **Archivo Modificado Exclusivamente:**
  - `frontend/src/pages/CarteraPage.jsx`: Reescritura arquitectónica de la capa de presentación y experiencia de usuario bajo las primitivas del Design System de SAED 2.0.
- **Artefactos y Evidencia Visual Generada:**
  - `docs/DS-05_CARTERA_REPORT.md` (este reporte)
  - `docs/screenshots/screenshot-ds05-1440x900.png` (Desktop 1440x900 - Pestaña Unidades)
  - `docs/screenshots/screenshot-ds05-tab-cuotas-1440x900.png` (Desktop 1440x900 - Pestaña Cuotas)
  - `docs/screenshots/screenshot-ds05-tab-antiguedad-1440x900.png` (Desktop 1440x900 - Pestaña Antigüedad)
  - `docs/screenshots/screenshot-ds05-1280x800.png` (Laptop 1280x800)
  - `docs/screenshots/screenshot-ds05-1024x800.png` (Desktop compacto / iPad horizontal)
  - `docs/screenshots/screenshot-ds05-768x1024.png` (Tablet vertical)
  - `docs/screenshots/screenshot-ds05-390x844.png` (Mobile iPhone)
  - `docs/screenshots/screenshot-ds05-390x844-cards.png` (Mobile iPhone - Tarjetas)
  - `docs/screenshots/screenshot-ds05-360x740.png` (Mobile Android)
  - `docs/screenshots/screenshot-ds05-360x740-cards.png` (Mobile Android - Tarjetas)

---

## 4. AUDITORÍA FINANCIERA Y CLARIFICACIÓN SEMÁNTICA DE DOMINIO

Para evitar interpretaciones erróneas de los datos financieros, se documentan formalmente las diferencias semánticas del modelo de cartera en Oracle ATP:

1. **`SALDO_PENDIENTE` (Nivel Cuota - `CUOTAS`):**
   - Representa la obligación residual exigible de una cuota particular generada para un inmueble.
   - Si `FECHA_VENCIMIENTO >= TRUNC(SYSDATE)`, la cuota se clasifica como corriente y no constituye mora.
2. **`ESTADO_CARTERA` (Nivel Unidad - `CARTERA`):**
   - Determinado en el procedimiento de recálculo mediante el desglose de envejecimiento:
     - `AL_DIA`: Inmueble sin saldo vencido (`SALDO_MORA_* = 0`), aunque tenga cuotas corrientes del periodo en curso.
     - `MORA_LEVE`: Saldo vencido entre 1 y 30 días (`SALDO_MORA_30 > 0`).
     - `MORA_MEDIA`: Saldo vencido entre 31 y 60 días (`SALDO_MORA_60 > 0`).
     - `MORA_GRAVE`: Saldo vencido superior a 60/90 días (`SALDO_MORA_90_MAS > 0`).
3. **`TOTAL_CARTERA` vs `TOTAL_MORA`:**
   - `TOTAL_CARTERA`: Sumatoria consolidada de todos los saldos por cobrar (corrientes + mora de todas las unidades).
   - `TOTAL_MORA`: Sumatoria exclusiva de los saldos con fecha de vencimiento expirada (`SALDO_MORA_30 + SALDO_MORA_60 + SALDO_MORA_90_MAS`).
4. **Independencia de Estado:**
   - Una unidad puede registrar cuotas con estado `PENDIENTE` y encontrarse simultáneamente `AL_DIA` en su estado de cartera si las cuotas corresponden al periodo vigente. El frontend respeta esta diferenciación sin alterar ni mutar ningún cálculo proveniente del backend.

---

## 5. ENDPOINTS EXISTENTES CONSUMIDOS

Todas las consultas y mutaciones se canalizan a través de `useTenantApi()`, garantizando el contexto de aislamiento multi-tenant:

1. **`GET /api/v1/cartera`**:
   - Devuelve la lista de unidades con su composición analítica de mora: `ID_CARTERA`, `ID_UNIDAD`, `NUMERO_APARTAMENTO`, `SALDO_CORRIENTE`, `SALDO_MORA_30`, `SALDO_MORA_60`, `SALDO_MORA_90_MAS`, `SALDO_TOTAL`, `FECHA_CORTE`, `ESTADO_CARTERA`.
2. **`GET /api/v1/cartera/resumen`**:
   - Agregados ejecutivos: `TOTAL_UNIDADES`, `TOTAL_CARTERA`, `TOTAL_MORA`, `COUNT_AL_DIA`, `COUNT_MORA_LEVE`, `COUNT_MORA_MEDIA`, `COUNT_MORA_GRAVE`.
3. **`GET /api/v1/cartera/antiguedad`**:
   - Distribución por rangos cronológicos: `RANGO` (`VIGENTE`, `0-30 dias`, `31-60 dias`, `61-90 dias`, `90+ dias`), `CANTIDAD_CUOTAS`, `TOTAL_SALDO`.
4. **`GET /api/v1/cuotas`**:
   - Cuentas de cobro individuales para conciliación detallada: `id`, `idUnidad`, `numeroApartamento`, `nombreResidente`, `periodo`, `valorTotal`, `saldoPendiente`, `fechaLimite`, `estado`.
5. **`POST /api/v1/cartera/recalcular`**:
   - Ejecuta la sentencia `MERGE INTO CARTERA` en Oracle ATP reconciliando los estados a partir de `CUOTAS` y `PAGOS` con auditoría financiera de alta severidad.

---

## 6. ARQUITECTURA DE PRESENTACIÓN Y EXPERIENCIA DE USUARIO

### Primitivas del Design System Incorporadas
- **`PageContainer`**: Espaciado exterior e interior responsivo con márgenes de grado empresarial.
- **Contextual Enterprise Header**:
  - Título y subtítulo descriptivos.
  - Badge de alcance de rol: `ADMIN_PROPIEDAD`.
  - Botón de refresco manual consolidado (`Actualizar`).
  - Botón de acción primaria financiera: `Recalcular Cartera` con animación de spinner reactivo durante la ejecución asíncrona y notificación vía toast de `sonner`.
- **KPI Metrics Strip (`MetricCard`)**:
  1. *Total Cartera*: Importe total por cobrar en la copropiedad (Icono `Wallet`, variante `primary`).
  2. *Total en Mora*: Deuda vencida acumulada (Icono `ShieldAlert`, variante `warning`).
  3. *Unidades al Día*: Inmuebles sin obligaciones en mora (Icono `CheckCircle2`, variante `success`).
  4. *Unidades en Mora*: Inmuebles que requieren gestión activa de cobro (Icono `AlertTriangle`, variante `danger` si > 0).

### Navegación por Pestañas Corporativas
1. **Pestaña 1: Cartera por Unidad (`Building`)**:
   - Búsqueda en tiempo real por número de apartamento.
   - Filtro reactivo por estado de cartera (`TODOS`, `AL_DIA`, `EN_MORA`, `MORA_LEVE`, `MORA_MEDIA`, `MORA_GRAVE`).
   - **Vista Desktop (`hidden md:block`)**: Tabla financiera con columnas analíticas de mora (Corriente, Mora 30d, Mora 60d, Mora 90d+, Saldo Total en negrita, Badge de Estado y Fecha de Corte).
   - **Vista Móvil (`md:hidden`)**: Lista de tarjetas individuales adaptativas con cabecera de inmueble, badge de estado, saldo total destacado, grilla de envejecimiento de 2 columnas y fecha de corte. Cero desbordamiento horizontal (`overflow-x: hidden`).
2. **Pestaña 2: Detalle de Cuotas (`Receipt`)**:
   - Búsqueda por concepto, apartamento o nombre de titular.
   - Filtro por estado de cuota (`TODOS`, `PENDIENTE`, `PAGADO`, `VENCIDA`).
   - Tabla detallada con desglose de Periodo, Valor Facturado, Saldo Pendiente y Badge de Estado.
3. **Pestaña 3: Antigüedad de Cartera (`BarChart3`)**:
   - Gráfico de barras horizontales con progreso porcentual calculado sobre la base de cartera total.
   - Colores semánticos según criticidad (Esmeralda para `VIGENTE`, Ámbar para `0-30 dias`, Rosa/Rojo para mora prolongada).
   - Contador de cuotas y saldo monetario formateado.

---

## 7. VERIFICACIÓN TÉCNICA Y CALIDAD DE CÓDIGO

- **Linter (ESLint):**
  ```bash
  npx eslint src/pages/CarteraPage.jsx
  # Resultado: 0 errors, 0 warnings (100% limpio)
  ```
- **Build de Producción:**
  ```bash
  npm run build
  # CarteraPage-DWUFOWgj.js: 21.99 kB │ gzip: 5.45 kB
  # built in 6.88s — 0 errores
  ```
- **Iconografía:** Exclusivamente `lucide-react` (`Wallet`, `ShieldAlert`, `CheckCircle2`, `AlertTriangle`, `Building`, `Receipt`, `BarChart3`, `RefreshCw`, `Search`, `X`, `FileText`, `Clock`). Cero `material-symbols-outlined`.

---

## 8. CONCLUSIÓN Y SIGUIENTE PASO

El hito **DS-05** queda oficialmente **CERTIFICADO**. Las interfaces de Landing, Login, AppShell (DS-02), Dashboard ADMIN_PROPIEDAD (DS-03), Gestión de Residentes (DS-04) y Cartera/Cobros (DS-05) se encuentran ahora completamente unificadas y protegidas contra regresiones.
