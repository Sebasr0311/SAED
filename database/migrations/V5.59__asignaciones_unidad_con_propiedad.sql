-- =============================================================================
-- SAED 2.0 - V5.59: A UNIT-LEVEL ASSIGNMENT ALWAYS CARRIES ITS PROPERTY
-- =============================================================================
-- Since V5.58 the resident and porter RLS predicates scope a user's assignments to the active context property
-- (id_propiedad = <context property>). A unit assignment without ID_PROPIEDAD would therefore silently lose all
-- access. Every application insert path already sets it; this migration back-fills any legacy row from its unit
-- and then enforces the rule with a validated CHECK constraint.
--
-- Idempotent; plain UPDATE (no MERGE) under the bootstrap context; the constraint is guarded by a data-dictionary
-- check; no blanket exception handlers.

BEGIN
    PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1);
    PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN');
END;
/

UPDATE USUARIO_ASIGNACIONES a
SET a.ID_PROPIEDAD = (SELECT u.ID_PROPIEDAD FROM UNIDADES u WHERE u.ID_UNIDAD = a.ID_UNIDAD)
WHERE a.ID_UNIDAD IS NOT NULL AND a.ID_PROPIEDAD IS NULL;

COMMIT;

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(1) INTO v_count FROM USER_CONSTRAINTS
    WHERE TABLE_NAME = 'USUARIO_ASIGNACIONES' AND CONSTRAINT_NAME = 'CK_ASIG_UNIDAD_PROPIEDAD';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE USUARIO_ASIGNACIONES ADD CONSTRAINT CK_ASIG_UNIDAD_PROPIEDAD '
            || 'CHECK (ID_UNIDAD IS NULL OR ID_PROPIEDAD IS NOT NULL)';
    END IF;
END;
/
