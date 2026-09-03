# SAED — REPORTE OFICIAL DE CERTIFICACIÓN: ADMIN_PROPIEDAD V1

**Fecha:** Septiembre 2026  
**Auditor:** Senior Architect & Security Auditor  
**Veredicto Oficial:** 🟢 CERTIFIED / PRODUCTION READY  
**Fronteras Inviolables:**
- `SUPERADMIN V1 RC1` (`ba46992`): 🟢 FROZEN / GLOBAL SCOPE
- `ADMIN_ORGANIZACION V1`: 🟢 CERTIFIED / ORGANIZATION SCOPE
- `ADMIN_PROPIEDAD V1`: 🟢 CERTIFIED / PROPERTY SCOPE

---

## 1. Evaluación Exhaustiva por las 20 Áreas Obligatorias

### 1. Definición del Rol ADMIN_PROPIEDAD
- **Estado:** 🟢 APROBADO
- **Detalle:** `ADMIN_PROPIEDAD` está acotado estrictamente a la gestión operativa de una o varias copropiedades específicas asociadas al tenant de una organización. Su autoridad reside únicamente en sus asignaciones válidas en base de datos.

### 2. Jerarquía Multi-Tenant
- **Estado:** 🟢 APROBADO
- **Detalle:** La jerarquía `SUPERADMIN (Global) -> ADMIN_ORGANIZACION (Organización) -> ADMIN_PROPIEDAD (Propiedad) -> PORTERO / RESIDENTE (Unidad)` se cumple rigurosamente en base de datos (Oracle RLS), backend (Spring Security) y frontend (AppShell y TenantContext).

### 3. Modelo de Seguridad y Scopes
- **Estado:** 🟢 APROBADO
- **Detalle:** Autenticado bajo `ROLE_ADMIN_PROPIEDAD`, con `SCOPE_ADMIN_PROPIEDAD` y `SCOPE_PROPIEDAD`. Denegados explícitamente `SCOPE_GLOBAL`, `SCOPE_SUPERADMIN` y `SCOPE_ADMIN_ORGANIZACION`.

### 4. Resolución de Contexto (Context Resolution)
- **Estado:** 🟢 APROBADO
- **Detalle:** El flujo `JWT -> JwtAuthenticationFilter -> SaedContext (userId, orgId, propId, roleCode, roleScope) -> SaedDataSourceProxy -> PKG_SAED_SESSION.SET_CONTEXT` sincroniza el contexto de aplicación con las variables de sesión `SAED_CTX` de Oracle en cada conexión.

### 5. Multi-Propiedad (Multi-Property Assignment)
- **Estado:** 🟢 APROBADO
- **Detalle:** Un administrador asignado a múltiples copropiedades conmuta su contexto operativo mediante `X-Assignment-Id`. El selector `TenantSwitcher` en el frontend y la validación server-side aíslan las consultas según la propiedad activa.

### 6. Zero Trust & Anti-Spoofing
- **Estado:** 🟢 APROBADO
- **Detalle:** Intentos de inyectar `idPropiedad` o `idOrganizacion` foráneos en payloads o headers son rechazados por validación en servicios y por predicados RLS en Oracle.

### 7. Protección IDOR (Insecure Direct Object Reference)
- **Estado:** 🟢 APROBADO
- **Detalle:** Las consultas por ID de unidades, residentes, visitantes, multas, quejas y contratos validan pertenencia a la propiedad activa (`id_propiedad = v_prop`).

### 8. Anti-Escalamiento de Privilegios
- **Estado:** 🟢 APROBADO
- **Detalle:** `AssignmentManagementService` rechaza la asignación de roles `SUPERADMIN` o `ADMIN_ORGANIZACION` por parte de `ADMIN_PROPIEDAD` con `AccessDeniedException`.

### 9. Aislamiento de Base de Datos (Oracle VPD / RLS)
- **Estado:** 🟢 APROBADO
- **Detalle:** Políticas `POL_RLS_PROP_*` aplican `FN_FILTRO_PROPIEDAD` y `FN_FILTRO_UNIDAD` asegurando aislamiento a nivel de motor relacional.

