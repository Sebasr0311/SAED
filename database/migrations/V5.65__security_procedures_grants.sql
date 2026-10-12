-- =============================================================================
-- SAED 2.0 - V5.65: GRANTS FOR THE COHABITANT REGISTRATION PROCEDURE
-- =============================================================================
-- A resident registers a cohabitant account. Row-level policies only let a resident touch its own USUARIOS and
-- USUARIO_ASIGNACIONES rows and USUARIOS visibility must not be widened, so the registration runs in a validated
-- security-schema procedure (V5.66, SP_REGISTRAR_CONVIVIENTE). This file grants the security account the minimum the
-- procedure needs on top of V5.0 / V5.36 (SELECT on USUARIOS, USUARIO_ASIGNACIONES, ROLES, RESIDENTES_UNIDAD):
--   * INSERT on USUARIOS and USUARIO_ASIGNACIONES (create the account and its assignment),
--   * SELECT on UNIDADES (unit, property and organization of the target unit),
--   * SELECT on PROPIEDAD_CONFIGURACION (LIMITE_CONVIVIENTES_POR_UNIDAD).
-- Idempotent: GRANT is repeatable. No data or policy is changed.

BEGIN
    PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1);
    PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN');
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
    EXECUTE IMMEDIATE 'GRANT INSERT ON USUARIOS TO ' || DBMS_ASSERT.ENQUOTE_NAME(v_sec_owner, FALSE);
    EXECUTE IMMEDIATE 'GRANT INSERT ON USUARIO_ASIGNACIONES TO ' || DBMS_ASSERT.ENQUOTE_NAME(v_sec_owner, FALSE);
    EXECUTE IMMEDIATE 'GRANT SELECT ON UNIDADES TO ' || DBMS_ASSERT.ENQUOTE_NAME(v_sec_owner, FALSE);
    EXECUTE IMMEDIATE 'GRANT SELECT ON PROPIEDAD_CONFIGURACION TO ' || DBMS_ASSERT.ENQUOTE_NAME(v_sec_owner, FALSE);
END;
/

COMMIT;
