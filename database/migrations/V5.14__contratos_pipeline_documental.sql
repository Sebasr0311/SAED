-- ============================================================================
-- SAED 2.0 - Migracion V5.14: Pipeline Documental de Contratos de Arrendamiento
-- GAP-F5-02: Generacion, formalizacion, integridad, almacenamiento y descarga de contratos en PDF
-- ============================================================================

-- 1. Agregar columnas de metadatos documentales e inmutabilidad historica a CONTRATOS
DECLARE
    v_count NUMBER;
BEGIN
    -- DOCUMENTO_HASH: Hash criptografico SHA-256 (64 hex characters)
    SELECT COUNT(*) INTO v_count FROM USER_TAB_COLS WHERE TABLE_NAME = 'CONTRATOS' AND COLUMN_NAME = 'DOCUMENTO_HASH';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE CONTRATOS ADD (DOCUMENTO_HASH VARCHAR2(64 CHAR))';
    END IF;

    -- DOCUMENTO_TAMANO_BYTES: Tamano exacto en bytes del archivo PDF generado
    SELECT COUNT(*) INTO v_count FROM USER_TAB_COLS WHERE TABLE_NAME = 'CONTRATOS' AND COLUMN_NAME = 'DOCUMENTO_TAMANO_BYTES';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE CONTRATOS ADD (DOCUMENTO_TAMANO_BYTES NUMBER)';
    END IF;

    -- DOCUMENTO_FECHA_GENERACION: Timestamp con zona horaria de emision del PDF
    SELECT COUNT(*) INTO v_count FROM USER_TAB_COLS WHERE TABLE_NAME = 'CONTRATOS' AND COLUMN_NAME = 'DOCUMENTO_FECHA_GENERACION';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE CONTRATOS ADD (DOCUMENTO_FECHA_GENERACION TIMESTAMP(6) WITH TIME ZONE)';
    END IF;

    -- HTML_CONGELADO: Snapshot inmutable del contenido HTML con variables resueltas al momento de emision
    SELECT COUNT(*) INTO v_count FROM USER_TAB_COLS WHERE TABLE_NAME = 'CONTRATOS' AND COLUMN_NAME = 'HTML_CONGELADO';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE CONTRATOS ADD (HTML_CONGELADO CLOB)';
    END IF;
END;
/

COMMENT ON COLUMN CONTRATOS.DOCUMENTO_HASH IS 'Hash criptografico SHA-256 del artefacto PDF almacenado';
COMMENT ON COLUMN CONTRATOS.DOCUMENTO_TAMANO_BYTES IS 'Tamano en bytes del archivo PDF generado';
COMMENT ON COLUMN CONTRATOS.DOCUMENTO_FECHA_GENERACION IS 'Fecha y hora exacta de generacion y formalizacion del documento PDF';
COMMENT ON COLUMN CONTRATOS.HTML_CONGELADO IS 'Snapshot HTML inmutable del contrato emitido con variables resueltas';
