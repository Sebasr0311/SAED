# SAED 1.0 - BACKEND ANALYSIS

## 1. Arquitectura actual
El sistema SAED 1.0 actual tiene una arquitectura híbrida inusual: es simultáneamente una aplicación de escritorio **JavaFX** y un servidor **REST artesanal**. 
- **Presentación Desktop**: JavaFX (vistas `.fxml` y controladores).
- **Servidor REST**: Implementado directamente usando `com.sun.net.httpserver.HttpServer`.
- **Acceso a Datos**: Patrón DAO puro usando JDBC plano sin ORM, conectado directamente a Oracle.

## 2. Stack actual
- **Lenguaje**: Java 17
- **Construcción**: Maven
- **Frontend/UI**: JavaFX 17 (Desktop) + Frontend externo (Web)
- **Base de Datos**: Oracle Database (driver `ojdbc11`)
- **Dependencias extra**: 
  - `jbcrypt` (Seguridad manual)
  - `gson` (Serialización JSON)
  - `zxing` (Códigos QR)
  - `openhtmltopdf` (Generación de PDF)
  - `javax.mail` (Envío de correos)

## 3. Estructura de carpetas
- `backend/src/main/java/com/edificio/admin/`
  - `dao/`: Patrón DAO con conexiones JDBC explícitas.
  - `model/`: DTOs y modelos de negocio.
  - `rest/`: Servidor HTTP manual y handlers.
  - `service/`: Lógica de negocio encapsulada.
  - `util/`: Utilidades generales (fechas, validaciones, QR).
  - `view/`: Controladores de JavaFX.
- `backend/src/main/resources/`: Vistas FXML, templates HTML para emails/contratos.

## 4. Funcionalidades existentes
Gestión de apartamentos, usuarios, contratos, pagos (integración con Wompi), visitas, parqueaderos, notificaciones por email, alertas y escáner QR.

## 5. Backend existente
El backend expone endpoints vía `/api/*` leyendo JSON manualmente con Gson. Utiliza un `AuthHandler` para validar credenciales y devuelve algún token manual o cookie. Las transacciones JDBC se manejan a nivel de Handler o DAO usando `Connection` y `PreparedStatement`.

## 6. Código reutilizable
- Lógica de negocio independiente del framework (e.g., `GeneradorQR.java`, lógica de cálculo en `service/`).
- Templates HTML para generación de PDF y correos (`src/main/resources/templates/`).
- Integración de pasarelas de pago (`WompiService.java`).

## 7. Código que necesita refactor
- **DAOs (`dao/`)**: Deben reescribirse para inyectar el contexto Oracle (`PKG_SAED_SESSION.SET_CONTEXT`) antes de cualquier DML/Select.
- **Servicios (`service/`)**: Eliminar instanciación directa de DAOs, usar Inyección de Dependencias (Spring).
- **Modelos (`model/`)**: Se pueden adaptar como DTOs o Entidades del nuevo sistema.

## 8. Código obsoleto
- **JavaFX (`view/`, `Main.java`)**: SAED 2.0 es puramente web/API. La interfaz de escritorio ya no es necesaria.
- **`rest/` (HttpServer manual)**: Será reemplazado enteramente por **Spring Web (Controllers)**.

## 9. Riesgos
- Falta total de inyección de dependencias.
- Manejo manual de conexiones a base de datos (`ConexionBD.java`) sin pool optimizado (o usando uno muy rústico).
- Falta de manejo centralizado de excepciones.

## 10. Deuda técnica
- Servidor REST implementado manualmente es difícil de mantener, testear y asegurar.
- Mezcla de UI de escritorio y servidor en el mismo módulo.
- Migraciones ejecutadas como cadenas SQL `ALTER TABLE` dentro del propio código de inicialización del servidor (prohibido en V3.9).

## 11. Dependencias
Falta Spring Boot (Web, Security). Las dependencias actuales de utilería (Gson, ZXing, Mail) pueden conservarse o migrarse al estándar de Spring (Jackson, Spring Mail).

## 12. Problemas de seguridad
- No hay aislamiento multi-tenant a nivel de base de datos en el código actual (asume lógica de filtrado en el backend o no tiene soporte multi-tenant real).
- Manejo manual de tokens y CORS.
- Al no usar `PKG_SAED_SESSION`, el backend actual podría provocar violaciones de datos cruzados.

## 13. Qué conservar
- Reglas de negocio (cálculos de cuotas, generación de PDFs).
- Assets estáticos (templates de correo, plantillas de PDF).
- Scripts de despliegue si son adaptables.

## 14. Qué migrar
- El sistema de autenticación manual a **Spring Security**.
- El patrón DAO puro a un **DAO administrado por Spring** utilizando `JdbcTemplate` para garantizar el control explícito sobre la sesión Oracle.

## 15. Qué reemplazar
- `com.sun.net.httpserver` -> `Spring Web`.
- `ConexionBD` manual -> `HikariCP` (Manejado por Spring).

## 16. Qué crear desde cero
- La infraestructura Multi-tenant (`SaedContext`, filtros HTTP).
- La invocación a `PKG_SAED_SESSION` por cada transacción de DB.
- Manejador global de excepciones (`@ControllerAdvice`).
