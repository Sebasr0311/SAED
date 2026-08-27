# SAED 2.0 Backend - Documentación de la API

## 1. Arquitectura Backend
SAED 2.0 utiliza un backend robusto basado en **Spring Boot 3.2.3**, **JDK 17/24** y una base de datos **Oracle**.
Se ha implementado una arquitectura basada en **Zero-Trust** utilizando políticas de **Oracle RLS (Row-Level Security)** para garantizar el aislamiento mutuo de organizaciones y copropiedades.

La autenticación se realiza mediante **JWT**, con verificación y resolución de contextos organizacionales en cada petición.

## 2. Stack Tecnológico
- **Java 17/24**
- **Spring Boot 3.2.3** (Web, Security, JDBC, Mail, Validation)
- **Oracle JDBC Driver**
- **jjwt** (JSON Web Tokens)
- **OpenAPI 3.0 / Swagger UI** (springdoc-openapi)
- **OpenHTMLtoPDF / Jsoup** (Renderizado y generación de documentos PDF)
- **H2 Database** (para pruebas en memoria)

## 3. Swagger UI & OpenAPI JSON
La documentación interactiva y los esquemas autogenerados del contrato REST se exponen en:
- **Swagger UI:** /swagger-ui.html o /swagger-ui/index.html
- **OpenAPI JSON:** /v3/api-docs

Al utilizar Swagger UI, debe autenticarse utilizando el botón **Authorize** proporcionando un token JWT válido (agregando opcionalmente el prefijo Bearer).

## 4. Configuración (Variables de Entorno)
Para ejecutar el proyecto, se espera la configuración mediante variables de entorno o sobrescribiendo el archivo pplication.yml:
- DB_URL: JDBC URL (ej. jdbc:oracle:thin:@localhost:1521/XEPDB1)
- DB_USERNAME: Usuario (ej. SAED_APP)
- DB_PASSWORD: Contraseña
- JWT_SECRET: Clave secreta (base64) para firmar los tokens JWT
- WOMPI_EVENTS_SECRET: Secreto utilizado para validar webhooks de pagos.

## 5. Autenticación y Contexto (Zero-Trust)
SAED 2.0 verifica la validez del token en cada solicitud. Además, ciertos endpoints requieren la presencia explícita del contexto organizativo:
- Authorization: Bearer <token>
- X-Assignment-Id: <id> -> Obligatorio para determinar bajo qué perfil o asignación el usuario está operando. Internamente, esto resuelve el X-Tenant-Id y aplica el contexto PKG_SAED_SECURITY_RLS.SET_CONTEXT en Oracle.

Si un contexto es inválido (Context Bleed o Spoofing), el GlobalExceptionHandler interceptará la acción devolviendo HTTP 403 Forbidden o HTTP 500.

## 6. Principales Módulos de la API
Los controladores se agrupan en las siguientes categorías (Tags):
- **Identity & Auth:** AuthController, MeController, OrganizationController, PropertyController
- **Gestión de Inmuebles & Residentes:** UnitController, UnitInhabitantController, PersonaController, DependentController
- **Portería y Visitas:** PorteriaController, PorteriaExtController
- **Comunicaciones:** AlertasController, ComunicadosController
- **Convivencia y Novedades:** BuzonController, MultasController, QuejasController
- **Paquetería y Parqueaderos:** PaquetesController, ParqueaderosController
- **Finanzas y Pagos:** ContratosController, PagosController, WompiController, ResidentesFinanzasController
- **Estadísticas:** DashboardController

## 7. Ejecución de Pruebas
El proyecto contiene pruebas de integridad y pruebas adversariales (AdversarialFoundationTest, ContextBleedIntegrationTest).
Para ejecutarlas, utilizar:
`ash
mvn clean test -DargLine="-Dnet.bytebuddy.experimental=true"
`

## 8. Generar Build (JAR)
`ash
mvn clean package -DskipTests
`
El artefacto compilado se alojará en 	arget/backend-1.0.0-SNAPSHOT.jar.
