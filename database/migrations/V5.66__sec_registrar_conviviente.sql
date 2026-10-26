-- =============================================================================
-- SAED 2.0 - V5.66: VALIDATED COHABITANT REGISTRATION (security schema)
-- =============================================================================
-- RUN AS THE SECURITY SCHEMA OWNER (SAED_SEC_MASTER in production), not as the application schema owner.
-- The "__sec_" file-name marker tells the isolated installer to execute this file as that principal.
-- Requires V5.65 (grants).
--
-- A resident must be able to register a cohabitant account, but the row-level policies only admit the caller's own
-- USUARIOS / USUARIO_ASIGNACIONES rows (ORA-28115 on insert) and USUARIOS visibility must not be widened. The
-- registration therefore runs here, with definer rights, after validating from the caller's own session context:
--   1. an authenticated tenant session (not anonymous / clearing / bootstrap) of a RESIDENTE or PROPIETARIO;
--   2. the caller holds a titular assignment on that unit, IN FORCE today, in the context property;
--   3. the caller is not, as a person, an ACTIVE household member (FAMILIAR/CONVIVIENTE/OTRO) of the unit without a
--      titular residence row (a titular assignment with no residence row at all is accepted);
--   4. the unit has room under LIMITE_CONVIVIENTES_POR_UNIDAD (property setting, default 4; a missing, non-numeric or
--      non-positive value means the default). The unit row is locked so concurrent registrations serialize;
--   5. an existing target account must belong to the given person and must not be the caller.
-- Then it creates the account (unless an existing one was given) and its RESIDENTE_CONVIVENCIA assignment for the
-- unit (same organization/property as the caller's titular assignment), and returns the user id.
-- Error codes: -20471 invalid context/arguments, -20472 role, -20473 titular assignment, -20474 titular residence,
-- -20475 unit outside the context property, -20476 quota reached, -20477 invalid target account / role unavailable.
-- Dates use SYSDATE only. Idempotent (CREATE OR REPLACE; synonyms and grants are repeatable).

DECLARE
    v_app_owner VARCHAR2(128);
    v_count     NUMBER;
BEGIN
    SELECT TABLE_OWNER INTO v_app_owner FROM USER_SYNONYMS WHERE SYNONYM_NAME = 'USUARIO_ASIGNACIONES';
    FOR t IN (SELECT COLUMN_VALUE AS NAME FROM TABLE(SYS.ODCIVARCHAR2LIST(
                  'USUARIOS', 'ROLES', 'RESIDENTES_UNIDAD', 'UNIDADES', 'PROPIEDAD_CONFIGURACION'))) LOOP
        SELECT COUNT(1) INTO v_count FROM USER_SYNONYMS WHERE SYNONYM_NAME = t.NAME;
        IF v_count = 0 THEN
            EXECUTE IMMEDIATE 'CREATE SYNONYM ' || t.NAME || ' FOR '
                || DBMS_ASSERT.ENQUOTE_NAME(v_app_owner, FALSE) || '.' || t.NAME;
        END IF;
    END LOOP;
END;
/

CREATE OR REPLACE PROCEDURE SP_REGISTRAR_CONVIVIENTE(
    p_id_unidad            IN  NUMBER,
    p_id_persona           IN  NUMBER,
    p_id_usuario_existente IN  NUMBER,
    p_nombre_usuario       IN  VARCHAR2,
    p_email                IN  VARCHAR2,
    p_hash_password        IN  VARCHAR2,
    p_id_usuario           OUT NUMBER
) AUTHID DEFINER AS
    v_state    VARCHAR2(30) := NVL(SYS_CONTEXT('SAED_CTX', 'STATE'), 'ANONYMOUS');
    v_rol      VARCHAR2(30) := SYS_CONTEXT('SAED_CTX', 'ROL_CODIGO');
    v_usr_txt  VARCHAR2(30) := SYS_CONTEXT('SAED_CTX', 'ID_USUARIO');
    v_prop_txt VARCHAR2(30) := SYS_CONTEXT('SAED_CTX', 'ID_PROPIEDAD');
    v_usr      NUMBER;
    v_prop     NUMBER;
    v_org      NUMBER;
    v_unit_prop NUMBER;
    v_limit    NUMBER;
    v_active   NUMBER;
    v_count    NUMBER;
    v_titular  NUMBER;
    v_member   NUMBER;
    v_id_rol   NUMBER;
BEGIN
    IF v_state IN ('ANONYMOUS', 'CLEARING', 'BOOTSTRAP') OR v_usr_txt IS NULL OR v_prop_txt IS NULL
       OR p_id_unidad IS NULL OR p_id_persona IS NULL
       OR (p_id_usuario_existente IS NULL AND (p_nombre_usuario IS NULL OR p_hash_password IS NULL)) THEN
        RAISE_APPLICATION_ERROR(-20471, 'Registro no autorizado: contexto o argumentos invalidos');
    END IF;
    IF v_rol IS NULL OR v_rol NOT IN ('RESIDENTE', 'PROPIETARIO') THEN
        RAISE_APPLICATION_ERROR(-20472, 'Registro no autorizado: rol no titular');
    END IF;
    v_usr := TO_NUMBER(v_usr_txt);
    v_prop := TO_NUMBER(v_prop_txt);

    SELECT COUNT(1), MAX(ua.ID_ORGANIZACION) INTO v_count, v_org
    FROM USUARIO_ASIGNACIONES ua
    JOIN ROLES r ON r.ID_ROL = ua.ID_ROL
    WHERE ua.ID_USUARIO = v_usr
      AND ua.ID_UNIDAD = p_id_unidad
      AND ua.ID_PROPIEDAD = v_prop
      AND ua.ESTADO = 'ACTIVA'
      AND ua.FECHA_INICIO <= TRUNC(SYSDATE)
      AND (ua.FECHA_FIN IS NULL OR ua.FECHA_FIN >= TRUNC(SYSDATE))
      AND r.CODIGO IN ('RESIDENTE', 'PROPIETARIO');
    IF v_count = 0 THEN
        RAISE_APPLICATION_ERROR(-20473, 'Registro no autorizado: el solicitante no es titular vigente de la unidad');
    END IF;
    v_org := NVL(v_org, TO_NUMBER(SYS_CONTEXT('SAED_CTX', 'ID_ORGANIZACION')));

    -- The in-force titular assignment is the authority. Only an ACTIVE residence row of the caller that makes it a
    -- household member (and no titular row) denies: an assignment without any residence row is still a titular.
    SELECT NVL(SUM(CASE WHEN ru.TIPO_RESIDENTE IN ('PROPIETARIO', 'ARRENDATARIO') THEN 1 ELSE 0 END), 0),
           NVL(SUM(CASE WHEN ru.TIPO_RESIDENTE IN ('FAMILIAR', 'CONVIVIENTE', 'OTRO') THEN 1 ELSE 0 END), 0)
      INTO v_titular, v_member
    FROM RESIDENTES_UNIDAD ru
    WHERE ru.ID_UNIDAD = p_id_unidad
      AND ru.ESTADO = 'ACTIVO'
      AND ru.ID_PERSONA IN (SELECT ID_PERSONA FROM USUARIOS WHERE ID_USUARIO = v_usr);
    IF v_titular = 0 AND v_member > 0 THEN
        RAISE_APPLICATION_ERROR(-20474, 'Registro no autorizado: el solicitante es miembro del hogar, no titular');
    END IF;

    SELECT ID_PROPIEDAD INTO v_unit_prop FROM UNIDADES WHERE ID_UNIDAD = p_id_unidad FOR UPDATE;
    IF v_unit_prop <> v_prop THEN
        RAISE_APPLICATION_ERROR(-20475, 'Registro no autorizado: la unidad no pertenece a la propiedad del contexto');
    END IF;

    SELECT MAX(CASE WHEN VALIDATE_CONVERSION(TRIM(VALOR) AS NUMBER) = 1 THEN TO_NUMBER(TRIM(VALOR)) END)
      INTO v_limit
    FROM PROPIEDAD_CONFIGURACION
    WHERE ID_PROPIEDAD = v_prop AND CLAVE = 'LIMITE_CONVIVIENTES_POR_UNIDAD';
    IF v_limit IS NULL OR v_limit <= 0 THEN
        v_limit := 4;
    END IF;
    SELECT COUNT(1) INTO v_active
    FROM RESIDENTES_UNIDAD
    WHERE ID_UNIDAD = p_id_unidad
      AND ESTADO = 'ACTIVO'
      AND (FECHA_FIN IS NULL OR FECHA_FIN > TRUNC(SYSDATE))
      AND TIPO_RESIDENTE = 'CONVIVIENTE';
    IF v_active >= v_limit THEN
        RAISE_APPLICATION_ERROR(-20476, 'Registro rechazado: limite de convivientes alcanzado');
    END IF;

    SELECT COUNT(1), MAX(ID_ROL) INTO v_count, v_id_rol
    FROM ROLES WHERE CODIGO = 'RESIDENTE_CONVIVENCIA' AND ESTADO = 'ACTIVO';
    IF v_count <> 1 THEN
        RAISE_APPLICATION_ERROR(-20477, 'Registro rechazado: rol de convivencia no disponible');
    END IF;

    IF p_id_usuario_existente IS NOT NULL THEN
        SELECT COUNT(1) INTO v_count
        FROM USUARIOS
        WHERE ID_USUARIO = p_id_usuario_existente AND ID_PERSONA = p_id_persona AND ID_USUARIO <> v_usr;
        IF v_count = 0 THEN
            RAISE_APPLICATION_ERROR(-20477, 'Registro rechazado: cuenta destino invalida');
        END IF;
        p_id_usuario := p_id_usuario_existente;
    ELSE
        INSERT INTO USUARIOS (ID_PERSONA, NOMBRE_USUARIO, EMAIL, HASH_PASSWORD, ESTADO, INTENTOS_FALLIDOS)
        VALUES (p_id_persona, p_nombre_usuario, p_email, p_hash_password, 'ACTIVO', 0)
        RETURNING ID_USUARIO INTO p_id_usuario;
    END IF;

    SELECT COUNT(1) INTO v_count
    FROM USUARIO_ASIGNACIONES
    WHERE ID_USUARIO = p_id_usuario AND ID_ROL = v_id_rol AND ID_UNIDAD = p_id_unidad
      AND ID_PROPIEDAD = v_prop AND ESTADO = 'ACTIVA';
    IF v_count = 0 THEN
        INSERT INTO USUARIO_ASIGNACIONES (ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ID_UNIDAD, ESTADO,
                                          FECHA_INICIO, ASIGNADO_POR)
        VALUES (p_id_usuario, v_id_rol, v_org, v_prop, p_id_unidad, 'ACTIVA', TRUNC(SYSDATE), v_usr);
    END IF;
END SP_REGISTRAR_CONVIVIENTE;
/

-- Same grantees as PKG_AUTH_BOOTSTRAP (the application principal of this installation).
BEGIN
    FOR g IN (SELECT DISTINCT GRANTEE FROM USER_TAB_PRIVS
              WHERE TABLE_NAME = 'PKG_AUTH_BOOTSTRAP' AND PRIVILEGE = 'EXECUTE') LOOP
        EXECUTE IMMEDIATE 'GRANT EXECUTE ON SP_REGISTRAR_CONVIVIENTE TO ' || DBMS_ASSERT.ENQUOTE_NAME(g.GRANTEE, FALSE);
    END LOOP;
END;
/
