-- =============================================================================
-- SAED 2.0 - V5.24: ACTAS DE ASAMBLEA PIPELINE (F10-05)
-- =============================================================================

-- 0. Contexto administrativo para DDL seguro
BEGIN
    PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1);
    PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN');
EXCEPTION
    WHEN OTHERS THEN NULL;
END;
/

-- 1. Agregar ID_DOCUMENTO a ACTAS_ASAMBLEA si no existe (Integración nativa F10-01)
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(1) INTO v_count FROM USER_TAB_COLS WHERE TABLE_NAME = 'ACTAS_ASAMBLEA' AND COLUMN_NAME = 'ID_DOCUMENTO';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE ACTAS_ASAMBLEA ADD (ID_DOCUMENTO NUMBER(19))';
    END IF;
END;
/

-- 2. FK e Índices para ACTAS_ASAMBLEA
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(1) INTO v_count FROM USER_CONSTRAINTS WHERE CONSTRAINT_NAME = 'FK_ACTAS_DOC';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE ACTAS_ASAMBLEA ADD CONSTRAINT FK_ACTAS_DOC FOREIGN KEY (ID_DOCUMENTO) REFERENCES DOCUMENTOS(ID_DOCUMENTO) ON DELETE SET NULL';
    END IF;

    SELECT COUNT(1) INTO v_count FROM USER_INDEXES WHERE INDEX_NAME = 'IX_ACTAS_DOC';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX IX_ACTAS_DOC ON ACTAS_ASAMBLEA (ID_DOCUMENTO)';
    END IF;
END;
/

-- 3. Asegurar política RLS en ACTAS_ASAMBLEA con PKG_SAED_SECURITY_RLS.FN_FILTRO_PROPIEDAD
BEGIN
    DBMS_RLS.ADD_GROUPED_POLICY(
        object_schema   => NULL,
        object_name     => 'ACTAS_ASAMBLEA',
        policy_group    => 'SYS_DEFAULT',
        policy_name     => 'POL_RLS_PROP_ACTAS_ASAMBLEA',
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

