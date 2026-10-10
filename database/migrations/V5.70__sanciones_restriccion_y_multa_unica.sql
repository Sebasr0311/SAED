-- =============================================================================
-- SAED 2.0 - V5.70: SANCTION EFFECTS APPLIED ONCE AND PERSISTED RESTRICTION PERIOD
-- =============================================================================
-- Audit F10-04 / F10-06 / F10-07 (plan F10-02).
--   * SANCIONES.RESTRICCION_DESDE / RESTRICCION_HASTA persist the period of an APLICADA
--     SUSPENSION_ZONAS_COMUNES so the reservations module can ask whether a unit is restricted
--     on a date (SancionService.hasActiveCommonAreaRestriction).
--   * UQ_MULTAS_SANCION_ORIGEN guarantees at most one fine per sanction, so a repeated or
--     concurrent resolution cannot create a duplicate fine. NULL origins (fines not created by a
--     sanction) are not indexed and remain unrestricted.
--
-- Idempotent: every change is guarded by a data-dictionary existence check.
-- Runs as a non-exempt owner under active VPD, therefore:
--   * new columns have no DEFAULT (no full-table rewrite, no ORA-28133);
--   * no upsert statements on policy-protected tables;
--   * no blanket exception handlers: any error aborts the migration.
-- If duplicated fines per sanction already exist the migration stops with ORA-20970 and changes
-- no data; duplicates must be reviewed and annulled by finance before re-running it.

BEGIN
    PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1);
    PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN');
END;
/

-- 1. Restriction period columns on SANCIONES (nullable, no default)
DECLARE
    PROCEDURE add_column(p_table IN VARCHAR2, p_column IN VARCHAR2, p_definition IN VARCHAR2) IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(1) INTO v_count FROM USER_TAB_COLS
        WHERE TABLE_NAME = p_table AND COLUMN_NAME = p_column;
        IF v_count = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE ' || p_table || ' ADD (' || p_column || ' ' || p_definition || ')';
        END IF;
    END;
BEGIN
    add_column('SANCIONES', 'RESTRICCION_DESDE', 'DATE');
    add_column('SANCIONES', 'RESTRICCION_HASTA', 'DATE');
END;
/

-- 2. Restriction period must be ordered
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(1) INTO v_count FROM USER_CONSTRAINTS
    WHERE TABLE_NAME = 'SANCIONES' AND CONSTRAINT_NAME = 'CK_SANCIONES_RESTRICCION';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE SANCIONES ADD CONSTRAINT CK_SANCIONES_RESTRICCION CHECK ('
            || 'RESTRICCION_DESDE IS NULL OR RESTRICCION_HASTA IS NULL OR RESTRICCION_HASTA >= RESTRICCION_DESDE)';
    END IF;
END;
/

-- 3. At most one fine per originating sanction
DECLARE
    v_count NUMBER;
    v_dupes NUMBER;
BEGIN
    SELECT COUNT(1) INTO v_count FROM USER_INDEXES
    WHERE TABLE_NAME = 'MULTAS' AND INDEX_NAME = 'UQ_MULTAS_SANCION_ORIGEN';
    IF v_count = 0 THEN
        SELECT COUNT(1) INTO v_dupes FROM (
            SELECT ID_SANCION_ORIGEN FROM MULTAS
            WHERE ID_SANCION_ORIGEN IS NOT NULL
            GROUP BY ID_SANCION_ORIGEN
            HAVING COUNT(1) > 1
        );
        IF v_dupes > 0 THEN
            RAISE_APPLICATION_ERROR(-20970,
                'V5.70: ' || v_dupes || ' sanction(s) have more than one fine in MULTAS; resolve them before applying UQ_MULTAS_SANCION_ORIGEN');
        END IF;
        EXECUTE IMMEDIATE 'CREATE UNIQUE INDEX UQ_MULTAS_SANCION_ORIGEN ON MULTAS (ID_SANCION_ORIGEN)';
    END IF;
END;
/
