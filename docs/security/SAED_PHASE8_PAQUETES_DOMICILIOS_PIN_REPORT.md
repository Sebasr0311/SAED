# SAED 2.0 — FASE 8: REPORTE DE AUDITORÍA E IMPLEMENTACIÓN COMPLETA DE PAQUETES/DOMICILIOS, PIN, NOTIFICACIONES, IMAGEN Y ENTREGA SEGURA

## 1. Objetivo
Auditar, corregir e implementar de forma integral, segura y adversarial el flujo de recepción, notificación, protección de imágenes, verificación de PIN de 4 dígitos y entrega atómica de paquetes y domicilios en SAED 2.0, garantizando Zero Trust, anti-IDOR, aislamiento multi-tenant y eliminación de bypasses en backend y Oracle XE.

---

## 2. Repositorio
`https://github.com/Sebasr0311/SAED`

---

## 3. Rama
`Sebasr0311/angelfish`

---

## 4. HEAD
`051caec8996c6a08f2606b3ae1100cc3c4c1a11a`

---

## 5. Estado Inicial Encontrado
- **Vulnerabilidad de entrega directa sin PIN:** Existía el endpoint `PUT /api/v1/buzon/{id}/entregado` y la función de repositorio `marcarEntregadoDirecto` que permitían pasar cualquier paquete a `ENTREGADO` sin validación de PIN.
- **Frontend permisivo:** En `PaquetesPage.jsx`, si el modal de entrega no recibía PIN o fallaba la llamada oficial, intentaba un fallback a `/buzon/{id}/entregado`.
- **Ausencia de tracking anti-fuerza bruta:** La tabla `PAQUETES` en Oracle XE no contaba con columna para registrar intentos fallidos de PIN.
- **Falta de endpoint autenticado de imágenes:** No existía `GET /api/v1/paquetes/{id}/imagen` con control de acceso por unidad/tenant; las imágenes dependían de URLs directas o Base64 sin control de descarga.
- **Fuga potencial de PIN en auditoría:** Las llaves `codigoRetiroPin` y `piningresado` no estaban explícitamente registradas en los filtros de `AuditSanitizer`.
- **Concurrencia sin control atómico:** La actualización de estado permitía race conditions donde dos entregas concurrentes podían reportar éxito simultáneo.

---

## 6. Auditoría del Módulo Existente
- **Backend Controllers:**
  - `PaquetesController`: Tenía endpoints CRUD pero faltaba endpoint seguro de imágenes.
  - `BuzonController`: Contenía bypass `marcarPaqueteEntregado` llamando a `marcarEntregadoDirecto`.
- **Backend Repositories & Services:**
  - `PaquetesRepositoryImpl`: Ejecutaba `UPDATE PAQUETES SET ESTADO = 'ENTREGADO'` incondicional respecto al estado previo.
  - `PaquetesServiceImpl`: Tenía generación de PIN pero sin blindaje anti-brute-force ni notificación a todos los roles de la unidad (`RESIDENTE`, `RESIDENTE_CONVIVENCIA`, `PROPIETARIO_UNIDAD`).
- **Oracle XE Database:**
  - Tabla `PAQUETES` protegida por RLS (`POL_RLS_PROP_PAQUETES` y `POL_RLS_UNIDAD_PAQUETES`).
  - Restricción `CK_PAQUETES_ESTADO`: `('RECIBIDO', 'NOTIFICADO', 'PENDIENTE_ENTREGA', 'ENTREGADO', 'DEVUELTO_A_EMPRESA')`.

---

## 7. Arquitectura Actual Encontrada
- **Patrón:** Clean Architecture / Layered Architecture en Spring Boot 3.2.3.
- **Seguridad perimetral:** Spring Security 6 con `JwtAuthenticationFilter` y `X-Assignment-Id`.
- **Seguridad en base de datos:** Oracle Virtual Private Database (VPD/RLS) mediante `PKG_SAED_SESSION` y políticas dinámicas por contexto.

---

