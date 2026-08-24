-- =============================================================================
-- SAED — SISTEMA DE ADMINISTRACIÓN RESIDENCIAL
-- Datos de prueba v2 — CORREGIDO para el esquema real
-- Oracle 18c XE
-- =============================================================================

-- =============================================================================
-- 1. TIPOS_DOCUMENTO
-- =============================================================================
MERGE INTO TIPOS_DOCUMENTO t
USING (SELECT 1 id, 'CC' cod, 'Cédula de Ciudadanía' descr, 1 act FROM DUAL
       UNION ALL SELECT 2, 'CE',    'Cédula de Extranjería',   1 FROM DUAL
       UNION ALL SELECT 3, 'NIT',   'NIT',                     1 FROM DUAL
       UNION ALL SELECT 4, 'PASAPORTE', 'Pasaporte',           1 FROM DUAL
       UNION ALL SELECT 5, 'TI',    'Tarjeta de Identidad',    1 FROM DUAL) s
ON (t.id_tipo_doc = s.id)
WHEN NOT MATCHED THEN
  INSERT (id_tipo_doc, codigo, descripcion, activo)
  VALUES (s.id, s.cod, s.descr, s.act);
COMMIT;

-- =============================================================================
-- 2. APARTAMENTOS
-- =============================================================================
INSERT INTO APARTAMENTOS (id_apartamento, numero, piso, tipo, area_m2, capacidad_maxima, estado, activo, fecha_registro, actualizado_en)
VALUES (SEC_APARTAMENTOS.NEXTVAL, '101', 1, 'ESTUDIO',  42.5,  2, 'DISPONIBLE', 1, SYSTIMESTAMP, SYSTIMESTAMP);
INSERT INTO APARTAMENTOS (id_apartamento, numero, piso, tipo, area_m2, capacidad_maxima, estado, activo, fecha_registro, actualizado_en)
VALUES (SEC_APARTAMENTOS.NEXTVAL, '201', 2, '1_HAB',    58.0,  3, 'OCUPADO', 1, SYSTIMESTAMP, SYSTIMESTAMP);
INSERT INTO APARTAMENTOS (id_apartamento, numero, piso, tipo, area_m2, capacidad_maxima, estado, activo, fecha_registro, actualizado_en)
VALUES (SEC_APARTAMENTOS.NEXTVAL, '202', 2, '2_HAB',    75.0,  4, 'OCUPADO', 1, SYSTIMESTAMP, SYSTIMESTAMP);
INSERT INTO APARTAMENTOS (id_apartamento, numero, piso, tipo, area_m2, capacidad_maxima, estado, activo, fecha_registro, actualizado_en)
VALUES (SEC_APARTAMENTOS.NEXTVAL, '301', 3, '3_HAB',    95.0,  5, 'OCUPADO', 1, SYSTIMESTAMP, SYSTIMESTAMP);
INSERT INTO APARTAMENTOS (id_apartamento, numero, piso, tipo, area_m2, capacidad_maxima, estado, activo, fecha_registro, actualizado_en)
VALUES (SEC_APARTAMENTOS.NEXTVAL, '401', 4, 'PENTHOUSE',140.0, 6, 'EN_MANTENIMIENTO', 1, SYSTIMESTAMP, SYSTIMESTAMP);
COMMIT;

-- =============================================================================
-- 3. PARQUEADEROS
-- =============================================================================
INSERT INTO PARQUEADEROS (id_parqueadero, codigo, tipo, es_visitante, estado, id_apartamento, fecha_registro, actualizado_en)
VALUES (SEC_PARQUEADEROS.NEXTVAL, 'P-01', 'VEHICULO', 0, 'OCUPADO',
        (SELECT id_apartamento FROM APARTAMENTOS WHERE numero='201'), SYSTIMESTAMP, SYSTIMESTAMP);
INSERT INTO PARQUEADEROS (id_parqueadero, codigo, tipo, es_visitante, estado, id_apartamento, fecha_registro, actualizado_en)
VALUES (SEC_PARQUEADEROS.NEXTVAL, 'P-02', 'VEHICULO', 0, 'OCUPADO',
        (SELECT id_apartamento FROM APARTAMENTOS WHERE numero='202'), SYSTIMESTAMP, SYSTIMESTAMP);
INSERT INTO PARQUEADEROS (id_parqueadero, codigo, tipo, es_visitante, estado, id_apartamento, fecha_registro, actualizado_en)
VALUES (SEC_PARQUEADEROS.NEXTVAL, 'P-03', 'MOTO', 0, 'DISPONIBLE',
        (SELECT id_apartamento FROM APARTAMENTOS WHERE numero='301'), SYSTIMESTAMP, SYSTIMESTAMP);
INSERT INTO PARQUEADEROS (id_parqueadero, codigo, tipo, es_visitante, estado, id_apartamento, fecha_registro, actualizado_en)
VALUES (SEC_PARQUEADEROS.NEXTVAL, 'V-01', 'VEHICULO', 1, 'DISPONIBLE', NULL, SYSTIMESTAMP, SYSTIMESTAMP);
INSERT INTO PARQUEADEROS (id_parqueadero, codigo, tipo, es_visitante, estado, id_apartamento, fecha_registro, actualizado_en)
VALUES (SEC_PARQUEADEROS.NEXTVAL, 'V-02', 'VEHICULO', 1, 'OCUPADO', NULL, SYSTIMESTAMP, SYSTIMESTAMP);
INSERT INTO PARQUEADEROS (id_parqueadero, codigo, tipo, es_visitante, estado, id_apartamento, fecha_registro, actualizado_en)
VALUES (SEC_PARQUEADEROS.NEXTVAL, 'B-01', 'BICICLETA', 1, 'EN_MANTENIMIENTO', NULL, SYSTIMESTAMP, SYSTIMESTAMP);
COMMIT;

