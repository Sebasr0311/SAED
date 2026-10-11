-- =============================================================================
-- SAED 2.0 - V5.55: HOUSEHOLD REVOCATION REQUIRES AN IN-FORCE TITULAR (security schema)
-- =============================================================================
-- RUN AS THE SECURITY SCHEMA OWNER (SAED_SEC_MASTER in production), not as the application schema owner.
-- The "__sec_" file-name marker tells the isolated installer to execute this file as that principal.
--
-- Tightens SP_REVOCAR_ACCESO_UNIDAD (V5.37). In addition to the existing checks the caller must:
--   * hold a titular assignment that is IN FORCE today: FECHA_INICIO <= today and FECHA_FIN empty or not yet past
--     (an ACTIVA state alone is not enough for a scheduled or already ended assignment);
--   * have, as a person, an ACTIVE residence row on that unit of a titular type (PROPIETARIO or ARRENDATARIO),
--     so a household member cannot revoke other members.
-- Dates use SYSDATE only. Grants and the synonym created by V5.37 are untouched (CREATE OR REPLACE).

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
      AND ua.FECHA_INICIO <= TRUNC(SYSDATE)
      AND (ua.FECHA_FIN IS NULL OR ua.FECHA_FIN >= TRUNC(SYSDATE))
      AND r.CODIGO IN ('RESIDENTE', 'PROPIETARIO');
    IF v_count = 0 THEN
        RAISE_APPLICATION_ERROR(-20463, 'Revocacion no autorizada: el solicitante no es titular vigente de la unidad');
    END IF;

    SELECT COUNT(1) INTO v_count
    FROM RESIDENTES_UNIDAD ru
    WHERE ru.ID_UNIDAD = p_id_unidad
      AND ru.ESTADO = 'ACTIVO'
      AND ru.TIPO_RESIDENTE IN ('PROPIETARIO', 'ARRENDATARIO')
      AND ru.ID_PERSONA IN (SELECT ID_PERSONA FROM USUARIOS WHERE ID_USUARIO = v_usr);
    IF v_count = 0 THEN
        RAISE_APPLICATION_ERROR(-20466, 'Revocacion no autorizada: el solicitante no reside en la unidad como titular');
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
