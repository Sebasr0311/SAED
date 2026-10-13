-- =============================================================================
-- SAED 2.0 - V5.73: ACTIVATION-TOKEN PROCEDURES (security schema)
-- =============================================================================
-- RUN AS THE SECURITY SCHEMA OWNER (SAED_SEC_MASTER in production), not as the application schema owner.
-- The "__sec_" file-name marker tells the isolated installer to execute this file as that principal.
-- Requires V5.72 (SELECT, INSERT, UPDATE on TOKENS_ACTIVACION; UPDATE on USUARIOS password/state columns) and
-- V5.65 (SELECT on the identity tables).
--
-- TOKENS_ACTIVACION now has a row-level policy (V5.72). The activation endpoints are unauthenticated (no session
-- context), the cohabitant of a resident is not visible to the resident, and an account that has no password yet
-- must not be reachable through direct table access, so every token operation runs here with definer rights:
--   SP_EMITIR_TOKEN_ACTIVACION     authenticated sessions: validates that the target account is inside the caller's
--                                  scope, invalidates the previous open tokens of the same type and stores the new
--                                  token HASH (the plain token never reaches the database).
--   SP_CONSULTAR_TOKEN_ACTIVACION  anonymous: state of a token hash (VALIDO, USADO, EXPIRADO, INEXISTENTE,
--                                  USUARIO_NO_DISPONIBLE); account data only for a valid token.
--   SP_ACTIVAR_CUENTA              anonymous: under a row lock, sets the password hash and activates the account and
--                                  consumes the token in one transaction (single use). A BLOQUEADO or INACTIVO account
--                                  is never reactivated by a token.
--   SP_REENVIAR_TOKEN_ACTIVACION   anonymous: issues a new token for the account with that user name or e-mail;
--                                  answers nothing when none matches (the caller gives a uniform response).
-- The reference instant (p_ahora) and the expiry are supplied by the application server, so token lifetimes do not
-- depend on the database clock or session time zone; assignment validity uses TRUNC(SYSDATE).
-- Error codes: -20501 invalid context/arguments, -20502 role without permission, -20503 caller assignment not in
-- force, -20504 target outside the caller's scope, -20505 unknown or unavailable account.
-- Idempotent (CREATE OR REPLACE; synonyms and grants are repeatable).

DECLARE
    v_app_owner VARCHAR2(128);
    v_count     NUMBER;
BEGIN
    SELECT TABLE_OWNER INTO v_app_owner FROM USER_SYNONYMS WHERE SYNONYM_NAME = 'USUARIO_ASIGNACIONES';
    FOR t IN (SELECT COLUMN_VALUE AS NAME FROM TABLE(SYS.ODCIVARCHAR2LIST(
                  'USUARIOS', 'ROLES', 'PERSONAS', 'TOKENS_ACTIVACION'))) LOOP
        SELECT COUNT(1) INTO v_count FROM USER_SYNONYMS WHERE SYNONYM_NAME = t.NAME;
        IF v_count = 0 THEN
            EXECUTE IMMEDIATE 'CREATE SYNONYM ' || t.NAME || ' FOR '
                || DBMS_ASSERT.ENQUOTE_NAME(v_app_owner, FALSE) || '.' || t.NAME;
        END IF;
    END LOOP;
END;
/

