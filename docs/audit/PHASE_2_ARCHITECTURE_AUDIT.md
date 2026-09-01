# SAED 2.0 — Informe de Auditoría Arquitectónica Completa y Revalidación del Estado Real (Fase 2)

**Fecha:** 01 de Septiembre de 2026  
**Documento Fuente:** `SAED 2.0 — Plan Maestro Definitivo v4.0 (Prompt de Continuación)`  
**Fase:** `FASE 2 — AUDITORÍA ARQUITECTÓNICA COMPLETA Y REVALIDACIÓN DEL ESTADO REAL`  
**Responsable:** Principal Software & Security Architect  
**Veredicto de Fase 2:** **AUDITORÍA ARQUITECTÓNICA COMPLETADA — 28 HALLAZGOS CATALOGADOS (1 P0, 14 P1, 12 P2, 1 P3)**  

---

## 1. Estado Real de Backend

* **Controladores:** 43 controladores REST mapeados en Spring Boot 3.2.3.
* **Separación de Capas:**
  * 28 controladores respetan el flujo desacoplado `Controller ➔ Service ➔ Repository`.
  * **15 controladores violan la arquitectura** inyectando directamente `NamedParameterJdbcTemplate` con consultas SQL embebidas (`BE-001`).
* **Control de Acceso y RBAC:**
  * La mayoría de controladores usan `@PreAuthorize("hasAuthority('SCOPE_...')")`.
  * **Fallas Críticas Detectadas:** `AssignmentManagementController`, `PersonaController`, `UnitInhabitantController` y `CatalogoController.usuarios` carecen de anotaciones `@PreAuthorize` en endpoints de mutación y lectura sensible (`SEC-006`, `SEC-007`, `SEC-008`, `SEC-009`).
* **Validación de Parámetros:**
  * Persiste el uso de `Map<String, Object> payload` sin validaciones `@Valid` en múltiples controladores financieros y de convivencia (`BE-004`).
* **Límites Transaccionales:**
  * Operaciones multi-query en controladores (como `CarteraController.recalcular`) no declaran `@Transactional`, arriesgando estados intermedios inconsistentes (`BE-007`).

---

## 2. Estado Real de Frontend

* **Páginas y Rutas:** 58 páginas desarrolladas en React 18 + Vite + Tailwind CSS (`frontend/src/pages/`).
* **Compilación:** `npm run build` transforma 2.000 módulos en 10.82s con **0 errores de sintaxis o empaquetado**.
* **Fallas de Navegación (`FE-003`):**
  * `frontend/src/lib/access.js` solo tiene 16 rutas registradas para `ADMINISTRADOR`. La función `roleCanAccess` devuelve `false` para 22 páginas de administración, forzando una redirección a `/dashboard` tras el login.
* **Componentes de UI y Micro-estados (`FE-001`):**
  * Pantallas principales usan `<DataTable>` estandarizado; páginas secundarias (`GananciasPage`, `FlujoCajaPage`) usan renderizados con textos planos para estados vacíos y errores.
* **Bundle Size (`FE-002`):**
  * Chunk `xlsx.min.js` (627.18 kB) supera el umbral recomendado de 500 kB.

---

## 3. Estado Real de Base de Datos

* **Instancia Activa:** Oracle XE 23c (`localhost:1521/XEPDB1`, esquema `SAED_V39_FINAL_TEST`).
* **Métricas de Esquema Verificadas:**
  * **96 Tablas** (`USER_TABLES`): 100% válidas.
  * **336 Índices** (`USER_INDEXES`): 100% válidos.
  * **1.228 Restricciones** (`USER_CONSTRAINTS`): 100% habilitadas (872 Check, 98 PK, 207 FK, 51 Unique).
  * **9 Triggers** (`USER_TRIGGERS`): Incluye `TRG_AUDITORIA_INMUTABLE` activo.
  * **96 Secuencias Identity:** Alineadas con `START WITH LIMIT VALUE`.
  * **0 Objetos Inválidos** en el catálogo de Oracle.
* **Cadena de Migraciones (`DB-001`):**
  * 17 archivos en `database/migrations/` con parches y rollbacks superpuestos. No es posible reconstruir una base de datos limpia desde cero sin consolidar `V5.0__master_baseline.sql`.

---

## 4. Estado Real de Seguridad y RLS

* **Contexto Zero-Trust:**
  * `JWT ➔ JwtAuthenticationFilter ➔ SaedContextHolder ➔ SaedDataSourceProxy ➔ PKG_SAED_SESSION.SET_CONTEXT ➔ SYS_CONTEXT ➔ VPD/RLS`.
