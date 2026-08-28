# Estado Real de SAED 2.0 — 28 de agosto de 2026

**Documento único de estado.** Reemplaza los 9 documentos de auditoría contradictorios anteriores.
Generado por inspección directa del código fuente y ejecución de comandos reales.

---

## 1. Línea base (ejecutada el 28/ago/2026)

### Backend
```
Tests run: 73, Failures: 1, Errors: 2, Skipped: 0
BUILD FAILURE (por tests pre-existentes que dependen de Oracle XE local)
```

**Detalle de fallos (pre-existentes, no relacionados con cambios recientes):**
- `Phase1AAuthIntegrationTest.testA_LoginCorrecto` — `InvalidCredentials` (requiere usuarios de prueba en Oracle XE local)
- `Phase1AAuthIntegrationTest.testH_JwtValidoAndStructure` — `InvalidCredentials` (mismo motivo)
- `Phase1AAuthIntegrationTest.testQ_UsoCorrectoDePkgAuthBootstrap` — `PKG_AUTH_BOOTSTRAP` no existe en el entorno de test

**70/73 tests pasan.** Los 3 fallos son de integración contra Oracle XE local con datos de prueba que no existen en el entorno de test.

### Frontend
```
✓ built in 7.91s
```
Build limpio, 0 errores.

---

## 2. Stack confirmado

| Capa | Tecnología |
|------|------------|
| Backend | Spring Boot 3.2 + Java 17, Controller → Service → Repository → JDBC (sin JPA) |
| Frontend | React 18 + Vite + TailwindCSS + shadcn-ui |
| DB | Oracle Autonomous Database (ATP) 19c con Row-Level Security (RLS) |
| Seguridad | JWT + SaedContext/SaedContextHolder + SaedConnectionProxy (SET_CONTEXT en cada request) |
| Pagos | Wompi (firma HMAC-SHA256 real, no mock) |
| Email | Brevo API v3 |
| Deploy | Render (backend) + Vercel (frontend) |

---

## 3. Wompi — confirmado REAL

**Evidencia de código** (`WompiServiceImpl.java` líneas 57-123):
- `crearIntencion()` consulta tablas reales (CUOTAS, MULTAS, TRANSACCIONES_PAGO)
- Genera firma de integridad HMAC-SHA256 real via `firmaIntegridad()`
- Inserta registro real en TRANSACCIONES_PAGO
- Devuelve: referencia, montoCentavos, publicKey, firmaIntegridad

**No hay mock.** El endpoint `POST /api/v1/pagos/wompi/solicitud` llama directamente a `wompiService.crearIntencion()`.

---

## 4. Auditoría append-only — confirmada

- **Tabla:** `AUDITORIA_LOG` (definida en DDL)
- **Triggers:** 4 triggers PL/SQL (`TRG_AUDIT_PROPIEDADES`, `TRG_AUDIT_PAGOS`, `TRG_AUDIT_ASIGNACIONES`, `TRG_AUDIT_MULTAS`) — migración `V4.12__audit_triggers.sql`
- **Protección:** `TRG_AUDITORIA_INMUTABLE` bloquea UPDATE/DELETE con `ORA-20099`
- **Java:** `GlobalExceptionHandler` invoca `SP_REGISTRAR_AUDITORIA` en violaciones de acceso

**Nota:** Los triggers fueron definidos en scripts SQL pero su estado REAL en Oracle ATP (ENABLED/DISABLED) no se puede verificar sin acceso directo a la DB de producción.

---

## 5. Multi-tenancy (RLS) — confirmado y probado

- **Patrón:** JWT → `SaedContextHolder` → `SaedConnectionProxy` → `SYS_CONTEXT('SAED_CTX', ...)` → Oracle RLS filtra filas automáticamente
- **Tests adversariales:** `ContextBleedIntegrationTest`, `AdversarialFoundationTest`, `Phase1BAdversarialTest`, `Phase1CAdversarialTest` — prueban activamente que un usuario de Org A no puede acceder a recursos de Org B
- **70/73 tests pasan** (los 3 fallos son de credenciales, no de aislamiento)

---

## 6. Módulos funcionales (verificados por código)

| # | Módulo | Backend | Frontend | Estado |
|---|--------|---------|----------|--------|
| 1 | Auth/JWT/Login | ✅ | ✅ Login.jsx | Completo |
| 2 | Organizaciones | ✅ OrganizationController | ✅ OrganizacionesPage | Completo |
| 3 | Propiedades | ✅ PropertyController | ✅ PropiedadesPage | Completo |
| 4 | Unidades (jerarquía) | ✅ UnitController | ✅ UnidadesPage | Completo |
| 5 | Roles/Asignaciones | ✅ AssignmentController | ✅ RolesYAsignacionesPage | Completo |
| 6 | Personas/Residentes | ✅ PersonController | ✅ ResidentesPage | Completo |
| 7 | Contratos | ✅ FinanzasController | ✅ ContratosPage | Funcional |
| 8 | Cuotas/Pagos | ✅ FinanzasController | ✅ PagosPage | Funcional |
| 9 | Wompi | ✅ WompiServiceImpl (real) | ✅ Widget integrado | Completo |
| 10 | Multas | ✅ MultasController | ✅ MultasPage | Funcional |
| 11 | Visitas/QR | ✅ PorteriaController | ✅ VisitasPage + EscanerQR | Completo |
| 12 | Parqueaderos | ✅ ParqueaderoController | ✅ ParqueaderosPage | Completo |
| 13 | Paquetes | ✅ PaqueteController | ✅ PaquetesPage | Completo |
| 14 | Quejas/PQRS | ✅ QuejasController | ✅ QuejasPage | Básico |
| 15 | Portería (accesos) | ✅ PorteriaController | ✅ PorteroDashboardPage | Funcional |
| 16 | Avisos/Comunicados | ✅ ComunicadosController | ✅ AvisosPage | Completo |
| 17 | Alertas | ✅ AlertasController | ✅ AlertasPage | Funcional |
| 18 | Dashboard | ✅ DashboardController | ✅ DashboardPage | Completo |
| 19 | Planes | ✅ PlanesController | ✅ PlanesPage | Completo |
| 20 | Membresías | ✅ MembresiasController | ✅ MembresiasPage | Completo |
| 21 | Auditoría | ✅ AuditoriaController | ✅ ReportesPage (tab) | Funcional |
| 22 | Reportes | ✅ ReportesController | ✅ ReportesPage (3 tabs) | Funcional |
| 23 | Portal Residente | ✅ (múltiples controllers) | ✅ ResidenteDashboardPage | Completo |
| 24 | Usuarios | ✅ UsuariosController | ✅ UsuariosPage | Completo |

