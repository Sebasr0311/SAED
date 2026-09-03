# SAED — ARQUITECTURA E IMPLEMENTACIÓN: ADMIN_PROPIEDAD V1

**Fecha:** Septiembre 2026  
**Versión:** 1.0.0  
**Estado:** 🟢 CERTIFIED / PRODUCTION READY  
**Fronteras Arquitectónicas:**
- `SUPERADMIN V1 RC1` (`ba46992`): 🟢 FROZEN / GLOBAL SCOPE
- `ADMIN_ORGANIZACION V1`: 🟢 CERTIFIED / ORGANIZATION SCOPE
- `ADMIN_PROPIEDAD V1`: 🟢 CERTIFIED / PROPERTY SCOPE

---

## 1. Misión y Definición del Rol

`ADMIN_PROPIEDAD` representa al **administrador operativo de una o varias copropiedades/propiedades específicas** dentro de una organización cliente de SAED.

### Jerarquía Multi-Tenant Inviolable
```text
SUPERADMIN (Plataforma Global SaaS — FROZEN)
    │
    ▼
ADMIN_ORGANIZACION (Cliente / Organización — CERTIFIED)
    │
    ▼
ADMIN_PROPIEDAD (Copropiedad / Propiedad — CERTIFIED)
    │
    ▼
PORTERO / RESIDENTE (Operación de Acceso / Unidades)
```

### Principios Fundamentales de Seguridad
1. **Límites de Autoridad Estrictos:** Su autoridad emana exclusivamente de sus asignaciones de propiedad (`USUARIO_ASIGNACIONES` con `id_propiedad`, `id_organizacion`, `id_rol` correspondiente a `ADMIN_PROPIEDAD`).
2. **Zero Trust & Tenant Isolation:** No puede acceder a propiedades no asignadas ni de organizaciones foráneas.
3. **Plataforma y Consola Org Denegadas:** Tiene expresamente denegado el acceso a consolas globales (`/api/v1/platform/*`, `/superadmin/*`) y de organización (`/api/v1/org/*`).
4. **Soporte Multi-Propiedad:** Un mismo usuario puede administrar varias copropiedades. El cambio de contexto se realiza exclusivamente a través del header `X-Assignment-Id`, validado server-side contra base de datos.
5. **Anti-Escalamiento de Privilegios:** Solo puede asignar roles de alcance igual o inferior (`PORTERO`, `RESIDENTE`) dentro de su propiedad asignada. No puede crear ni mutar roles `SUPERADMIN` ni `ADMIN_ORGANIZACION`.

---

## 2. Modelo de Autorización y Scopes

### Tokens y Autoridades
- **Rol:** `ROLE_ADMIN_PROPIEDAD`
- **Scope Directo:** `SCOPE_ADMIN_PROPIEDAD`
- **Scope Jerárquico:** `SCOPE_PROPIEDAD`
- **Scopes Prohibidos:** `SCOPE_GLOBAL`, `SCOPE_SUPERADMIN`, `SCOPE_ADMIN_ORGANIZACION`

### Resolución de Contexto
```text
Cliente HTTP (Request)
    ├── Authorization: Bearer <IdentityJWT>
    └── X-Assignment-Id: <idAsignacion>
           │
           ▼
JwtAuthenticationFilter
    ├── Valida firma y expiración del JWT
    ├── Valida asignación contra DB vía PKG_AUTH_BOOTSTRAP.GET_ASSIGNMENT_CONTEXT
    └── Construye SaedContext (userId, orgId, propId, roleCode='ADMIN_PROPIEDAD', roleScope='PROPIEDAD')
           │
           ▼
SaedContextHolder (ThreadLocal)
           │
           ▼
SaedDataSourceProxy
    ├── PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(userId)
    └── PKG_SAED_SESSION.SET_CONTEXT(userId, orgId, propId, 'ADMIN_PROPIEDAD')
           │
           ▼
Oracle Virtual Private Database (VPD / RLS)
    ├── PKG_SAED_SECURITY_RLS.FN_FILTRO_PROPIEDAD: "id_propiedad = v_prop"
    ├── PKG_SAED_SECURITY_RLS.FN_FILTRO_UNIDAD: "id_propiedad = v_prop"
    └── Mutaciones globales/org: "1=0"
```

---

## 3. Matriz de Permisos por Módulo Operativo

