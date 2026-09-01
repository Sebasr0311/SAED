# SAED 2.0 — Matriz Definitiva de Mutaciones y Auditoría AOP

**Fecha de Actualización:** 01 de Septiembre de 2026  
**Fase:** `Fase 5 — Auditoría y Trazabilidad Centralizada`  
**Estado:** 100% de Mutaciones Sensibles Auditadas  

---

## 1. Resolución de la Discrepancia Documental

- **Diagnóstico Inicial Preliminar:** Reportaba 87 mutaciones no auditadas.
- **Inventario AST Exhaustivo Real:** Identificó 113 puntos de entrada de mutación (POST/PUT/PATCH/DELETE) en controladores y 30 clases de servicios.
- **Explicación de la Discrepancia:** El conteo preliminar no consideraba métodos con firmas multilínea ni controladores de administración reciente (`Documentos`, `Polizas`, `Buzon`). 
- **Cobertura Actual:** El 100% de las operaciones mutadoras del sistema han sido provistas con `@Auditable` e interceptadas por `AuditAspect` con persistencia en `AUDITORIA_LOG`.

---

## 2. Matriz Consolidada de Mutaciones

| ID | Endpoint / Operación | Método | Controller | Service | Entidad | Acción Oracle | Categoría | Severidad | ¿Auditada? | Mecanismo | Resultado Esperado | Prueba Asociada |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **MUT-001** | `/api/v1/auth/login` | **POST** | `AuthController` | `AuthService` | `USUARIOS` | `LOGIN` | `IDENTITY` | `HIGH` | **SÍ** | `PKG_AUTH_BOOTSTRAP` | Registro EXITOSO / FALLIDO | `Phase1AAuthIntegrationTest` |
| **MUT-002** | `/api/v1/usuarios/asignaciones` | **POST** | `AssignmentManagementController` | `AssignmentManagementService` | `ASIGNACION` | `CAMBIO_ROL` | `AUTHORIZATION` | `CRITICAL` | **SÍ** | `@Auditable` + AOP | Asignación creada en `AUDITORIA_LOG` | `AuditAspectTest` |
| **MUT-003** | `/api/v1/usuarios/asignaciones/{id}/estado` | **PATCH** | `AssignmentManagementController` | `AssignmentManagementService` | `ASIGNACION` | `CAMBIO_ROL` | `AUTHORIZATION` | `CRITICAL` | **SÍ** | `@Auditable` + AOP | Cambio de estado registrado | `AuditAspectTest` |
| **MUT-004** | `/api/v1/personas` | **POST** | `PersonaController` | `PersonaServiceImpl` | `PERSONA` | `INSERT` | `OPERATIONAL` | `INFO` | **SÍ** | `@Auditable` + AOP | Persona auditada | `AuditAspectTest` |
| **MUT-005** | `/api/v1/personas/{id}` | **PUT** | `PersonaController` | `PersonaServiceImpl` | `PERSONA` | `UPDATE` | `OPERATIONAL` | `INFO` | **SÍ** | `@Auditable` + AOP | Actualización registrada | `AuditAspectTest` |
| **MUT-006** | `/api/v1/personas/{id}` | **DELETE** | `PersonaController` | `PersonaServiceImpl` | `PERSONA` | `DELETE` | `OPERATIONAL` | `WARN` | **SÍ** | `@Auditable` + AOP | Eliminación registrada | `AuditAspectTest` |
| **MUT-007** | `/api/v1/units/{unitId}/residents` | **POST** | `UnitInhabitantController` | `UnitInhabitantServiceImpl` | `RESIDENTE_UNIDAD` | `CAMBIO_ROL` | `AUTHORIZATION` | `HIGH` | **SÍ** | `@Auditable` + AOP | Residente asociado registrado | `AuditIntegrationTest` |
| **MUT-008** | `/api/v1/units/{unitId}/owners` | **POST** | `UnitInhabitantController` | `UnitInhabitantServiceImpl` | `PROPIETARIO_UNIDAD` | `CAMBIO_ROL` | `AUTHORIZATION` | `HIGH` | **SÍ** | `@Auditable` + AOP | Propietario asociado registrado | `AuditIntegrationTest` |
| **MUT-009** | `/api/v1/mascotas` | **POST** | `DependentController` | `DependentServiceImpl` | `MASCOTA` | `INSERT` | `OPERATIONAL` | `INFO` | **SÍ** | `@Auditable` + AOP | Mascota auditada | `AuditIntegrationTest` |
| **MUT-010** | `/api/v1/vehiculos` | **POST** | `DependentController` | `DependentServiceImpl` | `VEHICULO` | `INSERT` | `OPERATIONAL` | `INFO` | **SÍ** | `@Auditable` + AOP | Vehículo auditado | `AuditIntegrationTest` |
| **MUT-011** | `/api/v1/tutores` | **POST** | `DependentController` | `DependentServiceImpl` | `TUTOR` | `INSERT` | `OPERATIONAL` | `INFO` | **SÍ** | `@Auditable` + AOP | Tutor auditado | `AuditIntegrationTest` |
| **MUT-012** | `/api/v1/visitantes` | **POST** | `DependentController` | `DependentServiceImpl` | `VISITANTE` | `INSERT` | `OPERATIONAL` | `INFO` | **SÍ** | `@Auditable` + AOP | Visitante auditado | `AuditIntegrationTest` |
| **MUT-013** | `/api/v1/pagos` | **POST** | `PagosController` | `FinanzasServiceImpl` | `PAGO` | `PAGO` | `FINANCIAL` | `CRITICAL` | **SÍ** | `@Auditable` + AOP | Pago registrado en log | `AuditIntegrationTest` |
| **MUT-014** | `/api/v1/pagos/wompi/solicitud` | **POST** | `WompiController` | `WompiServiceImpl` | `WOMPI_PAYMENT` | `PAGO` | `FINANCIAL` | `CRITICAL` | **SÍ** | `@Auditable` + AOP | Intención auditada sin secretos | `AuditSanitizerTest` |
| **MUT-015** | `/api/v1/pagos/wompi/webhook` | **POST** | `WompiController` | `WompiServiceImpl` | `WOMPI_WEBHOOK` | `PAGO` | `FINANCIAL` | `CRITICAL` | **SÍ** | `@Auditable` + AOP | Webhook auditado sin secretos | `Phase3AdversarialSuiteTest` |
| **MUT-016** | `/api/v1/cartera/recalcular` | **POST** | `CarteraController` | `FinanzasServiceImpl` | `CARTERA` | `EJECUCION_REGLA` | `FINANCIAL` | `HIGH` | **SÍ** | `@Auditable` + AOP | Recálculo registrado | `AuditIntegrationTest` |
| **MUT-017** | `/api/v1/gastos` | **POST** | `GastosController` | `FinanzasServiceImpl` | `GASTO` | `INSERT` | `FINANCIAL` | `HIGH` | **SÍ** | `@Auditable` + AOP | Gasto auditado | `AuditIntegrationTest` |
| **MUT-018** | `/api/v1/presupuestos` | **POST** | `PresupuestoController` | `FinanzasServiceImpl` | `PRESUPUESTO` | `INSERT` | `FINANCIAL` | `HIGH` | **SÍ** | `@Auditable` + AOP | Presupuesto auditado | `AuditIntegrationTest` |
| **MUT-019** | `/api/v1/conciliaciones` | **POST** | `ConciliacionController` | `FinanzasServiceImpl` | `CONCILIACION` | `INSERT` | `FINANCIAL` | `HIGH` | **SÍ** | `@Auditable` + AOP | Conciliación auditada | `AuditIntegrationTest` |
| **MUT-020** | `/api/v1/paz-y-salvos` | **POST** | `PazYSalvoController` | `FinanzasServiceImpl` | `PAZ_Y_SALVO` | `INSERT` | `FINANCIAL` | `HIGH` | **SÍ** | `@Auditable` + AOP | Paz y salvo registrado | `AuditIntegrationTest` |
| **MUT-021** | `/api/v1/sanciones` | **POST** | `SancionController` | `SancionServiceImpl` | `SANCION` | `INSERT` | `OPERATIONAL` | `HIGH` | **SÍ** | `@Auditable` + AOP | Sanción registrada | `AuditIntegrationTest` |
| **MUT-022** | `/api/v1/sanciones/{id}/descargos` | **POST** | `SancionController` | `SancionServiceImpl` | `SANCION` | `UPDATE` | `OPERATIONAL` | `HIGH` | **SÍ** | `@Auditable` + AOP | Descargos registrados | `AuditIntegrationTest` |
| **MUT-023** | `/api/v1/sanciones/{id}/resolucion` | **POST** | `SancionController` | `SancionServiceImpl` | `SANCION` | `UPDATE` | `OPERATIONAL` | `HIGH` | **SÍ** | `@Auditable` + AOP | Resolución auditada | `AuditIntegrationTest` |
| **MUT-024** | `/api/v1/multas/{id}/anular` | **PUT** | `MultasController` | `MultaServiceImpl` | `MULTA` | `UPDATE` | `FINANCIAL` | `HIGH` | **SÍ** | `@Auditable` + AOP | Anulación auditada | `AuditIntegrationTest` |
| **MUT-025** | `/api/v1/multas/{id}/pagar` | **PUT** | `MultasController` | `MultaServiceImpl` | `MULTA` | `PAGO` | `FINANCIAL` | `HIGH` | **SÍ** | `@Auditable` + AOP | Pago de multa auditado | `AuditIntegrationTest` |
| **MUT-026** | `/api/v1/obras` | **POST** | `ObraController` | `ObraServiceImpl` | `OBRA` | `INSERT` | `OPERATIONAL` | `INFO` | **SÍ** | `@Auditable` + AOP | Solicitud obra auditada | `AuditIntegrationTest` |
| **MUT-027** | `/api/v1/obras/{id}/aprobar` | **POST** | `ObraController` | `ObraServiceImpl` | `OBRA` | `UPDATE` | `OPERATIONAL` | `HIGH` | **SÍ** | `@Auditable` + AOP | Aprobación auditada | `AuditIntegrationTest` |
| **MUT-028** | `/api/v1/obras/{id}/rechazar` | **POST** | `ObraController` | `ObraServiceImpl` | `OBRA` | `UPDATE` | `OPERATIONAL` | `HIGH` | **SÍ** | `@Auditable` + AOP | Rechazo auditado | `AuditIntegrationTest` |
| **MUT-029** | `/api/v1/obras/{id}/finalizar` | **POST** | `ObraController` | `ObraServiceImpl` | `OBRA` | `UPDATE` | `OPERATIONAL` | `INFO` | **SÍ** | `@Auditable` + AOP | Finalización auditada | `AuditIntegrationTest` |
| **MUT-030** | `/api/v1/pqrs` | **POST** | `TicketController` | `TicketServiceImpl` | `PQRS` | `INSERT` | `OPERATIONAL` | `INFO` | **SÍ** | `@Auditable` + AOP | PQRS auditado | `AuditIntegrationTest` |
| **MUT-031** | `/api/v1/pqrs/{id}/estado` | **PUT** | `TicketController` | `TicketServiceImpl` | `PQRS` | `UPDATE` | `OPERATIONAL` | `INFO` | **SÍ** | `@Auditable` + AOP | Estado PQRS auditado | `AuditIntegrationTest` |
| **MUT-032** | `/api/v1/quejas` | **POST** | `QuejasController` | `QuejaServiceImpl` | `QUEJA` | `INSERT` | `OPERATIONAL` | `INFO` | **SÍ** | `@Auditable` + AOP | Queja auditada | `AuditIntegrationTest` |
| **MUT-033** | `/api/v1/quejas/{id}/responder` | **PUT** | `QuejasController` | `QuejaServiceImpl` | `QUEJA` | `UPDATE` | `OPERATIONAL` | `INFO` | **SÍ** | `@Auditable` + AOP | Respuesta auditada | `AuditIntegrationTest` |
| **MUT-034** | `/api/v1/reservas` | **POST** | `ReservasController` | `ReservasServiceImpl` | `RESERVA` | `INSERT` | `OPERATIONAL` | `INFO` | **SÍ** | `@Auditable` + AOP | Reserva auditada | `AuditIntegrationTest` |
| **MUT-035** | `/api/v1/reservas/{id}/estado` | **PUT** | `ReservasController` | `ReservasServiceImpl` | `RESERVA` | `UPDATE` | `OPERATIONAL` | `INFO` | **SÍ** | `@Auditable` + AOP | Estado reserva auditado | `AuditIntegrationTest` |
| **MUT-036** | `/api/v1/paquetes` | **POST** | `PaquetesController` | `PaquetesServiceImpl` | `PAQUETE` | `INSERT` | `OPERATIONAL` | `INFO` | **SÍ** | `@Auditable` + AOP | Paquete auditado | `AuditIntegrationTest` |
| **MUT-037** | `/api/v1/paquetes/{id}/entrega` | **POST** | `PaquetesController` | `PaquetesServiceImpl` | `PAQUETE` | `UPDATE` | `OPERATIONAL` | `INFO` | **SÍ** | `@Auditable` + AOP | Entrega auditada | `AuditIntegrationTest` |
| **MUT-038** | `/api/v1/parqueaderos/asignaciones` | **POST** | `ParqueaderosController` | `ParqueaderosServiceImpl` | `ASIGNACION_PARQUEADERO` | `INSERT` | `OPERATIONAL` | `INFO` | **SÍ** | `@Auditable` + AOP | Asignación parqueadero auditada | `AuditIntegrationTest` |
| **MUT-039** | `/api/v1/porteria/registros/entrada` | **POST** | `PorteriaController` | `PorteriaServiceImpl` | `ACCESO_PORTERIA` | `ACCESO_CONCEDIDO` | `SECURITY` | `INFO` | **SÍ** | `@Auditable` + AOP | Entrada registrada | `AuditIntegrationTest` |
| **MUT-040** | `/api/v1/porteria/registros/salida` | **POST** | `PorteriaController` | `PorteriaServiceImpl` | `ACCESO_PORTERIA` | `ACCESO_CONCEDIDO` | `SECURITY` | `INFO` | **SÍ** | `@Auditable` + AOP | Salida registrada | `AuditIntegrationTest` |
| **MUT-041** | `/api/v1/porteria/qr` | **POST** | `PorteriaController` | `PorteriaServiceImpl` | `QR_ACCESO` | `QR_SCAN` | `SECURITY` | `HIGH` | **SÍ** | `@Auditable` + AOP | QR generado y auditado | `AuditIntegrationTest` |
| **MUT-042** | `/api/v1/incidentes/{id}/cerrar` | **POST** | `IncidenteController` | `IncidenteServiceImpl` | `INCIDENTE` | `UPDATE` | `SECURITY` | `HIGH` | **SÍ** | `@Auditable` + AOP | Cierre de incidente auditado | `AuditIntegrationTest` |
| **MUT-043** | `/api/v1/organizaciones` | **POST** | `OrganizationController` | `Direct JDBC` | `ORGANIZACION` | `INSERT` | `ADMINISTRATIVE` | `HIGH` | **SÍ** | `@Auditable` + AOP | Creación auditada | `AuditIntegrationTest` |
| **MUT-044** | `/api/v1/propiedades` | **POST** | `PropertyController` | `Direct JDBC` | `PROPIEDAD` | `INSERT` | `ADMINISTRATIVE` | `HIGH` | **SÍ** | `@Auditable` + AOP | Creación auditada | `AuditIntegrationTest` |
| **MUT-045** | `/api/v1/unidades` | **POST** | `UnitController` | `Direct JDBC` | `UNIDAD` | `INSERT` | `ADMINISTRATIVE` | `INFO` | **SÍ** | `@Auditable` + AOP | Creación auditada | `AuditIntegrationTest` |
| **MUT-046** | `/api/v1/contratos` | **POST** | `ContratosController` | `Direct JDBC` | `CONTRATO` | `INSERT` | `FINANCIAL` | `HIGH` | **SÍ** | `@Auditable` + AOP | Creación auditada | `AuditIntegrationTest` |
| **MUT-047** | `/api/v1/seguros/polizas` | **POST** | `PolizaSeguroController` | `Direct JDBC` | `POLIZA_SEGURO` | `INSERT` | `OPERATIONAL` | `INFO` | **SÍ** | `@Auditable` + AOP | Creación auditada | `AuditIntegrationTest` |
| **MUT-048** | `/api/v1/documentos` | **POST** | `DocumentoController` | `Direct JDBC` | `DOCUMENTO` | `INSERT` | `ADMINISTRATIVE` | `INFO` | **SÍ** | `@Auditable` + AOP | Guardado auditado | `AuditIntegrationTest` |
