# SAED 2.0 — Modelo de Seguridad de Auditoría y RLS

**Fecha:** 01 de Septiembre de 2026  
**Fase:** `Fase 5 — Auditoría y Trazabilidad Centralizada`  

---

## 1. Inmutabilidad en Base de Datos (Append-Only)

La tabla `AUDITORIA_LOG` está blindada contra manipulación en Oracle XE:

- **Trigger `TRG_AUDITORIA_INMUTABLE`:**
  ```sql
  CREATE OR REPLACE TRIGGER TRG_AUDITORIA_INMUTABLE
  BEFORE UPDATE OR DELETE ON AUDITORIA_LOG
  BEGIN
      RAISE_APPLICATION_ERROR(-20099, 'Seguridad: No está permitido modificar ni eliminar registros de auditoría.');
  END;
  /
  ```
- **Resultado:** Cualquier intento de `UPDATE` o `DELETE` (incluso por usuarios con privilegios elevados dentro de la aplicación) es abortado con error `ORA-20099`.

---

## 2. Aislamiento Multi-Tenant (Oracle Virtual Private Database / RLS)

Se aplica la política de seguridad `POL_AUDITORIA_LOG_SELECT` vinculada a `PKG_SAED_SECURITY_RLS.FN_FILTRO_PROPIEDAD`:

1. **`SUPERADMIN`:** Predicado `1=1` (acceso a todos los registros del sistema).
2. **`ADMIN_ORGANIZACION`:** Predicado `ID_ORGANIZACION = SYS_CONTEXT('SAED_CTX', 'ID_ORGANIZACION')` o propiedades de su organización.
3. **`ADMIN_PROPIEDAD`:** Predicado `ID_PROPIEDAD = SYS_CONTEXT('SAED_CTX', 'ID_PROPIEDAD')` (únicamente registros de su copropiedad).
4. **`RESIDENTE` / `PORTERO`:** Predicado `ID_USUARIO = SYS_CONTEXT('SAED_CTX', 'ID_USUARIO')` (solo sus propios eventos).
5. **Sesiones Anónimas:** Predicado `1=0` (acceso bloqueado por defecto).

---

## 3. Sanitización de Secretos y Cumplimiento

`AuditSanitizer` previene la fuga de credenciales en `AUDITORIA_LOG` y logs de aplicación:

- **Campos Enmascarados:** `password`, `contrasena`, `token`, `jwt`, `secret`, `signature`, `checksum`, `cvv`, `apiKey`, `credentials`, `authorization`.
- **Formato:** Reemplazados sistemáticamente por `"[PROTECTED]"`.
- **Estructuras Soportadas:** JSON strings, Mapas anidados, POJOs, Listas y Arrays.
