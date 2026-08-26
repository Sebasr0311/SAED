# PHASE 1B - CONTEXT STATE MACHINE

Este documento define los estados de contexto que rigen el ciclo de vida de una petición HTTP en SAED 2.0 y cómo Spring Boot interactúa con Oracle `PKG_SAED_SESSION`.

## 1. Definición de Estados

### STATE 0: ANONYMOUS
- **Descripción:** Petición HTTP sin JWT válido.
- **Spring Context:** Vacío o Anónimo.
- **Oracle Context:** Vacío. Conexión limpia (pool).
- **Alcance RLS:** `1=2` en todas las tablas sensibles.
- **Acciones válidas:** Login (`/api/v1/auth/login`).

### STATE 1: IDENTITY BOUND
- **Descripción:** Petición HTTP con JWT válido, pero sin un Tenant explícito seleccionado.
- **Spring Context:** Contiene el `id_usuario`.
- **Oracle Context:** Inyectado mediante `PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(id_usuario)`.
- **Variables Oracle Activas:** `USUARIO_ID` = `id_usuario`. Las variables jerárquicas (`ORGANIZACION_ID`, `PROPIEDAD_ID`, `ROL_CODIGO`) son **NULL**.
- **Alcance RLS:** 
  - Consultas a tablas tenant-dependent (`PROPIEDADES`, `CONTRATOS`): `1=2` (Protección contra Context Bleed).
  - Consultas a tablas de Bootstrap (`USUARIO_ASIGNACIONES`, `ROLES`): Limitadas al `id_usuario` actual.
- **Acciones válidas:** Consultar asignaciones disponibles (`/api/v1/auth/assignments`), refrescar token.

### STATE 2: TENANT BOUND (Fully Authorized)
- **Descripción:** Petición HTTP con JWT válido Y un `X-Assignment-Id` válido y verificado.
- **Spring Context:** Contiene el `id_usuario` y un objeto `SaedSecurityContext` con el Tenant.
- **Oracle Context:** Inyectado mediante `PKG_SAED_SESSION.SET_CONTEXT(usuario, org, prop, rol)`.
- **Variables Oracle Activas:** `USUARIO_ID`, `ORGANIZACION_ID`, `PROPIEDAD_ID`, `ROL_CODIGO`.
- **Alcance RLS:** El motor RLS filtra cada query usando las funciones de `PKG_SAED_SECURITY_RLS` (ej. `FN_FILTRO_ORGANIZACION`, `FN_FILTRO_PROPIEDAD`).
- **Acciones válidas:** Cualquier operación de negocio autorizada para el Rol actual.

## 2. Transiciones (Transitions)

1. **HTTP Request Inicia:** Connection obtenida del HikariCP (STATE 0).
2. **Spring Security Filter:** Verifica JWT. Pasa al STATE 1 lógicamente en Spring.
3. **`SaedDataSourceProxy` (V4.0):** Al ceder la conexión, inyecta STATE 1 en Oracle llamando a `SET_BOOTSTRAP_CONTEXT`.
4. **`AssignmentInterceptor` (Phase 1B):**
   - Lee `X-Assignment-Id`.
   - Consulta BBDD: Valida que Assignment pertenezca al Usuario.
   - Llama internamente (en la misma conexión) a un SP o instrucción SQL: `PKG_SAED_SESSION.SET_CONTEXT(id_usuario, id_org, ...)`.
   - La conexión pasa a STATE 2.
5. **Fin del Request:** HikariCP recupera la conexión. Obligatorio llamar a `PKG_SAED_SESSION.CLEAR_CONTEXT()` (estado vuelve a STATE 0).

## 3. Fallas en Transición
Si `AssignmentInterceptor` detecta que el `X-Assignment-Id` no pertenece al usuario:
- Cancela la petición inmediatamente.
- Lanza `AccessDeniedException`.
- Mantiene STATE 1.
- Registra intento de escalada de privilegios.
