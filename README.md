# SAED 2.0 - Sistema de Administracion de Edificios y Propiedades

[![React](https://img.shields.io/badge/Frontend-React%2018%20%2B%20Vite-blue.svg)](https://react.dev/)
[![Spring Boot](https://img.shields.io/badge/Backend-Spring%20Boot%203-6DB33F.svg)](https://spring.io/projects/spring-boot)
[![Oracle](https://img.shields.io/badge/Database-Oracle%2019c%20RLS-red.svg)](https://www.oracle.com/database/)
[![TailwindCSS](https://img.shields.io/badge/Styles-Tailwind%20CSS%20%2B%20shadcn--ui-38bdf8.svg)](https://tailwindcss.com/)

> **SAED 2.0** es la evolucion de la plataforma para la administracion de edificios residenciales, condominios y conjuntos cerrados. Integra control de accesos con codigos QR, gestion contractual, liquidacion financiera, y un robusto esquema Zero-Trust apoyado en Oracle Row-Level Security (RLS).

---

## Arquitectura y Estructura del Proyecto

El repositorio esta organizado en modulos desacoplados y bien estructurados:

```
SAED/
  frontend/                   # Frontend SPA (React 18 + Vite + TailwindCSS + shadcn-ui)
    src/
      components/ui/          # Componentes shadcn (Button, Card, Dialog, Table, etc.)
      pages/                  # Paginas de la aplicacion
      lib/                    # Hooks, API, contextos (Tenant, Auth, useTenantApi)
    package.json
    vite.config.js

  backend/                    # Backend REST (Spring Boot 3 + Java 17+)
    src/main/java/com/saed/backend/
      config/                 # SecurityConfig, CORS, SaedConnectionProxy
      identity/               # Auth: login, JWT, usuarios
      authorization/          # Asignaciones, unidades, propiedades, organizaciones
      catalog/                # Catalogos: roles, tipos, bloques
      finanzas/               # Contratos, pagos, Wompi, planes, membresias
      convivencia/            # Multas, quejas, buzon
      comunicacion/           # Comunicados, alertas, email (Brevo)
      dashboard/              # Dashboard KPIs, auditoria
      person/                 # Personas, dependientes
      porteria/               # Visitas, control de acceso
      common/                 # EmailService, excepciones, DTOs
    Dockerfile
    pom.xml
```

## Stack Tecnologico

| Capa | Tecnologia | Version |
|------|-----------|---------|
| Frontend | React + Vite + TailwindCSS + shadcn-ui | 18 + 5 |
| Backend | Spring Boot + Java | 3.2 + 17+ |
| Database | Oracle Autonomous Database (ATP) | 19c |
| Seguridad | JWT + Oracle RLS + PKG_AUTH_BOOTSTRAP | - |
| Pagos | Wompi (produccion) | - |
| Email | Brevo HTTP API v3 | - |
| Deploy | Render (backend) + Vercel (frontend) | - |

## URLs de Produccion

- **Frontend**: https://saedfront.vercel.app
- **Backend**: https://saed-backend.onrender.com
- **API Docs**: https://saed-backend.onrender.com/swagger-ui.html

## Credenciales de Prueba

| Rol | Usuario | Contrasena |
|-----|---------|-----------|
| SUPERADMIN | admin_global | Admin123! |
| RESIDENTE (org 1) | residente_hor | Admin123! |
| RESIDENTE (org 2) | ressol@test.com | Admin123! |

## Funcionalidades Implementadas

### Fase A - Wompi + Auditoria
- [x] Wompi real: crearIntencion de pago ($87.350 -> 8735000 centavos)
- [x] Webhook con verificacion HMAC-SHA256
- [x] 4 triggers de auditoria (PROPIEDADES, PAGOS, ASIGNACIONES, MULTAS)
- [x] GlobalExceptionHandler registra accesos denegados en AUDITORIA_LOG
- [x] AUDITORIA_LOG append-only (UPDATE/DELETE -> ORA-20099)

### Fase B - Paginas de Administracion
- [x] OrganizacionesPage: CRUD + activar/suspender (PATCH status)
- [x] PropiedadesPage: CRUD con organizacion y tipo de propiedad
- [x] RolesYAsignacionesPage: asignacion dinamica por alcance del rol

### Fase C - Planes y Membresias
- [x] PlanesController: CRUD de planes comerciales (FREE, PRO, ENTERPRISE)
- [x] MembresiasController: suscripciones de organizaciones a planes
- [x] Seed data en ATP: 3 planes + 2 membresias

### Fase D - Auditoria y Reportes
- [x] AuditoriaController: lectura con filtros (tabla, accion, fechas)
- [x] Estadisticas de auditoria agrupadas por tabla/accion
- [x] ReportesPage: tabs de registro y estadisticas

### Fase E - Notificaciones Email
- [x] EmailService via Brevo HTTP v3 (reemplaza SMTP)
- [x] Email masivo al publicar avisos (ComunicadosController)
- [x] Notificacion de multas y QR de visitas

### Seguridad
- [x] Zero-Trust: SET_CONTEXT por cada request via JwtAuthenticationFilter
- [x] RLS en 91 tablas (FN_FILTRO_ORGANIZACION, FN_FILTRO_PROPIEDAD, FN_FILTRO_UNIDAD)
- [x] Anti-escalada de privilegios en AssignmentManagementService
- [x] SCOPE_* authorities + context auto-resolved via GET_USER_PROFILE
- [x] PKG_AUTH_BOOTSTRAP: GET_AUTH_DATA, GET_ASSIGNMENT_CONTEXT, SET_CONTEXT

## Desarrollo

### Backend
```bash
cd backend
mvn clean package -DskipTests
java -jar target/backend-1.0.0-SNAPSHOT.jar
```

### Frontend
```bash
cd frontend
npm install
npm run dev      # desarrollo
npm run build    # produccion
```

### Tests
```bash
# Tests unitarios (no requieren BD)
mvn test -Dtest="!*Integration*,!*Phase1*"

# Todos los tests (requiere Oracle XE local)
mvn clean test
```

## Base de Datos

- **ATP SAED2**: 96 tablas, 91 politicas RLS, 9 triggers ENABLED
- **Packages**: PKG_SAED_SESSION, PKG_SAED_SECURITY_RLS, PKG_AUTH_BOOTSTRAP
- **Deploy scripts**: `C:\Users\JUAN\Downloads\SAED_2_0_Base_Datos_Final\`

## Documentacion

- `SAED_MAESTRO_CREDENCIALES.txt` - Todas las credenciales del sistema
- `SAED_2_0_PAQUETE_DEPLOY.txt` - Paquete de despliegue completo
- `docs/1.0/` - Documentacion SAED 1.0 (legado)
