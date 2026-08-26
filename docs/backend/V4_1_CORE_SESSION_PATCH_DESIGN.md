# SAED 2.0 — V4.1 CORE SESSION PATCH DESIGN

## 1. Análisis del Deadlock Circular
En la arquitectura V3.9, la base de datos implementa RLS (VPD) estricto. La función de inicialización de sesión `PKG_SAED_SESSION.SET_CONTEXT` debe validar el estado del usuario. Para ello, realiza un `SELECT estado FROM USUARIOS WHERE id_usuario = p_id_usuario`.
Sin embargo, dado que `PKG_SAED_SESSION` se ejecuta con `AUTHID DEFINER` (bajo el esquema `SAED_V39_FINAL_TEST`), la consulta dispara la política `POL_RLS_SEC_USR` (función `FN_FILTRO_USUARIOS`).
Como la sesión apenas se está inicializando, la variable de contexto `ID_ORGANIZACION` es nula. La función de política interpreta esto como un intento de acceso sin contexto y retorna `1=0`.
El resultado es que `SET_CONTEXT` sufre un `NO_DATA_FOUND` (ORA-20082) siempre, imposibilitando el acceso inicial.

## 2. Alternativas Técnicas

### Alternativa A: Máquina de Estados en el Contexto (Recomendada)
Introducir una variable `STATE` en `SAED_CTX` con valores: `NONE`, `BOOTSTRAP`, `BUSINESS`.
Se añade un procedimiento `SET_BOOTSTRAP_CONTEXT(p_id_usuario)` que solo establece la identidad y pone `STATE = 'BOOTSTRAP'`.
Las funciones RLS (`FN_FILTRO_USUARIOS` y `FN_FILTRO_ASIGNACION`) se parchean para permitir leer la propia identidad y asignaciones si `STATE = 'BOOTSTRAP'`.
Una vez elegida la asignación, se llama a `SET_CONTEXT`, el cual ya puede leer `USUARIOS` para validar, y transiciona a `STATE = 'BUSINESS'`, activando el RLS completo.

### Alternativa B: Elevación de Privilegios Interna
Modificar `PKG_SAED_SESSION.SET_CONTEXT` para que en lugar de hacer `SELECT` directo a `USUARIOS`, invoque una nueva función atómica en `SAED_SEC_MASTER.PKG_AUTH_BOOTSTRAP.IS_USER_ACTIVE(p_id)`.
Para listar asignaciones, crear `GET_USER_ASSIGNMENTS` retornando un `SYS_REFCURSOR` desde `SAED_SEC_MASTER`.
**Desventaja:** `SYS_REFCURSOR` rompe el estándar de Spring (Dificulta uso de JPA/JDBC mappings). Requiere extender significativamente el bypass de seguridad.

### Alternativa C: Vistas Seguras de Identidad
No modificar el RLS, pero crear vistas `VW_MY_USER` y `VW_MY_ASSIGNMENTS` en el esquema `SAED_SEC_MASTER` que eximan el RLS pero filtren por el `ID_USUARIO` del JWT. 
**Desventaja:** Duplica la superficie de la base de datos y obliga a Spring a apuntar a vistas en lugar de las tablas originales, complicando la arquitectura ORM.

## 3. Alternativa Recomendada: A (State Machine RLS)
Se recomienda la **Alternativa A** porque formaliza matemáticamente los estados de inicialización solicitados por el diseño (STATE 0, 1, 2, 3), mantiene la inmutabilidad física de los datos, no otorga privilegios elevados a la aplicación, y permite que Spring consulte las tablas base naturalmente usando sus repositorios originales.

## 4. Diseño de la Solución (V4.1)

### Modificaciones Requeridas en V4.1
1. **`PKG_SAED_SESSION` (Reemplazo del BODY y SPEC)**:
   - Nuevo procedimiento: `SET_BOOTSTRAP_CONTEXT(p_id_usuario)`.
   - Modificación de `SET_CONTEXT` para que exija un `STATE` actual de `BOOTSTRAP`, valide todo, y transicione a `BUSINESS`.
   - Modificación de `CLEAR_CONTEXT` para resetear al `STATE = NONE`.

2. **`PKG_SAED_SECURITY_RLS` (Reemplazo del BODY)**:
   - `FN_FILTRO_USUARIOS`: Si `STATE = 'BOOTSTRAP'`, retornar `'id_usuario = ' || v_usr`.
   - `FN_FILTRO_ASIGNACION`: Si `STATE = 'BOOTSTRAP'`, retornar `'id_usuario = ' || v_usr`.
   - En estado `BUSINESS`, la lógica estricta de la organización (V3.9) permanece intacta.

### Flujo de Estados
- **STATE 0 (NO AUTHENTICATED)**: RLS bloquea todo (`1=0`).
- **STATE 1 (BOOTSTRAP)**: Solo el dueño de un JWT válido puede establecer este estado. El RLS restringe la visión *exclusivamente* a su propia fila en `USUARIOS` y `USUARIO_ASIGNACIONES`. Negocio sigue bloqueado (`1=0`).
- **STATE 2 (FULL TENANT CONTEXT)**: Obtenido mediante `SET_CONTEXT`. RLS filtra por Organización.
- **STATE 3 (CLEARED)**: Vuelve a STATE 0.