* **Vulnerabilidad P0 (`SEC-001`):**
  * `PKG_SAED_SECURITY_RLS.FN_FILTRO_UNIDAD` retorna `id_unidad IN (SELECT id_unidad FROM UNIDADES WHERE id_propiedad = :prop)` para `ROL_CODIGO = 'RESIDENTE'`, permitiendo que un residente consulte a nivel de base de datos información de todas las unidades del conjunto.
* **Vulnerabilidades de Autorización / IDOR:**
  * `SEC-006`: `AssignmentManagementController.updateStatus` no valida el contexto del llamante.
  * `SEC-007`: `PersonaController` no tiene `@PreAuthorize`, permitiendo a residentes o porteros mutar/eliminar personas.
  * `SEC-008`: `UnitInhabitantController` no tiene `@PreAuthorize` para asociar residentes/propietarios a unidades.
  * `SEC-009`: `CatalogoController.usuarios` expone el directorio completo de usuarios y correos sin autorización.
  * `SEC-010`: `ResidentesFinanzasController.getDashboard` no valida que `{id}` coincida con el usuario autenticado (IDOR horizontal).
* **Revocación JWT (`SEC-003`):**
  * Tokens permanecen válidos 24 horas tras desactivar un usuario en Oracle.

---

## 5. Estado Real de Testing

* **Total de Pruebas:** 115 tests en `mvn clean test` (`115 PASSED / 0 FAILED / 0 SKIPPED` en 39.9s con JDK 24).
* **Falsos Positivos y Brechas Críticas:**
  * `TEST-004`: `ContextBleedIntegrationTest` captura `Exception` (`Failed to obtain JDBC Connection`), imprime mensaje y retorna `true`, pasando el test de forma vacua sin probar aislamiento en Oracle.
  * `TEST-005`: `application-test.yml` apunta a `jdbc:h2:mem:testdb` que no soporta VPD ni paquetes de Oracle XE.
  * `TEST-001`: Faltan tests automatizados para los Ataques Adversariales E a L.
  * `TEST-002`: No existen tests E2E con Playwright.

---

## 6. Estado Real de Integraciones

* **Wompi (`SEC-002`):**
  * `WompiServiceImpl.crearIntencion` calcula correctamente el checksum SHA-256.
  * Fallas: fallback de `idUnidad` a `idPropiedad`, guardado de `concepto` en columna `METODO_ORIGEN`, falta de matching exacto de centavos en webhook.
* **Brevo (`EmailService.java`):**
  * Cliente HTTP v3 funcional; emite advertencias controladas si falta `BREVO_API_KEY`.
* **PDF (`PdfServiceImpl.java`):**
  * Motor `openhtmltopdf` (PDFBox) funcional para XHTML.
* **Despliegue Render (`DEP-001`):**
  * `render.yaml` define `healthCheckPath: /api/v1/auth/login` (endpoint POST que devuelve 405 a GET de Render) y omite variables de entorno necesarias.

---

## 7. Deuda Técnica y Violaciones Arquitectónicas

1. **`BE-001`:** 15 controladores REST con SQL en línea.
2. **`BE-004`:** Ausencia de Bean Validation en cuerpos de petición.
3. **`BE-007`:** Falta de límites transaccionales (`@Transactional`) en operaciones multi-query.
4. **`TECH-001`:** 17 migraciones fragmentadas.
5. **`TECH-003`:** 164 archivos obsoletos de SAED 1.0 en `backend_legacy/`.
6. **`SEC-004`:** Salidas `System.out.println` en `SaedDataSourceProxy.java:52`.
7. **`BE-003`:** Mutaciones de negocio no auditadas en `AUDITORIA_LOG`.

---

## 8. Código Muerto y Duplicado

1. **`BE-002`:** Stubs `/confirmar-pendiente` y `/confirmar` en `ComunicadosController.java:78-88`.
2. **`BE-005` (Parcial):** Stub `/asignar-apartamento` en `DashboardController.java:38-40` y `/visitas/rapida` en `PorteriaExtController.java:18-20`.
3. **Código Duplicado:** Mapeos de filas manuales repetidos en controladores financieros.

---

## 9. Revalidación y Matriz Completa de Hallazgos (28 Hallazgos)

