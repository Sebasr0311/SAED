-- Fix ORA-02290 CHK_VEH_HORAS: cambiar DEFAULT SYSTIMESTAMP a CURRENT_TIMESTAMP
-- para que hora_entrada en VEHICULOS_VISITA se almacene en zona horaria de sesion (America/Bogota)
-- y la comparacion hora_salida > hora_entrada del trigger TRG_ACCESO_SALIDA funcione correctamente.
--
-- Ademas: fix existente en ATP - DEFAULT SYSTIMESTAMP almacena UTC sin conversion a session TZ,
-- haciendo que hora_salida (CURRENT_TIMESTAMP en Bogota UTC-5) sea MENOR que hora_entrada (UTC).
-- Se necesita:
--   1. Cambiar DEFAULT para nuevos INSERTs (ALTER TABLE)
--   2. Corregir filas existentes restando 5h (UTC → Bogota)
--   3. Recompilar trigger para usar CURRENT_TIMESTAMP (ya deberia estar)

-- Paso 1: Cambiar DEFAULT para nuevas filas
ALTER TABLE VEHICULOS_VISITA MODIFY (hora_entrada DEFAULT CURRENT_TIMESTAMP);
ALTER TABLE REGISTROS_ACCESO MODIFY (hora_entrada DEFAULT CURRENT_TIMESTAMP);
ALTER TABLE REGISTROS_ACCESO MODIFY (fecha_registro DEFAULT CURRENT_TIMESTAMP);

-- Paso 2: Corregir filas existentes que tienen UTC en lugar de Bogota
-- Detectamos UTC: hora_entrada > CURRENT_TIMESTAMP (porque UTC=+0 vs Bogota=-5)
UPDATE VEHICULOS_VISITA
   SET hora_entrada = hora_entrada - INTERVAL '5' HOUR
 WHERE hora_entrada > CURRENT_TIMESTAMP;

-- Paso 3: Verificar que no queden filas con hora_entrada > hora_salida (potenciales violaciones)
SELECT COUNT(*) AS filas_con_violacion_potencial
FROM   VEHICULOS_VISITA
WHERE  hora_salida IS NOT NULL
  AND  hora_entrada >= hora_salida;

-- Paso 4: Recompilar trigger
ALTER TRIGGER TRG_ACCESO_SALIDA COMPILE;

-- Verificar cambios
SELECT table_name, column_name, data_default, data_type
FROM   user_tab_columns
WHERE  table_name IN ('VEHICULOS_VISITA', 'REGISTROS_ACCESO')
  AND  column_name IN ('HORA_ENTRADA', 'FECHA_REGISTRO')
ORDER BY table_name, column_name;
