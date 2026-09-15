# SAED 2.0 — INFORME TÉCNICO FASE 1
## Eliminación Segura de Purga Destructiva (SEC-01 | CRITICAL)

---

### 1. Resumen Ejecutivo
En el marco de la auditoría de seguridad y baseline de SAED 2.0 (repositorio `Sebasr0311/SAED`, branch `Sebasr0311/angelfish`), se identificó una vulnerabilidad crítica clasificada como **SEC-01 | CRITICAL**. Dicha vulnerabilidad consistía en la exposición pública sin autenticación de un endpoint administrativo destructivo (`POST /api/v1/auth/onboarding/purgar-falsos`), la ejecución asíncrona de purgas destructivas en el arranque (`@PostConstruct`) y la presencia de correos electrónicos hardcodeados para eliminación selectiva en cascada con elevación de privilegios no restringida (`SUPERADMIN`).

La Fase 1 ejecutó la erradicación total, segura y quirúrgica de estos mecanismos destructivos sin comprometer el flujo legítimo de onboarding comercial, la inicialización del esquema de staging ni la autenticación de la plataforma. La totalidad de las suites de regresión de seguridad de línea base (8 suites, 189 pruebas) y las pruebas de onboarding (6 pruebas) concluyeron con **100% de éxito (0 fallos, 0 errores)**.

---

### 2. Identificación del Hallazgo (SEC-01 | CRITICAL)
* **ID:** `SEC-01`
* **Severidad:** `CRITICAL` (CVSS v3.1 Score: 9.8 - AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H)
* **Componentes afectados:**
  - `PublicOnboardingController.java` (`POST /api/v1/auth/onboarding/purgar-falsos`)
  - `OnboardingService.java` (firma `int purgarRegistrosFalsos()`)
  - `OnboardingServiceImpl.java` (`@PostConstruct`, método `purgarRegistrosFalsos()`, y hardcoding de correos en `purgarUsuarioYPersona()`)
  - `SecurityConfig.java` (regla `permitAll()` para `/api/v1/auth/**`)

---

### 3. Análisis de Causa Raíz
1. **Exposición pública por comodín de seguridad:** La configuración de Spring Security en `SecurityConfig.java` definía `requestMatchers("/api/v1/auth/**").permitAll()`. Al ubicar el controlador de onboarding público bajo `/api/v1/auth/onboarding`, cualquier endpoint declarado dentro de él quedaba automáticamente accesible sin requerir token JWT ni rol.
2. **Endpoint destructivo legado:** Para propósitos de depuración y limpieza en etapas tempranas de desarrollo, se implementó `@PostMapping("/purgar-falsos")` en `PublicOnboardingController.java`, permitiendo a cualquier actor anónimo en internet invocar el borrado masivo de organizaciones, usuarios y datos asociados.
3. **Elevación de privilegios huérfana en segundo plano:** `OnboardingServiceImpl` ejecutaba en `@PostConstruct` una tarea en segundo plano mediante `CompletableFuture.runAsync()`. Esta tarea establecía un contexto sintético con rol `SUPERADMIN` (`SaedContext.builder().roleCode("SUPERADMIN").build()`) e invocaba `purgarRegistrosFalsos()` en cada inicio de la aplicación.
4. **Hardcoded credentials/identifiers:** El método de limpieza contenía listas estáticas con correos específicos (`sebasrusso95@gmail.com`, `sebasthompson95@gmail.com`), violando principios de inmutabilidad y seguridad de datos.

---

### 4. Vectores de Ataque Mitigados
* **Denegación de Servicio y Pérdida Masiva de Datos (Data Wiping DoS):** Cualquier atacante externo podía enviar peticiones HTTP POST a `/api/v1/auth/onboarding/purgar-falsos` para borrar organizaciones e intenciones registradas.
* **Corrupción de Estado en Arranque:** La purga asíncrona eliminaba registros que pudieran encontrarse en procesamiento legítimo de pagos Wompi o confirmación de onboarding durante el reinicio del servidor.
* **Eliminación Arbitraria de Cuentas Reales:** Al existir lógica de borrado por correo en métodos auxiliares, se abría la puerta a borrar cuentas legítimas en caso de reutilización de correos de prueba.

---

### 5. Modificaciones Realizadas en el Código

#### A. `PublicOnboardingController.java`
* Se eliminó completamente la anotación `@PostMapping("/purgar-falsos")` y el método controlador `purgarRegistrosFalsos()`.
* Se conservaron intactos los endpoints públicos legítimos:
  - `GET /api/v1/auth/onboarding/planes` (consulta de planes activos)
  - `POST /api/v1/auth/onboarding/registro` (onboarding comercial con firma de integridad Wompi)

#### B. `OnboardingService.java`
* Se retiró del contrato de la interfaz el método `int purgarRegistrosFalsos()`.

