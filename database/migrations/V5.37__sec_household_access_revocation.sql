-- =============================================================================
-- SAED 2.0 - V5.37: VALIDATED HOUSEHOLD ACCESS REVOCATION (security schema)
-- =============================================================================
-- RUN AS THE SECURITY SCHEMA OWNER (SAED_SEC_MASTER in production), not as the application schema owner.
-- The "__sec_" file-name marker tells the isolated installer to execute this file as that principal.
--
-- F05-01 / F05-02. A titular (RESIDENTE or PROPIETARIO assignment on the unit) who removes a non-titular household
-- member must also revoke that member's resident access to the unit. Row-level policies hide other users'
-- assignments from residents and the application never elevates the database context, so the revocation is done
-- here, with definer rights, after validating from the caller's own session context:
--   1. the session is an authenticated tenant session (not anonymous/bootstrap) of a RESIDENTE or PROPIETARIO;
--   2. the caller holds an ACTIVA RESIDENTE/PROPIETARIO assignment on that very unit;
--   3. the member was a FAMILIAR/CONVIVIENTE/OTRO of that unit and has no ACTIVO residence there any more.
-- Only RESIDENTE / RESIDENTE_CONVIVENCIA assignments of that member on that unit are revoked; every other
-- assignment and the member's account are untouched.
-- Requires V5.36 (grants). Idempotent (CREATE OR REPLACE; synonym and grants are repeatable).

DECLARE
    v_app_owner VARCHAR2(128);
    v_count     NUMBER;
BEGIN
    SELECT TABLE_OWNER INTO v_app_owner FROM USER_SYNONYMS WHERE SYNONYM_NAME = 'USUARIO_ASIGNACIONES';
    SELECT COUNT(1) INTO v_count FROM USER_SYNONYMS WHERE SYNONYM_NAME = 'RESIDENTES_UNIDAD';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE SYNONYM RESIDENTES_UNIDAD FOR '
            || DBMS_ASSERT.ENQUOTE_NAME(v_app_owner, FALSE) || '.RESIDENTES_UNIDAD';
    END IF;
END;
/

CREATE OR REPLACE PROCEDURE SP_REVOCAR_ACCESO_UNIDAD(
    p_id_unidad  IN NUMBER,
    p_id_persona IN NUMBER
) AUTHID DEFINER AS
    v_state   VARCHAR2(30) := NVL(SYS_CONTEXT('SAED_CTX', 'STATE'), 'ANONYMOUS');
    v_rol     VARCHAR2(30) := SYS_CONTEXT('SAED_CTX', 'ROL_CODIGO');
    v_usr_txt VARCHAR2(30) := SYS_CONTEXT('SAED_CTX', 'ID_USUARIO');
    v_usr     NUMBER;
    v_count   NUMBER;
BEGIN
    IF v_state IN ('ANONYMOUS', 'CLEARING', 'BOOTSTRAP') OR v_usr_txt IS NULL
       OR p_id_unidad IS NULL OR p_id_persona IS NULL THEN
        RAISE_APPLICATION_ERROR(-20461, 'Revocacion no autorizada: contexto invalido');
    END IF;
    IF v_rol IS NULL OR v_rol NOT IN ('RESIDENTE', 'PROPIETARIO') THEN
        RAISE_APPLICATION_ERROR(-20462, 'Revocacion no autorizada: rol no titular');
    END IF;
    v_usr := TO_NUMBER(v_usr_txt);

    SELECT COUNT(1) INTO v_count
    FROM USUARIO_ASIGNACIONES ua
    JOIN ROLES r ON r.ID_ROL = ua.ID_ROL
    WHERE ua.ID_USUARIO = v_usr
      AND ua.ID_UNIDAD = p_id_unidad
      AND ua.ESTADO = 'ACTIVA'
      AND r.CODIGO IN ('RESIDENTE', 'PROPIETARIO');
    IF v_count = 0 THEN
        RAISE_APPLICATION_ERROR(-20463, 'Revocacion no autorizada: el solicitante no es titular de la unidad');
    END IF;

    SELECT COUNT(1) INTO v_count
    FROM RESIDENTES_UNIDAD
    WHERE ID_UNIDAD = p_id_unidad
      AND ID_PERSONA = p_id_persona
      AND TIPO_RESIDENTE IN ('FAMILIAR', 'CONVIVIENTE', 'OTRO');
    IF v_count = 0 THEN
        RAISE_APPLICATION_ERROR(-20464, 'Revocacion no autorizada: la persona no es miembro del hogar');
    END IF;

    SELECT COUNT(1) INTO v_count
    FROM RESIDENTES_UNIDAD
    WHERE ID_UNIDAD = p_id_unidad
      AND ID_PERSONA = p_id_persona
      AND ESTADO = 'ACTIVO';
    IF v_count > 0 THEN
        RAISE_APPLICATION_ERROR(-20465, 'Revocacion no autorizada: la residencia sigue activa');
    END IF;

    UPDATE USUARIO_ASIGNACIONES
       SET ESTADO = 'INACTIVA', FECHA_FIN = TRUNC(SYSDATE)
     WHERE ID_UNIDAD = p_id_unidad
       AND ESTADO = 'ACTIVA'
       AND ID_USUARIO IN (SELECT ID_USUARIO FROM USUARIOS WHERE ID_PERSONA = p_id_persona)
       AND ID_ROL IN (SELECT ID_ROL FROM ROLES WHERE CODIGO IN ('RESIDENTE', 'RESIDENTE_CONVIVENCIA'))
       AND ID_USUARIO <> v_usr;
END SP_REVOCAR_ACCESO_UNIDAD;
/

-- Same grantees as PKG_AUTH_BOOTSTRAP (the application principal of this installation).
BEGIN
    FOR g IN (SELECT DISTINCT GRANTEE FROM USER_TAB_PRIVS
              WHERE TABLE_NAME = 'PKG_AUTH_BOOTSTRAP' AND PRIVILEGE = 'EXECUTE') LOOP
        EXECUTE IMMEDIATE 'GRANT EXECUTE ON SP_REVOCAR_ACCESO_UNIDAD TO ' || DBMS_ASSERT.ENQUOTE_NAME(g.GRANTEE, FALSE);
    END LOOP;
END;
/
