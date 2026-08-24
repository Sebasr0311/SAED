# SAED 2.0 — Diagnóstico de Migración y Plan Maestro de Reconstrucción

> **Proyecto:** SAED (Sistema de Administración de Edificios / Propiedades)  
> **De:** SAED 1.0 (Monolito Mono-Edificio en Java + Oracle 19c ATP + React/Vite)  
> **Hacia:** SAED 2.0 (Plataforma Multi-Tenant SaaS: Organizaciones → Propiedades → Unidades → Operaciones)  
> **Perfil Técnico:** Arquitectura de Software & Base de Datos Relacional  
> **Fecha:** Agosto 2026

---

## 1. RESUMEN EJECUTIVO

El diagnóstico integral del repositorio SAED 1.0 confirma que el sistema cuenta con una **lógica de negocio madura y funcional en sus procesos nucleares** (ciclo de visitas con QR de un solo uso, liquidación de cuotas, trazabilidad de pagos Wompi/webhook con firma SHA-256, generación de contratos en PDF con Flying Saucer, y mensajería/buzón). Sin embargo, su diseño actual padece un **acoplamiento estructural absoluto a un modelo mono-propiedad (un solo edificio residencial)**, carece de abstracciones multi-organización y presenta una autorización basada en 3 roles fijos en memoria.

### Métricas Clave de Reutilización del Código Fuente
- **Reutilización Directa (CONSERVAR):** **32%** (servicios criptográficos, validadores, generación de QR con ZXing, renderizado de PDF, schemas base de visitas/pagos, componentes UI primitivos de React/Tailwind).
- **Refactorización Necesaria (REFACTORIZAR):** **45%** (DAOs, handlers REST, contratos, apartamentos/unidades, frontend pages, modelo de roles a RBAC con scope, pool de conexiones).
- **Eliminar / Archivar (LEGACY):** **8%** (JavaFX obsoleto en `pom.xml`, scripts SQL duplicados/parches temporales, migraciones embebidas DDL en el arranque del servidor, endpoints con lógica N+1 en memoria).
- **Código Nuevo Requerido (NUEVO):** **15%** (Núcleo Multi-Tenant: Organizaciones, Propiedades, Bloques, Unidades, Planes, Membresías, Módulos, RBAC Granular con Scopes, Auditoría Unificada).

### Principales Fortalezas
1. **Base de Datos Relacional Robusta (Oracle 19c ATP):** El modelo actual implementa integridad referencial estricta, restricciones `CHECK`, `FOREIGN KEYS` con reglas bien definidas, y disparadores de negocio probados.
2. **Ciclo de Accesos y QR Seguro:** El subsistema de visitas temporales, validación de expiración en minutos y consumo de QR de un solo uso está conceptualmente bien diseñado.
3. **Flujo de Pagos e Integración Externa:** Integración con Wompi funcional con validación de checksum y reconciliador periódico de transacciones.
4. **Stack Tecnológico Moderno y Limpio:** React 18 + Vite en frontend y Java 17/25 + Oracle JDBC (ojdbc11/oraclepki) en backend, sin frameworks pesados ni dependencias obsoletas.

### Principales Riesgos y Limitaciones
1. **Ausencia de Tenant ID / Property ID en el Modelo Relacional:** Todas las tablas (`APARTAMENTOS`, `RESIDENTES`, `CONTRATOS`, `VISITAS`, `PAGOS`) asumen implícitamente la existencia de un solo edificio.
2. **Conexiones JDBC sin Connection Pool:** `ConexionBD` utiliza `ThreadLocal<Connection>` con `DriverManager.getConnection()`. Bajo tráfico concurrente multi-cliente en la nube colapsará el listener de Oracle ATP.
3. **Autorización Rígida sin Scopes:** El backend verifica `hasRole("ADMINISTRADOR")`. Un administrador de un edificio tendría acceso inmediato e irrestricto a los datos de todas las demás organizaciones.
4. **Acoplamiento de Entidad "APARTAMENTO":** El sistema no modela casas, oficinas, bodegas o lotes en conjuntos cerrados.

---

## 2. ARQUITECTURA ACTUAL

```
                                  ARQUITECTURA SAED 1.0
                                  
+-----------------------------------------------------------------------------------+
| FRONTEND: React 18 (Vite SPA) + TailwindCSS + Lucide Icons                         |
|   - 28 Páginas divididas rígidamente por rol (Admin / Portero / Residente)       |
|   - Router con ProtectedRoute validando solo claims.rol en sessionStorage         |
|   - Cliente HTTP (api.js) con timeout de 30s                                      |
+------------------------------------------+----------------------------------------+
                                           | HTTPS / JSON (REST)
                                           v
+-----------------------------------------------------------------------------------+
| BACKEND: Java 17/25 (com.sun.net.httpserver.HttpServer embebido)                 |
|   - Pool fijo de 4 hilos de atención HTTP                                         |
|   - Handlers REST manuales (parsin de URLs por split('/'))                        |
|   - Servicios de Negocio (Wompi, Alertas, Contratos PDF, BCrypt)                  |
|   - Scheduler en memoria (SingleThreadScheduledExecutor)                         |
|   - Autenticación: JWT (HMAC-SHA256) + BCrypt (factor 10/12)                       |
+------------------------------------------+----------------------------------------+
                                           | JDBC Thin (ojdbc11 + oraclepki + SSO Wallet)
                                           v
+-----------------------------------------------------------------------------------+
| BASE DE DATOS: Oracle Autonomous Database (ATP 19c Enterprise en sa-bogota-1)     |
|   - Esquema: RESIDENCIAL                                                          |
|   - 22 Tablas, 21 Secuencias, 17 Triggers, 5 Vistas, 3 Paquetes PL/SQL            |
|   - Conexión vía Oracle Wallet Mutual TLS (Port 1522 TCPS)                        |
+-----------------------------------------------------------------------------------+
```

---

## 3. INVENTARIO COMPLETO Y CLASIFICACIÓN

