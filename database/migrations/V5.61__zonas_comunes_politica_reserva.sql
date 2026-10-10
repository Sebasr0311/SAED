-- =============================================================================
-- SAED 2.0 - V5.61: COMMON-AREA BOOKING POLICY
-- =============================================================================
-- F08-01. Per-zone booking rules enforced server-side by ReservasServiceImpl:
--   HORA_APERTURA / HORA_CIERRE     operating window (HH:MM)          default 06:00-22:00
--   ANTICIPACION_MIN_HORAS          minimum advance in hours          default 2
--   ANTICIPACION_MAX_DIAS           maximum advance in days           default 60
--   MAX_RESERVAS_ACTIVAS_UNIDAD     active reservations per unit      default 2
--   VALOR_DEPOSITO                  deposit required before approval  default 0 (none)
--   HORAS_LIMITE_CANCELACION        resident cancellation window      default 24
-- Defaults are conservative and editable by the property administrator.
--
-- Idempotent; runs as a non-exempt owner under active VPD:
--   * columns are added WITHOUT a default: on this policy-protected table even a constant default
--     forces a rewrite that active VPD rejects (ORA-28133, verified on the isolated install);
--     defaults are then set with MODIFY (future rows) and existing rows are filled by UPDATE;
--   * no blanket exception handlers.

BEGIN
    PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1);
    PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN');
END;
/

DECLARE
    PROCEDURE add_column(p_column IN VARCHAR2, p_type IN VARCHAR2, p_default IN VARCHAR2) IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(1) INTO v_count FROM USER_TAB_COLS
        WHERE TABLE_NAME = 'ZONAS_COMUNES' AND COLUMN_NAME = p_column;
        IF v_count = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE ZONAS_COMUNES ADD (' || p_column || ' ' || p_type || ')';
        END IF;
        -- Default for future inserts only (metadata change, no table rewrite).
        EXECUTE IMMEDIATE 'ALTER TABLE ZONAS_COMUNES MODIFY (' || p_column || ' DEFAULT ' || p_default || ')';
        -- Existing zones get the same conservative value.
        EXECUTE IMMEDIATE 'UPDATE ZONAS_COMUNES SET ' || p_column || ' = ' || p_default || ' WHERE ' || p_column || ' IS NULL';
    END;
BEGIN
    add_column('HORA_APERTURA', 'VARCHAR2(5 CHAR)', '''06:00''');
    add_column('HORA_CIERRE', 'VARCHAR2(5 CHAR)', '''22:00''');
    add_column('ANTICIPACION_MIN_HORAS', 'NUMBER(5)', '2');
    add_column('ANTICIPACION_MAX_DIAS', 'NUMBER(5)', '60');
    add_column('MAX_RESERVAS_ACTIVAS_UNIDAD', 'NUMBER(5)', '2');
    add_column('VALOR_DEPOSITO', 'NUMBER(12,2)', '0');
    add_column('HORAS_LIMITE_CANCELACION', 'NUMBER(5)', '24');
END;
/

DECLARE
    PROCEDURE add_check(p_name IN VARCHAR2, p_condition IN VARCHAR2) IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(1) INTO v_count FROM USER_CONSTRAINTS
        WHERE TABLE_NAME = 'ZONAS_COMUNES' AND CONSTRAINT_NAME = p_name;
        IF v_count = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE ZONAS_COMUNES ADD CONSTRAINT ' || p_name || ' CHECK (' || p_condition || ')';
        END IF;
    END;
BEGIN
    add_check('CK_ZONA_HORARIO', 'HORA_APERTURA IS NULL OR HORA_CIERRE IS NULL OR HORA_APERTURA < HORA_CIERRE');
    add_check('CK_ZONA_POLITICA', 'NVL(ANTICIPACION_MIN_HORAS, 0) >= 0 AND NVL(ANTICIPACION_MAX_DIAS, 1) > 0 '
        || 'AND NVL(MAX_RESERVAS_ACTIVAS_UNIDAD, 1) > 0 AND NVL(VALOR_DEPOSITO, 0) >= 0 '
        || 'AND NVL(HORAS_LIMITE_CANCELACION, 0) >= 0');
END;
/

COMMIT;
