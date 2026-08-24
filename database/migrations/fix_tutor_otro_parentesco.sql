-- ============================================================================
-- MIGRACION DE PRODUCCION: agregar columna OTRO_PARENTESCO a TUTORES
-- Entorno objetivo: Oracle ATP (residencial_high) — PRODUCCION
--
-- Diagnostico (cerrado, A+B+C+D):
--   A) Esquema real ATP: 12 columnas en TUTORES, SIN OTRO_PARENTESCO (verificado).
--   B) DDL que sembro ATP (modelo_relacional_v4_atp.sql): byte-identical con el repo.
--   C) Tamano decidido: VARCHAR2(100 CHAR), nullable.
--   D) ALTER probado en XE local: INSERT/UPDATE/GET validados (4 casos).
--
-- El modelo Java (Tutor.java) y el frontend ya usan otroParentesco
-- (drift Java-only introducido en 38fa2e0/7d0db04 sin migracion SQL).
-- CHK_TUTOR_PARENTESCO ya permite 'OTRO' -> NO se modifica el CHECK.
--
-- CARACTERISTICAS:
--   * Idempotente: si OTRO_PARENTESCO ya existe, no hace nada.
--   * Especifico de TUTORES.
--   * VARCHAR2(100 CHAR), nullable.
--   * Sin DROP, sin UPDATE, sin recreacion de tabla.
--   * Solo DDL ADD + verificacion read-only posterior.
--
-- EJECUCION (tras aprobacion explicita):
--   sqlplus "RESIDENCIAL/***@residencial_high" @fix_tutor_otro_parentesco.sql
--   (con TNS_ADMIN apuntando al wallet de ATP)
--
-- VERIFICACION POST-APLICACION (separada, no incluida aqui):
--   SELECT column_id, column_name, data_type, data_length, char_length, nullable
--     FROM user_tab_columns
--    WHERE table_name = 'TUTORES' ORDER BY column_id;
--   Esperado: OTRO_PARENTESCO | VARCHAR2 | 400 | 100 | Y
-- ============================================================================

SET SERVEROUTPUT ON

-- 1) ALTER idempotente con guard de existencia de tabla + columna
DECLARE
    v_table_count  NUMBER;
    v_column_count NUMBER;
BEGIN
    -- Barrera de seguridad: confirmar que estamos en el esquema correcto
    SELECT COUNT(*)
      INTO v_table_count
      FROM user_tables
     WHERE table_name = 'TUTORES';

    IF v_table_count = 0 THEN
        RAISE_APPLICATION_ERROR(
            -20001,
            'La tabla TUTORES no existe en el esquema actual'
        );
    END IF;

    SELECT COUNT(*)
      INTO v_column_count
      FROM user_tab_columns
     WHERE table_name  = 'TUTORES'
       AND column_name = 'OTRO_PARENTESCO';

    IF v_column_count = 0 THEN
        EXECUTE IMMEDIATE
            'ALTER TABLE TUTORES ADD (otro_parentesco VARCHAR2(100 CHAR))';

        DBMS_OUTPUT.PUT_LINE(
            '[OK] TUTORES.OTRO_PARENTESCO agregada (VARCHAR2(100 CHAR), nullable)'
        );
    ELSE
        DBMS_OUTPUT.PUT_LINE(
            '[SKIP] TUTORES.OTRO_PARENTESCO ya existe; sin cambios'
        );
    END IF;
END;
/

-- 2) Verificacion de la columna (read-only)
SELECT
    column_name,
    data_type,
    data_length,
    char_length,
    nullable
FROM user_tab_columns
WHERE table_name = 'TUTORES'
  AND column_name = 'OTRO_PARENTESCO';

-- ============================================================================
-- RESULTADO ESPERADO (primera ejecucion):
--   [OK] TUTORES.OTRO_PARENTESCO agregada (VARCHAR2(100 CHAR), nullable)
--   OTRO_PARENTESCO | VARCHAR2 | 400 | 100 | Y
--
-- RESULTADO ESPERADO (re-ejecucion, idempotencia):
--   [SKIP] TUTORES.OTRO_PARENTESCO ya existe; sin cambios
--   OTRO_PARENTESCO | VARCHAR2 | 400 | 100 | Y
--
-- PASOS POSTERIORES (aprobados por el usuario, fuera de este script):
--   1. Smoke test 16-17 en ATP: OTRO+texto, OTRO+NULL, parentesco normal,
--      UPDATE de otroParentesco, GET/lectura, adulto <16/>=18 sin tutor.
--   2. Sincronizar los 3 DDL canonicos (script_oracle.sql,
--      modelo_relacional_v4.sql, modelo_relacional_v4_atp.sql) para que
--      el drift no reaparezca en futuras reconstrucciones de BD.
-- ============================================================================