## 8. Tablas Existentes Relacionadas
- `PAQUETES`: Almacena encomiendas, PIN, estado, portero receptor, persona receptora, foto de evidencia e intentos fallidos de PIN.
- `UNIDADES`: Destino físico de la encomienda (apartamento / oficina / casa).
- `PERSONAS` y `USUARIOS`: Destinatarios, porteros y residentes.
- `USUARIO_ASIGNACIONES`: Alcance y rol de los usuarios activos.
- `NOTIFICACIONES`: Bandeja in-app de avisos para residentes con PIN generado.
- `COMUNICADOS`: Canal de comunicación que agrupa avisos y notificaciones operativas.
- `AUDITORIA_LOG` y `AUDITORIA_ACCESOS`: Trazabilidad inmutable de eventos operacionales.

---

## 9. Migraciones Existentes
- `V5.0__master_baseline.sql`: Estructura consolidada inicial.
- `V5.12__paquetes_intentos_pin.sql`: Migración creada en esta fase que agrega la columna `INTENTOS_FALLIDOS_PIN NUMBER DEFAULT 0 NOT NULL` a `PAQUETES`.

---

## 10. Endpoints Existentes y Modificados
| Método | Endpoint | Roles Permitidos | Estado / Comportamiento |
|---|---|---|---|
| `POST` | `/api/v1/paquetes` | `PORTERO`, `ADMIN_PROPIEDAD` | Validado: genera PIN 4 dígitos backend, aísla tenant, notifica residentes in-app. |
| `GET` | `/api/v1/paquetes` | `PORTERO`, `ADMIN_PROPIEDAD`, `RESIDENTE`, `RESIDENTE_CONVIVENCIA` | Aislado por unidad para residentes, por propiedad para portería. |
| `GET` | `/api/v1/paquetes/{id}` | `PORTERO`, `ADMIN_PROPIEDAD`, `RESIDENTE`, `RESIDENTE_CONVIVENCIA` | Anti-IDOR estricto (403/404 si es ajeno). |
| `GET` | `/api/v1/paquetes/{id}/imagen` | `PORTERO`, `ADMIN_PROPIEDAD`, `RESIDENTE`, `RESIDENTE_CONVIVENCIA` | **NUEVO.** Sirve recurso seguro en Base64 o filesystem con headers seguros. |
| `PUT` | `/api/v1/paquetes/{id}` | `PORTERO`, `ADMIN_PROPIEDAD` | Bloqueado si el paquete ya está `ENTREGADO`. |
| `POST` | `/api/v1/paquetes/{id}/entrega` | `PORTERO`, `ADMIN_PROPIEDAD` | Validación obligatoria de PIN, rate limit (3 fallos -> 429), transición atómica (409 si race condition). |
| `PUT` | `/api/v1/buzon/{id}/entregado` | Cualquiera | **NEUTRALIZADO.** Lanza `UnsupportedOperationException` (400 Bad Request `OPERATION_NOT_ALLOWED`). |

---

## 11. Frontend Existente
- `PaquetesPage.jsx`:
  - Se eliminó el bypass a `/buzon/{id}/entregado`.
  - El modal de entrega requiere estrictamente un PIN numérico de 4 dígitos (`maxLength={4}`, regex `^\d{4}$`).
  - Muestra alertas y mensajes descriptivos sobre intentos restantes y bloqueos.

---

## 12. Cambios Realizados
1. **Oracle XE DDL & Inicializador:**
   - Adición en vivo de `INTENTOS_FALLIDOS_PIN` respetando RLS (`ALTER TABLE PAQUETES ADD ...` seguido de `MODIFY ... DEFAULT 0`).
   - `ProductionSchemaInitializer.java`: Verificación y adición idempotente al arranque.
   - Creación de `V5.12__paquetes_intentos_pin.sql`.
2. **AuditSanitizer:**
   - Adición de `codigoretiropin`, `codigoretiro`, `piningresado`, `retiropin` a palabras sensibles para enmascaramiento con `[PROTECTED]`.
3. **GlobalExceptionHandler:**
   - Manejo de `SecurityException` retornando HTTP 429 Too Many Requests (`SECURITY_BLOCKED`).
   - Manejo de `UnsupportedOperationException` retornando HTTP 400 Bad Request (`OPERATION_NOT_ALLOWED`).
