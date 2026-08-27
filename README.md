# SAED 2.0 - Sistema de Administración de Edificios y Propiedades

[![React](https://img.shields.io/badge/Frontend-React%2018%20%2B%20Vite-blue.svg)](https://react.dev/)
[![Spring Boot](https://img.shields.io/badge/Backend-Spring%20Boot%203-6DB33F.svg)](https://spring.io/projects/spring-boot)
[![Oracle](https://img.shields.io/badge/Database-Oracle%2019c%20RLS-red.svg)](https://www.oracle.com/database/)
[![TailwindCSS](https://img.shields.io/badge/Styles-Tailwind%20CSS-38bdf8.svg)](https://tailwindcss.com/)

> **SAED 2.0** es la evolución de la plataforma para la administración de edificios residenciales, condominios y conjuntos cerrados. Integra control de accesos con códigos QR, gestión contractual, liquidación financiera, y un robusto esquema Zero-Trust apoyado en Oracle Row-Level Security (RLS).

---

## 🏗️ Arquitectura y Estructura del Proyecto

El repositorio está organizado en módulos desacoplados y bien estructurados bajo la nueva arquitectura de SAED 2.0:

```text
SAED/
├── frontend/                   # Frontend SPA (React 18 + Vite + TailwindCSS)
│   ├── src/                    # Componentes UI, Páginas, Contextos
│   ├── package.json            # Dependencias y scripts de Node
│   └── vite.config.js          # Configuración del bundler Vite
│
├── backend/                    # Backend REST (Spring Boot 3 + Java 17/24)
│   ├── src/main/java/          # Código fuente (com.saed.backend)
│   │   ├── config/             # Configuración de Seguridad y Base de datos
│   │   ├── identity/           # Autenticación y Autorización
│   │   └── [modulos]/          # Módulos del sistema (person, tenant, finanzas, etc.)
│   ├── src/main/resources/     # application.yml y recursos estáticos
│   └── pom.xml                 # Dependencias Maven
│
├── backend_legacy/             # (Archivado) Backend SAED 1.0 Original
│
├── database/                   # Base de Datos y Migraciones (Oracle 19c)
│   ├── migrations/             # Migraciones incrementales (V3.9 - V4.9)
│   └── docs/                   # Documentación histórica del plan de base de datos
│
├── docs/                       # Documentación Técnica (Fases 1A - 1L)
│
└── scripts/                    # Scripts de utilidad y ejecución
    └── dev/                    # Scripts de arranque y configuración local
```

---

## 🛠️ Pila Tecnológica

- **Frontend:** React 18, Vite 5, TailwindCSS, React Router v6, Radix UI.
- **Backend:** Java 17+ (Compilado para 24), Spring Boot 3, Spring Security, JDBC Template (Sin JPA para mayor control RLS).
- **Base de Datos:** Oracle Database 19c Enterprise / ATP.
- **Seguridad:** Autenticación JWT y modelo Zero-Trust gestionado directamente en la base de datos a través de Oracle RLS (Row-Level Security). No existen filtros de tenencia en la capa Java.

---

## 🚀 Configuración y Ejecución

### Prerrequisitos
- JDK 17 o superior (Recomendado JDK 24)
- Node.js 18+ y npm
- Maven 3.8+

### 1. Variables de Entorno (Backend)
Configura las variables en tu entorno o en `backend/src/main/resources/application.yml`:
```yaml
spring:
  datasource:
    url: ${DB_URL:jdbc:oracle:thin:@localhost:1521/XEPDB1}
    username: ${DB_USERNAME:SAED_V39_FINAL_TEST}
    password: ${DB_PASSWORD}
jwt:
  secret: ${JWT_SECRET:your_secure_jwt_secret_key_here}
```

### 2. Ejecutar el Backend (Spring Boot)
Para levantar el servidor backend de SAED 2.0 en el puerto `8080`:
```bash
cd backend
mvn spring-boot:run
```
*(Alternativamente, puedes usar el script `scripts/dev/iniciar-backend.bat` en Windows)*

### 3. Ejecutar el Frontend (React + Vite)
Para levantar el entorno de desarrollo del frontend en el puerto `5173`:
```bash
cd frontend
npm install
npm run dev
```

Para construir el paquete optimizado de producción:
```bash
npm run build
```
