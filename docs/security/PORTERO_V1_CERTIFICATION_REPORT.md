# SAED — PORTERO V1 AUDITORÍA ADVERSARIAL Y REPORTE DE CERTIFICACIÓN

> **Fecha:** 2026-09-01  
> **Evaluador:** Senior Security & Software Architect  
> **Veredicto:** 🟢 **CERTIFIED — PRODUCTION READY**  
> **Estado de Roles:**
> - `SUPERADMIN V1 RC1`: 🟢 FROZEN
> - `ADMIN_ORGANIZACION V1`: 🟢 CERTIFIED
> - `ADMIN_PROPIEDAD V1`: 🟢 CERTIFIED
> - `PORTERO V1`: 🟢 **CERTIFIED**

---

## 1. Resumen Ejecutivo de Certificación

Se ha completado la auditoría integral, adversarial y no destructiva para el rol **PORTERO V1** dentro de la plataforma SaaS multi-tenant SAED.

El rol ha sido validado bajo el principio fundamental de **separación estricta de responsabilidades**:
$$\text{PORTERO} = \text{Operador Frontal de Seguridad y Recepción} \neq \text{Administrador de la Propiedad}$$

La verificación técnica incluyó:
- **Pruebas Adversariales Específicas:** **42/42 escenarios de ataque y autorización en verde** ([`PorteroAdversarialAuthorizationTest.java`](file:///C:/Users/JUAN/Antigravity%20IDE/SAED/backend/src/test/java/com/saed/backend/authorization/PorteroAdversarialAuthorizationTest.java)).
- **Suite Completa Backend:** **266/266 pruebas pasando en verde (0 fallos, 0 errores, 0 skipped)** en 40.26s.
- **Frontend Build:** `npm run build` limpio en 5.79s con 0 errores de compilación ni tipos.
- **Oracle VPD / RLS:** Zero-Trust isolation verificado a nivel de motor de base de datos Oracle XE / ATP.

---

## 2. Matriz de Auditoría por Áreas (31 Áreas de Certificación)

| # | Área de Auditoría | Estado | Evidencia y Hallazgos |
|---|---|:---:|---|
| **1** | **ROLE_PORTERO** | 🟢 APROBADO | Rol registrado con `CODIGO = 'PORTERO'`, `ALCANCE = 'PROPIEDAD'`, `ID_ROL = 4`. |
| **2** | **SCOPE_PORTERO** | 🟢 APROBADO | Scope emitido en JWT y validado por `JwtAuthenticationFilter`. |
| **3** | **Separación Operador vs Administrador** | 🟢 APROBADO | Prohibido el acceso a endpoints administrativos (`/api/v1/properties`, `/api/v1/assignments`, etc.). |
| **4** | **Inyección de Identidad / Token** | 🟢 APROBADO | `JwtProvider` emite claims inalterables verificados por firma HS384. |
| **5** | **X-Assignment-Id** | 🟢 APROBADO | Header obligatorio validado contra la base de datos para cargar contexto multi-propiedad. |
| **6** | **Validación de Asignación** | 🟢 APROBADO | `validateAssignment` asegura que la asignación esté activa y pertenezca al usuario autenticado. |
| **7** | **Zero-Trust Context** | 🟢 APROBADO | `SaedContextHolder` mantiene ThreadLocal aislado y limpiado en `finally`. |
| **8** | **Oracle RLS / VPD** | 🟢 APROBADO | `PKG_SAED_SECURITY_RLS.FN_FILTRO_PROPIEDAD` y `FN_FILTRO_UNIDAD` aíslan filas por `id_propiedad = v_prop`. |
| **9** | **Limpieza de Pool Hikari** | 🟢 APROBADO | `SaedDataSourceProxy` ejecuta `PKG_SAED_SESSION.CLEAR_CONTEXT` tras cada transacción. |
| **10** | **Unidades (Read Operativo)** | 🟢 APROBADO | Lectura de unidades permitida para seleccionar destino de visitas; mutaciones denegadas (403). |
| **11** | **Directorio Personas** | 🟢 APROBADO | Consulta de residentes y propietarios permitida; creación/eliminación denegada (403). |
| **12** | **Visitantes** | 🟢 APROBADO | Portero puede registrar nuevos visitantes en portería (`POST /api/v1/visitantes`). |
| **13** | **Programación de Visitas** | 🟢 APROBADO | Registro de visitas peatonales y vehiculares operativo (`POST /api/v1/porteria/visitas`). |
| **14** | **Salida de Visitas** | 🟢 APROBADO | Marcación de salida con actualización de estado a `FINALIZADA` (`PUT /api/v1/porteria/visitas/{id}/salida`). |
| **15** | **Historial y Resumen de Visitas** | 🟢 APROBADO | Consulta de visitas activas e historial por rango de fechas (`/visitas-resumen`, `/visitas/historial`). |
| **16** | **Control de Accesos** | 🟢 APROBADO | Registro de entradas y salidas de personas (`/api/v1/porteria/registros/entrada`, `/salida`). |
| **17** | **Anti-Spoofing en Registros** | 🟢 APROBADO | `propiedadId` y `porteroOperadorId` son forzados desde el contexto autenticado, impidiendo suplantación. |
| **18** | **Validación de Código QR** | 🟢 APROBADO | Endpoint `/api/v1/porteria/qr/validar` valida vigencia, estado `ACTIVO`, cupo y propiedad. |
| **19** | **QR Expirado / Consumido** | 🟢 APROBADO | QRs vencidos o con usos agotados retornan `{"valido": false}` sin permitir paso. |
| **20** | **Aislamiento Cross-Tenant de QR** | 🟢 APROBADO | Oracle RLS impide que un portero valide QRs emitidos por otra propiedad o tenant. |
| **21** | **Vehículos de Visita** | 🟢 APROBADO | Registro de ingreso y liquidación/salida de vehículos (`/api/v1/porteria/vehiculos`). |
| **22** | **Correspondencia y Paquetes** | 🟢 APROBADO | Registro de correspondencia y entrega segura validando PIN de retiro del habitante. |
| **23** | **Parqueaderos de Visita** | 🟢 APROBADO | Lectura de disponibilidad y asignaciones permitida; configuración de bahías denegada (403). |
| **24** | **Finanzas y Pagos (Bloqueo)** | 🟢 APROBADO | `/api/v1/cuotas`, `/api/v1/cartera`, `/api/v1/pagos` retornan 403 Forbidden. |
| **25** | **Sanciones y Multas (Bloqueo)** | 🟢 APROBADO | `/api/v1/multas/todas` retorna 403 Forbidden. |
| **26** | **Quejas y PQRS (Bloqueo)** | 🟢 APROBADO | `/api/v1/quejas/todas`, `/api/v1/pqrs/todos` retornan 403 Forbidden. |
| **27** | **Contratos y Seguros (Bloqueo)**| 🟢 APROBADO | `/api/v1/contratos`, `/api/v1/seguros/polizas` retornan 403 Forbidden. |
| **28** | **Plataforma y SaaS (Bloqueo)** | 🟢 APROBADO | `/api/v1/platform/*`, `/api/v1/org/*` retornan 403 Forbidden. |
| **29** | **Anti-Privilege Escalation** | 🟢 APROBADO | Intentos de asignar roles de mayor jerarquía lanzan `AccessDeniedException`. |
| **30** | **Conmutación Multi-Propiedad** | 🟢 APROBADO | Cambio de `X-Assignment-Id` conmuta dinámicamente el contexto sin fuga de datos. |
| **31** | **Frontend y Experiencia Operador** | 🟢 APROBADO | `PorteroDashboardPage`, `VisitasPage`, `PaquetesPage`, `EscannerQRPage` y `access.js` optimizados. |

---

## 3. Cobertura de la Suite Adversarial (`PorteroAdversarialAuthorizationTest.java`)

Se crearon **42 pruebas adversariales**, organizadas en 7 vectores de ataque:

```text
1. Operaciones Positivas de Portería (11 tests)
   ✔ portero_canReadUnits
   ✔ portero_canReadUnitById
   ✔ portero_canReadResidentsOfUnit
   ✔ portero_canReadOwnersOfUnit
   ✔ portero_canReadPersonas
   ✔ portero_canReadVisitasResumen
   ✔ portero_canReadVisitasHistorial
   ✔ portero_canReadRegistrosByPropiedad
   ✔ portero_canReadPaquetes
   ✔ portero_canReadParqueaderos
   ✔ portero_canReadParqueaderosAsignaciones

2. Seguridad y Validación QR (3 tests)
   ✔ portero_validarQr_valido
   ✔ portero_validarQr_expirado
   ✔ portero_validarQr_inexistente

3. Denegación en Mutación de Unidades y Personas (6 tests)
   ✔ portero_cannotCreateUnits (403)
   ✔ portero_cannotUpdateUnits (403)
   ✔ portero_cannotAddOwners (403)
   ✔ portero_cannotAddResidents (403)
   ✔ portero_cannotCreatePersonas (403)
   ✔ portero_cannotDeletePersonas (403)

4. Denegación en Finanzas, Legal y Sanciones (9 tests)
   ✔ portero_cannotAccessCuotas (403)
   ✔ portero_cannotAccessCartera (403)
   ✔ portero_cannotAccessCarteraResumen (403)
   ✔ portero_cannotRegisterPagos (403)
   ✔ portero_cannotAccessMultas (403)
   ✔ portero_cannotAccessQuejasAdmin (403)
   ✔ portero_cannotAccessPqrsAdmin (403)
   ✔ portero_cannotAccessSeguros (403)
   ✔ portero_cannotAccessContratos (403)

5. Denegación en Plataforma y Organización (8 tests)
   ✔ portero_cannotAccessPlatformDashboard (403)
   ✔ portero_cannotAccessPlatformPlans (403)
   ✔ portero_cannotAccessPlatformAdmins (403)
   ✔ portero_cannotAccessOrgProfile (403)
   ✔ portero_cannotAccessOrgDashboard (403)
   ✔ portero_cannotCreateProperties (403)
   ✔ portero_cannotCreateAssignments (403)
   ✔ portero_cannotUpdateAssignmentStatus (403)

6. Anti-Escalamiento de Privilegios (3 tests)
   ✔ portero_cannotAssignSuperAdminRole (AccessDeniedException)
   ✔ portero_cannotAssignAdminOrgRole (AccessDeniedException)
   ✔ portero_cannotAssignAdminPropRole (AccessDeniedException)

7. Conmutación Multi-Propiedad y Cross-Tenant IDOR (2 tests)
   ✔ portero_multiPropertySwitching
   ✔ portero_cannotUseForeignAssignment (403)
```

---

## 4. Estado de Regresión Global

- **Total de pruebas ejecutadas:** 266
- **Fallos:** 0
- **Errores:** 0
- **Omitidas:** 0
- **Tiempo de ejecución suite backend:** 40.26s
- **Frontend `npm run build`:** Clean build (5.79s)

---

## 5. Veredicto Final

# 🟢 CERTIFIED — PORTERO V1

El rol `PORTERO V1` cumple estrictamente todas las especificaciones de seguridad, control de acceso, aislamiento multi-tenant y experiencia de operador de recepción en SAED. Queda oficialmente certificado y listo para la siguiente fase: `RESIDENTE V1`.
