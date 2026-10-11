-- =============================================================================
-- SAED 2.0 - V5.56: DURABLE PROPERTY-DELETION OTP CHALLENGES
-- =============================================================================
-- Audit F4-02. The deletion challenge (OTP) used to live in application memory: it was lost on restart and was
-- invisible to other instances. It is now a tenant table. Only the salted SHA-256 of the OTP is stored (never
-- the OTP), with its expiry (database clock), the failed-attempt counter and the lifecycle state. State changes
-- are conditional UPDATEs, so verification, blocking and the single consumption stay atomic across instances.
-- Rows disappear with their property (ON DELETE CASCADE). Tenant policy: FN_FILTRO_PROPIEDAD (ID_PROPIEDAD).
--
-- Idempotent: guarded by data-dictionary checks. Only a new table is created; any error aborts.

BEGIN
    PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1);
    PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN');
END;
/

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(1) INTO v_count FROM USER_TABLES WHERE TABLE_NAME = 'DESAFIOS_ELIM_PROPIEDAD';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE TABLE DESAFIOS_ELIM_PROPIEDAD ('
            || 'ID_DESAFIO VARCHAR2(36 CHAR) NOT NULL, '
            || 'ID_PROPIEDAD NUMBER NOT NULL, '
            || 'ID_ORGANIZACION NUMBER NOT NULL, '
            || 'ID_USUARIO NUMBER NOT NULL, '
            || 'OTP_HASH VARCHAR2(64 CHAR) NOT NULL, '
            || 'SALT VARCHAR2(36 CHAR) NOT NULL, '
            || 'INTENTOS NUMBER(3) DEFAULT 0 NOT NULL, '
            || 'ESTADO VARCHAR2(12 CHAR) DEFAULT ''PENDIENTE'' NOT NULL, '
            || 'FECHA_CREACION DATE DEFAULT SYSDATE NOT NULL, '
            || 'FECHA_EXPIRACION DATE NOT NULL, '
            || 'CONSTRAINT PK_DESAFIOS_ELIM_PROP PRIMARY KEY (ID_DESAFIO), '
            || 'CONSTRAINT CK_DESAFIOS_ELIM_ESTADO CHECK (ESTADO IN '
            || '(''PENDIENTE'', ''VERIFICADO'', ''CONSUMIDO'', ''EXPIRADO'', ''BLOQUEADO'')), '
            || 'CONSTRAINT FK_DESAFIOS_ELIM_PROP FOREIGN KEY (ID_PROPIEDAD) '
            || 'REFERENCES PROPIEDADES (ID_PROPIEDAD) ON DELETE CASCADE)';
    END IF;
END;
/

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(1) INTO v_count FROM USER_INDEXES
    WHERE TABLE_NAME = 'DESAFIOS_ELIM_PROPIEDAD' AND INDEX_NAME = 'IX_DESAFIOS_ELIM_PROP_USR';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX IX_DESAFIOS_ELIM_PROP_USR '
            || 'ON DESAFIOS_ELIM_PROPIEDAD (ID_PROPIEDAD, ID_USUARIO)';
    END IF;
END;
/

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(1) INTO v_count FROM USER_POLICIES
    WHERE OBJECT_NAME = 'DESAFIOS_ELIM_PROPIEDAD' AND POLICY_NAME = 'POL_RLS_PROP_DESAFIOS_ELIM';
    IF v_count = 0 THEN
        DBMS_RLS.ADD_POLICY(
            object_schema   => NULL,
            object_name     => 'DESAFIOS_ELIM_PROPIEDAD',
            policy_name     => 'POL_RLS_PROP_DESAFIOS_ELIM',
            function_schema => NULL,
            policy_function => 'PKG_SAED_SECURITY_RLS.FN_FILTRO_PROPIEDAD',
            statement_types => 'SELECT,INSERT,UPDATE,DELETE',
            update_check    => TRUE,
            enable          => TRUE
        );
    END IF;
END;
/
