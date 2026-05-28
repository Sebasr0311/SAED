-- =============================================================================
-- SAED - Migration: fix_modelo_v5_1.sql
-- Applies 3 fixes to the live DB (run against RESIDENCIAL@xepdb1).
-- Safe to run on a DB initialized from modelo_relacional_v4.sql (any version).
-- =============================================================================
SET SQLBLANKLINES ON
SET DEFINE OFF

PROMPT ============================================================
PROMPT Fix 1: Add ANULADA to CHK_CUOTA_ESTADO (CUOTAS_ARRIENDO)
PROMPT ============================================================

-- Drop the old constraint first, then recreate with ANULADA added.
ALTER TABLE CUOTAS_ARRIENDO DROP CONSTRAINT CHK_CUOTA_ESTADO;

ALTER TABLE CUOTAS_ARRIENDO ADD CONSTRAINT CHK_CUOTA_ESTADO
    CHECK (estado IN ('PENDIENTE','PAGADA','VENCIDA','EN_MORA','ANULADA'));

PROMPT Fix 1 OK.

PROMPT ============================================================
PROMPT Fix 2: Add payment-detail columns to MULTAS
PROMPT ============================================================

-- Add three nullable columns for payment traceability.
ALTER TABLE MULTAS ADD (
    registrado_pago_por  NUMBER,
    metodo_pago          VARCHAR2(20  CHAR),
    comprobante_url      VARCHAR2(2000 CHAR)
);

-- FK: who registered the payment
ALTER TABLE MULTAS ADD CONSTRAINT FK_MULTA_REGISTRADO_PAGO
    FOREIGN KEY (registrado_pago_por)
    REFERENCES USUARIOS(id_usuario);

-- CHECK: accepted payment methods (NULL allowed — field is optional)
ALTER TABLE MULTAS ADD CONSTRAINT CHK_MULTA_METODO_PAGO
    CHECK (metodo_pago IS NULL OR metodo_pago IN (
        'EFECTIVO','TRANSFERENCIA','CHEQUE',
        'TARJETA','CONSIGNACION','OTRO'
    ));

PROMPT Fix 2 OK.

PROMPT ============================================================
PROMPT Fix 3: Add tipo_cuota to CUOTAS_ARRIENDO
PROMPT ============================================================

-- Add tipo_cuota column with default ARRIENDO so existing rows are valid.
ALTER TABLE CUOTAS_ARRIENDO ADD tipo_cuota VARCHAR2(15 CHAR) DEFAULT 'ARRIENDO' NOT NULL;

-- Drop the old unique constraint (id_contrato, anio, mes) and recreate
-- including tipo_cuota so both ARRIENDO and ADMINISTRACION can coexist
-- for the same contract/period.
ALTER TABLE CUOTAS_ARRIENDO DROP CONSTRAINT UQ_CUOTA_PERIODO;

ALTER TABLE CUOTAS_ARRIENDO ADD CONSTRAINT UQ_CUOTA_PERIODO
    UNIQUE (id_contrato, anio, mes, tipo_cuota);

-- CHECK: only recognised types allowed
ALTER TABLE CUOTAS_ARRIENDO ADD CONSTRAINT CHK_CUOTA_TIPO
    CHECK (tipo_cuota IN ('ARRIENDO','ADMINISTRACION'));

PROMPT Fix 3 OK.

COMMIT;

PROMPT ============================================================
PROMPT Migration fix_modelo_v5_1 applied successfully.
PROMPT ============================================================
