# DEMO-03 — REPORTE DE LIVE PRODUCTION SMOKE TEST
## CERTIFICACIÓN FINAL DEMO DAY — SAED 2.0

**Fecha de Ejecución:** 05 de Septiembre de 2026 — 10:25 COT  
**Auditor Responsable:** Senior Software Architect & Lead Security Reviewer  
**Entorno Objetivo:** Cloud Production (En Vivo)  
**Topología en Nube:** Vercel CDN → Render Web Service (Spring Boot 3) → Oracle ATP 19c Cloud (RLS / VPD)  
**Estado de Freeze:** Activo (Cero modificaciones de código fuente, cero refactorizaciones, cero alteraciones de DDL/DML)  
**Veredicto Oficial:** **`🔴 LIVE DEMO NOT CERTIFIED (CLOUD PRODUCTION)`**  
**Estrategia Recomendada para el Demo Day:** **`🟢 CONTINGENCIA RECOMENDADA: EJECUCIÓN SOBRE REPLICA CERTIFICADA (MVP-07 100% PASS)`**

---

### 1. Resumen Ejecutivo

El propósito de la fase **DEMO-03** fue validar de forma exhaustiva, no destructiva y en tiempo real el comportamiento del MVP de SAED 2.0 sobre la infraestructura cloud en producción (Vercel, Render y Oracle Autonomous Database ATP 19c), garantizando la viabilidad del flujo de presentación ante el jurado del Demo Day.

Tras auditar 32 casos de prueba en vivo (telemetría HTTP, navegación Playwright, sesiones JWT y consultas RLS):
- **27 de 32 casos resultaron exitosos (84.4% PASS).**
- El frontend en Vercel opera de forma impecable: CDN con TTFB ≤ 120ms, bundles optimizados, diseño responsive certificado (390x844 y desktop 1440x900) y cero excepciones críticas en la consola del navegador.
- El backend en Render se encuentra activo y responde en caliente entre 200ms y 1.700ms.
- El aislamiento multi-tenant y la seguridad de contexto `SAED_CTX` en Oracle ATP operan correctamente en modo `ACCESSED LOCALLY` (PGA Session-Local).
- **Causa Raíz Bloqueante en Producción Cloud:** La base de datos Oracle ATP Cloud conserva el esquema y datos iniciales de la migración C6.1.2 y no tiene aplicados los scripts finales de semillas (`V5.99__demo_seeds.sql`) ni las extensiones de esquema para tablas de visitas y vehículos. Como resultado:
  1. La tabla `ROLES` en Oracle ATP no cuenta con el rol `PORTERO`.
  2. Los usuarios `camartinez` (residente demo) y `portero01` (portero demo) no tienen asignaciones activas en `USUARIO_ASIGNACIONES` dentro de Oracle ATP (`rol: null`), impidiendo el acceso a sus respectivos dashboards.
  3. Las consultas a `/api/v1/visitas`, `/api/v1/porteria/visitas-resumen` y `/api/v1/parqueaderos` retornan HTTP 500 (`DATABASE_ERROR`) por tablas o columnas ausentes en la instancia ATP de la nube.

---

### 2. Topología de Infraestructura Auditada

```
┌─────────────────────────────────────────────────────────────┐
│                       USUARIO / JURADO                      │
└──────────────────────────────┬──────────────────────────────┘
                               │ HTTPS / TLS 1.3
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                    FRONTEND: VERCEL CDN                     │
│               https://saedfront.vercel.app                  │
│  - Edge Network: Cloudflare / Vercel (POP: BOG / IAD1)      │
│  - TTFB: 63ms · HTTP 200 OK · Cache HIT                     │
│  - Configurado contra: https://saed-backend.onrender.com    │
└──────────────────────────────┬──────────────────────────────┘
                               │ REST / JSON (Bearer JWT)
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                 BACKEND: RENDER PAAS                        │
│          https://saed-backend.onrender.com/api/v1           │
│  - Runtime: OpenJDK 24 / Spring Boot 3.3                   │
│  - Conexión: HikariCP + TCPS (Oracle Cloud Wallet)          │
│  - Latencia en Caliente: 200ms - 1.700ms                    │
│  - Cold-Start Inicial: 66.0s (Suspensión Free Tier)         │
└──────────────────────────────┬──────────────────────────────┘
                               │ TCPS (Puerto 1522 / mTLS)
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                 DATABASE: ORACLE ATP CLOUD                  │
│       Host: adb.sa-bogota-1.oraclecloud.com:1522            │
│       Servicio: geadcc8b471d081_saed2_high                  │
│  - Contexto: SAED_CTX (TYPE: ACCESSED LOCALLY)              │
│  - Seguridad: Virtual Private Database (RLS / VPD)          │
│  - Esquema: SAED_APP / SAED_SEC_MASTER                      │
└─────────────────────────────────────────────────────────────┘
```

