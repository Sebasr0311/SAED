# SAED 2.0 — PHASE 1 AUTHORIZATION MATRIX

Esta matriz se deriva estrictamente del modelo de Base de Datos Base V3.9 y las políticas nativas de Row-Level Security (RLS). En SAED 2.0, el Backend refuerza (Defense-in-depth) pero **NO reemplaza** estas reglas.

## Matriz Híbrida: Recurso, Acción, Rol, Scope y Restricción Oracle RLS

| RECURSO (Tabla) | ACCIÓN | ROLES PERMITIDOS (RBAC) | SCOPE (Alcance) | RESTRICCIÓN Y SEGURIDAD (RLS / Backend) |
|---|---|---|---|---|
| **ORGANIZACION** | `READ` | Todos los roles | Todos | Solo puede ver la Organización a la que pertenece el `id_asignacion` elegido. SuperAdmin puede ver todas. |
| **ORGANIZACION** | `UPDATE` | `ADMIN_ORGANIZACION`, `ADMIN_GENERAL`, `SUPERADMIN` | `ORGANIZACION`, `GLOBAL` | Solo su organización. Cambio de Estado (ej. a SUSPENDIDA) restringido a backend solo si emisor = SUPERADMIN. |
| **PROPIEDAD** | `CREATE` | `ADMIN_ORGANIZACION`, `ADMIN_GENERAL` | `ORGANIZACION` | Forzosamente atada al `id_organizacion` del Tenant en curso. |
| **PROPIEDAD** | `READ` | Todos los roles | Todos | `ADMIN_PROPIEDAD`, `PORTERO` u operativos, solo verán la(s) Propiedad(es) en su Assignment (`PROPIEDADES_SELECCIONADAS` o `PROPIEDAD`). |
| **USUARIO** | `CREATE` | Administradores, Propietarios (Delegado) | `>= PROPIEDAD` | Inserción permitida. Posterior `USUARIO_ASIGNACION` debe encuadrar en la autoridad del creador. |
| **USUARIO** | `UPDATE` | Autenticado (Propio), Administrador (Tercero) | `>= PROPIEDAD` | Nadie puede escalar privilegios en su propio Usuario. Los Admins solo pueden editar datos de `PERSONAS` de usuarios bajo su Tenant/Propiedad. |
| **ASIGNACION** | `CREATE / UPDATE` | `ADMIN_ORGANIZACION`, `ADMIN_GENERAL`, `ADMIN_PROPIEDAD` | `>= PROPIEDAD` | Regla de jerarquía: Ningún rol puede asignar un rol superior al suyo. Tampoco asignar un Scope que exceda su propia visibilidad RLS. El Backend validará la jerarquía antes del DML. |
| **MEMBRESIA** | `READ` | `ADMIN_ORGANIZACION`, `ADMIN_GENERAL` | `ORGANIZACION` | Visualización en solo-lectura sobre la vigencia del contrato. |
| **MEMBRESIA** | `UPDATE` | `SUPERADMIN` | `GLOBAL` | Las transacciones de facturación de Membresías no se autorizan al Tenant. |

## Control de Jerarquía en Backend (Privilege Escalation Prevention)

Para las asignaciones, SAED 2.0 Java impondrá una validación Pre-DML. 
Aunque Oracle aísle los datos del Tenant (no puedo asignar a alguien a la Org B si estoy en la Org A), Oracle nativo (sin un trigger complejo) no impide que un `ADMIN_PROPIEDAD` intente insertarle un `ADMIN_ORGANIZACION` a otro usuario dentro de su mismo Tenant.
*Spring Boot mitigará este vacío comparando la "fuerza" del Scope del Emisor (JWT) vs Receptor (DTO).*

**Niveles de Fuerza (Hardcoded Validator):**
1. GLOBAL (`SUPERADMIN`)
2. ORGANIZACION (`ADMIN_ORGANIZACION`, `ADMIN_GENERAL`)
3. PROPIEDADES_SELECCIONADAS / PROPIEDAD (`ADMIN_PROPIEDAD`)
4. UNIDAD (`RESIDENTE`, `PROPIETARIO_UNIDAD`, Operativos)

*Regla: Un emisor de nivel N solo puede emitir o modificar asignaciones para roles de nivel N o superior numéricamente (menor privilegio).*
