-- ============================================================================
-- V5.17__manual_payment_approval_lifecycle.sql
-- SAED 2.0: Ciclo de Aprobación y Rechazo de Pagos Manuales (GAP-F6-05)
-- ============================================================================

-- 1. Asegurar columnas de auditoría de rechazo y metadatos de comprobante en PAGOS
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM USER_TAB_COLS WHERE TABLE_NAME = 'PAGOS' AND COLUMN_NAME = 'RECHAZADO_POR';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE PAGOS ADD (RECHAZADO_POR NUMBER)';
        EXECUTE IMMEDIATE 'ALTER TABLE PAGOS ADD CONSTRAINT FK_PAGOS_RECHAZADOR FOREIGN KEY (RECHAZADO_POR) REFERENCES USUARIOS(ID_USUARIO)';
    END IF;

    SELECT COUNT(*) INTO v_count FROM USER_TAB_COLS WHERE TABLE_NAME = 'PAGOS' AND COLUMN_NAME = 'FECHA_RECHAZO';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE PAGOS ADD (FECHA_RECHAZO TIMESTAMP WITH TIME ZONE)';
    END IF;

    SELECT COUNT(*) INTO v_count FROM USER_TAB_COLS WHERE TABLE_NAME = 'PAGOS' AND COLUMN_NAME = 'COMPROBANTE_HASH';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE PAGOS ADD (COMPROBANTE_HASH VARCHAR2(64 CHAR))';
    END IF;

    SELECT COUNT(*) INTO v_count FROM USER_TAB_COLS WHERE TABLE_NAME = 'PAGOS' AND COLUMN_NAME = 'COMPROBANTE_TAMANO_BYTES';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE PAGOS ADD (COMPROBANTE_TAMANO_BYTES NUMBER)';
    END IF;
END;
/

-- 2. Modificar TRG_APLICAR_PAGO_CUOTA:
--    Garantiza que una inserción en PAGO_DETALLE solo descuente el saldo de la CUOTA
--    si el pago padre ya se encuentra en estado 'APROBADO' (ej. Wompi directo).
--    Para pagos manuales creados como 'PENDIENTE_APROBACION', el trigger NO descuenta saldo.
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
        WHERE id_cuota = :NEW.id_cuota;
    END IF;
EXCEPTION
    WHEN NO_DATA_FOUND THEN
        NULL;
END;
/

ALTER TRIGGER TRG_APLICAR_PAGO_CUOTA ENABLE;

-- 3. Trigger TRG_PAGOS_APROBACION_ESTADO (Opcional en caso de transición SQL directa):
--    Cuando PAGOS cambia de 'PENDIENTE_APROBACION' a 'APROBADO', aplica los montos
--    de PAGO_DETALLE a las cuotas correspondientes si no fueron aplicados.
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
        WHERE id_cuota = r.ID_CUOTA;
    END LOOP;
END;
/

ALTER TRIGGER TRG_PAGOS_APROBACION_ESTADO ENABLE;
