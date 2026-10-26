-- =============================================================================
-- SAED 2.0 - V5.72: ROW-LEVEL POLICIES FOR GASTOS_SOPORTES_HISTORIAL AND TOKENS_ACTIVACION
-- =============================================================================
-- Two tenant-relevant tables were left without a policy (oracle-isolation-progress.md, "tables without a policy").
--
-- GASTOS_SOPORTES_HISTORIAL (replaced expense supports, financial audit trail)
--   It had no tenant column, so the property predicate could not be applied. The column ID_PROPIEDAD is added
--   WITHOUT default (active VPD rejects ADD COLUMN ... DEFAULT with ORA-28133), back-filled from GASTOS, made
--   NOT NULL, and kept filled by a BEFORE INSERT trigger that copies it from the parent expense, so no writer needs
--   to change. The policy uses FN_FILTRO_PROPIEDAD, i.e. the same visibility as the expense itself.
--
-- TOKENS_ACTIVACION (single-use activation / password-reset tokens; only the SHA-256 hash is stored)
--   It has no property or organization column; the owner of a token is its user, so the policy uses
--   FN_FILTRO_USUARIOS on ID_USUARIO (platform administrator: all; organization/property administrator: users of
--   the organization; residents: themselves; anonymous: nothing). The activation endpoints are unauthenticated and
--   therefore see no row: token issuance, lookup, activation and resend now run in validated security-schema
--   procedures (V5.73, __sec_). This file grants the security account what those procedures need:
--     * SELECT, INSERT, UPDATE on TOKENS_ACTIVACION,
--     * UPDATE (HASH_PASSWORD, ESTADO, INTENTOS_FALLIDOS) on USUARIOS (set the password and activate).
--
-- EVENTOS_SISTEMA stays without a policy on purpose: it is a global catalog of automation trigger events (code,
-- name, origin module, payload variables); it holds no tenant data and no tenant column. Tenant rules reference
-- it by foreign key and are protected themselves.
--
-- Idempotent: dictionary checks before every change; plain UPDATE (no MERGE); no blanket exception handlers.

BEGIN
    PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1);
    PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN');
END;
/

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(1) INTO v_count FROM USER_TAB_COLS
    WHERE TABLE_NAME = 'GASTOS_SOPORTES_HISTORIAL' AND COLUMN_NAME = 'ID_PROPIEDAD';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE GASTOS_SOPORTES_HISTORIAL ADD (ID_PROPIEDAD NUMBER)';
    END IF;
END;
/

UPDATE GASTOS_SOPORTES_HISTORIAL h
SET h.ID_PROPIEDAD = (SELECT g.ID_PROPIEDAD FROM GASTOS g WHERE g.ID_GASTO = h.ID_GASTO)
WHERE h.ID_PROPIEDAD IS NULL;

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(1) INTO v_count FROM USER_TAB_COLS
    WHERE TABLE_NAME = 'GASTOS_SOPORTES_HISTORIAL' AND COLUMN_NAME = 'ID_PROPIEDAD' AND NULLABLE = 'Y';
    IF v_count > 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE GASTOS_SOPORTES_HISTORIAL MODIFY (ID_PROPIEDAD NOT NULL)';
    END IF;
END;
/

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(1) INTO v_count FROM USER_INDEXES WHERE INDEX_NAME = 'IX_HIST_PROPIEDAD';
    IF v_count = 0 THEN
        BEGIN
            EXECUTE IMMEDIATE 'CREATE INDEX IX_HIST_PROPIEDAD ON GASTOS_SOPORTES_HISTORIAL (ID_PROPIEDAD)';
        EXCEPTION
            WHEN OTHERS THEN
                -- ORA-01408: an equivalent index already exists under another name.
                IF SQLCODE != -1408 THEN
                    RAISE;
                END IF;
        END;
    END IF;
END;
/

CREATE OR REPLACE TRIGGER TRG_GASTOS_SOP_HIST_PROP
BEFORE INSERT ON GASTOS_SOPORTES_HISTORIAL
FOR EACH ROW
WHEN (NEW.ID_PROPIEDAD IS NULL)
BEGIN
    SELECT g.ID_PROPIEDAD INTO :NEW.ID_PROPIEDAD FROM GASTOS g WHERE g.ID_GASTO = :NEW.ID_GASTO;
END;
/

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(1) INTO v_count FROM USER_POLICIES
    WHERE OBJECT_NAME = 'GASTOS_SOPORTES_HISTORIAL' AND POLICY_NAME = 'POL_RLS_PROP_GASTOS_SOP_HIST';
    IF v_count = 0 THEN
        DBMS_RLS.ADD_GROUPED_POLICY(
            object_schema   => NULL,
            object_name     => 'GASTOS_SOPORTES_HISTORIAL',
            policy_group    => 'SYS_DEFAULT',
            policy_name     => 'POL_RLS_PROP_GASTOS_SOP_HIST',
            function_schema => NULL,
            policy_function => 'PKG_SAED_SECURITY_RLS.FN_FILTRO_PROPIEDAD',
            statement_types => 'SELECT,INSERT,UPDATE,DELETE',
            update_check    => TRUE,
            enable          => TRUE,
            static_policy   => FALSE,
            policy_type     => DBMS_RLS.DYNAMIC,
            long_predicate  => FALSE
        );
    END IF;
END;
/

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(1) INTO v_count FROM USER_POLICIES
    WHERE OBJECT_NAME = 'TOKENS_ACTIVACION' AND POLICY_NAME = 'POL_RLS_USR_TOKENS_ACT';
    IF v_count = 0 THEN
        DBMS_RLS.ADD_GROUPED_POLICY(
            object_schema   => NULL,
            object_name     => 'TOKENS_ACTIVACION',
            policy_group    => 'SYS_DEFAULT',
            policy_name     => 'POL_RLS_USR_TOKENS_ACT',
            function_schema => NULL,
            policy_function => 'PKG_SAED_SECURITY_RLS.FN_FILTRO_USUARIOS',
            statement_types => 'SELECT,INSERT,UPDATE,DELETE',
            update_check    => TRUE,
            enable          => TRUE,
            static_policy   => FALSE,
            policy_type     => DBMS_RLS.DYNAMIC,
            long_predicate  => FALSE
        );
    END IF;
END;
/

DECLARE
    v_sec_owner CONSTANT VARCHAR2(128) := 'SAED_SEC_MASTER';
    v_count NUMBER;
BEGIN
    SELECT COUNT(1) INTO v_count FROM ALL_USERS WHERE USERNAME = v_sec_owner;
    IF v_count = 0 THEN
        RAISE_APPLICATION_ERROR(-20936, 'Security account ' || v_sec_owner || ' does not exist');
    END IF;
    EXECUTE IMMEDIATE 'GRANT SELECT, INSERT, UPDATE ON TOKENS_ACTIVACION TO ' || DBMS_ASSERT.ENQUOTE_NAME(v_sec_owner, FALSE);
    EXECUTE IMMEDIATE 'GRANT UPDATE (HASH_PASSWORD, ESTADO, INTENTOS_FALLIDOS) ON USUARIOS TO '
        || DBMS_ASSERT.ENQUOTE_NAME(v_sec_owner, FALSE);
END;
/

COMMIT;
