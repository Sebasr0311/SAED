# SAED 2.0 — Acta de Despliegue Final y Certificación de Producción

**Fecha:** 13 de Septiembre de 2026  
**Versión:** SAED 2.0.0-PROD  
**Repositorio Oficial:** [`https://github.com/Sebasr0311/SAED.git`](https://github.com/Sebasr0311/SAED.git)  
**Rama de Producción:** `main` (Commit `051caec`)  
**Estado:** **100% OPERATIVO, DESPLEGADO Y CERTIFICADO EN VIVO**  

---

## 1. Topología y URLs de Producción

| Componente | Plataforma / Infraestructura | URL Pública Activa | Estado |
| :--- | :--- | :--- | :---: |
| **Frontend Web** | GitHub Pages (Vite / React 18 SPA) | [`https://sebasr0311.github.io/SAED/`](https://sebasr0311.github.io/SAED/) | 🟢 **200 OK** |
| **Backend API REST** | Render (Docker Java 17 / Spring Boot 3) | [`https://saed-backend.onrender.com/api/v1`](https://saed-backend.onrender.com/api/v1) | 🟢 **ONLINE** |
| **Base de Datos** | Oracle Cloud Autonomous AI DB (ATP 23ai) | `adb.sa-bogota-1.oraclecloud.com:1522` (`saed2_high`) | 🟢 **CONECTADO** |
| **Testing Cloud Automation** | TestSprite Cloud Platform | [`TestSprite Dashboard`](https://www.testsprite.com/dashboard-v3/o/cf669c02-659a-53bb-a874-e67d00f315dc/projects/e5392f69-9a48-4a8c-84d2-093857d3a9c3/) | 🟢 **6/6 PASSED** |

---

## 2. Estado de Pipelines CI/CD (GitHub Actions)

Los dos pipelines automatizados ejecutados sobre el commit final `051caec` concluyeron con éxito total:

| Pipeline | Trigger | Duración | Veredicto |
| :--- | :--- | :---: | :---: |
| **`CI - SAED 2.0`** (Backend Compile, Checkstyle, OWASP, Frontend Lint & Build) | Push a `main` | 57s | 🟢 **SUCCESS** |
| **`Deploy frontend to GitHub Pages`** (Build con `VITE_BASE_PATH=/SAED/`, Artifact Upload, Pages Deploy) | Push a `main` | 38s | 🟢 **SUCCESS** |

---

## 3. Matriz de Roles y Credenciales Certificadas en Vivo

Se validó el inicio de sesión real contra el endpoint productivo `https://saed-backend.onrender.com/api/v1/auth/login` para cada uno de los 6 roles de la arquitectura:

| Rol Canónico | Usuario | Password | Alcance (Scope) | Estado en ATP | Pruebas Cloud TestSprite |
| :--- | :--- | :--- | :---: | :---: | :---: |
| **`SUPERADMIN`** | `admin_global` | `admin123` | `GLOBAL` | `ACTIVO` | 🟢 **PASSED** |
| **`ADMIN_ORGANIZACION`** | `admin_org` | `admin123` | `ORGANIZACION` | `ACTIVO` | 🟢 **PASSED** |
| **`ADMIN_PROPIEDAD`** | `admin` | `admin123` | `PROPIEDAD` | `ACTIVO` | 🟢 **PASSED** |
| **`PORTERO`** | `portero01` | `admin123` | `PROPIEDAD` | `ACTIVO` | 🟢 **PASSED** |
| **`RESIDENTE`** (Titular) | `camartinez` | `admin123` | `UNIDAD` | `ACTIVO` | 🟢 **PASSED** |
| **`RESIDENTE_CONVIVENCIA`** | `jjuan123` | `admin123` | `UNIDAD` | `ACTIVO` | 🟢 **PASSED** |

---

## 4. Garantías Arquitectónicas Implementadas

1. **Aislamiento Multi-Tenant RLS / VPD**: Políticas de Row-Level Security en Oracle ATP garantizan que ningún tenant u organización acceda a datos ajenos.
2. **Defensa de Fuerza Bruta y Auditoría**: El paquete `PKG_AUTH_BOOTSTRAP` registra intentos fallidos, bloquea cuentas bajo ataque y audita accesos en tiempo real con transacciones autónomas.
3. **Perimetraje Inverso Zero-Trust**: `SUPERADMIN` opera a nivel SaaS global sin facultades para violar el secreto ni los datos privados de copropiedad de los residentes.
4. **Resiliencia de Esquema**: `ProductionSchemaInitializer` auto-repara roles canónicos y asignaciones al iniciar la aplicación.