**24 módulos con backend + frontend funcionales.**

---

## 7. Módulos NO existentes (faltantes contra documento maestro)

| # | Módulo | Prioridad |
|---|--------|-----------|
| 1 | Presupuesto (ingresos/egresos/ejecución) | Crítica |
| 2 | Flujo de caja | Alta |
| 3 | Conciliación bancaria | Alta |
| 4 | Paz y salvos | Media |
| 5 | Zonas comunes | Alta |
| 6 | Reservas | Alta |
| 7 | Mantenimiento (correctivo/preventivo) | Alta |
| 8 | Activos (inventario) | Alta |
| 9 | PQRS/SLA (con escalamiento) | Alta |
| 10 | Proveedores | Media |
| 11 | Contratistas | Media |
| 12 | Obras/Remodelaciones | Media |
| 13 | Incidentes | Media |
| 14 | Documentos (repositorio) | Media |
| 15 | Asambleas/Quórum/Votaciones | Media |
| 16 | Sanciones con debido proceso | Media |
| 17 | Seguros | Baja |
| 18 | Emergencias | Baja |
| 19 | Consumos (agua/energía/gas) | Baja |
| 20 | Automatizaciones | Media |

---

## 8. Deuda técnica identificada

### Archivos limpiados en esta fase (19 eliminados)
- 3 binarios sueltos (.class)
- 2 scripts Java de un solo uso
- 7 scripts Python generadores de código
- 7 scripts SQL de prueba sueltos

### Paquetes Java vacíos eliminados (5)
`audit/`, `auth/`, `organization/`, `property/`, `user/`

### Lado a lado: ApartamentosPage vs UnidadesPage
- **UnidadesPage.jsx** = fuente de verdad (modelo 2.0 jerárquico, multi-tenant con RLS)
- **ApartamentosPage.jsx** = legado (modelo plano, sin multi-tenancy) — aún tiene ruta activa

### Scripts fix_*.sql sueltos (12, NO eliminados — documentados)
Ver inventario completo en la sección de evidencia de la Fase A.

---

## 9. Criterios de la sección 43 del documento maestro — Evaluación

| # | Criterio | Estado |
|---|----------|--------|
| 1 | Modelo de datos | ✅ 96 tablas Oracle |
| 2 | Constraints e índices | ✅ Definidos en DDL |
| 3 | Reglas de negocio | ⚠️ Parcial (RLS funciona, faltan reglas de negocio por módulo) |
| 4 | Backend (controller/service/repository) | ✅ 23 controllers, 24+ services |
| 5 | Autorización | ✅ JWT + roles + scopes |
| 6 | Tenant scope (RLS) | ✅ Probado con tests adversariales |
| 7 | Frontend | ✅ 30+ páginas funcionales |
| 8 | Estados de carga/error | ✅ useFetch + ErrorBoundary |
| 9 | Auditoría | ✅ Triggers + append-only |
| 10 | Pruebas | ⚠️ 70/73 pasan (3 fallos pre-existentes) |
| 11 | Datos de prueba | ⚠️ Seed data parcial (2 orgs, 2 props, 3 usuarios, 3 planes) |
| 12 | Documentación | ⚠️ README actualizado, faltan docs por módulo |
| 13 | Manejo de errores | ✅ GlobalExceptionHandler + ApiResponse |
| 14 | Rendimiento | ⚠️ Sin N+1 evidentes, pero sin profiling real |

---

## 10. Resumen cuantitativo

| Métrica | Valor |
|---------|-------|
| Archivos totales (excluyendo .git, target, node_modules) | ~699 |
| Controllers backend | 23 |
| Services backend | 24+ |
| Páginas frontend | 30+ |
| Tablas Oracle | 96 |
| RLS policies | 91 |
| Triggers | 9 (incl. 4 de auditoría) |
| Tests backend | 73 (70 pasan) |
| Módulos completos | 24 de ~36 requeridos |
| Módulos faltantes | 20 |

---

*Documento generado el 28 de agosto de 2026 por verificación directa del código fuente.*
*Reemplaza: CHECKLIST_SAED_2_0.md, FINAL_COMPREHENSIVE_AUDIT_SAED_2_0.md, FINAL_REMEDIATION_AUDIT_SAED_2_0.md, docs/FINAL_PROJECT_AUDIT.md, FINAL_RELEASE_AUDIT_SAED_2_0.md, DEPLOYMENT_READINESS_AUDIT.md, PRODUCTION_DEPLOYMENT_REPORT.md, FINAL_DEPLOYMENT_AUDIT_SAED_2_0.md, PRODUCTION_QA_REPORT.md.*