| Módulo | Read | Create | Update | Delete | Alcance / Límite |
| :--- | :---: | :---: | :---: | :---: | :--- |
| **Propiedades** | ✅ | ❌ | ❌ | ❌ | Solo lectura de propiedad asignada. Creación/estado restringido a Org Admin. |
| **Unidades** | ✅ | ✅ | ✅ | ❌ | Gestión de unidades de la propiedad asignada (bloqueado a context propId). |
| **Residentes / Habitantes** | ✅ | ✅ | ❌ | ❌ | Asignación de propietarios y residentes en unidades de la propiedad. |
| **Personas** | ✅ | ✅ | ✅ | ✅ | Gestión de directorio de personas vinculadas a la propiedad. |
| **Visitas** | ✅ | ✅ | ✅ | ❌ | Registro, control de acceso y autorizaciones de visita. |
| **Portería / Registros / QR**| ✅ | ✅ | ✅ | ✅ | Gestión de porterías, validación QR y control vehicular. |
| **Correspondencia / Paquetes**| ✅ | ✅ | ✅ | ❌ | Registro y entrega de encomiendas de unidades de la propiedad. |
| **Dependientes** | ✅ | ✅ | ✅ | ✅ | Vehículos, mascotas, tutores y visitantes recurrentes. |
| **Parqueaderos** | ✅ | ✅ | ✅ | ✅ | Gestión de bahías y asignación a residentes/visitantes. |
| **Cuotas y Cartera** | ✅ | ✅ | ❌ | ❌ | Consulta de cuotas, cartera, aging y recalcular saldos. |
| **Pagos** | ✅ | ✅ | ❌ | ❌ | Registro de recaudos y pagos de cuotas de administración. |
| **Multas y Sanciones** | ✅ | ❌ | ✅ | ❌ | Consulta, anulación o registro de pago de multas de la copropiedad. |
| **Quejas y Reclamos** | ✅ | ❌ | ✅ | ❌ | Consulta y respuesta oficial a quejas de residentes. |
| **PQRS (Tickets)** | ✅ | ✅ | ✅ | ❌ | Creación y actualización de estado de tickets PQRS. |
| **Seguros (Pólizas)** | ✅ | ✅ | ✅ | ✅ | Pólizas de áreas comunes y activos de la propiedad. |
| **Contratos (Arriendo/Prov)**| ✅ | ✅ | ✅ | ✅ | Contratos de arrendamiento y de proveedores de la propiedad. |
| **Reportes** | ✅ | ❌ | ❌ | ❌ | Reportes operativos y financieros de la propiedad. |
| **Pista de Auditoría** | ✅ | ❌ | ❌ | ❌ | Consulta de logs de auditoría acotados a su propiedad. |

---

## 4. Pruebas Adversariales Automatizadas

La clase [`AdminPropiedadAdversarialAuthorizationTest.java`](file:///C:/Users/JUAN/Antigravity%20IDE/SAED/backend/src/test/java/com/saed/backend/authorization/AdminPropiedadAdversarialAuthorizationTest.java) ejecuta **30 pruebas de seguridad automatizadas**:
1. Acceso a unidades (200 OK)
2. Acceso a personas (200 OK)
3. Acceso a cuotas (200 OK)
4. Acceso a multas (200 OK)
5. Acceso a quejas (200 OK)
6. Acceso a tickets PQRS (200 OK)
7. Acceso a seguros (200 OK)
8. Acceso a cartera (200 OK)
9. Acceso a resumen de cartera (200 OK)
10. Acceso a portería (200 OK)
11. Acceso a paquetes (200 OK)
12. Acceso a contratos (200 OK)
13. Acceso a parqueaderos (200 OK)
14. Acceso a reportes (200 OK)
15. Acceso a auditoría acotada (200 OK)
16. Ataque: Acceso a `/api/v1/platform/dashboard` $\to$ 403 Forbidden
17. Ataque: Acceso a `/api/v1/platform/plans` $\to$ 403 Forbidden
18. Ataque: Acceso a `/api/v1/platform/admins` $\to$ 403 Forbidden
19. Ataque: Acceso a `/api/v1/platform/memberships` $\to$ 403 Forbidden
20. Ataque: Acceso a `/api/v1/org/profile` $\to$ 403 Forbidden
21. Ataque: Acceso a `/api/v1/org/dashboard` $\to$ 403 Forbidden
22. Ataque: Acceso a `/api/v1/org/subscription` $\to$ 403 Forbidden
23. Ataque: Acceso a `/api/v1/org/admins` $\to$ 403 Forbidden
24. Ataque: Acceso a `/api/v1/organizations` $\to$ 403 Forbidden
25. Ataque: Creación de propiedad `POST /api/v1/properties` $\to$ 403 Forbidden
26. Ataque: Mutación de estado de propiedad `PATCH /api/v1/properties/{id}/status` $\to$ 403 Forbidden
27. Ataque: Intento de asignación de rol `SUPERADMIN` $\to$ AccessDeniedException
28. Ataque: Intento de asignación de rol `ADMIN_ORGANIZACION` $\to$ AccessDeniedException
29. Aislamiento Multi-Propiedad: Alternancia de `X-Assignment-Id` entre Propiedad 1 y Propiedad 2 aísla el contexto
30. Anti-Spoofing: Intento de uso de assignment perteneciente a otro usuario $\to$ 403 Forbidden

---

## 5. Verificación de Suites y Build

- **Backend Total Tests:** 224 / 224 passing (0 failures, 0 errors, 0 skipped).
- **Adversarial Tests:** 30 / 30 passing in `AdminPropiedadAdversarialAuthorizationTest`.
- **Frontend Build:** `npm run build` exitoso en 6.84s con 0 errores de compilación o bundling.