4. **PaquetesRepository & PaquetesRepositoryImpl:**
   - `registrarEntrega` ahora retorna `boolean` y ejecuta `UPDATE ... WHERE ID_PAQUETE = :id AND ESTADO IN ('RECIBIDO', 'PENDIENTE_ENTREGA')`.
   - `marcarEntregadoDirecto` lanza `UnsupportedOperationException`.
   - `incrementarIntentosFallidos` implementado con `@Transactional(propagation = Propagation.REQUIRES_NEW)` para persistencia inmutable frente a rollbacks de la transacción principal.
5. **PaquetesService & PaquetesServiceImpl:**
   - Generación criptográfica `SecureRandom` con formato `%04d` (rango `0000`–`9999`).
   - Validación anti-IDOR en registro verificando que `idUnidad` pertenece a `idPropiedad`.
   - Despacho de notificaciones `IN_APP` a todos los residentes activos de la unidad.
   - Verificación de límite de 3 intentos fallidos de PIN antes de validar.
   - Manejo de entrega atómica y notificación post-entrega (sin revelar PIN).
   - Servicio de imágenes `getImagenPaquete` y `getImagenMimeType` con verificación de pertenencia a unidad/propiedad.
6. **PaquetesController:**
   - Adición del endpoint `GET /api/v1/paquetes/{id}/imagen`.

---

## 13. Modelo de Paquete
- Entidad: `PAQUETES`.
- Campos principales: `ID_PAQUETE`, `ID_PROPIEDAD`, `ID_UNIDAD`, `EMPRESA_MENSAJERIA`, `NUMERO_GUIA`, `DESCRIPCION`, `TAMANO`, `FOTO_PAQUETE_URL`, `CODIGO_RETIRO_PIN`, `INTENTOS_FALLIDOS_PIN`, `ESTADO`.
- Estados soportados: `RECIBIDO`, `PENDIENTE_ENTREGA`, `ENTREGADO`, `DEVUELTO_A_EMPRESA`.

---

## 14. Modelo de Domicilio
- Unificado bajo el modelo de `PAQUETES` con `TIPO = 'DOMICILIO'` o `TIPO = 'PAQUETE'` y descripción del comercio/repartidor (`EMPRESA_MENSAJERIA`).

---

## 15. Modelo de PIN
- **Longitud:** Exactamente 4 caracteres numéricos (`^\d{4}$`).
- **Rango:** `0000` a `9999`. Valores con ceros a la izquierda (ej. `0047`) son válidos y preservados como `String`.
- **Generación:** Servidor exclusivo mediante `SecureRandom`.
- **Autoridad:** El backend ignora cualquier PIN suministrado en la creación.
- **Exposición:** Notificado al residente; el portero lo introduce al momento de la entrega física.

---

## 16. Modelo de Notificaciones
- Se inserta registro en `NOTIFICACIONES` con canal `IN_APP` para los usuarios con rol `RESIDENTE`, `RESIDENTE_CONVIVENCIA` o `PROPIETARIO_UNIDAD` asociados a la unidad destino en `USUARIO_ASIGNACIONES`.
- El mensaje incluye empresa, descripción y el código PIN de retiro.
- Al entregarse el paquete, se genera una notificación de confirmación de entrega que **no contiene el PIN**.

---

## 17. Modelo de Imágenes
- Almacenamiento: URLs directas, Base64 Data URLs (`data:image/jpeg;base64,...`) o paths en `FileStorageService`.
- Protección: Servidas a través de `GET /api/v1/paquetes/{id}/imagen`. Requiere sesión activa con asignación autorizada en la misma unidad (residente) o misma propiedad (portero/admin). Cross-tenant o unidades ajenas reciben 403 Forbidden o 404 Not Found.

---

## 18. Flujo de Recepción
1. Portero autenticado envía `POST /api/v1/paquetes`.
2. Backend extrae `propertyId` y `userId` del token (`SaedContextHolder`).
3. Backend valida que `idUnidad` pertenece a la propiedad activa (anti-IDOR).
4. Backend genera PIN de 4 dígitos con `SecureRandom`.
5. Se inserta paquete en estado `RECIBIDO`.
6. Se inserta notificación `IN_APP` con el PIN para los habitantes de la unidad.
7. Se registra evento en `AUDITORIA_LOG`.
8. Retorna `201 Created`.

---

