-- =============================================================================
-- SAED 2.0 - V5.71: ONE INSURANCE EXPIRY ALERT PER POLICY AND PERIOD
-- =============================================================================
-- Audit F10-08 (plan F10-04). PolizaVencimientoService records an expiry alert per policy and
-- expiry period (the policy end date as ISO text). PERIODO_REFERENCIA stores that period and the
-- function-based unique index UQ_ALERTA_POLIZA_PERIODO guarantees that restarts, retries and
-- several application instances never record the same notice twice. Rows without a period (every
-- other alert type and alerts created before this migration) produce an all-NULL key and are not
-- indexed, so they are unaffected.
--
-- Idempotent: guarded by data-dictionary checks. Runs as a non-exempt owner under active VPD:
-- the new column has no DEFAULT and no data is rewritten; any error aborts the migration.

BEGIN
    PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1);
    PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN');
END;
/

-- 1. Period column (nullable, no default)
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(1) INTO v_count FROM USER_TAB_COLS
    WHERE TABLE_NAME = 'ALERTAS_ADMIN' AND COLUMN_NAME = 'PERIODO_REFERENCIA';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE ALERTAS_ADMIN ADD (PERIODO_REFERENCIA VARCHAR2(20 CHAR))';
    END IF;
END;
/

-- 2. Unique alert per policy, alert type and period (only rows that carry a period)
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(1) INTO v_count FROM USER_INDEXES
    WHERE TABLE_NAME = 'ALERTAS_ADMIN' AND INDEX_NAME = 'UQ_ALERTA_POLIZA_PERIODO';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE UNIQUE INDEX UQ_ALERTA_POLIZA_PERIODO ON ALERTAS_ADMIN ('
            || 'CASE WHEN PERIODO_REFERENCIA IS NOT NULL THEN ID_POLIZA END, '
            || 'CASE WHEN PERIODO_REFERENCIA IS NOT NULL THEN TIPO_ALERTA END, '
            || 'PERIODO_REFERENCIA)';
    END IF;
END;
/
