-- =============================================================================
-- SAED — SISTEMA DE ADMINISTRACIÓN RESIDENCIAL
-- Datos de prueba completos | Oracle 18c XE
-- Generado: Mayo 2026
-- =============================================================================

-- Limpiar en orden inverso de dependencias (útil para re-ejecutar)
/*
DELETE FROM ALERTAS_PAGO;
DELETE FROM REGISTROS_ACCESO;
DELETE FROM QR_ACCESOS;
DELETE FROM VEHICULOS_VISITA;
DELETE FROM VISITAS;
DELETE FROM VISITANTES;
DELETE FROM PAGOS;
DELETE FROM CUOTAS_ARRIENDO;
DELETE FROM MULTAS;
DELETE FROM BUZON;
DELETE FROM CONTRATO_RESIDENTE;
DELETE FROM TUTORES;
DELETE FROM CONTRATOS;
DELETE FROM USUARIOS;
DELETE FROM RESIDENTES;
DELETE FROM PARQUEADEROS;
DELETE FROM APARTAMENTOS;
COMMIT;
*/

-- =============================================================================
-- 1. TIPOS_DOCUMENTO (catálogo base — insertar solo si no existen)
-- =============================================================================
MERGE INTO TIPOS_DOCUMENTO t
USING (SELECT 1 id_tipo_doc, 'Cédula de Ciudadanía' nombre, 'CC' abreviatura, 1 activo FROM DUAL
       UNION ALL SELECT 2, 'Cédula de Extranjería',   'CE',        1 FROM DUAL
       UNION ALL SELECT 3, 'NIT',                     'NIT',       1 FROM DUAL
       UNION ALL SELECT 4, 'Pasaporte',               'PASAPORTE', 1 FROM DUAL
       UNION ALL SELECT 5, 'Tarjeta de Identidad',    'TI',        1 FROM DUAL) s
ON (t.id_tipo_doc = s.id_tipo_doc)
WHEN NOT MATCHED THEN
  INSERT (id_tipo_doc, nombre, abreviatura, activo)
  VALUES (s.id_tipo_doc, s.nombre, s.abreviatura, s.activo);
COMMIT;

-- =============================================================================
-- 2. APARTAMENTOS  (5 unidades — pisos 1-4, tipos variados)
-- =============================================================================
-- AP-101: ESTUDIO piso 1, DISPONIBLE
INSERT INTO APARTAMENTOS (id_apartamento, numero, piso, tipo, area_m2, capacidad_maxima, estado, activo, fecha_registro, actualizado_en)
VALUES (SEC_APARTAMENTOS.NEXTVAL, '101', 1, 'ESTUDIO', 42.50, 2, 'DISPONIBLE', 1, SYSTIMESTAMP, SYSTIMESTAMP);

-- AP-201: 1HAB piso 2, OCUPADO
INSERT INTO APARTAMENTOS (id_apartamento, numero, piso, tipo, area_m2, capacidad_maxima, estado, activo, fecha_registro, actualizado_en)
VALUES (SEC_APARTAMENTOS.NEXTVAL, '201', 2, '1HAB', 58.00, 3, 'OCUPADO', 1, SYSTIMESTAMP, SYSTIMESTAMP);

-- AP-202: 2HAB piso 2, OCUPADO
INSERT INTO APARTAMENTOS (id_apartamento, numero, piso, tipo, area_m2, capacidad_maxima, estado, activo, fecha_registro, actualizado_en)
VALUES (SEC_APARTAMENTOS.NEXTVAL, '202', 2, '2HAB', 75.00, 4, 'OCUPADO', 1, SYSTIMESTAMP, SYSTIMESTAMP);

-- AP-301: 3HAB piso 3, OCUPADO
INSERT INTO APARTAMENTOS (id_apartamento, numero, piso, tipo, area_m2, capacidad_maxima, estado, activo, fecha_registro, actualizado_en)
VALUES (SEC_APARTAMENTOS.NEXTVAL, '301', 3, '3HAB', 95.00, 5, 'OCUPADO', 1, SYSTIMESTAMP, SYSTIMESTAMP);

-- AP-401: PENTHOUSE piso 4, EN_MANTENIMIENTO
INSERT INTO APARTAMENTOS (id_apartamento, numero, piso, tipo, area_m2, capacidad_maxima, estado, activo, fecha_registro, actualizado_en)
VALUES (SEC_APARTAMENTOS.NEXTVAL, '401', 4, 'PENTHOUSE', 140.00, 6, 'EN_MANTENIMIENTO', 1, SYSTIMESTAMP, SYSTIMESTAMP);

COMMIT;

-- =============================================================================
-- 3. PARQUEADEROS  (fijos + rotativos de visitante)
-- =============================================================================
-- P-01: fijo vehiculo -> AP-201 (id=2)
INSERT INTO PARQUEADEROS (id_parqueadero, codigo, tipo, es_visitante, estado, id_apartamento, fecha_registro, actualizado_en)
VALUES (SEC_PARQUEADEROS.NEXTVAL, 'P-01', 'VEHICULO', 0, 'OCUPADO',
        (SELECT id_apartamento FROM APARTAMENTOS WHERE numero='201'), SYSTIMESTAMP, SYSTIMESTAMP);

-- P-02: fijo vehiculo -> AP-202 (id=3)
INSERT INTO PARQUEADEROS (id_parqueadero, codigo, tipo, es_visitante, estado, id_apartamento, fecha_registro, actualizado_en)
VALUES (SEC_PARQUEADEROS.NEXTVAL, 'P-02', 'VEHICULO', 0, 'OCUPADO',
        (SELECT id_apartamento FROM APARTAMENTOS WHERE numero='202'), SYSTIMESTAMP, SYSTIMESTAMP);

-- P-03: fijo moto -> AP-301 (id=4)
INSERT INTO PARQUEADEROS (id_parqueadero, codigo, tipo, es_visitante, estado, id_apartamento, fecha_registro, actualizado_en)
VALUES (SEC_PARQUEADEROS.NEXTVAL, 'P-03', 'MOTO', 0, 'DISPONIBLE',
        (SELECT id_apartamento FROM APARTAMENTOS WHERE numero='301'), SYSTIMESTAMP, SYSTIMESTAMP);

-- P-04: rotativo visitante vehiculo, DISPONIBLE
INSERT INTO PARQUEADEROS (id_parqueadero, codigo, tipo, es_visitante, estado, id_apartamento, fecha_registro, actualizado_en)
VALUES (SEC_PARQUEADEROS.NEXTVAL, 'V-01', 'VEHICULO', 1, 'DISPONIBLE', NULL, SYSTIMESTAMP, SYSTIMESTAMP);

-- P-05: rotativo visitante vehiculo, OCUPADO (en uso por visita activa)
INSERT INTO PARQUEADEROS (id_parqueadero, codigo, tipo, es_visitante, estado, id_apartamento, fecha_registro, actualizado_en)
VALUES (SEC_PARQUEADEROS.NEXTVAL, 'V-02', 'VEHICULO', 1, 'OCUPADO', NULL, SYSTIMESTAMP, SYSTIMESTAMP);

