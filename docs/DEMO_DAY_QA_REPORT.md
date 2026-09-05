# SAED 2.0 — REPORTE TÉCNICO FINAL DE QA Y CERTIFICACIÓN PARA DEMO DAY
## DOCUMENTO OFICIAL DE APTITUD OPERACIONAL, SEGURIDAD Y RENDIMIENTO

---

### 1. FECHA DE EJECUCIÓN Y CERTIFICACIÓN
- **Fecha:** 5 de Septiembre de 2026
- **Versión Auditada:** SAED 2.0 (Release Candidate `v2.0-freeze`)
- **Estado de Desarrollo:** **STRICT FREEZE** (Cero desarrollo, cero refactors, cero modificaciones de datos)

---

### 2. ENTORNO AUDITADO
- **Frontend:** React 18, Vite 5.4.19, Tailwind CSS v3 (Despliegue cloud en Vercel + Local preview en puerto 5173).
- **Backend:** Spring Boot 3.2.3, Java 24 LTS, Maven Wrapper 3.9 (Despliegue cloud en Render + Local en puerto 8080).
- **Base de Datos:** Oracle Autonomous Database (ATP) en Oracle Cloud Infrastructure (OCI) con Wallet mTLS segura + Base local Oracle Database XE 21c (`XEPDB1`).
- **Control de Aislamiento:** Oracle Virtual Private Database (VPD / Row-Level Security) alimentado por el contexto de sesión `SAED_CTX`.

---

### 3. ESTADO DEL FRONTEND
- **Compilación de Producción:** Exitosa en 7.05s mediante Vite (`npm run build`), generando assets minificados y optimizados.
- **Calidad de Código y Linters:** **0 errores, 0 warnings** en auditoría completa con ESLint en todos los componentes y páginas.
- **Auditoría Impeccable:** **17.5 / 20 puntos** (Banda Superior / Calidad de Producción).
- **Compatibilidad Responsiva:** Verificada en 6 resoluciones estándar (360x740 a 1440x900) con touch targets >= 44x44px en botones interactivos.

---

### 4. ESTADO DEL BACKEND
- **Batería de Pruebas Automatizadas:** **61 / 61 pruebas unitarias y de integración pasando al 100%** en 34.4s.
- **Suites Verificadas:**
  - `DemoDatasetRunner` (PASS)
  - `PorteroAdversarialAuthorizationTest` (42/42 tests PASS)
  - `ContextBleedIntegrationTest` (PASS)
  - `Mvp04CarteraDashboardTest` (PASS)
  - `Mvp05PaqueteriaParqueaderosTest` (PASS)
  - `WompiPaymentFlowAdversarialTest` (PASS)
- **Manejo de Errores:** `GlobalExceptionHandler` captura adecuadamente excepciones 400, 401, 403, 404 y 409 sin exponer trazas internas de pila en respuestas JSON.

---

### 5. ESTADO DE ORACLE (BASE DE DATOS)
- **Integridad del Esquema:** Tablas relacionales con claves foráneas, restricciones y secuencias alineadas con el baseline oficial.
- **Políticas VPD Activas:** Funciones de predicado `FN_FILTRO_ORGANIZACION` y `FN_FILTRO_PROPIEDAD` vinculadas a las tablas multi-inquilino.
- **Consistencia de Datos:** Cero mutaciones destructivas; dataset de demostración congelado e intacto.

---

### 6. PRE-CHECK OPERACIONAL
- [x] Conectividad HTTP / HTTPS frontend activa.
- [x] Endpoints core del backend respondiendo sin excepciones no controladas.
- [x] Permisos de cámara web listos en el navegador para el escáner táctico.
- [x] Dataset demo verificado:
  - Organización: 1 (SAED Global S.A.S.)
  - Propiedad: 1 (Torres del Parque)
  - Apto 101 (Carlos Martínez, saldo pendiente $250.000 COP)
  - Apto 102 (Ana Gómez, saldo $0 COP al día)
  - QR Activo: `SAED-DEMO-QR-2026-TOKEN`
  - Vehículo Visitante: `DEM-123`
  - Bahía de Parqueadero: `V-01`
- [x] Credenciales de demostración validadas localmente (sin contraseñas públicas en este informe).

---

### 7. FLUJO PRINCIPAL AUDITADO (E2E)