-- =============================================================================
-- 4. RESIDENTES
-- =============================================================================
INSERT INTO RESIDENTES (id_residente, id_tipo_doc, numero_documento, nombres, apellidos, fecha_nacimiento, telefono, email, foto_url, fecha_ingreso, es_menor_edad, activo, fecha_registro, actualizado_en)
VALUES (SEC_RESIDENTES.NEXTVAL, 1, '10456789', 'Carlos Andrés', 'Pérez Ramos', DATE '1990-03-15', '3001234567', 'carlos.perez@gmail.com', 'https://saed.storage/fotos/r1.jpg', DATE '2024-01-01', 0, 1, SYSTIMESTAMP, SYSTIMESTAMP);
INSERT INTO RESIDENTES (id_residente, id_tipo_doc, numero_documento, nombres, apellidos, fecha_nacimiento, telefono, email, foto_url, fecha_ingreso, es_menor_edad, activo, fecha_registro, actualizado_en)
VALUES (SEC_RESIDENTES.NEXTVAL, 1, '52678901', 'María Fernanda', 'Ruiz Torres', DATE '1993-07-22', '3109876543', 'maria.ruiz@hotmail.com', 'https://saed.storage/fotos/r2.jpg', DATE '2024-01-01', 0, 1, SYSTIMESTAMP, SYSTIMESTAMP);
INSERT INTO RESIDENTES (id_residente, id_tipo_doc, numero_documento, nombres, apellidos, fecha_nacimiento, telefono, email, foto_url, fecha_ingreso, es_menor_edad, activo, fecha_registro, actualizado_en)
VALUES (SEC_RESIDENTES.NEXTVAL, 1, '80234567', 'Jhon Sebastián', 'Martínez López', DATE '1985-11-08', '3156781234', 'jhon.martinez@outlook.com', 'https://saed.storage/fotos/r3.jpg', DATE '2023-06-01', 0, 1, SYSTIMESTAMP, SYSTIMESTAMP);
INSERT INTO RESIDENTES (id_residente, id_tipo_doc, numero_documento, nombres, apellidos, fecha_nacimiento, telefono, email, foto_url, fecha_ingreso, es_menor_edad, activo, fecha_registro, actualizado_en)
VALUES (SEC_RESIDENTES.NEXTVAL, 1, '43567890', 'Luisa Valentina', 'Gómez Ríos', DATE '1991-05-30', '3204567890', 'luisa.gomez@gmail.com', NULL, DATE '2023-06-01', 0, 1, SYSTIMESTAMP, SYSTIMESTAMP);
INSERT INTO RESIDENTES (id_residente, id_tipo_doc, numero_documento, nombres, apellidos, fecha_nacimiento, telefono, email, foto_url, fecha_ingreso, es_menor_edad, activo, fecha_registro, actualizado_en)
VALUES (SEC_RESIDENTES.NEXTVAL, 1, '71890123', 'Pedro Antonio', 'Soto Vargas', DATE '1978-02-14', '3012345678', 'pedro.soto@empresa.com', NULL, DATE '2022-03-01', 0, 1, SYSTIMESTAMP, SYSTIMESTAMP);
INSERT INTO RESIDENTES (id_residente, id_tipo_doc, numero_documento, nombres, apellidos, fecha_nacimiento, telefono, email, foto_url, fecha_ingreso, es_menor_edad, activo, fecha_registro, actualizado_en)
VALUES (SEC_RESIDENTES.NEXTVAL, 1, '36789012', 'Ana Lucía', 'Morales Peña', DATE '1970-09-25', '3175678901', 'ana.morales@correo.co', NULL, DATE '2024-01-01', 0, 1, SYSTIMESTAMP, SYSTIMESTAMP);
-- Menor de edad
INSERT INTO RESIDENTES (id_residente, id_tipo_doc, numero_documento, nombres, apellidos, fecha_nacimiento, telefono, email, fecha_ingreso, es_menor_edad, activo, fecha_registro, actualizado_en)
VALUES (SEC_RESIDENTES.NEXTVAL, 5, '1234567890', 'Miguel Ángel', 'Torres Soto', DATE '2010-06-12', NULL, NULL, DATE '2022-03-01', 1, 1, SYSTIMESTAMP, SYSTIMESTAMP);
COMMIT;

-- =============================================================================
-- 5. USUARIOS
-- =============================================================================
INSERT INTO USUARIOS (id_usuario, id_residente, username, password_hash, rol, activo, ultimo_login, fecha_registro, actualizado_en)
VALUES (SEC_USUARIOS.NEXTVAL, NULL, 'admin_saed', '$2b$12$eImiTXuWVxfM37uY4JANjOeH/y3bFMPqV5oRqF2LYqHBH0X3hZBfO', 'ADMINISTRADOR', 1, SYSTIMESTAMP - INTERVAL '2' HOUR, SYSTIMESTAMP, SYSTIMESTAMP);
INSERT INTO USUARIOS (id_usuario, id_residente, username, password_hash, rol, activo, ultimo_login, fecha_registro, actualizado_en)
VALUES (SEC_USUARIOS.NEXTVAL, NULL, 'portero_dia',  '$2b$12$abcXYZ123portero456hashDIA789xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx', 'PORTERO', 1, SYSTIMESTAMP - INTERVAL '6' HOUR, SYSTIMESTAMP, SYSTIMESTAMP);
INSERT INTO USUARIOS (id_usuario, id_residente, username, password_hash, rol, activo, ultimo_login, fecha_registro, actualizado_en)
VALUES (SEC_USUARIOS.NEXTVAL, NULL, 'portero_noche','$2b$12$abcXYZ123portero456hashNOCHE789xxxxxxxxxxxxxxxxxxxxxxxxxxxxxx', 'PORTERO', 1, SYSTIMESTAMP - INTERVAL '14' HOUR, SYSTIMESTAMP, SYSTIMESTAMP);
INSERT INTO USUARIOS (id_usuario, id_residente, username, password_hash, rol, activo, ultimo_login, fecha_registro, actualizado_en)
VALUES (SEC_USUARIOS.NEXTVAL, (SELECT id_residente FROM RESIDENTES WHERE numero_documento='10456789'), 'carlos.perez', '$2b$12$abcXYZ123residente456hashR1xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx', 'RESIDENTE', 1, SYSTIMESTAMP - INTERVAL '1' DAY, SYSTIMESTAMP, SYSTIMESTAMP);
INSERT INTO USUARIOS (id_usuario, id_residente, username, password_hash, rol, activo, ultimo_login, fecha_registro, actualizado_en)
VALUES (SEC_USUARIOS.NEXTVAL, (SELECT id_residente FROM RESIDENTES WHERE numero_documento='80234567'), 'jhon.martinez','$2b$12$abcXYZ123residente456hashR3xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx', 'RESIDENTE', 1, SYSTIMESTAMP - INTERVAL '3' DAY, SYSTIMESTAMP, SYSTIMESTAMP);
COMMIT;

-- =============================================================================
-- 6. CONTRATOS
-- =============================================================================
-- C1: AP-201, ACTIVO, dia_pago=5
INSERT INTO CONTRATOS (id_contrato, id_apartamento, id_registrado_por, fecha_inicio, fecha_fin, valor_mensual, dia_pago, dias_gracia, porcentaje_mora, estado, observaciones, fecha_registro, actualizado_en)
VALUES (SEC_CONTRATOS.NEXTVAL,
        (SELECT id_apartamento FROM APARTAMENTOS WHERE numero='201'),
        (SELECT id_usuario FROM USUARIOS WHERE username='admin_saed'),
        DATE '2024-01-01', NULL, 1200000, 5, 5, 1.5, 'ACTIVO', 'Canon $1.200.000', SYSTIMESTAMP, SYSTIMESTAMP);

-- C2: AP-202, ACTIVO, dia_pago=10
INSERT INTO CONTRATOS (id_contrato, id_apartamento, id_registrado_por, fecha_inicio, fecha_fin, valor_mensual, dia_pago, dias_gracia, porcentaje_mora, estado, observaciones, fecha_registro, actualizado_en)
VALUES (SEC_CONTRATOS.NEXTVAL,
        (SELECT id_apartamento FROM APARTAMENTOS WHERE numero='202'),
        (SELECT id_usuario FROM USUARIOS WHERE username='admin_saed'),
        DATE '2023-06-01', NULL, 1800000, 10, 3, 2.0, 'ACTIVO', 'Canon $1.800.000, mora 2%', SYSTIMESTAMP, SYSTIMESTAMP);

