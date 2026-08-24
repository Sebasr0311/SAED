# SAED — Sistema de Administración de Edificios y Propiedades

[![React](https://img.shields.io/badge/Frontend-React%2018%20%2B%20Vite-blue.svg)](https://react.dev/)
[![Java](https://img.shields.io/badge/Backend-Java%2017%2F25%20REST-orange.svg)](https://www.oracle.com/java/)
[![Oracle](https://img.shields.io/badge/Database-Oracle%20ATP%2019c-red.svg)](https://www.oracle.com/autonomous-database/)
[![TailwindCSS](https://img.shields.io/badge/Styles-Tailwind%20CSS-38bdf8.svg)](https://tailwindcss.com/)

> **SAED** es una plataforma moderna para la administración de edificios residenciales, condominios y conjuntos cerrados, integrando control de accesos por códigos QR efímeros, gestión contractual, liquidación y recaudo financiero con pasarela Wompi, generación de documentos PDF y módulo de buzón/paquetería.

---

## 🏛️ Arquitectura y Estructura del Proyecto

El repositorio está organizado en módulos desacoplados y bien estructurados:

```
SAED/
├── frontend/                   # Frontend SPA (React 18 + Vite + TailwindCSS)
│   ├── src/                    # Componentes UI, Páginas, AuthContext y Libs
│   ├── public/                 # Recursos públicos y estáticos
│   ├── package.json            # Dependencias y scripts de Node
│   ├── vite.config.js          # Configuración del bundler Vite
│   ├── tailwind.config.js      # Sistema de diseño y tokens de Tailwind
│   └── index.html              # Entry point HTML de la aplicación
│
├── backend/                    # Backend REST (Java 17/25)
│   ├── src/main/java/          # Código fuente Java (com.edificio.admin)
│   │   ├── dao/                # Capa DAO JDBC (Oracle)
│   │   ├── exception/          # Excepciones de dominio
│   │   ├── model/              # Modelos y Enums
│   │   ├── rest/               # Servidor REST, Handlers, Middleware, DTOs, JWT
│   │   ├── service/            # Wompi, Alertas, Generador PDF, EmailService
│   │   └── util/               # Criptografía, ZXing QR, Validadores, WalletSetup
│   ├── src/main/resources/     # Plantillas HTML para contratos PDF y correos
│   ├── pom.xml                 # Dependencias Maven y configuración de build
│   └── Dockerfile              # Empaquetado contenerizado de producción
│
├── database/                   # Base de Datos (Oracle ATP / Oracle 19c)
│   ├── schema/                 # Scripts DDL base del modelo relacional
│   ├── migrations/             # Parches y correcciones incrementales
│   ├── seeds/                  # Semillas y datos de prueba
│   ├── utilities/              # Scripts Java auxiliares (Auditoria, Migracion)
│   └── docs/                   # Documentación técnica de BD
│
├── docs/                       # Documentación Técnica del Proyecto
│   ├── arquitectura/           # Blueprints SAED 2.0, Diagnósticos y DESIGN.md
│   ├── contexto/               # Resumen del proyecto, pantallas y especificaciones
│   ├── diagramas/              # Diagramas PlantUML y arquitectura
│   └── plantillas/             # Plantillas fuente de contratos y correos
│
├── scripts/                    # Scripts de utilidad y ejecución
│   └── dev/                    # Scripts de arranque y configuración local
│
├── render.yaml                 # Manifiesto de despliegue multi-servicio (Render)
├── .env.example                # Plantilla de variables de entorno
└── README.md                   # Documentación principal
```

---

## 🚀 Pila Tecnológica

- **Frontend:** React 18, Vite 5, TailwindCSS, React Router v6, Radix UI Primitives, Lucide Icons, Sonner.
- **Backend:** Java 17/25, Servidor HTTP REST embebido, BCrypt, JWT (HMAC-SHA256), ZXing (QR), OpenHTMLtoPDF (Flying Saucer).
- **Base de Datos:** Oracle Database 19c Enterprise / Oracle Autonomous Database (ATP) con soporte de Oracle Wallet Mutual TLS (TCPS).
- **Integraciones:** Pasarela de Pagos Wompi (Webhooks con checksum SHA-256), Brevo HTTPS API (Emails transaccionales).

---

## ⚙️ Configuración y Ejecución

### Prerrequisitos
- JDK 17 o superior
- Node.js 18+ y npm / pnpm
- Maven 3.8+

### 1. Variables de Entorno (Backend)
Configura las variables en tu entorno o en `backend/.env`:
```env
PORT=8080
DB_URL=jdbc:oracle:thin:@residencial_high
DB_USER=RESIDENCIAL
DB_PASS=tu_password_oracle
JWT_SECRET=tu_clave_secreta_jwt
BREVO_API_KEY=tu_api_key_brevo
WOMPI_PUBLIC_KEY=tu_public_key_wompi
WOMPI_INTEGRITY_SECRET=tu_integrity_secret_wompi
WOMPI_EVENTS_SECRET=tu_events_secret_wompi
```

### 2. Ejecutar el Backend (Java)
```bash
cd backend
mvn compile exec:java -Dexec.mainClass="com.edificio.admin.RestServerMain"
```

### 3. Ejecutar el Frontend (React)
```bash
cd frontend
npm install
npm run dev
```
La aplicación web estará disponible en `http://localhost:5173`.

---

## 📄 Documentación Adicional

- [Diagnóstico de Migración SAED 2.0](docs/arquitectura/SAED_2_0_DIAGNOSTICO_MIGRACION.md)
- [Blueprint Relacional SAED 2.0 (v1)](docs/arquitectura/SAED_2_0_MODELO_RELACIONAL_BLUEPRINT_V1.md)
- [Documentación de Base de Datos](docs/contexto/DOCUMENTACION_BASE_DATOS.txt)