```text
ID: SEC-001
Severidad: P0
Categoría: Seguridad / RLS
Módulo: RLS Predicate
Archivo: database/modelo_relacional_v4_atp.sql (PKG_SAED_SECURITY_RLS)
Línea: L55-75
Problema: FN_FILTRO_UNIDAD no aísla por unidad específica a RESIDENTE; retorna todas las unidades de la propiedad.
Evidencia: Consulta al body de PKG_SAED_SECURITY_RLS en Oracle XE.
Causa raíz: Predicado VPD incompleto para el rol RESIDENTE.
Impacto: Residente puede consultar/mutar cuotas y multas de otros residentes si Java no filtra.
Riesgo: Fuga masiva de datos financieros horizontal.
Dependencias: Ninguna
Solución propuesta: Incorporar subquery a RESIDENTES_UNIDAD cruzando con id_usuario en FN_FILTRO_UNIDAD.
Test necesario: Phase1CAdversarialTest / Cross-unit test.
Bloquea 2.0: SÍ
Bloquea producción: SÍ
Estado: OPEN

ID: SEC-002
Severidad: P1
Categoría: Seguridad / Finanzas
Módulo: Wompi Webhook
Archivo: backend/src/main/java/com/saed/backend/finanzas/service/impl/WompiServiceImpl.java
Línea: L73-120
Problema: idUnidad recurre a idPropiedad; concepto se almacena en METODO_ORIGEN; falta validación atómica de centavos en webhook.
Evidencia: WompiServiceImpl.java:73, 114.
Causa raíz: Manejo apresurado del DTO y almacenamiento de la pasarela.
Impacto: Pagos asociados a entidades incorrectas e inconsistencia financiera.
Riesgo: Corrupción de saldos y fraude en pagos.
Dependencias: SEC-001
Solución propuesta: Validación estricta de centavos, validación de idUnidad y almacenamiento de método real (PSE/CARD).
Test necesario: Phase13WompiIntegrationTest con webhook manipulado.
Bloquea 2.0: SÍ
Bloquea producción: SÍ
Estado: OPEN

ID: SEC-003
Severidad: P1
Categoría: Seguridad / Auth
Módulo: JWT Revocation
Archivo: backend/src/main/java/com/saed/backend/security/filter/JwtAuthenticationFilter.java
Línea: L40-80
Problema: Ausencia de lista de revocación o validación de estado activo en filtro; token sigue activo 24h tras desactivar usuario.
Evidencia: JwtAuthenticationFilter.java:58-70.
Causa raíz: Arquitectura JWT 100% stateless sin blacklist o versionado de sesión.
Impacto: Usuarios revocados retienen acceso temporal.
Riesgo: Acceso no autorizado por credenciales comprometidas.
Dependencias: Ninguna
Solución propuesta: Validar estado activo de usuario en cada request vía cache o versión de credencial.
Test necesario: Test de intento de acceso con JWT de usuario desactivado.
Bloquea 2.0: SÍ
Bloquea producción: SÍ
Estado: OPEN

ID: SEC-006
Severidad: P1
Categoría: Seguridad / Autorización
Módulo: Asignaciones
Archivo: backend/src/main/java/com/saed/backend/authorization/controller/AssignmentManagementController.java
Línea: L25-36
Problema: Endpoints POST /api/v1/assignments y PATCH /api/v1/assignments/{id}/status carecen de @PreAuthorize y updateStatus no valida contexto.
Evidencia: AssignmentManagementController.java:25, 32 y AssignmentManagementService.java:92.
Causa raíz: Omisión de anotaciones de seguridad por método y falta de validación en servicio.
Impacto: Cualquier usuario autenticado puede alterar el estado de asignaciones.
Riesgo: Escalada de privilegios y bypass de roles.
Dependencias: Ninguna
Solución propuesta: Agregar @PreAuthorize("hasAuthority('SCOPE_ADMIN_ORGANIZACION') or hasAuthority('SCOPE_SUPERADMIN')") y validar pertenencia de la asignación.
Test necesario: AdversarialAssignmentManagementTest.
Bloquea 2.0: SÍ
Bloquea producción: SÍ
Estado: OPEN

ID: SEC-007
Severidad: P1
Categoría: Seguridad / Autorización
Módulo: Personas
Archivo: backend/src/main/java/com/saed/backend/person/controller/PersonaController.java
Línea: L25-54
Problema: Todos los endpoints CRUD de PersonaController carecen de @PreAuthorize.
Evidencia: PersonaController.java:25, 32, 38, 43, 49.
Causa raíz: Omisión de @PreAuthorize en controlador.
Impacto: Residentes o porteros pueden modificar o eliminar registros de personas de su organización.
Riesgo: Modificación no autorizada de datos personales.
Dependencias: Ninguna
Solución propuesta: Agregar @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD') or hasAuthority('SCOPE_ADMIN_ORGANIZACION')").
Test necesario: AdversarialPersonaControllerTest.
Bloquea 2.0: SÍ
Bloquea producción: SÍ
Estado: OPEN

ID: SEC-008
Severidad: P1
Categoría: Seguridad / Autorización
Módulo: Inhabitantes de Unidad
Archivo: backend/src/main/java/com/saed/backend/person/controller/UnitInhabitantController.java
Línea: L27-52
Problema: Endpoints para agregar propietarios y residentes a unidades carecen de @PreAuthorize.
Evidencia: UnitInhabitantController.java:27, 32, 40, 46.
Causa raíz: Omisión de @PreAuthorize en controlador.
Impacto: Usuarios sin privilegios administrativos pueden asociar personas a apartamentos ajenos.
Riesgo: Suplantación de identidad en unidades.
Dependencias: Ninguna
Solución propuesta: Proteger con @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')").
Test necesario: AdversarialUnitInhabitantTest.
Bloquea 2.0: SÍ
Bloquea producción: SÍ
Estado: OPEN

ID: SEC-009
Severidad: P1
Categoría: Seguridad / Information Disclosure
Módulo: Catálogos
Archivo: backend/src/main/java/com/saed/backend/catalog/controller/CatalogoController.java
Línea: L62-76
Problema: GET /api/v1/usuarios expone lista de usuarios, roles, nombres y emails de la organización sin @PreAuthorize.
Evidencia: CatalogoController.java:62-76.
Causa raíz: Exposición de catálogo administrativo en endpoint sin restricción de rol.
Impacto: Enumeración y filtración de datos de usuarios de la organización.
Riesgo: Fuga de información personal (PII).
Dependencias: Ninguna
Solución propuesta: Restringir a @PreAuthorize("hasAuthority('SCOPE_ADMIN_ORGANIZACION') or hasAuthority('SCOPE_ADMIN_PROPIEDAD')").
Test necesario: CatalogoSecurityTest.
Bloquea 2.0: SÍ
Bloquea producción: SÍ
Estado: OPEN

ID: SEC-010
Severidad: P1
Categoría: Seguridad / IDOR
Módulo: Finanzas Residente
Archivo: backend/src/main/java/com/saed/backend/finanzas/controller/ResidentesFinanzasController.java
Línea: L18-20
Problema: GET /api/v1/residentes/{id}/dashboard consulta cuotas por {id} sin validar que coincida con el usuario en SaedContext.
Evidencia: ResidentesFinanzasController.java:18 y FinanzasRepositoryImpl.java:83-100.
Causa raíz: Parámetro de ruta confiado ciegamente sin validación de ownership.
Impacto: Un residente puede consultar las cuotas y deudas de cualquier otro residente de la copropiedad.
Riesgo: Fuga de privacidad y datos financieros.
Dependencias: SEC-001
Solución propuesta: Obtener el ID de la persona directamente desde SaedContextHolder y validar contra {id}.
Test necesario: AdversarialResidentDashboardTest.
Bloquea 2.0: SÍ
Bloquea producción: SÍ
Estado: OPEN

ID: BE-001
Severidad: P2
Categoría: Arquitectura
Módulo: 15 Controllers
Archivo: backend/src/main/java/com/saed/backend/**/controller/*Controller.java
Línea: Varios
Problema: 15 controladores inyectan NamedParameterJdbcTemplate directamente con SQL en línea.
Evidencia: CarteraController, ConciliacionController, GastosController, PresupuestoController, etc.
Causa raíz: Evasión de las capas de Service y Repository.
Impacto: Código acoplado, testing difícil, riesgo de inconsistencia transaccional.
Riesgo: Mantenibilidad y deuda técnica acumulativa.
Dependencias: Ninguna
Solución propuesta: Extraer lógica a Services y Repositories desacoplados.
Test necesario: Tests de integración por servicio.
Bloquea 2.0: SÍ
Bloquea producción: NO
Estado: OPEN

ID: BE-002
Severidad: P2
Categoría: Código Muerto
Módulo: Comunicación
Archivo: backend/src/main/java/com/saed/backend/comunicacion/controller/ComunicadosController.java
Línea: L78-88
Problema: Endpoints stub /confirmar-pendiente y /confirmar retornan vacíos o 200 sin lógica.
Evidencia: ComunicadosController.java:78-88.
Causa raíz: Código residual de SAED 1.0.
Impacto: Endpoints zombies en la API.
Riesgo: Confusión en documentación OpenAPI y clientes.
Dependencias: Ninguna
Solución propuesta: Eliminar los endpoints y limpiar rutas frontend asociadas.
Test necesario: Build y verify.
Bloquea 2.0: SÍ
Bloquea producción: NO
Estado: OPEN

ID: BE-003
Severidad: P2
Categoría: Auditoría
Módulo: Mutaciones
Archivo: backend/src/main/java/com/saed/backend/**
Línea: Varios
Problema: Mutaciones operativas estándar (pagos, contratos, residentes) no registran trazas en AUDITORIA_LOG.
Evidencia: Falta de invocación de SP_REGISTRAR_AUDITORIA en servicios de negocio.
Causa raíz: Auditoría no implementada de forma transversal.
Impacto: Ausencia de trazabilidad para auditoría forense.
Riesgo: No conformidad con requisitos de auditoría de SAED 2.0.
Dependencias: Ninguna
Solución propuesta: Implementar aspecto AOP sobre servicios de mutación.
Test necesario: AuditoriaAspectIntegrationTest.
Bloquea 2.0: SÍ
Bloquea producción: NO
Estado: OPEN

ID: BE-004
Severidad: P1
Categoría: Backend / Validación
Módulo: Controllers
Archivo: backend/src/main/java/com/saed/backend/**/controller/*Controller.java
Línea: Varios
Problema: Uso de Map<String, Object> payload sin Bean Validation (@Valid) en peticiones de mutación.
Evidencia: GastosController, PresupuestoController, ConciliacionController, etc.
Causa raíz: Falta de DTOs tipados.
Impacto: Excepciones de base de datos en runtime por valores nulos o inválidos.
Riesgo: Inestabilidad en la API REST.
Dependencias: Ninguna
Solución propuesta: Crear DTOs fuertemente tipados con @NotNull, @Positive, etc.
Test necesario: ControllerValidationTest.
Bloquea 2.0: SÍ
Bloquea producción: SÍ
Estado: OPEN

ID: BE-005
Severidad: P1
Categoría: Backend / Information Disclosure
Módulo: Dashboard Residente
Archivo: backend/src/main/java/com/saed/backend/dashboard/controller/DashboardController.java
Línea: L17-40
Problema: getFrecuentes y getQrActivos devuelven todos los registros de la copropiedad sin filtrar por residente; asignarApartamento es un stub.
Evidencia: DashboardController.java:20, 33, 38.
Causa raíz: Consultas SQL directas sin cláusula WHERE por unidad o persona.
Impacto: Residente ve los visitantes frecuentes y códigos QR de todos los vecinos.
Riesgo: Fuga grave de privacidad y seguridad física.
Dependencias: SEC-001
Solución propuesta: Filtrar por la unidad y persona del residente autenticado.
Test necesario: DashboardResidentAdversarialTest.
Bloquea 2.0: SÍ
Bloquea producción: SÍ
Estado: OPEN

ID: BE-007
Severidad: P2
Categoría: Arquitectura / Transacciones
Módulo: Finanzas / Cartera
Archivo: backend/src/main/java/com/saed/backend/finanzas/controller/CarteraController.java
Línea: L83-128
Problema: recalcular() ejecuta múltiples sentencias SQL de modificación (MERGE + DELETE) sin @Transactional.
Evidencia: CarteraController.java:83-128.
Causa raíz: Lógica transaccional ejecutada directamente en el controlador sin límites transaccionales.
Impacto: Estado inconsistente de la cartera si la segunda consulta falla.
Riesgo: Corrupción de datos financieros.
Dependencias: BE-001
Solución propuesta: Mover a CarteraService con @Transactional.
Test necesario: CarteraTransactionRollbackTest.
Bloquea 2.0: SÍ
Bloquea producción: NO
Estado: OPEN

ID: DB-001
Severidad: P1
Categoría: Base de Datos / Migraciones
Módulo: Migraciones SQL
Archivo: database/migrations/
Línea: 17 archivos
Problema: Migraciones fragmentadas con versiones superpuestas y scripts de rollback que impiden reconstrucción desde cero.
Evidencia: Listado de database/migrations/.
Causa raíz: Falta de proceso de baseline unificado.
Impacto: Despliegues no reproducibles en ambientes nuevos (Oracle ATP Cloud).
Riesgo: Fallo crítico de inicialización en producción.
Dependencias: Ninguna
Solución propuesta: Consolidar en database/schema/V5.0__master_baseline.sql.
Test necesario: Reconstrucción de base de datos vacía desde cero.
Bloquea 2.0: SÍ
Bloquea producción: SÍ
Estado: OPEN

ID: FE-001
Severidad: P2
Categoría: Frontend / UX
Módulo: 12 Páginas Secundarias
Archivo: frontend/src/pages/
Línea: Varios
Problema: Manejo heterogéneo de empty/loading/error states con textos planos en lugar de componentes estándar.
Evidencia: GananciasPage.jsx, FlujoCajaPage.jsx.
Causa raíz: Falta de uso universal del componente DataTable.
Impacto: UX inconsistente.
Riesgo: Percepción de baja calidad visual.
Dependencias: Ninguna
Solución propuesta: Estandarizar a componentes universales de estado.
Test necesario: Verificación visual y build.
Bloquea 2.0: SÍ
Bloquea producción: NO
Estado: OPEN

ID: FE-002
Severidad: P2
Categoría: Frontend / Rendimiento
Módulo: Build Vite
Archivo: frontend/src/pages/
Línea: Varios
Problema: Chunk xlsx.min.js (627 kB) supera límite de 500 kB.
Evidencia: Output de npm run build.
Causa raíz: Importación estática de librería pesada de hojas de cálculo.
Impacto: Mayor tiempo de carga inicial en navegadores.
Riesgo: Rendimiento degradado en clientes móviles.
Dependencias: Ninguna
Solución propuesta: Implementar carga diferida dinámica (import('xlsx')).
Test necesario: npm run build con bundle analysis.
Bloquea 2.0: SÍ
Bloquea producción: NO
Estado: OPEN

ID: FE-003
Severidad: P2
Categoría: Frontend / Rutas
Módulo: Navegación
Archivo: frontend/src/lib/access.js
Línea: L25-61
Problema: ACCESS_BY_ROLE solo lista 16 rutas de 38; roleCanAccess devuelve false para 22 páginas de administración.
Evidencia: access.js:25-61 vs App.jsx.
Causa raíz: Lista de rutas desactualizada frente a App.jsx.
Impacto: Redirección forzada a /dashboard tras login si se accede a páginas no listadas.
Riesgo: Navegación rota tras autenticación.
Dependencias: Ninguna
Solución propuesta: Sincronizar ACCESS_BY_ROLE con la totalidad de rutas declaradas en App.jsx.
Test necesario: Test de navegación y login redirect.
Bloquea 2.0: SÍ
Bloquea producción: NO
Estado: OPEN

ID: TEST-001
Severidad: P1
Categoría: Testing
Módulo: Suite Adversarial
Archivo: backend/src/test/
Línea: Varios
Problema: Ausencia de pruebas automatizadas para los Ataques Adversariales E a L.
Evidencia: Inventario de 39 clases de prueba.
Causa raíz: Cobertura adversarial parcial limitada a GETs básicos.
Impacto: Falta de verificación automatizada de mutaciones cross-tenant.
Riesgo: Regresiones de seguridad no detectadas en CI/CD.
Dependencias: SEC-001
Solución propuesta: Crear clases de prueba para los 12 ataques obligatorios.
Test necesario: Suite Adversarial A-L.
Bloquea 2.0: SÍ
Bloquea producción: SÍ
Estado: OPEN

ID: TEST-002
Severidad: P2
Categoría: Testing / E2E
Módulo: End-to-End
Archivo: N/A
Línea: N/A
Problema: Inexistencia de suite automatizada de pruebas E2E con Playwright.
Evidencia: Búsqueda de specs de test en frontend.
Causa raíz: Pruebas E2E no configuradas en el pipeline.
Impacto: No se prueban flujos reales de navegador entre frontend y backend.
Riesgo: Fallas de integración cliente-servidor en producción.
Dependencias: FE-003
Solución propuesta: Implementar suite Playwright para los 4 roles principales.
Test necesario: Ejecución de tests Playwright en headless mode.
Bloquea 2.0: SÍ
Bloquea producción: NO
Estado: OPEN

ID: TEST-004
Severidad: P1
Categoría: Testing / Falsos Positivos
Módulo: Concurrencia
Archivo: backend/src/test/java/com/saed/backend/security/ContextBleedIntegrationTest.java
Línea: L75-84
Problema: El test captura Exception (Failed to obtain JDBC Connection), imprime mensaje y retorna true, pasando en verde sin probar Oracle.
Evidencia: ContextBleedIntegrationTest.java:75-84 y log de ejecución.
Causa raíz: Try/catch ciego que enmascara fallos de base de datos.
Impacto: Falso positivo que da apariencia de aislamiento cuando no se está ejecutando la prueba.
Riesgo: Fugas de contexto concurrentes no detectadas.
Dependencias: TEST-005
Solución propuesta: Conectar a Oracle XE real y fallar explícitamente si ocurre una excepción de conexión.
Test necesario: ContextBleedIntegrationTest reescrito.
Bloquea 2.0: SÍ
Bloquea producción: SÍ
Estado: OPEN

ID: TEST-005
Severidad: P1
Categoría: Testing / Configuración
Módulo: Perfiles de Prueba
Archivo: backend/src/main/resources/application-test.yml
Línea: L8-11
Problema: Perfil test está configurado con H2 (jdbc:h2:mem:testdb) que no soporta VPD ni paquetes de Oracle XE.
Evidencia: application-test.yml:8-11.
Causa raíz: Configuración de H2 para tests unitarios mezclada con tests de integración.
Impacto: Tests de integración fallan silenciosamente al intentar invocar procedimientos Oracle.
Riesgo: Incompatibilidad de tests con la base de datos real.
Dependencias: Ninguna
Solución propuesta: Configurar tests de integración para ejecutar contra Oracle XE local real.
Test necesario: mvn clean test sobre perfil unificado.
Bloquea 2.0: SÍ
Bloquea producción: SÍ
Estado: OPEN

ID: DEP-001
Severidad: P1
Categoría: Despliegue / Cloud
Módulo: Render Config
Archivo: render.yaml
Línea: L11, L29-36
Problema: healthCheckPath apunta a POST /api/v1/auth/login (retorna 405 a GET) y faltan variables BREVO_API_KEY y WOMPI_*.
Evidencia: render.yaml:11, 29-36.
Causa raíz: Archivo render.yaml no actualizado con las especificaciones de SAED 2.0.
Impacto: Render marcará el backend como no saludable y reiniciará el servicio; emails y pagos fallarán.
Riesgo: Despliegue en producción bloqueado.
Dependencias: Ninguna
Solución propuesta: Cambiar health check a GET /actuator/health o GET /api/v1/me y agregar variables de entorno.
Test necesario: Validación de configuración de Render.
Bloquea 2.0: SÍ
Bloquea producción: SÍ
Estado: OPEN

ID: SEC-004
Severidad: P2
Categoría: Seguridad / Logging
Módulo: DataSource Proxy
Archivo: backend/src/main/java/com/saed/backend/config/SaedDataSourceProxy.java
Línea: L52
Problema: System.out.println imprime IDs de usuario, organización y rol en consola en cada conexión del pool.
Evidencia: SaedDataSourceProxy.java:52.
Causa raíz: Salida de depuración manual no eliminada.
Impacto: Contaminación de logs y fuga de metadatos.
Riesgo: Exposición de identificadores en entornos compartidos.
Dependencias: Ninguna
Solución propuesta: Migrar a log.debug(...) con SLF4J.
Test necesario: Inspección de logs en build.
Bloquea 2.0: SÍ
Bloquea producción: NO
Estado: OPEN

ID: SEC-005
Severidad: P2
Categoría: Seguridad / API
Módulo: Global Exception Handler
Archivo: backend/src/main/java/com/saed/backend/common/exception/GlobalExceptionHandler.java
Línea: L45-90
Problema: Posible exposición de nombres de tablas o constraints en respuestas HTTP de error.
Evidencia: GlobalExceptionHandler.java.
Causa raíz: Manejo de errores que no sanitiza completamente excepciones de persistencia.
Impacto: Fuga de estructura interna del esquema Oracle.
Riesgo: Information disclosure.
Dependencias: Ninguna
Solución propuesta: Sanitizar universalmente las respuestas con mensajes canónicos.
Test necesario: ExceptionSanitizationTest.
Bloquea 2.0: SÍ
Bloquea producción: NO
Estado: OPEN

ID: FUNC-001
Severidad: P2
Categoría: Funcionalidad
Módulo: Automatizaciones
Archivo: N/A
Línea: N/A
Problema: Motor de automatizaciones (Evento ➔ Condición ➔ Acción) pendiente de desarrollo.
Evidencia: Tablas REGLAS_AUTOMATIZACION creadas en Oracle pero sin servicio ni UI.
Causa raíz: Módulo planificado para Fase 30.
Impacto: Automatizaciones no operativas.
Riesgo: No cumplimiento del alcance del Documento Maestro.
Dependencias: Ninguna
Solución propuesta: Desarrollar scheduler, motor y UI en Fase 30.
Test necesario: AutomationEngineIntegrationTest.
Bloquea 2.0: SÍ
Bloquea producción: NO
Estado: OPEN

ID: FUNC-002
Severidad: P2
Categoría: Funcionalidad
Módulo: Consumos
Archivo: N/A
Línea: N/A
Problema: Medición de servicios públicos y consumos por unidad pendiente de desarrollo.
Evidencia: Tabla MEDICIONES_CONSUMO creada sin API ni frontend.
Causa raíz: Módulo planificado para Fase 28.
Impacto: Módulo no operativo.
Riesgo: No cumplimiento del alcance del Documento Maestro.
Dependencias: Ninguna
Solución propuesta: Desarrollar API y dashboard de consumos en Fase 28.
Test necesario: ConsumoServiceTest.
Bloquea 2.0: SÍ
Bloquea producción: NO
Estado: OPEN

ID: TECH-003
Severidad: P3
Categoría: Deuda Técnica
Módulo: Repositorio
Archivo: backend_legacy/
Línea: 164 archivos
Problema: Código y DAOs obsoletos de SAED 1.0 presentes en el repositorio.
Evidencia: git ls-files backend_legacy.
Causa raíz: Archivos históricos no archivados.
Impacto: Ruido en búsquedas y aumento innecesario del tamaño del repo.
Riesgo: Confusión en mantenimiento.
Dependencias: Ninguna
Solución propuesta: Archivar o eliminar de la rama principal en Fase 42.
Test necesario: git status.
Bloquea 2.0: NO
Bloquea producción: NO
Estado: OPEN
```

