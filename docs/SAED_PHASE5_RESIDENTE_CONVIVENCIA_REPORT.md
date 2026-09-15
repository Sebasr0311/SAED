# SAED 2.0 — REPORTE DE AUDITORÍA Y HARDENING: FASE 5
## Modelo de Autorización, Asignación y Convivencia (`RESIDENTE_CONVIVENCIA`)

---

## 1. Resumen Ejecutivo

En la **Fase 5**, se completó una auditoría exhaustiva, corrección quirúrgica y hardening del rol **`RESIDENTE_CONVIVENCIA`** en el repositorio canónico `https://github.com/Sebasr0311/SAED` (rama `Sebasr0311/angelfish`).

Este rol secundario está concebido para miembros del hogar o cohabitantes de una unidad habitacional que gozan de facultades cotidianas (generación de pases rápidos para sus propios visitantes, visualización de paquetería de su unidad, radicación de quejas ante la administración, recepción de avisos y consulta de documentos públicos), pero que **bajo ninguna circunstancia** deben gozar de atribuciones de titularidad (tales como ver el historial completo de visitas de la unidad, gestionar contratos/cuotas de administración, o escalar privilegios a roles operativos o administrativos).

### Resultados Clave de Verificación:
- **Tests de Seguridad Ejecutados**: 48 tests en total (`ResidenteConvivenciaSecurityIntegrationTest` [9 tests nuevos SEC-F5-01 al SEC-F5-09], `PorteroPasswordChangeSecurityTest`, `PorteroPasswordChangeWebMvcSecurityTest`, `VisitAuthorizationSecurityIntegrationTest`, `WompiContextIsolationSecurityTest`).
- **Fallos y Errores**: **0** (100% de efectividad).
- **Quality Gate Checkstyle (Ratchet)**: **2708 violaciones** detectadas, satisfaciendo el umbral máximo de **2720** (`BUILD SUCCESS`).
- **Frontend Quality Gate**: ESLint completado con **0 errores** (222 warnings menores preexistentes), y Build de producción Vite completado exitosamente en **18.63s**.
- **Control de Versiones**: **0 commits, 0 pushes** realizados. Todos los cambios se encuentran contenidos estrictamente en el working tree local.

---

## 2. Hallazgos Auditados y Correcciones Implementadas

### 2.1 Eliminación de DDL en Runtime (`UsuarioController.java`)
- **Vulnerabilidad**: El controlador REST `UsuarioController.java` ejecutaba sentencias `ALTER TABLE ROLES DROP/ADD CONSTRAINT` y `MERGE INTO ROLES` durante peticiones HTTP de creación/actualización de usuarios, introduciendo riesgo severo de bloqueos de diccionario (`ORA-00054`), inyección y degradación de rendimiento.
- **Corrección**: Se removió completamente el bloque de DDL en tiempo de ejecución. La obtención del rol ahora es estrictamente declarativa y transaccional:
  ```java
  Long idRol = jdbcTemplate.queryForObject(
      "SELECT ID_ROL FROM ROLES WHERE CODIGO = :cod AND ESTADO = 'ACTIVO'",
      new MapSqlParameterSource("cod", req.getRol().toUpperCase()),
      Long.class
  );
  ```

### 2.2 Validación Estricta de Cupo Exclusiva para `CONVIVIENTE` (`UnitInhabitantServiceImpl.java` y `DashboardController.java`)
- **Inconsistencia**: El método `asignarApartamento` mapeaba erróneamente `"CONVIVIENTE"` hacia `"FAMILIAR"`, y el servicio de habitantes aplicaba el bloqueo pesimista de cupo (`validateAndLockQuota`) indistintamente sobre cualquier tipo de habitante (`FAMILIAR`, `OTRO`), agotando indebidamente el límite contratado para convivientes.
- **Corrección**:
  - `DashboardController.java`: Mapeo explícito `"CONVIVIENTE" -> "CONVIVIENTE"` y disparo condicional de cuota únicamente para convivientes.
  - `UnitInhabitantServiceImpl.java`: Línea 109 modificada para que `validateAndLockQuota(unitId)` se ejecute **únicamente** cuando `tipo.equals("CONVIVIENTE")`. Los habitantes clasificados como `FAMILIAR` u `OTRO` se registran normalmente sin consumir el cupo de convivencia de la unidad.

