-- =============================================================================
-- SAED 2.0 - V5.26: ALERTAS ADMIN ESTRUCTURADAS PARA POLIZAS (F10-07 HARDENING)
-- Soporte para deduplicación robusta por ID_POLIZA sin depender de regex en texto
-- =============================================================================

-- 0. Contexto administrativo para DDL seguro
BEGIN
    PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1);
    PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN');
EXCEPTION
    WHEN OTHERS THEN NULL;
END;
/

-- 1. Agregar ID_POLIZA a ALERTAS_ADMIN si no existe
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(1) INTO v_count FROM USER_TAB_COLS WHERE TABLE_NAME = 'ALERTAS_ADMIN' AND COLUMN_NAME = 'ID_POLIZA';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE ALERTAS_ADMIN ADD (ID_POLIZA NUMBER(19))';
    END IF;
END;
/

-- 2. Crear índice para deduplicación atómica y consultas eficientes por póliza y ventana temporal
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(1) INTO v_count FROM USER_INDEXES WHERE INDEX_NAME = 'IX_ALERTAS_ADMIN_POLIZA';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX IX_ALERTAS_ADMIN_POLIZA ON ALERTAS_ADMIN (ID_PROPIEDAD, TIPO_ALERTA, ID_POLIZA, FECHA_CREACION)';
    END IF;
END;
/
