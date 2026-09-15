# FASE 4 — AUDITORÍA ORACLE Y BASELINE V5.0

**Fecha de Ejecución:** 2026-09-01  
**Esquema de Validación y Portabilidad:** `SAED_BASELINE_TEST_01` (Esquema completamente nuevo y aislado)  
**Esquema Maestro de Seguridad:** `SAED_SEC_MASTER`  
**Motor de Base de Datos:** Oracle Database 21c Express Edition Release 21.0.0.0.0 (XEPDB1)  
**Backend:** Spring Boot 3.2.3 (Java 24 / Target 17)  
**Frontend:** React 18.3.1 + Vite 5.4.21 + TailwindCSS  

---

## 1. Problemas Encontrados

Durante la reconstrucción del esquema desde cero utilizando migraciones versionadas y scripts consolidados, se identificaron cuatro (4) hallazgos arquitectónicos fundamentales:

1. **Interrupción Prematura por Comandos de Cliente SQL\*Plus (`EXIT;` / `WHENEVER`):**
   Archivos heredados (como `V4.13__seed_planes.sql`) contenían sentencias `EXIT;` al final del archivo. Al concatenar los artefactos en un baseline monolítico, el cliente SQL\*Plus desconectaba la sesión antes de compilar los paquetes PL/SQL (`PKG_SAED_SESSION`, `PKG_SAED_SECURITY_RLS`), omitiendo el registro de las 90 políticas RLS.
2. **Esquema Hardcodeado en Paquetes de Seguridad (`SAED_V39_FINAL_TEST`):**
   El paquete `SAED_SEC_MASTER.PKG_AUTH_BOOTSTRAP` contenía consultas estáticas con prefijo de esquema (`SAED_V39_FINAL_TEST.USUARIOS`, `SAED_V39_FINAL_TEST.PERSONAS`, etc.), generando error `ORA-00942 (tabla o vista no existe)` al intentar instalar el sistema en un esquema con nombre diferente.
3. **Conflicto de Enlace en Contexto Global (`SAED_CTX` / `ORA-01031`):**
   El namespace de contexto de seguridad de base de datos (`CREATE CONTEXT SAED_CTX USING ... ACCESSED GLOBALLY`) quedaba atado al paquete del esquema que lo creó originalmente. Si la aplicación intentaba operar contra un esquema nuevo mientras el contexto pertenecía a otro paquete, Oracle bloqueaba la mutación con `ORA-01031 (privilegios insuficientes)`.
4. **Dependencias e Incompletitud en Semillas de Identidad y Catálogos (`ORA-20082`, `ORA-01400`, `ORA-20010`):**
   Una instalación limpia carecía de semillas mínimas para operar (`admin_global`, catálogos base, organización y propiedad inicial). Al incorporar las semillas surgieron violaciones de constraints:
   - `ORA-01400`: `PROPIEDADES.DIRECCION` es `NOT NULL` y venía omitido en el seed.
   - `ORA-20010`: El disparador de integridad `TRG_ASIGNACION_VALIDA_SCOPE` bloqueaba asignaciones con rol `SUPERADMIN` (alcance `GLOBAL`) que tuvieran `ID_ORGANIZACION` o `ID_PROPIEDAD` no nulos.

---

## 2. Causa Raíz de Cada Problema

| Hallazgo | Causa Raíz Técnica |
| :--- | :--- |
| **1. Comandos de Cliente** | Mezcla de scripts diseñados para ejecución interactiva manual con scripts DDL/DML destinados a automatización CI/CD y despliegue unificado. |
| **2. Esquema Hardcodeado** | Ausencia de capa de abstracción mediante sinónimos dinámicos en el esquema definidor `SAED_SEC_MASTER` (`AUTHID DEFINER`), provocando acoplamiento rígido a un nombre de esquema temporal de desarrollo. |
| **3. Contexto Global SAED_CTX** | La arquitectura de Oracle VPD para contextos globales exige que solo el *trusted package* registrado pueda llamar a `DBMS_SESSION.SET_CONTEXT`. Si el contexto no se vincula explícitamente al esquema actual (`USER.PKG_SAED_SESSION`), el contexto queda huérfano o inaccesible para nuevas instancias. |
| **4. Semillas y Triggers** | Incompatibilidad entre los payloads de inicialización y las restricciones de integridad y triggers de negocio (`NOT NULL` en `PROPIEDADES.DIRECCION` y regla de alcance en `TRG_ASIGNACION_VALIDA_SCOPE`). |

---

## 3. Solución Arquitectónica Aplicada

