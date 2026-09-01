# SAED 2.0 — Matriz de Seguridad y Políticas RLS (Oracle Virtual Private Database)

**Fecha de Auditoría y Verificación:** 2026-09-01  
**Entorno de Validación:** Oracle XE 18c / SAED_V39_FINAL_TEST  
**Estado:** 90 Políticas RLS Habilitadas y Activas / 0 Errores de Predicado (0 ORA-28113) / 0 Objetos Inválidos

---

## 1. Resumen Ejecutivo de Arquitectura Zero-Trust

La seguridad multi-tenant de SAED 2.0 está implementada mediante una arquitectura en capas:
1. **Filtro de Autenticación (`JwtAuthenticationFilter`):** Valida la identidad del usuario a través de un JWT firmado con HMAC SHA-256. Resuelve asignaciones activas validadas con `SAED_SEC_MASTER.PKG_AUTH_BOOTSTRAP` (AUTHID DEFINER) y rechaza cuentas inactivas/suspendidas (`SEC-003`).
2. **Proxy de Conexión JDBC (`SaedDataSourceProxy`):** Antes de cada ejecución en el pool de conexiones (HikariCP), inyecta en la sesión de base de datos el contexto físico mediante `PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(userId)` y `PKG_SAED_SESSION.SET_CONTEXT(userId, orgId, propId, roleCode)`. Al finalizar la petición, ejecuta `PKG_SAED_SESSION.CLEAR_CONTEXT()`, previniendo sangrado de contexto entre hilos (`TEST-004`).
3. **Motor VPD / RLS de Oracle (`PKG_SAED_SECURITY_RLS`):** Aplica predicados dinámicos a nivel de fila antes de que cualquier consulta SQL devuelva datos (`SEC-001`).

---

## 2. Matriz de Funciones de Predicado y Entidades Protegidas

| Función de Predicado | Alcance de Seguridad | Entidades Protegidas | Predicado Aplicado para `RESIDENTE` / `PROPIETARIO_UNIDAD` | Predicado Aplicado para `ADMIN_PROPIEDAD` | Predicado Aplicado para `ADMIN_ORGANIZACION` |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `FN_FILTRO_ORGANIZACION` | Aislamiento por Organización | `ORGANIZACIONES`, `PERSONAS` | Acceso a su propia persona, residentes de su unidad y visitantes invitados | `id_organizacion = :org` | `id_organizacion = :org` |
| `FN_FILTRO_PROPIEDAD` | Aislamiento por Propiedad | `PROPIEDADES`, `BLOQUES`, `COMUNICADOS`, `NOTIFICACIONES`, `ENCUESTAS`, `ENCUESTA_OPCIONES`, `PQRS_TICKETS`, `PQRS_TRAZABILIDAD`, `VISITANTES`, `ZONAS_COMUNES` | Restringido a comunicados, encuestas y visitantes de su propiedad/unidad | `id_propiedad = :prop` | `id_propiedad IN (SELECT id_propiedad FROM PROPIEDADES WHERE id_organizacion = :org)` |
| `FN_FILTRO_UNIDAD` | Aislamiento a Nivel de Unidad | `UNIDADES`, `CUOTAS`, `PAGOS`, `PAGO_DETALLE`, `MULTAS`, `CARTERA`, `CONTRATOS`, `CONTRATO_RESIDENTE`, `MASCOTAS`, `VEHICULOS`, `PAZ_Y_SALVOS`, `OBRAS`, `OBRA_TRABAJADORES`, `QR_ACCESOS`, `VISITAS`, `VEHICULOS_VISITA`, `RESERVAS`, `RESIDENTES_UNIDAD`, `PROPIETARIOS_UNIDAD`, `TUTORES`, `ASIGNACIONES_PARQUEADERO`, `TRANSACCIONES_PAGO` | `id_unidad IN (SELECT id_unidad FROM USUARIO_ASIGNACIONES WHERE id_usuario = :usr AND estado = 'ACTIVA')` | `id_unidad IN (SELECT id_unidad FROM UNIDADES WHERE id_propiedad = :prop)` | `id_unidad IN (SELECT id_unidad FROM UNIDADES WHERE id_propiedad IN (SELECT id_propiedad FROM PROPIEDADES WHERE id_organizacion = :org))` |
| `FN_FILTRO_ASIGNACION` | Aislamiento de Asignaciones de Rol | `USUARIO_ASIGNACIONES` | `id_usuario = :usr` | `id_organizacion = :org` | `id_organizacion = :org` |
| `FN_FILTRO_USUARIOS` | Aislamiento de Cuentas de Usuario | `USUARIOS` | `id_usuario = :usr` | `id_usuario IN (SELECT id_usuario FROM USUARIO_ASIGNACIONES WHERE id_organizacion = :org)` | `id_usuario IN (SELECT id_usuario FROM USUARIO_ASIGNACIONES WHERE id_organizacion = :org)` |
| `FN_FILTRO_GLOBAL_READONLY` | Catálogos Maestros Globales | `TIPOS_DOCUMENTO`, `TIPOS_PROPIEDAD`, `TIPOS_UNIDAD`, `ROLES`, `MODULOS`, `PERMISOS` | `1=1` (Lectura libre autenticada) | `1=1` | `1=1` |
| `FN_FILTRO_GLOBAL_MUTATE` | Mutación de Catálogos y Admin Global | `ADMINISTRADORES_SAED`, `AUDITORIA_LOG`, `HISTORIAL_SESIONES` | `1=0` (Solo SUPERADMIN `1=1`) | `1=0` | `1=0` |

