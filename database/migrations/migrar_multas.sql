-- Migración: Tabla MULTAS
-- ============================================================

CREATE SEQUENCE SEC_MULTAS START WITH 1 INCREMENT BY 1 NOCACHE ORDER;

CREATE TABLE MULTAS (
    id_multa       NUMBER DEFAULT SEC_MULTAS.NEXTVAL NOT NULL
                   CONSTRAINT PK_MULTA PRIMARY KEY,
    id_apartamento NUMBER NOT NULL,
    id_mensaje     NUMBER,
    tipo           VARCHAR2(20 CHAR) NOT NULL,
    monto          NUMBER(10,2) NOT NULL,
    estado         VARCHAR2(15 CHAR) DEFAULT 'PENDIENTE' NOT NULL,
    descripcion    VARCHAR2(500),
    foto_evidencia CLOB,
    fecha_creacion TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    fecha_pago     TIMESTAMP,
    creado_por     NUMBER NOT NULL,

    CONSTRAINT FK_MULTA_APARTAMENTO
        FOREIGN KEY (id_apartamento) REFERENCES APARTAMENTOS(id_apartamento),
    CONSTRAINT FK_MULTA_MENSAJE
        FOREIGN KEY (id_mensaje) REFERENCES BUZON(id_mensaje),
    CONSTRAINT FK_MULTA_CREADOR
        FOREIGN KEY (creado_por) REFERENCES USUARIOS(id_usuario),
    CONSTRAINT CHK_MULTA_TIPO
        CHECK (tipo IN ('RUIDO', 'PARQUEADERO')),
    CONSTRAINT CHK_MULTA_ESTADO
        CHECK (estado IN ('PENDIENTE', 'PAGADA', 'ANULADA')),
    CONSTRAINT CHK_MULTA_MONTO
        CHECK (monto > 0)
);
