-- =============================================================================
-- SAED 2.0 - V5.43: WORKS FINAL INSPECTION
-- =============================================================================
-- Audit F9-11 (plan F09-03). POST /api/v1/obras/{id}/finalizar requires an inspection record:
-- result (APROBADA, CON_OBSERVACIONES; RECHAZADA blocks completion), observations, inspector and
-- time are persisted on the work in the same conditional update that sets FINALIZADA. The generic
-- status endpoint can no longer finalize a work.
--
-- Idempotent: guarded by data-dictionary checks. Runs as a non-exempt owner under active VPD:
-- the new columns are nullable, have no DEFAULT and no data is rewritten; any error aborts.

BEGIN
    PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1);
    PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN');
END;
/

DECLARE
    PROCEDURE add_column(p_column VARCHAR2, p_definition VARCHAR2) IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(1) INTO v_count FROM USER_TAB_COLS
        WHERE TABLE_NAME = 'OBRAS' AND COLUMN_NAME = p_column;
        IF v_count = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE OBRAS ADD (' || p_column || ' ' || p_definition || ')';
        END IF;
    END;
BEGIN
    add_column('INSPECCION_RESULTADO', 'VARCHAR2(20 CHAR)');
    add_column('INSPECCION_OBSERVACIONES', 'VARCHAR2(1000 CHAR)');
    add_column('INSPECCION_POR', 'NUMBER');
    add_column('INSPECCION_FECHA', 'TIMESTAMP(6) WITH TIME ZONE');
END;
/

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(1) INTO v_count FROM USER_CONSTRAINTS
    WHERE TABLE_NAME = 'OBRAS' AND CONSTRAINT_NAME = 'CK_OBRAS_INSPECCION_RES';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE OBRAS ADD CONSTRAINT CK_OBRAS_INSPECCION_RES CHECK '
            || '(INSPECCION_RESULTADO IS NULL OR INSPECCION_RESULTADO IN (''APROBADA'',''CON_OBSERVACIONES'',''RECHAZADA''))';
    END IF;
END;
/
