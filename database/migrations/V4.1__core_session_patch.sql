-- V4.1__core_session_patch.sql
-- Parche arquitectónico del Contexto de Sesión (State Machine)
-- Repara el deadlock de V3.9 al invocar SET_CONTEXT permitiendo un estado transitorio "BOOTSTRAP"

ALTER SESSION SET CONTAINER = XEPDB1;
WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
WHENEVER OSERROR EXIT FAILURE ROLLBACK

-- 1. Actualizar Especificación de PKG_SAED_SESSION
CREATE OR REPLACE PACKAGE SAED_V39_FINAL_TEST.PKG_SAED_SESSION AS
    PROCEDURE SET_BOOTSTRAP_CONTEXT(
        p_id_usuario IN NUMBER
    );
    PROCEDURE SET_CONTEXT (
        p_id_usuario      IN NUMBER,
        p_id_organizacion IN NUMBER,
        p_id_propiedad    IN NUMBER DEFAULT NULL,
        p_rol_codigo      IN VARCHAR2 DEFAULT NULL
    );
    PROCEDURE CLEAR_CONTEXT;
END PKG_SAED_SESSION;
/

-- 2. Actualizar Cuerpo de PKG_SAED_SESSION
CREATE OR REPLACE PACKAGE BODY SAED_V39_FINAL_TEST.PKG_SAED_SESSION AS
    PROCEDURE SET_BOOTSTRAP_CONTEXT(
        p_id_usuario IN NUMBER
    ) AS
    BEGIN
        DBMS_SESSION.SET_CONTEXT('SAED_CTX', 'STATE', 'BOOTSTRAP');
        DBMS_SESSION.SET_CONTEXT('SAED_CTX', 'ID_USUARIO', TO_CHAR(p_id_usuario));
        DBMS_SESSION.CLEAR_CONTEXT('SAED_CTX', NULL, 'ID_ORGANIZACION');
        DBMS_SESSION.CLEAR_CONTEXT('SAED_CTX', NULL, 'ID_PROPIEDAD');
        DBMS_SESSION.CLEAR_CONTEXT('SAED_CTX', NULL, 'ROL_CODIGO');
    END SET_BOOTSTRAP_CONTEXT;

    PROCEDURE SET_CONTEXT (
        p_id_usuario      IN NUMBER,
        p_id_organizacion IN NUMBER,
        p_id_propiedad    IN NUMBER DEFAULT NULL,
        p_rol_codigo      IN VARCHAR2 DEFAULT NULL
    ) AS
        v_valido          NUMBER := 0;
        v_estado_usr      USUARIOS.estado%TYPE;
        v_id_asignacion   USUARIO_ASIGNACIONES.id_asignacion%TYPE;
        v_current_state   VARCHAR2(30) := NVL(SYS_CONTEXT('SAED_CTX', 'STATE'), 'ANONYMOUS');
        v_current_user    NUMBER := TO_NUMBER(SYS_CONTEXT('SAED_CTX', 'ID_USUARIO'));
    BEGIN
        IF v_current_state NOT IN ('BOOTSTRAP', 'BUSINESS') THEN
            RAISE_APPLICATION_ERROR(-20085, 'Seguridad: No se puede establecer contexto de negocio sin identidad previa validada (Bootstrap).');
        END IF;

        IF v_current_user != p_id_usuario THEN
            RAISE_APPLICATION_ERROR(-20086, 'Seguridad: Violacion de identidad. El usuario solicitado no coincide con la identidad Bootstrap.');
        END IF;

        BEGIN
            SELECT estado INTO v_estado_usr FROM USUARIOS WHERE id_usuario = p_id_usuario;
            IF v_estado_usr != 'ACTIVO' THEN
                RAISE_APPLICATION_ERROR(-20081, 'Seguridad: El usuario especificado se encuentra inactivo o bloqueado.');
            END IF;
        EXCEPTION WHEN NO_DATA_FOUND THEN
            RAISE_APPLICATION_ERROR(-20082, 'Seguridad: El id_usuario especificado no existe.');
        END;

        IF p_rol_codigo = 'SUPERADMIN' THEN
            SELECT COUNT(*) INTO v_valido FROM ADMINISTRADORES_SAED WHERE id_usuario = p_id_usuario AND estado = 'ACTIVO';
            IF v_valido = 0 THEN RAISE_APPLICATION_ERROR(-20083, 'Seguridad: El usuario no tiene rol SUPERADMIN.'); END IF;
            DBMS_SESSION.SET_CONTEXT('SAED_CTX', 'ID_USUARIO', TO_CHAR(p_id_usuario));
            DBMS_SESSION.SET_CONTEXT('SAED_CTX', 'ID_ORGANIZACION', TO_CHAR(NVL(p_id_organizacion, 0)));
            DBMS_SESSION.SET_CONTEXT('SAED_CTX', 'ROL_CODIGO', 'SUPERADMIN');
            IF p_id_propiedad IS NOT NULL THEN DBMS_SESSION.SET_CONTEXT('SAED_CTX', 'ID_PROPIEDAD', TO_CHAR(p_id_propiedad)); END IF;
            DBMS_SESSION.SET_CONTEXT('SAED_CTX', 'STATE', 'BUSINESS');
            RETURN;
        END IF;

        BEGIN
            SELECT ua.id_asignacion INTO v_id_asignacion
            FROM USUARIO_ASIGNACIONES ua
            JOIN ROLES r ON r.id_rol = ua.id_rol
            WHERE ua.id_usuario = p_id_usuario
              AND ua.id_organizacion = p_id_organizacion
              AND r.codigo = p_rol_codigo
              AND ua.estado = 'ACTIVA'
              AND (ua.fecha_fin IS NULL OR ua.fecha_fin >= TRUNC(SYSDATE))
              AND ROWNUM = 1;
        EXCEPTION WHEN NO_DATA_FOUND THEN
            RAISE_APPLICATION_ERROR(-20080, 'Seguridad: Asignacion no autorizada para el usuario.');
        END;

        IF p_id_propiedad IS NOT NULL AND p_id_propiedad > 0 THEN
            SELECT COUNT(*) INTO v_valido FROM PROPIEDADES WHERE id_propiedad = p_id_propiedad AND id_organizacion = p_id_organizacion;
            IF v_valido = 0 THEN RAISE_APPLICATION_ERROR(-20084, 'Seguridad: Propiedad no autorizada o no existe en la organizacion.'); END IF;
            DBMS_SESSION.SET_CONTEXT('SAED_CTX', 'ID_PROPIEDAD', TO_CHAR(p_id_propiedad));
        ELSE
            DBMS_SESSION.CLEAR_CONTEXT('SAED_CTX', NULL, 'ID_PROPIEDAD');
        END IF;

        DBMS_SESSION.SET_CONTEXT('SAED_CTX', 'ID_USUARIO', TO_CHAR(p_id_usuario));
        DBMS_SESSION.SET_CONTEXT('SAED_CTX', 'ID_ORGANIZACION', TO_CHAR(p_id_organizacion));
        DBMS_SESSION.SET_CONTEXT('SAED_CTX', 'ROL_CODIGO', p_rol_codigo);
        DBMS_SESSION.SET_CONTEXT('SAED_CTX', 'STATE', 'BUSINESS');
    END SET_CONTEXT;

    PROCEDURE CLEAR_CONTEXT AS
    BEGIN
        DBMS_SESSION.SET_CONTEXT('SAED_CTX', 'STATE', 'CLEARING');
        DBMS_SESSION.CLEAR_ALL_CONTEXT('SAED_CTX');
    END CLEAR_CONTEXT;