> [!NOTE]
> Se descubrió que el host `sistema-administracion-edificios.onrender.com` reportó `503 Service Suspended` por el proveedor Render, mientras que el endpoint productivo activo configurado en el bundle de Vercel es **`https://saed-backend.onrender.com`**.

---

### 3. Telemetría de Disponibilidad y Cold-Start

| Componente | Métrica Medida | Tiempo Registrado | Calificación | Observaciones |
| :--- | :--- | :---: | :---: | :--- |
| **Vercel CDN** | TTFB (Time to First Byte) | 63 ms | **Excelente** | Servido desde edge caché (BOG). |
| **Vercel CDN** | Descarga de Bundle JS | 185 ms | **Excelente** | Gzip/Brotli activo, scripts diferidos. |
| **Render Web** | Cold-Start (Wake-up) | 66.020 ms (66s) | **Previsto** | Reactivación del contenedor desde estado *idle*. |
| **Render Web** | Latencia Login en Caliente | 1.430 ms | **Aceptable** | Cifrado BCrypt + resolución PL/SQL en Oracle ATP. |
| **Render Web** | Consulta de Unidades / Personas | 420 ms | **Excelente** | `JdbcTemplate` con filtrado RLS en PGA privada. |
| **Render Web** | Consulta de Cartera y Cuotas | 850 ms | **Excelente** | Cálculo agregado de saldos y morosidad. |

---

### 4. Matriz de Roles y Autenticación en la Nube

Se evaluó la autenticación en vivo contra `https://saed-backend.onrender.com/api/v1/auth/login`:

| Rol | Usuario | Password | Status HTTP | ID Usuario | Rol Retornado | Asignación en Oracle ATP | Estado en Nube |
| :--- | :--- | :--- | :---: | :---: | :---: | :---: | :---: |
| **SUPERADMIN** | `admin_global` | `admin_global123` | **200 OK** | 1 | `SUPERADMIN` | `ID: 1` · `GLOBAL` | 🟢 Operativo |
| **ADMIN_PROPIEDAD** | `admin` | `admin123` | **200 OK** | 5 | `ADMIN_PROPIEDAD` | `ID: 205` · Propiedad 1 · Org 1 | 🟢 Operativo |
| **ADMIN_PROPIEDAD** | `residente_hor` | `admin123` | **200 OK** | 2 | `ADMIN_PROPIEDAD` | `ID: 102` · Propiedad 1 · Org 1 | 🟢 Operativo |
| **RESIDENTE** | `residente_sol` | `admin123` | **200 OK** | 3 | `RESIDENTE` | `ID: 104` · Propiedad 2 · Org 2 | 🟢 Operativo |
| **RESIDENTE (Demo)**| `camartinez` | `admin123` | **200 OK** | 4 | `null` | **Sin Asignación Activa** | 🔴 Incompleto |
| **PORTERO (Demo)** | `portero01` | `admin123` | **200 OK** | 6 | `null` | **Sin Asignación Activa** | 🔴 Incompleto |

#### Hallazgo Estructural en Base de Datos de Producción (Oracle ATP):
La consulta directa al catálogo de roles (`GET /api/v1/roles`) evidenció:
```json
[
  { "ID_ROL": 1, "CODIGO": "SUPERADMIN", "ALCANCE": "GLOBAL" },
  { "ID_ROL": 2, "CODIGO": "ADMIN_ORGANIZACION", "ALCANCE": "ORGANIZACION" },
  { "ID_ROL": 3, "CODIGO": "ADMIN_PROPIEDAD", "ALCANCE": "PROPIEDAD" },
  { "ID_ROL": 4, "CODIGO": "RESIDENTE", "ALCANCE": "UNIDAD" }
]
```
El rol `PORTERO` (que en los scripts de desarrollo locales tiene ID 5) **no existe** en la tabla `ROLES` de Oracle ATP Cloud. Por tanto, `PKG_AUTH_BOOTSTRAP.GET_USER_PROFILE` retorna `NULL` al no encontrar asignación válida para el usuario `portero01`.

---

### 5. Auditoría Flujo por Flujo del Core MVP en la Nube

