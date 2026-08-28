-- ============================================================================
-- SAED 2.0 - Triggers de AUDITORIA de aplicacion (v2 - constraints correctas)
-- ACCION debe estar en: LOGIN, LOGOUT, INSERT, UPDATE, DELETE, QR_SCAN,
--   ACCESO_CONCEDIDO, ACCESO_DENEGADO, PAGO, CAMBIO_CONFIGURACION,
--   CAMBIO_ROL, EXPORTACION_REPORTE, EJECUCION_REGLA
-- ESTADO_ANTERIOR / ESTADO_NUEVO deben ser JSON (o NULL).
-- ============================================================================
SET PAGESIZE 0 FEEDBACK ON
ALTER SESSION SET CURRENT_SCHEMA = SAED_APP;
-- helper: NULL si la org no existe (evita FK_AUDITORIA_ORG)
CREATE OR REPLACE FUNCTION SAED_APP.FN_AUDIT_ORG_SAFE(p_org NUMBER) RETURN NUMBER AS
    v_count NUMBER;
BEGIN
    IF p_org IS NULL THEN RETURN NULL; END IF;
    SELECT COUNT(*) INTO v_count FROM ORGANIZACIONES WHERE id_organizacion = p_org;
    IF v_count = 0 THEN RETURN NULL; END IF;
    RETURN p_org;
END;
/

-- 1. PROPIEDADES
CREATE OR REPLACE TRIGGER TRG_AUDIT_PROPIEDADES
AFTER INSERT OR UPDATE OR DELETE ON PROPIEDADES
FOR EACH ROW
DECLARE
    v_org  NUMBER := TO_NUMBER(SYS_CONTEXT('SAED_CTX','ID_ORGANIZACION'));
    v_accion VARCHAR2(20);
    v_id NUMBER;
    v_anterior CLOB;
    v_nuevo CLOB;