1. **Sanitización Integral de Comandos Cliente:**
   - Se purgó todo comando `EXIT;`, `QUIT;`, `CONNECT`, `HOST`, `DEFINE` intermedio del DDL unificado.
   - Se estableció un único punto de cierre y control transaccional al final del archivo maestro `V5.0__master_baseline.sql`.
2. **Abstracción Dinámica de Esquema con Sinónimos en `SAED_SEC_MASTER`:**
   - Se implementó un bloque PL/SQL dinámico en el baseline que genera sinónimos privados dentro de `SAED_SEC_MASTER` apuntando al esquema activo de la sesión:
     ```sql
     EXECUTE IMMEDIATE 'CREATE OR REPLACE SYNONYM SAED_SEC_MASTER.USUARIOS FOR ' || USER || '.USUARIOS';
     EXECUTE IMMEDIATE 'CREATE OR REPLACE SYNONYM SAED_SEC_MASTER.PERSONAS FOR ' || USER || '.PERSONAS';
     EXECUTE IMMEDIATE 'CREATE OR REPLACE SYNONYM SAED_SEC_MASTER.USUARIO_ASIGNACIONES FOR ' || USER || '.USUARIO_ASIGNACIONES';
     EXECUTE IMMEDIATE 'CREATE OR REPLACE SYNONYM SAED_SEC_MASTER.ROLES FOR ' || USER || '.ROLES';
     EXECUTE IMMEDIATE 'CREATE OR REPLACE SYNONYM SAED_SEC_MASTER.AUDITORIA_LOG FOR ' || USER || '.AUDITORIA_LOG';
     ```
   - Las consultas dentro de `PKG_AUTH_BOOTSTRAP` se normalizaron para consumir los sinónimos sin prefijos de esquema fijos.
3. **Enlace Explícito y Dinámico del Contexto Global:**
   - La creación del contexto en el baseline ahora califica unívocamente el *trusted package* con el esquema en ejecución:
     ```sql
     EXECUTE IMMEDIATE 'CREATE OR REPLACE CONTEXT SAED_CTX USING ' || USER || '.PKG_SAED_SESSION ACCESSED GLOBALLY';
     ```
4. **Normalización Rigurosa de Semillas Maestras:**
   - Se incorporó la totalidad de catálogos requeridos (`TIPOS_DOCUMENTO`, `TIPOS_PROPIEDAD`, `TIPOS_UNIDAD`, `ROLES`, `PLANES`).
   - Se proveyeron valores válidos y completos para campos obligatorios (`PROPIEDADES.DIRECCION = 'CALLE 100 # 15-20'`).
   - Se respetó la regla del trigger `TRG_ASIGNACION_VALIDA_SCOPE`, seteando `ID_ORGANIZACION = NULL`, `ID_PROPIEDAD = NULL`, `ID_UNIDAD = NULL` para la asignación con rol `SUPERADMIN` (alcance `GLOBAL`).
   - Se ordenó el script para que tablas, constraints, paquetes y semillas se creen antes de registrar las 90 políticas RLS.

---

## 4. Cambios Realizados

- **`database/migrations/V5.0__master_baseline.sql`:** Archivo monolítico unificado de baseline V5.0 (324 KB, 5.022 líneas) que contiene:
  1. Estructuras DDL (96 tablas, 1 vista).
  2. Constraints e Índices (338 índices, 1.219 constraints).
  3. Triggers de auditoría e integridad de alcance (9 triggers).
  4. Semillas de catálogos, planes e identidad inicial (`admin_global`, Org 1, Prop 1).
  5. Paquetes de sesión y RLS (`PKG_SAED_SESSION`, `PKG_SAED_SECURITY_RLS`).
  6. Registro declarativo de las 90 políticas de Virtual Private Database (RLS).
  7. Sinónimos y paquete maestro de autenticación (`SAED_SEC_MASTER.PKG_AUTH_BOOTSTRAP`).
- **`backend/src/main/resources/application.yml` & `application-test.yml`:** Parametrizados para conectar dinámicamente al esquema de pruebas provisto (`SAED_BASELINE_TEST_01`).
- **`backend/src/test/java/com/saed/backend/identity/Phase1AAuthIntegrationTest.java`:** Removida referencia residual hardcodeada a `SAED_V39_FINAL_TEST.USUARIOS`.

---

## 5. Evidencia de Reconstrucción Desde Cero

Se ejecutó la creación y aprovisionamiento limpio del esquema `SAED_BASELINE_TEST_01`:

```sql
-- Ejecución SQL*Plus:
sqlplus -s SAED_BASELINE_TEST_01/saed2026@localhost:1521/XEPDB1 @database/migrations/V5.0__master_baseline.sql
-- Resultado: Exit code 0, sin errores de compilación ni fallos DDL.
```

---

## 6. Evidencia de Objetos Oracle (`SAED_BASELINE_TEST_01`)

Consulta ejecutada sobre la base de datos viva:

```sql
SELECT 'TOTAL_TABLES: ' || count(*) FROM user_tables;
-- TOTAL_TABLES: 96

SELECT 'TOTAL_INDEXES: ' || count(*) FROM user_indexes;
-- TOTAL_INDEXES: 338

SELECT 'TOTAL_CONSTRAINTS: ' || count(*) FROM user_constraints;
-- TOTAL_CONSTRAINTS: 1219

SELECT 'TOTAL_TRIGGERS: ' || count(*) FROM user_triggers;
-- TOTAL_TRIGGERS: 9

SELECT 'TOTAL_VIEWS: ' || count(*) FROM user_views;
-- TOTAL_VIEWS: 1

SELECT 'TOTAL_POLICIES: ' || count(*) FROM user_policies;
-- TOTAL_POLICIES: 90

SELECT 'TOTAL_PACKAGES: ' || count(*) FROM user_objects WHERE object_type IN ('PACKAGE', 'PACKAGE BODY');
-- TOTAL_PACKAGES: 4

SELECT 'INVALID_OBJECTS: ' || count(*) FROM user_objects WHERE status != 'VALID';
-- INVALID_OBJECTS: 0

SELECT 'INVALID_OBJECTS_SEC: ' || count(*) FROM all_objects WHERE owner = 'SAED_SEC_MASTER' AND status != 'VALID';
-- INVALID_OBJECTS_SEC: 0
```

---

## 7. Evidencia de RLS

- **Políticas RLS Activas:** 90 políticas registradas mediante `DBMS_RLS.ADD_POLICY`.
- **Funciones de Predicado:**
  - `PKG_SAED_SECURITY_RLS.FN_FILTRO_GLOBAL`
  - `PKG_SAED_SECURITY_RLS.FN_FILTRO_ORGANIZACION`
  - `PKG_SAED_SECURITY_RLS.FN_FILTRO_PROPIEDAD`
  - `PKG_SAED_SECURITY_RLS.FN_FILTRO_RESIDENTE`
  - `PKG_SAED_SECURITY_RLS.FN_FILTRO_GLOBAL_MUTATE`
  - `PKG_SAED_SECURITY_RLS.FN_FILTRO_ORGANIZACION_MUTATE`
  - `PKG_SAED_SECURITY_RLS.FN_FILTRO_PROPIEDAD_MUTATE`
  - `PKG_SAED_SECURITY_RLS.FN_FILTRO_RESIDENTE_MUTATE`
- **Estado en Oracle:** 90/90 ENABLED, 0 políticas inválidas.

---

## 8. Evidencia de `SAED_CTX`

Consulta sobre `DBA_CONTEXT`:

```sql
SELECT schema, package, type FROM dba_context WHERE namespace = 'SAED_CTX';
```

**Resultado:**
- `SCHEMA`: `SAED_BASELINE_TEST_01`
- `PACKAGE`: `PKG_SAED_SESSION`
- `TYPE`: `ACCESSED GLOBALLY`

---

## 9. Evidencia de Seeds

```sql
SELECT 'SEED_USUARIOS: ' || count(*) FROM usuarios;
-- SEED_USUARIOS: 1 (admin_global / ID 1)

SELECT 'SEED_PERSONAS: ' || count(*) FROM personas;
-- SEED_PERSONAS: 1 (Admin Global / ID 1)

SELECT 'SEED_ORGANIZACIONES: ' || count(*) FROM organizaciones;
-- SEED_ORGANIZACIONES: 1 (ORGANIZACION PRINCIPAL SAED / ID 1)

SELECT 'SEED_PROPIEDADES: ' || count(*) FROM propiedades;
-- SEED_PROPIEDADES: 1 (PROPIEDAD PRINCIPAL / ID 1 / CALLE 100 # 15-20)

SELECT 'SEED_ROLES: ' || count(*) FROM roles;
-- SEED_ROLES: 5 (SUPERADMIN, ADMIN_PROPIEDAD, ADMIN_ORGANIZACION, PORTERO, RESIDENTE)

SELECT 'SEED_ASIGNACIONES: ' || count(*) FROM usuario_asignaciones;
-- SEED_ASIGNACIONES: 1 (Asignación ID 1 / SUPERADMIN / Scope GLOBAL)
```

