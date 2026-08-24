SET SQLBLANKLINES ON
SET DEFINE OFF

-- ══════════════════════════════════════════════════════════════════
-- fix_modelo_v5_3.sql  —  Migración v5.2 → v5.3
-- Proyecto : Sistema de Administración de Edificios (SAED)
-- Fecha    : 2026-05-27
-- Ejecutar : sqlplus RESIDENCIAL/Residencial2024#@localhost/xepdb1
-- ══════════════════════════════════════════════════════════════════
--
-- CAMBIOS:
--   · DROP COLUMN MULTAS.comprobante_url  (VARCHAR2 — URL soporte de pago)
--
-- RAZÓN: Las multas no requieren adjuntar URL de comprobante.
--        El flujo de pago solo registra método de pago y usuario
--        que lo procesó. foto_evidencia (CLOB) se conserva para
--        adjuntar foto del vehículo mal estacionado (PARQUEADERO).
--
-- IDEMPOTENCIA: El ALTER TABLE está envuelto en un bloque PL/SQL que
--               verifica si la columna aún existe antes de intentar
--               eliminarla, evitando error ORA-00904 si ya fue borrada.
-- ══════════════════════════════════════════════════════════════════

-- §1  Eliminar comprobante_url de MULTAS
BEGIN
    EXECUTE IMMEDIATE 'ALTER TABLE MULTAS DROP COLUMN comprobante_url';
    DBMS_OUTPUT.PUT_LINE('OK: MULTAS.comprobante_url eliminada');
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE = -00904 THEN
            DBMS_OUTPUT.PUT_LINE('SKIP: MULTAS.comprobante_url ya no existe');
        ELSE
            RAISE;
        END IF;
END;
/

-- §2  Verificación final — debe mostrar 13 columnas para MULTAS
SELECT column_name, data_type, nullable
FROM   user_tab_columns
WHERE  table_name = 'MULTAS'
ORDER  BY column_id;

COMMIT;

-- FIN fix_modelo_v5_3.sql