-- C3: AP-301, FINALIZADO
INSERT INTO CONTRATOS (id_contrato, id_apartamento, id_registrado_por, fecha_inicio, fecha_fin, valor_mensual, dia_pago, dias_gracia, porcentaje_mora, estado, observaciones, fecha_registro, actualizado_en)
VALUES (SEC_CONTRATOS.NEXTVAL,
        (SELECT id_apartamento FROM APARTAMENTOS WHERE numero='301'),
        (SELECT id_usuario FROM USUARIOS WHERE username='admin_saed'),
        DATE '2022-03-01', DATE '2024-02-28', 2200000, 5, 5, 1.5, 'FINALIZADO', 'Contrato finalizado', SYSTIMESTAMP, SYSTIMESTAMP);
COMMIT;

-- =============================================================================
-- 7. CONTRATO_RESIDENTE
--    Roles permitidos: ARRENDATARIO, CODEUDOR, RESIDENTE_MENOR, OTRO
-- =============================================================================
-- C1: Carlos (ARRENDATARIO), María (OTRO), Ana (CODEUDOR)
INSERT INTO CONTRATO_RESIDENTE (id_contrato_res, id_contrato, id_residente, rol_en_contrato)
VALUES (SEC_CONTRATO_RESIDENTE.NEXTVAL,
        (SELECT id_contrato FROM CONTRATOS WHERE id_apartamento=(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='201') AND estado='ACTIVO'),
        (SELECT id_residente FROM RESIDENTES WHERE numero_documento='10456789'), 'ARRENDATARIO');

INSERT INTO CONTRATO_RESIDENTE (id_contrato_res, id_contrato, id_residente, rol_en_contrato)
VALUES (SEC_CONTRATO_RESIDENTE.NEXTVAL,
        (SELECT id_contrato FROM CONTRATOS WHERE id_apartamento=(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='201') AND estado='ACTIVO'),
        (SELECT id_residente FROM RESIDENTES WHERE numero_documento='52678901'), 'OTRO');

INSERT INTO CONTRATO_RESIDENTE (id_contrato_res, id_contrato, id_residente, rol_en_contrato)
VALUES (SEC_CONTRATO_RESIDENTE.NEXTVAL,
        (SELECT id_contrato FROM CONTRATOS WHERE id_apartamento=(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='201') AND estado='ACTIVO'),
        (SELECT id_residente FROM RESIDENTES WHERE numero_documento='36789012'), 'CODEUDOR');

-- C2: Jhon (ARRENDATARIO), Luisa (OTRO)
INSERT INTO CONTRATO_RESIDENTE (id_contrato_res, id_contrato, id_residente, rol_en_contrato)
VALUES (SEC_CONTRATO_RESIDENTE.NEXTVAL,
        (SELECT id_contrato FROM CONTRATOS WHERE id_apartamento=(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='202') AND estado='ACTIVO'),
        (SELECT id_residente FROM RESIDENTES WHERE numero_documento='80234567'), 'ARRENDATARIO');

INSERT INTO CONTRATO_RESIDENTE (id_contrato_res, id_contrato, id_residente, rol_en_contrato)
VALUES (SEC_CONTRATO_RESIDENTE.NEXTVAL,
        (SELECT id_contrato FROM CONTRATOS WHERE id_apartamento=(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='202') AND estado='ACTIVO'),
        (SELECT id_residente FROM RESIDENTES WHERE numero_documento='43567890'), 'OTRO');

-- C3: Pedro (ARRENDATARIO), Miguel (RESIDENTE_MENOR)
INSERT INTO CONTRATO_RESIDENTE (id_contrato_res, id_contrato, id_residente, rol_en_contrato)
VALUES (SEC_CONTRATO_RESIDENTE.NEXTVAL,
        (SELECT id_contrato FROM CONTRATOS WHERE id_apartamento=(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='301') AND estado='FINALIZADO'),
        (SELECT id_residente FROM RESIDENTES WHERE numero_documento='71890123'), 'ARRENDATARIO');

INSERT INTO CONTRATO_RESIDENTE (id_contrato_res, id_contrato, id_residente, rol_en_contrato)
VALUES (SEC_CONTRATO_RESIDENTE.NEXTVAL,
        (SELECT id_contrato FROM CONTRATOS WHERE id_apartamento=(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='301') AND estado='FINALIZADO'),
        (SELECT id_residente FROM RESIDENTES WHERE numero_documento='1234567890'), 'RESIDENTE_MENOR');
COMMIT;

-- =============================================================================
-- 8. TUTORES (para Miguel menor de edad)
--    Columnas: id_tutor, id_residente_menor, id_tipo_doc, numero_documento,
--              nombres, apellidos, parentesco, telefono, email, ...
-- =============================================================================
INSERT INTO TUTORES (id_tutor, id_residente_menor, id_tipo_doc, numero_documento, nombres, apellidos, parentesco, telefono, email, fecha_registro, actualizado_en)
VALUES (SEC_TUTORES.NEXTVAL,
        (SELECT id_residente FROM RESIDENTES WHERE numero_documento='1234567890'),
        1, '71890123', 'Pedro Antonio', 'Soto Vargas', 'PADRE', '3012345678', 'pedro.soto@empresa.com',
        SYSTIMESTAMP, SYSTIMESTAMP);
COMMIT;

-- =============================================================================
-- 9. CUOTAS_ARRIENDO
-- =============================================================================
-- C1 (AP-201): Feb25 PAGADA, Mar25 PAGADA, Abr25 EN_MORA, May25 PENDIENTE, Jun25 PENDIENTE
INSERT INTO CUOTAS_ARRIENDO (id_cuota, id_contrato, anio, mes, fecha_limite, valor_base, valor_mora, valor_total, estado, fecha_generacion, actualizado_en)
VALUES (SEC_CUOTAS_ARRIENDO.NEXTVAL, (SELECT id_contrato FROM CONTRATOS WHERE id_apartamento=(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='201') AND estado='ACTIVO'), 2025, 2, DATE '2025-02-05', 1200000, 0, 1200000, 'PAGADA', SYSTIMESTAMP, SYSTIMESTAMP);
INSERT INTO CUOTAS_ARRIENDO (id_cuota, id_contrato, anio, mes, fecha_limite, valor_base, valor_mora, valor_total, estado, fecha_generacion, actualizado_en)
VALUES (SEC_CUOTAS_ARRIENDO.NEXTVAL, (SELECT id_contrato FROM CONTRATOS WHERE id_apartamento=(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='201') AND estado='ACTIVO'), 2025, 3, DATE '2025-03-05', 1200000, 0, 1200000, 'PAGADA', SYSTIMESTAMP, SYSTIMESTAMP);
INSERT INTO CUOTAS_ARRIENDO (id_cuota, id_contrato, anio, mes, fecha_limite, valor_base, valor_mora, valor_total, estado, fecha_generacion, actualizado_en)
VALUES (SEC_CUOTAS_ARRIENDO.NEXTVAL, (SELECT id_contrato FROM CONTRATOS WHERE id_apartamento=(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='201') AND estado='ACTIVO'), 2025, 4, DATE '2025-04-05', 1200000, 18000, 1218000, 'EN_MORA', SYSTIMESTAMP, SYSTIMESTAMP);
INSERT INTO CUOTAS_ARRIENDO (id_cuota, id_contrato, anio, mes, fecha_limite, valor_base, valor_mora, valor_total, estado, fecha_generacion, actualizado_en)
VALUES (SEC_CUOTAS_ARRIENDO.NEXTVAL, (SELECT id_contrato FROM CONTRATOS WHERE id_apartamento=(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='201') AND estado='ACTIVO'), 2025, 5, DATE '2025-05-05', 1200000, 0, 1200000, 'PENDIENTE', SYSTIMESTAMP, SYSTIMESTAMP);
INSERT INTO CUOTAS_ARRIENDO (id_cuota, id_contrato, anio, mes, fecha_limite, valor_base, valor_mora, valor_total, estado, fecha_generacion, actualizado_en)
VALUES (SEC_CUOTAS_ARRIENDO.NEXTVAL, (SELECT id_contrato FROM CONTRATOS WHERE id_apartamento=(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='201') AND estado='ACTIVO'), 2025, 6, DATE '2025-06-05', 1200000, 0, 1200000, 'PENDIENTE', SYSTIMESTAMP, SYSTIMESTAMP);

