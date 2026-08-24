SET SQLBLANKLINES ON
SET DEFINE OFF

-- ══════════════════════════════════════════════════════════════════
-- fix_modelo_v5_4.sql  —  Migración v5.3 → v5.4
-- Proyecto : Sistema de Administración de Edificios (SAED)
-- Fecha    : 2026-05-27
-- Ejecutar : sqlplus RESIDENCIAL/Residencial2024#@localhost/xepdb1
-- ══════════════════════════════════════════════════════════════════
--
-- CAMBIOS:
--   · ADD COLUMN MULTAS.foto_evidencia CLOB
--       (fue eliminada incorrectamente en v5.3; se restaura en v5.4)
--   · RESIDENTES: nombres VARCHAR2(100→25), apellidos VARCHAR2(100→25)
--                 email   VARCHAR2(254→40)
--   · TUTORES:    nombres VARCHAR2(100→25), apellidos VARCHAR2(100→25)
--                 email   VARCHAR2(254→40)
--   · VISITANTES: nombres VARCHAR2(100→25), apellidos VARCHAR2(100→25)
--                 email   VARCHAR2(254→40)
--   · VEHICULOS_VISITA: marca VARCHAR2(100→15), color VARCHAR2(60→15)
--
-- IDEMPOTENCIA:
--   - La restitución de foto_evidencia usa un bloque PL/SQL que omite
--     ORA-01430 si la columna ya existe.
--   - Los MODIFY son seguros si los datos existentes caben en el nuevo
--     tamaño (verificado: max nombres=16, max email=27, max marca=6).
-- ══════════════════════════════════════════════════════════════════

-- §1  Restaurar foto_evidencia en MULTAS
BEGIN
    EXECUTE IMMEDIATE 'ALTER TABLE MULTAS ADD (foto_evidencia CLOB)';
    DBMS_OUTPUT.PUT_LINE('OK: MULTAS.foto_evidencia restaurada');
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE = -01430 THEN
            DBMS_OUTPUT.PUT_LINE('SKIP: MULTAS.foto_evidencia ya existe');
        ELSE
            RAISE;
        END IF;
END;
/

-- §2  Reducir VARCHAR2 en RESIDENTES
ALTER TABLE RESIDENTES MODIFY (nombres   VARCHAR2(25));
ALTER TABLE RESIDENTES MODIFY (apellidos VARCHAR2(25));
ALTER TABLE RESIDENTES MODIFY (email     VARCHAR2(40));

-- §3  Reducir VARCHAR2 en TUTORES
ALTER TABLE TUTORES MODIFY (nombres   VARCHAR2(25));
ALTER TABLE TUTORES MODIFY (apellidos VARCHAR2(25));
ALTER TABLE TUTORES MODIFY (email     VARCHAR2(40));

-- §4  Reducir VARCHAR2 en VISITANTES
ALTER TABLE VISITANTES MODIFY (nombres   VARCHAR2(25));
ALTER TABLE VISITANTES MODIFY (apellidos VARCHAR2(25));
ALTER TABLE VISITANTES MODIFY (email     VARCHAR2(40));

-- §5  Reducir VARCHAR2 en VEHICULOS_VISITA
ALTER TABLE VEHICULOS_VISITA MODIFY (marca VARCHAR2(15));
ALTER TABLE VEHICULOS_VISITA MODIFY (color VARCHAR2(15));

-- §6  Verificación final
SELECT 'MULTAS'          AS tabla, COUNT(*) AS columnas FROM user_tab_columns WHERE table_name = 'MULTAS'          UNION ALL
SELECT 'RESIDENTES'      AS tabla, COUNT(*) AS columnas FROM user_tab_columns WHERE table_name = 'RESIDENTES'      UNION ALL
SELECT 'TUTORES'         AS tabla, COUNT(*) AS columnas FROM user_tab_columns WHERE table_name = 'TUTORES'         UNION ALL
SELECT 'VISITANTES'      AS tabla, COUNT(*) AS columnas FROM user_tab_columns WHERE table_name = 'VISITANTES'      UNION ALL
SELECT 'VEHICULOS_VISITA'AS tabla, COUNT(*) AS columnas FROM user_tab_columns WHERE table_name = 'VEHICULOS_VISITA';

SELECT column_name, data_type, data_length, nullable
FROM   user_tab_columns
WHERE  table_name IN ('MULTAS','RESIDENTES','TUTORES','VISITANTES','VEHICULOS_VISITA')
  AND  column_name IN ('FOTO_EVIDENCIA','NOMBRES','APELLIDOS','EMAIL','MARCA','COLOR')
ORDER  BY table_name, column_name;

COMMIT;

-- FIN fix_modelo_v5_4.sql
