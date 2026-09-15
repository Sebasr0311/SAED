# SAED 2.0 — Inventario de Limpieza y Consolidación del Repositorio

**Fecha:** 2026-09-15  
**Tag de Restauración:** `SAED-PRE-CLEANUP` (Commit: `1daae7b`)  
**Rama:** `Sebasr0311/angelfish` / `main`  
**Repositorio Oficial:** https://github.com/Sebasr0311/SAED  

---

## 1. Resumen Ejecutivo del Estado Inicial

El repositorio contiene el código fuente de **SAED 2.0** (SaaS Multi-tenant residencial), junto con residuos y artefactos históricos de la versión anterior (aplicación monolítica JavaFX de escritorio) y múltiples fases de auditoría acumuladas en el root y subcarpetas.

### Métricas Iniciales del Repositorio:
- **Total de Archivos Trackeados en Git:** 1,266 archivos
- **Líneas de Código Activas:**
  - Backend Spring Boot 3: ~30 paquetes Java, 62 controladores REST, ~120 suites de tests.
  - Frontend React 18: 85 vistas/páginas en `frontend/src/pages`, componentes Shadcn/Tailwind.
  - Base de Datos: 2 fuentes de verdad paralelas (`database/` y `database_final_release/`).
  - Documentación: 86 archivos markdown en la raíz de `docs/` sin taxonomía de carpetas.
  - Tests E2E / Pruebas: 24 especificaciones Playwright en `tests/e2e/`, más 7 scripts `testsprite_*.py` en el root del repositorio.
  - Residuos Legacy: Proyecto JavaFX monolítico completo en `backend_legacy/` (194 archivos).

---

## 2. Inventario Detallado y Clasificación (Fase 1)

Las categorías de clasificación son:
- **KEEP**: Necesario para SAED 2.0 o infraestructura activa del repositorio.
- **REFACTOR**: Tiene valor pero su ubicación, nombre o estructura debe ajustarse.
- **ARCHIVE**: Tiene valor histórico o documental pero debe salir del runtime/raíz.
- **DELETE**: Código muerto, temporales, duplicados o binarios no versionables.
- **REVIEW_REQUIRED**: Requiere confirmación o inspección previa.

---

### 2.1 Componente: Raíz del Repositorio (Root)

| Elemento | Tipo | Clasificación | Motivo y Destino |
| :--- | :--- | :--- | :--- |
| `README.md` | Archivo | **REFACTOR** | Reescribir profesionalmente para reflejar la visión, stack y arquitectura de SAED 2.0. |
| `.gitignore` | Archivo | **REFACTOR** | Profesionalizar para blindar uploads, test-results, binarios locales y scratch. |
| `.env.example` | Archivo | **KEEP** | Plantilla de variables de entorno para frontend y backend. |
| `render.yaml` | Archivo | **KEEP** | Descriptor de despliegue IaC para Render (servicios web y API). |
| `SAED_2.0_DOCUMENTO_MAESTRO_COMPLETO_FINAL.txt` | Archivo | **KEEP** | Documento maestro canónico de SAED 2.0. Se conserva copia en root y en `docs/contexto/`. |
| `testsprite_admin_organizacion_test.py` | Archivo | **REFACTOR** | Script E2E de TestSprite. Mover a `tests/testsprite/`. |
| `testsprite_admin_propiedad_test.py` | Archivo | **REFACTOR** | Script E2E de TestSprite. Mover a `tests/testsprite/`. |
| `testsprite_conviviente_test.py` | Archivo | **REFACTOR** | Script E2E de TestSprite. Mover a `tests/testsprite/`. |
| `testsprite_paquetes_test.py` | Archivo | **REFACTOR** | Script E2E de TestSprite. Mover a `tests/testsprite/`. |
| `testsprite_portero_test.py` | Archivo | **REFACTOR** | Script E2E de TestSprite. Mover a `tests/testsprite/`. |
| `testsprite_residente_titular_test.py` | Archivo | **REFACTOR** | Script E2E de TestSprite. Mover a `tests/testsprite/`. |
| `testsprite_superadmin_test.py` | Archivo | **REFACTOR** | Script E2E de TestSprite. Mover a `tests/testsprite/`. |
| `backend/` | Directorio | **KEEP** | Runtime backend activo (Spring Boot 3 + Oracle ATP). |
| `frontend/` | Directorio | **KEEP** | Runtime frontend activo (React 18 + Vite). |
| `backend_legacy/` | Directorio | **ARCHIVE** | Monolito JavaFX antiguo (194 archivos). Mover a `archive/legacy/backend_legacy/`. |
| `database/` | Directorio | **KEEP / REFACTOR** | Única fuente de verdad de base de datos de SAED 2.0. |
| `database_final_release/` | Directorio | **ARCHIVE** | Esquema estático v4 y scripts duplicados. Mover a `archive/legacy/database_v4_final_release/`. |
| `scratch/` | Directorio | **ARCHIVE** | Scripts temporales de auditoría (`audit_forms.js`, etc.). Mover a `archive/scratch/`. |
| `tests/` | Directorio | **KEEP / REFACTOR** | Suite de pruebas E2E Playwright. Reorganizar para alojar tests de python. |
| `scripts/` | Directorio | **KEEP / REFACTOR** | Scripts de arranque para desarrollo local. |
| `docs/` | Directorio | **REFACTOR** | Documentación del proyecto. Clasificar en subcarpetas estructuradas. |
| `test-results/` | Directorio | **DELETE / GITIGNORE** | Artefactos generados de pruebas locales. |
| `testsprite_tests/` | Directorio | **DELETE / GITIGNORE** | Temporales generados por CLI testsprite. |
| `uploads/` | Directorio | **GITIGNORE** | Directorio de carga de archivos en tiempo de ejecución. |
| `.agents/` | Directorio | **KEEP** | Configuración de agentes y workflows de desarrollo. |
| `.codegraph/` | Directorio | **KEEP / GITIGNORE DB** | Índice CodeGraph local. |
| `.github/` | Directorio | **KEEP** | Workflows y plantillas de GitHub Actions. |

