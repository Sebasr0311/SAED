-- =============================================================================
-- SAED 2.0 - V5.90: SEED THE INCIDENTES MODULE CATALOG ENTRY AND PLAN ENTITLEMENTS
-- =============================================================================
-- IncidenteController is annotated with @RequireModule("INCIDENTES") but V5.13 never seeded that module,
-- so every request to /api/v1/incidentes/** was answered with 403 for all roles.
-- Entitlement mirrors PQRS: FREE (1) = 'N', PRO (2) = 'S', ENTERPRISE (3) = 'S'.
--
-- Idempotent; INSERT ... SELECT ... WHERE NOT EXISTS (no MERGE, which active VPD rejects with ORA-28132).

BEGIN
    PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1);
    PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN');
END;
/

INSERT INTO MODULOS (CODIGO, NOMBRE, DESCRIPCION)
SELECT 'INCIDENTES', 'Incidentes y Convivencia', 'Registro, seguimiento y resolucion de incidentes de convivencia y seguridad.'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM MODULOS WHERE CODIGO = 'INCIDENTES');

INSERT INTO PLAN_MODULOS (ID_PLAN, ID_MODULO, HABILITADO)
SELECT p.ID_PLAN, m.ID_MODULO, CASE WHEN p.ID_PLAN = 1 THEN 'N' ELSE 'S' END
FROM (SELECT 1 AS ID_PLAN FROM DUAL UNION ALL SELECT 2 FROM DUAL UNION ALL SELECT 3 FROM DUAL) p
CROSS JOIN MODULOS m
WHERE m.CODIGO = 'INCIDENTES'
  AND NOT EXISTS (
      SELECT 1 FROM PLAN_MODULOS pm WHERE pm.ID_PLAN = p.ID_PLAN AND pm.ID_MODULO = m.ID_MODULO
  );

COMMIT;