-- C2 (AP-202): Feb25 PAGADA, Mar25 VENCIDA, Abr25 EN_MORA, May25 PENDIENTE, Jun25 PENDIENTE
INSERT INTO CUOTAS_ARRIENDO (id_cuota, id_contrato, anio, mes, fecha_limite, valor_base, valor_mora, valor_total, estado, fecha_generacion, actualizado_en)
VALUES (SEC_CUOTAS_ARRIENDO.NEXTVAL, (SELECT id_contrato FROM CONTRATOS WHERE id_apartamento=(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='202') AND estado='ACTIVO'), 2025, 2, DATE '2025-02-10', 1800000, 0, 1800000, 'PAGADA', SYSTIMESTAMP, SYSTIMESTAMP);
INSERT INTO CUOTAS_ARRIENDO (id_cuota, id_contrato, anio, mes, fecha_limite, valor_base, valor_mora, valor_total, estado, fecha_generacion, actualizado_en)
VALUES (SEC_CUOTAS_ARRIENDO.NEXTVAL, (SELECT id_contrato FROM CONTRATOS WHERE id_apartamento=(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='202') AND estado='ACTIVO'), 2025, 3, DATE '2025-03-10', 1800000, 0, 1800000, 'VENCIDA', SYSTIMESTAMP, SYSTIMESTAMP);
INSERT INTO CUOTAS_ARRIENDO (id_cuota, id_contrato, anio, mes, fecha_limite, valor_base, valor_mora, valor_total, estado, fecha_generacion, actualizado_en)
VALUES (SEC_CUOTAS_ARRIENDO.NEXTVAL, (SELECT id_contrato FROM CONTRATOS WHERE id_apartamento=(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='202') AND estado='ACTIVO'), 2025, 4, DATE '2025-04-10', 1800000, 54000, 1854000, 'EN_MORA', SYSTIMESTAMP, SYSTIMESTAMP);
INSERT INTO CUOTAS_ARRIENDO (id_cuota, id_contrato, anio, mes, fecha_limite, valor_base, valor_mora, valor_total, estado, fecha_generacion, actualizado_en)
VALUES (SEC_CUOTAS_ARRIENDO.NEXTVAL, (SELECT id_contrato FROM CONTRATOS WHERE id_apartamento=(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='202') AND estado='ACTIVO'), 2025, 5, DATE '2025-05-10', 1800000, 0, 1800000, 'PENDIENTE', SYSTIMESTAMP, SYSTIMESTAMP);
INSERT INTO CUOTAS_ARRIENDO (id_cuota, id_contrato, anio, mes, fecha_limite, valor_base, valor_mora, valor_total, estado, fecha_generacion, actualizado_en)
VALUES (SEC_CUOTAS_ARRIENDO.NEXTVAL, (SELECT id_contrato FROM CONTRATOS WHERE id_apartamento=(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='202') AND estado='ACTIVO'), 2025, 6, DATE '2025-06-10', 1800000, 0, 1800000, 'PENDIENTE', SYSTIMESTAMP, SYSTIMESTAMP);

-- C3 (AP-301, finalizado): Ene24 PAGADA, Feb24 PAGADA
INSERT INTO CUOTAS_ARRIENDO (id_cuota, id_contrato, anio, mes, fecha_limite, valor_base, valor_mora, valor_total, estado, fecha_generacion, actualizado_en)
VALUES (SEC_CUOTAS_ARRIENDO.NEXTVAL, (SELECT id_contrato FROM CONTRATOS WHERE id_apartamento=(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='301') AND estado='FINALIZADO'), 2024, 1, DATE '2024-01-05', 2200000, 0, 2200000, 'PAGADA', SYSTIMESTAMP, SYSTIMESTAMP);
INSERT INTO CUOTAS_ARRIENDO (id_cuota, id_contrato, anio, mes, fecha_limite, valor_base, valor_mora, valor_total, estado, fecha_generacion, actualizado_en)
VALUES (SEC_CUOTAS_ARRIENDO.NEXTVAL, (SELECT id_contrato FROM CONTRATOS WHERE id_apartamento=(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='301') AND estado='FINALIZADO'), 2024, 2, DATE '2024-02-05', 2200000, 0, 2200000, 'PAGADA', SYSTIMESTAMP, SYSTIMESTAMP);
COMMIT;

-- =============================================================================
-- 10. PAGOS
--    FK: id_registrado_por → USUARIOS(id_usuario)
-- =============================================================================
INSERT INTO PAGOS (id_pago, id_cuota, id_registrado_por, fecha_pago, valor_pagado, metodo_pago, referencia, fecha_registro)
VALUES (SEC_PAGOS.NEXTVAL,
        (SELECT id_cuota FROM CUOTAS_ARRIENDO q JOIN CONTRATOS c ON q.id_contrato=c.id_contrato WHERE c.id_apartamento=(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='201') AND q.anio=2025 AND q.mes=2),
        (SELECT id_usuario FROM USUARIOS WHERE username='admin_saed'),
        DATE '2025-02-04', 1200000, 'TRANSFERENCIA', 'TXN-2025-020401', SYSTIMESTAMP);
INSERT INTO PAGOS (id_pago, id_cuota, id_registrado_por, fecha_pago, valor_pagado, metodo_pago, referencia, fecha_registro)
VALUES (SEC_PAGOS.NEXTVAL,
        (SELECT id_cuota FROM CUOTAS_ARRIENDO q JOIN CONTRATOS c ON q.id_contrato=c.id_contrato WHERE c.id_apartamento=(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='201') AND q.anio=2025 AND q.mes=3),
        (SELECT id_usuario FROM USUARIOS WHERE username='admin_saed'),
        DATE '2025-03-03', 1200000, 'TRANSFERENCIA', 'TXN-2025-030301', SYSTIMESTAMP);
