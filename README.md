# SAED 2.0 — Sistema de Administración de Edificios y Copropiedades

[![Java](https://img.shields.io/badge/Java-17%2B-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3.3-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-18-61DAFB?style=for-the-badge&logo=react&logoColor=black)](https://react.dev/)
[![Vite](https://img.shields.io/badge/Vite-5-646CFF?style=for-the-badge&logo=vite&logoColor=white)](https://vitejs.dev/)
[![Oracle](https://img.shields.io/badge/Oracle_Autonomous_DB-19c-F80000?style=for-the-badge&logo=oracle&logoColor=white)](https://www.oracle.com/database/)
[![Tailwind CSS](https://img.shields.io/badge/Tailwind_CSS-3-06B6D4?style=for-the-badge&logo=tailwind-css&logoColor=white)](https://tailwindcss.com/)

---

## 1. Descripción
**SAED 2.0** es una plataforma SaaS multi-tenant diseñada para la administración integral de edificios residenciales, condominios y conjuntos cerrados. Resuelve la gestión operativa, financiera, de seguridad y convivencia mediante un modelo centralizado y seguro, garantizando trazabilidad y aislamiento riguroso entre organizaciones y copropiedades.

---

## 2. Visión
Convertirse en el estándar de software para la propiedad horizontal en Colombia y Latinoamérica, ofreciendo a inmobiliarias, administradores, personal de vigilancia y habitantes una experiencia moderna, accesible y confiable con soporte para pasarelas de pago locales (Wompi), integración con servicios geográficos oficiales (API Colombia) y control de acceso seguro mediante códigos QR y códigos PIN de paquetería.

---

## 3. Arquitectura
SAED 2.0 implementa una arquitectura desacoplada y orientada al dominio con segregación estricta:
- **Frontend SPA:** Cliente ligero en React 18 con Vite, Tailwind CSS y componentes accesibles (Radix UI / Shadcn). Enrutamiento declarativo protegido por Guards de roles y scopes.
- **Backend API REST:** Servicio stateless en Spring Boot 3 con Java 17+, gobernado por Spring Security y un contexto dinámico de sesión (`SaedContextHolder`).
- **Persistencia y Aislamiento:** Oracle Autonomous Transaction Processing (ATP) utilizando Virtual Private Database (VPD) / Row Level Security (RLS). Cada conexión a la base de datos se ejecuta bajo el contexto seguro `SAED_CTX` que impide el acceso a datos entre organizaciones ajenas a nivel de motor de base de datos.
- **Cero Confianza (Zero-Trust):** La seguridad no recae exclusivamente en la capa web; el motor de base de datos valida matemáticamente la organización, propiedad y asignación de cada consulta.

---

## 4. Stack Tecnológico

| Capa | Tecnologías | Propósito |
| :--- | :--- | :--- |
| **Frontend** | React 18, Vite 5, Tailwind CSS, Lucide Icons, Axios | Aplicación web reactiva para 5 perfiles de usuario |
| **Backend** | Spring Boot 3.3.3, Java 17, Spring Security 6, HikariCP | API REST empresarial y orquestación de negocio |
| **Base de Datos** | Oracle Database 19c / 23ai (ATP / XE) | Base relacional con VPD/RLS y auditoría inmutable |
| **Seguridad** | JWT (HMAC-SHA256), Spring Security, BCrypt | Autenticación basada en token y control de acceso (RBAC) |
| **Pagos** | Wompi API v1 + Webhooks (HMAC-SHA256) | Pasarela de pagos para cuotas y onboarding de copropiedades |
| **Comunicaciones** | Brevo HTTP API v3, Plantillas HTML responsivas | Notificación de avisos masivos, cobros y visitas |
| **Geolocalización** | API Colombia (`api-colombia.com/api/v1`) | Carga dinámica de 32 departamentos y +1.100 municipios |
| **Testing** | JUnit 5, Mockito, Playwright (Node.js), TestSprite | Pruebas unitarias, integración, seguridad y E2E |

---

## 5. Estructura del Repositorio

El repositorio ha sido consolidado y reorganizado para eliminar código muerto y separar el runtime activo de los registros históricos:

```text
SAED/
├── .github/              # Workflows de CI/CD para GitHub Actions
├── archive/              # Repositorio histórico y código legacy congelado
│   ├── legacy/           # Antiguo monolito JavaFX, dumps v4 y utilitarios
│   ├── scratch/          # Scripts experimentales de auditoría
│   └── README.md         # Documentación de activos archivados
├── backend/              # API REST empresarial (Spring Boot 3 + Java 17)
│   ├── src/main/java/    # Código fuente: controladores, servicios, RLS
│   ├── src/main/resources# application.yml multi-perfil y plantillas
│   ├── src/test/java/    # Suites de pruebas unitarias, RLS y seguridad
│   ├── Dockerfile        # Contenedor de producción
│   └── pom.xml           # Descriptor de dependencias Maven
├── database/             # Única fuente de verdad de la base de datos
│   ├── migrations/       # Migraciones evolutivas secuenciales (V5.0 a V5.12)
│   ├── seeds/            # Semillas de prueba, producción y demo
│   └── README.md         # Guía de despliegue y estructura relacional
├── docs/                 # Centro de documentación técnica
│   ├── architecture/     # Especificaciones de arquitectura y contratos
│   ├── database/         # Modelo de datos, diccionarios y matrices RLS
│   ├── security/         # Certificaciones de roles, permisos y políticas
│   ├── testing/          # Reportes QA, matrices de prueba y cobertura
│   ├── deployment/       # Despliegue en Render, Oracle Cloud y Vercel
│   ├── cleanup/          # Reporte e inventario de limpieza del repositorio
│   ├── contexto/         # Documento Maestro oficial de SAED 2.0
│   └── archive/          # Reportes históricos de fases y sprints anteriores
├── frontend/             # Cliente Web SPA (React 18 + Vite + Tailwind)
│   ├── src/components/   # Componentes reutilizables, AppShell, selectores
│   ├── src/pages/        # 85 vistas operativas según rol
│   ├── src/lib/          # Clientes API, contexto Auth/Tenant, API Colombia
│   └── package.json      # Dependencias y scripts de empaquetado
├── scripts/              # Herramientas de automatización para desarrollo
│   └── dev/              # Scripts de inicio rápido local (.bat)
├── tests/                # Suites de pruebas automatizadas
│   ├── e2e/              # 24 especificaciones Playwright (JavaScript)
│   ├── testsprite/       # Pruebas automatizadas de rol (Python)
│   └── README.md         # Instrucciones de ejecución de pruebas
├── .env.example          # Plantilla oficial de variables de entorno
├── .gitignore            # Reglas de exclusión de artefactos y secretos
├── render.yaml           # Descriptor de infraestructura como código (Render)
└── README.md             # Este documento
```

---

## 6. Base de Datos y Modelo Multi-Tenant
La persistencia descansa sobre Oracle Autonomous Database:
- **Línea Base (`V5.0__master_baseline.sql`):** Estructura relacional completa para organizaciones, propiedades, torres/bloques, unidades, personas, usuarios, asignaciones, visitas, pqrs, finanzas y multas.
- **Aislamiento en Profundidad:** Contexto de base de datos `SAED_CTX` fijado por sesión HTTP en el pool de conexiones Hikari (`PKG_SAED_SESSION.SET_CONTEXT`).
- **Auditoría Inmutable:** Tabla `AUDITORIA_LOG` protegida con disparadores append-only donde mutaciones no autorizadas generan excepción (`ORA-20099`).

---

## 7. Seguridad y Modelo de Control de Acceso
SAED 2.0 define 6 scopes de seguridad en runtime:

| Scope | Perfil | Nivel de Visibilidad y Operación |
| :--- | :--- | :--- |
| `SCOPE_SUPERADMIN` | Superadministrador de Plataforma | Gestión de organizaciones SaaS, planes comerciales, suscripciones y auditoría global. No interviene en la operación de copropiedades individuales. |
| `SCOPE_ADMIN_ORGANIZACION` | Administrador de Organización | Administración de las propiedades asignadas a su empresa o inmobiliaria. |
| `SCOPE_ADMIN_PROPIEDAD` | Administrador de Copropiedad | Control operativo, financiero (cartera, pagos), asambleas, sanciones y mantenimiento del edificio. |
| `SCOPE_PORTERO` | Personal de Recepción / Vigilancia | Control de accesos en portería, registro y validación de visitas QR, custodia y entrega de paquetería con PIN. No accede a finanzas. |
| `SCOPE_RESIDENTE` | Residente Titular | Gestión de su unidad, creación de invitaciones QR, pago de expensas, consulta de estado de cuenta y reporte de PQRS. |
| `SCOPE_RESIDENTE_CONVIVENCIA` | Residente Habitante / Conviviente | Acceso a paquetería, reservas, solicitudes y visitas QR. Bloqueado estrictamente (HTTP 403) para estados financieros y cobros. |

---

## 8. Desarrollo Local y Puesta en Marcha

### Prerrequisitos
- **Java:** OpenJDK 17 o superior.
- **Maven:** 3.9 o superior (o utilizar el wrapper `./mvnw`).
- **Node.js:** 18 LTS o superior con npm.
- **Oracle Database:** Instancia XE local o ATP en Oracle Cloud.

### Configuración de Entorno
1. Copiar `.env.example` a `.env` en la raíz o configurar variables del sistema:
   ```bash
   cp .env.example .env
   ```
2. Configurar variables de base de datos en `backend/.env.example`.

### Inicio Rápido (Scripts de Desarrollo)
En Windows, utilizar los scripts incluidos en `scripts/dev/`:
```bash
# Iniciar ambos servicios en consolas separadas
scripts\dev\iniciar-todo.bat

# O iniciar individualmente:
scripts\dev\iniciar-backend.bat
scripts\dev\iniciar-frontend.bat
```

---

## 9. Testing y Calidad

El proyecto implementa pruebas continuas en múltiples niveles:
- **Backend (Unitarias e Integración):**
  ```bash
  cd backend
  ./mvnw test
  ```
- **Frontend (Build y Verificación de Tipos):**
  ```bash
  cd frontend
  npm run build
  ```
- **Pruebas End-to-End (Playwright):**
  ```bash
  npx playwright test tests/e2e/
  ```

---

## 10. Despliegue e Infraestructura
- **Backend API:** Configurado para Render mediante `render.yaml` y contenedor `backend/Dockerfile`.
- **Frontend SPA:** Desplegado en Vercel con reglas de enrutamiento SPA definidas en `frontend/vercel.json`.
- **Base de Datos:** Oracle Autonomous Transaction Processing (ATP) en Oracle Cloud Infrastructure (OCI).

---

## 11. Documentación del Proyecto
Para detalles técnicos exhaustivos, consultar [`docs/README.md`](./docs/README.md) y el [Documento Maestro](./docs/contexto/SAED_2.0_DOCUMENTO_MAESTRO_COMPLETO_FINAL.txt).

---

## 12. Estado del Proyecto y Roadmap
- **Fase Actual:** Consolidación y Limpieza Integral de Arquitectura SAED 2.0.
- **Hitos Completados:**
  - Multi-tenancy real con Oracle RLS.
  - Integración pasarela de pagos Wompi (cuotas y planes).
  - Control de paquetería con PIN confidencial para habitantes y convivientes.
  - Integración con API oficial de Colombia para selección dinámica de departamentos y municipios.
- **Próximos Pasos:**
  - Automatización de asambleas virtuales con quórum en tiempo real.
  - Notificaciones push nativas vía WebSockets y PWA.
  - Módulo de domótica e IoT para control de talanqueras de parqueadero.

---

## 13. Buenas Prácticas y Contribución
- Utilizar **Conventional Commits** (`feat:`, `fix:`, `chore:`, `docs:`, `refactor:`).
- No versionar credenciales ni secretos; usar variables de entorno.
- Las migraciones de base de datos son estrictamente incrementales e inmutables.