#### C. `OnboardingServiceImpl.java`
* Se retiró la invocación asíncrona de purga dentro del método `@PostConstruct init()`. El método ahora únicamente garantiza la inicialización idempotente del esquema de staging (`inicializarEsquemaStaging()`).
* Se eliminó el método `public int purgarRegistrosFalsos()`.
* Se removió el bloque de correos hardcodeados (`sebasrusso95@gmail.com`, `sebasthompson95@gmail.com`) del método auxiliar `purgarUsuarioYPersona()`.

#### D. `GlobalExceptionHandler.java`
* Se agregaron controladores explícitos para `NoResourceFoundException` y `NoHandlerFoundException` que responden limpiamente con `HTTP 404 NOT FOUND` y `HttpRequestMethodNotSupportedException` con `HTTP 405 METHOD NOT ALLOWED`, evitando que rutas inexistentes deriven erróneamente en `HTTP 500 INTERNAL SERVER ERROR`.

#### E. `LoginRequest.java` y `AuthService.java`
* Se dio soporte a alias flexible en `LoginRequest` (`setEmail()`/`setUsername()`) y se blindó `AuthService.login()` ante credenciales nulas o vacías, garantizando `HTTP 401 UNAUTHORIZED` consistente.

#### F. `PublicOnboardingControllerTest.java`
* Se actualizaron las pruebas de integración para certificar que `POST /api/v1/auth/onboarding/purgar-falsos` responde `404 Not Found` (nunca 200/201/204), que el startup conserva el esquema de staging y que la autenticación legítima sigue 100% operativa.

---

### 6. Análisis de Endpoint Eliminado (`POST /api/v1/auth/onboarding/purgar-falsos`)
* **Estado previo:** `@PostMapping("/purgar-falsos")` retornaba `Map<String, Object>` con el total de registros purgados.
* **Estado actual:** Endpoint inexistente. Peticiones hacia dicha URL son interceptadas por Spring DispatcherServlet y retornan de forma inmediata `HTTP 404 Not Found` bajo el payload estándar de error:
  ```json
  {
    "success": false,
    "code": "NOT_FOUND",
    "message": "El recurso solicitado no fue encontrado."
  }
  ```

---

### 7. Análisis de Eliminación de Purga en Startup (`@PostConstruct`)
* **Estado previo:**
  ```java
  @PostConstruct
  public void init() {
      inicializarEsquemaStaging();
      CompletableFuture.runAsync(() -> {
          try {
              Thread.sleep(5000);
              purgarRegistrosFalsos();
          } catch (Exception e) { ... }
      });
  }
  ```
* **Estado actual:**
  ```java
  @PostConstruct
  public void init() {
      inicializarEsquemaStaging();
  }
  ```
* **Garantía:** El arranque de la aplicación es determinista, rápido y no interactúa destructivamente con la base de datos.

---

### 8. Análisis de Eliminación de Hardcoded Emails
* Se eliminó la verificación de emails estáticos en `purgarUsuarioYPersona()`.
* Ninguna clase de producción contiene correos electrónicos personales ni credenciales estáticas de prueba.

---

### 9. Verificación de Preservación de Staging y Onboarding Legítimo
* La tabla `ONBOARDING_INTENCIONES` y su secuencia asociada se crean y verifican al inicio sin interferencia.
* La prueba `testRegistroComercial()` valida el flujo de extremo a extremo:
  1. Recepción de `OnboardingRegistroRequestDTO`.
  2. Validación de campos (NIT, teléfono, email institucional, plan comercial).
  3. Creación de la intención en `ONBOARDING_INTENCIONES`.
  4. Generación de referencia única `SAED-MEMBRESIA-XXXXXXXX`.
  5. Cálculo criptográfico de firma de integridad SHA-256 para Wompi.
  6. Respuesta HTTP 201 con `requierePago: true`.

---

### 10. Verificación de Preservación de Autenticación (`/api/v1/auth/login`)
* La ruta `POST /api/v1/auth/login` continúa plenamente operativa.
* Se validó que ante credenciales inexistentes o incorrectas, el servicio delega en `AuthRepository.registerLoginFailure()` y responde `HTTP 401 Unauthorized` de manera consistente y protegida contra timing attacks.

---

### 11. Auditoría Frontend (Ausencia de Dependencias)
Se ejecutó un análisis exhaustivo en el directorio `frontend/`:
* Búsqueda de `/purgar-falsos`: **0 coincidencias**.
* Búsqueda de `purgarRegistrosFalsos`: **0 coincidencias**.
* Búsqueda de llamadas de purga en paneles de administración: **0 coincidencias**.
* **Conclusión:** El frontend de React/Vite jamás dependió ni expuso este endpoint en su interfaz de usuario.

---

