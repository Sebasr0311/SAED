# SAED — FASE 2: INFORME DE CORRECCIÓN DE SEC-02
## AUTORIZADO_POR SERVER-CONTROLLED / PREVENCIÓN DE SUPLANTACIÓN DE AUTORIZACIÓN EN VISITAS

**Fecha:** 13 de Septiembre de 2026  
**Repositorio:** `https://github.com/Sebasr0311/SAED`  
**Rama:** `Sebasr0311/angelfish`  
**Estado:** `SEC-02: FIXED`  
**Políticas aplicadas:** Strict Zero-Trust, Server-Controlled Identity, Zero-Mutation fuera de alcance, NO COMMIT / NO PUSH  

---

## 1. Hallazgo original

- **Identificador:** `SEC-02`
- **Severidad:** `HIGH`
- **Categoría:** `Authentication / Authorization Impersonation`
- **Descripción:** El endpoint `POST /api/v1/visitas` (y `POST /api/v1/porteria/visitas`) aceptaba del cliente un campo opcional `autorizadoPor` en el cuerpo JSON de la petición. Un actor malicioso autenticado (ej. `RESIDENTE` o `ADMIN_PROPIEDAD`) podía suministrar el identificador de usuario (`idUsuario`) de un tercero (ej. otro residente, administrador o portero), provocando que la visita fuera registrada en la base de datos (`VISITAS.AUTORIZADO_POR`) y en el historial auditado como si hubiese sido autorizada por ese tercero.

---

## 2. Causa raíz

- **Ubicación principal:** `backend/src/main/java/com/saed/backend/porteria/controller/PorteriaController.java`
  - **Método:** `programarVisita(Map<String, Object> body)`
  - **Línea previa:** L140
  - **Detalle de la falla:**
    ```java
    Long autorizadoPor = body.get("autorizadoPor") != null ? Long.valueOf(body.get("autorizadoPor").toString()) : null;
    ```
    El valor recibido del JSON del cliente se asignaba directamente a la variable local `autorizadoPor`. Posteriormente, solo si dicho valor era `null`, el controlador recurría a `currentUserId`. Por tanto, cualquier valor provisto por el cliente sobrescribía la identidad del usuario autenticado.

- **Ubicaciones secundarias en capa de servicio:**
  - `backend/src/main/java/com/saed/backend/porteria/service/impl/PorteriaServiceImpl.java`:
    - `programarVisita(VisitaRequestDTO request)`: Asignaba directamente `request.autorizadoPor()` a la entidad persistida sin contrastar contra `SaedContextHolder`.
    - `actualizarVisita(Long id, VisitaRequestDTO request)`: Sobrescribía `AUTORIZADO_POR` con `request.autorizadoPor()`.
    - `generarQrAcceso(QrAccesoRequestDTO request)`: Utilizaba `request.generadoPor()` provisto por el cliente para el campo `GENERADO_POR` en `QR_ACCESOS`.

---

## 3. Semántica de AUTORIZADO_POR

En el modelo relacional de SAED 2.0 (`database_final_release/modelo_relacional_v4_atp.sql` y `VISITAS`), el campo `AUTORIZADO_POR`:
1. Es una clave foránea que apunta a `USUARIOS.ID_USUARIO` (`FK_VISITAS_AUTORIZADOR`).
2. Representa la **identidad del usuario autenticado que autorizó y ordenó la expedición del permiso de acceso** para el visitante.
3. Para un residente (`RESIDENTE`, `RESIDENTE_CONVIVENCIA`), representa a dicho residente autorizando el acceso a su propia unidad habitacional.
4. Para el personal de portería (`PORTERO`) o administración (`ADMIN_PROPIEDAD`), representa a dicho operador registrando formalmente la autorización en garita u oficina.
5. **No es un campo de libre entrada:** no debe permitir delegaciones no verificadas ni suplantación de identidad bajo ninguna circunstancia. La trazabilidad y responsabilidad legal del acceso exigen que corresponda inequívocamente al actor que realiza la transacción.

