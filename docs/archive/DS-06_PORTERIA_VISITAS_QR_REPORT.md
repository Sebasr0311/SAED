# SAED 2.0 — REPORTE TÉCNICO DS-06
## REDISEÑO OPERATIVO DE PORTERÍA, VISITAS Y ACCESO QR (ROL PORTERO)

**Fecha:** 4 de Septiembre de 2026  
**Fase:** Design System Interno — Hito DS-06  
**Alcance:** Exclusivo a `frontend/src/pages/EscannerQRPage.jsx` y `frontend/src/pages/PorteroDashboardPage.jsx`  
**Referencia Visual:** Congelada desde Landing, Login, AppShell (DS-02), Dashboard (DS-03), Residentes (DS-04) y Cartera (DS-05)  
**Estado:** ✅ **CERTIFICADO / LISTO PARA PRODUCCIÓN**

---

## 1. RESUMEN EJECUTIVO

El hito **DS-06** transformó el módulo de **Portería, Control de Visitas y Validación QR** en el showcase operativo por excelencia de SAED 2.0, elevando la experiencia del rol `PORTERO` a los más exigentes estándares de **Modern Enterprise SaaS / PropTech Premium**.

La modernización se concentró en la consola operativa de garita (`EscannerQRPage.jsx`) y en el tablero principal del portero (`PorteroDashboardPage.jsx`). Se implementó un visor de escaneo de grado industrial con HUD de brackets y animación de barrido láser, captura por cámara en tiempo real con selector de dispositivos, entrada manual por token/USB, acreditación de identidad con badge semántico, control vehicular con validación de placas y confirmación de cupo de parqueadero asignado (`V-XX`), gestión de salidas con liberación de cupos en un clic, y grilla en tiempo real de parqueaderos para visitantes.

Se eliminaron al 100% los iconos legados de Material Symbols sustituyéndolos por `lucide-react`, se aseguraron touch targets táctiles de al menos 44px para pantallas táctiles de garita/tablets, y se garantizó cero desbordamiento horizontal en resoluciones desde 360px hasta 1440px+.

---

## 2. INVARIANTES ABSOLUTAS CERTIFICADAS (10/10)

| # | Invariante Requerida | Estado | Evidencia de Control |
|---|----------------------|:------:|----------------------|
| 1 | **Backend Spring Boot** | 🔒 INTOCADO | 0 archivos modificados en `backend/`. Git status limpio. |
| 2 | **Oracle ATP / Base de Datos** | 🔒 READ-ONLY | Cero DDL, DML ni alteraciones de esquema o procedimientos (`SP_VALIDAR_CONSUMIR_QR`). |
| 3 | **RLS / VPD** | 🔒 PRESERVADO | Aislamiento estricto por copropiedad vía `X-Assignment-Id` y contexto de sesión Oracle. |
| 4 | **JWT / AuthContext / AuthProvider** | 🔒 INTOCADO | Autenticación, claims y ciclo de vida de tokens inalterados. |
| 5 | **ProtectedRoute** | 🔒 INTOCADO | Guards de rol para `PORTERO` en `/escanner-qr` y `/portero-dashboard` intactos. |
| 6 | **TenantProvider** | 🔒 INTOCADO | Contexto de asignación y copropiedad activa preservados. |
| 7 | **Roles y Permisos (RBAC)** | 🔒 INTACTO | Matriz de seguridad y permisos de `PORTERO` inalterada. |
| 8 | **ROLE_HOME / ACCESS_BY_ROLE** | 🔒 INTACTO | Redirecciones funcionales de inicio inalteradas (`/portero-dashboard`). |
| 9 | **APIs y Contratos REST** | 🔒 INTOCADO | Cero endpoints nuevos; consumo exacto de `PorteriaController` y `ParqueaderoController`. |
| 10 | **Landing, Login, AppShell, DS-03, DS-04, DS-05** | 🔒 CONGELADOS | Componentes previos inalterados y protegidos de cualquier regresión. |

---

## 3. ARCHIVOS MODIFICADOS Y ARTEFACTOS GENERADOS

