-- ==============================================================================
-- V5.9: Formalización de Roles y Diferenciación Propietario / Residente / Arrendatario / Conviviente
-- Requisitos #11 y #12 del Plan Maestro SAED 2.0
-- ==============================================================================

-- 1. Asegurar la existencia y vigencia del Rol 'PROPIETARIO' (Alcance UNIDAD)
MERGE INTO ROLES r
USING (
    SELECT 'PROPIETARIO' AS CODIGO, 
           'Propietario No Residente' AS NOMBRE, 
           'UNIDAD' AS ALCANCE, 
           'ACTIVO' AS ESTADO 
    FROM DUAL
) s
ON (r.CODIGO = s.CODIGO)
WHEN NOT MATCHED THEN
    INSERT (CODIGO, NOMBRE, ALCANCE, ESTADO)
    VALUES (s.CODIGO, s.NOMBRE, s.ALCANCE, s.ESTADO);

-- 2. Asegurar que RESIDENTES_UNIDAD soporte los tipos canónicos requeridos
BEGIN
    EXECUTE IMMEDIATE 'ALTER TABLE RESIDENTES_UNIDAD DROP CONSTRAINT CK_RESIDUNIDAD_TIPO';
EXCEPTION
    WHEN OTHERS THEN
        NULL; -- Si no existe o tiene otro nombre, se omite
END;
/

BEGIN
    EXECUTE IMMEDIATE 'ALTER TABLE RESIDENTES_UNIDAD ADD CONSTRAINT CK_RESIDUNIDAD_TIPO CHECK (tipo_residente IN (''PROPIETARIO'', ''ARRENDATARIO'', ''FAMILIAR'', ''CONVIVIENTE'', ''OTRO''))';
EXCEPTION
    WHEN OTHERS THEN
        NULL;
END;
/

COMMENT ON COLUMN RESIDENTES_UNIDAD.TIPO_RESIDENTE IS 'Categoría del habitante físico: PROPIETARIO (residente), ARRENDATARIO, FAMILIAR, CONVIVIENTE, OTRO';
