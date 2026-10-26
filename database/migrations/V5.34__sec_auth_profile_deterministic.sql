-- =============================================================================
-- SAED 2.0 - V5.34: DETERMINISTIC DEFAULT CONTEXT IN PKG_AUTH_BOOTSTRAP (security schema)
-- =============================================================================
-- RUN AS THE SECURITY SCHEMA OWNER (SAED_SEC_MASTER in production), not as the application schema owner:
-- PKG_AUTH_BOOTSTRAP is an AUTHID DEFINER package owned by the security account. The "__sec_" file-name
-- marker tells the isolated installer to execute this file as that principal.
--
-- Change: GET_USER_PROFILE selected the default role with ROWNUM = 1 and no ORDER BY, so users with several
-- assignments received an arbitrary default role (it changed between installs), and assignments outside
-- FECHA_INICIO/FECHA_FIN could still be selected. Both the login response and the JWT filter use this
-- procedure for the default context. The rest of the package body is copied verbatim from V5.0.

CREATE OR REPLACE PACKAGE BODY PKG_AUTH_BOOTSTRAP AS

    PROCEDURE GET_AUTH_DATA(
        p_email       IN  VARCHAR2,
        p_id_usuario  OUT NUMBER,
        p_hash        OUT VARCHAR2,
        p_estado      OUT VARCHAR2,
        p_intentos    OUT NUMBER
    ) IS
    BEGIN
        SELECT id_usuario, hash_password, estado, intentos_fallidos
          INTO p_id_usuario, p_hash, p_estado, p_intentos
          FROM USUARIOS
         WHERE email = p_email
           AND ROWNUM = 1;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            p_id_usuario := NULL;
            p_hash       := NULL;
            p_estado     := NULL;
            p_intentos   := NULL;
    END GET_AUTH_DATA;

    PROCEDURE GET_ASSIGNMENT_CONTEXT(
        p_id_usuario    IN  NUMBER,
        p_id_asignacion IN  NUMBER,
        p_org_id        OUT NUMBER,
        p_prop_id       OUT NUMBER,
        p_unidad_id     OUT NUMBER,
        p_rol_codigo    OUT VARCHAR2,
        p_alcance       OUT VARCHAR2
    ) IS
    BEGIN
        SELECT ua.id_organizacion, ua.id_propiedad, ua.id_unidad, r.codigo, r.alcance
          INTO p_org_id, p_prop_id, p_unidad_id, p_rol_codigo, p_alcance
          FROM USUARIO_ASIGNACIONES ua
          JOIN ROLES r ON r.id_rol = ua.id_rol
         WHERE ua.id_asignacion = p_id_asignacion
           AND ua.id_usuario = p_id_usuario
           AND ua.estado = 'ACTIVA'
           AND r.estado = 'ACTIVO';
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            p_org_id     := NULL;
            p_prop_id    := NULL;
            p_unidad_id  := NULL;
            p_rol_codigo := NULL;
            p_alcance    := NULL;
    END GET_ASSIGNMENT_CONTEXT;

    PROCEDURE REGISTER_LOGIN_FAILURE(
        p_id_usuario IN NUMBER,
        p_ip_origen  IN VARCHAR2 DEFAULT NULL
    ) IS
        PRAGMA AUTONOMOUS_TRANSACTION;
        v_intentos NUMBER;
    BEGIN
        IF p_id_usuario IS NULL THEN
            RETURN;
        END IF;

        UPDATE USUARIOS
           SET intentos_fallidos = intentos_fallidos + 1
         WHERE id_usuario = p_id_usuario
         RETURNING intentos_fallidos INTO v_intentos;

        IF v_intentos >= 5 THEN
            UPDATE USUARIOS
               SET estado = 'BLOQUEADO',
                   fecha_bloqueo = SYSTIMESTAMP
             WHERE id_usuario = p_id_usuario;
        END IF;

        BEGIN
            INSERT INTO AUDITORIA_LOG (
                id_usuario, accion, entidad, id_entidad_afectada, ip_origen, resultado
            ) VALUES (
                p_id_usuario, 'LOGIN', 'USUARIOS', p_id_usuario, p_ip_origen, 'FALLIDO'
            );
        EXCEPTION
            WHEN OTHERS THEN NULL;
        END;

        COMMIT;
    END REGISTER_LOGIN_FAILURE;

    PROCEDURE REGISTER_LOGIN_SUCCESS(
        p_id_usuario IN NUMBER,
        p_ip_origen  IN VARCHAR2 DEFAULT NULL
    ) IS
        PRAGMA AUTONOMOUS_TRANSACTION;
    BEGIN
        IF p_id_usuario IS NULL THEN
            RETURN;
        END IF;

        UPDATE USUARIOS
           SET intentos_fallidos = 0,
               fecha_bloqueo = NULL,
               ultimo_login = SYSTIMESTAMP
         WHERE id_usuario = p_id_usuario;

        BEGIN
            INSERT INTO AUDITORIA_LOG (
                id_usuario, accion, entidad, id_entidad_afectada, ip_origen, resultado
            ) VALUES (
                p_id_usuario, 'LOGIN', 'USUARIOS', p_id_usuario, p_ip_origen, 'EXITOSO'
            );
        EXCEPTION
            WHEN OTHERS THEN NULL;
        END;

        COMMIT;
    END REGISTER_LOGIN_SUCCESS;

    PROCEDURE GET_USER_PROFILE(
        p_id_usuario      IN  NUMBER,
        p_nombre_usuario  OUT VARCHAR2,
        p_email           OUT VARCHAR2,
        p_rol_codigo      OUT VARCHAR2,
        p_alcance         OUT VARCHAR2,
        p_org_id          OUT NUMBER,
        p_prop_id         OUT NUMBER,
        p_unidad_id       OUT NUMBER
    ) IS
    BEGIN
        SELECT nombre_usuario, email
          INTO p_nombre_usuario, p_email
          FROM USUARIOS
         WHERE id_usuario = p_id_usuario;

        BEGIN
            -- Deterministic default context: the oldest ACTIVE assignment that is in force today
            -- (database clock). ROWNUM = 1 without ORDER BY picked an arbitrary role for multi-role users
            -- and ignored FECHA_INICIO/FECHA_FIN (isolated-Oracle finding, recovery-15).
            SELECT codigo, alcance, id_organizacion, id_propiedad, id_unidad
              INTO p_rol_codigo, p_alcance, p_org_id, p_prop_id, p_unidad_id
              FROM (SELECT r.codigo, r.alcance, ua.id_organizacion, ua.id_propiedad, ua.id_unidad
                      FROM USUARIO_ASIGNACIONES ua
                      JOIN ROLES r ON r.id_rol = ua.id_rol
                     WHERE ua.id_usuario = p_id_usuario
                       AND ua.estado = 'ACTIVA'
                       AND TRUNC(SYSDATE) BETWEEN TRUNC(ua.fecha_inicio) AND COALESCE(ua.fecha_fin, TRUNC(SYSDATE))
                     ORDER BY ua.id_asignacion)
             WHERE ROWNUM = 1;
        EXCEPTION
            WHEN NO_DATA_FOUND THEN
                p_rol_codigo := NULL;
                p_alcance    := NULL;
                p_org_id     := NULL;
                p_prop_id    := NULL;
                p_unidad_id  := NULL;
        END;
    END GET_USER_PROFILE;

END PKG_AUTH_BOOTSTRAP;
/
