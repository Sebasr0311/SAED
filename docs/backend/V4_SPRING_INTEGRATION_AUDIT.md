# SAED 2.0 — V4 SPRING INTEGRATION AUDIT

## CURRENT STATE
El sistema intenta invocar `SaedDataSourceProxy` para inyectar el contexto de seguridad mediante `PKG_SAED_SESSION.SET_CONTEXT`.

## PROBLEMS
**INTEGRATION DEADLOCK CRÍTICO DESCUBIERTO.**
El baseline V3.9 contiene un defecto arquitectónico circular insalvable:
1. `PKG_SAED_SESSION` se ejecuta por defecto con `AUTHID DEFINER`.
2. Al llamar a `SET_CONTEXT(id_usuario, 0)`, el paquete intenta ejecutar `SELECT estado INTO v_estado_usr FROM USUARIOS WHERE id_usuario = p_id_usuario`.
3. Al ejecutarse como el definidor (`SAED_V39_FINAL_TEST`), la política RLS `POL_RLS_SEC_USR` entra en acción.
4. Puesto que el contexto apenas se está construyendo, `SYS_CONTEXT('SAED_CTX', 'ID_ORGANIZACION')` es nulo, causando que `FN_FILTRO_USUARIOS` retorne `1=0`.
5. La consulta a `USUARIOS` siempre retorna cero filas, originando que `SET_CONTEXT` lance la excepción `ORA-20082: Seguridad: El id_usuario especificado no existe.` invariablemente, incluso si el usuario existe y es válido.

Esta falla es universal. Incluso si un DBA (`SYSDBA`) invoca `SET_CONTEXT`, el paquete sigue ejecutándose como el definidor (sujeto a RLS) y falla. Es matemáticamente imposible establecer un contexto.

## FILES TO CHANGE
No se puede continuar con Spring Boot.

## FILES TO KEEP
- N/A

## SECURITY RISKS
- Denial of Service absoluto. El sistema no puede inicializarse para ningún usuario, jamás.

## MIGRATION PLAN
**DETENIDO.**
Según la regla: *"NO parchear V3.9 silenciosamente. Si aparece una incompatibilidad: DETENER -> DOCUMENTAR -> NO parchear V3.9 silenciosamente"*.
Dado que las reglas impiden explícitamente modificar `PKG_SAED_SESSION`, `PKG_SAED_SECURITY_RLS` o los objetos de V3.9, no existe forma técnica de solventar este deadlock desde la V4.0 sin tocar dichos objetos. Se requiere autorización explícita para crear una migración de parche (Ej. `V4.1__fix_session_deadlock.sql`) que reemplace el cuerpo de `PKG_SAED_SESSION` o `PKG_SAED_SECURITY_RLS`.