-- P-06: rotativo visitante bicicleta, EN_MANTENIMIENTO
INSERT INTO PARQUEADEROS (id_parqueadero, codigo, tipo, es_visitante, estado, id_apartamento, fecha_registro, actualizado_en)
VALUES (SEC_PARQUEADEROS.NEXTVAL, 'B-01', 'BICICLETA', 1, 'EN_MANTENIMIENTO', NULL, SYSTIMESTAMP, SYSTIMESTAMP);

COMMIT;

-- =============================================================================
-- 4. RESIDENTES  (7 personas con documentos variados)
-- =============================================================================
-- R1: Carlos Pérez — arrendatario AP-201
INSERT INTO RESIDENTES (id_residente, id_tipo_doc, numero_documento, nombres, apellidos, fecha_nacimiento, telefono, email, foto_url, doc_pdf_url, fecha_ingreso, es_menor_edad, activo, fecha_registro, actualizado_en)
VALUES (SEC_RESIDENTES.NEXTVAL, 1, '10456789', 'Carlos Andrés', 'Pérez Ramos',
        DATE '1990-03-15', '3001234567', 'carlos.perez@gmail.com',
        'https://saed.storage/fotos/r1.jpg', NULL,
        DATE '2024-01-01', 0, 1, SYSTIMESTAMP, SYSTIMESTAMP);

-- R2: María Ruiz — conviviente AP-201
INSERT INTO RESIDENTES (id_residente, id_tipo_doc, numero_documento, nombres, apellidos, fecha_nacimiento, telefono, email, foto_url, doc_pdf_url, fecha_ingreso, es_menor_edad, activo, fecha_registro, actualizado_en)
VALUES (SEC_RESIDENTES.NEXTVAL, 1, '52678901', 'María Fernanda', 'Ruiz Torres',
        DATE '1993-07-22', '3109876543', 'maria.ruiz@hotmail.com',
        'https://saed.storage/fotos/r2.jpg', NULL,
        DATE '2024-01-01', 0, 1, SYSTIMESTAMP, SYSTIMESTAMP);

-- R3: Jhon Martínez — arrendatario AP-202
INSERT INTO RESIDENTES (id_residente, id_tipo_doc, numero_documento, nombres, apellidos, fecha_nacimiento, telefono, email, foto_url, doc_pdf_url, fecha_ingreso, es_menor_edad, activo, fecha_registro, actualizado_en)
VALUES (SEC_RESIDENTES.NEXTVAL, 1, '80234567', 'Jhon Sebastián', 'Martínez López',
        DATE '1985-11-08', '3156781234', 'jhon.martinez@outlook.com',
        'https://saed.storage/fotos/r3.jpg', NULL,
        DATE '2023-06-01', 0, 1, SYSTIMESTAMP, SYSTIMESTAMP);

-- R4: Luisa Gómez — conviviente AP-202
INSERT INTO RESIDENTES (id_residente, id_tipo_doc, numero_documento, nombres, apellidos, fecha_nacimiento, telefono, email, foto_url, doc_pdf_url, fecha_ingreso, es_menor_edad, activo, fecha_registro, actualizado_en)
VALUES (SEC_RESIDENTES.NEXTVAL, 1, '43567890', 'Luisa Valentina', 'Gómez Ríos',
        DATE '1991-05-30', '3204567890', 'luisa.gomez@gmail.com',
        NULL, NULL,
        DATE '2023-06-01', 0, 1, SYSTIMESTAMP, SYSTIMESTAMP);

-- R5: Pedro Soto — arrendatario AP-301 (contrato finalizado)
INSERT INTO RESIDENTES (id_residente, id_tipo_doc, numero_documento, nombres, apellidos, fecha_nacimiento, telefono, email, foto_url, doc_pdf_url, fecha_ingreso, es_menor_edad, activo, fecha_registro, actualizado_en)
VALUES (SEC_RESIDENTES.NEXTVAL, 1, '71890123', 'Pedro Antonio', 'Soto Vargas',
        DATE '1978-02-14', '3012345678', 'pedro.soto@empresa.com',
        NULL, NULL,
        DATE '2022-03-01', 0, 1, SYSTIMESTAMP, SYSTIMESTAMP);

-- R6: Ana Morales — fiadora contrato AP-201
INSERT INTO RESIDENTES (id_residente, id_tipo_doc, numero_documento, nombres, apellidos, fecha_nacimiento, telefono, email, foto_url, doc_pdf_url, fecha_ingreso, es_menor_edad, activo, fecha_registro, actualizado_en)
VALUES (SEC_RESIDENTES.NEXTVAL, 1, '36789012', 'Ana Lucía', 'Morales Peña',
        DATE '1970-09-25', '3175678901', 'ana.morales@correo.co',
        NULL, NULL,
        DATE '2024-01-01', 0, 1, SYSTIMESTAMP, SYSTIMESTAMP);

-- R7: Miguel Torres — menor de edad, conviviente AP-301
INSERT INTO RESIDENTES (id_residente, id_tipo_doc, numero_documento, nombres, apellidos, fecha_nacimiento, telefono, email, foto_url, doc_pdf_url, fecha_ingreso, es_menor_edad, activo, fecha_registro, actualizado_en)
VALUES (SEC_RESIDENTES.NEXTVAL, 5, '1234567890', 'Miguel Ángel', 'Torres Soto',
        DATE '2010-06-12', NULL, NULL,
        NULL, NULL,
        DATE '2022-03-01', 1, 1, SYSTIMESTAMP, SYSTIMESTAMP);

COMMIT;

-- =============================================================================
-- 5. USUARIOS  (1 admin, 2 porteros, 2 residentes)
-- =============================================================================
-- U1: Administrador (sin residente asociado)
INSERT INTO USUARIOS (id_usuario, id_residente, username, password_hash, rol, activo, ultimo_login, fecha_registro, actualizado_en)
VALUES (SEC_USUARIOS.NEXTVAL, NULL, 'admin_saed',
        '$2b$12$eImiTXuWVxfM37uY4JANjOeH/y3bFMPqV5oRqF2LYqHBH0X3hZBfO', -- bcrypt: Admin@2025
        'ADMINISTRADOR', 1, SYSTIMESTAMP - INTERVAL '2' HOUR, SYSTIMESTAMP, SYSTIMESTAMP);

-- U2: Portero turno día
INSERT INTO USUARIOS (id_usuario, id_residente, username, password_hash, rol, activo, ultimo_login, fecha_registro, actualizado_en)
VALUES (SEC_USUARIOS.NEXTVAL, NULL, 'portero_dia',
        '$2b$12$abcXYZ123portero456hashDIA789xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx',
        'PORTERO', 1, SYSTIMESTAMP - INTERVAL '6' HOUR, SYSTIMESTAMP, SYSTIMESTAMP);

-- U3: Portero turno noche
INSERT INTO USUARIOS (id_usuario, id_residente, username, password_hash, rol, activo, ultimo_login, fecha_registro, actualizado_en)
VALUES (SEC_USUARIOS.NEXTVAL, NULL, 'portero_noche',
        '$2b$12$abcXYZ123portero456hashNOCHE789xxxxxxxxxxxxxxxxxxxxxxxxxxxxxx',
        'PORTERO', 1, SYSTIMESTAMP - INTERVAL '14' HOUR, SYSTIMESTAMP, SYSTIMESTAMP);