CREATE OR REPLACE PROCEDURE SP_EMITIR_TOKEN_ACTIVACION(
    p_id_usuario     IN  NUMBER,
    p_token_hash     IN  VARCHAR2,
    p_tipo           IN  VARCHAR2,
    p_expira         IN  TIMESTAMP WITH TIME ZONE,
    p_ip             IN  VARCHAR2,
    o_nombre_usuario OUT VARCHAR2,
    o_email          OUT VARCHAR2,
    o_primer_nombre  OUT VARCHAR2
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
    IF v_state <> 'BUSINESS' OR v_usr_txt IS NULL OR v_rol IS NULL
       OR p_id_usuario IS NULL OR p_token_hash IS NULL OR p_expira IS NULL OR p_tipo IS NULL
       OR p_tipo NOT IN ('ACTIVACION_INICIAL', 'RECUPERACION_PASSWORD') THEN
        RAISE_APPLICATION_ERROR(-20501, 'Token no autorizado: contexto o argumentos invalidos');
    END IF;
    v_usr := TO_NUMBER(v_usr_txt);
    v_org := TO_NUMBER(v_org_txt);
    v_prop := TO_NUMBER(v_prop_txt);

    IF v_rol = 'SUPERADMIN' THEN
        -- The platform session was validated by PKG_SAED_SESSION (ADMINISTRADORES_SAED); any account may be targeted.
        v_count := 1;
    ELSIF v_rol IN ('ADMIN_ORGANIZACION', 'ADMIN_PROPIEDAD') THEN
        IF v_org IS NULL OR v_org = 0 OR (v_rol = 'ADMIN_PROPIEDAD' AND v_prop IS NULL) THEN
            RAISE_APPLICATION_ERROR(-20501, 'Token no autorizado: contexto o argumentos invalidos');
        END IF;
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
            RAISE_APPLICATION_ERROR(-20503, 'Token no autorizado: el solicitante no es administrador vigente');
        END IF;
        SELECT COUNT(1) INTO v_count
        FROM USUARIO_ASIGNACIONES ua
        WHERE ua.ID_USUARIO = p_id_usuario
          AND ua.ID_ORGANIZACION = v_org
          AND (v_rol = 'ADMIN_ORGANIZACION' OR ua.ID_PROPIEDAD = v_prop);
        IF v_count = 0 THEN
            RAISE_APPLICATION_ERROR(-20504, 'Token no autorizado: la cuenta esta fuera del ambito del solicitante');
        END IF;
        IF v_rol = 'ADMIN_PROPIEDAD' THEN
            SELECT COUNT(1) INTO v_count
            FROM USUARIO_ASIGNACIONES ua
            JOIN ROLES r ON r.ID_ROL = ua.ID_ROL
            WHERE ua.ID_USUARIO = p_id_usuario
              AND r.CODIGO IN ('SUPERADMIN', 'ADMIN_ORGANIZACION');
            IF v_count > 0 THEN
                RAISE_APPLICATION_ERROR(-20504, 'Token no autorizado: la cuenta esta fuera del ambito del solicitante');
            END IF;
        END IF;
    ELSIF v_rol IN ('RESIDENTE', 'PROPIETARIO') THEN
        IF v_prop IS NULL THEN
            RAISE_APPLICATION_ERROR(-20501, 'Token no autorizado: contexto o argumentos invalidos');
        END IF;
        SELECT COUNT(1) INTO v_count
        FROM USUARIO_ASIGNACIONES ua
        JOIN ROLES r ON r.ID_ROL = ua.ID_ROL
        WHERE ua.ID_USUARIO = v_usr
          AND ua.ID_UNIDAD IS NOT NULL
          AND ua.ID_PROPIEDAD = v_prop
          AND ua.ESTADO = 'ACTIVA'
          AND ua.FECHA_INICIO <= TRUNC(SYSDATE)
          AND (ua.FECHA_FIN IS NULL OR ua.FECHA_FIN >= TRUNC(SYSDATE))
          AND r.CODIGO IN ('RESIDENTE', 'PROPIETARIO');
        IF v_count = 0 THEN
            RAISE_APPLICATION_ERROR(-20503, 'Token no autorizado: el solicitante no es titular vigente');
        END IF;
        SELECT COUNT(1) INTO v_count
        FROM USUARIO_ASIGNACIONES tgt
        JOIN ROLES tr ON tr.ID_ROL = tgt.ID_ROL
        WHERE tgt.ID_USUARIO = p_id_usuario
          AND tr.CODIGO = 'RESIDENTE_CONVIVENCIA'
          AND tgt.ESTADO = 'ACTIVA'
          AND tgt.ID_PROPIEDAD = v_prop
          AND tgt.ID_UNIDAD IN (
              SELECT ua.ID_UNIDAD
              FROM USUARIO_ASIGNACIONES ua
              JOIN ROLES r ON r.ID_ROL = ua.ID_ROL
              WHERE ua.ID_USUARIO = v_usr
                AND ua.ID_UNIDAD IS NOT NULL
                AND ua.ID_PROPIEDAD = v_prop
                AND ua.ESTADO = 'ACTIVA'
                AND ua.FECHA_INICIO <= TRUNC(SYSDATE)
                AND (ua.FECHA_FIN IS NULL OR ua.FECHA_FIN >= TRUNC(SYSDATE))
                AND r.CODIGO IN ('RESIDENTE', 'PROPIETARIO'));
        IF v_count = 0 THEN
            RAISE_APPLICATION_ERROR(-20504, 'Token no autorizado: la cuenta esta fuera del ambito del solicitante');
        END IF;
    ELSE
        RAISE_APPLICATION_ERROR(-20502, 'Token no autorizado: rol sin permiso para emitir tokens');
    END IF;

    SELECT COUNT(1), MAX(u.NOMBRE_USUARIO), MAX(u.EMAIL), MAX(NVL(p.PRIMER_NOMBRE, u.NOMBRE_USUARIO))
      INTO v_count, o_nombre_usuario, o_email, o_primer_nombre
    FROM USUARIOS u
    LEFT JOIN PERSONAS p ON p.ID_PERSONA = u.ID_PERSONA
    WHERE u.ID_USUARIO = p_id_usuario
      AND u.ESTADO IN ('ACTIVO', 'PENDIENTE_VERIFICACION');
    IF v_count = 0 THEN
        RAISE_APPLICATION_ERROR(-20505, 'Token rechazado: cuenta desconocida o no disponible');
    END IF;

    UPDATE TOKENS_ACTIVACION SET USADO = 1
    WHERE ID_USUARIO = p_id_usuario AND TIPO = p_tipo AND USADO = 0;

    INSERT INTO TOKENS_ACTIVACION (ID_USUARIO, TOKEN_HASH, TIPO, FECHA_EXPIRACION, USADO, IP_SOLICITUD)
    VALUES (p_id_usuario, p_token_hash, p_tipo, p_expira, 0, SUBSTR(p_ip, 1, 50));
END SP_EMITIR_TOKEN_ACTIVACION;
/

CREATE OR REPLACE PROCEDURE SP_CONSULTAR_TOKEN_ACTIVACION(
    p_token_hash     IN  VARCHAR2,
    p_ahora          IN  TIMESTAMP WITH TIME ZONE,
    o_estado         OUT VARCHAR2,
    o_id_usuario     OUT NUMBER,
    o_nombre_usuario OUT VARCHAR2,
    o_email          OUT VARCHAR2,
    o_primer_nombre  OUT VARCHAR2,
    o_expira         OUT TIMESTAMP WITH TIME ZONE
) AUTHID DEFINER AS
    v_id_usuario NUMBER;
    v_usado      NUMBER;
    v_expira     TIMESTAMP WITH TIME ZONE;
    v_count      NUMBER;
BEGIN
    o_estado := 'INEXISTENTE';
    IF p_token_hash IS NULL OR p_ahora IS NULL THEN
        RETURN;
    END IF;
    SELECT COUNT(1), MAX(ID_USUARIO), MAX(USADO), MAX(FECHA_EXPIRACION)
      INTO v_count, v_id_usuario, v_usado, v_expira
    FROM TOKENS_ACTIVACION
    WHERE TOKEN_HASH = p_token_hash;
    IF v_count = 0 THEN
        RETURN;
    END IF;
    IF v_usado = 1 THEN
        o_estado := 'USADO';
        RETURN;
    END IF;
    IF v_expira < p_ahora THEN
        o_estado := 'EXPIRADO';
        RETURN;
    END IF;
    SELECT COUNT(1), MAX(u.NOMBRE_USUARIO), MAX(u.EMAIL), MAX(NVL(p.PRIMER_NOMBRE, u.NOMBRE_USUARIO))
      INTO v_count, o_nombre_usuario, o_email, o_primer_nombre
    FROM USUARIOS u
    LEFT JOIN PERSONAS p ON p.ID_PERSONA = u.ID_PERSONA
    WHERE u.ID_USUARIO = v_id_usuario
      AND u.ESTADO IN ('ACTIVO', 'PENDIENTE_VERIFICACION');
    IF v_count = 0 THEN
        o_estado := 'USUARIO_NO_DISPONIBLE';
        o_nombre_usuario := NULL;
        o_email := NULL;
        o_primer_nombre := NULL;
        RETURN;
    END IF;
    o_estado := 'VALIDO';
    o_id_usuario := v_id_usuario;
    o_expira := v_expira;
END SP_CONSULTAR_TOKEN_ACTIVACION;
/

CREATE OR REPLACE PROCEDURE SP_ACTIVAR_CUENTA(
    p_token_hash    IN  VARCHAR2,
    p_hash_password IN  VARCHAR2,
    p_ahora         IN  TIMESTAMP WITH TIME ZONE,
    o_estado        OUT VARCHAR2,
    o_id_usuario    OUT NUMBER
) AUTHID DEFINER AS
BEGIN
    o_estado := 'INEXISTENTE';
    IF p_token_hash IS NULL OR p_hash_password IS NULL OR p_ahora IS NULL THEN
        RETURN;
    END IF;
    FOR t IN (SELECT ID_TOKEN, ID_USUARIO, USADO, FECHA_EXPIRACION
              FROM TOKENS_ACTIVACION
              WHERE TOKEN_HASH = p_token_hash
              FOR UPDATE) LOOP
        IF t.USADO = 1 THEN
            o_estado := 'USADO';
        ELSIF t.FECHA_EXPIRACION < p_ahora THEN
            o_estado := 'EXPIRADO';
        ELSE
            UPDATE USUARIOS
            SET HASH_PASSWORD = p_hash_password, ESTADO = 'ACTIVO', INTENTOS_FALLIDOS = 0
            WHERE ID_USUARIO = t.ID_USUARIO
              AND ESTADO IN ('ACTIVO', 'PENDIENTE_VERIFICACION');
            IF SQL%ROWCOUNT = 0 THEN
                o_estado := 'USUARIO_NO_DISPONIBLE';
            ELSE
                UPDATE TOKENS_ACTIVACION SET USADO = 1
                WHERE ID_USUARIO = t.ID_USUARIO AND USADO = 0;
                o_estado := 'ACTIVADA';
                o_id_usuario := t.ID_USUARIO;
            END IF;
        END IF;
    END LOOP;
END SP_ACTIVAR_CUENTA;
/

CREATE OR REPLACE PROCEDURE SP_REENVIAR_TOKEN_ACTIVACION(
    p_identificador  IN  VARCHAR2,
    p_token_hash     IN  VARCHAR2,
    p_expira         IN  TIMESTAMP WITH TIME ZONE,
    p_ip             IN  VARCHAR2,
    o_id_usuario     OUT NUMBER,
    o_nombre_usuario OUT VARCHAR2,
    o_email          OUT VARCHAR2,
    o_primer_nombre  OUT VARCHAR2
) AUTHID DEFINER AS
    v_count NUMBER;
BEGIN
    IF p_identificador IS NULL OR TRIM(p_identificador) IS NULL OR p_token_hash IS NULL OR p_expira IS NULL THEN
        RETURN;
    END IF;
    SELECT COUNT(1), MIN(u.ID_USUARIO)
      INTO v_count, o_id_usuario
    FROM USUARIOS u
    WHERE (LOWER(u.NOMBRE_USUARIO) = LOWER(TRIM(p_identificador)) OR LOWER(u.EMAIL) = LOWER(TRIM(p_identificador)))
      AND u.ESTADO IN ('ACTIVO', 'PENDIENTE_VERIFICACION');
    IF v_count = 0 THEN
        o_id_usuario := NULL;
        RETURN;
    END IF;
    SELECT MAX(u.NOMBRE_USUARIO), MAX(u.EMAIL), MAX(NVL(p.PRIMER_NOMBRE, u.NOMBRE_USUARIO))
      INTO o_nombre_usuario, o_email, o_primer_nombre
    FROM USUARIOS u
    LEFT JOIN PERSONAS p ON p.ID_PERSONA = u.ID_PERSONA
    WHERE u.ID_USUARIO = o_id_usuario;

    UPDATE TOKENS_ACTIVACION SET USADO = 1
    WHERE ID_USUARIO = o_id_usuario AND TIPO = 'ACTIVACION_INICIAL' AND USADO = 0;

    INSERT INTO TOKENS_ACTIVACION (ID_USUARIO, TOKEN_HASH, TIPO, FECHA_EXPIRACION, USADO, IP_SOLICITUD)
    VALUES (o_id_usuario, p_token_hash, 'ACTIVACION_INICIAL', p_expira, 0, SUBSTR(p_ip, 1, 50));
END SP_REENVIAR_TOKEN_ACTIVACION;
/

-- Same grantees as PKG_AUTH_BOOTSTRAP (the application principal of this installation).
BEGIN
    FOR g IN (SELECT DISTINCT GRANTEE FROM USER_TAB_PRIVS
              WHERE TABLE_NAME = 'PKG_AUTH_BOOTSTRAP' AND PRIVILEGE = 'EXECUTE') LOOP
        EXECUTE IMMEDIATE 'GRANT EXECUTE ON SP_EMITIR_TOKEN_ACTIVACION TO ' || DBMS_ASSERT.ENQUOTE_NAME(g.GRANTEE, FALSE);
        EXECUTE IMMEDIATE 'GRANT EXECUTE ON SP_CONSULTAR_TOKEN_ACTIVACION TO ' || DBMS_ASSERT.ENQUOTE_NAME(g.GRANTEE, FALSE);
        EXECUTE IMMEDIATE 'GRANT EXECUTE ON SP_ACTIVAR_CUENTA TO ' || DBMS_ASSERT.ENQUOTE_NAME(g.GRANTEE, FALSE);
        EXECUTE IMMEDIATE 'GRANT EXECUTE ON SP_REENVIAR_TOKEN_ACTIVACION TO ' || DBMS_ASSERT.ENQUOTE_NAME(g.GRANTEE, FALSE);
    END LOOP;
END;
/
