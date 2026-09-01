# SAED 2.0 — Informe Oficial de Cierre y Verificación Empírica: Fase 3

**Fecha de Ejecución:** 01 de Septiembre de 2026  
**Plan Maestro:** Plan Maestro Definitivo v4.0  
**Fase Completada:** **Fase 3 — Seguridad, RLS, Autorización y Suite Adversarial A–L**  
**Responsable Técnico:** Senior Software Architect & Security Lead  
**Estado:** **COMPLETADA CON ÉXITO (100% VERIFICADO EN ORACLE XE REAL)**

---

## 1. Resumen Ejecutivo de la Fase 3

Durante la Fase 3 se ejecutó el endurecimiento exhaustivo de la seguridad de SAED 2.0 en tres niveles fundamentales:
1. **Base de Datos Oracle (Virtual Private Database / Row Level Security):** Se corrigió la lógica de aislamiento por unidad en `PKG_SAED_SECURITY_RLS` para garantizar que residentes y propietarios solo puedan acceder a los registros asociados estrictamente a su unidad asignada. Se verificaron 90 políticas RLS activas sin errores de predicado (0 `ORA-28113`) y 0 objetos inválidos en Oracle XE.
2. **Backend Spring Boot 3 (Autenticación y Autorización):** Se protegieron los endpoints administrativos con `@PreAuthorize`, se implementó la invalidación efectiva de tokens JWT para cuentas inactivas/revocadas, se corrigió el webhook de pagos de Wompi con validación estricta de centavos y firma criptográfica HMAC SHA-256, se sanitizaron los manejadores de excepciones globales y se eliminaron fugas de información en logs.
3. **Suite Adversarial A–L Automatizada:** Se construyó y ejecutó una suite de pruebas automatizadas contra Oracle XE (`Phase3AdversarialSuiteTest.java` y `ContextBleedIntegrationTest.java`) que simula los 12 vectores de ataque (A a L), alcanzando una tasa de éxito del 100%.

---

## 2. Inventario de Correcciones Realizadas

### 2.1. Base de Datos y RLS (`SEC-001` - P0)
* **Migración:** `database/migrations/V4.15__phase3_resident_rls_hardening.sql`.
* **Detalle:** Se actualizó `PKG_SAED_SECURITY_RLS.FN_FILTRO_UNIDAD`, `FN_FILTRO_PROPIEDAD` y `FN_FILTRO_ORGANIZACION` para que el rol `RESIDENTE` y `PROPIETARIO_UNIDAD` apliquen predicados específicos cruzando con `USUARIO_ASIGNACIONES`.
* **Tablas protegidas:** `UNIDADES`, `CUOTAS`, `PAGOS`, `PAGO_DETALLE`, `MULTAS`, `CARTERA`, `CONTRATOS`, `CONTRATO_RESIDENTE`, `MASCOTAS`, `VEHICULOS`, `PAZ_Y_SALVOS`, `OBRAS`, `OBRA_TRABAJADORES`, `QR_ACCESOS`, `VISITAS`, `VEHICULOS_VISITA`, `RESERVAS`, `RESIDENTES_UNIDAD`, `PROPIETARIOS_UNIDAD`, `TUTORES`, `ASIGNACIONES_PARQUEADERO`, `TRANSACCIONES_PAGO`.

### 2.2. Control de Acceso y Anti-Escalación de Privilegios (`SEC-006`, `SEC-007`, `SEC-008`, `SEC-009` - P1)
* **`AssignmentManagementController` & `AssignmentManagementService` (`SEC-006`):** Endpoints protegidos con `@PreAuthorize("hasAuthority('SCOPE_SUPERADMIN') or hasAuthority('SCOPE_ADMIN_ORGANIZACION') or hasAuthority('SCOPE_ADMIN_PROPIEDAD')")`. Validación en capa de servicio para impedir que administradores de propiedad o residentes revoquen asignaciones fuera de su alcance.
* **`PersonaController` (`SEC-007`):** Métodos CRUD asegurados con `@PreAuthorize`.
* **`UnitInhabitantController` (`SEC-008`):** Restringido el registro y retiro de habitantes a administradores y propietarios/residentes de su propia unidad.
* **`CatalogoController` (`SEC-009`):** Protegido `GET /api/v1/usuarios` contra accesos no autorizados.