-- U4: Residente Carlos Pérez (R1)
INSERT INTO USUARIOS (id_usuario, id_residente, username, password_hash, rol, activo, ultimo_login, fecha_registro, actualizado_en)
VALUES (SEC_USUARIOS.NEXTVAL,
        (SELECT id_residente FROM RESIDENTES WHERE numero_documento='10456789'),
        'carlos.perez',
        '$2b$12$abcXYZ123residente456hashR1xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx',
        'RESIDENTE', 1, SYSTIMESTAMP - INTERVAL '1' DAY, SYSTIMESTAMP, SYSTIMESTAMP);

-- U5: Residente Jhon Martínez (R3)
INSERT INTO USUARIOS (id_usuario, id_residente, username, password_hash, rol, activo, ultimo_login, fecha_registro, actualizado_en)
VALUES (SEC_USUARIOS.NEXTVAL,
        (SELECT id_residente FROM RESIDENTES WHERE numero_documento='80234567'),
        'jhon.martinez',
        '$2b$12$abcXYZ123residente456hashR3xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx',
        'RESIDENTE', 1, SYSTIMESTAMP - INTERVAL '3' DAY, SYSTIMESTAMP, SYSTIMESTAMP);

COMMIT;

-- =============================================================================
-- 6. CONTRATOS
--    C1: ACTIVO  — AP-201, inició 2024-01-01
--    C2: ACTIVO  — AP-202, inició 2023-06-01 (más antiguo)
--    C3: FINALIZADO — AP-301, Pedro Soto (2022-03-01 a 2024-02-28)
-- =============================================================================
-- C1: AP-201, activo, dia_pago=5
INSERT INTO CONTRATOS (id_contrato, id_apartamento, id_tutor, id_registrado_por, fecha_inicio, fecha_fin, valor_mensual, dia_pago, dias_gracia, porcentaje_mora, contrato_pdf_url, estado, observaciones, fecha_registro, actualizado_en)
VALUES (SEC_CONTRATOS.NEXTVAL,
        (SELECT id_apartamento FROM APARTAMENTOS WHERE numero='201'),
        NULL,
        (SELECT id_usuario FROM USUARIOS WHERE username='admin_saed'),
        DATE '2024-01-01', NULL, 1200000, 5, 5, 1.5,
        'https://saed.storage/contratos/c1.pdf',
        'ACTIVO', 'Contrato estándar canon $1.200.000', SYSTIMESTAMP, SYSTIMESTAMP);

-- C2: AP-202, activo, dia_pago=10, mayor mora
INSERT INTO CONTRATOS (id_contrato, id_apartamento, id_tutor, id_registrado_por, fecha_inicio, fecha_fin, valor_mensual, dia_pago, dias_gracia, porcentaje_mora, contrato_pdf_url, estado, observaciones, fecha_registro, actualizado_en)
VALUES (SEC_CONTRATOS.NEXTVAL,
        (SELECT id_apartamento FROM APARTAMENTOS WHERE numero='202'),
        NULL,
        (SELECT id_usuario FROM USUARIOS WHERE username='admin_saed'),
        DATE '2023-06-01', NULL, 1800000, 10, 3, 2.0,
        'https://saed.storage/contratos/c2.pdf',
        'ACTIVO', 'Canon $1.800.000, mora 2% mensual', SYSTIMESTAMP, SYSTIMESTAMP);

-- C3: AP-301, finalizado
INSERT INTO CONTRATOS (id_contrato, id_apartamento, id_tutor, id_registrado_por, fecha_inicio, fecha_fin, valor_mensual, dia_pago, dias_gracia, porcentaje_mora, contrato_pdf_url, estado, observaciones, fecha_registro, actualizado_en)
VALUES (SEC_CONTRATOS.NEXTVAL,
        (SELECT id_apartamento FROM APARTAMENTOS WHERE numero='301'),
        NULL,
        (SELECT id_usuario FROM USUARIOS WHERE username='admin_saed'),
        DATE '2022-03-01', DATE '2024-02-28', 2200000, 5, 5, 1.5,
        'https://saed.storage/contratos/c3.pdf',
        'FINALIZADO', 'Contrato finalizado por término de vigencia', SYSTIMESTAMP, SYSTIMESTAMP);

COMMIT;

-- =============================================================================
-- 7. CONTRATO_RESIDENTE
-- =============================================================================
-- C1-AP201: Carlos(ARRENDATARIO), María(CONVIVIENTE), Ana(FIADOR)
INSERT INTO CONTRATO_RESIDENTE (id_contrato_res, id_contrato, id_residente, rol_en_contrato)
VALUES (SEC_CONTRATO_RES.NEXTVAL,
        (SELECT id_contrato FROM CONTRATOS WHERE id_apartamento=(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='201') AND estado='ACTIVO'),
        (SELECT id_residente FROM RESIDENTES WHERE numero_documento='10456789'),
        'ARRENDATARIO');

INSERT INTO CONTRATO_RESIDENTE (id_contrato_res, id_contrato, id_residente, rol_en_contrato)
VALUES (SEC_CONTRATO_RES.NEXTVAL,
        (SELECT id_contrato FROM CONTRATOS WHERE id_apartamento=(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='201') AND estado='ACTIVO'),
        (SELECT id_residente FROM RESIDENTES WHERE numero_documento='52678901'),
        'CONVIVIENTE');

INSERT INTO CONTRATO_RESIDENTE (id_contrato_res, id_contrato, id_residente, rol_en_contrato)
VALUES (SEC_CONTRATO_RES.NEXTVAL,
        (SELECT id_contrato FROM CONTRATOS WHERE id_apartamento=(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='201') AND estado='ACTIVO'),
        (SELECT id_residente FROM RESIDENTES WHERE numero_documento='36789012'),
        'FIADOR');

-- C2-AP202: Jhon(ARRENDATARIO), Luisa(CONVIVIENTE)
INSERT INTO CONTRATO_RESIDENTE (id_contrato_res, id_contrato, id_residente, rol_en_contrato)
VALUES (SEC_CONTRATO_RES.NEXTVAL,
        (SELECT id_contrato FROM CONTRATOS WHERE id_apartamento=(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='202') AND estado='ACTIVO'),
        (SELECT id_residente FROM RESIDENTES WHERE numero_documento='80234567'),
        'ARRENDATARIO');

INSERT INTO CONTRATO_RESIDENTE (id_contrato_res, id_contrato, id_residente, rol_en_contrato)
VALUES (SEC_CONTRATO_RES.NEXTVAL,
        (SELECT id_contrato FROM CONTRATOS WHERE id_apartamento=(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='202') AND estado='ACTIVO'),
        (SELECT id_residente FROM RESIDENTES WHERE numero_documento='43567890'),
        'CONVIVIENTE');

-- C3-AP301: Pedro(ARRENDATARIO), Miguel(CONVIVIENTE menor)
INSERT INTO CONTRATO_RESIDENTE (id_contrato_res, id_contrato, id_residente, rol_en_contrato)
VALUES (SEC_CONTRATO_RES.NEXTVAL,
        (SELECT id_contrato FROM CONTRATOS WHERE id_apartamento=(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='301') AND estado='FINALIZADO'),
        (SELECT id_residente FROM RESIDENTES WHERE numero_documento='71890123'),
        'ARRENDATARIO');