INSERT INTO PAGOS (id_pago, id_cuota, id_registrado_por, fecha_pago, valor_pagado, metodo_pago, referencia, fecha_registro)
VALUES (SEC_PAGOS.NEXTVAL,
        (SELECT id_cuota FROM CUOTAS_ARRIENDO q JOIN CONTRATOS c ON q.id_contrato=c.id_contrato WHERE c.id_apartamento=(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='201') AND q.anio=2025 AND q.mes=4),
        (SELECT id_usuario FROM USUARIOS WHERE username='admin_saed'),
        DATE '2025-04-18', 600000, 'EFECTIVO', NULL, SYSTIMESTAMP);
INSERT INTO PAGOS (id_pago, id_cuota, id_registrado_por, fecha_pago, valor_pagado, metodo_pago, referencia, fecha_registro)
VALUES (SEC_PAGOS.NEXTVAL,
        (SELECT id_cuota FROM CUOTAS_ARRIENDO q JOIN CONTRATOS c ON q.id_contrato=c.id_contrato WHERE c.id_apartamento=(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='202') AND q.anio=2025 AND q.mes=2),
        (SELECT id_usuario FROM USUARIOS WHERE username='admin_saed'),
        DATE '2025-02-09', 1800000, 'TARJETA', 'CARD-2025-0209', SYSTIMESTAMP);
INSERT INTO PAGOS (id_pago, id_cuota, id_registrado_por, fecha_pago, valor_pagado, metodo_pago, referencia, fecha_registro)
VALUES (SEC_PAGOS.NEXTVAL,
        (SELECT id_cuota FROM CUOTAS_ARRIENDO q JOIN CONTRATOS c ON q.id_contrato=c.id_contrato WHERE c.id_apartamento=(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='301') AND q.anio=2024 AND q.mes=1),
        (SELECT id_usuario FROM USUARIOS WHERE username='admin_saed'),
        DATE '2024-01-04', 2200000, 'CHEQUE', 'CHQ-00123', SYSTIMESTAMP);
INSERT INTO PAGOS (id_pago, id_cuota, id_registrado_por, fecha_pago, valor_pagado, metodo_pago, referencia, fecha_registro)
VALUES (SEC_PAGOS.NEXTVAL,
        (SELECT id_cuota FROM CUOTAS_ARRIENDO q JOIN CONTRATOS c ON q.id_contrato=c.id_contrato WHERE c.id_apartamento=(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='301') AND q.anio=2024 AND q.mes=2),
        (SELECT id_usuario FROM USUARIOS WHERE username='admin_saed'),
        DATE '2024-02-06', 2200000, 'TRANSFERENCIA', 'TXN-2024-020601', SYSTIMESTAMP);
COMMIT;

-- =============================================================================
-- 11. VISITANTES
-- =============================================================================
INSERT INTO VISITANTES (id_visitante, id_tipo_doc, numero_documento, nombres, apellidos, telefono, activo, fecha_registro, actualizado_en)
VALUES (SEC_VISITANTES.NEXTVAL, 1, '1098765432', 'Laura Camila', 'Díaz Suárez', '3113456789', 1, SYSTIMESTAMP, SYSTIMESTAMP);
INSERT INTO VISITANTES (id_visitante, id_tipo_doc, numero_documento, nombres, apellidos, telefono, activo, fecha_registro, actualizado_en)
VALUES (SEC_VISITANTES.NEXTVAL, 1, '9876543210', 'Roberto', 'Castillo Medina', '3184567890', 1, SYSTIMESTAMP, SYSTIMESTAMP);
INSERT INTO VISITANTES (id_visitante, id_tipo_doc, numero_documento, nombres, apellidos, telefono, activo, fecha_registro, actualizado_en)
VALUES (SEC_VISITANTES.NEXTVAL, 4, 'PAS-78901234', 'Emily', 'Johnson', NULL, 1, SYSTIMESTAMP, SYSTIMESTAMP);
INSERT INTO VISITANTES (id_visitante, id_tipo_doc, numero_documento, nombres, apellidos, telefono, activo, fecha_registro, actualizado_en)
VALUES (SEC_VISITANTES.NEXTVAL, 1, '19234567', 'Fernando', 'Ospina Lara', '3001239876', 1, SYSTIMESTAMP, SYSTIMESTAMP);
INSERT INTO VISITANTES (id_visitante, id_tipo_doc, numero_documento, nombres, apellidos, telefono, activo, fecha_registro, actualizado_en)
VALUES (SEC_VISITANTES.NEXTVAL, 2, 'CE-456789', 'Jean Pierre', 'Dupont', '3170987654', 1, SYSTIMESTAMP, SYSTIMESTAMP);
COMMIT;

-- =============================================================================
-- 12. VISITAS
--    Columnas reales: id_visita, id_contrato_res, id_residente, tiempo_validez_min,
--                     cantidad_personas, notas, estado, fecha_registro, actualizado_en
--    NO tiene id_visitante — eso va en REGISTRO_VISITA.
-- =============================================================================
-- V1: FINALIZADA — Carlos (ARRENDATARIO) recibió a Laura
INSERT INTO VISITAS (id_visita, id_contrato_res, id_residente, tiempo_validez_min, cantidad_personas, estado, notas, fecha_registro, actualizado_en)
VALUES (SEC_VISITAS.NEXTVAL,
        (SELECT id_contrato_res FROM CONTRATO_RESIDENTE WHERE id_residente=(SELECT id_residente FROM RESIDENTES WHERE numero_documento='10456789') AND rol_en_contrato='ARRENDATARIO' AND ROWNUM=1),
        (SELECT id_residente FROM RESIDENTES WHERE numero_documento='10456789'),
        30, 1, 'FINALIZADA', 'Visita frecuente', SYSTIMESTAMP - INTERVAL '2' DAY, SYSTIMESTAMP);

-- V2: ACTIVA — Carlos recibió a Roberto (con carro)
INSERT INTO VISITAS (id_visita, id_contrato_res, id_residente, tiempo_validez_min, cantidad_personas, estado, notas, fecha_registro, actualizado_en)
VALUES (SEC_VISITAS.NEXTVAL,
        (SELECT id_contrato_res FROM CONTRATO_RESIDENTE WHERE id_residente=(SELECT id_residente FROM RESIDENTES WHERE numero_documento='10456789') AND rol_en_contrato='ARRENDATARIO' AND ROWNUM=1),
        (SELECT id_residente FROM RESIDENTES WHERE numero_documento='10456789'),
        60, 2, 'ACTIVA', 'Amigos, vienen con carro', SYSTIMESTAMP - INTERVAL '1' HOUR, SYSTIMESTAMP);

-- V3: PENDIENTE — Jhon autorizó a Fernando
INSERT INTO VISITAS (id_visita, id_contrato_res, id_residente, tiempo_validez_min, cantidad_personas, estado, notas, fecha_registro, actualizado_en)
VALUES (SEC_VISITAS.NEXTVAL,
        (SELECT id_contrato_res FROM CONTRATO_RESIDENTE WHERE id_residente=(SELECT id_residente FROM RESIDENTES WHERE numero_documento='80234567') AND rol_en_contrato='ARRENDATARIO' AND ROWNUM=1),
        (SELECT id_residente FROM RESIDENTES WHERE numero_documento='80234567'),
        30, 1, 'PENDIENTE', 'Técnico de internet', SYSTIMESTAMP - INTERVAL '30' MINUTE, SYSTIMESTAMP);