### Archivos Modificados Exclusivamente en Frontend:
1. **`frontend/src/pages/EscannerQRPage.jsx`**:
   - Reingeniería completa de la consola de escaneo de garita con primitivas del Design System (`PageContainer`, `MetricCard`, Tabs corporativos).
   - Visor de cámara en vivo con brackets HUD `[ ]`, línea de barrido animada y selector reactivo de cámaras/webcams.
   - Pestaña de Acreditación y Autorización inmediata con selección vehicular (`A_PIE`, `CARRO`, `MOTO`, `BICICLETA`, `OTRO`), validación de placa y confirmación visual de cupo asignado.
   - Pestaña de *Visitas Dentro / Salida* con búsqueda reactiva, vista de tabla para desktop y tarjetas para móviles, y botón táctil de marcado de salida.
   - Pestaña de *Cupos de Parqueadero* con visualización de bahías de visitantes y badges semánticos (`DISPONIBLE`, `OCUPADO`, `EN_MANTENIMIENTO`).
   - Pestaña de *Historial de Accesos* con bitácora cronológica.
2. **`frontend/src/pages/PorteroDashboardPage.jsx`**:
   - Integración de `PageContainer` y `MetricCard` eliminando componentes legados.
   - Sustitución total de `material-symbols-outlined` por `lucide-react` (`QrCode`, `Package`, `Car`, `ShieldCheck`, `Volume2`, `Gavel`, etc.).
   - Corrección de endpoint: resolución hacia `/api/v1/porteria/visitas-resumen` eliminando petición errónea 404 a `/porteria/visitas-resumen/hoy`.
   - Hero card corporativa con acceso directo al "Centro de Validación y Control de Acceso QR".

### Evidencia Visual y Capturas Generadas (11 capturas):
- `docs/screenshots/screenshot-ds06-1440x900.png` (Desktop 1440x900 - Consola Inicial de Escaneo QR)
- `docs/screenshots/screenshot-ds06-validated-1440x900.png` (Desktop 1440x900 - QR Validado, Acreditación y Selección Vehicular)
- `docs/screenshots/screenshot-ds06-tab-salidas-1440x900.png` (Desktop 1440x900 - Pestaña Visitas Dentro y Registro de Salida)
- `docs/screenshots/screenshot-ds06-tab-parqueaderos-1440x900.png` (Desktop 1440x900 - Pestaña Estado de Parqueaderos Visitantes)
- `docs/screenshots/screenshot-ds06-portero-dashboard-1440x900.png` (Desktop 1440x900 - Tablero de Mando del Portero)
- `docs/screenshots/screenshot-ds06-1280x800.png` (Laptop 1280x800)
- `docs/screenshots/screenshot-ds06-1024x800.png` (Tablet Paisaje / iPad 1024x800)
- `docs/screenshots/screenshot-ds06-768x1024.png` (Tablet Retrato 768x1024)
- `docs/screenshots/screenshot-ds06-390x844.png` (Mobile iPhone 390x844 - Consola QR con visor)
- `docs/screenshots/screenshot-ds06-390x844-salidas.png` (Mobile iPhone 390x844 - Pestaña Salidas en Tarjetas)
- `docs/screenshots/screenshot-ds06-360x740.png` (Mobile Android 360x740)

---

## 4. FLUJO OPERATIVO Y ENDPOINTS CONSUMIDOS

La consola de portería implementa el ciclo completo de control de acceso físico en estricto apego al contrato de la API y lógica de Oracle:

```
[VALIDAR QR / TOKEN] ──> [ACREDITAR IDENTIDAD] ──> [SELECCIONAR TRANSPORTE] ──> [REGISTRAR ENTRADA] ──> [PARQUEADERO ASIGNADO]
                                                                                                                   │
[LIBERAR PARQUEADERO] <── [MARCAR SALIDA] <── [LISTA VISITAS DENTRO] <────────────────────────────────────────────┘
```

### Endpoints Certificados:
1. **`POST /api/v1/porteria/qr/validar`**:
   - Payload: `{ codigoQr: string }` o `{ token: string }`.
   - Respuesta: `{ valido: boolean, mensaje: string, idVisita: number, fechaExpiracion: string, nombreVisitante: string, documentoVisitante: string, nombreResidente: string, numeroApartamento: string, notas: string }`.
2. **`POST /api/v1/porteria/qr/entrada`**:
   - Payload: `{ codigoQr: string, medioTransporte: string, placa: string, descripcion: string }`.
   - Efecto en Oracle: Ejecuta `SP_VALIDAR_CONSUMIR_QR`, consume el QR atómicamente (o decrementa usos si es recurrente), crea registro en `REGISTRO_ACCESOS`, pasa la visita a `EN_CURSO` y si es `CARRO`/`MOTO`, reserva el primer cupo `DISPONIBLE` en `PARQUEADEROS` marcándolo `OCUPADO`.
   - Respuesta: `{ success: true, mensaje: string, parqueadero: "V-03" }`.