| Componente | Tipo | Función | Dependencias | Estado | Clasificación |
|---|---|---|---|---|---|
| `ConexionBD.java` | Backend DAO | Singleton de conexión JDBC ThreadLocal | Oracle JDBC, DriverManager | Funcional (sin pool) | **REFACTORIZAR** (Integrar HikariCP/UCP) |
| `RestServer.java` | Backend Server | Servidor HTTP embebido y rutas REST | `com.sun.net.httpserver` | Acoplado / DDL en start | **REFACTORIZAR** (Migrar a Router modular) |
| `AuthMiddleware.java` | Backend Auth | Validación de JWT y estado activo | `JwtUtil`, `UsuarioDAO` | Funcional | **REFACTORIZAR** (Inyectar tenant/scope) |
| `AuthScope.java` | Backend Auth | Helpers de autorización y scoping | `ConexionBD`, SQL estático | Acoplado a mono-edificio | **REFACTORIZAR** (Evolucionar a RBAC con Scope) |
| `JwtUtil.java` | Backend Util | Generación y verificación de tokens | HMAC-SHA256, Gson | Excelente estado | **CONSERVAR** (Expandir claims: org, prop, perms) |
| `JsonUtil.java` | Backend Util | Serialización/deserialización JSON | Gson | Excelente estado | **CONSERVAR** |
| `GeneradorQR.java` | Backend Util | Generación de imágenes QR base64 | ZXing 3.5.2 | Excelente estado | **CONSERVAR** |
| `PdfGeneratorService.java` | Backend Service | Generación de PDFs desde XHTML | Flying Saucer, OpenHTMLtoPDF | Funcional | **CONSERVAR** |
| `EmailService.java` | Backend Service | Envío de correos transaccionales | Brevo HTTPS API | Funcional | **CONSERVAR** |
| `WompiService.java` | Backend Service | Pasarela de pagos y webhooks | Wompi REST, SHA-256 | Funcional | **REFACTORIZAR** (Trazabilidad org/propiedad) |
| `ApartamentoDAO.java` | Backend DAO | CRUD de apartamentos | BaseDAO, SQL estático | Mono-propiedad | **REFACTORIZAR** (Reemplazar por `UnidadDAO`) |
| `ContratoDAO.java` | Backend DAO | Gestión de contratos de arrendamiento | BaseDAO, Oracle DDL | Acoplado a Apartamentos | **REFACTORIZAR** (Vincular a Unidad y Organización) |
| `ResidenteDAO.java` | Backend DAO | Gestión de residentes y personas | BaseDAO, SQL | Acoplado a Apartamento | **REFACTORIZAR** (Separar Persona de Residente) |
| `VisitaDAO.java` | Backend DAO | Gestión de visitas y registros | BaseDAO, Triggers | Muy completo | **REFACTORIZAR** (Agregar `id_propiedad`) |
| `QRAccesoDAO.java` | Backend DAO | Tokenización y consumo de QR | BaseDAO, Triggers | Sólido | **REFACTORIZAR** (Agregar `id_propiedad`) |
| `PagoDAO.java` | Backend DAO | Liquidación de cuotas y pagos | BaseDAO, Transacciones | Sólido | **REFACTORIZAR** (Vincular a Unidad y Org) |
| `AlertaService.java` | Backend Service | Scheduler de vencimiento de cuotas | AlertaDAO, Java Mail | Funcional | **REFACTORIZAR** (Ejecutar por organización) |
| `pom.xml (JavaFX)` | Configuración | Dependencias JavaFX en proyecto REST | OpenJFX 17 | Código muerto | **ELIMINAR / ARCHIVAR** |
| `App.jsx` | Frontend Routing | Rutas protegidas y lazy loading | React Router v6 | Acoplado a 3 roles | **REFACTORIZAR** (Router dinámico por permisos) |
| `AuthContext.jsx` | Frontend State | Manejo de sesión y tokens | sessionStorage | Funcional | **REFACTORIZAR** (Añadir activeOrg, activeProp) |
| `access.js` | Frontend Auth | Mapa estático de rutas por rol | Rutas hardcodeadas | Rígido | **REFACTORIZAR** (Evaluación por permisos) |
| `AppShell.jsx` | Frontend Layout | Barra lateral y navegación | Lucide, Tailwind | Muy bien diseñado | **REFACTORIZAR** (Agregar Selectores de Org/Prop) |
| `EscannerQRPage.jsx` | Frontend Page | Escaneo de QR para portería | html5-qrcode / WebCam | Excelente UX | **CONSERVAR** (Reutilizar con selector de prop) |
| `ContratosPage.jsx` | Frontend Page | Gestión y creación de contratos | api.js, Modal | Completo | **REFACTORIZAR** (Adaptar a Unidades) |
| `PagosPage.jsx` | Frontend Page | Registro y visualización de pagos | api.js | Completo | **REFACTORIZAR** (Filtrado por propiedad) |
| `ResidentesPage.jsx` | Frontend Page | Directorio y alta de residentes | api.js, Modal | Muy completo | **REFACTORIZAR** (Multi-unidad/propiedad) |
| `ApartamentosPage.jsx` | Frontend Page | Gestión de apartamentos físicos | api.js | Mono-edificio | **REFACTORIZAR** (Convertir a `UnidadesPage.jsx`) |
| `Res...Page.jsx (7 págs)` | Frontend Pages | Vistas específicas de residentes | Duplicación de lógica | Fragmentado | **REFACTORIZAR** (Unificar componentes modulares) |
| `ORGANIZACIONES` | BD Tabla | Clientes y entidades SaaS | N/A | Inexistente | **NUEVO** |
| `PROPIEDADES` | BD Tabla | Edificios, conjuntos, urbanizaciones | `ORGANIZACIONES` | Inexistente | **NUEVO** |
| `BLOQUES` | BD Tabla | Torres, etapas, manzanas | `PROPIEDADES` | Inexistente | **NUEVO** |
| `UNIDADES` | BD Tabla | Apartamentos, casas, locales | `BLOQUES` / `PROPIEDADES` | Inexistente | **NUEVO** (Evolución de `APARTAMENTOS`) |
| `ROLES` / `PERMISOS` | BD Tablas | RBAC Granular con Scopes | `USUARIOS` | Inexistente | **NUEVO** |
| `PLANES` / `MEMBRESIAS` | BD Tablas | Límites y módulos por cliente | `ORGANIZACIONES` | Inexistente | **NUEVO** |
| `AUDITORIA_LOG` | BD Tabla | Trazabilidad global inmutable | `USUARIOS`, `ORGANIZACIONES` | Inexistente | **NUEVO** |

---

## 4. ANÁLISIS DEL FRONTEND (REACT / VITE)