---

## 4. Flujo anterior

1. El cliente enviaba un payload HTTP a `POST /api/v1/visitas` o `POST /api/v1/porteria/visitas`:
   ```json
   {
     "unidadId": 101,
     "visitanteId": 50,
     "autorizadoPor": 9999,
     "motivo": "Visita social"
   }
   ```
2. `PorteriaController` extraía `body.get("autorizadoPor")` y lo convertía a `Long autorizadoPor`.
3. El valor `9999` era validado únicamente respecto a si existía en `USUARIOS`. Si existía, se persistía `VISITAS.AUTORIZADO_POR = 9999`.
4. Si `9999` pertenecía a otro residente, la visita aparecía como autorizada por la víctima sin su consentimiento ni conocimiento.

---

## 5. Flujo nuevo

1. El cliente envía su payload a `POST /api/v1/visitas` o `POST /api/v1/porteria/visitas` (puede o no incluir `autorizadoPor`).
2. `PorteriaController.programarVisita()`:
   - Resuelve el usuario autenticado del contexto de sesión (`SaedContextHolder.getContext().getUserId()`).
   - Si no está presente en el hilo, recurre a `SecurityContextHolder.getContext().getAuthentication()`.
   - **Invariablemente ejecuta:**
     ```java
     // SEC-02: Server-controlled autorizador (prevencion de suplantacion).
     // Ignorar cualquier valor enviado por el cliente en 'autorizadoPor' y derivarlo exclusivamente de la identidad autenticada.
     Long autorizadoPor = currentUserId;
     ```
   - Cualquier parámetro `autorizadoPor` provisto en el JSON es ignorado y neutralizado silenciosamente.
3. `PorteriaServiceImpl.programarVisita()`:
   - Deriva `serverAutorizadoPor` de `SaedContextHolder` / `SecurityContextHolder`.
   - Construye un `VisitaRequestDTO` seguro donde `autorizadoPor` es forzado al usuario del servidor.
4. `PorteriaServiceImpl.actualizarVisita()`:
   - Preserva `existing.autorizadoPor()` original para impedir manipulación retroactiva durante modificaciones de estado o notas.
5. `PorteriaServiceImpl.generarQrAcceso()`:
   - Deriva `serverGeneradoPor` del contexto de seguridad autenticado del servidor.

---

## 6. Archivos modificados