INSERT INTO CONTRATO_RESIDENTE (id_contrato_res, id_contrato, id_residente, rol_en_contrato)
VALUES (SEC_CONTRATO_RES.NEXTVAL,
        (SELECT id_contrato FROM CONTRATOS WHERE id_apartamento=(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='301') AND estado='FINALIZADO'),
        (SELECT id_residente FROM RESIDENTES WHERE numero_documento='1234567890'),
        'CONVIVIENTE');

COMMIT;

-- =============================================================================
-- 8. TUTORES  (para Miguel, menor de edad — contrato C3)
-- =============================================================================
INSERT INTO TUTORES (id_tutor, id_contrato, nombres, apellidos, parentesco, telefono, email, fecha_registro, actualizado_en)
VALUES (SEC_TUTORES.NEXTVAL,
        (SELECT id_contrato FROM CONTRATOS WHERE id_apartamento=(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='301') AND estado='FINALIZADO'),
        'Pedro Antonio', 'Soto Vargas', 'PADRE', '3012345678', 'pedro.soto@empresa.com',
        SYSTIMESTAMP, SYSTIMESTAMP);

COMMIT;

-- =============================================================================
-- 9. CUOTAS_ARRIENDO
--    Contrato C1 (AP-201, $1.200.000, dia_pago=5):
--      Feb25 PAGADA, Mar25 PAGADA, Abr25 EN_MORA, May25 PENDIENTE, Jun25 PENDIENTE
--    Contrato C2 (AP-202, $1.800.000, dia_pago=10):
--      Feb25 PAGADA, Mar25 VENCIDA, Abr25 EN_MORA, May25 PENDIENTE, Jun25 PENDIENTE
--    Contrato C3 (AP-301, finalizado): Feb24 PAGADA, últimas cuotas
-- =============================================================================

-- ── C1: AP-201 ──────────────────────────────────────────────────────────────
INSERT INTO CUOTAS_ARRIENDO (id_cuota, id_contrato, anio, mes, fecha_limite, valor_base, valor_mora, valor_total, estado, fecha_generacion, actualizado_en)
VALUES (SEC_CUOTAS_ARRIENDO.NEXTVAL,
        (SELECT id_contrato FROM CONTRATOS WHERE id_apartamento=(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='201') AND estado='ACTIVO'),
        2025, 2, DATE '2025-02-05', 1200000, 0, 1200000, 'PAGADA', SYSTIMESTAMP, SYSTIMESTAMP);

INSERT INTO CUOTAS_ARRIENDO (id_cuota, id_contrato, anio, mes, fecha_limite, valor_base, valor_mora, valor_total, estado, fecha_generacion, actualizado_en)
VALUES (SEC_CUOTAS_ARRIENDO.NEXTVAL,
        (SELECT id_contrato FROM CONTRATOS WHERE id_apartamento=(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='201') AND estado='ACTIVO'),
        2025, 3, DATE '2025-03-05', 1200000, 0, 1200000, 'PAGADA', SYSTIMESTAMP, SYSTIMESTAMP);

INSERT INTO CUOTAS_ARRIENDO (id_cuota, id_contrato, anio, mes, fecha_limite, valor_base, valor_mora, valor_total, estado, fecha_generacion, actualizado_en)
VALUES (SEC_CUOTAS_ARRIENDO.NEXTVAL,
        (SELECT id_contrato FROM CONTRATOS WHERE id_apartamento=(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='201') AND estado='ACTIVO'),
        2025, 4, DATE '2025-04-05', 1200000, 18000, 1218000, 'EN_MORA', SYSTIMESTAMP, SYSTIMESTAMP);

INSERT INTO CUOTAS_ARRIENDO (id_cuota, id_contrato, anio, mes, fecha_limite, valor_base, valor_mora, valor_total, estado, fecha_generacion, actualizado_en)
VALUES (SEC_CUOTAS_ARRIENDO.NEXTVAL,
        (SELECT id_contrato FROM CONTRATOS WHERE id_apartamento=(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='201') AND estado='ACTIVO'),
        2025, 5, DATE '2025-05-05', 1200000, 0, 1200000, 'PENDIENTE', SYSTIMESTAMP, SYSTIMESTAMP);

INSERT INTO CUOTAS_ARRIENDO (id_cuota, id_contrato, anio, mes, fecha_limite, valor_base, valor_mora, valor_total, estado, fecha_generacion, actualizado_en)
VALUES (SEC_CUOTAS_ARRIENDO.NEXTVAL,
        (SELECT id_contrato FROM CONTRATOS WHERE id_apartamento=(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='201') AND estado='ACTIVO'),
        2025, 6, DATE '2025-06-05', 1200000, 0, 1200000, 'PENDIENTE', SYSTIMESTAMP, SYSTIMESTAMP);

-- ── C2: AP-202 ──────────────────────────────────────────────────────────────
INSERT INTO CUOTAS_ARRIENDO (id_cuota, id_contrato, anio, mes, fecha_limite, valor_base, valor_mora, valor_total, estado, fecha_generacion, actualizado_en)
VALUES (SEC_CUOTAS_ARRIENDO.NEXTVAL,
        (SELECT id_contrato FROM CONTRATOS WHERE id_apartamento=(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='202') AND estado='ACTIVO'),
        2025, 2, DATE '2025-02-10', 1800000, 0, 1800000, 'PAGADA', SYSTIMESTAMP, SYSTIMESTAMP);

INSERT INTO CUOTAS_ARRIENDO (id_cuota, id_contrato, anio, mes, fecha_limite, valor_base, valor_mora, valor_total, estado, fecha_generacion, actualizado_en)
VALUES (SEC_CUOTAS_ARRIENDO.NEXTVAL,
        (SELECT id_contrato FROM CONTRATOS WHERE id_apartamento=(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='202') AND estado='ACTIVO'),
        2025, 3, DATE '2025-03-10', 1800000, 0, 1800000, 'VENCIDA', SYSTIMESTAMP, SYSTIMESTAMP);

INSERT INTO CUOTAS_ARRIENDO (id_cuota, id_contrato, anio, mes, fecha_limite, valor_base, valor_mora, valor_total, estado, fecha_generacion, actualizado_en)
VALUES (SEC_CUOTAS_ARRIENDO.NEXTVAL,
        (SELECT id_contrato FROM CONTRATOS WHERE id_apartamento=(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='202') AND estado='ACTIVO'),
        2025, 4, DATE '2025-04-10', 1800000, 54000, 1854000, 'EN_MORA', SYSTIMESTAMP, SYSTIMESTAMP);

INSERT INTO CUOTAS_ARRIENDO (id_cuota, id_contrato, anio, mes, fecha_limite, valor_base, valor_mora, valor_total, estado, fecha_generacion, actualizado_en)
VALUES (SEC_CUOTAS_ARRIENDO.NEXTVAL,
        (SELECT id_contrato FROM CONTRATOS WHERE id_apartamento=(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='202') AND estado='ACTIVO'),
        2025, 5, DATE '2025-05-10', 1800000, 0, 1800000, 'PENDIENTE', SYSTIMESTAMP, SYSTIMESTAMP);