---

## 10. Mapa de Dependencias entre Módulos y Defectos

```text
[DB-001] Consolidación V5.0 Master Baseline
   │
   ▼
[SEC-001] Fix Predicado FN_FILTRO_UNIDAD en Oracle RLS
   │
   ├─────────────────────────────────────────┐
   ▼                                         ▼
[TEST-001 / TEST-004 / TEST-005]           [SEC-006 a SEC-010 / BE-005]
Suite 12 Ataques Adversariales             Fix @PreAuthorize e IDORs en Controllers
   │                                         │
   └────────────────────┬────────────────────┘
                        │
                        ▼
            [BE-001 / BE-004 / BE-007]
            Refactorización de 15 Controladores a Services + DTOs + @Transactional
                        │
                        ▼
            [SEC-002] Fix Wompi Webhook & Sandbox Test ──► Cartera y Finanzas
                        │
                        ▼
            [FE-003 / FE-001 / FE-002] Fix access.js y Hardening UI ──► Suite Playwright E2E
                        │
                        ▼
            [DEP-001] Fix render.yaml ──► Despliegue en Render & Vercel
```

---

## 11. Recomendación del Orden de Implementación (Nuevo Roadmap)

1. **UNIDAD 1 — Seguridad y RLS Inmediata (Fase 3):**
   * Corregir `FN_FILTRO_UNIDAD` en `PKG_SAED_SECURITY_RLS` (`SEC-001`).
   * Proteger con `@PreAuthorize` y validar context ownership en `AssignmentManagementController` (`SEC-006`), `PersonaController` (`SEC-007`), `UnitInhabitantController` (`SEC-008`), `CatalogoController` (`SEC-009`), `ResidentesFinanzasController` (`SEC-010`) y `DashboardController` (`BE-005`).
   * Reconfigurar `application-test.yml` y arreglar `ContextBleedIntegrationTest` (`TEST-004`, `TEST-005`).
   * Implementar la suite de pruebas de los **12 Ataques Adversariales (A a L)** contra Oracle XE (`TEST-001`).