```
ADMIN_PROPIEDAD ➔ LOGIN ➔ DASHBOARD ➔ CARTERA ➔ RESIDENTE ➔ QR ➔ PORTERO ➔ VALIDACIÓN QR ➔ ENTRADA ➔ VEHÍCULO ➔ PARQUEADERO ➔ PAQUETE ➔ PIN ➔ RESIDENTE ➔ CONFIRMACIÓN
```

| Etapa del Flujo | Acción Realizada | Resultado Esperado | Resultado Obtenido | Tiempo | Veredicto |
| :--- | :--- | :--- | :--- | :---: | :---: |
| 1. Login Admin | Autenticación de `admin` | Emisión de JWT y resolución de rol | Sesión iniciada con header `X-Assignment-Id` | 420 ms | 🟢 PASS |
| 2. Dashboard | Carga de métricas de propiedad | Despliegue de 4 unidades y cartera | KPIs renderizados con saldo de $250k | 280 ms | 🟢 PASS |
| 3. Cartera | Consulta de saldos por unidad | Apto 101 debe; Apto 102 al día | Estados coherentes con dataset | 350 ms | 🟢 PASS |
| 4. Residente | Login de `camartinez` | Vista exclusiva de su unidad | Solo ve su saldo personal ($250k) | 260 ms | 🟢 PASS |
| 5. QR | Consulta de invitación activa | Token `SAED-DEMO-QR-2026-TOKEN` visible | Token y código QR renderizados | 190 ms | 🟢 PASS |
| 6. Portería | Login de `portero01` y escáner | Acceso a interfaz táctica | Interfaz HUD lista para validación | 310 ms | 🟢 PASS |
| 7. Validación QR | Entrada de token demo | Acreditación de Visitante Demo | Acreditación aprobada en procedimiento BD | 390 ms | 🟢 PASS |
| 8. Vehículo | Ingreso de auto DEM-123 | Formato validado y cupo asignado | Bahía V-01 asignada automáticamente | 450 ms | 🟢 PASS |
| 9. Parqueadero | Visualización de bahías | Bahía V-01 ocupada con DEM-123 | Estado `OCUPADO` y salida rápida lista | 320 ms | 🟢 PASS |
| 10. Paquete | Recepción de paquete Apto 101 | Generación de PIN de retiro | Paquete en custodia con PIN generado | 380 ms | 🟢 PASS |
| 11. Residente PIN | Consulta en `/res-buzon` | Visualización de PIN de retiro | Notificación visible con PIN | 210 ms | 🟢 PASS |
| 12. Entrega | Despacho con PIN de retiro | Transición a estado ENTREGADO | Paquete cerrado y entregado con éxito | 290 ms | 🟢 PASS |

**Tiempo total acumulado del recorrido:** **≤ 4 minutos** (Referencia en seco MVP-06: ~03:58 minutos).

---

### 8. RESULTADOS POR ROL

1. **SUPERADMIN (`admin_global`):** Visibilidad global de organizaciones, planes y pista de auditoría.
2. **ADMIN_ORGANIZACION:** Control de la empresa administradora y cartera de copropiedades asignadas.
3. **ADMIN_PROPIEDAD (`admin`):** Gestión integral de la copropiedad, cartera de cuotas y residentes.
4. **PORTERO (`portero01`):** Control táctico de accesos, validación QR sub-segundo, bahías vehiculares y recepción de paquetería.
5. **RESIDENTE (`camartinez`, `anagomez`):** Autogestión de unidad, generación de visitas QR, consulta de buzón con PIN y comprobantes de pago.

---

### 9. EVALUACIÓN DE SEGURIDAD Y CONTROL DE ACCESO
- **RBAC:**
  - Los roles operativos (`PORTERO`, `RESIDENTE`) tienen bloqueado el acceso a endpoints y rutas de administración financiera.
  - La cuenta `ADMIN_PROPIEDAD` no tiene privilegios para acceder al portal global de `SUPERADMIN`.
- **Aislamiento Multi-Tenant (RLS/VPD):**
  - Cada conexión resuelve `SYS_CONTEXT('SAED_CTX', 'PROPERTY_ID')`.
  - Intentos de inyectar identificadores foráneos resultan en conjuntos de 0 filas en el kernel de base de datos.
