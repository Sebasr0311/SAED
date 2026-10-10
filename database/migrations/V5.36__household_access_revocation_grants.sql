-- =============================================================================
-- SAED 2.0 - V5.36: GRANTS FOR THE HOUSEHOLD ACCESS REVOCATION PROCEDURE
-- =============================================================================
-- F05-01. When a titular removes a household member from the unit, that member's resident assignment for the
-- unit must be revoked. Row-level policies only let a resident see their own USUARIO_ASIGNACIONES rows, and the
-- application must not elevate the database context, so the revocation runs in a validated security-schema
-- procedure (V5.37, SP_REVOCAR_ACCESO_UNIDAD). This file grants the minimum it needs:
--   * SELECT on RESIDENTES_UNIDAD (validate the member's residence),
--   * UPDATE (ESTADO, FECHA_FIN) on USUARIO_ASIGNACIONES (revoke the assignment).
-- The grantee is the owner of PKG_AUTH_BOOTSTRAP (the security account), resolved from the catalog.
-- Idempotent: GRANT is repeatable. No data or policy is changed.

BEGIN
    PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1);
    PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN');
END;
/

DECLARE
    v_sec_owner VARCHAR2(128);
BEGIN
    SELECT OWNER INTO v_sec_owner
    FROM ALL_OBJECTS
    WHERE OBJECT_NAME = 'PKG_AUTH_BOOTSTRAP' AND OBJECT_TYPE = 'PACKAGE'
      AND ROWNUM = 1;
    EXECUTE IMMEDIATE 'GRANT SELECT ON RESIDENTES_UNIDAD TO ' || DBMS_ASSERT.ENQUOTE_NAME(v_sec_owner, FALSE);
    EXECUTE IMMEDIATE 'GRANT UPDATE (ESTADO, FECHA_FIN) ON USUARIO_ASIGNACIONES TO '
        || DBMS_ASSERT.ENQUOTE_NAME(v_sec_owner, FALSE);
END;
/

COMMIT;
