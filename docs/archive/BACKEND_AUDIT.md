# SAED 2.0 — Auditoría Completa de Backend (Spring Boot 3.2.3)

**Fecha:** 01 de Septiembre de 2026  
**Plan Maestro:** `Versión 4.0 — Definitiva`  
**Fase:** `Fase 1 — Auditoría Definitiva`  
**Auditor:** Principal Backend Architect  

---

## 1. Inventario de Controladores REST (43 Controladores)

| Paquete | Controlador | Endpoints Principales | Estado Arquitectónico |
| :--- | :--- | :--- | :--- |
| `authorization` | `AssignmentController` | `GET /api/v1/auth/assignments` | Conforme (Usa Service) |
| `authorization` | `AssignmentManagementController` | `POST, PUT, DELETE /api/v1/assignments` | Conforme (Usa Service) |
| `authorization` | `OrganizationController` | `GET, POST, PUT, DELETE /api/v1/organizations` | Conforme (Usa Service) |
| `authorization` | `PropertyController` | `GET, POST, PUT, DELETE /api/v1/properties` | Conforme (Usa Service) |
| `authorization` | `UnitController` | `GET, POST, PUT, DELETE /api/v1/units` | Conforme (Usa Service) |
| `catalog` | `CatalogoController` | `GET /api/v1/roles`, `tipos-documento`, `paises` | **Inconsistente:** SQL directo en Controller |
| `comunicacion` | `AlertasController` | `GET, PUT /api/v1/alertas` | **Inconsistente:** SQL directo en Controller |
| `comunicacion` | `ComunicadosController` | `GET, POST /api/v1/buzon/avisos` | **Inconsistente:** SQL directo en Controller + Endpoints stub |
| `comunicacion` | `WompiController` | `GET, POST /api/v1/pagos/wompi/*` | **Inconsistente:** SQL directo en Controller |
| `convivencia` | `BuzonController` | `GET, PUT /api/v1/buzon` | Conforme (Usa Service) |
| `convivencia` | `MultasController` | `GET, POST, PUT /api/v1/multas` | Conforme (Usa Service) |
| `convivencia` | `QuejasController` | `GET, POST, PUT /api/v1/quejas` | Conforme (Usa Service) |
| `dashboard` | `AuditoriaController` | `GET /api/v1/audit/logs` | **Inconsistente:** SQL directo en Controller |
| `dashboard` | `DashboardController` | `GET /api/v1/residentes/dashboard` | Conforme (Usa Service) |
| `dashboard` | `ReportesController` | `GET /api/v1/reportes/*` | **Inconsistente:** SQL directo en Controller |
| `documentos` | `DocumentoController` | `GET, POST, PUT, DELETE /api/v1/documentos` | Conforme (Usa Service) |
| `finanzas` | `CarteraController` | `GET, POST, PUT /api/v1/cartera/*` | **Inconsistente:** SQL directo en Controller |
| `finanzas` | `ConciliacionController` | `GET, POST, PUT /api/v1/conciliaciones/*` | **Inconsistente:** SQL directo en Controller |
| `finanzas` | `ContratosAdminController`| `GET, POST /api/v1/contratos-admin` | Conforme (Usa Service) |
| `finanzas` | `ContratosController` | `GET, POST, PUT, DELETE /api/v1/contratos` | Conforme (Usa Service) |
| `finanzas` | `FlujoCajaController` | `GET, POST /api/v1/flujo-caja/*` | **Inconsistente:** SQL directo en Controller |
| `finanzas` | `GastosController` | `GET, POST, PUT, DELETE /api/v1/gastos/*` | **Inconsistente:** SQL directo en Controller |
| `finanzas` | `MembresiasController` | `GET, POST, PUT /api/v1/membresias/*` | **Inconsistente:** SQL directo en Controller |
| `finanzas` | `PagosController` | `GET, POST, PUT /api/v1/pagos` | Conforme (Usa Service) |
| `finanzas` | `PazYSalvoController` | `GET, POST /api/v1/paz-y-salvos/*` | **Inconsistente:** SQL directo en Controller |
| `finanzas` | `PlanesController` | `GET, POST, PUT /api/v1/planes/*` | **Inconsistente:** SQL directo en Controller |
| `finanzas` | `PresupuestoController`| `GET, POST, PUT /api/v1/presupuestos/*` | **Inconsistente:** SQL directo en Controller |
| `finanzas` | `ResidentesFinanzasController`| `GET /api/v1/residentes/cuotas` | Conforme (Usa Service) |
| `identity` | `AuthController` | `POST /api/v1/auth/login`, `register` | Conforme (Usa Service) |
| `identity` | `MeController` | `GET /api/v1/me` | Conforme (Usa Service) |
| `incidentes` | `IncidenteController` | `GET, POST, PUT /api/v1/incidentes` | Conforme (Usa Service) |
| `obras` | `ObraController` | `GET, POST, PUT /api/v1/obras` | Conforme (Usa Service) |
| `paquetes` | `PaquetesController` | `GET, POST, PUT /api/v1/paquetes` | Conforme (Usa Service) |
| `parqueaderos` | `ParqueaderosController`| `GET, POST, PUT /api/v1/parqueaderos` | Conforme (Usa Service) |
| `person` | `DependentController` | `GET, POST, PUT, DELETE /api/v1/dependents` | Conforme (Usa Service) |
| `person` | `PersonaController` | `GET, POST, PUT, DELETE /api/v1/personas` | Conforme (Usa Service) |
| `person` | `UnitInhabitantController`| `GET, POST, DELETE /api/v1/units/{unitId}/inhabitants` | Conforme (Usa Service) |
| `porteria` | `PorteriaController` | `GET, POST, PUT /api/v1/porteria/*` | Conforme (Usa Service) |
| `porteria` | `PorteriaExtController`| `GET /api/v1/porterias` | **Inconsistente:** SQL directo en Controller |
| `pqrs` | `TicketController` | `GET, POST, PUT /api/v1/pqrs` | Conforme (Usa Service) |
| `reservas` | `ReservasController` | `GET, POST, PUT, DELETE /api/v1/reservas` | Conforme (Usa Service) |
| `sanciones` | `SancionController` | `GET, POST, PUT /api/v1/sanciones` | Conforme (Usa Service) |
| `seguros` | `PolizaSeguroController`| `GET, POST, PUT, DELETE /api/v1/seguros/polizas` | Conforme (Usa Service) |

