-- =============================================================================
-- SAED 2.0 - V5.57: PERSISTED VOTING RESULT ON CLOSE
-- =============================================================================
-- Audit F10-12 (DEC-03, DEC-11). Closing a voting now computes the weighted result and the outcome of
-- the required majority (TIPO_MAYORIA_REQUERIDA) and stores them with the closing in one statement.
--   RESULTADO            APROBADA | RECHAZADA (null while the voting is not closed)
--   OPCION_GANADORA      SI | NO | BLANCO | ABSTENCION | EMPATE (null without votes)
--   PESO_SI/NO/BLANCO/ABSTENCION   summed unit coefficient per option
--   PESO_TOTAL_EMITIDO   total weight cast
--   BASE_COEFICIENTES    summed coefficient of the property's active units when the voting was closed
--   PORCENTAJE_SI        SI weight as a percentage of the weight cast
-- The closing timestamp is the existing HORA_CIERRE.
--
-- Idempotent; runs as a non-exempt owner under active VPD on a policy-protected table:
--   * columns are added WITHOUT a default (nothing here needs one);
--   * already closed votings are back-filled with plain UPDATE statements (no MERGE);
--   * no blanket exception handlers.

BEGIN
    PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1);
    PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN');
END;
/

DECLARE
    PROCEDURE add_column(p_column IN VARCHAR2, p_type IN VARCHAR2) IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(1) INTO v_count FROM USER_TAB_COLS
        WHERE TABLE_NAME = 'VOTACIONES' AND COLUMN_NAME = p_column;
        IF v_count = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE VOTACIONES ADD (' || p_column || ' ' || p_type || ')';
        END IF;
    END;
BEGIN
    add_column('RESULTADO', 'VARCHAR2(10 CHAR)');
    add_column('OPCION_GANADORA', 'VARCHAR2(10 CHAR)');
    add_column('PESO_SI', 'NUMBER(12,6)');
    add_column('PESO_NO', 'NUMBER(12,6)');
    add_column('PESO_BLANCO', 'NUMBER(12,6)');
    add_column('PESO_ABSTENCION', 'NUMBER(12,6)');
    add_column('PESO_TOTAL_EMITIDO', 'NUMBER(12,6)');
    add_column('BASE_COEFICIENTES', 'NUMBER(12,6)');
    add_column('PORCENTAJE_SI', 'NUMBER(7,4)');
END;
/

-- Back-fill: tally the weights of votings that were closed before this migration.
UPDATE VOTACIONES v
SET (PESO_SI, PESO_NO, PESO_BLANCO, PESO_ABSTENCION) = (
        SELECT NVL(SUM(CASE WHEN vt.OPCION_VOTO = 'SI' THEN vt.COEFICIENTE_VOTO END), 0),
               NVL(SUM(CASE WHEN vt.OPCION_VOTO = 'NO' THEN vt.COEFICIENTE_VOTO END), 0),
               NVL(SUM(CASE WHEN vt.OPCION_VOTO = 'BLANCO' THEN vt.COEFICIENTE_VOTO END), 0),
               NVL(SUM(CASE WHEN vt.OPCION_VOTO = 'ABSTENCION' THEN vt.COEFICIENTE_VOTO END), 0)
        FROM VOTOS vt WHERE vt.ID_VOTACION = v.ID_VOTACION),
    BASE_COEFICIENTES = (
        SELECT NVL(SUM(u.COEFICIENTE_COPROPIEDAD), 0)
        FROM UNIDADES u JOIN ASAMBLEAS a ON a.ID_PROPIEDAD = u.ID_PROPIEDAD
        WHERE a.ID_ASAMBLEA = v.ID_ASAMBLEA AND u.ESTADO = 'ACTIVA')
WHERE v.ESTADO = 'CERRADA' AND v.RESULTADO IS NULL AND v.PESO_SI IS NULL;