INSERT INTO CUOTAS_ARRIENDO (id_cuota, id_contrato, anio, mes, fecha_limite, valor_base, valor_mora, valor_total, estado, fecha_generacion, actualizado_en)
VALUES (SEC_CUOTAS_ARRIENDO.NEXTVAL,
        (SELECT id_contrato FROM CONTRATOS WHERE id_apartamento=(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='202') AND estado='ACTIVO'),
        2025, 6, DATE '2025-06-10', 1800000, 0, 1800000, 'PENDIENTE', SYSTIMESTAMP, SYSTIMESTAMP);

-- ── C3: AP-301 (finalizado — últimas cuotas históricas) ─────────────────────
INSERT INTO CUOTAS_ARRIENDO (id_cuota, id_contrato, anio, mes, fecha_limite, valor_base, valor_mora, valor_total, estado, fecha_generacion, actualizado_en)
VALUES (SEC_CUOTAS_ARRIENDO.NEXTVAL,
        (SELECT id_contrato FROM CONTRATOS WHERE id_apartamento=(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='301') AND estado='FINALIZADO'),
        2024, 1, DATE '2024-01-05', 2200000, 0, 2200000, 'PAGADA', SYSTIMESTAMP, SYSTIMESTAMP);

INSERT INTO CUOTAS_ARRIENDO (id_cuota, id_contrato, anio, mes, fecha_limite, valor_base, valor_mora, valor_total, estado, fecha_generacion, actualizado_en)
VALUES (SEC_CUOTAS_ARRIENDO.NEXTVAL,
        (SELECT id_contrato FROM CONTRATOS WHERE id_apartamento=(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='301') AND estado='FINALIZADO'),
        2024, 2, DATE '2024-02-05', 2200000, 0, 2200000, 'PAGADA', SYSTIMESTAMP, SYSTIMESTAMP);

COMMIT;

-- =============================================================================
-- 10. PAGOS
-- =============================================================================
-- Pagos C1-AP201
INSERT INTO PAGOS (id_pago, id_cuota, fecha_pago, valor_pagado, metodo_pago, referencia, id_responsable, fecha_registro)
VALUES (SEC_PAGOS.NEXTVAL,
        (SELECT id_cuota FROM CUOTAS_ARRIENDO q JOIN CONTRATOS c ON q.id_contrato=c.id_contrato
         WHERE c.id_apartamento=(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='201') AND q.anio=2025 AND q.mes=2),
        DATE '2025-02-04', 1200000, 'TRANSFERENCIA', 'TXN-2025-020401',
        (SELECT id_usuario FROM USUARIOS WHERE username='admin_saed'), SYSTIMESTAMP);

INSERT INTO PAGOS (id_pago, id_cuota, fecha_pago, valor_pagado, metodo_pago, referencia, id_responsable, fecha_registro)
VALUES (SEC_PAGOS.NEXTVAL,
        (SELECT id_cuota FROM CUOTAS_ARRIENDO q JOIN CONTRATOS c ON q.id_contrato=c.id_contrato
         WHERE c.id_apartamento=(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='201') AND q.anio=2025 AND q.mes=3),
        DATE '2025-03-03', 1200000, 'TRANSFERENCIA', 'TXN-2025-030301',
        (SELECT id_usuario FROM USUARIOS WHERE username='admin_saed'), SYSTIMESTAMP);

-- Abono parcial cuota abril (EN_MORA) — pago tardío
INSERT INTO PAGOS (id_pago, id_cuota, fecha_pago, valor_pagado, metodo_pago, referencia, id_responsable, fecha_registro)
VALUES (SEC_PAGOS.NEXTVAL,
        (SELECT id_cuota FROM CUOTAS_ARRIENDO q JOIN CONTRATOS c ON q.id_contrato=c.id_contrato
         WHERE c.id_apartamento=(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='201') AND q.anio=2025 AND q.mes=4),
        DATE '2025-04-18', 600000, 'EFECTIVO', NULL,
        (SELECT id_usuario FROM USUARIOS WHERE username='admin_saed'), SYSTIMESTAMP);

-- Pagos C2-AP202
INSERT INTO PAGOS (id_pago, id_cuota, fecha_pago, valor_pagado, metodo_pago, referencia, id_responsable, fecha_registro)
VALUES (SEC_PAGOS.NEXTVAL,
        (SELECT id_cuota FROM CUOTAS_ARRIENDO q JOIN CONTRATOS c ON q.id_contrato=c.id_contrato
         WHERE c.id_apartamento=(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='202') AND q.anio=2025 AND q.mes=2),
        DATE '2025-02-09', 1800000, 'TARJETA', 'CARD-2025-0209',
        (SELECT id_usuario FROM USUARIOS WHERE username='admin_saed'), SYSTIMESTAMP);

-- Pagos C3-AP301 (históricos)
INSERT INTO PAGOS (id_pago, id_cuota, fecha_pago, valor_pagado, metodo_pago, referencia, id_responsable, fecha_registro)
VALUES (SEC_PAGOS.NEXTVAL,
        (SELECT id_cuota FROM CUOTAS_ARRIENDO q JOIN CONTRATOS c ON q.id_contrato=c.id_contrato
         WHERE c.id_apartamento=(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='301') AND q.anio=2024 AND q.mes=1),
        DATE '2024-01-04', 2200000, 'CHEQUE', 'CHQ-00123',
        (SELECT id_usuario FROM USUARIOS WHERE username='admin_saed'), SYSTIMESTAMP);

INSERT INTO PAGOS (id_pago, id_cuota, fecha_pago, valor_pagado, metodo_pago, referencia, id_responsable, fecha_registro)
VALUES (SEC_PAGOS.NEXTVAL,
        (SELECT id_cuota FROM CUOTAS_ARRIENDO q JOIN CONTRATOS c ON q.id_contrato=c.id_contrato
         WHERE c.id_apartamento=(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='301') AND q.anio=2024 AND q.mes=2),
        DATE '2024-02-06', 2200000, 'TRANSFERENCIA', 'TXN-2024-020601',
        (SELECT id_usuario FROM USUARIOS WHERE username='admin_saed'), SYSTIMESTAMP);

COMMIT;

-- =============================================================================
-- 11. VISITANTES  (frecuentes + ocasionales)
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
-- 12. VISITAS  (varios estados)
-- =============================================================================
-- V1: FINALIZADA — Carlos recibió a Laura (frecuente)
INSERT INTO VISITAS (id_visita, id_residente, id_visitante, id_contrato_res, tiempo_validez_min, cantidad_personas, estado, notas, fecha_registro, actualizado_en)
VALUES (SEC_VISITAS.NEXTVAL,
        (SELECT id_residente FROM RESIDENTES WHERE numero_documento='10456789'),
        (SELECT id_visitante FROM VISITANTES WHERE numero_documento='1098765432'),
        (SELECT id_contrato_res FROM CONTRATO_RESIDENTE cr JOIN CONTRATOS c ON cr.id_contrato=c.id_contrato
         WHERE cr.id_residente=(SELECT id_residente FROM RESIDENTES WHERE numero_documento='10456789') AND c.estado='ACTIVO' AND cr.rol_en_contrato='ARRENDATARIO'),
        30, 1, 'FINALIZADA', 'Visita de pareja frecuente', SYSTIMESTAMP - INTERVAL '2' DAY, SYSTIMESTAMP);

