# SAED 2.0 — V4.1 TEST PLAN

## Pruebas Requeridas

1. **State 0 (NONE) - Sin Acceso**
   - Ejecutar un `SELECT` a `USUARIOS` sin haber llamado a nada.
   - **Esperado:** 0 filas devueltas (RLS = `1=0`).

2. **State 1 (BOOTSTRAP) - Inicialización**
   - Llamar a `PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(mi_id)`.
   - Ejecutar `SELECT * FROM USUARIOS`.
   - **Esperado:** Exactamente 1 fila (mi usuario).
   - Ejecutar `SELECT * FROM USUARIOS WHERE id_usuario != mi_id`.
   - **Esperado:** 0 filas.
   - Ejecutar `SELECT * FROM USUARIO_ASIGNACIONES`.
   - **Esperado:** Solo mis asignaciones.
   - Ejecutar `SELECT * FROM PROPIEDADES`.
   - **Esperado:** 0 filas (Negocio bloqueado).

3. **State 2 (BUSINESS) - Transición Exitosa**
   - Con estado `BOOTSTRAP`, llamar a `PKG_SAED_SESSION.SET_CONTEXT(...)` con parámetros válidos.
   - **Esperado:** Éxito.
   - Ejecutar `SELECT * FROM PROPIEDADES`.
   - **Esperado:** Solo propiedades de la organización seteada.

4. **Transición Fallida - Spoofing**
   - Con estado `BOOTSTRAP`, llamar a `PKG_SAED_SESSION.SET_CONTEXT(...)` inyectando una `id_organizacion` ajena o un `rol` no correspondiente a la asignación.
   - **Esperado:** ORA Exception, contexto denegado.

5. **State 3 (CLEARED) - Context Bleed Prevention**
   - Llamar a `PKG_SAED_SESSION.CLEAR_CONTEXT()`.
   - Ejecutar `SELECT * FROM USUARIOS`.
   - **Esperado:** 0 filas.

Estas pruebas aseguran que las barreras establecidas en la V3.9 por el RLS se flexibilizan **únicamente** en la tabla de autenticación temporal y **exclusivamente** para la identidad en curso, garantizando que el diseño Zero Trust no se vea comprometido.
