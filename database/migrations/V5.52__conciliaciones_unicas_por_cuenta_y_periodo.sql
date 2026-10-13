-- =============================================================================
-- SAED 2.0 - V5.52: ONE RECONCILIATION PER BANK ACCOUNT AND PERIOD
-- =============================================================================
-- Audit F6-15 / plan F06-05.
-- The reconciliation API now refuses a second reconciliation of the same bank account and period
-- (409). This migration enforces it in the database as well, so concurrent requests cannot create
-- duplicates, and keeps a reconciliation from being marked CONCILIADO while bank and book balances
-- differ.
--
--   * UQ_CONCILIACION_CUENTA_PERIODO: unique function-based index on (property, UPPER(account), period).
--   * CK_CONCIL_CONCILIADO_SALDOS: CONCILIADO requires SALDO_BANCO = SALDO_LIBROS. Added NOVALIDATE so
--     historical rows are not rewritten or rejected; it applies to every new or changed row.
--
-- Idempotent (data-dictionary checks). Runs as a non-exempt owner under active VPD: no column is
-- added, no default, no data is rewritten and any error aborts. If duplicate reconciliations of the same
-- account and period already exist the migration stops with ORA-20972 and changes nothing; finance must
-- review and merge or delete them first.

BEGIN
    PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1);
    PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN');
END;
/

DECLARE
    v_count NUMBER;
    v_dupes NUMBER;
BEGIN
    SELECT COUNT(1) INTO v_count FROM USER_INDEXES
    WHERE TABLE_NAME = 'CONCILIACIONES' AND INDEX_NAME = 'UQ_CONCILIACION_CUENTA_PERIODO';
    IF v_count = 0 THEN
        SELECT COUNT(1) INTO v_dupes FROM (
            SELECT ID_PROPIEDAD, UPPER(BANCO_CUENTA), PERIODO FROM CONCILIACIONES
            GROUP BY ID_PROPIEDAD, UPPER(BANCO_CUENTA), PERIODO
            HAVING COUNT(1) > 1
        );
        IF v_dupes > 0 THEN
            RAISE_APPLICATION_ERROR(-20972,
                'V5.52: ' || v_dupes || ' bank account/period pair(s) have more than one reconciliation; resolve them before applying UQ_CONCILIACION_CUENTA_PERIODO');
        END IF;
        EXECUTE IMMEDIATE 'CREATE UNIQUE INDEX UQ_CONCILIACION_CUENTA_PERIODO ON CONCILIACIONES (ID_PROPIEDAD, UPPER(BANCO_CUENTA), PERIODO)';
    END IF;
END;
/

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(1) INTO v_count FROM USER_CONSTRAINTS
    WHERE TABLE_NAME = 'CONCILIACIONES' AND CONSTRAINT_NAME = 'CK_CONCIL_CONCILIADO_SALDOS';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE CONCILIACIONES ADD CONSTRAINT CK_CONCIL_CONCILIADO_SALDOS CHECK '
            || '(ESTADO <> ''CONCILIADO'' OR SALDO_BANCO = SALDO_LIBROS) ENABLE NOVALIDATE';
    END IF;
END;
/