-- V2: ACTIVA — Carlos recibió a Roberto (en este momento dentro del edificio)
INSERT INTO VISITAS (id_visita, id_residente, id_visitante, id_contrato_res, tiempo_validez_min, cantidad_personas, estado, notas, fecha_registro, actualizado_en)
VALUES (SEC_VISITAS.NEXTVAL,
        (SELECT id_residente FROM RESIDENTES WHERE numero_documento='10456789'),
        (SELECT id_visitante FROM VISITANTES WHERE numero_documento='9876543210'),
        (SELECT id_contrato_res FROM CONTRATO_RESIDENTE cr JOIN CONTRATOS c ON cr.id_contrato=c.id_contrato
         WHERE cr.id_residente=(SELECT id_residente FROM RESIDENTES WHERE numero_documento='10456789') AND c.estado='ACTIVO' AND cr.rol_en_contrato='ARRENDATARIO'),
        60, 2, 'ACTIVA', 'Amigos de visita, vienen con carro', SYSTIMESTAMP - INTERVAL '1' HOUR, SYSTIMESTAMP);

-- V3: PENDIENTE — Jhon autorizó a Fernando (QR generado, no ha llegado)
INSERT INTO VISITAS (id_visita, id_residente, id_visitante, id_contrato_res, tiempo_validez_min, cantidad_personas, estado, notas, fecha_registro, actualizado_en)
VALUES (SEC_VISITAS.NEXTVAL,
        (SELECT id_residente FROM RESIDENTES WHERE numero_documento='80234567'),
        (SELECT id_visitante FROM VISITANTES WHERE numero_documento='19234567'),
        (SELECT id_contrato_res FROM CONTRATO_RESIDENTE cr JOIN CONTRATOS c ON cr.id_contrato=c.id_contrato
         WHERE cr.id_residente=(SELECT id_residente FROM RESIDENTES WHERE numero_documento='80234567') AND c.estado='ACTIVO' AND cr.rol_en_contrato='ARRENDATARIO'),
        30, 1, 'PENDIENTE', 'Técnico de internet', SYSTIMESTAMP - INTERVAL '30' MINUTE, SYSTIMESTAMP);

-- V4: CANCELADA — Jhon canceló visita de Emily
INSERT INTO VISITAS (id_visita, id_residente, id_visitante, id_contrato_res, tiempo_validez_min, cantidad_personas, estado, notas, fecha_registro, actualizado_en)
VALUES (SEC_VISITAS.NEXTVAL,
        (SELECT id_residente FROM RESIDENTES WHERE numero_documento='80234567'),
        (SELECT id_visitante FROM VISITANTES WHERE numero_documento='PAS-78901234'),
        (SELECT id_contrato_res FROM CONTRATO_RESIDENTE cr JOIN CONTRATOS c ON cr.id_contrato=c.id_contrato
         WHERE cr.id_residente=(SELECT id_residente FROM RESIDENTES WHERE numero_documento='80234567') AND c.estado='ACTIVO' AND cr.rol_en_contrato='ARRENDATARIO'),
        30, 1, 'CANCELADA', 'Canceló por cambio de planes', SYSTIMESTAMP - INTERVAL '5' DAY, SYSTIMESTAMP);

-- V5: EXPIRADA — QR generado pero nunca escaneado
INSERT INTO VISITAS (id_visita, id_residente, id_visitante, id_contrato_res, tiempo_validez_min, cantidad_personas, estado, notas, fecha_registro, actualizado_en)
VALUES (SEC_VISITAS.NEXTVAL,
        (SELECT id_residente FROM RESIDENTES WHERE numero_documento='10456789'),
        (SELECT id_visitante FROM VISITANTES WHERE numero_documento='CE-456789'),
        (SELECT id_contrato_res FROM CONTRATO_RESIDENTE cr JOIN CONTRATOS c ON cr.id_contrato=c.id_contrato
         WHERE cr.id_residente=(SELECT id_residente FROM RESIDENTES WHERE numero_documento='10456789') AND c.estado='ACTIVO' AND cr.rol_en_contrato='ARRENDATARIO'),
        15, 1, 'EXPIRADA', 'QR expiró sin usarse', SYSTIMESTAMP - INTERVAL '3' DAY, SYSTIMESTAMP);

COMMIT;

-- =============================================================================
-- 13. QR_ACCESOS
-- =============================================================================
-- QR visita V1 (FINALIZADA — ya usado)
INSERT INTO QR_ACCESOS (id_qr, id_visita, codigo_qr, fecha_generacion, fecha_expiracion, usado, fecha_uso, id_vigilante_uso)
VALUES (SEC_QR.NEXTVAL,
        (SELECT id_visita FROM VISITAS WHERE estado='FINALIZADA' AND ROWNUM=1),
        'QR-' || RAWTOHEX(SYS_GUID()),
        SYSTIMESTAMP - INTERVAL '2' DAY,
        SYSTIMESTAMP - INTERVAL '2' DAY + INTERVAL '30' MINUTE,
        1, SYSTIMESTAMP - INTERVAL '2' DAY + INTERVAL '10' MINUTE,
        (SELECT id_usuario FROM USUARIOS WHERE username='portero_dia'));

-- QR visita V2 (ACTIVA — usado al entrar, sin salida)
INSERT INTO QR_ACCESOS (id_qr, id_visita, codigo_qr, fecha_generacion, fecha_expiracion, usado, fecha_uso, id_vigilante_uso)
VALUES (SEC_QR.NEXTVAL,
        (SELECT id_visita FROM VISITAS WHERE estado='ACTIVA' AND ROWNUM=1),
        'QR-' || RAWTOHEX(SYS_GUID()),
        SYSTIMESTAMP - INTERVAL '1' HOUR,
        SYSTIMESTAMP - INTERVAL '1' HOUR + INTERVAL '60' MINUTE,
        1, SYSTIMESTAMP - INTERVAL '55' MINUTE,
        (SELECT id_usuario FROM USUARIOS WHERE username='portero_dia'));

-- QR visita V3 (PENDIENTE — generado, aún no usado)
INSERT INTO QR_ACCESOS (id_qr, id_visita, codigo_qr, fecha_generacion, fecha_expiracion, usado, fecha_uso, id_vigilante_uso)
VALUES (SEC_QR.NEXTVAL,
        (SELECT id_visita FROM VISITAS WHERE estado='PENDIENTE' AND ROWNUM=1),
        'QR-' || RAWTOHEX(SYS_GUID()),
        SYSTIMESTAMP - INTERVAL '30' MINUTE,
        SYSTIMESTAMP + INTERVAL '15' MINUTE,
        0, NULL, NULL);

