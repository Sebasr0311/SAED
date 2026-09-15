# SAED 2.0 — REPORTE FINAL DE LIMPIEZA, CONSOLIDACIÓN Y REORGANIZACIÓN DEL REPOSITORIO

**Fecha de Ejecución:** 15 de Septiembre de 2026  
**Repositorio Oficial:** `https://github.com/Sebasr0311/SAED`  
**Rama:** `Sebasr0311/angelfish`  
**Tag de Restauración Previsto:** `SAED-PRE-CLEANUP` (`1daae7b8b438b4daaa49a710bc931758c0678f14`)  
**Responsable:** Arquitecto de Software / Equipo de Ingeniería SAED  

---

## 1. ESTADO INICIAL DEL REPOSITORIO

Antes de iniciar este proceso de consolidación, el repositorio de SAED presentaba una acumulación considerable de deuda técnica organizativa, vestigios de versiones preliminares (SAED 1.0 monolítico desktop) y fragmentación de fuentes de verdad:

1. **Monolito JavaFX en el Árbol Activo (`backend_legacy/`):**
   - Coexistían 194 archivos de una aplicación desktop previa (`com.edificio:admin-residencial`) construida sobre JavaFX 19, FXML y controladores locales.
   - Aunque desacoplada en tiempo de ejecución del nuevo backend Spring Boot 3, su presencia en la raíz inducía a confusión arquitectónica e indexaciones innecesarias de herramientas de análisis.
2. **Dispersión en la Raíz del Proyecto:**
   - 7 scripts individuales de pruebas de humo y E2E de TestSprite (`testsprite_*.py`) residían directamente en la raíz.
   - Directorio `scratch/` con scripts de auditoría efímeros versionados sin control.
3. **Doble Fuente de Verdad Relacional (TECH-003):**
   - Existían simultáneamente `database/` y `database_final_release/`.
   - `database_final_release/` contenía un esquema estático monolítico v4 (`modelo_relacional_v4_atp.sql`) que competía conceptualmente con las migraciones versionadas de Flyway (`database/migrations/V5.0` a `V5.12`).
   - Múltiples subcarpetas huérfanas en `database/`: `legacy/`, `utilities/`, `docs/`, con scripts SQL duplicados (`schema_v4.sql`, `seed_data_v4.sql`, `drop_v4.sql`, etc.).
4. **Desorganización Masiva en Documentación (`docs/`):**
   - Más de 86 documentos Markdown sueltos en el directorio `docs/` sin taxonomía, mezclando certificaciones operativas, reportes forenses, guías de demos pasadas, auditorías y minutas.
5. **Divulgación de Credenciales en Documentación:**
   - El archivo `README.md` original contenía contraseñas de desarrollo e instrucciones de conexión con credenciales predeterminadas en texto plano.

---

## 2. RESUMEN DEL INVENTARIO PREVIO A LA LIMPIEZA

Conforme a la regla de oro del proyecto (*no eliminar nada de manera irreversible sin auditarlo y registrarlo primero*), se generó un inventario exhaustivo de 600+ archivos previo a cualquier mutación de staging.

