# PHASE 1B TEST DATA BLOCKER

## 1. Constraint o política que bloquea
Las políticas de seguridad a nivel de filas (RLS) en las tablas base, específicamente `POL_RLS_SEC_ADM` asociada a la tabla `ADMINISTRADORES_SAED`, y `POL_RLS_ORG_ORGANIZACIONES` asociada a `ORGANIZACIONES`. 
La restricción `update_check => TRUE` configurada en `DBMS_RLS.ADD_GROUPED_POLICY` impide realizar sentencias `INSERT` si el registro resultante no cumple el predicado de seguridad para la sesión actual.

## 2. SQL exacto que falla
```sql
INSERT INTO ORGANIZACIONES (ID_ORGANIZACION, NOMBRE, IDENTIFICACION_FISCAL, EMAIL_CONTACTO, PAIS, ESTADO)
VALUES (1000, 'Org Test A', '900000000-1', 'contact@orga.com', 'Colombia', 'ACTIVA');

INSERT INTO ADMINISTRADORES_SAED (ID_USUARIO, ESTADO) VALUES (1000, 'ACTIVO');
```

## 3. Código ORA obtenido
```text
ORA-28115: policy with check option violation
```

## 4. Objeto Oracle responsable
El paquete `PKG_SAED_SECURITY_RLS` provee las funciones `FN_FILTRO_ORGANIZACION` y `FN_FILTRO_GLOBAL_MUTATE`. Ambas exigen que el contexto de sesión contenga el rol `SUPERADMIN` (`v_rol = 'SUPERADMIN'`) para retornar `NULL` (acceso total).
Sin embargo, para obtener el rol `SUPERADMIN` utilizando la API oficial (`PKG_SAED_SESSION.SET_CONTEXT`), este último realiza la siguiente comprobación:
```sql
IF p_rol_codigo = 'SUPERADMIN' THEN
    SELECT COUNT(*) INTO v_valido FROM ADMINISTRADORES_SAED WHERE id_usuario = p_id_usuario AND estado = 'ACTIVO';
...
```

## 5. Por qué no debe modificarse
Se ha generado un **Catch-22 (Dependencia Cíclica)** absoluto:
- Para insertar el primer `SUPERADMIN` en `ADMINISTRADORES_SAED`, la sesión necesita tener activo el contexto `SUPERADMIN`.
- Para tener activo el contexto `SUPERADMIN`, el usuario debe estar previamente registrado en `ADMINISTRADORES_SAED`.

Bajo las estrictas directivas de esta iteración:
- Está prohibido alterar la definición de `PKG_SAED_SESSION`.
- Está prohibido alterar `PKG_SAED_SECURITY_RLS`.
- Está prohibido alterar las políticas usando `DBMS_RLS.DROP_POLICY`.
- Está prohibido inyectar el privilegio de sistema `EXEMPT ACCESS POLICY`.
- Está prohibido asociar el namespace `SAED_CTX` a un paquete falso para bypassear la seguridad.

Por lo tanto, es algorítmica y arquitectónicamente **imposible** inyectar datos de prueba iniciales de forma segura.

## 6. Alternativas
1. **Conceder `EXEMPT ACCESS POLICY` temporalmente:** Permitir que el owner (`SAED_V39_FINAL_TEST`) se salte sus propias políticas RLS exclusivamente durante la ejecución del script de *seed*.
2. **Backdoor en `PKG_SAED_SESSION`:** Agregar un flag temporal o ambiente de desarrollo que permita invocar `DBMS_SESSION.SET_CONTEXT('SAED_CTX', 'ROL_CODIGO', 'SUPERADMIN')` sin validar contra `ADMINISTRADORES_SAED`.
3. **Migración Seed de Producción:** Ejecutar el insert inicial como parte de la migración original V3.9 desactivando temporalmente `check_option` antes de que se habilite el RLS de forma rígida, lo que requiere modificar el baseline (prohibido actualmente).

## 7. Recomendación
La alternativa más viable, rastreable y menos intrusiva para propósitos exclusivos de pruebas (Integration/Adversarial Testing) en el ciclo CI/CD es la **Alternativa 1**.

Recomiendo autorizar la ejecución del siguiente bloque para envolver los scripts de *seed* (asumiendo conexión con rol DBA o permisos `GRANT` temporalmente):
```sql
-- Desde un usuario DBA:
GRANT EXEMPT ACCESS POLICY TO SAED_V39_FINAL_TEST;

-- Ejecutar seed_phase_1b_oracle.sql como SAED_V39_FINAL_TEST
-- ...

-- Desde un usuario DBA:
REVOKE EXEMPT ACCESS POLICY FROM SAED_V39_FINAL_TEST;
```
Esto certifica que la arquitectura funcional y los paquetes V3.9/V4.0/V4.1 permanecen **intactos e inmutables**, respetando el diseño Zero-Trust.