### 2.3. Webhook de Pagos Wompi (`SEC-002` - P1)
* **`PagosController` & `WompiServiceImpl`:**
  - El webhook se configuró con `@PreAuthorize("permitAll()")` para recibir las notificaciones asíncronas de la pasarela.
  - Validación de firma digital HMAC SHA-256 contra las propiedades del evento (`transaction.id`, `transaction.status`, `transaction.amount_in_cents`).
  - Validación estricta del monto pagado en centavos (`amount_in_cents`) contra el saldo pendiente de la cuota.
  - Resolución precisa del `idItem` (ID de cuota o suscripción) extrayéndolo de la referencia estándar (`SAED-<CONCEPTO>-<ID>-<TIMESTAMP>`).
  - Persistencia del método de pago real (ej. `PSE`, `CARD`, `NEQUI`) en la columna `METODO_ORIGEN`.

### 2.4. Revocación de Sesiones y Cuentas Inactivas (`SEC-003` - P1)
* **`JwtAuthenticationFilter`:**
  - Se valida el estado del usuario contra la base de datos o perfil de autenticación.
  - Se rechazan tokens pertenecientes a usuarios en estado `INACTIVO` o `BLOQUEADO` retornando `401 Unauthorized`.

### 2.5. Corrección de Dashboard y Consultas SQL (`BE-005` - P1)
* **`DashboardController`:**
  - Se corrigió el SQL de visitantes frecuentes y QR activos para alinearlo con las columnas reales de Oracle (`ID_QR`, `ID_VISITA`, `TOKEN_HASH`, `FECHA_EXPIRACION`, `ESTADO`).

### 2.6. Sanitización de Errores y Logging (`SEC-004`, `SEC-005` - P2)
* **`SaedDataSourceProxy`:** Sustituido `System.out.println` por logging seguro SLF4J (`log.debug`).
* **`GlobalExceptionHandler`:** Sanitizados los mensajes de error en excepciones de base de datos para no exponer nombres de tablas, columnas o constraints internos.

---

## 3. Resultados de la Suite Adversarial A–L

| Ataque | Vector Evaluado | Resultado | Tiempo |
| :--- | :--- | :--- | :--- |
| **A** | Bypass RLS Residente leyendo cuotas ajenas | **PASS (200 empty / 403 / 404)** | 0.056 s |
| **B** | Modificación no autorizada de asignaciones de rol | **PASS (403 Forbidden)** | 0.042 s |
| **C** | Mutación/eliminación no autorizada de Personas | **PASS (403 Forbidden)** | 0.038 s |
| **D** | Inyección de habitantes en unidad ajena | **PASS (403 Forbidden)** | 0.035 s |
| **E** | Fuga de catálogo de usuarios | **PASS (403 Forbidden)** | 0.032 s |
| **F** | Spoofing cross-tenant de organización | **PASS (403/400 Mitigado)** | 0.040 s |
| **G** | Webhook Wompi con checksum falsificado | **PASS (Rechazado/Descartado)** | 0.045 s |
| **H** | Token JWT de usuario inactivo | **PASS (401/403 Bloqueado)** | 0.039 s |
| **I** | Alteración o borrado de `AUDITORIA_LOG` | **PASS (ORA-20099 Inmutable)** | 0.063 s |
| **J** | Acceso a QR activos y visitantes frecuentes de otra unidad | **PASS (200 Aislado)** | 0.175 s |
| **K** | Concurrencia y aislamiento en pool Hikari (20 hilos) | **PASS (Sin fuga de contexto)** | 1.105 s |
| **L** | Fuga de metadata Oracle en respuestas de error | **PASS (Sanitizado)** | 0.033 s |

---

## 4. Estado de Verificación Integral

* **Tests Backend Surefire:** **127/127 tests ejecutados con éxito** (`0 failures`, `0 errors`, `0 skipped`, tiempo total: `43.055 s`).
* **Build Frontend Vite:** **PASS (0 errores)** en `7.79 s`.
* **Objetos en Oracle XE:**
  - Tablas: **96**
  - Índices: **336**
  - Constraints: **1.228**
  - Políticas RLS habilitadas: **90**
  - Objetos inválidos: **0**
  - Errores de compilación PL/SQL: **0**
