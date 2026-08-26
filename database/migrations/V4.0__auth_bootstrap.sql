ALTER SESSION SET CONTAINER = XEPDB1;

WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
WHENEVER OSERROR EXIT FAILURE ROLLBACK

-- ==============================================================================
-- SAED 2.0 Database Migration: V4.0 Authentication Bootstrap
-- Autor: Equipo de Arquitectura y Seguridad
-- Módulo: PKG_AUTH_BOOTSTRAP
-- Objetivo: Permitir consulta atómica de validación de credenciales (bypass RLS)
--           sin debilitar el modelo Zero-Trust.
-- ==============================================================================

--------------------------------------------------------------------------------
-- 1. SPEC DEL PAQUETE
--------------------------------------------------------------------------------
CREATE OR REPLACE PACKAGE SAED_SEC_MASTER.PKG_AUTH_BOOTSTRAP AUTHID DEFINER AS

    -- Extrae identidad mínima para Auth
    PROCEDURE GET_AUTH_DATA(
        p_email       IN  VARCHAR2,
        p_id_usuario  OUT NUMBER,
        p_hash        OUT VARCHAR2,
        p_estado      OUT VARCHAR2,
        p_intentos    OUT NUMBER
    );

    -- Resuelve tenant tras validación JWT
    PROCEDURE GET_ASSIGNMENT_CONTEXT(
        p_id_usuario    IN  NUMBER,
        p_id_asignacion IN  NUMBER,
        p_org_id        OUT NUMBER,
        p_prop_id       OUT NUMBER,
        p_unidad_id     OUT NUMBER,
        p_rol_codigo    OUT VARCHAR2,
        p_alcance       OUT VARCHAR2
    );

    -- Auditoría fallida y bloqueo
    PROCEDURE REGISTER_LOGIN_FAILURE(
        p_id_usuario IN NUMBER,
        p_ip_origen  IN VARCHAR2 DEFAULT NULL
    );

    -- Auditoría exitosa
    PROCEDURE REGISTER_LOGIN_SUCCESS(
        p_id_usuario IN NUMBER,
        p_ip_origen  IN VARCHAR2 DEFAULT NULL
    );

END PKG_AUTH_BOOTSTRAP;
/

--------------------------------------------------------------------------------
-- 2. CUERPO DEL PAQUETE
--------------------------------------------------------------------------------
CREATE OR REPLACE PACKAGE BODY SAED_SEC_MASTER.PKG_AUTH_BOOTSTRAP AS

    ----------------------------------------------------------------------------
    -- GET_AUTH_DATA
    ----------------------------------------------------------------------------
    PROCEDURE GET_AUTH_DATA(
        p_email       IN  VARCHAR2,
        p_id_usuario  OUT NUMBER,
        p_hash        OUT VARCHAR2,
        p_estado      OUT VARCHAR2,
        p_intentos    OUT NUMBER
    ) IS
    BEGIN
        -- Búsqueda atómica. Al tener EXEMPT ACCESS POLICY el esquema definidor,
        -- salta la barrera del RLS. Si no existe, lanza NO_DATA_FOUND.
        SELECT id_usuario, hash_password, estado, intentos_fallidos
          INTO p_id_usuario, p_hash, p_estado, p_intentos
          FROM SAED_V39_FINAL_TEST.USUARIOS
         WHERE email = p_email
           AND ROWNUM = 1;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            p_id_usuario := NULL;
            p_hash       := NULL;
            p_estado     := NULL;
            p_intentos   := NULL;
    END GET_AUTH_DATA;

    ----------------------------------------------------------------------------
    -- GET_ASSIGNMENT_CONTEXT
    ----------------------------------------------------------------------------
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
          FROM SAED_V39_FINAL_TEST.USUARIO_ASIGNACIONES ua
          JOIN SAED_V39_FINAL_TEST.ROLES r ON r.id_rol = ua.id_rol
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

    ----------------------------------------------------------------------------
    -- REGISTER_LOGIN_FAILURE
    ----------------------------------------------------------------------------
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

        -- Incrementar contador
        UPDATE SAED_V39_FINAL_TEST.USUARIOS 
           SET intentos_fallidos = intentos_fallidos + 1
         WHERE id_usuario = p_id_usuario
     RETURNING intentos_fallidos INTO v_intentos;

        -- Bloquear si supera umbral (Ej: 5 intentos)
        IF v_intentos >= 5 THEN
            UPDATE SAED_V39_FINAL_TEST.USUARIOS
               SET estado = 'BLOQUEADO',
                   fecha_bloqueo = SYSTIMESTAMP
             WHERE id_usuario = p_id_usuario;
        END IF;

        -- Registrar Auditoría
        BEGIN
            INSERT INTO SAED_V39_FINAL_TEST.AUDITORIA_LOG (
                id_usuario, accion, entidad, id_entidad_afectada, ip_origen, resultado
            ) VALUES (
                p_id_usuario, 'LOGIN', 'USUARIOS', p_id_usuario, p_ip_origen, 'FALLIDO'
            );
        EXCEPTION
            WHEN OTHERS THEN
                NULL; -- Prevenir que fallo de auditoría impacte a caller
        END;

        COMMIT;
    END REGISTER_LOGIN_FAILURE;

    ----------------------------------------------------------------------------
    -- REGISTER_LOGIN_SUCCESS
    ----------------------------------------------------------------------------
    PROCEDURE REGISTER_LOGIN_SUCCESS(
        p_id_usuario IN NUMBER,
        p_ip_origen  IN VARCHAR2 DEFAULT NULL
    ) IS
        PRAGMA AUTONOMOUS_TRANSACTION;
    BEGIN
        IF p_id_usuario IS NULL THEN
            RETURN;
        END IF;

        -- Reiniciar contador y actualizar último login
        UPDATE SAED_V39_FINAL_TEST.USUARIOS 
           SET intentos_fallidos = 0,
               fecha_bloqueo = NULL,
               ultimo_login = SYSTIMESTAMP
         WHERE id_usuario = p_id_usuario;

        -- Registrar Auditoría
        BEGIN
            INSERT INTO SAED_V39_FINAL_TEST.AUDITORIA_LOG (
                id_usuario, accion, entidad, id_entidad_afectada, ip_origen, resultado
            ) VALUES (
                p_id_usuario, 'LOGIN', 'USUARIOS', p_id_usuario, p_ip_origen, 'EXITOSO'
            );
        EXCEPTION
            WHEN OTHERS THEN
                NULL;
        END;

        COMMIT;
    END REGISTER_LOGIN_SUCCESS;

END PKG_AUTH_BOOTSTRAP;
/

--------------------------------------------------------------------------------
-- 3. GRANTS AL APLICATIVO
--------------------------------------------------------------------------------
GRANT EXECUTE ON SAED_SEC_MASTER.PKG_AUTH_BOOTSTRAP TO SAED_V39_FINAL_TEST;

EXIT;
