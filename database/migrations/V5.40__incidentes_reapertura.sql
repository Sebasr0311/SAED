-- =============================================================================
-- SAED 2.0 - V5.40: INCIDENT REOPEN TRAIL
-- =============================================================================
-- Audit F9-03 (plan F09-04). POST /api/v1/incidentes/{id}/reabrir moves a CERRADO incident back
-- to EN_INVESTIGACION. The reason, the user, the time and a reopen counter are persisted on the
-- incident row. The generic status endpoint no longer reopens incidents.
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
        WHERE TABLE_NAME = 'INCIDENTES' AND COLUMN_NAME = p_column;
        IF v_count = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE INCIDENTES ADD (' || p_column || ' ' || p_definition || ')';
        END IF;
    END;
BEGIN
    add_column('MOTIVO_REAPERTURA', 'VARCHAR2(1000 CHAR)');
    add_column('REABIERTO_POR', 'NUMBER');
    add_column('FECHA_REAPERTURA', 'TIMESTAMP(6) WITH TIME ZONE');
    add_column('NUMERO_REAPERTURAS', 'NUMBER(5)');
END;
/