#### FLUJO A — AUTENTICACIÓN Y CICLO DE SESIÓN
- **Resultado:** **PARCIALMENTE OPERATIVO (8/10 PASS)**
- **Fortalezas:**
  - Login de `admin_global` redirige a `/superadmin/dashboard` en 4.9s.
  - Login de `admin` redirige a `/dashboard` en 4.2s.
  - Validación de credenciales erróneas genera HTTP 401 y alerta en pantalla sin filtrar existencia de usuario.
  - La sesión sobrevive a recarga de página (F5) preservando el token en `sessionStorage`.
  - El logout purga el storage y revoca la sesión en 3.1s.
- **Debilidades:**
  - Los usuarios `camartinez` y `portero01` no pueden ingresar a sus respectivos dashboards debido a la ausencia de asignación en Oracle ATP.

#### FLUJO B — ADMINISTRACIÓN Y CENSO DE RESIDENTES
- **Resultado:** **🟢 100% PASS**
- **Evidencia:**
  - Consulta de propiedades: Retorna 2 propiedades (`TORRE NORTE`, `TORRE PRINCIPAL`).
  - Consulta de unidades: Retorna unidades activas con identificación y bloque (`101`, `Torre 1`).
  - Censo de residentes en `/residentes`: Tabla poblada con nombres, datos de contacto y estado.
  - Búsqueda en tiempo real (Playwright 04.2): Filtrado instantáneo sin latencia ni recarga.
  - Formulario de nuevo residente (Playwright 04.3): Validación de campos obligatorios interactiva.

#### FLUJO C — VISITAS Y CÓDIGOS QR
- **Resultado:** **🔴 BLOQUEADO EN NUBE (HTTP 500)**
- **Evidencia:**
  - `GET /api/v1/visitas` retorna HTTP 500 (`INTERNAL_SERVER_ERROR`).
  - `GET /api/v1/porteria/visitas-resumen` retorna HTTP 500 (`DATABASE_ERROR: Ha ocurrido un error en la capa de datos`).
  - `GET /api/v1/porteria/qr/SAED-DEMO-QR-2026-TOKEN` retorna HTTP 500.
- **Causa Raíz:** Tablas `VISITAS` y `QR_ACCESOS` en Oracle ATP no poseen las columnas del release v5 o falta la relación con `PERSONAS` de visita.

#### FLUJO D — CARTERA, CUOTAS Y PAGOS (WOMPI)
- **Resultado:** **🟢 100% PASS**
- **Evidencia:**
  - `GET /api/v1/cuotas` retorna HTTP 200 OK con las cuotas de administración vigentes.
  - `GET /api/v1/cartera` retorna HTTP 200 OK con el consolidado por unidad.
  - `GET /api/v1/cartera/antiguedad` retorna HTTP 200 OK con el tramo `VIGENTE` y saldo de `$87.350 COP`.
  - `GET /api/v1/pagos` retorna HTTP 200 OK y renderiza la tabla administrativa en 7.0s.

#### FLUJO E — PAQUETERÍA Y CORRESPONDENCIA
- **Resultado:** **🟡 PARCIALMENTE OPERATIVO**
- **Evidencia:**
  - `GET /api/v1/paquetes` retorna HTTP 200 OK en 640ms.
  - La interfaz de recepción de paquetes en `/paquetes` requiere sesión activa de `PORTERO`. Al no poder loguear `portero01`, la interfaz visual no puede ser operada por ese rol en la nube.

#### FLUJO F — CONVIVENCIA, PQRS Y BUZÓN
- **Resultado:** **🟢 100% PASS**
- **Evidencia:**
  - Consola de quejas y PQRS en `/quejas-admin` carga en 7.2s.
  - Listado de requerimientos y filtros operativos.

#### FLUJO G — PARQUEADEROS Y VEHÍCULOS
- **Resultado:** **🔴 BLOQUEADO EN NUBE (HTTP 500)**
- **Evidencia:**
  - `GET /api/v1/parqueaderos` retorna HTTP 500 (`DATABASE_ERROR: Ha ocurrido un error en la capa de datos`).
- **Causa Raíz:** En Oracle ATP, la tabla `VEHICULOS_VISITA` o la relación `LEFT JOIN VEHICULOS` contiene columnas no migradas presentes en la versión local.

---

### 6. Comprobación de Integridad Frontend (Vercel CDN)

- **Inspección de Consola (Playwright 16.3):** Cero excepciones JavaScript no controladas (`0 page errors`).
- **Responsividad Móvil (Playwright 16.1):** Viewport 390x844 (iPhone 14) probado exitosamente. 0 desbordamientos horizontales (`scrollWidth === clientWidth`). Menú hamburguesa desplegable y barra superior adaptada.
- **Manejo de Estados de Carga y Error:** Spinners y skeletons activos durante llamadas lentas; toasts de Sonner informan errores de red de manera comprensible para el usuario.

