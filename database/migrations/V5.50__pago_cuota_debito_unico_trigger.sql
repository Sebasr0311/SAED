-- =============================================================================
-- SAED 2.0 - V5.50: SINGLE OWNER OF THE FEE DEBIT (the database triggers)
-- =============================================================================
-- Audit F6-03 / plan F06-03.
--
-- Until this phase a payment reduced CUOTAS.SALDO_PENDIENTE twice: once by the V5.17 triggers and
-- once more from Java (FinanzasServiceImpl.aprobarPago and the gateway settlement). A partial
-- payment (debt 100, pay 30) left a balance of 40 instead of 70; full payments hid the defect
-- behind GREATEST(..., 0).
--
-- Decision: the database triggers are the single owner of the debit. The Java debit was removed
-- in the same change set. This script (re)defines both triggers so their exact body is part of
-- the versioned schema instead of depending on drift-prone startup code:
--
--   TRG_APLICAR_PAGO_CUOTA        AFTER INSERT ON PAGO_DETALLE
--       debits the fee when the parent payment is already APROBADO (gateway settlement, which
--       inserts the payment as APROBADO and then its detail). A detail of a payment that is
--       PENDIENTE_APROBACION or RECHAZADO never debits.
--   TRG_PAGOS_APROBACION_ESTADO   AFTER UPDATE OF ESTADO ON PAGOS
--       debits every detail of the payment on the single PENDIENTE_APROBACION -> APROBADO
--       transition (manual approval). The Java update is conditional on that previous state, so
--       a repeated or concurrent approval matches no row and cannot fire the trigger twice.
--
-- Differences from V5.17: cancelled fees (ANULADA) are never touched, and the NO_DATA_FOUND
-- handler is gone (PAGO_DETALLE.ID_PAGO has a foreign key, a missing parent is a real error).
-- A fee keeps PAGADA_PARCIAL while it still has a balance, PAGADA at balance zero.
--
-- Idempotent (CREATE OR REPLACE). Runs as a non-exempt owner under active VPD; any error aborts.

BEGIN
    PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1);
    PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN');
END;
/

CREATE OR REPLACE TRIGGER TRG_APLICAR_PAGO_CUOTA
    AFTER INSERT ON PAGO_DETALLE
    FOR EACH ROW
DECLARE
    v_estado VARCHAR2(25);
BEGIN
    SELECT ESTADO INTO v_estado FROM PAGOS WHERE ID_PAGO = :NEW.ID_PAGO;
    IF v_estado = 'APROBADO' THEN
        UPDATE CUOTAS
        SET saldo_pendiente = GREATEST(0, saldo_pendiente - :NEW.monto_aplicado),
            estado = CASE
                WHEN (saldo_pendiente - :NEW.monto_aplicado) <= 0 THEN 'PAGADA'
                ELSE 'PAGADA_PARCIAL'
            END
        WHERE id_cuota = :NEW.id_cuota
          AND estado <> 'ANULADA';
    END IF;
END;
/

ALTER TRIGGER TRG_APLICAR_PAGO_CUOTA ENABLE;

CREATE OR REPLACE TRIGGER TRG_PAGOS_APROBACION_ESTADO
    AFTER UPDATE OF ESTADO ON PAGOS
    FOR EACH ROW
    WHEN (NEW.ESTADO = 'APROBADO' AND OLD.ESTADO = 'PENDIENTE_APROBACION')
BEGIN
    FOR r IN (SELECT ID_CUOTA, MONTO_APLICADO FROM PAGO_DETALLE WHERE ID_PAGO = :NEW.ID_PAGO) LOOP
        UPDATE CUOTAS
        SET saldo_pendiente = GREATEST(0, saldo_pendiente - r.MONTO_APLICADO),
            estado = CASE
                WHEN (saldo_pendiente - r.MONTO_APLICADO) <= 0 THEN 'PAGADA'
                ELSE 'PAGADA_PARCIAL'
            END
        WHERE id_cuota = r.ID_CUOTA
          AND estado <> 'ANULADA';
    END LOOP;
END;
/

ALTER TRIGGER TRG_PAGOS_APROBACION_ESTADO ENABLE;