---

## 3. Verificación de Seguridad Adversarial A–L

| Código de Ataque | Vector de Ataque | Nivel | Estado Verificado | Evidencia en Tests |
| :--- | :--- | :--- | :--- | :--- |
| **Ataque A** | Bypass RLS Residente leyendo cuotas ajenas (IDOR / `SEC-001`) | P0 | **MITIGADO** | `Phase3AdversarialSuiteTest.testAtaqueA_ResidenteBypassCuotasOtraUnidad` (PASS) |
| **Ataque B** | Escalación de privilegios creando/modificando asignaciones (`SEC-006`) | P1 | **MITIGADO** | `Phase3AdversarialSuiteTest.testAtaqueB_ResidenteModificarAsignaciones` (PASS) |
| **Ataque C** | Explotación no autorizada del CRUD de Personas (`SEC-007`) | P1 | **MITIGADO** | `Phase3AdversarialSuiteTest.testAtaqueC_ResidentePersonaCrud` (PASS) |
| **Ataque D** | Inyección de habitantes o propietarios en unidad ajena (`SEC-008`) | P1 | **MITIGADO** | `Phase3AdversarialSuiteTest.testAtaqueD_ResidenteInyectarHabitantes` (PASS) |
| **Ataque E** | Fuga del catálogo de usuarios o hashes de contraseña (`SEC-009`) | P1 | **MITIGADO** | `Phase3AdversarialSuiteTest.testAtaqueE_ResidenteConsultaCatalogoUsuarios` (PASS) |
| **Ataque F** | Spoofing cross-tenant de organización o propiedad | P1 | **MITIGADO** | `Phase3AdversarialSuiteTest.testAtaqueF_AdminOrgSpoofingOtraOrg` (PASS) |
| **Ataque G** | Webhook de pagos Wompi falsificado o con centavos adulterados (`SEC-002`) | P1 | **MITIGADO** | `Phase3AdversarialSuiteTest.testAtaqueG_WebhookFalsificadoRechazado` (PASS) |
| **Ataque H** | Uso de tokens JWT pertenecientes a usuarios inactivos (`SEC-003`) | P1 | **MITIGADO** | `Phase3AdversarialSuiteTest.testAtaqueH_UsuarioInactivoBloqueado` (PASS) |
| **Ataque I** | Alteración o borrado de registros en `AUDITORIA_LOG` (`ORA-20099`) | P0 | **MITIGADO** | `Phase3AdversarialSuiteTest.testAtaqueI_AuditoriaInmutable` (PASS) |
| **Ataque J** | Fuga de códigos QR activos y visitantes frecuentes entre unidades (`BE-005`) | P1 | **MITIGADO** | `Phase3AdversarialSuiteTest.testAtaqueJ_ResidenteQrActivosAislamiento` (PASS) |
| **Ataque K** | Concurrencia y fuga de contexto en pool Hikari con 20 hilos (`TEST-004`) | P1 | **MITIGADO** | `ContextBleedIntegrationTest` & `Phase3AdversarialSuiteTest.testAtaqueK_ContextBleedConcurrency` (PASS) |
| **Ataque L** | Fuga de metadatos o nombres de constraints Oracle en errores SQL (`SEC-005`) | P2 | **MITIGADO** | `Phase3AdversarialSuiteTest.testAtaqueL_SanitizacionErroresSql` (PASS) |