BEGIN
    IF INSERTING THEN
        v_accion := 'INSERT'; v_id := :NEW.id_propiedad;
        v_nuevo := '{"nombre":"' || REPLACE(:NEW.nombre,'"','''') || '","estado":"' || :NEW.estado || '"}';
    ELSIF UPDATING THEN
        v_accion := 'UPDATE'; v_id := :NEW.id_propiedad;
        v_anterior := '{"nombre":"' || REPLACE(:OLD.nombre,'"','''') || '","estado":"' || :OLD.estado || '"}';
        v_nuevo := '{"nombre":"' || REPLACE(:NEW.nombre,'"','''') || '","estado":"' || :NEW.estado || '"}';
    ELSE
        v_accion := 'DELETE'; v_id := :OLD.id_propiedad;
        v_anterior := '{"nombre":"' || REPLACE(:OLD.nombre,'"','''') || '"}';
    END IF;
    IF v_org IS NULL THEN v_org := NVL(:NEW.id_organizacion, :OLD.id_organizacion); END IF;
    SP_REGISTRAR_AUDITORIA(
        p_id_usuario => TO_NUMBER(SYS_CONTEXT('SAED_CTX','ID_USUARIO')),
        p_id_organizacion => FN_AUDIT_ORG_SAFE(v_org),
        p_id_propiedad => v_id,
        p_accion => v_accion,
        p_entidad => 'PROPIEDADES',
        p_id_entidad_afectada => v_id,
        p_estado_anterior => v_anterior,
        p_estado_nuevo => v_nuevo
    );
END;
/
ALTER TRIGGER TRG_AUDIT_PROPIEDADES ENABLE;

-- 2. PAGOS
CREATE OR REPLACE TRIGGER TRG_AUDIT_PAGOS
AFTER INSERT OR UPDATE ON PAGOS
FOR EACH ROW
DECLARE
    v_anterior CLOB;
    v_nuevo CLOB;
BEGIN
    IF INSERTING THEN
        v_nuevo := '{"monto":"' || :NEW.monto_total || '","metodo":"' || :NEW.metodo_pago || '","estado":"' || :NEW.estado || '"}';
    ELSE
        v_anterior := '{"estado":"' || :OLD.estado || '"}';
        v_nuevo := '{"estado":"' || :NEW.estado || '"}';
    END IF;
    SP_REGISTRAR_AUDITORIA(
        p_id_usuario => TO_NUMBER(SYS_CONTEXT('SAED_CTX','ID_USUARIO')),
        p_id_organizacion => FN_AUDIT_ORG_SAFE(TO_NUMBER(SYS_CONTEXT('SAED_CTX','ID_ORGANIZACION'))),
        p_id_propiedad => TO_NUMBER(SYS_CONTEXT('SAED_CTX','ID_PROPIEDAD')),
        p_accion => 'PAGO',
        p_entidad => 'PAGOS',
        p_id_entidad_afectada => NVL(:NEW.id_pago, :OLD.id_pago),
        p_estado_anterior => v_anterior,
        p_estado_nuevo => v_nuevo
    );
END;
/
ALTER TRIGGER TRG_AUDIT_PAGOS ENABLE;

-- 3. USUARIO_ASIGNACIONES
CREATE OR REPLACE TRIGGER TRG_AUDIT_ASIGNACIONES
AFTER INSERT OR UPDATE OR DELETE ON USUARIO_ASIGNACIONES
FOR EACH ROW
DECLARE
    v_anterior CLOB;
    v_nuevo CLOB;
BEGIN
    IF INSERTING THEN
        v_nuevo := '{"usuario":"' || :NEW.id_usuario || '","rol":"' || :NEW.id_rol || '","estado":"' || :NEW.estado || '"}';
    ELSIF UPDATING THEN
        v_anterior := '{"estado":"' || :OLD.estado || '"}';
        v_nuevo := '{"estado":"' || :NEW.estado || '"}';
    ELSE
        v_anterior := '{"estado":"' || :OLD.estado || '"}';
    END IF;
    SP_REGISTRAR_AUDITORIA(
        p_id_usuario => TO_NUMBER(SYS_CONTEXT('SAED_CTX','ID_USUARIO')),
        p_id_organizacion => FN_AUDIT_ORG_SAFE(NVL(:NEW.id_organizacion, :OLD.id_organizacion)),
        p_id_propiedad => NVL(:NEW.id_propiedad, :OLD.id_propiedad),
        p_accion => 'CAMBIO_ROL',
        p_entidad => 'USUARIO_ASIGNACIONES',
        p_id_entidad_afectada => NVL(:NEW.id_asignacion, :OLD.id_asignacion),
        p_estado_anterior => v_anterior,
        p_estado_nuevo => v_nuevo
    );
END;
/
ALTER TRIGGER TRG_AUDIT_ASIGNACIONES ENABLE;

-- 4. MULTAS
CREATE OR REPLACE TRIGGER TRG_AUDIT_MULTAS
AFTER INSERT OR UPDATE ON MULTAS
FOR EACH ROW
DECLARE
    v_anterior CLOB;
    v_nuevo CLOB;
BEGIN
    IF INSERTING THEN
        v_nuevo := '{"monto":"' || :NEW.monto || '","estado":"' || :NEW.estado || '"}';
    ELSE
        v_anterior := '{"estado":"' || :OLD.estado || '"}';
        v_nuevo := '{"estado":"' || :NEW.estado || '"}';
    END IF;
    SP_REGISTRAR_AUDITORIA(
        p_id_usuario => TO_NUMBER(SYS_CONTEXT('SAED_CTX','ID_USUARIO')),
        p_id_organizacion => FN_AUDIT_ORG_SAFE(TO_NUMBER(SYS_CONTEXT('SAED_CTX','ID_ORGANIZACION'))),
        p_id_propiedad => TO_NUMBER(SYS_CONTEXT('SAED_CTX','ID_PROPIEDAD')),
        p_accion => 'INSERT',
        p_entidad => 'MULTAS',
        p_id_entidad_afectada => NVL(:NEW.id_multa, :OLD.id_multa),
        p_estado_anterior => v_anterior,
        p_estado_nuevo => v_nuevo
    );
END;
/
ALTER TRIGGER TRG_AUDIT_MULTAS ENABLE;

SELECT 'TRIGGERS_AUDITORIA: '||COUNT(*) FROM all_triggers WHERE owner='SAED_APP' AND trigger_name LIKE 'TRG_AUDIT_%';

EXIT;