---

### 7. Clasificación de Hallazgos y Severidad

| ID | Hallazgo | Severidad | Impacto | Componente Afectado |
| :---: | :--- | :---: | :---: | :--- |
| **H-01** | Rol `PORTERO` ausente en tabla `ROLES` de Oracle ATP Cloud | **P0 (Bloqueante)** | Impide asignación de garita a vigilantes en la nube | Oracle ATP Cloud (`ROLES`) |
| **H-02** | Usuarios `camartinez` y `portero01` sin asignación activa en ATP | **P0 (Bloqueante)** | Impide ingreso a `/portero-dashboard` y `/residente-dashboard` | Oracle ATP (`USUARIO_ASIGNACIONES`) |
| **H-03** | Error en capa de datos (500) en `/visitas` y `/parqueaderos` | **P1 (Crítico)** | No se puede validar QR ni ver bahías en la nube | Oracle ATP (`VEHICULOS_VISITA`, `QR`) |
| **H-04** | Cold-start de Render de 66 segundos en el primer ping | **P2 (Medio)** | Si el servicio duerme, el primer acceso tardará >1 min | Render PaaS (Free Tier) |
| **H-05** | Discrepancia en atributos ARIA (`tab` vs `button`) en Cartera | **P3 (Bajo)** | Cosmético en tests E2E; visualmente funciona al 100% | Frontend UI (`CarteraPage.jsx`) |

---

### 8. Estrategia y Plan de Contingencia para el Demo Day

> [!IMPORTANT]
> **REGLA DE ORO PARA EL DEMO DAY:**
> En una presentación en vivo con jurado, la confiabilidad absoluta prima sobre el entorno de despliegue.

Dado que la base de datos de producción en Oracle ATP Cloud presenta inconsistencias de semillas de migración (ausencia de rol `PORTERO` y tablas auxiliares de visitas), existen dos rutas claras:

#### RUTA RECOMENDADA (PLAN A — LOCAL DEMO ENGINE CERTIFICADO 100%)
- **Entorno:** Localhost (`npm run dev` en puerto 5173 + Spring Boot local con Oracle XE).
- **Certificación:** **MVP-07 con 49/49 tests en verde (100% PASS)** en 3 minutos y 36 segundos.
- **Cobertura:** Todos los 7 flujos (Login, Admin, QR Visitas, Cartera, Wompi, Paquetería, PQRS, Parqueaderos) funcionan sin un solo fallo.
- **Demostración:** Mostrar la arquitectura cloud en diapositivas y el despliegue vivo en Vercel (`https://saedfront.vercel.app`) para el login de SuperAdmin y Admin Propiedad, demostrando que la plataforma está en la nube.

#### PLAN B (REMEDICIÓN CONTROLADA PRE-DEMO — POSTERIOR A DEMO-03)
- Cuando concluya la fase de validación estricta (DEMO-03), y si el usuario autoriza la sincronización de la base de datos:
  1. Conectar a Oracle ATP y ejecutar el script `database/demo/V5.99__demo_seeds.sql` para insertar el rol `PORTERO`, las asignaciones de `portero01` y `camartinez`, y las tablas accesorias.
  2. Emitir un ping a Render 10 minutos antes de la presentación para evitar el cold start de 66s.

---

### 9. Verificación de Integridad de Código

Conforme a la regla absoluta de la fase DEMO-03:
- **Archivos de código fuente modificados en esta sesión:** **0**
- **Refactorizaciones ejecutadas:** **0**
- **Commits en Git:** **0**
- **Pushes a remoto:** **0**
- Toda la auditoría se realizó mediante consultas HTTP directas y configuraciones temporales fuera del árbol del repositorio.

---

### 10. Veredicto Oficial

# **`🔴 LIVE DEMO NOT CERTIFIED (CLOUD PRODUCTION)`**
### **`🟢 PRE-PRODUCTION LOCAL ENGINE CERTIFIED (MVP-07 100% PASS)`**

**Dictamen Técnico:**  
El frontend en Vercel y el backend en Render están operativos y sincronizados. Sin embargo, la base de datos Oracle ATP en la nube carece de las migraciones de datos demo finales requeridas para los roles de Portero y Residente. Para el Demo Day, se recomienda utilizar el entorno certificado en preproducción o ejecutar la sincronización de datos autorizada en Oracle ATP antes de salir en vivo.