-- V4: CANCELADA — Jhon canceló a Emily
INSERT INTO VISITAS (id_visita, id_contrato_res, id_residente, tiempo_validez_min, cantidad_personas, estado, notas, fecha_registro, actualizado_en)
VALUES (SEC_VISITAS.NEXTVAL,
        (SELECT id_contrato_res FROM CONTRATO_RESIDENTE WHERE id_residente=(SELECT id_residente FROM RESIDENTES WHERE numero_documento='80234567') AND rol_en_contrato='ARRENDATARIO' AND ROWNUM=1),
        (SELECT id_residente FROM RESIDENTES WHERE numero_documento='80234567'),
        30, 1, 'CANCELADA', 'Cambio de planes', SYSTIMESTAMP - INTERVAL '5' DAY, SYSTIMESTAMP);

-- V5: EXPIRADA — QR expiró sin uso
INSERT INTO VISITAS (id_visita, id_contrato_res, id_residente, tiempo_validez_min, cantidad_personas, estado, notas, fecha_registro, actualizado_en)
VALUES (SEC_VISITAS.NEXTVAL,
        (SELECT id_contrato_res FROM CONTRATO_RESIDENTE WHERE id_residente=(SELECT id_residente FROM RESIDENTES WHERE numero_documento='10456789') AND rol_en_contrato='ARRENDATARIO' AND ROWNUM=1),
        (SELECT id_residente FROM RESIDENTES WHERE numero_documento='10456789'),
        15, 1, 'EXPIRADA', 'QR expiró sin usarse', SYSTIMESTAMP - INTERVAL '3' DAY, SYSTIMESTAMP);
COMMIT;

-- =============================================================================
-- 13. VEHICULOS_VISITA
--    Columnas: id_vehiculo_visita, id_visita, id_parqueadero, placa, tipo,
--              descripcion_tipo, color, marca, hora_entrada, hora_salida, ...
--    tipo permitido: VEHICULO, MOTO, BICICLETA, OTRO
-- =============================================================================
-- Vehículo V2 (ACTIVA) — parqueadero V-02
INSERT INTO VEHICULOS_VISITA (id_vehiculo_visita, id_visita, tipo, placa, descripcion_tipo, marca, id_parqueadero)
VALUES (SEC_VEHICULOS_VISITA.NEXTVAL,
        (SELECT id_visita FROM VISITAS WHERE estado='ACTIVA' AND ROWNUM=1),
        'VEHICULO', 'ABC-123', 'Toyota Corolla gris 2022', 'Toyota',
        (SELECT id_parqueadero FROM PARQUEADEROS WHERE codigo='V-02'));

-- Vehículo V1 (FINALIZADA) — moto, sin parqueadero
INSERT INTO VEHICULOS_VISITA (id_vehiculo_visita, id_visita, tipo, placa, descripcion_tipo, marca)
VALUES (SEC_VEHICULOS_VISITA.NEXTVAL,
        (SELECT id_visita FROM VISITAS WHERE estado='FINALIZADA' AND ROWNUM=1),
        'MOTO', 'MOT-456', 'Yamaha FZ azul', 'Yamaha');
COMMIT;

-- =============================================================================
-- 14. REGISTRO_VISITA (bridge VISITAS × VISITANTES × VEHICULOS_VISITA)
-- =============================================================================
-- V1: Laura visitó a Carlos (a pie, titular)
INSERT INTO REGISTRO_VISITA (id_registro_visita, id_visita, id_visitante, id_vehiculo_visita, es_titular)
VALUES (SEC_REGISTRO_VISITA.NEXTVAL,
        (SELECT id_visita FROM VISITAS WHERE estado='FINALIZADA' AND ROWNUM=1),
        (SELECT id_visitante FROM VISITANTES WHERE numero_documento='1098765432'),
        NULL, 1);

-- V2: Roberto visitó a Carlos (con carro, titular)
INSERT INTO REGISTRO_VISITA (id_registro_visita, id_visita, id_visitante, id_vehiculo_visita, es_titular)
VALUES (SEC_REGISTRO_VISITA.NEXTVAL,
        (SELECT id_visita FROM VISITAS WHERE estado='ACTIVA' AND ROWNUM=1),
        (SELECT id_visitante FROM VISITANTES WHERE numero_documento='9876543210'),
        (SELECT id_vehiculo_visita FROM VEHICULOS_VISITA WHERE placa='ABC-123' AND ROWNUM=1), 1);

-- V3: Fernando visita a Jhon (a pie)
INSERT INTO REGISTRO_VISITA (id_registro_visita, id_visita, id_visitante, id_vehiculo_visita, es_titular)
VALUES (SEC_REGISTRO_VISITA.NEXTVAL,
        (SELECT id_visita FROM VISITAS WHERE estado='PENDIENTE' AND ROWNUM=1),
        (SELECT id_visitante FROM VISITANTES WHERE numero_documento='19234567'),
        NULL, 1);

-- V4: Emily cancelada
INSERT INTO REGISTRO_VISITA (id_registro_visita, id_visita, id_visitante, id_vehiculo_visita, es_titular)
VALUES (SEC_REGISTRO_VISITA.NEXTVAL,
        (SELECT id_visita FROM VISITAS WHERE estado='CANCELADA' AND ROWNUM=1),
        (SELECT id_visitante FROM VISITANTES WHERE numero_documento='PAS-78901234'),
        NULL, 1);

-- V5: Jean Pierre expirado
INSERT INTO REGISTRO_VISITA (id_registro_visita, id_visita, id_visitante, id_vehiculo_visita, es_titular)
VALUES (SEC_REGISTRO_VISITA.NEXTVAL,
        (SELECT id_visita FROM VISITAS WHERE estado='EXPIRADA' AND ROWNUM=1),
        (SELECT id_visitante FROM VISITANTES WHERE numero_documento='CE-456789'),
        NULL, 1);
COMMIT;

-- =============================================================================
-- 15. QR_ACCESOS
--    Secuencia: SEC_QR_ACCESOS
-- =============================================================================
INSERT INTO QR_ACCESOS (id_qr, id_visita, codigo_qr, fecha_generacion, fecha_expiracion, usado, fecha_uso, id_vigilante_uso)
VALUES (SEC_QR_ACCESOS.NEXTVAL,
        (SELECT id_visita FROM VISITAS WHERE estado='FINALIZADA' AND ROWNUM=1),
        'QR-' || RAWTOHEX(SYS_GUID()),
        SYSTIMESTAMP - INTERVAL '2' DAY,
        SYSTIMESTAMP - INTERVAL '2' DAY + INTERVAL '30' MINUTE,
        1, SYSTIMESTAMP - INTERVAL '2' DAY + INTERVAL '10' MINUTE,
        (SELECT id_usuario FROM USUARIOS WHERE username='portero_dia'));

INSERT INTO QR_ACCESOS (id_qr, id_visita, codigo_qr, fecha_generacion, fecha_expiracion, usado, fecha_uso, id_vigilante_uso)
VALUES (SEC_QR_ACCESOS.NEXTVAL,
        (SELECT id_visita FROM VISITAS WHERE estado='ACTIVA' AND ROWNUM=1),
        'QR-' || RAWTOHEX(SYS_GUID()),
        SYSTIMESTAMP - INTERVAL '1' HOUR,
        SYSTIMESTAMP - INTERVAL '1' HOUR + INTERVAL '60' MINUTE,
        1, SYSTIMESTAMP - INTERVAL '55' MINUTE,
        (SELECT id_usuario FROM USUARIOS WHERE username='portero_dia'));

