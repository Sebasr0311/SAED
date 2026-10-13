-- =============================================================================
-- SAED 2.0 - V5.53: FINE-ORIGIN RECEIVABLES (one fee per fine)
-- =============================================================================
-- Audit F10-06 / plan F06-03 integration. A resolved sanction fine is posted to the unit's receivables
-- (FineReceivablesAdapter). Several fines of the same concept can fall in the same unit and month, which
-- the old UQ_CUOTA_UNICA (unit, concept, period) forbids, and a fine must never produce two fees.
--
--   * CUOTAS.ID_MULTA_ORIGEN (nullable, no default) links a fee to the fine that originated it.
--   * UQ_CUOTA_UNICA_ORIGEN (unit, concept, period, NVL(origin, 0)) replaces UQ_CUOTA_UNICA: ordinary
--     fees keep the exact old uniqueness (origin 0), fine fees are unique per fine as well.
--   * UQ_CUOTA_MULTA_ORIGEN: at most one fee per originating fine (the idempotency guard of the adapter).
--   * TRG_CUOTA_MULTA_PAGADA: when a fine-origin fee becomes PAGADA the fine is marked PAGADA, so the
--     fine ledger and the receivables agree whichever path collected the money.
--
-- Idempotent (data-dictionary checks). Runs as a non-exempt owner under active VPD: the new column has
-- no DEFAULT, no data is rewritten and any error aborts.

BEGIN
    PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1);
    PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN');
END;
/

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(1) INTO v_count FROM USER_TAB_COLS
    WHERE TABLE_NAME = 'CUOTAS' AND COLUMN_NAME = 'ID_MULTA_ORIGEN';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE CUOTAS ADD (ID_MULTA_ORIGEN NUMBER)';
    END IF;

    SELECT COUNT(1) INTO v_count FROM USER_CONSTRAINTS
    WHERE TABLE_NAME = 'CUOTAS' AND CONSTRAINT_NAME = 'FK_CUOTAS_MULTA_ORIGEN';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE CUOTAS ADD CONSTRAINT FK_CUOTAS_MULTA_ORIGEN '
            || 'FOREIGN KEY (ID_MULTA_ORIGEN) REFERENCES MULTAS (ID_MULTA)';
    END IF;
END;
/

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(1) INTO v_count FROM USER_INDEXES
    WHERE TABLE_NAME = 'CUOTAS' AND INDEX_NAME = 'UQ_CUOTA_MULTA_ORIGEN';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE UNIQUE INDEX UQ_CUOTA_MULTA_ORIGEN ON CUOTAS (ID_MULTA_ORIGEN)';
    END IF;

    SELECT COUNT(1) INTO v_count FROM USER_INDEXES
    WHERE TABLE_NAME = 'CUOTAS' AND INDEX_NAME = 'UQ_CUOTA_UNICA_ORIGEN';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE UNIQUE INDEX UQ_CUOTA_UNICA_ORIGEN ON CUOTAS '
            || '(ID_UNIDAD, ID_CONCEPTO, PERIODO, NVL(ID_MULTA_ORIGEN, 0))';
    END IF;
END;
/

-- The old three-column constraint is now implied by UQ_CUOTA_UNICA_ORIGEN (strictly weaker for fine fees).
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(1) INTO v_count FROM USER_CONSTRAINTS
    WHERE TABLE_NAME = 'CUOTAS' AND CONSTRAINT_NAME = 'UQ_CUOTA_UNICA';
    IF v_count > 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE CUOTAS DROP CONSTRAINT UQ_CUOTA_UNICA';
    END IF;
END;
/

CREATE OR REPLACE TRIGGER TRG_CUOTA_MULTA_PAGADA
    AFTER UPDATE OF ESTADO ON CUOTAS
    FOR EACH ROW
    WHEN (NEW.ESTADO = 'PAGADA' AND OLD.ESTADO <> 'PAGADA' AND NEW.ID_MULTA_ORIGEN IS NOT NULL)
BEGIN
    UPDATE MULTAS
    SET ESTADO = 'PAGADA'
    WHERE ID_MULTA = :NEW.ID_MULTA_ORIGEN
      AND ESTADO IN ('IMPUESTA', 'EN_DESCARGOS', 'RATIFICADA');
END;
/

ALTER TRIGGER TRG_CUOTA_MULTA_PAGADA ENABLE;
