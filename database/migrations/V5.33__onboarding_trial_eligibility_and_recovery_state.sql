-- =============================================================================
-- SAED 2.0 - V5.33: ONBOARDING TRIAL ELIGIBILITY AND PAYMENT RECOVERY STATE
-- =============================================================================
-- DEC-06 / F3-04 / F3-08 / F3-09 / F3-12.
--   * PLANES.PERMITE_PRUEBA: a trial is offered only for plans flagged 'S'. Every existing plan
--     starts as 'N' (no trial) until a platform administrator enables it.
--   * ONBOARDING_INTENCIONES gets first-class columns for the flow type (PAGO / PRUEBA / GRATIS),
--     tax id, administrator e-mail, plan and the reason of the last state change, so that
--     "one trial per tax id / e-mail" and plan-reference checks no longer parse DATOS_REGISTRO.
--   * State PAGO_REQUIERE_REVISION records a verified payment that could not be provisioned
--     (rejected or stale intention, duplicate tax id, ...): explicit recovery, never silent.
--     The state check is added ENABLE NOVALIDATE so that unknown legacy rows do not block it.
--
-- Idempotent; runs as a non-exempt owner under active VPD:
--   * new columns use constant defaults only (no ORA-28133 rewrite);
--   * no MERGE on policy-protected tables (ONBOARDING_INTENCIONES and PLANES have no policy);
--   * index creation tolerates only ORA-01408;
--   * no blanket exception handlers.

BEGIN
    PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1);
    PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN');
END;
/

-- 1. Trial eligibility flag on plans
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(1) INTO v_count FROM USER_TAB_COLS
    WHERE TABLE_NAME = 'PLANES' AND COLUMN_NAME = 'PERMITE_PRUEBA';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE PLANES ADD (PERMITE_PRUEBA CHAR(1 CHAR) DEFAULT ''N'' NOT NULL)';
    END IF;

    SELECT COUNT(1) INTO v_count FROM USER_CONSTRAINTS
    WHERE TABLE_NAME = 'PLANES' AND CONSTRAINT_NAME = 'CK_PLANES_PERMITE_PRUEBA';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE PLANES ADD CONSTRAINT CK_PLANES_PERMITE_PRUEBA CHECK (PERMITE_PRUEBA IN (''S'', ''N''))';
    END IF;
END;
/

-- 2. Intention columns
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
    add_column('ONBOARDING_INTENCIONES', 'TIPO_FLUJO', 'VARCHAR2(20 CHAR)');
    add_column('ONBOARDING_INTENCIONES', 'IDENTIFICACION_FISCAL', 'VARCHAR2(40 CHAR)');
    add_column('ONBOARDING_INTENCIONES', 'EMAIL_ADMIN', 'VARCHAR2(150 CHAR)');
    add_column('ONBOARDING_INTENCIONES', 'ID_PLAN', 'NUMBER');
    add_column('ONBOARDING_INTENCIONES', 'MOTIVO_ESTADO', 'VARCHAR2(500 CHAR)');
END;
/

-- 3. Backfill the new columns of existing intentions from their stored registration JSON.
--    JSON_VALUE returns NULL for rows whose payload is not valid JSON (default NULL ON ERROR).
BEGIN
    EXECUTE IMMEDIATE q'[
        UPDATE ONBOARDING_INTENCIONES
        SET IDENTIFICACION_FISCAL = REGEXP_REPLACE(JSON_VALUE(DATOS_REGISTRO, '$.identificacionFiscal'), '[^0-9A-Za-z-]', ''),
            EMAIL_ADMIN = LOWER(TRIM(JSON_VALUE(DATOS_REGISTRO, '$.emailAdmin'))),
            ID_PLAN = JSON_VALUE(DATOS_REGISTRO, '$.idPlan' RETURNING NUMBER)
        WHERE IDENTIFICACION_FISCAL IS NULL AND EMAIL_ADMIN IS NULL AND ID_PLAN IS NULL]';
    EXECUTE IMMEDIATE q'[
        UPDATE ONBOARDING_INTENCIONES
        SET TIPO_FLUJO = CASE WHEN MONTO_CENTAVOS = 0 THEN 'PRUEBA' ELSE 'PAGO' END
        WHERE TIPO_FLUJO IS NULL]';
END;
/

-- 4. Allowed intention states and flow types (legacy rows are not validated)
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(1) INTO v_count FROM USER_CONSTRAINTS
    WHERE TABLE_NAME = 'ONBOARDING_INTENCIONES' AND CONSTRAINT_NAME = 'CK_ONB_INTENCIONES_ESTADO';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE ONBOARDING_INTENCIONES ADD CONSTRAINT CK_ONB_INTENCIONES_ESTADO CHECK '
            || '(ESTADO IN (''PENDIENTE'', ''COMPLETADA'', ''RECHAZADA'', ''PAGO_REQUIERE_REVISION'', ''APROBADA'')) '
            || 'ENABLE NOVALIDATE';
    END IF;

    SELECT COUNT(1) INTO v_count FROM USER_CONSTRAINTS
    WHERE TABLE_NAME = 'ONBOARDING_INTENCIONES' AND CONSTRAINT_NAME = 'CK_ONB_INTENCIONES_TIPO';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE ONBOARDING_INTENCIONES ADD CONSTRAINT CK_ONB_INTENCIONES_TIPO CHECK '
            || '(TIPO_FLUJO IS NULL OR TIPO_FLUJO IN (''PAGO'', ''PRUEBA'', ''GRATIS'')) ENABLE NOVALIDATE';
    END IF;
END;
/

-- 5. Lookup indexes for the once-per-tax-id/e-mail trial rule
DECLARE
    PROCEDURE create_index(p_name IN VARCHAR2, p_ddl IN VARCHAR2) IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(1) INTO v_count FROM USER_INDEXES WHERE INDEX_NAME = p_name;
        IF v_count = 0 THEN
            BEGIN
                EXECUTE IMMEDIATE p_ddl;
            EXCEPTION
                WHEN OTHERS THEN
                    -- ORA-01408: an equivalent column list is already indexed; nothing to add.
                    IF SQLCODE != -1408 THEN
                        RAISE;
                    END IF;
            END;
        END IF;
    END;
BEGIN
    create_index('IX_ONB_INTENCIONES_NIT',
        'CREATE INDEX IX_ONB_INTENCIONES_NIT ON ONBOARDING_INTENCIONES (IDENTIFICACION_FISCAL, TIPO_FLUJO)');
    create_index('IX_ONB_INTENCIONES_EMAIL',
        'CREATE INDEX IX_ONB_INTENCIONES_EMAIL ON ONBOARDING_INTENCIONES (EMAIL_ADMIN, TIPO_FLUJO)');
END;
/
