-- ============================================================================
-- V5.63__visitantes_scope_and_contratos_activo_unico.sql
-- Two schema corrections found by the database test tier.
--
-- 1. VISITANTES owns its scope. A visitor pre-registered by a resident or an administrator has no visit yet, and the
--    VISITANTES row-level policy only admitted rows reachable through VISITAS, so the insert was rejected
--    (ORA-28115 / ACCESS_DENIED_RLS). VISITANTES gets ID_PROPIEDAD (the property whose context registered the
--    visitor) and REGISTRADO_POR (the registering user). V5.64 builds the predicates on them. Existing rows get the
--    property of their most recent visit; a visitor that never had a visit stays NULL (visible to nobody but the
--    platform administrator, exactly as before).
--    Oracle rules honoured: columns are added WITHOUT default (no table rewrite under active VPD), plain UPDATE (no
--    MERGE on a policy table), every change guarded by the data dictionary.
--
-- 2. UIX_CONTRATOS_ACTIVO_UNIDAD was (ID_UNIDAD, CASE ESTADO WHEN 'ACTIVO' THEN 1 END). Oracle skips an index entry
--    only when ALL its keys are NULL, and ID_UNIDAD is never NULL, so every contract was indexed and a unit could
--    hold a single non-active contract (no history). The index becomes unique over ONE function-based key that is
--    NULL unless the contract is ACTIVO, which keeps "one ACTIVE contract per unit" and allows any number of
--    historical contracts.
-- ============================================================================
BEGIN
    PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1);
    PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN');
END;
/

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(1) INTO v_count FROM USER_TAB_COLS WHERE TABLE_NAME = 'VISITANTES' AND COLUMN_NAME = 'ID_PROPIEDAD';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE VISITANTES ADD (ID_PROPIEDAD NUMBER)';
    END IF;
    SELECT COUNT(1) INTO v_count FROM USER_TAB_COLS WHERE TABLE_NAME = 'VISITANTES' AND COLUMN_NAME = 'REGISTRADO_POR';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE VISITANTES ADD (REGISTRADO_POR NUMBER)';
    END IF;
END;
/

UPDATE VISITANTES vis
SET vis.ID_PROPIEDAD = (SELECT u.ID_PROPIEDAD
                        FROM VISITAS v JOIN UNIDADES u ON u.ID_UNIDAD = v.ID_UNIDAD
                        WHERE v.ID_VISITANTE = vis.ID_VISITANTE
                        ORDER BY v.ID_VISITA DESC
                        FETCH FIRST 1 ROW ONLY)
WHERE vis.ID_PROPIEDAD IS NULL;

COMMIT;

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(1) INTO v_count FROM USER_CONSTRAINTS
    WHERE TABLE_NAME = 'VISITANTES' AND CONSTRAINT_NAME = 'FK_VISITANTES_PROPIEDAD';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE VISITANTES ADD CONSTRAINT FK_VISITANTES_PROPIEDAD '
            || 'FOREIGN KEY (ID_PROPIEDAD) REFERENCES PROPIEDADES (ID_PROPIEDAD)';
    END IF;
    SELECT COUNT(1) INTO v_count FROM USER_CONSTRAINTS
    WHERE TABLE_NAME = 'VISITANTES' AND CONSTRAINT_NAME = 'FK_VISITANTES_REGISTRADO_POR';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE VISITANTES ADD CONSTRAINT FK_VISITANTES_REGISTRADO_POR '
            || 'FOREIGN KEY (REGISTRADO_POR) REFERENCES USUARIOS (ID_USUARIO) ON DELETE SET NULL';
    END IF;
    SELECT COUNT(1) INTO v_count FROM USER_INDEXES WHERE INDEX_NAME = 'IX_VISITANTES_PROPIEDAD';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX IX_VISITANTES_PROPIEDAD ON VISITANTES (ID_PROPIEDAD)';
    END IF;
END;
/

DECLARE
    v_count NUMBER;
    v_keys  NUMBER;
BEGIN
    SELECT COUNT(1) INTO v_count FROM USER_INDEXES WHERE INDEX_NAME = 'UIX_CONTRATOS_ACTIVO_UNIDAD';
    IF v_count > 0 THEN
        SELECT COUNT(1) INTO v_keys FROM USER_IND_COLUMNS WHERE INDEX_NAME = 'UIX_CONTRATOS_ACTIVO_UNIDAD';
        IF v_keys > 1 THEN
            EXECUTE IMMEDIATE 'DROP INDEX UIX_CONTRATOS_ACTIVO_UNIDAD';
            v_count := 0;
        END IF;
    END IF;
    IF v_count = 0 THEN
        SELECT COUNT(1) INTO v_count FROM (
            SELECT ID_UNIDAD FROM CONTRATOS WHERE ESTADO = 'ACTIVO' GROUP BY ID_UNIDAD HAVING COUNT(*) > 1);
        IF v_count > 0 THEN
            RAISE_APPLICATION_ERROR(-20963, 'Cannot enforce one ACTIVE contract per unit: ' || v_count
                || ' unit(s) already hold several ACTIVO contracts');
        END IF;
        EXECUTE IMMEDIATE 'CREATE UNIQUE INDEX UIX_CONTRATOS_ACTIVO_UNIDAD ON CONTRATOS '
            || '((CASE WHEN ESTADO = ''ACTIVO'' THEN ID_UNIDAD END))';
    END IF;
END;
/
