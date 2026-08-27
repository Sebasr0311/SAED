# SAED 2.0 - Sistema de AdministraciÃ³n de Edificios y Propiedades

[![React](https://img.shields.io/badge/Frontend-React%2018%20%2B%20Vite-blue.svg)](https://react.dev/)
[![Spring Boot](https://img.shields.io/badge/Backend-Spring%20Boot%203-6DB33F.svg)](https://spring.io/projects/spring-boot)
[![Oracle](https://img.shields.io/badge/Database-Oracle%2019c%20RLS-red.svg)](https://www.oracle.com/database/)
[![TailwindCSS](https://img.shields.io/badge/Styles-Tailwind%20CSS-38bdf8.svg)](https://tailwindcss.com/)

> **SAED 2.0** es la evoluciÃ³n de la plataforma para la administraciÃ³n de edificios residenciales, condominios y conjuntos cerrados. Integra control de accesos con cÃ³digos QR, gestiÃ³n contractual, liquidaciÃ³n financiera, y un robusto esquema Zero-Trust apoyado en Oracle Row-Level Security (RLS).

---

## ðŸ—ï¸ Arquitectura y Estructura del Proyecto

El repositorio estÃ¡ organizado en mÃ³dulos desacoplados y bien estructurados bajo la nueva arquitectura de SAED 2.0:

```text
SAED/
â”œâ”€â”€ frontend/                   # Frontend SPA (React 18 + Vite + TailwindCSS)
â”‚   â”œâ”€â”€ src/                    # Componentes UI, PÃ¡ginas, Contextos
â”‚   â”œâ”€â”€ package.json            # Dependencias y scripts de Node
â”‚   â””â”€â”€ vite.config.js          # ConfiguraciÃ³n del bundler Vite
â”‚
â”œâ”€â”€ backend/                    # Backend REST (Spring Boot 3 + Java 17/24)
â”‚   â”œâ”€â”€ src/main/java/          # CÃ³digo fuente (com.saed.backend)
â”‚   â”‚   â”œâ”€â”€ config/             # ConfiguraciÃ³n de Seguridad y Base de datos
â”‚   â”‚   â”œâ”€â”€ identity/           # AutenticaciÃ³n y AutorizaciÃ³n
â”‚   â”‚   â””â”€â”€ [modulos]/          # MÃ³dulos del sistema (person, tenant, finanzas, etc.)
â”‚   â”œâ”€â”€ src/main/resources/     # application.yml y recursos estÃ¡ticos
â”‚   â””â”€â”€ pom.xml                 # Dependencias Maven
â”‚
â”œâ”€â”€ backend_legacy/             # (Archivado) Backend SAED 1.0 Original
â”‚
â”œâ”€â”€ database/                   # Base de Datos y Migraciones (Oracle 19c)
â”‚   â”œâ”€â”€ migrations/             # Migraciones incrementales (V3.9 - V4.9)
â”‚   â””â”€â”€ docs/                   # DocumentaciÃ³n histÃ³rica del plan de base de datos
â”‚
â”œâ”€â”€ docs/                       # DocumentaciÃ³n TÃ©cnica (Fases 1A - 1L)
â”‚
â””â”€â”€ scripts/                    # Scripts de utilidad y ejecuciÃ³n
    â””â”€â”€ dev/                    # Scripts de arranque y configuraciÃ³n local
```

---

## ðŸ› ï¸ Pila TecnolÃ³gica

- **Frontend:** React 18, Vite 5, TailwindCSS, React Router v6, Radix UI.
- **Backend:** Java 17+ (Compilado para 24), Spring Boot 3, Spring Security, JDBC Template (Sin JPA para mayor control RLS).
- **Base de Datos:** Oracle Database 19c Enterprise / ATP.
- **Seguridad:** AutenticaciÃ³n JWT y modelo Zero-Trust gestionado directamente en la base de datos a travÃ©s de Oracle RLS (Row-Level Security). No existen filtros de tenencia en la capa Java.

---

## ðŸš€ ConfiguraciÃ³n y EjecuciÃ³n

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

Para construir el paquete optimizado de producciÃ³n:
```bash
npm run build
```


---

## 📚 Documentación de la API (Swagger / OpenAPI)

SAED 2.0 incluye documentación interactiva y detallada de la API REST gracias a la integración de **OpenAPI 3.0**.

- **Swagger UI:** Cuando el backend esté en ejecución, puedes acceder a la interfaz interactiva navegando a http://localhost:8080/swagger-ui.html.
- **OpenAPI JSON:** Puedes obtener el esquema en crudo desde http://localhost:8080/v3/api-docs.

Para más información, consulta la [Documentación del Backend (BACKEND_API_DOCS.md)](docs/backend/BACKEND_API_DOCS.md).