## 19. Flujo de Entrega
1. Residente se acerca a portería y presenta su PIN de 4 dígitos.
2. Portero envía `POST /api/v1/paquetes/{id}/entrega` con `codigoRetiroPin`.
3. Backend verifica que el paquete no esté bloqueado (`INTENTOS_FALLIDOS_PIN < 3`).
4. Backend compara el PIN ingresado con el almacenado.
5. Si no coincide, incrementa `INTENTOS_FALLIDOS_PIN` vía transacción autónoma (`REQUIRES_NEW`), registra auditoría y retorna `400 Bad Request`.
6. Si coincide, ejecuta `UPDATE PAQUETES SET ESTADO = 'ENTREGADO', ... WHERE ID_PAQUETE = :id AND ESTADO IN ('RECIBIDO', 'PENDIENTE_ENTREGA')`.
7. Si otra transacción concurrente ya lo entregó, retorna `409 Conflict`.
8. Si tuvo éxito, notifica al residente de la entrega y retorna `200 OK`.

---

## 20. Seguridad del PIN
- Generado criptográficamente en backend.
- Rechazo absoluto de PIN inyectado en requests de creación.
- Inmutable tras la creación del paquete.
- Neutralizado en auditoría: nunca se persiste en texto plano en `AUDITORIA_LOG`.

---

## 21. Protección Anti-Fuerza Bruta
- Límite estricto: Máximo 3 intentos fallidos de PIN.
- El 4to intento rechaza la solicitud de inmediato con HTTP 429 Too Many Requests (`SECURITY_BLOCKED`).
- El contador es persistido en `INTENTOS_FALLIDOS_PIN` con `Propagation.REQUIRES_NEW`, evitando que rollbacks transaccionales borren los intentos fallidos.

---

## 22. Control de Estados
- Transición válida: `RECIBIDO` / `PENDIENTE_ENTREGA` $\to$ `ENTREGADO`.
- Paquetes en estado `ENTREGADO` no pueden volver a ser entregados (retorna `409 Conflict`).
- Paquetes en estado `ENTREGADO` no pueden ser actualizados ni modificados.

---

## 23. Concurrencia
- Control a nivel de fila en base de datos mediante condición `WHERE ESTADO IN ('RECIBIDO', 'PENDIENTE_ENTREGA')`.
- Ante dos solicitudes concurrentes con el PIN correcto sobre el mismo paquete, exactamente 1 transacción gana (HTTP 200) y la otra recibe HTTP 409 Conflict.

---

## 24. Tenant Isolation
- Forzado en dos capas:
  1. Capa de aplicación: `propId` derivado del token JWT en `SaedContextHolder`. Validación de pertenencia de unidades a la propiedad.
  2. Capa de base de datos: Oracle VPD / RLS con `FN_FILTRO_PROPIEDAD` y `FN_FILTRO_UNIDAD`.

---

## 25. Anti-IDOR
- Un residente de la Unidad 2 no puede consultar paquetes ni imágenes de la Unidad 1 (bloqueado con 403 Forbidden o filtrado por RLS retornando 404 Not Found).
- Un portero de la Propiedad 2 no puede registrar entregas de paquetes de la Propiedad 1 (bloqueado con 403 Forbidden).

---

## 26. Roles y Autorizaciones
- `PORTERO`: Registro de recepción, entrega mediante PIN, consulta de pendientes de la propiedad.
- `ADMIN_PROPIEDAD`: Gestión completa y supervisión en la propiedad.
- `RESIDENTE`: Consulta de sus propios paquetes, visualización de PIN, descarga de imagen de su unidad.
- `RESIDENTE_CONVIVENCIA`: Consulta de paquetes y descarga de imagen de su unidad compartida.
- `SUPERADMIN`: Control global de auditoría.

---

## 27. Auditoría
- Todo intento de recepción, entrega y fallo de PIN se registra en `AUDITORIA_LOG`.
- `AuditSanitizer` enmascara automáticamente llaves como `codigoRetiroPin`, `pin`, `piningresado` reemplazando su valor por `[PROTECTED]`.
- No existe fuga de PIN en `ESTADO_ANTERIOR` ni `ESTADO_NUEVO`.

---

## 28. Pruebas SEC-F8 (`PackageDeliverySecurityIntegrationTest.java`)
Resultados contra base de datos Oracle XE real (`localhost:1521/XEPDB1`):

