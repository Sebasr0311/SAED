-- Validaciones de apartamentos y residentes (2026-08)
-- 1) Descripcion del tipo cuando el apartamento es OTRO (local, bodega, sotano...)
ALTER TABLE APARTAMENTOS ADD (descripcion_tipo VARCHAR2(100));