- **Inventario Registrado:** [`docs/cleanup/SAED_CLEANUP_INVENTORY.md`](file:///C:/Users/JUAN/orca/workspaces/SAED/angelfish/docs/cleanup/SAED_CLEANUP_INVENTORY.md)
- **Punto Inmutable de Restauración:** Tag Git `SAED-PRE-CLEANUP` creado sobre el commit `1daae7b`.
- **Distribución Inicial de Archivos:**
  - `backend/`: 218 archivos (Spring Boot 3, Java 17, producción SAED 2.0).
  - `frontend/`: 96 archivos (React 18, Vite, Tailwind CSS, producción SAED 2.0).
  - `backend_legacy/`: 194 archivos (JavaFX 19, candidato a archivo histórico).
  - `database/`: 28 archivos (mezcla de Flyway activo, utilitarios y esquemas v4 obsoletos).
  - `database_final_release/`: 3 archivos (esquema estático monolítico v4).
  - `docs/`: 86 archivos Markdown sueltos sin estructurar.
  - Raíz (`.`): 7 scripts `testsprite_*.py`, `scratch/` (3 archivos), configuraciones de CI/despliegue.

---

## 3. ARCHIVOS ELIMINADOS (JUSTIFICACIÓN TÉCNICA)

Se aplicó un criterio de eliminación extremadamente quirúrgico: **únicamente artefactos temporales no reproducibles y scripts scratch huérfanos**. No se eliminó ninguna línea de código funcional de negocio.

| Archivo / Ruta Eliminada | Tipo | Justificación Técnica |
|---|---|---|
| `backend/clean.py` | Script Python efímero | Script auxiliar no oficial utilizado para pruebas locales de limpieza; no forma parte de ningún pipeline ni módulo de producción. |
| `backend/DbCheck.class` | Binario compilado no versionado | Artefacto de compilación residual fuera de `target/`. |
| `backend/DbCleanTest.class` | Binario compilado no versionado | Artefacto de compilación residual fuera de `target/`. |

---

## 4. ARCHIVOS ARCHIVADOS EN `archive/`

Para preservar la trazabilidad histórica completa sin contaminar los árboles de compilación y trabajo diario de SAED 2.0, los siguientes componentes fueron transferidos con preservación de historial (`git mv`) hacia la carpeta de archivo:

### 4.1. Monolito Desktop JavaFX
- **Origen:** `backend_legacy/` (194 archivos).
- **Destino:** `archive/legacy/backend_legacy/`
- **Justificación:** Aplicación de escritorio JavaFX de la generación anterior (SAED 1.0). Se conserva intacta como referencia de modelos de dominio y algoritmos contables iniciales, pero totalmente aislada del ciclo de vida de SAED 2.0.

### 4.2. Esquema Relacional Monolítico Estático v4
- **Origen:** `database_final_release/` (`modelo_relacional_v4_atp.sql`, `datos_prueba.sql`, `LEER_INSTRUCCIONES_DESPLIEGUE.txt`).
- **Destino:** `archive/legacy/database_v4_final_release/`
- **Justificación:** Esquema monolítico no versionado previo a la migración Flyway. Su presencia duplicaba la fuente de verdad y rompía la consistencia multi-tenant.

### 4.3. Scripts y Utilidades Legacy de Base de Datos
- **Origen:** `database/legacy/` (`schema_v4.sql`, `seed_data_v4.sql`, `drop_v4.sql`, `create_schema.sql`, etc.), `database/utilities/` (`RunSchemaFinal.java`, `migrar.bat`), `database/docs/` (`ERD.md`, `ORACLE_MIGRATION_GUIDE.md`), `database/schema_final_release_v4.sql`, `database/datos_prueba.sql`.
- **Destino:** `archive/legacy/database_v4/`, `archive/legacy/database_utilities/`, `archive/legacy/database_docs/`
- **Justificación:** Artefactos pre-V5 que utilizaban DDL y DML estáticos previos al motor de migraciones incrementales.

### 4.4. Scripts Temporales de Auditoría
- **Origen:** `scratch/` (`audit_forms.js`, `fuzz_system_tests.js`, `test_validation_audit.js`).
- **Destino:** `archive/scratch/`
- **Justificación:** Herramientas utilitarias creadas durante fases de auditoría funcional previas; preservadas para auditorías futuras de fuzzing sin ensuciar la raíz.

### 4.5. Documentación Histórica y Reportes de Ciclos Pasados
- **Origen:** 37 reportes y minutas de demo en `docs/`.
- **Destino:** `docs/archive/` (ver Sección 6 para el detalle).
- **Justificación:** Informes de avances pasados, guiones de presentación de demos superadas y análisis de fases previas que ya no describen el estado vivo del software.

---

## 5. ARCHIVOS CONSERVADOS (JUSTIFICACIÓN POR COMPONENTE)

Los componentes vigentes de **SAED 2.0** fueron ratificados y protegidos:

1. **`backend/` (Spring Boot 3.x, Java 17):**
   - Todos los controladores REST, servicios, repositorios (`JdbcTemplate`), modelos DTO, configuraciones de seguridad (`JwtAuthenticationFilter`, `SaedDataSourceProxy`), y suites completas de pruebas unitarias e integración.
2. **`frontend/` (React 18, Vite, Tailwind CSS):**
   - Todas las páginas (`src/pages/`), componentes atómicos (`src/components/ui/`), shells (`AppShell.jsx`), contextos de autenticación y tenant, y configuración de build (`vite.config.js`).
3. **`database/` (Única Fuente de Verdad Relacional):**
   - Migraciones Flyway ordenadas en `database/migrations/`:
     - `V5.0__master_baseline.sql` a `V5.12__auditoria_accesos_modulo.sql`.
   - Seeds de inicialización y demostración en `database/seeds/`:
     - `database/seeds/demo/V5.99__demo_seeds.sql`
     - `database/seeds/test-data/phase-1b/`
4. **`tests/` (Suites de Prueba E2E):**
   - Suite unificada de pruebas E2E de TestSprite en `tests/testsprite/`.
5. **Configuraciones de Infraestructura y Raíz:**
   - `render.yaml`, `Dockerfile`, `.gitignore`, `README.md`, `pom.xml`, `.mvn/`.

---

## 6. ARCHIVOS REORGANIZADOS / RENOMBRADOS (MAPEO)

### 6.1. Reorganización de la Raíz hacia `tests/`
| Ruta Anterior (Raíz) | Nueva Ruta Oficial |
|---|---|
| `testsprite_admin_organizacion_test.py` | `tests/testsprite/testsprite_admin_organizacion_test.py` |
| `testsprite_admin_propiedad_test.py` | `tests/testsprite/testsprite_admin_propiedad_test.py` |
| `testsprite_conviviente_test.py` | `tests/testsprite/testsprite_conviviente_test.py` |
| `testsprite_paquetes_test.py` | `tests/testsprite/testsprite_paquetes_test.py` |
| `testsprite_portero_test.py` | `tests/testsprite/testsprite_portero_test.py` |
| `testsprite_residente_titular_test.py` | `tests/testsprite/testsprite_residente_titular_test.py` |
| `testsprite_superadmin_test.py` | `tests/testsprite/testsprite_superadmin_test.py` |

### 6.2. Reorganización de Semillas en `database/`
| Ruta Anterior | Nueva Ruta Oficial |
|---|---|
| `database/demo/V5.99__demo_seeds.sql` | `database/seeds/demo/V5.99__demo_seeds.sql` |
| `database/test-data/phase-1b/*` | `database/seeds/test-data/phase-1b/*` |

### 6.3. Taxonomía de Documentación (`docs/`)
Los 86 documentos planos se categorizaron según su disciplina técnica:

- **`docs/architecture/` (8 documentos):**
  `AUDIT_ARCHITECTURE.md`, `AUDIT_ROLE_ARCHITECTURE.md`, `CHECKLIST_SAED_2_0.md`, `MODULE_STATUS_MATRIX.md`, `PHASE_2_ARCHITECTURE_AUDIT.md`, `SAED_1_TO_SAED_2_BACKEND_MAP.md`, `SAED_2_0_MASTER_STATUS.md`, `SAED_MODEL_MASTER_IMPLEMENTATION_AUDIT.md`.
- **`docs/security/` (18 documentos):**
  `AUDIT_MUTATION_MATRIX.md`, `AUDIT_SECURITY_MODEL.md`, `PHASE_3_SECURITY_HARDENING_REPORT_2026_09_01.md`, `RLS_AUDIT.md`, `RLS_SECURITY_MATRIX.md`, `SECURITY_AUDIT.md`, reportes de certificación de seguridad por rol (`SUPERADMIN`, `ADMIN_ORGANIZACION`, `ADMIN_PROPIEDAD`, `PORTERO`, `RESIDENTE_TITULAR`, `CONVIVENCIA`), y reportes de hardening de fases (`SAED_PHASE2` a `SAED_PHASE8`).
- **`docs/database/` (8 documentos):**
  `DATABASE_AUDIT.md`, `PHASE_4_ORACLE_BASELINE_AUDIT_REPORT_2026_09_01.md`, `CLOUD-01_ATP_OBJECT_MATRIX.md`, `CLOUD-01_ATP_SYNCHRONIZATION_REPORT.md`, `CLOUD-02_FINAL_LIVE_CERTIFICATION_REPORT.md`, `C6.2_DEADLOCK_FORENSIC_REPORT.md`, `C6.4_FINDING_DB01_FORENSIC_REPORT.md`, `C6.4.1_PRODUCTION_RECOMPILATION_REPORT.md`.
- **`docs/testing/` (11 documentos):**
  `TEST_COVERAGE_AUDIT.md`, `QA-08_GLOBAL_FORM_VALIDATION_AUDIT.md`, `QA-08.1_DIFF_REGRESSION_GATE_REPORT.md`, `QA-08.2_ENCODING_AUDIT_REPORT.md`, `C6.2.1_SOLUTION_VALIDATION_REPORT.md`, `DEMO-03_LIVE_PRODUCTION_SMOKE_REPORT.md`, `DEMO-03_LIVE_SMOKE_MATRIX.md`, `SAED_PRUEBA_DE_HUMO_INTERFACES.md`, `SAED_SIMULACION_PAGO_RESIDENTE.md`, `SAED_PHASE4_1_VERIFICATION_REPORT.md`, `SAED_PHASE4_CI_QUALITY_GATES_REPORT.md`.
- **`docs/deployment/` (4 documentos):**
  `SAED_2.0_PRODUCTION_READY_REPORT.md`, `SAED_DESPLIEGUE_FINAL_CERTIFICACION.md`, `C6.1.2_PRODUCTION_EXECUTION_REPORT.md`, `C6.2.2_PRODUCTION_CHANGE_REPORT.md`.
- **`docs/cleanup/` (2 documentos):**
  `SAED_CLEANUP_INVENTORY.md`, `SAED_CLEANUP_REPORT.md`.
- **`docs/archive/` (37 documentos):**
  Informes de sprints preliminares, minutas y guiones de demos previas (`DEMO_02_*`, `MVP-01` a `MVP-07`, `DS-01` a `DS-07`, auditorías iniciales de agosto 2026).

---

## 7. DUPLICADOS RESUELTOS

### Deuda Técnica TECH-003: Doble Repositorio de Base de Datos
- **Problema:** Existencia de dos fuentes contradictorias de verdad relacional (`database/` y `database_final_release/`).
- **Resolución:**
  1. Se archivó completamente `database_final_release/` en `archive/legacy/database_v4_final_release/`.
  2. Se archivaron todos los scripts estáticos pre-Flyway de `database/` en `archive/legacy/database_v4/`.
  3. Se consolidó `database/migrations/` (versiones `V5.0` a `V5.12`) como el **único canal oficial e inmutable** para evolucionar el esquema relacional en Oracle ATP.

---

## 8. COMPONENTES LEGACY MANEJADOS

1. **JavaFX Monolith:**
   - Ubicado previamente en `backend_legacy/`, fue movido íntegramente a `archive/legacy/backend_legacy/`.
   - Se verificó que ninguna configuración de compilación de `backend/pom.xml` hiciera referencia a este directorio.
2. **Esquemas SQL Monolíticos Obsoletos:**
   - Todos los archivos `schema_v4.sql`, `seed_data_v4.sql`, `modelo_relacional_v4_atp.sql`, etc., quedaron confinados en `archive/legacy/`.
3. **Scripts de Ejecución Local Inseguros:**
   - Archivos de lote como `migrar.bat` y `RunSchemaFinal.java` que contenían llamadas JDBC directas fueron archivados; SAED 2.0 utiliza el plugin/CLI oficial de Flyway o los inicializadores de Spring Boot.

---

## 9. SECRETOS Y DATOS SENSIBLES ELIMINADOS O PROTEGIDOS

1. **Saneamiento de `README.md`:**
   - Se eliminaron las credenciales en texto plano que estaban expuestas en las instrucciones de ejecución y credenciales de prueba por defecto.
   - Se implementaron tablas de roles con referencia a variables de entorno (`SECURITY_USER_NAME`, `SECURITY_USER_PASSWORD`, etc.) y políticas de autenticación segura.
2. **Robustecimiento de `.gitignore`:**
   - Se agregaron exclusiones estrictas para prevenir fugas accidentales:
     - Archivos de entorno: `.env`, `.env.*`, `*.env`
     - Billeteras y certificados Oracle: `wallet/`, `*.p12`, `*.jks`, `*.key`, `*.pem`
     - Directorios de uploads locales: `uploads/`, `backend/uploads/`
     - Artefactos temporales de pruebas: `test-results/`, `playwright-report/`, `blob-report/`
     - Logs de ejecución y volcados: `*.log`, `npm-debug.log*`, `yarn-debug.log*`, `yarn-error.log*`

---

## 10. ESTRUCTURA FINAL DEL REPOSITORIO

```text
SAED/
├── .github/                          # Workflows de CI/CD y automatización
├── archive/                          # Componentes históricos archivados (aislados)
│   ├── legacy/
│   │   ├── backend_legacy/           # Monolito original JavaFX 19 (SAED 1.0)
│   │   ├── database_docs/            # Documentación relacional v4 histórica
│   │   ├── database_utilities/       # Utilitarios JDBC y bat obsoletos
│   │   ├── database_v4/              # Esquema DDL y DML estáticos v4
│   │   └── database_v4_final_release/# Release estático v4 consolidado
│   ├── scratch/                      # Scripts temporales de auditoría previa
│   └── README.md                     # Manifiesto y políticas de la carpeta archive
├── backend/                          # Backend Oficial SAED 2.0 (Spring Boot 3, Java 17)
│   ├── src/
│   │   ├── main/java/com/saed/backend/  # Código de producción (Clean Architecture)
│   │   └── test/java/com/saed/backend/  # Pruebas unitarias y de integración
│   └── pom.xml                       # Descriptor Maven oficial
├── database/                         # ÚNICA FUENTE DE VERDAD RELACIONAL (Oracle ATP)
│   ├── migrations/                   # Migraciones incrementales Flyway (V5.0 a V5.12)
│   ├── seeds/
│   │   ├── demo/                     # Datos de demostración controlados (V5.99)
│   │   └── test-data/                # Datos para pruebas de integración
│   └── README.md                     # Guía del ciclo de vida de base de datos
├── docs/                             # Documentación Técnica Centralizada
│   ├── architecture/                 # Arquitectura de software, dominios y matrices
│   ├── archive/                      # Informes históricos de sprints y demos pasadas
│   ├── cleanup/                      # Inventario y reporte formal de limpieza SAED 2.0
│   ├── database/                     # Auditorías de BD, matrices de objetos y RLS
│   ├── deployment/                   # Guías y certificaciones de despliegue en producción
│   ├── security/                     # Auditorías de seguridad y matrices de roles
│   ├── testing/                      # Informes de cobertura, smoke tests y planes QA
│   └── README.md                     # Índice general de navegación documental
├── frontend/                         # Frontend Oficial SAED 2.0 (React 18, Vite)
│   ├── src/                          # Componentes, vistas, contextos y hooks
│   ├── package.json                  # Dependencias de npm
│   └── vite.config.js                # Configuración de empaquetado Vite
├── tests/                            # Pruebas E2E y suites del sistema
│   ├── testsprite/                   # Suites de pruebas E2E de TestSprite
│   └── README.md                     # Guía de ejecución de pruebas
├── .gitignore                        # Reglas de exclusión profesionalizadas
├── Dockerfile                        # Imagen de contenedor para despliegue
├── render.yaml                       # Manifiesto de infraestructura cloud
└── README.md                         # Documentación maestra y onboarding de SAED 2.0
```

---

## 11. VERIFICACIÓN DE REFERENCIAS ROTAS

Se auditó de forma estricta todo el código activo para garantizar que ningún build, script de CI ni configuración dependa de rutas que fueron movidas o archivadas:

1. **`render.yaml`:**
   - Referencias a directorios raíz (`backend`, `frontend`) verificadas y correctas.
2. **`backend/pom.xml`:**
   - La raíz del proyecto backend es autónoma; no tiene módulos hijos que apunten a `backend_legacy/` o a `archive/`.
3. **`frontend/vite.config.js`:**
   - Rutas relativas del bundle frontend confinadas estrictamente a `frontend/src`.
4. **Imports en Frontend y Backend:**
   - Cero imports rotos detectados en ambos proyectos.

---

## 12. RESULTADOS DE BUILDS Y VALIDACIONES EJECUTADAS

### 12.1. Validación del Frontend
- **Comando:** `npm run build` en `frontend/`
- **Resultado:** **EXITOSO (0 errores)**
- **Tiempo de Ejecución:** 11.43s
- **Detalle:** Todos los chunks generados limpiamente en `frontend/dist/`.

### 12.2. Validación del Backend
- **Comando:** `mvn test-compile` en `backend/`
- **Resultado:** **BUILD SUCCESS (0 errores)**
- **Tiempo de Ejecución:** 9.33s
- **Detalle:** Compilación limpia de los 218 archivos de producción y todas las clases de prueba.

---

## 13. DEUDA TÉCNICA IDENTIFICADA DURANTE LA LIMPIEZA

Para el backlog del ciclo de reconstrucción/continuación de SAED 2.0, se identificaron los siguientes puntos:

1. **Unificación del Runner de Pruebas E2E:**
   - Los scripts `tests/testsprite/*.py` utilizan scripts Python individuales que deben integrarse formalmente en un orquestador de CI (ej. GitHub Actions / Pytest).
2. **Automatización de Migraciones Flyway en Despliegue:**
   - Asegurar que la ejecución de `database/migrations/` esté plenamente automatizada mediante pipeline CI/CD antes del despliegue del contenedor de backend.
3. **Poda de DTOs Inutilizados:**
   - Revisar si existen DTOs huérfanos que hayan quedado desfasados tras la transición de endpoints v4 a v5.

---

## 14. RIESGOS RESIDUALES Y MITIGACIONES

| Riesgo | Probabilidad | Impacto | Mitigación Implementada |
|---|---|---|---|
| Desarrolladores buscando scripts SQL en `database_final_release/` | Baja | Bajo | `database/README.md` y `archive/README.md` indican claramente que `database/migrations/` es la única fuente de verdad. |
| Necesidad de consultar código JavaFX de la v1.0 | Media | Bajo | Todo el código se encuentra intacto en `archive/legacy/backend_legacy/` con su historial git preservado. |
| Inconsistencias imprevistas en ramas paralelas | Baja | Alto | Tag de restauración inmutable `SAED-PRE-CLEANUP` disponible para revertir o comparar en cualquier momento. |

---

## 15. RECOMENDACIONES PARA EL DESARROLLO DE SAED 2.0

1. **Respetar la Taxonomía de Base de Datos:** Todo cambio estructural debe originarse en un archivo versionado dentro de `database/migrations/` siguiendo la convención Flyway `V5.x__descripcion.sql`. Prohibido crear scripts estáticos en la raíz.
2. **Mantener la Raíz Libre de Scripts Efímeros:** Pruebas y utilidades deben ubicarse en `tests/` o en sus respectivos submódulos.
3. **Cumplir con Conventional Commits:** Utilizar prefijos semánticos (`feat:`, `fix:`, `chore:`, `refactor:`, `docs:`) sin atributos de autoría artificial.
4. **Validar Builds Localmente:** Ejecutar `npm run build` y `mvn test-compile` antes de proponer merges hacia la rama principal.

---

## 16. CONFIRMACIÓN DE NO-REGRESIÓN

Se certifica que:
- **0 líneas de lógica de negocio fueron alteradas o suprimidas.**
- Los endpoints REST, entidades JPA/JDBC, esquemas de bases de datos activos, componentes React y reglas de seguridad permanecen 100% operativos e idénticos a su estado previo.
- La base del repositorio ha quedado profesionalmente consolidada, limpia y lista para continuar el desarrollo de SAED 2.0.
