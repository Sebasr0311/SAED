-- =============================================================================
-- SAED 2.0 - V5.68: ADMINISTRATOR ACCOUNT CREATION (security schema)
-- =============================================================================
-- RUN AS THE SECURITY SCHEMA OWNER (SAED_SEC_MASTER in production), not as the application schema owner.
-- The "__sec_" file-name marker tells the isolated installer to execute this file as that principal.
-- Requires V5.65 (INSERT on USUARIOS, SELECT on PERSONAS).
--
-- The USUARIOS row-level policy (V5.64, FN_FILTRO_USUARIOS) shows an organization or property administrator only the
-- accounts that already hold an assignment in the organization, and its check option applies to inserts too. The
-- first row of a brand-new account has no assignment yet, so the administrator's own insert is refused with
-- ORA-28115 and no administrator could register a porter, resident or owner. USUARIOS visibility must not be widened,
-- so the account row is created here with definer rights after validating from the caller's own session context:
--   1. an authenticated tenant session (not anonymous / clearing / bootstrap) with a user and an organization;
--   2. the session role is ADMIN_PROPIEDAD or ADMIN_ORGANIZACION (the platform SUPERADMIN is not subject to the
--      policy and never needs this procedure);
--   3. the caller holds an ACTIVE assignment of that role in force today in the context organization (and, for a
--      property administrator, in the context property);
--   4. the person exists and has no account yet.
-- The procedure only creates the account row and returns its id. The scope of the target (organization, property,
-- unit, role) is authorized by the application and the assignment is inserted by the administrator under the
-- row-level policies; an account without an assignment grants no access.
-- Error codes: -20491 invalid context/arguments, -20492 role, -20493 administrator assignment, -20494 unknown
-- person, -20495 person already has an account.
-- Dates use SYSDATE only. Idempotent (CREATE OR REPLACE; synonyms and grants are repeatable).

DECLARE
    v_app_owner VARCHAR2(128);
    v_count     NUMBER;
BEGIN
    SELECT TABLE_OWNER INTO v_app_owner FROM USER_SYNONYMS WHERE SYNONYM_NAME = 'USUARIO_ASIGNACIONES';
    FOR t IN (SELECT COLUMN_VALUE AS NAME FROM TABLE(SYS.ODCIVARCHAR2LIST(
                  'USUARIOS', 'ROLES', 'PERSONAS'))) LOOP
        SELECT COUNT(1) INTO v_count FROM USER_SYNONYMS WHERE SYNONYM_NAME = t.NAME;
        IF v_count = 0 THEN
            EXECUTE IMMEDIATE 'CREATE SYNONYM ' || t.NAME || ' FOR '
                || DBMS_ASSERT.ENQUOTE_NAME(v_app_owner, FALSE) || '.' || t.NAME;
        END IF;
    END LOOP;
END;
/

CREATE OR REPLACE PROCEDURE SP_REGISTRAR_CUENTA(
    p_id_persona     IN  NUMBER,
    p_nombre_usuario IN  VARCHAR2,
    p_email          IN  VARCHAR2,
    p_hash_password  IN  VARCHAR2,
    p_id_usuario     OUT NUMBER
) AUTHID DEFINER AS
    v_state    VARCHAR2(30) := NVL(SYS_CONTEXT('SAED_CTX', 'STATE'), 'ANONYMOUS');
    v_rol      VARCHAR2(30) := SYS_CONTEXT('SAED_CTX', 'ROL_CODIGO');
    v_usr_txt  VARCHAR2(30) := SYS_CONTEXT('SAED_CTX', 'ID_USUARIO');
    v_org_txt  VARCHAR2(30) := SYS_CONTEXT('SAED_CTX', 'ID_ORGANIZACION');
    v_prop_txt VARCHAR2(30) := SYS_CONTEXT('SAED_CTX', 'ID_PROPIEDAD');
    v_usr      NUMBER;
    v_org      NUMBER;
    v_prop     NUMBER;
    v_count    NUMBER;
BEGIN
    IF v_state IN ('ANONYMOUS', 'CLEARING', 'BOOTSTRAP') OR v_usr_txt IS NULL OR v_org_txt IS NULL
       OR p_id_persona IS NULL OR p_nombre_usuario IS NULL OR p_hash_password IS NULL THEN
        RAISE_APPLICATION_ERROR(-20491, 'Registro no autorizado: contexto o argumentos invalidos');
    END IF;
    IF v_rol IS NULL OR v_rol NOT IN ('ADMIN_PROPIEDAD', 'ADMIN_ORGANIZACION') THEN
        RAISE_APPLICATION_ERROR(-20492, 'Registro no autorizado: rol sin permiso para crear cuentas');
    END IF;
    IF v_rol = 'ADMIN_PROPIEDAD' AND v_prop_txt IS NULL THEN
        RAISE_APPLICATION_ERROR(-20491, 'Registro no autorizado: contexto o argumentos invalidos');
    END IF;
    v_usr := TO_NUMBER(v_usr_txt);
    v_org := TO_NUMBER(v_org_txt);
    v_prop := TO_NUMBER(v_prop_txt);

    SELECT COUNT(1) INTO v_count
    FROM USUARIO_ASIGNACIONES ua
    JOIN ROLES r ON r.ID_ROL = ua.ID_ROL
    WHERE ua.ID_USUARIO = v_usr
      AND ua.ESTADO = 'ACTIVA'
      AND ua.FECHA_INICIO <= TRUNC(SYSDATE)
      AND (ua.FECHA_FIN IS NULL OR ua.FECHA_FIN >= TRUNC(SYSDATE))
      AND r.CODIGO = v_rol
      AND ua.ID_ORGANIZACION = v_org
      AND (v_rol = 'ADMIN_ORGANIZACION' OR ua.ID_PROPIEDAD = v_prop);
    IF v_count = 0 THEN
        RAISE_APPLICATION_ERROR(-20493, 'Registro no autorizado: el solicitante no es administrador vigente');
    END IF;

    SELECT COUNT(1) INTO v_count FROM PERSONAS WHERE ID_PERSONA = p_id_persona;
    IF v_count = 0 THEN
        RAISE_APPLICATION_ERROR(-20494, 'Registro rechazado: persona desconocida');
    END IF;
    SELECT COUNT(1) INTO v_count FROM USUARIOS WHERE ID_PERSONA = p_id_persona;
    IF v_count > 0 THEN
        RAISE_APPLICATION_ERROR(-20495, 'Registro rechazado: la persona ya tiene una cuenta');
    END IF;

    INSERT INTO USUARIOS (ID_PERSONA, NOMBRE_USUARIO, EMAIL, HASH_PASSWORD, ESTADO, INTENTOS_FALLIDOS)
    VALUES (p_id_persona, p_nombre_usuario, p_email, p_hash_password, 'ACTIVO', 0)
    RETURNING ID_USUARIO INTO p_id_usuario;
END SP_REGISTRAR_CUENTA;
/

-- Same grantees as PKG_AUTH_BOOTSTRAP (the application principal of this installation).
BEGIN
    FOR g IN (SELECT DISTINCT GRANTEE FROM USER_TAB_PRIVS
              WHERE TABLE_NAME = 'PKG_AUTH_BOOTSTRAP' AND PRIVILEGE = 'EXECUTE') LOOP
        EXECUTE IMMEDIATE 'GRANT EXECUTE ON SP_REGISTRAR_CUENTA TO ' || DBMS_ASSERT.ENQUOTE_NAME(g.GRANTEE, FALSE);
    END LOOP;
END;
/