---

## 2. Hallazgos Backend y Deuda Técnica

1. **`BE-001` (P2) — Evasión de la Capa de Servicio/Repositorio:**
   * 15 controladores inyectan directamente `NamedParameterJdbcTemplate` y contienen consultas SQL en línea.
   * **Riesgo:** Dificulta el testing unitario con mocks, duplica lógica de mapeo de filas y mezcla preocupaciones de transporte HTTP con persistencia.
2. **`BE-002` (P2) — Endpoints Stub / Muertos en ComunicadosController:**
   * `/api/v1/buzon/confirmar-pendiente` retorna lista vacía fija con comentario `Feature removed in V4 schema`.
   * `/api/v1/buzon/confirmar` retorna `200 OK` vacío sin realizar acción.
3. **`BE-003` (P2) — Logging Incompleto de Auditoría Operativa:**
   * Las mutaciones estándar (creación de residentes, contratos, pagos manuales) no llaman al procedimiento `SP_REGISTRAR_AUDITORIA`, dejando a `AUDITORIA_LOG` registrando solo intentos de acceso denegados (403/401).
4. **`BE-004` (P1) — Validaciones DTO y Anotaciones `@Valid` Faltantes:**
   * Varios endpoints que reciben `Map<String, Object> payload` no utilizan DTOs tipados con `@Valid` (`@NotNull`, `@Size`, `@Pattern`), lo que traslada la validación a excepciones de runtime o base de datos.
