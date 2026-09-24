-- ============================================================================
-- SAED 2.0 - Migracion V5.16: Pipeline Documental de Paz y Salvo Integral
-- GAP-F6-04: Certificado oficial de Paz y Salvo, almacenamiento seguro y hash SHA-256
-- ============================================================================

DECLARE
    v_count NUMBER;
BEGIN
    -- DOCUMENTO_HASH: Hash criptografico SHA-256 (64 hex characters)
    SELECT COUNT(*) INTO v_count FROM USER_TAB_COLS WHERE TABLE_NAME = 'PAZ_Y_SALVOS' AND COLUMN_NAME = 'DOCUMENTO_HASH';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE PAZ_Y_SALVOS ADD (DOCUMENTO_HASH VARCHAR2(64 CHAR))';
    END IF;

    -- DOCUMENTO_TAMANO_BYTES: Tamano exacto en bytes del archivo PDF generado
    SELECT COUNT(*) INTO v_count FROM USER_TAB_COLS WHERE TABLE_NAME = 'PAZ_Y_SALVOS' AND COLUMN_NAME = 'DOCUMENTO_TAMANO_BYTES';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE PAZ_Y_SALVOS ADD (DOCUMENTO_TAMANO_BYTES NUMBER)';
    END IF;

    -- DOCUMENTO_FECHA_GENERACION: Timestamp con zona horaria de emision del PDF
    SELECT COUNT(*) INTO v_count FROM USER_TAB_COLS WHERE TABLE_NAME = 'PAZ_Y_SALVOS' AND COLUMN_NAME = 'DOCUMENTO_FECHA_GENERACION';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE PAZ_Y_SALVOS ADD (DOCUMENTO_FECHA_GENERACION TIMESTAMP(6) WITH TIME ZONE)';
    END IF;
END;
/

COMMENT ON COLUMN PAZ_Y_SALVOS.DOCUMENTO_HASH IS 'Hash criptografico SHA-256 del artefacto PDF de Paz y Salvo almacenado';
COMMENT ON COLUMN PAZ_Y_SALVOS.DOCUMENTO_TAMANO_BYTES IS 'Tamano en bytes del archivo PDF oficial generado';
COMMENT ON COLUMN PAZ_Y_SALVOS.DOCUMENTO_FECHA_GENERACION IS 'Fecha y hora de generacion fisica del certificado PDF oficial';