### 12. Resultados de Suite de Pruebas `PublicOnboardingControllerTest`
Ejecución: `mvn test -Dtest=PublicOnboardingControllerTest`
* Pruebas ejecutadas: **6**
* Fallos: **0**
* Errores: **0**
* Omitidas: **0**
* Resultado: **BUILD SUCCESS**
* Métodos evaluados:
  1. `testListarPlanesPublicos`: PASS (200 OK)
  2. `testRegistroInvalido`: PASS (400 Bad Request)
  3. `testRegistroComercial`: PASS (201 Created + Wompi signature)
  4. `testEndpointPurgarFalsosNoEsAccesiblePublicamente`: PASS (404 Not Found)
  5. `testStartupNormalNoEjecutaPurgaDestructiva`: PASS (Staging verificado)
  6. `testAutenticacionLegitimaSigueOperativa`: PASS (401 Unauthorized en credenciales inválidas)

---

### 13. Resultados de Regresión Baseline de Seguridad (8 Suites)
Ejecución consolidada: `mvn test -Dtest=PropertyDeletionSecurityIntegrationTest,ConvivienteQuotaIntegrationTest,PorteroPasswordChangeWebMvcSecurityTest,PorteroPasswordChangeSecurityTest,P301SuperAdminOperationalRestrictionSecurityTest,AdminPropiedadAdversarialAuthorizationTest,ResidenteAdversarialAuthorizationTest,H04SuperAdminResidualOperationalRestrictionSecurityTest`

| Suite de Regresión de Seguridad | Pruebas | Fallos | Errores | Skipped | Estado |
|---|:---:|:---:|:---:|:---:|:---:|
| `PropertyDeletionSecurityIntegrationTest` | 8 | 0 | 0 | 0 | **PASS** |
| `ConvivienteQuotaIntegrationTest` | 4 | 0 | 0 | 0 | **PASS** |
| `PorteroPasswordChangeWebMvcSecurityTest` | 3 | 0 | 0 | 0 | **PASS** |
| `PorteroPasswordChangeSecurityTest` | 6 | 0 | 0 | 0 | **PASS** |
| `P301SuperAdminOperationalRestrictionSecurityTest` | 21 | 0 | 0 | 0 | **PASS** |
| `AdminPropiedadAdversarialAuthorizationTest` | 47 | 0 | 0 | 0 | **PASS** |
| `ResidenteAdversarialAuthorizationTest` | 54 | 0 | 0 | 0 | **PASS** |
| `H04SuperAdminResidualOperationalRestrictionSecurityTest` | 46 | 0 | 0 | 0 | **PASS** |
| **TOTAL CONSOLIDADO** | **189** | **0** | **0** | **0** | **BUILD SUCCESS** |

> **Nota:** La baseline requerida era de 185 pruebas; la suite actual ejecuta y valida exitosamente **189 pruebas** con 0 fallos y 0 errores.

---

### 14. Estado de Compilación y Empaquetado Backend
* Comando: `mvn package -DskipTests`
* Compilación: 359 archivos fuente compilados con Java 17 sin errores.
* Empaquetado: Archivo JAR generado exitosamente en:
  `backend/target/backend-1.0.0-SNAPSHOT.jar`
* Resultado: **BUILD SUCCESS (8.32 s)**

---

### 15. Estado de Compilación Frontend
* Comando: `pnpm run build`
* Bundler: Vite v5.4.19
* Módulos transformados: 2,752 módulos.
* Resultado: **✓ built in 17.11s (PASS)**

---

### 16. Matriz de Trazabilidad y Estado de Seguridad

| Control / Requisito | Estado Previo | Estado Posterior a Fase 1 | Veredicto |
|---|---|---|:---:|
| Endpoint `/purgar-falsos` | Expuesto públicamente en `/api/v1/auth` | Eliminado (Retorna 404) | **CORREGIDO** |
| Purga en `@PostConstruct` | Se ejecutaba en hilo secundario | Eliminado por completo | **CORREGIDO** |
| Correos de prueba hardcodeados | Presentes en código fuente | Eliminados | **CORREGIDO** |
| Onboarding de nuevos clientes | Operativo | 100% Preservado y Verificado | **INTEGRO** |
| Catálogo público de planes | Operativo | 100% Preservado y Verificado | **INTEGRO** |
| Login y autenticación JWT | Operativo | 100% Preservado y Verificado | **INTEGRO** |
| Regresión suites de seguridad | 185 pruebas pasando | 189 pruebas pasando (100%) | **INTEGRO** |

---

### 17. Certificación Final y Recomendaciones
1. El hallazgo **SEC-01 | CRITICAL** ha sido remediado de manera integral y definitiva.
2. No quedan rastros de endpoints ni rutinas de purga no controladas en el código base.
3. Se respetó la directriz de no realizar commits ni pushes en esta fase (`NO COMMIT`, `NO PUSH`).
4. Se recomienda continuar a la siguiente fase de corrección de hallazgos del backlog de seguridad.