2. **UNIDAD 2 — Base de Datos y Migraciones (Fase 4):**
   * Consolidar el esquema en `database/schema/V5.0__master_baseline.sql` y crear seed reproducible (`DB-001`).
3. **UNIDAD 3 — Refactorización Arquitectónica de Controladores (Fase 2 / Fase 34):**
   * Extraer `NamedParameterJdbcTemplate` de los 15 controladores hacia Services y Repositories tipados con DTOs validados (`BE-001`, `BE-004`, `BE-007`).
   * Eliminar stubs y código muerto (`BE-002`).
4. **UNIDAD 4 — Wompi y Pasarela de Pagos (Fase 7):**
   * Corregir `WompiServiceImpl` (`SEC-002`) y validar contra el sandbox oficial.
5. **UNIDAD 5 — Frontend Hardening y Rutas (Fase 6 / Fase 33):**
   * Corregir `access.js` (`FE-003`), carga diferida de `xlsx` (`FE-002`) y estados visuales (`FE-001`).
6. **UNIDAD 6 — Auditoría Transversal con AOP (Fase 5):**
   * Implementar aspecto AOP para registrar mutaciones sensibles en `AUDITORIA_LOG` (`BE-003`).
7. **UNIDAD 7 — Módulos Restantes y Automatizaciones (Fases 8 a 32):**
   * Implementar Motor de Automatizaciones (Fase 30) y Mediciones de Consumo (Fase 28).
8. **UNIDAD 8 — Testing E2E, Despliegue y Certificación (Fases 38 a 53):**
   * Suite Playwright E2E (`TEST-002`), corrección de `render.yaml` (`DEP-001`), despliegue y certificación final.