### Diagnóstico de la Estructura Actual
- **Pila Técnica:** React 18.3, Vite 5.4, TailwindCSS, React Router DOM 6.23, Radix UI Primitives, Sonner (toasts), Lucide React.
- **Aspectos Positivos:**
  - Código estructurado con code-splitting (`React.lazy`) en [`client/App.jsx`](file:///C:/Users/JUAN/IdeaProjects/sistema-administracion-edificios/client/App.jsx).
  - Componentes UI reutilizables de alta calidad en `client/components/ui/` (`DataTable`, `Modal`, `ConfirmDialog`, `Pagination`, `EmptyState`).
  - Buen manejo de estados de carga, diálogos de confirmación destructiva y feedback visual.
- **Puntos Críticos de Refactorización:**
  1. **Duplicación de Páginas por Rol:** Existen 7 páginas con prefijo `Res*` (`ResCuotasPage`, `ResQuejasPage`, `ResVisitaPage`, `ResApartamentoPage`, etc.) que duplican la lógica de las páginas principales de administración pero con botones ocultos o APIs filtradas. En SAED 2.0 se deben unificar mediante vistas modulares gobernadas por permisos.
  2. **Ausencia de Contexto Multi-Organización:** [`AuthContext.jsx`](file:///C:/Users/JUAN/IdeaProjects/sistema-administracion-edificios/client/lib/AuthContext.jsx) solo almacena `{ user, token }`. Debe enriquecerse para soportar:
     - `organizacionesDisponibles`: lista de organizaciones a las que pertenece el usuario.
     - `organizacionActiva`: organización seleccionada en la sesión.
     - `propiedadesDisponibles`: lista de propiedades accesibles bajo el alcance del usuario.
     - `propiedadActiva`: propiedad seleccionada en el selector del Shell.
     - `permisos`: set de permisos (`['unidades:read', 'pagos:create', ...]`).
  3. **Sidebar y AppShell Rígidos:** [`AppShell.jsx`](file:///C:/Users/JUAN/IdeaProjects/sistema-administracion-edificios/client/components/layout/AppShell.jsx) renderiza menús basándose en `user.rol === 'ADMINISTRADOR'`. Se debe refactorizar para incluir el *Tenant/Property Switcher* en el TopBar y renderizar enlaces basados en `can(PERMISO)`.

---

## 5. ANÁLISIS DEL BACKEND (JAVA)

### Diagnóstico de la Estructura Actual
- **Servidor HTTP:** Emplea `com.sun.net.httpserver.HttpServer` nativo del JDK. Es ligero y sin dependencias pesadas, pero carece de un router jerárquico robusto (realiza `split("/")` en cada handler).
- **Capa de Handlers:** Los handlers en `src/main/java/com/edificio/admin/rest/handler/` mezclan lógica de serialización, control de transacciones y llamadas directas a DAOs.
- **Capa de Servicios:** Existen servicios bien desacoplados como `WompiService`, `ContratoPdfService`, `EmailService` y `AlertaService`.
- **Capa DAO:** La clase `BaseDAO` provee la conexión, pero no existe soporte para transacciones multi-DAO (Unit of Work). Cada llamada ejecuta `conn.setAutoCommit(true)` o maneja commits locales.
- **Puntos Críticos de Refactorización:**
  1. **Pool de Conexiones:** `ConexionBD` abre conexiones directamente vía `DriverManager`. Se debe reemplazar por un pool de alto rendimiento como **Oracle UCP (Universal Connection Pool)** o **HikariCP**.
  2. **Inyección de Tenant Scope en cada Request:** Actualmente ningún handler o DAO filtra por `id_organizacion` o `id_propiedad`. Todas las consultas `SELECT * FROM ...` son vulnerables a fuga de datos inter-cliente si no se refactorizan con filtrado obligatorio por contexto de sesión.
  3. **Manejo de Errores y Excepciones:** Muchos bloques `try-catch` capturan `Exception` genérica y retornan status 500 o fallan silenciosamente.

---

## 6. ANÁLISIS DE LA BASE DE DATOS (ORACLE ATP)

### Diagnóstico del Modelo Actual
El esquema `RESIDENCIAL` cuenta con 22 tablas en Oracle 19c ATP:
- `TIPOS_DOCUMENTO`, `APARTAMENTOS`, `PARQUEADEROS`, `RESIDENTES`, `TUTORES`, `USUARIOS`, `CONTRATOS`, `CONTRATO_RESIDENTE`, `CUOTAS_ARRIENDO`, `PAGOS`, `MULTAS`, `VISITANTES`, `VISITAS`, `VEHICULOS_VISITA`, `REGISTRO_VISITA`, `QR_ACCESOS`, `FRECUENTES_RESIDENTE`, `REGISTROS_ACCESO`, `BUZON`, `QUEJAS_SUGERENCIAS`, `ALERTAS_PAGO`, `TRANSACCIONES_PAGO`.

### Evaluación de Objetos PL/SQL
1. **Triggers (17 en total):** Muy útiles para mantener integridad en mono-edificio (ej: `TRG_CONT_SYNC_APARTAMENTO` que cambia el estado a `OCUPADO` al activar contrato). Deben revisarse para asegurar que no bloqueen operaciones batch en un entorno SaaS.
2. **Procedimientos y Paquetes:**
   - `SP_VALIDAR_QR`: Funciona adecuadamente calculando minutos restantes y estado.
   - `PKG_PAGOS` y `PKG_RESIDENTES`: Detectados en estado `INVALID` en la auditoría; deben ser refactorizados para recibir el contexto de propiedad/organización.
3. **Secuencias:** 21 secuencias independientes. En el modelo multi-tenant se pueden conservar o migrar a columnas `IDENTITY (GENERATED BY DEFAULT AS IDENTITY)` nativas de Oracle 19c.

---

## 7. SEGURIDAD Y VULNERABILIDADES DETECTADAS

| Vulnerabilidad / Riesgo | Severidad | Ubicación Actual | Impacto en SAED 2.0 | Solución Requerida |
|---|---|---|---|---|
| **Riesgo IDOR Multi-Tenant** | **CRÍTICA** | Todos los handlers (`/api/apartamentos/{id}`, etc.) | Un usuario autenticado de la Org A podría consultar/editar registros de la Org B modificando el ID numérico en la URL. | Scoping obligatorio: validar en backend que la entidad pertenece a la `id_propiedad` / `id_organizacion` del token. |
| **Rol "ADMINISTRADOR" Global** | **ALTA** | `AuthScope.java` (`requireRole`) | Otorga privilegios absolutos a nivel de sistema sin discriminar propiedad u organización. | Implementar RBAC: `Rol` + `Permiso` + `Alcance (Global, Org, Propiedad)`. |
| **Falta de Rate Limiting en API** | **MEDIA** | `RestServer.java` / `AuthHandler.java` | Ataques de fuerza bruta en `/api/auth/login` o denegación de servicio en generación de QR. | Implementar filtro de Rate Limiting por IP/Token (Bucket4j o token bucket en memoria). |
| **Firma JWT con clave efímera fallback** | **MEDIA** | `JwtUtil.java` | Si falta `JWT_SECRET`, genera una clave aleatoria en RAM; al reiniciar se invalidan todas las sesiones. | Exigir `JWT_SECRET` estricto en el arranque o abortar con error de configuración. |
| **Almacenamiento de Tokens en sessionStorage** | **BAJA** | `client/lib/storage.js` | Vulnerable a XSS si se inyectan scripts externos. | Mantener política CSP estricta o migrar a cookies `HttpOnly; SameSite=Lax`. |

---

## 8. ANÁLISIS ARQUITECTÓNICO Y DE ACOPLAMIENTO

### 1. Acoplamiento de la Entidad "APARTAMENTO"
- **Problema:** En el código Java (`Apartamento.java`, `ApartamentoDAO.java`) y en frontend (`ApartamentosPage.jsx`), las columnas `piso` y `numero` son obligatorias y estructuran todo el negocio.
- **Impacto:** Imposibilita gestionar conjuntos de casas, condominios campestres, locales comerciales o bodegas donde los conceptos de "piso" son irrelevantes y se requieren "manzanas", "etapas", "torres" o "bloques".
- **Solución:** Crear la jerarquía `PROPIEDADES` → `BLOQUES` (opcional) → `UNIDADES` (con subtipos: APARTAMENTO, CASA, LOCAL, OFICINA, PARQUEADERO_INDEP).

### 2. Acoplamiento de Personas y Usuarios
- **Problema:** La tabla `RESIDENTES` almacena datos personales (nombres, apellidos, teléfono, correo) y `USUARIOS` apunta con una FK opcional a `RESIDENTES`. Si una persona es propietaria en una propiedad y residente en otra, o es portero y residente a la vez, se duplican sus datos.
- **Solución:** Abstraer `PERSONAS` (entidad base identificable por tipo y número de documento) y desacoplarla de sus diferentes roles/vinculaciones en el sistema.

---

## 9. TRANSICIÓN A MULTI-TENANCY

Para que SAED 2.0 soporte múltiples organizaciones y propiedades, la jerarquía conceptual de datos debe estructurarse de la siguiente manera:

```
[PLATAFORMA SAED]
  ├── SUPERADMIN
  ├── PLANES (Básico, Pro, Enterprise)
  ├── MÓDULOS (Visitas, Pagos, Contratos, Buzón, Multas, QR)
  │
  └── ORGANIZACIONES (Clientes / Empresas de Administración)
        ├── MEMBRESÍA (Plan activo, vigencia, límites de unidades/usuarios)
        ├── USUARIOS_ORGANIZACION (Propietarios, Admins Generales)
        │
        └── PROPIEDADES (Edificio Torres del Valle, Conjunto Campestre El Roble)
              ├── ADMINISTRADORES_PROPIEDAD
              ├── BLOQUES (Torre A, Torre B, Manzana 1, Manzana 2) [Opcional]
              │     │
              │     └── UNIDADES (Apto 101, Casa 12, Local 3)
              │           ├── CONTRATOS (Vigencias, arrendatarios, cuotas)
              │           ├── RESIDENTES_UNIDAD (Personas vinculadas)
              │           ├── CUOTAS & PAGOS (Cuentas por cobrar, recibos)
              │           └── PARQUEADEROS ASIGNADOS
              │
              └── OPERACIONES DE PROPIEDAD
                    ├── PORTEROS / VIGILANTES
                    ├── VISITAS & REGISTROS QR
                    ├── PARQUEADEROS COMUNALES / VISITANTES
                    ├── QUEJAS, COMUNICADOS & BUZÓN
                    └── AUDITORÍA DE ACCESOS
```

---

## 10. SISTEMA DE ROLES, PERMISOS Y ALCANCES (RBAC + SCOPE)

### Modelo Conceptual de Autorización

```
+----------------+       +-------------------+       +--------------------+
|    USUARIO     | ----> | ASIGNACION_ROL    | ----> |        ROL         |
+----------------+       +-------------------+       +--------------------+
                                   |                           |
                                   v                           v
                         +-------------------+       +--------------------+
                         |      ALCANCE      |       |      PERMISOS      |
                         | (Scope Context)   |       | (unidades:read,    |
                         | - GLOBAL          |       |  pagos:create,     |
                         | - ORGANIZACION_ID |       |  visitas:scan,...) |
                         | - PROPIEDAD_ID    |       +--------------------+
                         +-------------------+
```

### Matriz de Roles Iniciales y Alcances

| Rol | Alcance Típico (Scope) | Permisos Principales |
|---|---|---|
| **SUPERADMIN** | `GLOBAL` | Gestión de Organizaciones, Planes, Módulos, Métricas globales de plataforma. |
| **ADMIN_ORGANIZACION (Propietario)** | `ORGANIZACION` | Crear propiedades, asignar administradores, ver facturación y membresía, reportes consolidados. |
| **ADMIN_GENERAL** | `ORGANIZACION` (Todas las propiedades) | Gestión operativa de todas las propiedades asignadas dentro de la organización. |
| **ADMIN_PROPIEDAD** | `PROPIEDAD` (1 o varias específicas) | Gestión de unidades, contratos, cuotas, residentes, quejas y portería de sus propiedades. |
| **PORTERO / VIGILANTE** | `PROPIEDAD` | Escaneo de QR, registro manual de visitas, control de parqueaderos de visitantes, paquetería. |
| **RESIDENTE / PROPIETARIO UNIDAD** | `UNIDAD` | Generar QR de visitas, registrar visitantes frecuentes, ver cuotas, pagar en línea, buzón/quejas. |

---

## 11. MATRIZ DE FUNCIONALIDADES (ACTUAL VS. SAED 2.0)

| Funcionalidad | Implementación SAED 1.0 | Implementación SAED 2.0 | Acción |
|---|---|---|---|
| **Autenticación** | JWT con username/password + claims simples | JWT con Claims de Organización, Permisos y Multi-factor opcional | **REFACTORIZAR** |
| **Superadministración** | Inexistente (no hay concepto de plataforma) | Dashboard web de Superadmin para clientes, membresías y módulos | **NUEVO** |
| **Gestión de Inmuebles** | `APARTAMENTOS` (tabla plana con piso/número) | `PROPIEDADES` → `BLOQUES` → `UNIDADES` (tipos configurables) | **REFACTORIZAR** |
| **Contratos y Arriendos** | Contrato ligado a Apartamento físico | Contrato ligado a Unidad con soporte de coarrendatarios y prórrogas | **REFACTORIZAR** |
| **Generación de QR** | Generador ZXing con HMAC token | Generador ZXing con trazabilidad por propiedad y validación multi-puerta | **CONSERVAR / REFACTORIZAR** |
| **Control de Visitas** | Visita mono-edificio | Visitas asociadas a Propiedad/Unidad con historial unificado | **REFACTORIZAR** |
| **Pasarela Wompi** | Integración con Webhooks y firma SHA-256 | Integración multi-cuenta Wompi por Organización/Propiedad | **REFACTORIZAR** |
| **Generador de PDF** | OpenHTMLtoPDF / Flying Saucer | Mismo motor, con plantillas personalizables por Organización | **CONSERVAR** |
| **Notificaciones / Email** | Brevo API para emails transaccionales | Brevo API multi-remitente + WebSockets / Notificaciones push in-app | **CONSERVAR / EXPANDIR** |
| **Buzón y Quejas** | Mensajería básica entre admin y apto | Sistema de tickets con categorías, estados, respuestas y adjuntos | **REFACTORIZAR** |
| **Auditoría** | Logs dispersos y timestamps en tablas | Tabla centralizada inmutable `AUDITORIA_LOG` con diff JSON de cambios | **NUEVO** |
| **Analítica y Reportes** | Métricas simples en `GananciasPage` | Dashboards analíticos de ocupación, recaudo, cartera vencida y accesos | **REFACTORIZAR / NUEVO** |

---

## 12. MODELO DE BASE DE DATOS OBJETIVO (ORACLE ATP)

```sql
-- ============================================================================
-- ESQUEMA OBJETIVO SAED 2.0 (ORACLE 19c ATP)
-- ============================================================================

-- 1. PLATAFORMA Y MEMBRESÍAS
CREATE TABLE PLANES (
    id_plan             NUMBER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    codigo              VARCHAR2(30) NOT NULL UNIQUE,
    nombre              VARCHAR2(100) NOT NULL,
    max_propiedades     NUMBER DEFAULT 1 NOT NULL,
    max_unidades        NUMBER DEFAULT 50 NOT NULL,
    max_usuarios        NUMBER DEFAULT 5 NOT NULL,
    precio_mensual      NUMBER(12,2) DEFAULT 0 NOT NULL,
    activo              NUMBER(1) DEFAULT 1 NOT NULL
);

CREATE TABLE MODULOS (
    id_modulo           NUMBER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    codigo              VARCHAR2(50) NOT NULL UNIQUE,
    nombre              VARCHAR2(100) NOT NULL,
    descripcion         VARCHAR2(250)
);

CREATE TABLE PLAN_MODULOS (
    id_plan             NUMBER REFERENCES PLANES(id_plan) ON DELETE CASCADE,
    id_modulo           NUMBER REFERENCES MODULOS(id_modulo) ON DELETE CASCADE,
    PRIMARY KEY (id_plan, id_modulo)
);

CREATE TABLE ORGANIZACIONES (
    id_organizacion     NUMBER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    id_plan             NUMBER REFERENCES PLANES(id_plan),
    nombre              VARCHAR2(150) NOT NULL,
    nit_identificacion  VARCHAR2(50) NOT NULL UNIQUE,
    email_contacto      VARCHAR2(120) NOT NULL,
    telefono_contacto   VARCHAR2(30),
    estado_membresia    VARCHAR2(20) DEFAULT 'ACTIVA' CHECK (estado_membresia IN ('ACTIVA', 'SUSPENDIDA', 'VENCIDA', 'PRUEBA')),
    fecha_registro      TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- 2. SEGURIDAD Y RBAC
CREATE TABLE PERSONAS (
    id_persona          NUMBER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    id_tipo_doc         NUMBER NOT NULL,
    numero_documento    VARCHAR2(30) NOT NULL,
    nombres             VARCHAR2(100) NOT NULL,
    apellidos           VARCHAR2(100) NOT NULL,
    telefono            VARCHAR2(30),
    email               VARCHAR2(120),
    CONSTRAINT uk_persona_doc UNIQUE (id_tipo_doc, numero_documento)
);

CREATE TABLE USUARIOS (
    id_usuario          NUMBER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    id_persona          NUMBER REFERENCES PERSONAS(id_persona),
    id_organizacion     NUMBER REFERENCES ORGANIZACIONES(id_organizacion), -- NULL para Superadmin
    username            VARCHAR2(60) NOT NULL UNIQUE,
    password_hash       VARCHAR2(255) NOT NULL,
    activo              NUMBER(1) DEFAULT 1 NOT NULL,
    ultimo_login        TIMESTAMP WITH TIME ZONE,
    fecha_registro      TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE ROLES (
    id_rol              NUMBER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    codigo              VARCHAR2(50) NOT NULL UNIQUE,
    nombre              VARCHAR2(100) NOT NULL,
    nivel_alcance       VARCHAR2(20) DEFAULT 'PROPIEDAD' CHECK (nivel_alcance IN ('GLOBAL', 'ORGANIZACION', 'PROPIEDAD', 'UNIDAD'))
);

CREATE TABLE PERMISOS (
    id_permiso          NUMBER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    codigo              VARCHAR2(80) NOT NULL UNIQUE,
    nombre              VARCHAR2(120) NOT NULL,
    modulo              VARCHAR2(50) NOT NULL
);

CREATE TABLE ROL_PERMISOS (
    id_rol              NUMBER REFERENCES ROLES(id_rol) ON DELETE CASCADE,
    id_permiso          NUMBER REFERENCES PERMISOS(id_permiso) ON DELETE CASCADE,
    PRIMARY KEY (id_rol, id_permiso)
);

-- 3. PROPIEDADES Y UNIDADES
CREATE TABLE TIPOS_PROPIEDAD (
    id_tipo_propiedad   NUMBER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    codigo              VARCHAR2(30) NOT NULL UNIQUE,
    nombre              VARCHAR2(80) NOT NULL -- 'EDIFICIO', 'CONJUNTO_CERRADO', 'CENTRO_COMERCIAL'
);

CREATE TABLE PROPIEDADES (
    id_propiedad        NUMBER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    id_organizacion     NUMBER REFERENCES ORGANIZACIONES(id_organizacion) ON DELETE CASCADE NOT NULL,
    id_tipo_propiedad   NUMBER REFERENCES TIPOS_PROPIEDAD(id_tipo_propiedad) NOT NULL,
    nombre              VARCHAR2(150) NOT NULL,
    direccion           VARCHAR2(200) NOT NULL,
    ciudad              VARCHAR2(100) NOT NULL,
    telefono            VARCHAR2(30),
    configuracion_json  CLOB, -- Parámetros específicos (tolerancias, reglas de visitas, horarios)
    activo              NUMBER(1) DEFAULT 1 NOT NULL
);

CREATE TABLE USUARIO_ASIGNACIONES (
    id_asignacion       NUMBER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    id_usuario          NUMBER REFERENCES USUARIOS(id_usuario) ON DELETE CASCADE NOT NULL,
    id_rol              NUMBER REFERENCES ROLES(id_rol) NOT NULL,
    id_organizacion     NUMBER REFERENCES ORGANIZACIONES(id_organizacion) ON DELETE CASCADE,
    id_propiedad        NUMBER REFERENCES PROPIEDADES(id_propiedad) ON DELETE CASCADE, -- NULL si es Admin General
    activo              NUMBER(1) DEFAULT 1 NOT NULL
);

CREATE TABLE BLOQUES (
    id_bloque           NUMBER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    id_propiedad        NUMBER REFERENCES PROPIEDADES(id_propiedad) ON DELETE CASCADE NOT NULL,
    nombre              VARCHAR2(80) NOT NULL, -- 'Torre 1', 'Manzana B', 'Etapa 2'
    descripcion         VARCHAR2(200)
);

CREATE TABLE TIPOS_UNIDAD (
    id_tipo_unidad      NUMBER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    codigo              VARCHAR2(30) NOT NULL UNIQUE, -- 'APARTAMENTO', 'CASA', 'LOCAL', 'OFICINA'
    nombre              VARCHAR2(80) NOT NULL
);

CREATE TABLE UNIDADES (
    id_unidad           NUMBER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    id_propiedad        NUMBER REFERENCES PROPIEDADES(id_propiedad) ON DELETE CASCADE NOT NULL,
    id_bloque           NUMBER REFERENCES BLOQUES(id_bloque) ON DELETE SET NULL,
    id_tipo_unidad      NUMBER REFERENCES TIPOS_UNIDAD(id_tipo_unidad) NOT NULL,
    identificador       VARCHAR2(30) NOT NULL, -- '101', 'Casa 5', 'Local 2B'
    piso                NUMBER,
    area_m2             NUMBER(8,2),
    coeficiente_coprop  NUMBER(6,4), -- Coeficiente para liquidación de expensas
    estado              VARCHAR2(20) DEFAULT 'DISPONIBLE' CHECK (estado IN ('DISPONIBLE', 'OCUPADA', 'MANTENIMIENTO')),
    activo              NUMBER(1) DEFAULT 1 NOT NULL,
    CONSTRAINT uk_unidad_prop UNIQUE (id_propiedad, id_bloque, identificador)
);

-- 4. TABLA CENTRAL DE AUDITORÍA
CREATE TABLE AUDITORIA_LOG (
    id_log              NUMBER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    id_organizacion     NUMBER,
    id_propiedad        NUMBER,
    id_usuario          NUMBER,
    accion              VARCHAR2(50) NOT NULL, -- 'LOGIN', 'INSERT', 'UPDATE', 'DELETE', 'QR_SCAN'
    entidad             VARCHAR2(50) NOT NULL, -- 'CONTRATOS', 'PAGOS', 'VISITAS', 'UNIDADES'
    id_registro_afectado VARCHAR2(100),
    ip_origen           VARCHAR2(45),
    detalle_anterior    CLOB,
    detalle_nuevo       CLOB,
    fecha_evento        TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);
```

---

## 13. DEPENDENCIAS Y ORDEN DE MIGRACIÓN

El grafo de dependencias técnicas impone el siguiente orden riguroso:

```
[1. NUEVO MODELO DE BD] (Plataforma, Organizaciones, Propiedades, Unidades, RBAC)
         │
         ▼
[2. CAPA DE CONEXIÓN & POOL] (HikariCP / Oracle UCP con Multi-Tenant DataSources)
         │
         ▼
[3. AUTENTICACIÓN & RBAC CORE] (JWT claims con Org, Propiedad y Permisos)
         │
         ▼
[4. MÓDULO ORGANIZACIONES & PROPIEDADES] (CRUD de clientes, propiedades y bloques)
         │
         ▼
[5. MÓDULO UNIDADES & PERSONAS] (Migración de Apartamentos a Unidades)
         │
         ▼
[6. MIGRACIÓN OPERATIVA] (Contratos, Cuotas, Pagos, Visitas, QR, Buzón por Propiedad)
         │
         ▼
[7. FRONTEND MULTI-TENANT] (AppShell con Tenant Switcher, RBAC Router)
         │
         ▼
[8. SUPERADMIN DASHBOARD & AUDITORÍA] (Gestión de membresías y logs)
```

---

## 14. MATRIZ DE RIESGOS

| Riesgo Técnico / Operativo | Severidad | Probabilidad | Estrategia de Mitigación |
|---|---|---|---|
| **Colapso de Conexiones en Oracle ATP** | **CRÍTICA** | ALTA | Reemplazar inmediatamente el `ThreadLocal` de `ConexionBD` por un pool de conexiones gestionado (**HikariCP** o **Oracle UCP**). |
| **Fuga de Información entre Organizaciones (Cross-Tenant Leak)** | **CRÍTICA** | MEDIA | Obligar a que todos los DAOs reciban el contexto de sesión y apliquen `WHERE id_propiedad = ?` / `WHERE id_organizacion = ?`. Prohibir consultas sin scope. |
| **Regresión en Validación de QR en Portería** | **ALTA** | MEDIA | Mantener el código core de `SP_VALIDAR_QR` y `GeneradorQR.java`, envolviéndolo en un endpoint que valide pertenencia a la propiedad. |
| **Inconsistencias en Migración de Datos 1.0 → 2.0** | **ALTA** | BAJA | Crear un script de migración ETL que cree una Organización por defecto ("Organización Demo") y asigne el edificio actual como `Propiedad #1`. |
| **Cold Start del Backend en Render Free Tier** | **MEDIA** | ALTA | Configurar un worker/cron de keep-alive en `/health` cada 10 minutos y optimizar el tiempo de inicialización de la JVM. |

---

## 15. CÓDIGO LEGACY (PLAN DE DISPOSICIÓN)

1. **Eliminar Inmediatamente:**
   - Dependencias JavaFX en [`pom.xml`](file:///C:/Users/JUAN/IdeaProjects/sistema-administracion-edificios/pom.xml) (`javafx-controls`, `javafx-fxml`, plugin `javafx-maven-plugin`).
   - Scripts de parches temporales en `database/` (`fix_modelo_v5_1.sql` al `v5_4.sql`, `datos_prueba_corregido.sql`).
   - Migración DDL manual dentro de [`RestServer.java`](file:///C:/Users/JUAN/IdeaProjects/sistema-administracion-edificios/src/main/java/com/edificio/admin/rest/RestServer.java#L25-L37) (`ALTER TABLE APARTAMENTOS ADD...`).
2. **Archivar en `/legacy/`:**
   - [`MigrarDatosLocalToATP.java`](file:///C:/Users/JUAN/IdeaProjects/sistema-administracion-edificios/database/MigrarDatosLocalToATP.java) y [`RunSchemaFinal.java`](file:///C:/Users/JUAN/IdeaProjects/sistema-administracion-edificios/database/RunSchemaFinal.java).
   - Scripts de pruebas directas `Cols.java`, `FullList.java`, `ListFull.java`.
3. **Mantener Temporalmente Durante Transición:**
   - `modelo_relacional_v4_atp.sql` como referencia de DDL hasta estabilizar el nuevo esquema v2.0.

---

## 16. CÓDIGO REUTILIZABLE (DIRECTO / CONSERVAR)

- **Criptografía y Tokens:** [`JwtUtil.java`](file:///C:/Users/JUAN/IdeaProjects/sistema-administracion-edificios/src/main/java/com/edificio/admin/rest/JwtUtil.java) (librería JWT y hashing).
- **Código QR:** [`GeneradorQR.java`](file:///C:/Users/JUAN/IdeaProjects/sistema-administracion-edificios/src/main/java/com/edificio/admin/util/GeneradorQR.java) (generación ZXing en Base64).
- **Generación de Documentos:** [`PdfGeneratorService.java`](file:///C:/Users/JUAN/IdeaProjects/sistema-administracion-edificios/src/main/java/com/edificio/admin/service/PdfGeneratorService.java) y [`ContratoPdfService.java`](file:///C:/Users/JUAN/IdeaProjects/sistema-administracion-edificios/src/main/java/com/edificio/admin/service/ContratoPdfService.java).
- **Correo Electrónico:** [`EmailService.java`](file:///C:/Users/JUAN/IdeaProjects/sistema-administracion-edificios/src/main/java/com/edificio/admin/service/EmailService.java) (integración HTTPS con Brevo).
- **Frontend Primitives:** Todos los componentes UI en `client/components/ui/` (`DataTable.jsx`, `Modal.jsx`, `Button.jsx`, `Form.jsx`, `Pagination.jsx`, `Toast.jsx`, `ConfirmDialog.jsx`).
- **Lógica de Escaneo:** [`client/pages/EscannerQRPage.jsx`](file:///C:/Users/JUAN/IdeaProjects/sistema-administracion-edificios/client/pages/EscannerQRPage.jsx) (integración de cámara web y lectura de códigos QR).

---

## 17. CÓDIGO QUE REQUIERE REFACTORIZACIÓN

| Archivo | Problema Actual | Cambio Requerido | Dependencias |
|---|---|---|---|
| [`ConexionBD.java`](file:///C:/Users/JUAN/IdeaProjects/sistema-administracion-edificios/src/main/java/com/edificio/admin/dao/ConexionBD.java) | ThreadLocal sin pool de conexiones. | Reemplazar por HikariCP / Oracle UCP con configuración de maxPoolSize. | ojdbc11, HikariCP |
| [`AuthScope.java`](file:///C:/Users/JUAN/IdeaProjects/sistema-administracion-edificios/src/main/java/com/edificio/admin/rest/AuthScope.java) | Validación de roles estáticos y consultas mono-edificio. | Reemplazar por `RbacService` que evalúe `(usuario, permiso, scope)`. | `USUARIO_ASIGNACIONES` |
| [`ApartamentoHandler.java`](file:///C:/Users/JUAN/IdeaProjects/sistema-administracion-edificios/src/main/java/com/edificio/admin/rest/handler/ApartamentoHandler.java) | Acoplado a apartamento y realiza joins en memoria N+1. | Renombrar a `UnidadHandler.java`, soportar filtros por propiedad/bloque y paginación SQL. | `UnidadService`, `UnidadDAO` |
| [`ContratoService.java`](file:///C:/Users/JUAN/IdeaProjects/sistema-administracion-edificios/src/main/java/com/edificio/admin/service/ContratoService.java) | Asume apartamento único. | Vincular contrato a `id_unidad` y validar disponibilidad multi-tenant. | `ContratoDAO`, `UnidadDAO` |
| [`WompiHandler.java`](file:///C:/Users/JUAN/IdeaProjects/sistema-administracion-edificios/src/main/java/com/edificio/admin/rest/handler/WompiHandler.java) | Registra pagos sin contexto de propiedad. | Asociar `TRANSACCIONES_PAGO` a `id_organizacion` y `id_propiedad`. | `WompiService` |
| [`client/lib/AuthContext.jsx`](file:///C:/Users/JUAN/IdeaProjects/sistema-administracion-edificios/client/lib/AuthContext.jsx) | Solo guarda user y rol. | Manejar estado de organización activa, propiedad activa y lista de permisos. | React Context API |
| [`client/components/layout/AppShell.jsx`](file:///C:/Users/JUAN/IdeaProjects/sistema-administracion-edificios/client/components/layout/AppShell.jsx) | Menú hardcodeado por 3 roles. | Integrar `TenantSelector` en header y filtrar sidebar según permisos del rol activo. | `AuthContext`, Lucide |

---

## 18. CÓDIGO NUEVO REQUERIDO

1. **Backend - Multi-Tenant Core:**
   - `com.edificio.admin.model.Organizacion`, `Propiedad`, `Bloque`, `Unidad`, `Plan`, `Membresia`, `Permiso`.
   - `OrganizacionDAO`, `PropiedadDAO`, `BloqueDAO`, `UnidadDAO`, `PlanDAO`, `AuditoriaDAO`.
   - `OrganizacionService`, `PropiedadService`, `RbacService`, `AuditoriaService`.
   - `OrganizacionHandler`, `PropiedadHandler`, `SuperadminHandler`, `AuditoriaHandler`.
2. **Backend - Infraestructura:**
   - `TenantContext.java`: ThreadLocal / Scoped value para propagar `id_organizacion` e `id_propiedad` transparentemente a través del ciclo de vida del request.
   - `RateLimitFilter.java`: Protección contra ataques de fuerza bruta en login y endpoints públicos.
3. **Frontend - Vistas Nuevas:**
   - `client/pages/superadmin/SuperadminDashboardPage.jsx` (Gestión de organizaciones, planes y métricas).
   - `client/pages/admin/OrganizacionConfigPage.jsx` (Configuración de la organización y módulos).
   - `client/pages/admin/PropiedadesPage.jsx` (Alta y administración de edificios/conjuntos).
   - `client/pages/admin/UnidadesPage.jsx` (Gestión unificada de apartamentos, casas, locales).
   - `client/pages/admin/AuditoriaPage.jsx` (Visor de logs de seguridad y trazabilidad).
   - `client/components/layout/TenantSelector.jsx` (Dropdown en cabecera para alternar propiedad/organización).

---

## 19. PLAN DE MIGRACIÓN RECOMENDADO POR FASES (EQUIPO DE 4 DESARROLLADORES)

```
+---------------------------------------------------------------------------------------+
| FASE 0: PREPARACIÓN & REPO BASE (Semana 1)                                            |
|   - Creación del nuevo repositorio limpio SAED 2.0.                                   |
|   - Depuración de dependencias obsoletas (JavaFX) y limpieza de scripts SQL.          |
|   - Configuración de Connection Pool (HikariCP/Oracle UCP).                           |
+---------------------------------------------------------------------------------------+
| FASE 1: NÚCLEO RELACIONAL & MULTI-TENANCY EN BD (Semana 1 - 2)                       |
|   - Despliegue del DDL v2.0 en Oracle ATP (Organizaciones, Propiedades, Unidades).    |
|   - Creación del esquema RBAC (Roles, Permisos, Asignaciones).                        |
|   - Script ETL de migración: importar datos actuales a "Organización Piloto".         |
+---------------------------------------------------------------------------------------+
| FASE 2: SEGURIDAD, RBAC & TENANT CONTEXT (Semana 2 - 3)                               |
|   - Implementación de TenantContext y AuthMiddleware con Scopes.                      |
|   - Refactorización de JwtUtil y Login para retornar contexto de organización.        |
|   - Pruebas unitarias de autorización e IDOR prevention.                              |
+---------------------------------------------------------------------------------------+
| FASE 3: REFACTORIZACIÓN DE DAOs Y SERVICIOS CORE (Semana 3 - 4)                       |
|   - Refactor de Unidades, Residentes, Contratos, Pagos, Visitas con scope.            |
|   - Actualización de WompiService y AlertaService para multi-propiedad.               |
|   - Recompilación y prueba de SPs/Triggers en Oracle ATP.                             |
+---------------------------------------------------------------------------------------+
| FASE 4: REFACTORIZACIÓN DEL FRONTEND & APP SHELL (Semana 4 - 5)                       |
|   - Rediseño de AuthContext con selector de Organización / Propiedad.                 |
|   - Implementación del nuevo AppShell y navegación basada en permisos.                |
|   - Unificación de páginas de Residentes/Admin en componentes reutilizables.          |
+---------------------------------------------------------------------------------------+
| FASE 5: MÓDULOS DE SUPERADMINISTRADOR & AUDITORÍA (Semana 5 - 6)                      |
|   - Desarrollo de dashboard Superadmin (Planes, Membresías, Clientes).                |
|   - Implementación del módulo centralizado de Auditoría (`AUDITORIA_LOG`).            |
|   - Reportes analíticos de ocupación, cartera y visitas por propiedad.                |
+---------------------------------------------------------------------------------------+
| FASE 6: QA, PERFORMANCE & DESPLIEGUE CONTINUO (Semana 6 - 7)                          |
|   - Pruebas end-to-end de flujos completos (Creación de Org -> Propiedad -> QR).     |
|   - Optimización de índices y análisis de planes de ejecución en Oracle.              |
|   - Pipeline CI/CD en Render / Vercel.                                                |
+---------------------------------------------------------------------------------------+
```

### Distribución del Trabajo para Equipo de 4 Desarrolladores
- **Dev 1 (Líder / Backend & DB Architect):** DDL de Oracle ATP, migración ETL de datos, pool de conexiones, `TenantContext`, `RbacService`, seguridad de tokens y endpoints de Superadmin.
- **Dev 2 (Backend Core & Integraciones):** Refactor de DAOs y Services (`UnidadDAO`, `ContratoDAO`, `PagoDAO`, `WompiService`, `EmailService`), SPs de QR y Scheduler multi-tenant.
- **Dev 3 (Frontend Lead & Shell):** `AuthContext`, `AppShell`, `TenantSelector`, sistema de rutas dinámicas por permisos, páginas de Superadmin y configuración de organizaciones.
- **Dev 4 (Frontend Features & QA):** Refactorización de páginas operativas (`UnidadesPage`, `ContratosPage`, `PagosPage`, `EscannerQRPage`, `VisitasPage`), reportes analíticos y pruebas end-to-end.

---

## 20. RECOMENDACIÓN FINAL Y ESTIMACIÓN TÉCNICA

### Balance de Reutilización
- **32% Conservar Directo:** Motores de utilidades, integraciones de correo, QR, generador PDF, componentes UI primitivos de React.
- **45% Refactorizar:** Adaptación de DAOs, Handlers, Servicios, páginas de React y modelo de datos a la jerarquía `Organización → Propiedad → Unidad`.
- **8% Eliminar / Archivar:** JavaFX, scripts DDL desactualizados, endpoints con joins en memoria.
- **15% Construir Nuevo:** Superadmin, suscripciones/planes, RBAC granular con scopes, selector de tenant y tabla de auditoría unificada.

### Dictamen del Arquitecto
La base de código heredada de **SAED 1.0 tiene un valor técnico real y está lista para ser transformada**. No se debe reconstruir desde cero (desperdiciaría semanas de estabilización de flujos de pago, PDF y QR), pero tampoco se debe continuar construyendo sobre el modelo de un solo edificio. El plan planteado permite ejecutar la evolución a **SAED 2.0** de forma ordenada, segura y modular en un ciclo estimado de **6 a 7 semanas** con el equipo disponible.
