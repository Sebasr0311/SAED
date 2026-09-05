# DEMO-03 — Matriz de Smoke Tests en Producción Real (Live Cloud)
## SAED 2.0 — Vercel · Render · Oracle ATP Cloud

**Fecha de Ejecución:** 05 de Septiembre de 2026 — 10:20 COT  
**Entorno de Pruebas:** Cloud Production (En Vivo)  
**Frontend URL:** `https://saedfront.vercel.app` (Vercel CDN)  
**Backend URL:** `https://saed-backend.onrender.com/api/v1` (Render PaaS)  
**Base de Datos:** Oracle Autonomous Transaction Processing (ATP 19c Cloud `adb.sa-bogota-1.oraclecloud.com`)  
**Contexto de Seguridad:** `SAED_CTX` (`ACCESSED LOCALLY` / Session-Local PGA)  

---

### Matriz de Ejecución por Flujo

| ID | Flujo / Caso de Prueba | Rol Evaluado | Resultado | HTTP Status | Errores JS Consola | Latencia (ms) | Evidencia / Notas |
| :---: | :--- | :---: | :---: | :---: | :---: | :---: | :--- |
| **00.1** | Carga de Landing Page y Enlace a Login | Anónimo | **PASS** | 200 OK | 0 | 2.200 ms | Título "SAED", botón de acceso operativo |
| **01.1** | Login SUPERADMIN redirige a `/superadmin/dashboard` | `admin_global` | **PASS** | 200 OK | 0 | 4.900 ms | Token JWT emitido, acceso a métricas de plataforma |
| **01.2** | Login ADMIN_PROPIEDAD redirige a `/dashboard` | `admin` | **PASS** | 200 OK | 0 | 4.200 ms | Asignación 205 (Org 1, Prop 1), acceso a centro operativo |
| **01.3** | Login PORTERO redirige a `/portero-dashboard` | `portero01` | **FAIL** | 200 OK (Auth) | 0 | 17.700 ms | Credenciales válidas, pero `USUARIO_ASIGNACIONES` carece de rol en ATP (`rol: null`) |
| **01.4** | Login RESIDENTE redirige a `/residente-dashboard` | `camartinez` | **FAIL** | 200 OK (Auth) | 0 | 17.600 ms | Credenciales válidas, pero `USUARIO_ASIGNACIONES` carece de rol en ATP (`rol: null`) |
| **01.4b** | Login RESIDENTE Alternativo (`residente_sol`) | `residente_sol` | **PASS** | 200 OK | 0 | 1.431 ms | Asignación activa en ATP: Org 2, Prop 2, Unidad 2 |
| **01.5** | Rechazo de Contraseña Incorrecta | `admin` | **PASS** | 401 Unauthorized | 0 | 4.100 ms | Toast de advertencia visible, permanece en `/login` |
| **01.6** | Rechazo de Usuario Inexistente | Anónimo | **PASS** | 401 Unauthorized | 0 | 2.900 ms | Alerta de credenciales inválidas sin revelar existencia |
| **01.7** | Validación de Campos Vacíos en Formulario | Anónimo | **PASS** | Client Validation | 0 | 1.500 ms | Validación nativa antes del envío |
| **01.8** | Protección de Rutas sin Token JWT | Anónimo | **PASS** | 302 / Redirect | 0 | 2.200 ms | Redirección automática inmediata a `/login` |
| **01.9** | Preservación de Sesión tras Recarga (F5) | `admin` | **PASS** | 200 OK | 0 | 8.900 ms | Token en `sessionStorage` intacto, recarga en `/dashboard` |
| **01.10**| Logout y Destrucción de Sesión | `admin` | **PASS** | 204 No Content | 0 | 3.100 ms | Token revocado, storage purgado, regreso a `/login` |
| **02.1** | Residente Bloqueado de Consolas de Administración | `residente_sol` | **PASS** | 403 / Redirect | 0 | 1.500 ms | Route guards impiden acceso a `/superadmin/*` |
| **02.3** | Admin Propiedad Bloqueado de Consola Global | `admin` | **PASS** | 403 / Redirect | 0 | 1.800 ms | Guard impide renderizado de consola SuperAdmin |
| **03.1** | Dashboard Admin Propiedad (KPIs y Tarjetas) | `admin` | **PASS** | 200 OK | 0 | 4.600 ms | Unidades, personas y métricas en vivo |
| **03.2** | Dashboard SuperAdmin (Métricas de Plataforma) | `admin_global` | **PASS** | 200 OK | 0 | 3.900 ms | Organizaciones, propiedades, suscripciones visibles |
| **04.1** | Censo de Residentes (Listado y KPIs) | `admin` | **PASS** | 200 OK | 0 | 8.300 ms | Tabla poblada con personas y unidades del tenant |
| **04.2** | Búsqueda en Tiempo Real en Residentes | `admin` | **PASS** | 200 OK | 0 | 9.200 ms | Filtrado dinámico instantáneo por nombre/apellido |
| **04.3** | Validación de Formulario de Registro de Residentes | `admin` | **PASS** | Client Validation | 0 | 8.600 ms | Modal valida campos obligatorios antes de enviar |
| **05.1** | Consulta de Cartera y Balances | `admin` | **PASS** | 200 OK | 0 | 1.200 ms | Endpoints `/cartera` y `/cartera/resumen` responden 200 OK |
| **05.2** | Antigüedad de Mora en Cartera | `admin` | **PASS** | 200 OK | 0 | 850 ms | Endpoint `/cartera/antiguedad` retorna tramo VIGENTE ($87.350) |
| **06.1** | Módulo de Pagos y Recaudos | `admin` | **PASS** | 200 OK | 0 | 7.000 ms | Bandeja de pagos operativa con buscador |
| **07.1** | Módulo de Visitas (Consulta de Bitácora) | `admin` | **FAIL** | 500 Data Error | 0 | 620 ms | `DATABASE_ERROR` en Oracle ATP (tabla no sincronizada) |
| **08.1** | Consulta y Validación de QR Demo | `admin` | **FAIL** | 500 Data Error | 0 | 580 ms | `INTERNAL_SERVER_ERROR` en ATP al consultar token demo |
| **10.1** | Módulo de Parqueaderos | `admin` | **FAIL** | 500 Data Error | 0 | 710 ms | `DATABASE_ERROR` en ATP por falta de `VEHICULOS_VISITA` |
| **11.1** | API de Paquetes en Portería | `admin` | **PASS** | 200 OK | 0 | 640 ms | Endpoint `/api/v1/paquetes` responde array de datos |
| **13.1** | Bandeja de PQRS y Quejas | `admin` | **PASS** | 200 OK | 0 | 7.200 ms | Carga exitosa de `/quejas-admin` |
| **14.1** | Listado de Usuarios del Sistema | `admin` | **PASS** | 200 OK | 0 | 6.800 ms | Tabla de cuentas de usuario cargada |
| **14.2** | Gestión de Roles y Asignaciones | `admin` | **PASS** | 200 OK | 0 | 11.200 ms | Consola `/roles-asignaciones` funcional |
| **15.1** | Aislamiento Multi-Tenant y Seguridad RLS | `admin` | **PASS** | 200 OK | 0 | 8.800 ms | Visualiza exclusivamente Conjunto Horizonte / Torre Norte |
| **16.1** | Responsividad Móvil (Viewport 390x844) | `admin` | **PASS** | 200 OK | 0 | 8.300 ms | 0 desbordamientos horizontales, menú colapsable operativo |
| **16.3** | Integridad JS en Consola (0 Errores Críticos) | `admin` | **PASS** | 200 OK | 0 | 14.800 ms | 0 excepciones JS no controladas en páginas núcleo |

---

### Resumen Estadístico de Ejecución

- **Total Casos Auditados en Producción Real:** 32 casos
- **Casos Exitosos (PASS):** 27 casos (**84.4%**)
- **Casos Fallidos (FAIL):** 5 casos (**15.6%**)
- **Causa Raíz de los Fallos en Producción:**
  1. Base de datos Oracle ATP Cloud no tiene aplicadas las migraciones de semillas `V5.99__demo_seeds.sql` ni tablas auxiliares de `VEHICULOS_VISITA` / `QR_ACCESOS`.
  2. La tabla `ROLES` en Oracle ATP Cloud solo contiene los roles iniciales (`SUPERADMIN`, `ADMIN_ORGANIZACION`, `ADMIN_PROPIEDAD`, `RESIDENTE`), omitiendo `PORTERO`.
  3. Los usuarios demo `camartinez` y `portero01` existen en `USUARIOS` pero carecen de registro en `USUARIO_ASIGNACIONES` dentro de la base de datos de producción.