-- QR visita V5 (EXPIRADA — nunca usado)
INSERT INTO QR_ACCESOS (id_qr, id_visita, codigo_qr, fecha_generacion, fecha_expiracion, usado, fecha_uso, id_vigilante_uso)
VALUES (SEC_QR.NEXTVAL,
        (SELECT id_visita FROM VISITAS WHERE estado='EXPIRADA' AND ROWNUM=1),
        'QR-' || RAWTOHEX(SYS_GUID()),
        SYSTIMESTAMP - INTERVAL '3' DAY,
        SYSTIMESTAMP - INTERVAL '3' DAY + INTERVAL '15' MINUTE,
        0, NULL, NULL);

COMMIT;

-- =============================================================================
-- 14. REGISTROS_ACCESO
-- =============================================================================
-- Acceso visita V1 (FINALIZADA: entrada y salida)
INSERT INTO REGISTROS_ACCESO (id_acceso, id_visita, id_vigilante, hora_entrada, hora_salida, observaciones, fecha_registro, actualizado_en)
VALUES (SEC_REG_ACCESO.NEXTVAL,
        (SELECT id_visita FROM VISITAS WHERE estado='FINALIZADA' AND ROWNUM=1),
        (SELECT id_usuario FROM USUARIOS WHERE username='portero_dia'),
        SYSTIMESTAMP - INTERVAL '2' DAY + INTERVAL '10' MINUTE,
        SYSTIMESTAMP - INTERVAL '2' DAY + INTERVAL '90' MINUTE,
        'Ingreso normal, verificado QR',
        SYSTIMESTAMP, SYSTIMESTAMP);

-- Acceso visita V2 (ACTIVA: entró pero no ha salido)
INSERT INTO REGISTROS_ACCESO (id_acceso, id_visita, id_vigilante, hora_entrada, hora_salida, observaciones, fecha_registro, actualizado_en)
VALUES (SEC_REG_ACCESO.NEXTVAL,
        (SELECT id_visita FROM VISITAS WHERE estado='ACTIVA' AND ROWNUM=1),
        (SELECT id_usuario FROM USUARIOS WHERE username='portero_dia'),
        SYSTIMESTAMP - INTERVAL '55' MINUTE,
        NULL,
        '2 personas, un vehículo, asignado V-02',
        SYSTIMESTAMP, SYSTIMESTAMP);

COMMIT;

-- =============================================================================
-- 15. VEHICULOS_VISITA
-- =============================================================================
-- Vehículo visita V2 (ACTIVA) — parqueadero V-02 asignado
INSERT INTO VEHICULOS_VISITA (id_vehiculo, id_visita, tipo, placa, descripcion, id_parqueadero, hora_salida)
VALUES (SEC_VEHICULOS.NEXTVAL,
        (SELECT id_visita FROM VISITAS WHERE estado='ACTIVA' AND ROWNUM=1),
        'CARRO', 'ABC-123', 'Toyota Corolla gris 2022',
        (SELECT id_parqueadero FROM PARQUEADEROS WHERE codigo='V-02'),
        NULL);

-- Vehículo visita V1 (FINALIZADA) — sin parqueadero
INSERT INTO VEHICULOS_VISITA (id_vehiculo, id_visita, tipo, placa, descripcion, id_parqueadero, hora_salida)
VALUES (SEC_VEHICULOS.NEXTVAL,
        (SELECT id_visita FROM VISITAS WHERE estado='FINALIZADA' AND ROWNUM=1),
        'MOTO', 'MOT-456', 'Yamaha FZ azul',
        NULL,
        SYSTIMESTAMP - INTERVAL '2' DAY + INTERVAL '90' MINUTE);

COMMIT;

-- =============================================================================
-- 16. BUZON
-- =============================================================================
-- Paquete AP-201 con foto
INSERT INTO BUZON (id_mensaje, id_apartamento, tipo, titulo, cuerpo, foto_captura, fecha_creacion, entregado)
VALUES (SEC_BUZON.NEXTVAL,
        (SELECT id_apartamento FROM APARTAMENTOS WHERE numero='201'),
        'PAQUETE', 'Paquete recibido',
        'Se recibió un paquete de Amazon en portería. Peso aproximado 2kg.',
        'https://saed.storage/buzón/pkg001.jpg',
        SYSTIMESTAMP - INTERVAL '3' HOUR, 0);

-- Paquete AP-202 ya entregado
INSERT INTO BUZON (id_mensaje, id_apartamento, tipo, titulo, cuerpo, foto_captura, fecha_creacion, entregado)
VALUES (SEC_BUZON.NEXTVAL,
        (SELECT id_apartamento FROM APARTAMENTOS WHERE numero='202'),
        'PAQUETE', 'Paquete entregado',
        'Paquete de Rappi entregado al residente el 20/05/2025.',
        NULL,
        SYSTIMESTAMP - INTERVAL '1' DAY, 1);

-- Aviso general (todos los apartamentos)
INSERT INTO BUZON (id_mensaje, id_apartamento, tipo, titulo, cuerpo, foto_captura, fecha_creacion, entregado)
VALUES (SEC_BUZON.NEXTVAL,
        (SELECT id_apartamento FROM APARTAMENTOS WHERE numero='201'),
        'AVISO', 'Suspensión de agua',
        'El día 25 de mayo de 2025 de 8am a 12pm habrá suspensión del servicio de agua por mantenimiento.',
        NULL, SYSTIMESTAMP - INTERVAL '12' HOUR, 0);

INSERT INTO BUZON (id_mensaje, id_apartamento, tipo, titulo, cuerpo, foto_captura, fecha_creacion, entregado)
VALUES (SEC_BUZON.NEXTVAL,
        (SELECT id_apartamento FROM APARTAMENTOS WHERE numero='202'),
        'AVISO', 'Suspensión de agua',
        'El día 25 de mayo de 2025 de 8am a 12pm habrá suspensión del servicio de agua por mantenimiento.',
        NULL, SYSTIMESTAMP - INTERVAL '12' HOUR, 0);

INSERT INTO BUZON (id_mensaje, id_apartamento, tipo, titulo, cuerpo, foto_captura, fecha_creacion, entregado)
VALUES (SEC_BUZON.NEXTVAL,
        (SELECT id_apartamento FROM APARTAMENTOS WHERE numero='301'),
        'AVISO', 'Suspensión de agua',
        'El día 25 de mayo de 2025 de 8am a 12pm habrá suspensión del servicio de agua por mantenimiento.',
        NULL, SYSTIMESTAMP - INTERVAL '12' HOUR, 0);

COMMIT;

-- =============================================================================
-- 17. MULTAS
-- =============================================================================
-- Multa RUIDO AP-201, PENDIENTE
INSERT INTO MULTAS (id_multa, id_apartamento, id_mensaje, tipo, monto, estado, fecha_creacion)
VALUES (SEC_MULTAS.NEXTVAL,
        (SELECT id_apartamento FROM APARTAMENTOS WHERE numero='201'),
        NULL, 'RUIDO', 100000, 'PENDIENTE', SYSTIMESTAMP - INTERVAL '5' DAY);

