-- =============================================================================
-- SAED 2.0 - V5.32: STARTUP-ONLY SCHEMA DRIFT
-- =============================================================================
-- Versions objects that previously existed only because application startup created them
-- (ProductionSchemaInitializer.initOnboardingIntenciones, initPersonasTenantColumns,
-- initMantenimientosYBloqueos, initReportesConfiguradosYHistorialPipeline, initPorteriaTurnos and
-- OnboardingServiceImpl.inicializarEsquemaStaging). Without them a migration-only schema fails:
-- PersonaRepositoryImpl filters on PERSONAS.ID_PROPIEDAD / ID_ORGANIZACION (ORA-00904), onboarding
-- has no staging table, and the V5.28 FN_FILTRO_PROPIEDAD default predicate references
-- BLOQUEOS_ZONA.ID_PROPIEDAD.
--
-- Idempotent: every change is guarded by a data-dictionary existence check, so environments that
-- already received these objects at startup are unchanged.
-- Runs as a non-exempt owner under active VPD, therefore:
--   * columns are added without a non-constant DEFAULT (full-table rewrite raises ORA-28133);
--   * no upsert statements on policy-protected tables (ORA-28132);
--   * index creation tolerates only ORA-01408 (equivalent column list already indexed);
--   * no blanket exception handlers: any other error aborts the migration.
--
-- Deliberately NOT included: POL_RLS_ORG_PERSONAS. The startup code never registers it; it removes
-- every policy on that table (ProductionSchemaInitializer.java lines 88-101, 435-445 and 504-521),
-- and V5.28 removes it as well. Re-adding it here would introduce a policy that neither the
-- migration chain nor the startup-maintained schema has, so it is out of scope for a drift fix.

BEGIN
    PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1);
    PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN');
END;
/

-- 1. ONBOARDING_INTENCIONES staging table (same definition as the startup DDL; no RLS policy at startup)
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(1) INTO v_count FROM USER_TABLES WHERE TABLE_NAME = 'ONBOARDING_INTENCIONES';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE TABLE ONBOARDING_INTENCIONES (
            REFERENCIA VARCHAR2(100 CHAR) PRIMARY KEY,
            DATOS_REGISTRO CLOB NOT NULL,
            MONTO_CENTAVOS NUMBER(14,0) NOT NULL,
            ESTADO VARCHAR2(30 CHAR) DEFAULT ''PENDIENTE'' NOT NULL,
            FECHA_CREACION TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
            FECHA_ACTUALIZACION TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
        )';
    END IF;
END;
/

-- 2. ONBOARDING_INTENCIONES e-mail tracking and linkage columns (constant defaults only)
DECLARE
    PROCEDURE add_column(p_table IN VARCHAR2, p_column IN VARCHAR2, p_definition IN VARCHAR2) IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(1) INTO v_count FROM USER_TAB_COLS
        WHERE TABLE_NAME = p_table AND COLUMN_NAME = p_column;
        IF v_count = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE ' || p_table || ' ADD (' || p_column || ' ' || p_definition || ')';
        END IF;
    END;
BEGIN
    add_column('ONBOARDING_INTENCIONES', 'CORREO_ESTADO', 'VARCHAR2(30 CHAR) DEFAULT ''SIMULADO''');
    add_column('ONBOARDING_INTENCIONES', 'CORREO_DETALLE', 'VARCHAR2(500 CHAR)');
    add_column('ONBOARDING_INTENCIONES', 'CORREO_FECHA', 'TIMESTAMP');
    add_column('ONBOARDING_INTENCIONES', 'ID_ORGANIZACION', 'NUMBER');
    add_column('ONBOARDING_INTENCIONES', 'ID_USUARIO', 'NUMBER');
END;
/

-- 3. PERSONAS tenant columns (nullable, no constraints, as at startup).
--    Backfill only when this migration adds the columns: environments that already have them
--    were already backfilled by startup. Of the startup backfill, only the two generic
--    resident/owner derivations are kept:
--      * the startup step that nulls ID_PROPIEDAD for administrative persons without a resident or
--        owner link is a no-op right after the columns are added (the two derivations below only
--        set rows that have such a link);
--      * the startup step keyed on specific e-mail addresses and document numbers is
--        environment-specific personal data and is not versioned.
DECLARE
    v_org_added  BOOLEAN;
    v_prop_added BOOLEAN;

    FUNCTION add_column(p_table IN VARCHAR2, p_column IN VARCHAR2, p_definition IN VARCHAR2) RETURN BOOLEAN IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(1) INTO v_count FROM USER_TAB_COLS
        WHERE TABLE_NAME = p_table AND COLUMN_NAME = p_column;
        IF v_count = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE ' || p_table || ' ADD (' || p_column || ' ' || p_definition || ')';
            RETURN TRUE;
        END IF;
        RETURN FALSE;
    END;