---

### 2.2 Componente: Backend (`backend/`)

| Elemento | Clasificación | Observaciones |
| :--- | :--- | :--- |
| `src/main/java/com/saed/backend/**` | **KEEP** | Núcleo de la API REST: controladores, servicios, repositorios, seguridad, RLS. |
| `src/main/resources/application.yml` | **KEEP** | Configuración multi-perfil (dev / prod) sin secretos hardcodeados. |
| `src/main/resources/templates/**` | **KEEP** | Plantillas HTML/PDF de contratos y notificaciones. |
| `src/test/java/com/saed/backend/**` | **KEEP** | Suites de pruebas unitarias, de integración, adversariales y de seguridad RLS. |
| `pom.xml` | **KEEP** | Configuración Maven de dependencias de Spring Boot 3.3.3 y Oracle JDBC. |
| `mvnw`, `mvnw.cmd`, `.mvn/` | **KEEP** | Maven Wrapper para compilación reproducible. |
| `Dockerfile` | **KEEP** | Contenedor de producción para despliegue en Render/Cloud. |
| `.dockerignore` | **KEEP** | Exclusiones de construcción de Docker. |
| `.env.example` | **KEEP** | Plantilla de configuración de entorno para backend. |
| `checkstyle.xml`, `owasp-suppressions.xml` | **KEEP** | Reglas de calidad y escaneo de vulnerabilidades. |
| `clean.py` | **DELETE** | Script temporal descartable de 14 líneas que mutaba un archivo SQL. |
| `DbCheck.java`, `DbCheck.class` | **DELETE** | Scratch script no trackeado en raíz de backend. |
| `DbCleanTest.java`, `DbCleanTest.class` | **DELETE** | Scratch script no trackeado en raíz de backend. |
| `target/` | **GITIGNORE** | Directorio de compilación Maven. |
| `uploads/` | **GITIGNORE** | Directorio de subida local en backend. |

---

### 2.3 Componente: Frontend (`frontend/`)

| Elemento | Clasificación | Observaciones |
| :--- | :--- | :--- |
| `src/**` | **KEEP** | 85 páginas, componentes UI Shadcn/Radix, servicios API, integración API Colombia. |
| `package.json`, `package-lock.json` | **KEEP** | Dependencias de React 18, Vite 5, Tailwind CSS, Lucide. |
| `vite.config.js` | **KEEP** | Configuración de empaquetado y proxy de desarrollo. |
| `tailwind.config.js`, `postcss.config.js` | **KEEP** | Configuración de estilos y sistema de diseño. |
| `tsconfig.json` | **KEEP** | Tipado y resolución de rutas (`@/*`). |
| `vercel.json` | **KEEP** | Reglas de enrutamiento SPA para Vercel. |
| `index.html` | **KEEP** | Entrada principal del SPA. |
| `.env.local` | **GITIGNORE** | Configuración de entorno local del desarrollador (ignorado en git). |
| `dist/` | **GITIGNORE** | Salida del build de producción. |
| `node_modules/` | **GITIGNORE** | Dependencias de Node. |

---

### 2.4 Componente: Base de Datos (`database/` vs `database_final_release/`)

**Diagnóstico de Duplicidad:**
Actualmente existen dos directorios relacionados con la base de datos:
1. `database_final_release/`: Contiene `modelo_relacional_v4_atp.sql` (500 KB, versión 4 estática monolithic previa) y `datos_prueba.sql`. Ya NO representa el estado activo de la base de datos de SAED 2.0.
2. `database/`: Contiene el baseline multi-tenant real (`V5.0__master_baseline.sql`) y las migraciones evolutivas activas `V5.1` a `V5.12` que soportan RLS, auditoría inmutable, Wompi, PIN de paquetería y rol `RESIDENTE_CONVIVENCIA`.