-- Multa PARQUEADERO AP-202, PAGADA
INSERT INTO MULTAS (id_multa, id_apartamento, id_mensaje, tipo, monto, estado, fecha_creacion)
VALUES (SEC_MULTAS.NEXTVAL,
        (SELECT id_apartamento FROM APARTAMENTOS WHERE numero='202'),
        NULL, 'PARQUEADERO', 50000, 'PAGADA', SYSTIMESTAMP - INTERVAL '10' DAY);

-- Multa RUIDO AP-202, ANULADA (apelación exitosa)
INSERT INTO MULTAS (id_multa, id_apartamento, id_mensaje, tipo, monto, estado, fecha_creacion)
VALUES (SEC_MULTAS.NEXTVAL,
        (SELECT id_apartamento FROM APARTAMENTOS WHERE numero='202'),
        NULL, 'RUIDO', 100000, 'ANULADA', SYSTIMESTAMP - INTERVAL '20' DAY);

-- Multa PARQUEADERO AP-201 vinculada al buzón
INSERT INTO MULTAS (id_multa, id_apartamento, id_mensaje, tipo, monto, estado, fecha_creacion)
VALUES (SEC_MULTAS.NEXTVAL,
        (SELECT id_apartamento FROM APARTAMENTOS WHERE numero='201'),
        (SELECT id_mensaje FROM BUZON WHERE id_apartamento=(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='201') AND tipo='PAQUETE' AND ROWNUM=1),
        'PARQUEADERO', 50000, 'PENDIENTE', SYSTIMESTAMP - INTERVAL '1' DAY);

COMMIT;

-- =============================================================================
-- 18. ALERTAS_PAGO
-- =============================================================================
-- Alerta PROXIMO_VENCIMIENTO cuota May-2025 AP-201 (3 días antes)
INSERT INTO ALERTAS_PAGO (id_alerta, id_cuota, tipo_alerta, canal, leida, enviada_en, leida_en)
VALUES (SEC_ALERTAS_PAGO.NEXTVAL,
        (SELECT id_cuota FROM CUOTAS_ARRIENDO q JOIN CONTRATOS c ON q.id_contrato=c.id_contrato
         WHERE c.id_apartamento=(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='201') AND q.anio=2025 AND q.mes=5),
        'PROXIMO_VENCIMIENTO', 'SISTEMA', 1,
        SYSTIMESTAMP - INTERVAL '3' DAY, SYSTIMESTAMP - INTERVAL '2' DAY);

-- Alerta PROXIMO_VENCIMIENTO cuota May-2025 AP-201 canal EMAIL
INSERT INTO ALERTAS_PAGO (id_alerta, id_cuota, tipo_alerta, canal, leida, enviada_en, leida_en)
VALUES (SEC_ALERTAS_PAGO.NEXTVAL,
        (SELECT id_cuota FROM CUOTAS_ARRIENDO q JOIN CONTRATOS c ON q.id_contrato=c.id_contrato
         WHERE c.id_apartamento=(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='201') AND q.anio=2025 AND q.mes=5),
        'PROXIMO_VENCIMIENTO', 'EMAIL', 0,
        SYSTIMESTAMP - INTERVAL '1' DAY, NULL);

-- Alerta VENCIDA cuota Mar-2025 AP-202
INSERT INTO ALERTAS_PAGO (id_alerta, id_cuota, tipo_alerta, canal, leida, enviada_en, leida_en)
VALUES (SEC_ALERTAS_PAGO.NEXTVAL,
        (SELECT id_cuota FROM CUOTAS_ARRIENDO q JOIN CONTRATOS c ON q.id_contrato=c.id_contrato
         WHERE c.id_apartamento=(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='202') AND q.anio=2025 AND q.mes=3),
        'VENCIDA', 'SISTEMA', 1,
        TIMESTAMP '2025-03-11 08:00:00', TIMESTAMP '2025-03-11 09:15:00');

INSERT INTO ALERTAS_PAGO (id_alerta, id_cuota, tipo_alerta, canal, leida, enviada_en, leida_en)
VALUES (SEC_ALERTAS_PAGO.NEXTVAL,
        (SELECT id_cuota FROM CUOTAS_ARRIENDO q JOIN CONTRATOS c ON q.id_contrato=c.id_contrato
         WHERE c.id_apartamento=(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='202') AND q.anio=2025 AND q.mes=3),
        'VENCIDA', 'WHATSAPP', 1,
        TIMESTAMP '2025-03-11 08:00:00', TIMESTAMP '2025-03-11 10:00:00');

-- Alerta EN_MORA cuota Abr-2025 AP-202
INSERT INTO ALERTAS_PAGO (id_alerta, id_cuota, tipo_alerta, canal, leida, enviada_en, leida_en)
VALUES (SEC_ALERTAS_PAGO.NEXTVAL,
        (SELECT id_cuota FROM CUOTAS_ARRIENDO q JOIN CONTRATOS c ON q.id_contrato=c.id_contrato
         WHERE c.id_apartamento=(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='202') AND q.anio=2025 AND q.mes=4),
        'EN_MORA', 'SISTEMA', 0,
        SYSTIMESTAMP - INTERVAL '5' DAY, NULL);

INSERT INTO ALERTAS_PAGO (id_alerta, id_cuota, tipo_alerta, canal, leida, enviada_en, leida_en)
VALUES (SEC_ALERTAS_PAGO.NEXTVAL,
        (SELECT id_cuota FROM CUOTAS_ARRIENDO q JOIN CONTRATOS c ON q.id_contrato=c.id_contrato
         WHERE c.id_apartamento=(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='202') AND q.anio=2025 AND q.mes=4),
        'EN_MORA', 'SMS', 0,
        SYSTIMESTAMP - INTERVAL '5' DAY, NULL);

-- Alerta EN_MORA cuota Abr-2025 AP-201
INSERT INTO ALERTAS_PAGO (id_alerta, id_cuota, tipo_alerta, canal, leida, enviada_en, leida_en)
VALUES (SEC_ALERTAS_PAGO.NEXTVAL,
        (SELECT id_cuota FROM CUOTAS_ARRIENDO q JOIN CONTRATOS c ON q.id_contrato=c.id_contrato
         WHERE c.id_apartamento=(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='201') AND q.anio=2025 AND q.mes=4),
        'EN_MORA', 'SISTEMA', 1,
        SYSTIMESTAMP - INTERVAL '10' DAY, SYSTIMESTAMP - INTERVAL '9' DAY);

COMMIT;

-- =============================================================================
-- VERIFICACIÓN RÁPIDA
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
SELECT 'QR_ACCESOS',      COUNT(*) FROM QR_ACCESOS      UNION ALL
SELECT 'REG_ACCESO',      COUNT(*) FROM REGISTROS_ACCESO UNION ALL
SELECT 'VEHICULOS',       COUNT(*) FROM VEHICULOS_VISITA UNION ALL
SELECT 'BUZON',           COUNT(*) FROM BUZON           UNION ALL
SELECT 'MULTAS',          COUNT(*) FROM MULTAS          UNION ALL
SELECT 'ALERTAS_PAGO',    COUNT(*) FROM ALERTAS_PAGO    UNION ALL
SELECT 'TUTORES',         COUNT(*) FROM TUTORES;

-- =============================================================================
-- FIN DEL SCRIPT
-- =============================================================================