INSERT INTO QR_ACCESOS (id_qr, id_visita, codigo_qr, fecha_generacion, fecha_expiracion, usado, fecha_uso, id_vigilante_uso)
VALUES (SEC_QR_ACCESOS.NEXTVAL,
        (SELECT id_visita FROM VISITAS WHERE estado='PENDIENTE' AND ROWNUM=1),
        'QR-' || RAWTOHEX(SYS_GUID()),
        SYSTIMESTAMP - INTERVAL '30' MINUTE,
        SYSTIMESTAMP + INTERVAL '15' MINUTE,
        0, NULL, NULL);

INSERT INTO QR_ACCESOS (id_qr, id_visita, codigo_qr, fecha_generacion, fecha_expiracion, usado, fecha_uso, id_vigilante_uso)
VALUES (SEC_QR_ACCESOS.NEXTVAL,
        (SELECT id_visita FROM VISITAS WHERE estado='EXPIRADA' AND ROWNUM=1),
        'QR-' || RAWTOHEX(SYS_GUID()),
        SYSTIMESTAMP - INTERVAL '3' DAY,
        SYSTIMESTAMP - INTERVAL '3' DAY + INTERVAL '15' MINUTE,
        0, NULL, NULL);
COMMIT;

-- =============================================================================
-- 16. REGISTROS_ACCESO
--    Secuencia: SEC_REGISTROS_ACCESO
-- =============================================================================
INSERT INTO REGISTROS_ACCESO (id_acceso, id_visita, id_vigilante, hora_entrada, hora_salida, observaciones, fecha_registro, actualizado_en)
VALUES (SEC_REGISTROS_ACCESO.NEXTVAL,
        (SELECT id_visita FROM VISITAS WHERE estado='FINALIZADA' AND ROWNUM=1),
        (SELECT id_usuario FROM USUARIOS WHERE username='portero_dia'),
        SYSTIMESTAMP - INTERVAL '2' DAY + INTERVAL '10' MINUTE,
        SYSTIMESTAMP - INTERVAL '2' DAY + INTERVAL '90' MINUTE,
        'Ingreso normal, QR verificado', SYSTIMESTAMP, SYSTIMESTAMP);

INSERT INTO REGISTROS_ACCESO (id_acceso, id_visita, id_vigilante, hora_entrada, hora_salida, observaciones, fecha_registro, actualizado_en)
VALUES (SEC_REGISTROS_ACCESO.NEXTVAL,
        (SELECT id_visita FROM VISITAS WHERE estado='ACTIVA' AND ROWNUM=1),
        (SELECT id_usuario FROM USUARIOS WHERE username='portero_dia'),
        SYSTIMESTAMP - INTERVAL '55' MINUTE, NULL,
        '2 personas, vehículo, asignado V-02', SYSTIMESTAMP, SYSTIMESTAMP);
COMMIT;

-- =============================================================================
-- 17. BUZON
--    Requiere: creado_por (FK a USUARIOS) NOT NULL
-- =============================================================================
INSERT INTO BUZON (id_mensaje, id_apartamento, tipo, titulo, cuerpo, foto_captura, creado_por, fecha_creacion, entregado)
VALUES (SEC_BUZON.NEXTVAL,
        (SELECT id_apartamento FROM APARTAMENTOS WHERE numero='201'),
        'PAQUETE', 'Paquete recibido', 'Se recibió un paquete de Amazon en portería. Peso aprox 2kg.',
        'https://saed.storage/buzon/pkg001.jpg',
        (SELECT id_usuario FROM USUARIOS WHERE username='portero_dia'),
        SYSTIMESTAMP - INTERVAL '3' HOUR, 0);

INSERT INTO BUZON (id_mensaje, id_apartamento, tipo, titulo, cuerpo, creado_por, fecha_creacion, entregado)
VALUES (SEC_BUZON.NEXTVAL,
        (SELECT id_apartamento FROM APARTAMENTOS WHERE numero='202'),
        'PAQUETE', 'Paquete entregado', 'Paquete de Rappi entregado al residente el 20/05/2025.',
        (SELECT id_usuario FROM USUARIOS WHERE username='portero_dia'),
        SYSTIMESTAMP - INTERVAL '1' DAY, 1);

INSERT INTO BUZON (id_mensaje, id_apartamento, tipo, titulo, cuerpo, creado_por, fecha_creacion, entregado)
VALUES (SEC_BUZON.NEXTVAL,
        (SELECT id_apartamento FROM APARTAMENTOS WHERE numero='201'),
        'AVISO', 'Suspensión de agua', '25 de mayo 8am-12pm suspensión del servicio por mantenimiento.',
        (SELECT id_usuario FROM USUARIOS WHERE username='admin_saed'),
        SYSTIMESTAMP - INTERVAL '12' HOUR, 0);

INSERT INTO BUZON (id_mensaje, id_apartamento, tipo, titulo, cuerpo, creado_por, fecha_creacion, entregado)
VALUES (SEC_BUZON.NEXTVAL,
        (SELECT id_apartamento FROM APARTAMENTOS WHERE numero='202'),
        'AVISO', 'Suspensión de agua', '25 de mayo 8am-12pm suspensión del servicio por mantenimiento.',
        (SELECT id_usuario FROM USUARIOS WHERE username='admin_saed'),
        SYSTIMESTAMP - INTERVAL '12' HOUR, 0);

INSERT INTO BUZON (id_mensaje, id_apartamento, tipo, titulo, cuerpo, creado_por, fecha_creacion, entregado)
VALUES (SEC_BUZON.NEXTVAL,
        (SELECT id_apartamento FROM APARTAMENTOS WHERE numero='301'),
        'AVISO', 'Suspensión de agua', '25 de mayo 8am-12pm suspensión del servicio por mantenimiento.',
        (SELECT id_usuario FROM USUARIOS WHERE username='admin_saed'),
        SYSTIMESTAMP - INTERVAL '12' HOUR, 0);
COMMIT;

-- =============================================================================
-- 18. MULTAS
-- =============================================================================
INSERT INTO MULTAS (id_multa, id_apartamento, tipo, monto, estado, fecha_creacion)
VALUES (SEC_MULTAS.NEXTVAL,
        (SELECT id_apartamento FROM APARTAMENTOS WHERE numero='201'),
        'RUIDO', 100000, 'PENDIENTE', SYSTIMESTAMP - INTERVAL '5' DAY);

INSERT INTO MULTAS (id_multa, id_apartamento, tipo, monto, estado, fecha_creacion)
VALUES (SEC_MULTAS.NEXTVAL,
        (SELECT id_apartamento FROM APARTAMENTOS WHERE numero='202'),
        'PARQUEADERO', 50000, 'PAGADA', SYSTIMESTAMP - INTERVAL '10' DAY);

