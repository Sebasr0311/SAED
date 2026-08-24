-- ============================================================================
-- FASE 2 - Wompi: agregar 'WOMPI' (y 'PSE', ya soportado por el enum) a los
-- CHECK de metodo de pago de PAGOS y MULTAS.
-- El enum MetodoPago.java ya tenia PSE; el CHECK de BD no lo contemplaba.
-- ============================================================================

ALTER TABLE PAGOS DROP CONSTRAINT CHK_PAGO_METODO;
ALTER TABLE PAGOS ADD CONSTRAINT CHK_PAGO_METODO CHECK (
    metodo_pago IN ('EFECTIVO','TRANSFERENCIA','CHEQUE','TARJETA','CONSIGNACION','PSE','OTRO','WOMPI')
);

ALTER TABLE MULTAS DROP CONSTRAINT CHK_MULTA_METODO_PAGO;
ALTER TABLE MULTAS ADD CONSTRAINT CHK_MULTA_METODO_PAGO CHECK (
    metodo_pago IS NULL OR metodo_pago IN ('EFECTIVO','TRANSFERENCIA','CHEQUE','TARJETA','CONSIGNACION','PSE','OTRO','WOMPI')
);

EXIT;