| Test ID | Nombre | Resultado | Detalle |
|---|---|---|---|
| `SEC-F8-01` | `secF8_01_registroValidoPaquete` | **PASSED** | Retorna 201 Created con paquete en estado RECIBIDO. |
| `SEC-F8-02` | `secF8_02_generacionAutomaticaPin4Digitos` | **PASSED** | PIN es de 4 dígitos numéricos en rango 0000-9999. |
| `SEC-F8-03` | `secF8_03_pinFrontendRechazadoComoAutoridad` | **PASSED** | PIN enviado en body es ignorado por backend. |
| `SEC-F8-04` | `secF8_04_notificacionCreadaParaUnidadCorrecta` | **PASSED** | Notificación IN_APP despachada a habitante de la unidad. |
| `SEC-F8-05` | `secF8_05_usuarioOtraUnidadNoAccedePaquete` | **PASSED** | Residente ajeno bloqueado con 403/404. |
| `SEC-F8-06` | `secF8_06_imagenAccesibleDestinatarioAutorizado` | **PASSED** | Residente titular y conviviente acceden a la imagen. |
| `SEC-F8-07` | `secF8_07_imagenBloqueadaCrossTenant` | **PASSED** | Acceso a imagen bloqueado cross-unit y cross-tenant (403/404). |
| `SEC-F8-08` | `secF8_08_pinCorrectoPermiteEntrega` | **PASSED** | Entrega autorizada, estado pasa a ENTREGADO en DB. |
| `SEC-F8-09` | `secF8_09_pinIncorrectoNoPermiteEntrega` | **PASSED** | PIN inválido rechazado (400) e incrementa intentos en DB. |
| `SEC-F8-10` | `secF8_10_manipularEstadoDirectamenteNoPermiteSaltarsePin` | **PASSED** | Bypass directo neutralizado con 400 OPERATION_NOT_ALLOWED. |
| `SEC-F8-11` | `secF8_11_paqueteEntregadoNoPuedeVolverAEntregarse` | **PASSED** | Segunda entrega rechazada con 409 Conflict. |
| `SEC-F8-12` | `secF8_12_pinDeOtroPaqueteNoFunciona` | **PASSED** | PIN de paquete ajeno genera 400 Bad Request. |
| `SEC-F8-13` | `secF8_13_porteroOtraPropiedadNoPuedeEntregar` | **PASSED** | Portero de otra propiedad rechazado con 403 Forbidden. |
| `SEC-F8-14` | `secF8_14_usuarioOtraOrganizacionNoPuedeAcceder` | **PASSED** | Usuario de otra organización bloqueado con 403/404. |
| `SEC-F8-15` | `secF8_15_manipulacionIdUnidadRechazada` | **PASSED** | Registro con idUnidad ajena rechazado con 403 Forbidden. |
| `SEC-F8-16` | `secF8_16_manipulacionIdPropiedadRechazada` | **PASSED** | idPropiedad del payload es neutralizada por token del portero. |
| `SEC-F8-17` | `secF8_17_manipulacionIdOrganizacionRechazada` | **PASSED** | idOrganizacion del payload es neutralizada por el token. |
| `SEC-F8-18` | `secF8_18_dosEntregasConcurrentesSolamenteUnaGana` | **PASSED** | Concurrencia real en 2 hilos: exactamente 1 gana (200) y 1 conflictúa (409). |
| `SEC-F8-19` | `secF8_19_multiplesPinIncorrectosActivanBloqueo` | **PASSED** | 3 fallos $\to$ intento 4 bloqueado con 429 SECURITY_BLOCKED. |
| `SEC-F8-20` | `secF8_20_pinNuncaApareceEnLogsAplicacion` | **PASSED** | Verificación de sanitización [PROTECTED] y 0 fugas en AUDITORIA_LOG. |

**Total suite:** 20 ejecutados, 20 exitosos, 0 fallos, 0 errores.

---

