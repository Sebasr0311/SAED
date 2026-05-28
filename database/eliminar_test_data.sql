-- Buscar residentes con 'test' en el nombre
SELECT id_residente, nombres, apellidos, numero_documento
FROM RESIDENTES
WHERE LOWER(nombres || ' ' || apellidos) LIKE '%test%';

-- Eliminar (reemplazar <ID> con el id_residente real):
-- DELETE FROM RESIDENTES WHERE id_residente = <ID>;
