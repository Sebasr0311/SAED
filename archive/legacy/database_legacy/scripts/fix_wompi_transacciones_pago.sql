-- ============================================================================
-- FASE 5.1 - Wompi: tabla TRANSACCIONES_PAGO
-- Patron verificado contra el esquema real: PK con DEFAULT de secuencia
-- (SEC_<TABLA>.NEXTVAL), estilo SEC_PAGOS/SEC_MULTAS (CACHE 20, NOCYCLE).
-- ============================================================================

-- 1. Secuencia
CREATE SEQUENCE SEC_TRANSACCIONES_PAGO
  START WITH 1 INCREMENT BY 1 CACHE 20 NOCYCLE;

-- 2. Tabla
CREATE TABLE TRANSACCIONES_PAGO (
    id                   NUMBER        DEFAULT "RESIDENCIAL"."SEC_TRANSACCIONES_PAGO"."NEXTVAL" NOT NULL,
    referencia           VARCHAR2(255) NOT NULL,
    id_apartamento       NUMBER        NOT NULL,
    id_usuario           NUMBER        NOT NULL,
    concepto             VARCHAR2(20)  NOT NULL,
    id_cuota             NUMBER        NULL,
    id_multa             NUMBER        NULL,
    monto_centavos       NUMBER(12,0)  NOT NULL,
    moneda               VARCHAR2(3)   DEFAULT 'COP' NOT NULL,
    estado               VARCHAR2(20)  DEFAULT 'PENDIENTE' NOT NULL,
    id_transaccion_wompi VARCHAR2(64)  NULL,
    metodo_pago_wompi    VARCHAR2(30)  NULL,
    payload_webhook      CLOB          NULL,
    fecha_creacion       TIMESTAMP(6)  DEFAULT SYSTIMESTAMP NOT NULL,
    fecha_confirmacion   TIMESTAMP(6)  NULL,
    CONSTRAINT PK_TPAGO           PRIMARY KEY (id),
    CONSTRAINT UQ_TPAGO_REFERENCIA UNIQUE (referencia),
    CONSTRAINT FK_TPAGO_APARTAMENTO FOREIGN KEY (id_apartamento) REFERENCES APARTAMENTOS (id_apartamento),
    CONSTRAINT FK_TPAGO_USUARIO    FOREIGN KEY (id_usuario)      REFERENCES USUARIOS (id_usuario),
    CONSTRAINT FK_TPAGO_CUOTA      FOREIGN KEY (id_cuota)        REFERENCES CUOTAS_ARRIENDO (id_cuota),
    CONSTRAINT FK_TPAGO_MULTA      FOREIGN KEY (id_multa)        REFERENCES MULTAS (id_multa),
    CONSTRAINT CHK_TPAGO_CONCEPTO  CHECK (concepto IN ('CUOTA','MULTA')),
    CONSTRAINT CHK_TPAGO_ESTADO    CHECK (estado IN ('PENDIENTE','APROBADO','RECHAZADO','VENCIDO','ERROR')),
    CONSTRAINT CHK_TPAGO_MONEDA    CHECK (moneda = 'COP'),
    CONSTRAINT CHK_TPAGO_MONTO     CHECK (monto_centavos > 0),
    CONSTRAINT CHK_TPAGO_EXCLUSION CHECK (
        (concepto = 'CUOTA' AND id_cuota IS NOT NULL AND id_multa IS NULL) OR
        (concepto = 'MULTA' AND id_multa IS NOT NULL AND id_cuota IS NULL)
    )
);

-- 3. Indices de consulta
CREATE INDEX IDX_TPAGO_ESTADO      ON TRANSACCIONES_PAGO (estado);
CREATE INDEX IDX_TPAGO_APARTAMENTO ON TRANSACCIONES_PAGO (id_apartamento);

-- 4. Indices unicos funcionales (invariante: max. 1 intencion PENDIENTE por item;
--    los NULL no colisionan en indices unicos de Oracle)
CREATE UNIQUE INDEX UQ_TPAGO_CUOTA_PEND ON TRANSACCIONES_PAGO (
    CASE WHEN concepto = 'CUOTA' AND estado = 'PENDIENTE' THEN id_cuota END
);
CREATE UNIQUE INDEX UQ_TPAGO_MULTA_PEND ON TRANSACCIONES_PAGO (
    CASE WHEN concepto = 'MULTA' AND estado = 'PENDIENTE' THEN id_multa END
);

-- 5. Comentarios (patron COMMENT ON del esquema, como PAGOS)
COMMENT ON TABLE TRANSACCIONES_PAGO IS
  'Intenciones de pago con Wompi (cuotas y multas). Registro de auditoria del flujo pasarela.';
COMMENT ON COLUMN TRANSACCIONES_PAGO.referencia IS 'Referencia unica generada por SAED (SAED-<CONCEPTO>-<id>-<timestamp>). Se envia a Wompi.';
COMMENT ON COLUMN TRANSACCIONES_PAGO.concepto IS 'CUOTA | MULTA. Excluyente con id_cuota/id_multa.';
COMMENT ON COLUMN TRANSACCIONES_PAGO.monto_centavos IS 'Monto en centavos COP (Wompi no usa decimales).';
COMMENT ON COLUMN TRANSACCIONES_PAGO.estado IS 'PENDIENTE | APROBADO | RECHAZADO | VENCIDO | ERROR';
COMMENT ON COLUMN TRANSACCIONES_PAGO.id_transaccion_wompi IS 'ID de la transaccion creada por el widget de Wompi.';
COMMENT ON COLUMN TRANSACCIONES_PAGO.metodo_pago_wompi IS 'CARD | NEQUI | PSE | BANCOLOMBIA (sin CHECK: Wompi puede agregar metodos).';
COMMENT ON COLUMN TRANSACCIONES_PAGO.payload_webhook IS 'Evento crudo transaction.updated recibido por el webhook (auditoria).';

EXIT;