END PKG_SAED_SESSION;
/

-- 3. Actualizar Cuerpo de PKG_SAED_SECURITY_RLS
CREATE OR REPLACE PACKAGE BODY SAED_V39_FINAL_TEST.PKG_SAED_SECURITY_RLS AS

    FUNCTION FN_FILTRO_ORGANIZACION (p_schema IN VARCHAR2, p_tab IN VARCHAR2) RETURN VARCHAR2 AS
        v_org VARCHAR2(30) := SYS_CONTEXT('SAED_CTX', 'ID_ORGANIZACION');
        v_rol VARCHAR2(30) := SYS_CONTEXT('SAED_CTX', 'ROL_CODIGO');
        v_state VARCHAR2(30) := NVL(SYS_CONTEXT('SAED_CTX', 'STATE'), 'ANONYMOUS');
    BEGIN
        IF v_state IN ('ANONYMOUS', 'CLEARING', 'BOOTSTRAP') THEN RETURN '1=0'; END IF;
        IF v_rol = 'SUPERADMIN' THEN RETURN NULL; END IF;
        IF v_org IS NULL OR v_org = '0' THEN RETURN '1=0'; END IF;
        IF p_tab = 'PERSONAS' THEN
            RETURN 'id_persona IN (SELECT id_persona FROM USUARIOS WHERE id_usuario IN (SELECT id_usuario FROM USUARIO_ASIGNACIONES WHERE id_organizacion = ' || v_org || ')) OR id_persona IN (SELECT id_persona FROM VISITANTES WHERE id_persona = PERSONAS.id_persona) OR id_persona IN (SELECT id_persona FROM PROPIETARIOS_UNIDAD JOIN UNIDADES ON PROPIETARIOS_UNIDAD.id_unidad = UNIDADES.id_unidad JOIN PROPIEDADES ON UNIDADES.id_propiedad = PROPIEDADES.id_propiedad WHERE PROPIEDADES.id_organizacion = ' || v_org || ')';
        END IF;
        RETURN 'id_organizacion = ' || v_org;
    END FN_FILTRO_ORGANIZACION;

    FUNCTION FN_FILTRO_PROPIEDAD (p_schema IN VARCHAR2, p_tab IN VARCHAR2) RETURN VARCHAR2 AS
        v_org VARCHAR2(30) := SYS_CONTEXT('SAED_CTX', 'ID_ORGANIZACION');
        v_prop VARCHAR2(30) := SYS_CONTEXT('SAED_CTX', 'ID_PROPIEDAD');
        v_rol VARCHAR2(30) := SYS_CONTEXT('SAED_CTX', 'ROL_CODIGO');
        v_state VARCHAR2(30) := NVL(SYS_CONTEXT('SAED_CTX', 'STATE'), 'ANONYMOUS');
    BEGIN
        IF v_state IN ('ANONYMOUS', 'CLEARING', 'BOOTSTRAP') THEN RETURN '1=0'; END IF;
        IF v_rol = 'SUPERADMIN' THEN RETURN NULL; END IF;
        IF v_org IS NULL OR v_org = '0' THEN RETURN '1=0'; END IF;
        IF v_prop IS NOT NULL THEN
            IF p_tab = 'PQRS_TRAZABILIDAD' THEN RETURN 'id_ticket IN (SELECT id_ticket FROM PQRS_TICKETS WHERE id_propiedad = ' || v_prop || ')'; END IF;
            IF p_tab = 'NOTIFICACIONES' THEN RETURN 'id_comunicado IN (SELECT id_comunicado FROM COMUNICADOS WHERE id_propiedad = ' || v_prop || ')'; END IF;
            IF p_tab = 'ENCUESTA_OPCIONES' THEN RETURN 'id_encuesta IN (SELECT id_encuesta FROM ENCUESTAS WHERE id_propiedad = ' || v_prop || ')'; END IF;
            RETURN 'id_propiedad = ' || v_prop;
        ELSE
            IF p_tab = 'PQRS_TRAZABILIDAD' THEN RETURN 'id_ticket IN (SELECT id_ticket FROM PQRS_TICKETS WHERE id_propiedad IN (SELECT id_propiedad FROM PROPIEDADES WHERE id_organizacion = ' || v_org || '))'; END IF;
            IF p_tab = 'NOTIFICACIONES' THEN RETURN 'id_comunicado IN (SELECT id_comunicado FROM COMUNICADOS WHERE id_propiedad IN (SELECT id_propiedad FROM PROPIEDADES WHERE id_organizacion = ' || v_org || '))'; END IF;
            IF p_tab = 'ENCUESTA_OPCIONES' THEN RETURN 'id_encuesta IN (SELECT id_encuesta FROM ENCUESTAS WHERE id_propiedad IN (SELECT id_propiedad FROM PROPIEDADES WHERE id_organizacion = ' || v_org || '))'; END IF;
            RETURN 'id_propiedad IN (SELECT id_propiedad FROM PROPIEDADES WHERE id_organizacion = ' || v_org || ')';
        END IF;
    END FN_FILTRO_PROPIEDAD;

    FUNCTION FN_FILTRO_UNIDAD (p_schema IN VARCHAR2, p_tab IN VARCHAR2) RETURN VARCHAR2 AS
        v_org VARCHAR2(30) := SYS_CONTEXT('SAED_CTX', 'ID_ORGANIZACION');
        v_prop VARCHAR2(30) := SYS_CONTEXT('SAED_CTX', 'ID_PROPIEDAD');
        v_rol VARCHAR2(30) := SYS_CONTEXT('SAED_CTX', 'ROL_CODIGO');
        v_state VARCHAR2(30) := NVL(SYS_CONTEXT('SAED_CTX', 'STATE'), 'ANONYMOUS');
    BEGIN
        IF v_state IN ('ANONYMOUS', 'CLEARING', 'BOOTSTRAP') THEN RETURN '1=0'; END IF;
        IF v_rol = 'SUPERADMIN' THEN RETURN NULL; END IF;
        IF v_org IS NULL OR v_org = '0' THEN RETURN '1=0'; END IF;
        IF v_prop IS NOT NULL THEN
            IF p_tab = 'UNIDADES' THEN RETURN 'id_propiedad = ' || v_prop; END IF;
            RETURN 'id_unidad IN (SELECT id_unidad FROM UNIDADES WHERE id_propiedad = ' || v_prop || ')';
        ELSE
            IF p_tab = 'UNIDADES' THEN RETURN 'id_propiedad IN (SELECT id_propiedad FROM PROPIEDADES WHERE id_organizacion = ' || v_org || ')'; END IF;
            RETURN 'id_unidad IN (SELECT id_unidad FROM UNIDADES WHERE id_propiedad IN (SELECT id_propiedad FROM PROPIEDADES WHERE id_organizacion = ' || v_org || '))';
        END IF;
    END FN_FILTRO_UNIDAD;

    FUNCTION FN_FILTRO_ASIGNACION (p_schema IN VARCHAR2, p_tab IN VARCHAR2) RETURN VARCHAR2 AS
        v_org VARCHAR2(30) := SYS_CONTEXT('SAED_CTX', 'ID_ORGANIZACION');
        v_rol VARCHAR2(30) := SYS_CONTEXT('SAED_CTX', 'ROL_CODIGO');
        v_usr VARCHAR2(30) := SYS_CONTEXT('SAED_CTX', 'ID_USUARIO');
        v_state VARCHAR2(30) := NVL(SYS_CONTEXT('SAED_CTX', 'STATE'), 'ANONYMOUS');
    BEGIN
        IF v_state IN ('ANONYMOUS', 'CLEARING') THEN RETURN '1=0'; END IF;
        IF v_state = 'BOOTSTRAP' THEN RETURN 'id_usuario = ' || v_usr; END IF;
        
        IF v_rol = 'SUPERADMIN' THEN RETURN NULL; END IF;
        IF v_org IS NULL OR v_org = '0' THEN RETURN '1=0'; END IF;
        IF v_rol = 'RESIDENTE' THEN RETURN 'id_usuario = ' || v_usr; END IF;
        RETURN 'id_organizacion = ' || v_org;
    END FN_FILTRO_ASIGNACION;

    FUNCTION FN_FILTRO_USUARIOS (p_schema IN VARCHAR2, p_tab IN VARCHAR2) RETURN VARCHAR2 AS
        v_org VARCHAR2(30) := SYS_CONTEXT('SAED_CTX', 'ID_ORGANIZACION');
        v_rol VARCHAR2(30) := SYS_CONTEXT('SAED_CTX', 'ROL_CODIGO');
        v_usr VARCHAR2(30) := SYS_CONTEXT('SAED_CTX', 'ID_USUARIO');
        v_state VARCHAR2(30) := NVL(SYS_CONTEXT('SAED_CTX', 'STATE'), 'ANONYMOUS');
    BEGIN
        IF v_state IN ('ANONYMOUS', 'CLEARING') THEN RETURN '1=0'; END IF;
        IF v_state = 'BOOTSTRAP' THEN RETURN 'id_usuario = ' || v_usr; END IF;
        
        IF v_rol = 'SUPERADMIN' THEN RETURN NULL; END IF;
        IF v_org IS NULL OR v_org = '0' THEN RETURN '1=0'; END IF;
        IF v_rol = 'RESIDENTE' THEN RETURN 'id_usuario = ' || v_usr; END IF;
        RETURN 'id_usuario IN (SELECT id_usuario FROM USUARIO_ASIGNACIONES WHERE id_organizacion = ' || v_org || ')';
    END FN_FILTRO_USUARIOS;

    FUNCTION FN_FILTRO_GLOBAL_READONLY (p_schema IN VARCHAR2, p_tab IN VARCHAR2) RETURN VARCHAR2 AS
    BEGIN
        RETURN '1=1';
    END FN_FILTRO_GLOBAL_READONLY;

    FUNCTION FN_FILTRO_GLOBAL_MUTATE (p_schema IN VARCHAR2, p_tab IN VARCHAR2) RETURN VARCHAR2 AS
        v_rol VARCHAR2(30) := SYS_CONTEXT('SAED_CTX', 'ROL_CODIGO');
        v_state VARCHAR2(30) := NVL(SYS_CONTEXT('SAED_CTX', 'STATE'), 'ANONYMOUS');
    BEGIN
        IF v_state IN ('ANONYMOUS', 'CLEARING', 'BOOTSTRAP') THEN RETURN '1=0'; END IF;
        IF v_rol = 'SUPERADMIN' THEN RETURN NULL; END IF;
        RETURN '1=0';
    END FN_FILTRO_GLOBAL_MUTATE;

END PKG_SAED_SECURITY_RLS;
/

EXIT;