## 29. Pruebas de Regresión Fases 1–7
Todas ejecutadas contra Oracle XE:
- `Mvp05PaqueteriaParqueaderosTest`: 7 tests, 0 fallos, 0 errores. **PASSED.**
- `Phase1GPaquetesIntegrationTest`: 1 test, 0 fallos, 0 errores. **PASSED.**
- `PropertyDeletionSecurityIntegrationTest`: **PASSED.**
- `VisitAuthorizationSecurityIntegrationTest`: 12 tests, 0 fallos, 0 errores. **PASSED.**
- `WompiContextIsolationSecurityIntegrationTest`: **PASSED.**
- `ResidenteConvivenciaSecurityIntegrationTest`: 9 tests, 0 fallos, 0 errores. **PASSED.**
- `ArrendatarioContratosSecurityIntegrationTest`: 10 tests, 0 fallos, 0 errores. **PASSED.**
- `ModelCLimitsSecurityIntegrationTest`: 12 tests, 0 fallos, 0 errores. **PASSED.**

---

## 30. Base de Datos Oracle XE
- RLS / VPD activo y validado en todas las operaciones.
- DDL aplicado en vivo: columna `INTENTOS_FALLIDOS_PIN` activa en `PAQUETES`.
- `ProductionSchemaInitializer` validado en arranque.

---

## 31. Checkstyle
- Comando: `mvn checkstyle:check`
- Límite permitido: $\le 2720$ violaciones.
- Resultado real: **2712 violaciones**.
- Veredicto: **PASSED.**

---

## 32. ESLint
- Comando: `pnpm run lint`
- Errores: **0 errores**.
- Advertencias: **221 warnings** (límite $\le 222$).
- Veredicto: **PASSED.**

---

## 33. Build Backend
- Comando: `mvn package -DskipTests`
- Artifact generado: `backend-1.0.0-SNAPSHOT.jar`
- Resultado: **BUILD SUCCESS.**

---

## 34. Build Frontend
- Comando: `pnpm run build`
- Vite build time: **11.38s**.
- Resultado: **PASSED.**

---

## 35. Archivos Modificados / Creados en Fase 8
### Backend
- `backend/src/main/java/com/saed/backend/audit/AuditSanitizer.java`
- `backend/src/main/java/com/saed/backend/common/exception/GlobalExceptionHandler.java`
- `backend/src/main/java/com/saed/backend/config/ProductionSchemaInitializer.java`
- `backend/src/main/java/com/saed/backend/paquetes/controller/PaquetesController.java`
- `backend/src/main/java/com/saed/backend/paquetes/repository/PaquetesRepository.java`
- `backend/src/main/java/com/saed/backend/paquetes/repository/impl/PaquetesRepositoryImpl.java`
- `backend/src/main/java/com/saed/backend/paquetes/service/PaquetesService.java`
- `backend/src/main/java/com/saed/backend/paquetes/service/impl/PaquetesServiceImpl.java`
- `backend/src/test/java/com/saed/backend/demo/Mvp05PaqueteriaParqueaderosTest.java`
- `backend/src/test/java/com/saed/backend/security/PackageDeliverySecurityIntegrationTest.java` *(nuevo)*

### Base de Datos
- `database/migrations/V5.12__paquetes_intentos_pin.sql` *(nuevo)*

### Frontend
- `frontend/src/pages/PaquetesPage.jsx`

---

## 36. Migraciones Nuevas
- `V5.12__paquetes_intentos_pin.sql`: Adición segura de columna `INTENTOS_FALLIDOS_PIN` con manejo compatible para tablas bajo RLS.

---

## 37. Riesgos
- Almacenamiento masivo de imágenes en Base64 dentro de la base de datos si no se configura almacenamiento externo (S3/GCS) en despliegues con alta afluencia de paquetería.

---

## 38. Decisiones Pendientes (Pending Product Decisions)
- Mecanismo de desbloqueo administrativo de un paquete bloqueado por 3 intentos fallidos (actualmente requiere intervención de base de datos o administrador de propiedad con rol específico).

---

## 39. Limitaciones
- El envío de notificaciones push móviles/SMS requiere configuración de pasarelas externas (Twilio/Firebase FCM); la notificación `IN_APP` se encuentra 100% funcional y persistida en Oracle.

---

## 40. Veredicto Final
# **VERIFIED**
La Fase 8 cumple cabalmente con todos los criterios de seguridad, aislamiento multi-tenant, generación criptográfica de PIN, protección de imágenes, atomicidad concurrente, protección anti-brute-force y calidad técnica establecidos en las directrices de SAED 2.0.