---

## 10. Evidencia de `mvn clean test`

Ejecución de la suite completa de pruebas de integración y seguridad backend contra `SAED_BASELINE_TEST_01`:

```
[INFO] Results:
[INFO] 
[INFO] Tests run: 127, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  41.121 s
```

---

## 11. Evidencia de Suite Adversarial A–L

Pruebas ejecutadas dentro de `Phase3AdversarialSuiteTest`:

| Test Adversarial | Escenario Evaluado | Resultado |
| :--- | :--- | :--- |
| **Adversarial A** | Aislamiento horizontal entre Organizaciones (Org 1 vs Org 2) | **PASS** |
| **Adversarial B** | Aislamiento horizontal entre Propiedades (Prop 10 vs Prop 20) | **PASS** |
| **Adversarial C** | Aislamiento de residente cruzado (Unidad 101 vs Unidad 201) | **PASS** |
| **Adversarial D** | Intento de escalación de privilegios modificando IDs en payload | **PASS** |
| **Adversarial E** | Token JWT válido de usuario bloqueado/inactivo es rechazado | **PASS** |
| **Adversarial F** | Bloqueo automático de cuenta tras 5 intentos fallidos | **PASS** |
| **Adversarial G** | Prevención de fuga de contexto en conexiones reutilizadas HikariCP | **PASS** |
| **Adversarial H** | Bypass de predicados RLS no es posible mediante inyección de parámetros | **PASS** |
| **Adversarial I** | Protección de integridad de Webhook Wompi ante checksum adulterado | **PASS** |
| **Adversarial J** | Protección de mutación financiera (Pagos no asignables a otras unidades) | **PASS** |
| **Adversarial K** | Validación estricta de alcance en asignaciones de usuario | **PASS** |
| **Adversarial L** | Auditoría inmutable de accesos y eventos de seguridad | **PASS** |

---

## 12. Evidencia de `npm run build`

Compilación de producción del cliente React:

```
> saed-frontend@1.0.0 build
> vite build

vite v5.4.21 building for production...
transforming...
✓ 2000 modules transformed.
rendering chunks...
computing gzip size...
✓ built in 10.89s
```

---

## 13. Prueba de Portabilidad con Esquema Diferente

- **Esquema de Prueba:** `SAED_BASELINE_TEST_01` (nombre completamente distinto a cualquier esquema anterior).
- **Proceso:** Creación de usuario limpio -> Ejecución directa de `V5.0__master_baseline.sql` -> Cero comandos manuales posteriores -> Ejecución de suite de tests backend y frontend.
- **Resultado:** Reconstrucción autónoma, portable y reproducible confirmada.

---

## 14. Qué Queda Sin Verificar

- Despliegue en Oracle Autonomous Database (ATP) en la nube (la validación se realizó en Oracle XE local, compatible con la sintaxis de ATP).
- Rendimiento bajo concurrencia masiva (>1.000 requests concurrentes continuos).

---

## 15. Checklist de Gate Fase 4

- [x] `V5.0__master_baseline.sql` se ejecuta completamente desde un esquema vacío.
- [x] No existen comandos `EXIT`, `QUIT` u otros comandos cliente que interrumpan el baseline.
- [x] No existen referencias hardcodeadas a `SAED_V39_FINAL_TEST`.
- [x] `SAED_CTX` funciona correctamente después de una instalación limpia.
- [x] El *trusted package* y los grants del contexto son reproducibles.
- [x] Seeds mínimos de identidad y catálogos están incluidos.
- [x] Seeds respetan todos los constraints reales y triggers de negocio.
- [x] 0 objetos `INVALID`.
- [x] 0 errores en `USER_ERRORS`.
- [x] Todas las 90 políticas RLS esperadas están `ENABLED`.
- [x] Suite adversarial A–L pasa (12/12 PASS).
- [x] `mvn clean test` pasa completamente (127/127 PASS).
- [x] `npm run build` pasa exitosamente.
- [x] Spring Boot funciona contra el esquema recién construido (`SAED_BASELINE_TEST_01`).
- [x] El baseline funciona con un nombre de esquema diferente (demostrado con `SAED_BASELINE_TEST_01`).
- [x] No fue necesario ejecutar SQL manual después del baseline.
- [x] No fue necesario copiar objetos desde otro esquema.
- [x] La reconstrucción completa queda documentada y reproducible.

---

**Estado del Gate:** **LISTO PARA REVISIÓN Y APROBACIÓN DEL USUARIO**
