-- Parqueaderos rotativos para visitantes
-- 15 de carro (VEHICULO) + 15 de moto (MOTO) = 30 espacios
-- Los primeros ya existen en datos_prueba.sql (VIS-01..04, MV-01..02)

-- Completar 15 carros: VIS-05 a VIS-15 (faltan 11)
INSERT INTO PARQUEADEROS (codigo, tipo, estado, es_visitante, id_apartamento)
SELECT 'VIS-' || LPAD(LEVEL + 4, 2, '0'), 'VEHICULO', 'DISPONIBLE', 1, NULL
FROM DUAL CONNECT BY LEVEL <= 11;

-- Completar 15 motos: MV-03 a MV-15 (faltan 13)
INSERT INTO PARQUEADEROS (codigo, tipo, estado, es_visitante, id_apartamento)
SELECT 'MV-' || LPAD(LEVEL + 2, 2, '0'), 'MOTO', 'DISPONIBLE', 1, NULL
FROM DUAL CONNECT BY LEVEL <= 13;

COMMIT;