### 10. Módulo de Propiedades y Unidades
- **Estado:** 🟢 APROBADO
- **Detalle:** Lectura de propiedad asignada permitida (`GET /api/v1/properties`). Creación y mutación de estado de propiedades restringida a `ADMIN_ORGANIZACION`. Gestión de unidades (`/api/v1/units`) bloqueada al `idPropiedad` del contexto.

### 11. Módulo de Habitantes y Directorio de Personas
- **Estado:** 🟢 APROBADO
- **Detalle:** Endpoints `/owners`, `/residents`, `/personas`, y dependientes (`/vehiculos`, `/mascotas`, `/tutores`, `/visitantes`) filtrados estrictamente por la propiedad.

### 12. Módulo de Portería, Accesos y Códigos QR
- **Estado:** 🟢 APROBADO
- **Detalle:** CRUD de porterías, autorizaciones de visita, registro de entradas/salidas, validación de códigos QR y control de vehículos de visita operando con aislamiento de copropiedad.

### 13. Módulo de Correspondencia (Paquetes)
- **Estado:** 🟢 APROBADO
- **Detalle:** Recepción, notificación y entrega de paquetería vinculadas a unidades de la copropiedad asignada.

### 14. Módulo de Parqueaderos
- **Estado:** 🟢 APROBADO
- **Detalle:** Registro de bahías y asignación de parqueaderos privados y de visitantes bajo `/api/v1/parqueaderos`.

### 15. Módulo Financiero, Pagos y Cartera
- **Estado:** 🟢 APROBADO
- **Detalle:** Consulta de cuotas, cartera, aging moroso, recálculo de saldos y registro de pagos operativos en la propiedad.

### 16. Módulo de Convivencia, Multas y Sanciones
- **Estado:** 🟢 APROBADO
- **Detalle:** Gestión de sanciones, quejas y multas de la copropiedad con trazabilidad y respuestas oficiales.

### 17. Módulo de PQRS y Seguros
- **Estado:** 🟢 APROBADO
- **Detalle:** Gestión de tickets PQRS con histórico de intervenciones y administración de pólizas de áreas comunes.

### 18. Módulo de Contratos y Reportes Operativos
- **Estado:** 🟢 APROBADO
- **Detalle:** Contratos de arrendamiento y de proveedores de la propiedad; generación de reportes de cartera morosa y pagos recientes.

### 19. Frontend SaaS y Experiencia de Usuario
- **Estado:** 🟢 APROBADO
- **Detalle:** `AppShell.jsx`, `TenantContext.jsx`, `TenantSwitcher.jsx` y páginas operativas adaptadas con navegación clara, soporte de conmutación multi-propiedad y diseño sobrio y accesible.

### 20. Auditoría y Trazabilidad (Audit Trail)
- **Estado:** 🟢 APROBADO
- **Detalle:** Acciones críticas auditadas mediante `@Auditable`. Consulta de `/api/v1/audit` restringida a los registros de la propiedad y organización del contexto.

---

## 2. Métricas de Calidad y Pruebas

| Suite / Verificación | Pruebas Ejecutadas | Resultado |
| :--- | :---: | :---: |
| **`AdminPropiedadAdversarialAuthorizationTest`** | 30 | 🟢 30/30 PASS (100%) |
| **`AdminOrganizacionAdversarialAuthorizationTest`** | 23 | 🟢 23/23 PASS (100%) |
| **`SuperAdminAdversarialAuthorizationTest`** | 24 | 🟢 24/24 PASS (100%) |
| **Backend Total Suite (`mvn test`)** | 224 | 🟢 224/224 PASS (0 failures, 0 errors, 0 skipped) |
| **Frontend Production Build (`npm run build`)** | - | 🟢 Clean build in 6.84s (0 errors) |

---

## 3. Veredicto Final

El rol **`ADMIN_PROPIEDAD V1`** cumple a cabalidad con todos los criterios de arquitectura multi-tenant, seguridad Zero Trust, aislamiento por Oracle RLS, soporte multi-propiedad y suite de pruebas adversariales automatizadas.

```text
=====================================================
   🟢 CERTIFICACIÓN OFICIAL: ADMIN_PROPIEDAD V1
   ESTADO: CERTIFIED / PRODUCTION READY
   TOTAL BACKEND TESTS: 224/224 PASSING
   ADVERSARIAL TESTS: 30/30 PASSING
   FRONTEND BUILD: CLEAN (6.84s)
=====================================================
```