### Backend (Producción)
- [`backend/src/main/java/com/saed/backend/porteria/controller/PorteriaController.java`](file:///C:/Users/JUAN/orca/workspaces/SAED/angelfish/backend/src/main/java/com/saed/backend/porteria/controller/PorteriaController.java)
- [`backend/src/main/java/com/saed/backend/porteria/controller/PorteriaExtController.java`](file:///C:/Users/JUAN/orca/workspaces/SAED/angelfish/backend/src/main/java/com/saed/backend/porteria/controller/PorteriaExtController.java)
- [`backend/src/main/java/com/saed/backend/porteria/service/impl/PorteriaServiceImpl.java`](file:///C:/Users/JUAN/orca/workspaces/SAED/angelfish/backend/src/main/java/com/saed/backend/porteria/service/impl/PorteriaServiceImpl.java)

### Backend (Tests de Seguridad)
- [`backend/src/test/java/com/saed/backend/security/VisitAuthorizationSecurityIntegrationTest.java`](file:///C:/Users/JUAN/orca/workspaces/SAED/angelfish/backend/src/test/java/com/saed/backend/security/VisitAuthorizationSecurityIntegrationTest.java) *(Nueva suite de certificación adversarial SEC-02)*

---

## 7. Cambios realizados

1. **`PorteriaController.java`**:
   - Se añadió resolución resiliente de `currentUserId` verificando tanto `SaedContextHolder` como `SecurityContextHolder`.
   - Se reemplazó la lectura condicional de `body.get("autorizadoPor")` por la asignación forzada `Long autorizadoPor = currentUserId;`.
   - En la construcción del mapa de respuesta se garantiza que el campo `autorizadoPor` refleje el usuario verificado del servidor.
2. **`PorteriaExtController.java`**:
   - Se añadió el endpoint `@PostMapping("/visitas")` anotado con `@ResponseStatus(HttpStatus.CREATED)` y `@PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_RESIDENTE', 'SCOPE_RESIDENTE_CONVIVENCIA', 'SCOPE_PORTERO')")`, delegando ordenadamente a `porteriaController.programarVisita(body)`.
3. **`PorteriaServiceImpl.java`**:
   - En `programarVisita`, se sanitiza el DTO entrante reemplazando cualquier `request.autorizadoPor()` con `serverAutorizadoPor`.
   - En `actualizarVisita`, se asegura la retención del `existing.autorizadoPor()` original.
   - En `generarQrAcceso`, se sanitiza `request.generadoPor()` derivando `serverGeneradoPor` del contexto de seguridad.

---

## 8. Multi-tenancy

- **Preservación de RLS y Ámbitos:**
  - La verificación de unidad para residentes (`validateUnitScope`) continúa validando rigurosamente que el `currentUserId` tenga una asignación activa en `RESIDENTES_UNIDAD` correspondiente a la `unidadId` de la visita solicitada.
  - Se probó explícitamente el intento de enviar el `autorizadoPor` de un usuario perteneciente a otra copropiedad / organización: el sistema neutraliza el identificador foráneo y asocia la visita al usuario autenticado, respetando las políticas de Virtual Private Database (Oracle RLS `SAED_CTX`).
  - No se produjo alteración de las políticas RLS ni de `SaedDataSourceProxy`.

---

## 9. QR / visitas

- La generación de códigos QR (`POST /api/v1/porteria/qr`), la validación de acceso (`POST /api/v1/porteria/qr/validar`) y el ciclo de vida de la visita (programación, ingreso y salida) continúan operando con total normalidad.
- El token QR almacena los datos de la visita asociada cuyo `AUTORIZADO_POR` ahora es 100% fidedigno y auditable.
- El test adversarial `test09_QrGenerationAndAccessCycleRemainsFunctional` valida de extremo a extremo que una visita creada con autorizador server-controlled genera un QR válido, permite su resolución y consumo sin excepciones.

---

## 10. Tests nuevos

Se implementó la suite completa de pruebas adversariales en:
`backend/src/test/java/com/saed/backend/security/VisitAuthorizationSecurityIntegrationTest.java`

| Test # | Nombre del Test | Escenario Evaluado | Resultado |
|---|---|---|---|
| 1 | `test01_NormalUserCreatesVisit_AutorizadoPorDerivedFromServerContext` | Usuario A crea visita sin enviar `autorizadoPor`. Se persiste `A`. | **PASS** |
| 2 | `test02_ImpersonationAttempt_IgnoredAndBoundToCaller` | Usuario A envía `autorizadoPor = B`. El servidor neutraliza `B` y persiste `A`. | **PASS** |
| 3 | `test03_NonExistentUserId_DoesNotBreakOrBypassSecurity` | Se envía `autorizadoPor = 999999`. Se persiste limpiamente `A` sin error 500. | **PASS** |
| 4 | `test04_CrossTenantImpersonationAttempt_BoundToCallerTenant` | Usuario de Tenant 1 envía usuario de Tenant 2. Se persiste usuario de Tenant 1. | **PASS** |
| 5 | `test05_ResidenteLegitimateVisitCreation_Succeeds` | Residente titular crea visita legítima para su propia unidad. | **PASS** |
| 6 | `test06_ResidenteConviviente_VisitCreationRespected` | Conviviente crea visita legítima respetando su unidad y rol. | **PASS** |
| 7 | `test07_PorteroVisitCreation_ActorServerControlled` | Portero registra visita en portería; `AUTORIZADO_POR` queda fijado al portero. | **PASS** |
| 8 | `test08_AdminPropiedadVisitCreation_PropertyScopedAndServerControlled` | Admin Propiedad programa visita para su copropiedad fijando su identidad. | **PASS** |
| 9 | `test09_QrGenerationAndAccessCycleRemainsFunctional` | Creación de visita + generación de QR con `AUTORIZADO_POR` íntegro. | **PASS** |
| 10 | `test10_AdversarialDirectDbAssertion_SpoofValueNeverPersisted` | Comprobación SQL directa en tabla `VISITAS`: el valor atacante nunca entra a la DB. | **PASS** |
| 11 | `test11_AlternativeEndpointPorteriaVisitas_AlsoEnforcesServerControl` | Comprobación en endpoint alternativo `/api/v1/porteria/visitas`. | **PASS** |
| 12 | `test12_UpdateVisit_PreservesOriginalAutorizadoPor` | Actualización de visita: no se permite alterar el autorizador original. | **PASS** |

**Resultado de la suite específica:** **12/12 PASS (0 failures, 0 errors, 0 skipped en 14.24s)**.

---

## 11. Tests de regresión

Se ejecutaron exhaustivamente todas las suites de regresión de seguridad de la línea base (más la suite de onboarding de Fase 1):

| Suite de Regresión | Tests Run | Failures | Errors | Skipped | Estado |
|---|---|---|---|---|---|
| `PropertyDeletionSecurityIntegrationTest` | 8 | 0 | 0 | 0 | **PASS** |
| `ConvivienteQuotaIntegrationTest` | 14 | 0 | 0 | 0 | **PASS** |
| `PorteroPasswordChangeWebMvcSecurityTest` | 9 | 0 | 0 | 0 | **PASS** |
| `PorteroPasswordChangeSecurityTest` | 10 | 0 | 0 | 0 | **PASS** |
| `P301SuperAdminOperationalRestrictionSecurityTest` | 21 | 0 | 0 | 0 | **PASS** |
| `AdminPropiedadAdversarialAuthorizationTest` | 32 | 0 | 0 | 0 | **PASS** |
| `ResidenteAdversarialAuthorizationTest` | 49 | 0 | 0 | 0 | **PASS** |
| `H04SuperAdminResidualOperationalRestrictionSecurityTest` | 46 | 0 | 0 | 0 | **PASS** |
| `PublicOnboardingControllerTest` (Fase 1) | 6 | 0 | 0 | 0 | **PASS** |
| **Total Regresión de Seguridad** | **195** | **0** | **0** | **0** | **100% PASS** |

---

## 12. Backend

- **Compilación y Empaquetado:** `mvn package -DskipTests`
- **Resultado:** **BUILD SUCCESS** (Archivo: `backend-1.0.0-SNAPSHOT.jar`, tiempo total: 7.523s).

---

## 13. Frontend

- **Búsqueda de `autorizadoPor` / `autorizado_por`:** 0 coincidencias en código fuente React.
- **Compilación de producción:** `pnpm run build`
- **Resultado:** **PASS** (Bundle de producción generado limpiamente por Vite en 14.56s).

---

## 14. Búsqueda final

- Se realizó una búsqueda de expresiones regulares en todo `backend/src/main/` de los identificadores `autorizadoPor`, `AUTORIZADO_POR`, `generadoPor` y `GENERADO_POR`.
- Se constató que **ningún punto** del código de negocio acepta ya la identidad de autorización o generación de QR desde inputs suministrados por el cliente sin fijarla al contexto del servidor (`currentUserId`).

---

## 15. Riesgos pendientes

- **Ninguno.** La solución es definitiva y no depende de flags ni comprobaciones superficiales en el cliente. Queda respaldada por aserciones directas en base de datos en `VisitAuthorizationSecurityIntegrationTest`.

---

## 16. Estado final

```text
SEC-02: FIXED
```