BEGIN
    v_org_added  := add_column('PERSONAS', 'ID_ORGANIZACION', 'NUMBER');
    v_prop_added := add_column('PERSONAS', 'ID_PROPIEDAD', 'NUMBER');

    IF v_org_added OR v_prop_added THEN
        EXECUTE IMMEDIATE '
            UPDATE PERSONAS p
            SET (ID_PROPIEDAD, ID_ORGANIZACION) = (
                SELECT u.ID_PROPIEDAD, pr.ID_ORGANIZACION
                FROM RESIDENTES_UNIDAD ru
                JOIN UNIDADES u ON ru.ID_UNIDAD = u.ID_UNIDAD
                JOIN PROPIEDADES pr ON u.ID_PROPIEDAD = pr.ID_PROPIEDAD
                WHERE ru.ID_PERSONA = p.ID_PERSONA AND ROWNUM = 1
            )
            WHERE p.ID_PROPIEDAD IS NULL AND EXISTS (
                SELECT 1 FROM RESIDENTES_UNIDAD ru WHERE ru.ID_PERSONA = p.ID_PERSONA
            )';

        EXECUTE IMMEDIATE '
            UPDATE PERSONAS p
            SET (ID_PROPIEDAD, ID_ORGANIZACION) = (
                SELECT u.ID_PROPIEDAD, pr.ID_ORGANIZACION
                FROM PROPIETARIOS_UNIDAD pu
                JOIN UNIDADES u ON pu.ID_UNIDAD = u.ID_UNIDAD
                JOIN PROPIEDADES pr ON u.ID_PROPIEDAD = pr.ID_PROPIEDAD
                WHERE pu.ID_PERSONA = p.ID_PERSONA AND ROWNUM = 1
            )
            WHERE p.ID_PROPIEDAD IS NULL AND EXISTS (
                SELECT 1 FROM PROPIETARIOS_UNIDAD pu WHERE pu.ID_PERSONA = p.ID_PERSONA
            )';
    END IF;
END;
/

-- 4. BLOQUEOS_ZONA tenant and maintenance columns (startup backfills ID_PROPIEDAD only when it adds it)
DECLARE
    v_prop_added BOOLEAN;
    v_mant_added BOOLEAN;

    FUNCTION add_column(p_table IN VARCHAR2, p_column IN VARCHAR2, p_definition IN VARCHAR2) RETURN BOOLEAN IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(1) INTO v_count FROM USER_TAB_COLS
        WHERE TABLE_NAME = p_table AND COLUMN_NAME = p_column;
        IF v_count = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE ' || p_table || ' ADD (' || p_column || ' ' || p_definition || ')';
            RETURN TRUE;
        END IF;
        RETURN FALSE;
    END;
BEGIN
    v_prop_added := add_column('BLOQUEOS_ZONA', 'ID_PROPIEDAD', 'NUMBER');
    IF v_prop_added THEN
        EXECUTE IMMEDIATE 'UPDATE BLOQUEOS_ZONA b SET ID_PROPIEDAD = (SELECT z.ID_PROPIEDAD FROM ZONAS_COMUNES z WHERE z.ID_ZONA = b.ID_ZONA) WHERE b.ID_PROPIEDAD IS NULL';
    END IF;
    -- Startup does not backfill ID_MANTENIMIENTO.
    v_mant_added := add_column('BLOQUEOS_ZONA', 'ID_MANTENIMIENTO', 'NUMBER');
END;
/

-- 5. BLOQUEOS_ZONA indexes
DECLARE
    PROCEDURE add_index(p_name IN VARCHAR2, p_definition IN VARCHAR2) IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(1) INTO v_count FROM USER_INDEXES WHERE INDEX_NAME = p_name;
        IF v_count = 0 THEN
            BEGIN
                EXECUTE IMMEDIATE 'CREATE INDEX ' || p_name || ' ON ' || p_definition;
            EXCEPTION
                WHEN OTHERS THEN
                    -- ORA-01408: an equivalent column list is already indexed under another name.
                    IF SQLCODE != -1408 THEN RAISE; END IF;
            END;
        END IF;
    END;
BEGIN
    add_index('IX_BLOQUEO_MANT', 'BLOQUEOS_ZONA (ID_MANTENIMIENTO)');
    add_index('IX_BLOQUEO_PROP', 'BLOQUEOS_ZONA (ID_PROPIEDAD)');
END;
/

-- 6. RLS policies registered at startup (same group, function, statement types and flags).
--    Object and function schema are NULL, i.e. the current owner.
--    Startup also tries POL_RLS_PROP_HISTORIAL_REPORTES, but that name has 31 characters and
--    DBMS_RLS rejects it (ORA-28104, verified on the isolated install); it would only duplicate the
--    baseline POL_RLS_PROP_HISTORIAL_REPOR, which already applies FN_FILTRO_PROPIEDAD to all
--    statement types. It is therefore not registered here.
DECLARE
    PROCEDURE add_policy(p_table IN VARCHAR2, p_policy IN VARCHAR2, p_function IN VARCHAR2, p_statements IN VARCHAR2) IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(1) INTO v_count FROM USER_POLICIES
        WHERE OBJECT_NAME = p_table AND POLICY_NAME = p_policy;
        IF v_count = 0 THEN
            DBMS_RLS.ADD_GROUPED_POLICY(
                object_schema   => NULL,
                object_name     => p_table,
                policy_group    => 'SYS_DEFAULT',
                policy_name     => p_policy,
                function_schema => NULL,
                policy_function => p_function,
                statement_types => p_statements,
                update_check    => FALSE,
                enable          => TRUE,
                static_policy   => FALSE,
                policy_type     => DBMS_RLS.DYNAMIC,
                long_predicate  => FALSE
            );
        END IF;
    END;
BEGIN
    add_policy('PORTERIA_TURNOS', 'POL_RLS_PROP_PORT_TURNOS', 'PKG_SAED_SECURITY_RLS.FN_FILTRO_PROPIEDAD', 'SELECT,INSERT,UPDATE,DELETE');
END;
/

COMMIT;
