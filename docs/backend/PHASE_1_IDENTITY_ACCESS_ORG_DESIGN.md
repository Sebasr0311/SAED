# SAED 2.0 — PHASE 1 IDENTITY, ACCESS, & ORGANIZATIONS DESIGN

## 1. Authentication

**Flujo Completo:**
1. **Login:** El cliente envía credenciales (email y password) al endpoint `/api/v1/auth/login`.
2. **Bootstrap Authentication:** Spring usa `JdbcTemplate` para consultar `PKG_AUTH_BOOTSTRAP.GET_AUTH_DATA(email)` (esto opera en *STATE 1 / BOOTSTRAP*). Oracle resuelve y retorna el hash bcrypt, estado e intentos del usuario.
3. **BCrypt:** Spring ejecuta `BCrypt.matches()` comparando la contraseña plana con el hash extraído.
4. **Validación de Estados:** Spring verifica que la cuenta no esté en estados `BLOQUEADO` ni `INACTIVO`. Reinicia contadores de intentos fallidos si fue exitoso, o los incrementa si falló.
5. **JWT:** Se genera un token JWT *Stateless*. Su subject (`sub`) es exclusivamente el `idUsuario`. No contiene claims de roles, tenants ni scopes.
6. **Resolución de Contexto (Peticiones Posteriores):** 
   - El cliente hace una petición HTTP a una ruta de negocio incluyendo el JWT y el Header `X-Assignment-Id: 105`.
   - Spring intercepta la petición, verifica la firma del JWT y obtiene el `id_usuario`.
   - Llama a `ContextService` el cual consulta la vista `USUARIO_ASIGNACIONES` (Oracle inyecta RLS de Bootstrap permitiendo ver solo las asignaciones propias).
   - Se obtiene la configuración del *Assignment* (id_rol, id_organizacion, id_propiedad, id_unidad).
   - Spring llama a `PKG_SAED_SESSION.SET_CONTEXT(id_usuario, id_asignacion)`, elevando la sesión en Oracle a **STATE 2 / BUSINESS**.
   - Se libera la ejecución hacia el controlador de Spring para acceso de negocio.

## 2. Usuario vs Persona

- **PERSONA:** Entidad central de información demográfica e identificativa legal (Nombre, Documento, Tipo de Persona, Fecha de nacimiento, Correo personal). Se mapea a la tabla `PERSONAS`.
- **USUARIO:** Representa exclusivamente la credencial de acceso digital (Email de login, Password Hash, Estados de sesión, Intentos de login, Timestamp de bloqueo). Se mapea a la tabla `USUARIOS`. La relación es `1:1`.
- **Estados de Usuario:** `PENDIENTE_VERIFICACION`, `ACTIVO`, `BLOQUEADO`, `INACTIVO`.

## 3. Asignaciones (Assignments)

La tabla `USUARIO_ASIGNACIONES` vincula la identidad, el rol y la dimensionalidad física/lógica:
`id_usuario` + `id_rol` + `id_organizacion` + `id_propiedad` + `id_unidad`.

**Reglas de validación (Derivadas de restricciones físicas):**
- Un usuario puede tener *N* asignaciones activas (ej: Admin en Org A, Residente en Org B).
- La trampa del NULL (ej. propiedades NULL para alcance ORGANIZACION) está protegida por un índice único (UIX) de Oracle usando `NVL(id_organizacion, -1)`. No pueden existir dos asignaciones exactas idénticas activas.
- Solamente se admiten estados: `ACTIVA`, `INACTIVA`, `VENCIDA`, `REVOCADA`.

## 4. Roles y Scopes

Los roles NO se inventarán, dependen estrictamente de `ROLES` en V3.9:
- `SUPERADMIN`: Control total de SAED (SaaS Platform). Scope: `GLOBAL`.
- `ADMIN_ORGANIZACION`: Dueño del Tenant. Scope: `ORGANIZACION`.
- `ADMIN_GENERAL`: Administrador delegado del Tenant. Scope: `ORGANIZACION`.
- `PROPIETARIO`: Propietario fiduciario o inversor. Scope: `PROPIEDADES_SELECCIONADAS`.
- `ADMIN_PROPIEDAD`: Administrador de un conjunto/edificio. Scope: `PROPIEDAD`.
- `PORTERO` / `VIGILANTE`: Operativo. Scope: `PROPIEDAD`.
- `RESIDENTE` / `PROPIETARIO_UNIDAD`: Final. Scope: `UNIDAD`.

**RBAC vs Scope:** 
RBAC (la tabla `ROL_PERMISO`) define *qué* puede hacer (Ej: Crear PQR). El *Scope* define *dónde* puede hacerlo (Ej: En qué propiedad). Oracle RLS cruza ambos nativamente utilizando `SYS_CONTEXT`. 

## 5. Selección de Contexto (Context Bleed Prevention)

El Backend expondrá `GET /api/v1/me/contexts`. Este endpoint listará todos los Assignments `ACTIVOS` del usuario (usando `STATE 1 BOOTSTRAP`). 
El frontend obligará al usuario a escoger uno de estos Contextos. Al hacer una llamada de negocio, el cliente provee el ID resultante en el Header HTTP `X-Assignment-Id`. Ni el frontend ni el token JWT dictarán qué rol tiene, toda la responsabilidad recae en el mapeo interno hacia `USUARIO_ASIGNACIONES`.

## 6. Organización

Tenant lógico. Tabla `ORGANIZACIONES`.
- **Estados:** `ACTIVA`, `SUSPENDIDA`, `INACTIVA`.
- Propiedades adicionales (*branding*, *moneda*) administradas mediante `ORGANIZACION_CONFIGURACION` en estructura clave-valor.
- Solo el `ADMIN_ORGANIZACION` u otros administradores explícitamente comisionados (`ADMIN_GENERAL`) pueden modificar esto si su Scope se lo permite.

## 7. Membresías

Unidas a la tabla `MEMBRESIAS` y `PLANES`.
- **Ciclo Comercial:** 
  1. `PRUEBA` (Trial) -> Mapea a un Plan Demo.
  2. `ACTIVA` (Active) -> Pago recurrente funcional.
  3. `SUSPENDIDA` (Suspended) -> Falta de pago, detiene todas las transacciones operativas pero preserva lectura (restringida mediante un Hook/Intercepción en RLS o Spring).
  4. `EXPIRADA` (Expired) -> Similar a suspendida pero por finalización de contrato temporal.
  5. `CANCELADA` (Cancelled) -> Baja definitiva (Soft delete).
- Solo existe una sola membresía VIGENTE (estado PRUEBA o ACTIVA) por organización (garantizado por el índice `UIX_MEMBRESIAS_VIGENTE`). 

## 8. Propiedades y Administradores

- **Propiedades (1 a N):** La tabla `PROPIEDADES` tiene la foránea `id_organizacion` (indirectamente vía `ORGANIZACION_PROPIEDAD` según el script de estructura, para permitir topología 1:N).
- **Modelo de Administración Flexible:** El cliente decide. Un `ADMIN_ORGANIZACION` puede crear un `ADMIN_GENERAL` que asuma todo el Tenant, o bien múltiples `ADMIN_PROPIEDAD` donde la asignación posee `id_propiedad` específico e ignora a las demás, aislándolos gracias a Oracle RLS. Nunca se codifica jerarquía estricta (ej. `if(rol == general)`), todo fluye a través de la política `SYS_CONTEXT`.
