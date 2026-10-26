-- =============================================================================
-- SAED 2.0 - V5.80: CONSUMPTION METER RESET POLICY (F11-03)
-- =============================================================================
-- A current reading lower than the previous one is accepted only when the reading is flagged as a
-- meter reset/replacement. The reset is stored with LECTURA_ANTERIOR = 0 (so CK_MEDICIONES_LECTURAS and
-- the consumption formula stay valid) and the previous meter value is kept in LECTURA_PREVIA_REINICIO.
--   REINICIO_MEDIDOR         'S' / 'N'   default 'N'
--   LECTURA_PREVIA_REINICIO  previous reading before the reset (NULL when not a reset)
--
-- Idempotent; runs as a non-exempt owner under active VPD:
--   * columns are added WITHOUT a default (a default on this policy-protected table forces a rewrite that
--     active VPD rejects with ORA-28133); the default is set with MODIFY and existing rows by UPDATE;
--   * no blanket exception handlers.

BEGIN
    PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1);
    PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN');
END;
/

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(1) INTO v_count FROM USER_TAB_COLS
    WHERE TABLE_NAME = 'MEDICIONES_CONSUMO' AND COLUMN_NAME = 'REINICIO_MEDIDOR';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE MEDICIONES_CONSUMO ADD (REINICIO_MEDIDOR CHAR(1 BYTE))';
    END IF;
    EXECUTE IMMEDIATE 'ALTER TABLE MEDICIONES_CONSUMO MODIFY (REINICIO_MEDIDOR DEFAULT ''N'')';
    EXECUTE IMMEDIATE 'UPDATE MEDICIONES_CONSUMO SET REINICIO_MEDIDOR = ''N'' WHERE REINICIO_MEDIDOR IS NULL';

    SELECT COUNT(1) INTO v_count FROM USER_TAB_COLS
    WHERE TABLE_NAME = 'MEDICIONES_CONSUMO' AND COLUMN_NAME = 'LECTURA_PREVIA_REINICIO';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE MEDICIONES_CONSUMO ADD (LECTURA_PREVIA_REINICIO NUMBER(10,2))';
    END IF;
END;
/

DECLARE
    PROCEDURE add_check(p_name IN VARCHAR2, p_condition IN VARCHAR2) IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(1) INTO v_count FROM USER_CONSTRAINTS
        WHERE TABLE_NAME = 'MEDICIONES_CONSUMO' AND CONSTRAINT_NAME = p_name;
        IF v_count = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE MEDICIONES_CONSUMO ADD CONSTRAINT ' || p_name || ' CHECK (' || p_condition || ')';
        END IF;
    END;
BEGIN
    add_check('CK_MEDICIONES_REINICIO', 'REINICIO_MEDIDOR IS NULL OR REINICIO_MEDIDOR IN (''S'', ''N'')');
    add_check('CK_MEDICIONES_REINICIO_BASE', 'NVL(REINICIO_MEDIDOR, ''N'') = ''N'' '
        || 'OR (LECTURA_ANTERIOR = 0 AND LECTURA_PREVIA_REINICIO IS NOT NULL AND LECTURA_PREVIA_REINICIO >= 0)');
END;
/

COMMIT;