3. **`POST /api/v1/porteria/qr/notificar`**:
   - Payload: `{ codigoQr: string, fotoCaptura: string }`.
   - Notifica al anfitrión residente la presencia del visitante en portería.
4. **`PUT /api/v1/porteria/visitas/{id}/salida`**:
   - Marca la visita como `FINALIZADA` en Oracle y libera automáticamente el parqueadero asociado regresándolo a estado `DISPONIBLE`.
5. **`GET /api/v1/porteria/visitas-resumen`**:
   - Carga la bitácora activa de visitas y autorizaciones para la copropiedad actual.
6. **`GET /api/v1/parqueaderos`**:
   - Devuelve la lista en tiempo real de `ParqueaderoDTO` (`id`, `codigo`, `tipo`, `estado`, `numeroApartamento`).

---

## 5. EXPERIENCIA DE USUARIO Y ERGONOMÍA OPERATIVA

- **Cabecera Contextual de Garita:** Indicador visual de garita en vivo con punto verde pulsante ("Garita Activa"), badge de rol `PORTERO`, selector desplegable de dispositivo de captura de video y botón de actualización manual.
- **Tira de KPIs Operativos (`MetricCard`):**
  1. *Visitas en Curso*: Visitantes activos dentro del recinto (Icono `UserCheck`, variante `primary`).
  2. *Total Activas / Hoy*: Concurrencia diaria de accesos (Icono `LogIn`, variante `info`).
  3. *Cupos Visitantes Libres*: Disponibilidad de parqueadero en tiempo real (Icono `Car`, variante `success`).
  4. *Salidas Pendientes*: Registro de salidas por confirmar (Icono `LogOut`, variante `warning`).
- **Visor HUD de Grado Industrial:** Marco con esquinas anguladas estilo militar/seguridad cibernética (`border-emerald-500`), haz láser animado en CSS (`animate-scan`), selector de linterna/flash para escáneres compatibles, y atajo táctil "Pegar del portapapeles" para lectores USB de pistola/código de barras.
- **Acreditación Guiada:** Tras validar, se despliega la tarjeta de identidad del visitante con datos del anfitrión y apartamento, y formulario de vehículo con botones táctiles grandes (≥44px) para `A Pie`, `Carro`, `Moto`, `Bici` y `Otro`. Si se selecciona vehículo, se exige la placa antes de autorizar.
- **Adaptabilidad Móvil Completa:** En resoluciones pequeñas, la tabla analítica se conmuta automáticamente a una lista de tarjetas corporativas compactas, optimizadas para tablets y teléfonos de guardas de seguridad.

---

## 6. VERIFICACIÓN TÉCNICA Y CALIDAD DE CÓDIGO

### Linter (ESLint):
```bash
npx eslint src/pages/EscannerQRPage.jsx src/pages/PorteroDashboardPage.jsx
# Resultado: 0 errors, 0 warnings (100% limpio)
```

### Build de Producción (Vite):
```bash
npm run build
# vite v5.4.14 building for production...
# dist/assets/PorteroDashboardPage-CqtGrHwy.js   20.17 kB │ gzip:  4.96 kB
# dist/assets/EscannerQRPage-DeIE-gqu.js         36.18 kB │ gzip: 10.35 kB
# built in 17.52s — 0 errores
```

### Verificación de Pruebas Automatizadas de Backend:
Se ejecutó la suite de autorización adversaria de portería en Java 24:
```bash
mvn test -Dtest=PorteroAdversarialAuthorizationTest
# [INFO] Running com.saed.backend.porteria.PorteroAdversarialAuthorizationTest
# [INFO] Tests run: 42, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 32.89 s
# [INFO] BUILD SUCCESS
```

---

## 7. CONCLUSIÓN Y ESTADO DE LA SUITE

El hito **DS-06** queda formalmente **CERTIFICADO**. Las interfaces operativas del rol `PORTERO` (`EscannerQRPage` y `PorteroDashboardPage`) han alcanzado la madurez visual y ergonómica de **SAED 2.0**, cerrando la cadena operativa junto con Landing, Login, AppShell (DS-02), Dashboard (DS-03), Residentes (DS-04) y Cartera (DS-05).