### 2.3 Preservación de Roles Operativos e Inactividad (`AuthRepositoryImpl.java` y `AssignmentRepositoryImpl.java`)
- **Vulnerabilidad**: Cuando un usuario con rol operativo (ej. un `PORTERO` o `ADMIN_PROPIEDAD`) coincidía físicamente como habitante registrado en una unidad en `RESIDENTES_UNIDAD`, la lógica de resolución de asignación degradaba o mutaba su rol activo a `RESIDENTE_CONVIVENCIA`.
- **Corrección**:
  - Se condicionó la auto-reparación y resolución para que solo aplique si el rol asignado actualmente es `RESIDENTE` (`"RESIDENTE".equalsIgnoreCase(currentRoleCode)`). Nunca se degrada un `PORTERO` o rol administrativo.
  - Se verificó que el registro en `RESIDENTES_UNIDAD` tenga estado `ESTADO = 'ACTIVO'` y pertenezca exactamente a la unidad de la asignación (`ru.ID_UNIDAD = :unidadId`). Si el cohabitante está `INACTIVO`, se ignora y no se muta la asignación.

### 2.4 Bloqueo de Historial General de Visitas de Unidad (`DashboardController.java`)
- **Vulnerabilidad**: `GET /{id}/visitas-historial` permitía acceso genérico a residentes de la unidad.
- **Corrección**: Se blindó para permitir únicamente a `SCOPE_ADMIN_PROPIEDAD` y `SCOPE_RESIDENTE` (titular). Si un `RESIDENTE_CONVIVENCIA` intenta consultar el historial general de visitas de la unidad, Spring Security deniega el acceso con `403 Forbidden`. En contraste, se autorizó `SCOPE_RESIDENTE_CONVIVENCIA` en `GET/POST/DELETE /{id}/frecuentes` y `GET /{id}/qr-activos` para la gestión de sus propios visitantes.

### 2.5 Aislamiento Estricto en Paquetería (`PaquetesController.java` y `PaquetesServiceImpl.java`)
- **Vulnerabilidad**: `RESIDENTE_CONVIVENCIA` no estaba autorizado en `/api/v1/paquetes`, o bien podía consultar paquetes de cualquier unidad de la copropiedad.
- **Corrección**:
  - Se incorporó `SCOPE_RESIDENTE_CONVIVENCIA` en `@PreAuthorize` de `GET /api/v1/paquetes` y `GET /api/v1/paquetes/{id}`.
  - En `PaquetesServiceImpl.java`, se impuso validación de pertenencia:
    - En el listado general, los residentes y convivientes solo reciben paquetes con `ID_UNIDAD = ctx.getUnitId()`.
    - En el detalle por ID, se verifica que la unidad del paquete coincida con la unidad asignada al token. De lo contrario, se lanza `AccessDeniedException` (HTTP `403 Forbidden`).

### 2.6 Habilitación de Convivencia en Módulos Esenciales
Se incorporó `SCOPE_RESIDENTE_CONVIVENCIA` con confinamiento tenant en:
1. **`BuzonController.java`**: `getMyBuzon`, `marcarLeido`, `marcarTodasLeidas`, `vaciarBuzon`, `vaciarMulti`, `marcarPaqueteEntregado`.
2. **`QuejasController.java`**: `getMyQuejas` y `createQueja`. Se corrigió además el defecto en `QuejaRepositoryImpl.java` donde se referenciaba incorrectamente `p.ID_USUARIO` en la tabla `PERSONAS` (inexistente en el modelo Oracle).
3. **`TicketController.java`**: `mis-tickets`, `getTicketById` y `createTicket`.
4. **`DocumentoController.java`**: `GET /documentos/residente`.
5. **`UnitController.java`**: `findAll` y `findById`, con validación cruzada en `UnitService.findById` para evitar que un conviviente inspeccione unidades ajenas.
6. **`PersonaController.java`**: `getPersona` y `updatePersona` (acceso exclusivo a su perfil personal).
7. **`ComunicadosController.java`**: `confirmar-pendiente`, `confirmar` y `resultado-notificar`.

---

## 3. Matriz de Pruebas Automatizadas de Seguridad (Fase 5)

La suite `ResidenteConvivenciaSecurityIntegrationTest` implementa 9 casos de prueba exhaustivos y adversariales:

