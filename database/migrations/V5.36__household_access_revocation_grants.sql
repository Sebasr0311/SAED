-- =============================================================================
-- SAED 2.0 - V5.36: GRANTS FOR THE HOUSEHOLD ACCESS REVOCATION PROCEDURE
-- =============================================================================
-- F05-01. When a titular removes a household member from the unit, that member's resident assignment for the
-- unit must be revoked. Row-level policies only let a resident see their own USUARIO_ASIGNACIONES rows, and the
-- application must not elevate the database context, so the revocation runs in a validated security-schema
-- procedure (V5.37, SP_REVOCAR_ACCESO_UNIDAD). This file grants the minimum it needs:
--   * SELECT on RESIDENTES_UNIDAD (validate the member's residence),
--   * UPDATE (ESTADO, FECHA_FIN) on USUARIO_ASIGNACIONES (revoke the assignment).
-- The grantee is the security account that owns PKG_AUTH_BOOTSTRAP. It is named SAED_SEC_MASTER, as in the
-- V5.0 baseline authentication grants. (It cannot be resolved through ALL_OBJECTS: the application owner has no
-- privilege on that package, so the lookup returned no row — ORA-01403 on the isolated install.)
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
    EXECUTE IMMEDIATE 'GRANT SELECT ON RESIDENTES_UNIDAD TO ' || DBMS_ASSERT.ENQUOTE_NAME(v_sec_owner, FALSE);
    EXECUTE IMMEDIATE 'GRANT UPDATE (ESTADO, FECHA_FIN) ON USUARIO_ASIGNACIONES TO '
        || DBMS_ASSERT.ENQUOTE_NAME(v_sec_owner, FALSE);
END;
/

COMMIT;
