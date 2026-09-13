-- ============================================================================
-- SAED 2.0 - Migracion V5.11: Soporte de Estados SUSPENDIDA e INACTIVA en Plantillas de Contrato
-- Permite suspender temporalmente plantillas activas y reactivarlas posteriormente
-- ============================================================================

BEGIN
    EXECUTE IMMEDIATE 'ALTER TABLE PLANTILLAS_CONTRATOS DROP CONSTRAINT CK_PLANTILLAS_CONTR_ESTADO';
EXCEPTION
    WHEN OTHERS THEN
        NULL;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'ALTER TABLE PLANTILLAS_CONTRATOS ADD CONSTRAINT CK_PLANTILLAS_CONTR_ESTADO CHECK (ESTADO IN (''ACTIVA'', ''BORRADOR'', ''HISTORICA'', ''SUSPENDIDA'', ''INACTIVA''))';
EXCEPTION
    WHEN OTHERS THEN
        NULL;
END;
/