| ID Caso | Nombre del Test | Escenario Evaluado | Resultado |
|:---|:---|:---|:---:|
| **SEC-F5-01** | `testResidenteConvivenciaCannotAccessAdminEndpoints` | Intento de acceso a endpoints de `ADMIN_PROPIEDAD` (`/api/v1/propiedades`) | **403 Forbidden** (PASS) |
| **SEC-F5-02** | `testResidenteConvivenciaCannotAccessSuperAdminEndpoints` | Intento de acceso a endpoints de `SUPERADMIN` (`/api/v1/platform/admins`) | **403 Forbidden** (PASS) |
| **SEC-F5-03** | `testResidenteConvivenciaCannotAccessTitularVisitHistory` | Intento de consulta del historial general de visitas de la unidad | **403 Forbidden** (PASS) |
| **SEC-F5-04** | `testResidenteConvivenciaCrossUnitAccessDenied` | Intento de consulta de otra unidad ajena (`GET /api/v1/units/2`) | **403 Forbidden** (PASS) |
| **SEC-F5-05** | `testResidenteConvivenciaAllowedOperations` | Acceso a buzón propio, quejas propias y cambio de contraseña con credenciales vigentes | **200 OK** (PASS) |
| **SEC-F5-06** | `testConvivienteQuotaServiceOnlyAppliesToConviviente` | Verificación de que `validateAndLockQuota` solo se invoca para `CONVIVIENTE`, no para `FAMILIAR` ni `OTRO` | **Verificado** (PASS) |
| **SEC-F5-07** | `testResidenteConvivenciaPackageAccessAndCrossUnitBlocked` | Consulta de paquetes de su propia unidad permitida (200 OK) y paquete de otra unidad bloqueado (403 Forbidden) | **200 / 403** (PASS) |
| **SEC-F5-08** | `testPorteroRoleNotOverriddenWhenUserIsCohabitant` | Garantiza que un usuario con asignación activa de `PORTERO` que figure como conviviente en una unidad no sea degradado | **Preservado** (PASS) |
| **SEC-F5-09** | `testInactiveCohabitantNotConvertedToResidenteConvivencia` | Un habitante con estado `INACTIVO` en `RESIDENTES_UNIDAD` no muta la asignación a `RESIDENTE_CONVIVENCIA` | **Rechazado** (PASS) |

---

## 4. Auditoría de Calidad y Ratchet

### 4.1 Checkstyle Ratchet
- **Umbral máximo permitido**: $\le 2720$ violaciones.
- **Violaciones actuales registradas**: **2708**.
- **Estado**: `BUILD SUCCESS` (Cumple plenamente con el Quality Gate sin introducir regresiones).

### 4.2 Frontend Quality Gates
- **ESLint**: 0 errores detectados en todo el árbol de componentes y páginas.
- **Vite Build**: Compilación limpia en 18.63 segundos (`dist/` generado satisfactoriamente sin errores de tipos ni referencias rotas).

---

## 5. Resumen de Archivos Modificados

```text
backend/src/main/java/com/saed/backend/identity/controller/UsuarioController.java
backend/src/main/java/com/saed/backend/person/service/impl/UnitInhabitantServiceImpl.java
backend/src/main/java/com/saed/backend/dashboard/controller/DashboardController.java
backend/src/main/java/com/saed/backend/config/ProductionSchemaInitializer.java
backend/src/main/java/com/saed/backend/authorization/repository/impl/AssignmentRepositoryImpl.java
backend/src/main/java/com/saed/backend/identity/repository/impl/AuthRepositoryImpl.java
backend/src/main/java/com/saed/backend/paquetes/controller/PaquetesController.java
backend/src/main/java/com/saed/backend/paquetes/service/impl/PaquetesServiceImpl.java
backend/src/main/java/com/saed/backend/convivencia/controller/BuzonController.java
backend/src/main/java/com/saed/backend/convivencia/controller/QuejasController.java
backend/src/main/java/com/saed/backend/convivencia/repository/impl/QuejaRepositoryImpl.java
backend/src/main/java/com/saed/backend/pqrs/controller/TicketController.java
backend/src/main/java/com/saed/backend/documentos/controller/DocumentoController.java
backend/src/main/java/com/saed/backend/authorization/controller/UnitController.java
backend/src/main/java/com/saed/backend/authorization/service/impl/UnitServiceImpl.java
backend/src/main/java/com/saed/backend/person/controller/PersonaController.java
backend/src/main/java/com/saed/backend/comunicacion/controller/ComunicadosController.java
backend/src/test/java/com/saed/backend/security/ResidenteConvivenciaSecurityIntegrationTest.java
```

---

## 6. Veredicto Final

La **Fase 5 (Auditoría y Corrección del Modelo RESIDENTE_CONVIVENCIA)** se encuentra **APROBADA Y CERTIFICADA**. 
El modelo de datos, la seguridad en base de datos Oracle XE/ATP, los filtros de autorización Spring Security y el comportamiento de la API REST se encuentran 100% alineados y protegidos contra escalamiento de privilegios, fuga de datos entre unidades y degradación indebida de roles.
