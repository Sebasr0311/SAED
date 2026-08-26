# SAED 2.0 — V4.1 ROLLBACK PLAN

Si por alguna razón la V4.1 produce comportamientos no deseados en producción y se debe regresar a V3.9, se ha proveído el script `V4.1__core_session_patch_ROLLBACK.sql`.

## Procedimiento de Rollback
1. Detener las instancias de Spring Boot (SAED_APP).
2. Ejecutar como DBA o dueño de esquema el script:
   `sqlplus -S SAED_V39_FINAL_TEST/saed2026 @database/migrations/V4.1__core_session_patch_ROLLBACK.sql`
3. Esto restaurará el paquete `PKG_SAED_SESSION` para que dependa exclusivamente de `SET_CONTEXT` y restaurará `PKG_SAED_SECURITY_RLS` a su estructura plana sin verificación de STATE.
4. Reiniciar Spring Boot habiendo revertido el commit de la rama `feature/db-v4.1-session-bootstrap`.

## Qué revierte exactamente
- Elimina `SET_BOOTSTRAP_CONTEXT`.
- Elimina la comprobación de estados lógicos (ANONYMOUS, BOOTSTRAP, BUSINESS, CLEARING) en `FN_FILTRO_...`.
- Reintroduce el deadlock de "usuario no existe" (ORA-20082) original de V3.9 al intentar conectarse sin Organización previa.

## Elementos No Revertibles
- El parche en la lógica es 100% idempotente y revertible a nivel código PL/SQL. No alteró datos de tablas de negocio ni destruyó tablas existentes.
- Es una migración de metadatos (Package Bodies), por ende el rollback es seguro y atómico.