**Acción de Consolidación:**
- **Única Fuente de Verdad:** `database/` se consolida como el único directorio canónico para la base de datos de SAED 2.0.
- **Archivo Histórico:** Mover `database_final_release/` a `archive/legacy/database_v4_final_release/`.
- **Limpieza Interna en `database/`:**
  - `database/migrations/`: KEEP (Secuencia formal de migraciones).
  - `database/seeds/`: KEEP / REFACTOR (Semillas ordenadas en `demo/` y `test/`).
  - `database/legacy/scripts/`: ARCHIVE (20 scripts SQL temporales de parche). Mover a `archive/legacy/database_scripts/`.
  - `database/utilities/`: ARCHIVE (Clases Java sueltas de migración manual `AuditoriaOracleCloud.java`, `Cols.java`, etc.). Mover a `archive/legacy/database_utilities/`.

---

### 2.5 Componente: Pruebas y Tests (`tests/` y scripts root)

| Elemento | Ubicación Actual | Clasificación | Destino Propuesto |
| :--- | :--- | :--- | :--- |
| Pruebas E2E Playwright | `tests/e2e/*.spec.js` | **KEEP** | `tests/e2e/` (24 specs completas). |
| Helpers de autenticación E2E | `tests/e2e/helpers/auth.js` | **KEEP** | `tests/e2e/helpers/auth.js`. |
| Scripts TestSprite Python (7) | `testsprite_*.py` (root) | **REFACTOR** | `tests/testsprite/` (organización profesional). |
| Resultados temporales | `test-results/` | **DELETE / GITIGNORE** | Mantener fuera de Git. |
| Temporales TestSprite | `testsprite_tests/` | **DELETE / GITIGNORE** | Mantener fuera de Git. |

---

### 2.6 Componente: Documentación (`docs/`)

Actualmente, 86 archivos `.md` residen directamente en la raíz de `docs/`.
La estructura organizada para SAED 2.0 será:

```text
docs/
├── architecture/      # Arquitectura global, multi-tenancy, contratos
├── database/          # Diccionario de datos, RLS, matrices VPD
├── security/          # Modelos de autorización, roles, auditoría
├── testing/           # Cobertura, planes QA, validaciones
├── deployment/        # Guías Render, Vercel, Oracle ATP, CI/CD
├── context/           # Documento maestro y contexto de proyecto
├── cleanup/           # Reportes de limpieza, inventario y consolidación
└── archive/           # Reportes históricos de sprints pasados, demos anteriores
```

---

## 3. Auditoría de Seguridad y Archivos Sensibles (Fase 3)

1. **Archivos `.env` en Git:**
   - `.env.example` (root): Plantilla segura con valores vacíos. No contiene secretos.
   - `backend/.env.example`: Plantilla segura con placeholders. No contiene secretos.
   - `frontend/.env.local`: No está trackeado en Git (protegido por `.gitignore`). Contiene únicamente `VITE_API_BASE_URL=http://localhost:8080/api/v1`.
2. **Llaves Privadas y Certificados (`*.pem`, `*.key`, `*.p12`, `*.jks`):**
   - No se encontraron certificados ni llaves criptográficas privadas versionadas en el árbol Git.
3. **Credenciales en Código:**
   - `application.yml`: Configurado con `${DB_URL}`, `${DB_USERNAME}`, `${DB_PASSWORD}`, `${JWT_SECRET}` para entorno de producción. Los valores por defecto de dev usan variables locales de pruebas.
   - `update_passwords.sql`: Contiene hashes bcrypt genéricos de desarrollo para cuentas seed.
   - **Recomendación:** Mantener la política Zero-Trust y verificar que `.gitignore` blinde permanentemente cualquier fichero `.env`, `.pem` o credencial local.

---

## 4. Plan de Ejecución de la Limpieza (Fases 4 a 11)

1. **Crear carpetas de destino organizadas:**
   - `archive/legacy/backend_legacy/`
   - `archive/legacy/database_v4_final_release/`
   - `archive/legacy/database_scripts/`
   - `archive/legacy/database_utilities/`
   - `archive/scratch/`
   - `tests/testsprite/`
   - Taxonomía `docs/` (`architecture/`, `database/`, `security/`, `testing/`, `deployment/`, `archive/`).
2. **Mover archivos con `git mv`** para preservar el historial Git y la trazabilidad.
3. **Eliminar archivos estrictamente temporales/descartables** (`backend/clean.py`, clases compiladas temporales).
4. **Professionalizar `.gitignore`**.
5. **Reescribir `README.md`** completo para SAED 2.0.
6. **Ejecutar validación de compilación y pruebas:**
   - Backend: `mvn test-compile` / `mvn test`.
   - Frontend: `npm run build`.
7. **Generar reporte final:** `docs/cleanup/SAED_CLEANUP_REPORT.md`.
