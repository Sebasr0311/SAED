-- =============================================================================
-- SAED 2.0 - V5.25: POLIZAS DE SEGURO PIPELINE (F10-07)
-- Cierre de Gaps: Deducible, Integración Documental F10-01 y RLS
-- =============================================================================

-- 0. Contexto administrativo para DDL seguro
BEGIN
    PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1);
    PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN');
EXCEPTION
    WHEN OTHERS THEN NULL;
END;
/

-- 1. Agregar DEDUCIBLE a POLIZAS_SEGURO si no existe
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(1) INTO v_count FROM USER_TAB_COLS WHERE TABLE_NAME = 'POLIZAS_SEGURO' AND COLUMN_NAME = 'DEDUCIBLE';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE POLIZAS_SEGURO ADD (DEDUCIBLE VARCHAR2(255 CHAR))';
    END IF;
END;
/

-- 2. Agregar ID_DOCUMENTO a POLIZAS_SEGURO si no existe (Integración F10-01)
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(1) INTO v_count FROM USER_TAB_COLS WHERE TABLE_NAME = 'POLIZAS_SEGURO' AND COLUMN_NAME = 'ID_DOCUMENTO';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE POLIZAS_SEGURO ADD (ID_DOCUMENTO NUMBER(19))';
    END IF;
END;
/

-- 3. FK e Índices para Integración Documental
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(1) INTO v_count FROM USER_CONSTRAINTS WHERE CONSTRAINT_NAME = 'FK_POLIZAS_DOC';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE POLIZAS_SEGURO ADD CONSTRAINT FK_POLIZAS_DOC FOREIGN KEY (ID_DOCUMENTO) REFERENCES DOCUMENTOS(ID_DOCUMENTO) ON DELETE SET NULL';
    END IF;

    SELECT COUNT(1) INTO v_count FROM USER_INDEXES WHERE INDEX_NAME = 'IX_POLIZAS_DOC';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX IX_POLIZAS_DOC ON POLIZAS_SEGURO (ID_DOCUMENTO)';
    END IF;
END;
/

-- 4. Asegurar política RLS en POLIZAS_SEGURO con PKG_SAED_SECURITY_RLS.FN_FILTRO_PROPIEDAD
BEGIN
    DBMS_RLS.ADD_GROUPED_POLICY(
        object_schema   => NULL,
        object_name     => 'POLIZAS_SEGURO',
        policy_group    => 'SYS_DEFAULT',
        policy_name     => 'POL_RLS_PROP_POLIZAS_SEGURO',
        function_schema => NULL,
        policy_function => 'PKG_SAED_SECURITY_RLS.FN_FILTRO_PROPIEDAD',
        update_check    => FALSE,
        enable          => TRUE,
        static_policy   => FALSE,
        policy_type     => dbms_rls.DYNAMIC,
        long_predicate  => FALSE
    );
EXCEPTION
    WHEN OTHERS THEN NULL;
END;
/