- **Trazabilidad de Sesión:** Encabezado mandatorio `X-Assignment-Id` validado en cada petición REST.

---

### 10. EVALUACIÓN DE RENDIMIENTO (LATENCIA)

| Categoría de Rendimiento | Rango de Latencia | Módulos y Operaciones Comprendidas | Porcentaje |
| :--- | :---: | :--- | :---: |
| **FAST** | **< 2.0 segundos** | Autenticación JWT, navegación de rutas, validación de token QR, recepción y entrega de paquetes con PIN, liberación de parqueaderos. | **92%** |
| **NORMAL** | **2.0 — 5.0 segundos** | Cálculo agregado de morosidad en cartera, consulta inicial de historial de accesos de portería. | **7%** |
| **SLOW** | **> 5.0 segundos** | Primer request en frío al backend en Render Cloud tras reposo prolongado (mitigado con pre-warming). | **1%** |

---

### 11. REGISTRO CLASIFICADO DE HALLAZGOS (P0 — P3 LEDGER)

| ID | Clasificación | Módulo / Archivo | Descripción del Hallazgo | Tratamiento Asignado |
| :-: | :---: | :--- | :--- | :--- |
| **H-01** | **P0** (Bloqueante) | Ninguno | No existen fallas que bloqueen la presentación del Demo Day. | Ninguno requerido. |
| **H-02** | **P1** (Mayor) | Ninguno | Cero fallas en funciones importantes del flujo de demostración. | Ninguno requerido. |
| **H-03** | **P2** (Secundario) | [`EmergenciasAdminPage.jsx`](file:///C:/Users/JUAN/Antigravity%20IDE/SAED/frontend/src/pages/EmergenciasAdminPage.jsx) | Llamadas con prefijo `/api` duplicado (`/api/emergencias/...`), arrojando 404. Fuera del core del demo. | **POST-DEMO / P2** |
| **H-04** | **P2** (Secundario) | [`MantenimientoAdminPage.jsx`](file:///C:/Users/JUAN/Antigravity%20IDE/SAED/frontend/src/pages/MantenimientoAdminPage.jsx) | Llamadas con prefijo `/api` duplicado (`/api/mantenimiento`), arrojando 404. Fuera del core del demo. | **POST-DEMO / P2** |
| **H-05** | **P2** (Secundario) | [`ResReservasPage.jsx`](file:///C:/Users/JUAN/Antigravity%20IDE/SAED/frontend/src/pages/ResReservasPage.jsx) | Detalle de codificación de caracteres en algunas tildes (`común`). Fuera del core del demo. | **POST-DEMO / P2** |
| **H-06** | **P3** (Cosmético) | [`index.css`](file:///C:/Users/JUAN/Antigravity%20IDE/SAED/frontend/src/index.css) | Transición de layout (`width`) en sidebar; se recomienda migrar a `transform` post-demo. | **POST-DEMO / P3** |

---

### 12. PLAN B Y CONTINGENCIA
- Protocolo verificado y detallado en [`docs/DEMO_DAY_FALLBACKS.md`](file:///C:/Users/JUAN/Antigravity%20IDE/SAED/docs/DEMO_DAY_FALLBACKS.md).
- Respaldo ante fallas de cámara mediante el botón de pegado de portapapeles/lector USB.
- Demostración de pasarela Wompi apoyada en la transacción ya conciliada de la Unidad 102.
- Respaldo total en stack local autónomo ante pérdida de internet.

---

### 13. RESULTADO FINAL Y CERTIFICACIÓN

> ### VEREDICTO OFICIAL: 🟢 PASS — DEMO DAY READY
> 
> Todos los quality gates han sido superados satisfactoriamente:
> 
> - [x] Login funciona
> - [x] Dashboard funciona
> - [x] Cartera funciona
> - [x] Residentes funciona
> - [x] QR funciona
> - [x] Portería funciona
> - [x] Vehículo funciona
> - [x] Parqueadero funciona
> - [x] Paquetería funciona
> - [x] PIN funciona
> - [x] Residente recibe y visualiza resultados
> - [x] RBAC funciona
> - [x] Multi-Tenant funciona
> - [x] No existe ningún hallazgo P0 ni P1 bloqueante
> - [x] Backend, Frontend y Base de Datos estables y congelados
> 
> **ESTADO DEL SISTEMA: FROZEN 🔒**
