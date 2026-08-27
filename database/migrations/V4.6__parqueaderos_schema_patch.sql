-- V4.6__parqueaderos_schema_patch.sql
-- Autor: srusso1
-- Descripcion: Elimina la redundancia y posible fuga de informacion entre el catalogo de parqueaderos y sus asignaciones,
-- asegurando el aislamiento Zero-Trust de los residentes a traves de ASIGNACIONES_PARQUEADERO (que ya posee RLS por unidad).

-- 1. Eliminar la Foreign Key redundante
ALTER TABLE "PARQUEADEROS" DROP CONSTRAINT "FK_PARQUEADERO_UNIDAD";

-- 2. Eliminar la columna que compromete la privacidad de las unidades asignadas desde un catalogo general
ALTER TABLE "PARQUEADEROS" DROP COLUMN "ASIGNADO_A_UNIDAD";

-- Nota: POL_RLS_PROP_PARQUEADEROS y POL_RLS_UNI_ASIGNACIONES_PA permanecen intactas.
