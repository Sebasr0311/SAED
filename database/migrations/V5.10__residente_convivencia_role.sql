-- ==============================================================================
-- V5.10: Formalización del Rol RESIDENTE_CONVIVENCIA (Alcance UNIDAD)
-- Corrección de Hallazgo Auditoría 13-A: Acceso independiente conviviente
-- ==============================================================================

MERGE INTO ROLES r
USING (
    SELECT 'RESIDENTE_CONVIVENCIA' AS CODIGO,
           'Residente Conviviente' AS NOMBRE,
           'UNIDAD' AS ALCANCE,
           'ACTIVO' AS ESTADO
    FROM DUAL
) s
ON (r.CODIGO = s.CODIGO)
WHEN NOT MATCHED THEN
    INSERT (CODIGO, NOMBRE, ALCANCE, ESTADO)
    VALUES (s.CODIGO, s.NOMBRE, s.ALCANCE, s.ESTADO);

COMMIT;
