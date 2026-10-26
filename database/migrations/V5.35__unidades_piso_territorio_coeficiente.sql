-- =============================================================================
-- SAED 2.0 - V5.35: UNIT FLOOR, PROPERTY TERRITORY, COEFFICIENT RANGE, MISSING CATALOG TYPES
-- =============================================================================
-- F04-01 / F4-H03 / F4-H04 / DEC-04.
--   * PROPIEDADES.DEPARTAMENTO: the country/state/city selector persisted only the city;
--     PAIS already exists (NOT NULL, default 'Colombia').
--   * UNIDADES.PISO: floor of the unit (free text, e.g. '12', 'PH', 'S1').
--   * CK_UNIDADES_COEF_FRACCION: coefficients are fractions 0 < c <= 1. Added ENABLE NOVALIDATE so
--     existing rows are not rejected by the migration; new and updated rows are checked. The
--     property sum <= 1.0000 is enforced by UnitService under a property row lock.
--   * TIPOS_UNIDAD gains PARQUEADERO_PRIVADO and TIPOS_PROPIEDAD gains URBANIZACION_MIXTA
--     (master document types, audit F4-H04).
--
-- Idempotent; runs as a non-exempt owner under active VPD:
--   * columns are added WITHOUT a default (policy-protected tables, ORA-28133);
--   * no MERGE, no blanket exception handlers.

BEGIN
    PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1);
    PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN');
END;
/

DECLARE
    PROCEDURE add_column(p_table IN VARCHAR2, p_column IN VARCHAR2, p_type IN VARCHAR2) IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(1) INTO v_count FROM USER_TAB_COLS
        WHERE TABLE_NAME = p_table AND COLUMN_NAME = p_column;
        IF v_count = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE ' || p_table || ' ADD (' || p_column || ' ' || p_type || ')';
        END IF;
    END;
BEGIN
    add_column('PROPIEDADES', 'DEPARTAMENTO', 'VARCHAR2(80 CHAR)');
    add_column('UNIDADES', 'PISO', 'VARCHAR2(10 CHAR)');
END;
/

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(1) INTO v_count FROM USER_CONSTRAINTS
    WHERE TABLE_NAME = 'UNIDADES' AND CONSTRAINT_NAME = 'CK_UNIDADES_COEF_FRACCION';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE UNIDADES ADD CONSTRAINT CK_UNIDADES_COEF_FRACCION CHECK '
            || '(COEFICIENTE_COPROPIEDAD IS NULL OR (COEFICIENTE_COPROPIEDAD > 0 AND COEFICIENTE_COPROPIEDAD <= 1)) '
            || 'ENABLE NOVALIDATE';
    END IF;
END;
/

-- Catalog check constraints: replace only when the new code is not yet allowed.
DECLARE
    PROCEDURE replace_check(p_table IN VARCHAR2, p_name IN VARCHAR2, p_new_code IN VARCHAR2, p_condition IN VARCHAR2) IS
        v_count NUMBER;
        v_text  VARCHAR2(4000);
    BEGIN
        SELECT COUNT(1) INTO v_count FROM USER_CONSTRAINTS
        WHERE TABLE_NAME = p_table AND CONSTRAINT_NAME = p_name;
        IF v_count > 0 THEN
            SELECT SEARCH_CONDITION_VC INTO v_text FROM USER_CONSTRAINTS
            WHERE TABLE_NAME = p_table AND CONSTRAINT_NAME = p_name;
            IF INSTR(v_text, p_new_code) > 0 THEN
                RETURN;
            END IF;
            EXECUTE IMMEDIATE 'ALTER TABLE ' || p_table || ' DROP CONSTRAINT ' || p_name;
        END IF;
        EXECUTE IMMEDIATE 'ALTER TABLE ' || p_table || ' ADD CONSTRAINT ' || p_name || ' CHECK (' || p_condition || ')';
    END;
BEGIN
    replace_check('TIPOS_UNIDAD', 'CK_TIPOSUNIDAD_CODIGO', 'PARQUEADERO_PRIVADO',
        'codigo IN (''APARTAMENTO'',''CASA'',''LOCAL'',''PARQUEADERO'',''PARQUEADERO_PRIVADO'',''DEPOSITO'',''OFICINA'',''PORTERIA'')');
    replace_check('TIPOS_PROPIEDAD', 'CK_TIPOSPROP_CODIGO', 'URBANIZACION_MIXTA',
        'codigo IN (''EDIFICIO'',''CONJUNTO_CERRADO'',''URBANIZACION_MIXTA'')');
END;
/

-- Catalog rows. Explicit ids continue after the current maximum: the seeded rows used explicit ids,
-- so the identity generator may not have advanced past them.
INSERT INTO TIPOS_UNIDAD (ID_TIPO_UNIDAD, CODIGO, NOMBRE)
SELECT (SELECT NVL(MAX(ID_TIPO_UNIDAD), 0) + 1 FROM TIPOS_UNIDAD), 'PARQUEADERO_PRIVADO', 'Parqueadero privado'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM TIPOS_UNIDAD WHERE CODIGO = 'PARQUEADERO_PRIVADO');

INSERT INTO TIPOS_PROPIEDAD (ID_TIPO_PROPIEDAD, CODIGO, NOMBRE)
SELECT (SELECT NVL(MAX(ID_TIPO_PROPIEDAD), 0) + 1 FROM TIPOS_PROPIEDAD), 'URBANIZACION_MIXTA', 'Urbanización mixta'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM TIPOS_PROPIEDAD WHERE CODIGO = 'URBANIZACION_MIXTA');

COMMIT;
