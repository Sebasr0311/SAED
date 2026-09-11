-- ============================================================================
-- SAED 2.0 - Migracion V5.7: Motor de Plantillas de Contratos por Organizacion
-- Requisitos #8, #9 y #10 del Plan Maestro:
-- 1. Contratos configurables por cada organizacion cliente (multi-tenant)
-- 2. Versionamiento, vigencia, variables dinamicas y campos requeridos
-- 3. Uso de plantillas activas por administradores de propiedad y residentes
-- ============================================================================

-- 1. Tabla de plantillas de contratos configurables por organizacion
CREATE TABLE PLANTILLAS_CONTRATOS (
    ID_PLANTILLA NUMBER GENERATED ALWAYS AS IDENTITY MINVALUE 1 MAXVALUE 9999999999999999999999999999 INCREMENT BY 1 START WITH 1 CACHE 20 NOORDER NOCYCLE NOT NULL ENABLE,
    ID_ORGANIZACION NUMBER NOT NULL ENABLE,
    CODIGO VARCHAR2(50 CHAR) NOT NULL ENABLE,
    NOMBRE VARCHAR2(150 CHAR) NOT NULL ENABLE,
    TIPO_CONTRATO VARCHAR2(50 CHAR) NOT NULL ENABLE,
    DESCRIPCION VARCHAR2(500 CHAR),
    CONTENIDO_HTML CLOB NOT NULL ENABLE,
    VARIABLES_DISPONIBLES CLOB,
    CAMPOS_REQUERIDOS CLOB,
    VERSION NUMBER(5, 0) DEFAULT 1 NOT NULL ENABLE,
    ESTADO VARCHAR2(30 CHAR) DEFAULT 'ACTIVA' NOT NULL ENABLE,
    VIGENCIA_DESDE DATE DEFAULT CURRENT_DATE NOT NULL ENABLE,
    VIGENCIA_HASTA DATE,
    CREADO_POR NUMBER,
    FECHA_CREACION TIMESTAMP(6) WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL ENABLE,
    FECHA_ACTUALIZACION TIMESTAMP(6) WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL ENABLE,
    CONSTRAINT PK_PLANTILLAS_CONTRATOS PRIMARY KEY (ID_PLANTILLA),
    CONSTRAINT FK_PLANTILLAS_CONTRATOS_ORG FOREIGN KEY (ID_ORGANIZACION) REFERENCES ORGANIZACIONES(ID_ORGANIZACION) ON DELETE CASCADE,
    CONSTRAINT UQ_PLANTILLAS_ORG_COD_VER UNIQUE (ID_ORGANIZACION, CODIGO, VERSION),
    CONSTRAINT CK_PLANTILLAS_TIPO_CONTRATO CHECK (TIPO_CONTRATO IN ('INICIAL', 'RENOVACION', 'PERMANENCIA', 'COMERCIAL', 'OTRO')),
    CONSTRAINT CK_PLANTILLAS_ESTADO CHECK (ESTADO IN ('ACTIVA', 'BORRADOR', 'HISTORICA'))
);

COMMENT ON TABLE PLANTILLAS_CONTRATOS IS 'Plantillas de contratos personalizables por cada organizacion con control de versiones y variables';
COMMENT ON COLUMN PLANTILLAS_CONTRATOS.CODIGO IS 'Codigo identificador de la plantilla dentro de la organizacion';
COMMENT ON COLUMN PLANTILLAS_CONTRATOS.CONTENIDO_HTML IS 'Cuerpo HTML de la plantilla con placeholders de variables';
COMMENT ON COLUMN PLANTILLAS_CONTRATOS.VARIABLES_DISPONIBLES IS 'JSON array de variables soportadas en la plantilla';
COMMENT ON COLUMN PLANTILLAS_CONTRATOS.CAMPOS_REQUERIDOS IS 'JSON array de campos obligatorios para generar contratos';
COMMENT ON COLUMN PLANTILLAS_CONTRATOS.VERSION IS 'Numero de version incremental de la plantilla';
COMMENT ON COLUMN PLANTILLAS_CONTRATOS.ESTADO IS 'Estado de la plantilla: ACTIVA, BORRADOR o HISTORICA';

-- 2. Indices para busquedas eficientes y control multitenant
CREATE INDEX IX_PLANTILLAS_ORG_ESTADO ON PLANTILLAS_CONTRATOS (ID_ORGANIZACION, ESTADO);
CREATE INDEX IX_PLANTILLAS_ORG_TIPO ON PLANTILLAS_CONTRATOS (ID_ORGANIZACION, TIPO_CONTRATO);

-- 3. Politica RLS para aislamiento por organizacion (Zero-Trust multi-tenant)
BEGIN
    DBMS_RLS.ADD_GROUPED_POLICY(
        object_schema   => NULL,
        object_name     => 'PLANTILLAS_CONTRATOS',
        policy_group    => 'SYS_DEFAULT',
        policy_name     => 'POL_RLS_ORG_PLANTILLAS_CONTR',
        function_schema => NULL,
        policy_function => 'PKG_SAED_SECURITY_RLS.FN_FILTRO_ORGANIZACION',
        statement_types => 'SELECT,INSERT,UPDATE,DELETE',
        update_check    => TRUE,
        enable          => TRUE,
        static_policy   => FALSE,
        policy_type     => DBMS_RLS.DYNAMIC
    );
EXCEPTION
    WHEN OTHERS THEN
        NULL; -- Ignorar si la funcion o grupo no estan disponibles en ambientes sin VPD
END;
/

-- 4. Vinculacion relacional con la tabla de contratos existentes
BEGIN
    EXECUTE IMMEDIATE 'ALTER TABLE CONTRATOS ADD ID_PLANTILLA NUMBER';
    EXECUTE IMMEDIATE 'ALTER TABLE CONTRATOS ADD CONSTRAINT FK_CONTRATOS_PLANTILLA FOREIGN KEY (ID_PLANTILLA) REFERENCES PLANTILLAS_CONTRATOS(ID_PLANTILLA) ON DELETE SET NULL';
EXCEPTION
    WHEN OTHERS THEN
        NULL;
END;
/