-- Back-fill: outcome with the same rules the application applies (DEC-11).
UPDATE VOTACIONES v
SET PESO_TOTAL_EMITIDO = PESO_SI + PESO_NO + PESO_BLANCO + PESO_ABSTENCION,
    PORCENTAJE_SI = CASE WHEN PESO_SI + PESO_NO + PESO_BLANCO + PESO_ABSTENCION = 0 THEN 0
                         ELSE ROUND(PESO_SI * 100 / (PESO_SI + PESO_NO + PESO_BLANCO + PESO_ABSTENCION), 4) END,
    OPCION_GANADORA = CASE
        WHEN PESO_SI + PESO_NO + PESO_BLANCO + PESO_ABSTENCION = 0 THEN NULL
        WHEN (CASE WHEN PESO_SI = GREATEST(PESO_SI, PESO_NO, PESO_BLANCO, PESO_ABSTENCION) THEN 1 ELSE 0 END)
           + (CASE WHEN PESO_NO = GREATEST(PESO_SI, PESO_NO, PESO_BLANCO, PESO_ABSTENCION) THEN 1 ELSE 0 END)
           + (CASE WHEN PESO_BLANCO = GREATEST(PESO_SI, PESO_NO, PESO_BLANCO, PESO_ABSTENCION) THEN 1 ELSE 0 END)
           + (CASE WHEN PESO_ABSTENCION = GREATEST(PESO_SI, PESO_NO, PESO_BLANCO, PESO_ABSTENCION) THEN 1 ELSE 0 END) > 1 THEN 'EMPATE'
        WHEN PESO_SI = GREATEST(PESO_SI, PESO_NO, PESO_BLANCO, PESO_ABSTENCION) THEN 'SI'
        WHEN PESO_NO = GREATEST(PESO_SI, PESO_NO, PESO_BLANCO, PESO_ABSTENCION) THEN 'NO'
        WHEN PESO_BLANCO = GREATEST(PESO_SI, PESO_NO, PESO_BLANCO, PESO_ABSTENCION) THEN 'BLANCO'
        ELSE 'ABSTENCION' END,
    RESULTADO = CASE
        WHEN PESO_SI + PESO_NO + PESO_BLANCO + PESO_ABSTENCION = 0 THEN 'RECHAZADA'
        WHEN TIPO_MAYORIA_REQUERIDA = 'CALIFICADA_70_PCT'
             AND ROUND(PESO_SI / (PESO_SI + PESO_NO + PESO_BLANCO + PESO_ABSTENCION), 4) >= 0.70 THEN 'APROBADA'
        WHEN TIPO_MAYORIA_REQUERIDA = 'UNANIMIDAD_100_PCT'
             AND PESO_SI = PESO_SI + PESO_NO + PESO_BLANCO + PESO_ABSTENCION THEN 'APROBADA'
        WHEN TIPO_MAYORIA_REQUERIDA = 'SIMPLE_50_MAS_1'
             AND PESO_SI > PESO_NO
             AND ROUND(PESO_SI / (PESO_SI + PESO_NO + PESO_BLANCO + PESO_ABSTENCION), 4) > 0.50 THEN 'APROBADA'
        ELSE 'RECHAZADA' END
WHERE v.ESTADO = 'CERRADA' AND v.RESULTADO IS NULL AND v.PESO_SI IS NOT NULL;

DECLARE
    PROCEDURE add_check(p_name IN VARCHAR2, p_condition IN VARCHAR2) IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(1) INTO v_count FROM USER_CONSTRAINTS
        WHERE TABLE_NAME = 'VOTACIONES' AND CONSTRAINT_NAME = p_name;
        IF v_count = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE VOTACIONES ADD CONSTRAINT ' || p_name || ' CHECK (' || p_condition || ')';
        END IF;
    END;
BEGIN
    add_check('CK_VOTACIONES_RESULTADO', 'RESULTADO IS NULL OR RESULTADO IN (''APROBADA'', ''RECHAZADA'')');
    add_check('CK_VOTACIONES_GANADORA',
        'OPCION_GANADORA IS NULL OR OPCION_GANADORA IN (''SI'', ''NO'', ''BLANCO'', ''ABSTENCION'', ''EMPATE'')');
    add_check('CK_VOTACIONES_RESULT_CERRADA', 'RESULTADO IS NULL OR ESTADO = ''CERRADA''');
    add_check('CK_VOTACIONES_PESOS', 'NVL(PESO_SI, 0) >= 0 AND NVL(PESO_NO, 0) >= 0 AND NVL(PESO_BLANCO, 0) >= 0 '
        || 'AND NVL(PESO_ABSTENCION, 0) >= 0 AND NVL(PESO_TOTAL_EMITIDO, 0) >= 0 AND NVL(BASE_COEFICIENTES, 0) >= 0 '
        || 'AND NVL(PORCENTAJE_SI, 0) BETWEEN 0 AND 100');
END;
/

COMMIT;