INSERT INTO MULTAS (id_multa, id_apartamento, tipo, monto, estado, fecha_creacion)
VALUES (SEC_MULTAS.NEXTVAL,
        (SELECT id_apartamento FROM APARTAMENTOS WHERE numero='202'),
        'RUIDO', 100000, 'ANULADA', SYSTIMESTAMP - INTERVAL '20' DAY);

INSERT INTO MULTAS (id_multa, id_apartamento, id_mensaje, tipo, monto, estado, fecha_creacion)
VALUES (SEC_MULTAS.NEXTVAL,
        (SELECT id_apartamento FROM APARTAMENTOS WHERE numero='201'),
        (SELECT id_mensaje FROM BUZON WHERE id_apartamento=(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='201') AND tipo='PAQUETE' AND ROWNUM=1),
        'PARQUEADERO', 50000, 'PENDIENTE', SYSTIMESTAMP - INTERVAL '1' DAY);
COMMIT;

-- =============================================================================
-- 19. ALERTAS_PAGO
-- =============================================================================
INSERT INTO ALERTAS_PAGO (id_alerta, id_cuota, tipo_alerta, canal, leida, enviada_en, leida_en)
VALUES (SEC_ALERTAS_PAGO.NEXTVAL,
        (SELECT id_cuota FROM CUOTAS_ARRIENDO q JOIN CONTRATOS c ON q.id_contrato=c.id_contrato WHERE c.id_apartamento=(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='201') AND q.anio=2025 AND q.mes=5),
        'PROXIMO_VENCIMIENTO', 'SISTEMA', 1, SYSTIMESTAMP - INTERVAL '3' DAY, SYSTIMESTAMP - INTERVAL '2' DAY);

INSERT INTO ALERTAS_PAGO (id_alerta, id_cuota, tipo_alerta, canal, leida, enviada_en, leida_en)
VALUES (SEC_ALERTAS_PAGO.NEXTVAL,
        (SELECT id_cuota FROM CUOTAS_ARRIENDO q JOIN CONTRATOS c ON q.id_contrato=c.id_contrato WHERE c.id_apartamento=(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='201') AND q.anio=2025 AND q.mes=5),
        'PROXIMO_VENCIMIENTO', 'EMAIL', 0, SYSTIMESTAMP - INTERVAL '1' DAY, NULL);

INSERT INTO ALERTAS_PAGO (id_alerta, id_cuota, tipo_alerta, canal, leida, enviada_en, leida_en)
VALUES (SEC_ALERTAS_PAGO.NEXTVAL,
        (SELECT id_cuota FROM CUOTAS_ARRIENDO q JOIN CONTRATOS c ON q.id_contrato=c.id_contrato WHERE c.id_apartamento=(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='202') AND q.anio=2025 AND q.mes=3),
        'VENCIDA', 'SISTEMA', 1, TIMESTAMP '2025-03-11 08:00:00', TIMESTAMP '2025-03-11 09:15:00');

INSERT INTO ALERTAS_PAGO (id_alerta, id_cuota, tipo_alerta, canal, leida, enviada_en, leida_en)
VALUES (SEC_ALERTAS_PAGO.NEXTVAL,
        (SELECT id_cuota FROM CUOTAS_ARRIENDO q JOIN CONTRATOS c ON q.id_contrato=c.id_contrato WHERE c.id_apartamento=(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='202') AND q.anio=2025 AND q.mes=3),
        'VENCIDA', 'WHATSAPP', 1, TIMESTAMP '2025-03-11 08:00:00', TIMESTAMP '2025-03-11 10:00:00');

INSERT INTO ALERTAS_PAGO (id_alerta, id_cuota, tipo_alerta, canal, leida, enviada_en, leida_en)
VALUES (SEC_ALERTAS_PAGO.NEXTVAL,
        (SELECT id_cuota FROM CUOTAS_ARRIENDO q JOIN CONTRATOS c ON q.id_contrato=c.id_contrato WHERE c.id_apartamento=(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='202') AND q.anio=2025 AND q.mes=4),
        'EN_MORA', 'SISTEMA', 0, SYSTIMESTAMP - INTERVAL '5' DAY, NULL);

INSERT INTO ALERTAS_PAGO (id_alerta, id_cuota, tipo_alerta, canal, leida, enviada_en, leida_en)
VALUES (SEC_ALERTAS_PAGO.NEXTVAL,
        (SELECT id_cuota FROM CUOTAS_ARRIENDO q JOIN CONTRATOS c ON q.id_contrato=c.id_contrato WHERE c.id_apartamento=(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='202') AND q.anio=2025 AND q.mes=4),
        'EN_MORA', 'SMS', 0, SYSTIMESTAMP - INTERVAL '5' DAY, NULL);

INSERT INTO ALERTAS_PAGO (id_alerta, id_cuota, tipo_alerta, canal, leida, enviada_en, leida_en)
VALUES (SEC_ALERTAS_PAGO.NEXTVAL,
        (SELECT id_cuota FROM CUOTAS_ARRIENDO q JOIN CONTRATOS c ON q.id_contrato=c.id_contrato WHERE c.id_apartamento=(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='201') AND q.anio=2025 AND q.mes=4),
        'EN_MORA', 'SISTEMA', 1, SYSTIMESTAMP - INTERVAL '10' DAY, SYSTIMESTAMP - INTERVAL '9' DAY);
COMMIT;

-- =============================================================================
-- VERIFICACIÓN
-- =============================================================================
SELECT 'APARTAMENTOS'     entidad, COUNT(*) total FROM APARTAMENTOS   UNION ALL
SELECT 'PARQUEADEROS',    COUNT(*) FROM PARQUEADEROS    UNION ALL
SELECT 'RESIDENTES',      COUNT(*) FROM RESIDENTES      UNION ALL
SELECT 'USUARIOS',        COUNT(*) FROM USUARIOS        UNION ALL
SELECT 'CONTRATOS',       COUNT(*) FROM CONTRATOS       UNION ALL
SELECT 'CONTRATO_RES',    COUNT(*) FROM CONTRATO_RESIDENTE UNION ALL
SELECT 'CUOTAS',          COUNT(*) FROM CUOTAS_ARRIENDO UNION ALL
SELECT 'PAGOS',           COUNT(*) FROM PAGOS           UNION ALL
SELECT 'VISITANTES',      COUNT(*) FROM VISITANTES      UNION ALL
SELECT 'VISITAS',         COUNT(*) FROM VISITAS         UNION ALL
SELECT 'VEHICULOS_VISITA',COUNT(*) FROM VEHICULOS_VISITA UNION ALL
SELECT 'REGISTRO_VISITA', COUNT(*) FROM REGISTRO_VISITA UNION ALL
SELECT 'QR_ACCESOS',      COUNT(*) FROM QR_ACCESOS      UNION ALL
SELECT 'REG_ACCESO',      COUNT(*) FROM REGISTROS_ACCESO UNION ALL
SELECT 'BUZON',           COUNT(*) FROM BUZON           UNION ALL
SELECT 'MULTAS',          COUNT(*) FROM MULTAS          UNION ALL
SELECT 'ALERTAS_PAGO',    COUNT(*) FROM ALERTAS_PAGO    UNION ALL
SELECT 'TUTORES',         COUNT(*) FROM TUTORES;
COMMIT;
