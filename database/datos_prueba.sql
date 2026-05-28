-- ╔══════════════════════════════════════════════════════════════════════╗
-- ║   SISTEMA DE ADMINISTRACIÓN RESIDENCIAL — EDIFICIO                   ║
-- ║   Datos de Prueba v5 — Compatible con modelo_relacional_v4.sql       ║
-- ╠══════════════════════════════════════════════════════════════════════╣
-- ║  IMPORTANTE: Ejecutar DESPUÉS del DDL (modelo_relacional_v4.sql)     ║
-- ║  Oracle 18c o superior · SQL Developer                               ║
-- ║                                                                      ║
-- ║  ESTRUCTURA DEL EDIFICIO                                             ║
-- ║  · Piso 1 : local comercial (no aplica)                              ║
-- ║  · Pisos 2-5 : 4 apartamentos por piso = 16 unidades                 ║
-- ║  · Piso 6 : no aplica (uso exclusivo técnico / azotea)               ║
-- ║  · Apt A (ej. 201): 3 hab / 2 baños  → tipo 3_HAB                   ║
-- ║  · Apts B, C, D   : 2 hab / 2 baños  → tipo 2_HAB                   ║
-- ║                                                                      ║
-- ║  CONTRASEÑAS DE PRUEBA POR ROL                                       ║
-- ║    ADMINISTRADOR : Admin2026!                                         ║
-- ║    PORTERO       : Portero2026!                                       ║
-- ║    RESIDENTE     : Residente2026!                                     ║
-- ║  Hashes BCrypt generados con jBCrypt 0.4, factor de costo 12.        ║
-- ╚══════════════════════════════════════════════════════════════════════╝
--
-- CAMBIOS RESPECTO A datos_prueba_v4.sql
-- ─────────────────────────────────────────────────────────────────────
--  Fix #1 (BUG): TUTORES — Carmen Inés Mendoza Ávila usaba el mismo
--          numero_documento ('1065432108') que Roberto Sánchez.
--          Corregido a '1065499099' (CC propia de la madre).
--  Fix #2 (cosmético): comentarios de conteo actualizados para reflejar
--          17 residentes (16 adultos + 1 menor) y 21 parqueaderos
--          (13 fijos + 4 VIS + 2 MV + 2 BV).
-- ─────────────────────────────────────────────────────────────────────
--
-- ┌──────────────────────────────────────────────────────────────────────┐
-- │  ÍNDICE DE CONTENIDO                                                  │
-- ├──────────────────────────────────────────────────────────────────────┤
-- │  §A   APARTAMENTOS (16 unidades: pisos 2-5)                          │
-- │  §B   PARQUEADEROS (13 fijos + 4 VIS + 2 MV + 2 BV = 21 total)     │
-- │  §C   RESIDENTES   (17 personas: 16 adultos + 1 menor)              │
-- │  §D   TUTORES      (1 tutor para el residente menor)                 │
-- │  §E   USUARIOS     (1 admin del DDL + 3 porteros + 12 residentes)   │
-- │  §F   CONTRATOS    (12 activos, 3 disponibles, 1 en mantenimiento)   │
-- │  §G   CONTRATO_RESIDENTE (vínculos residente-contrato)               │
-- │  §H   CUOTAS_ARRIENDO (3 meses por contrato activo)                  │
-- │  §I   PAGOS        (pagos de los meses PAGADOS)                      │
-- │  §J   ALERTAS_PAGO (muestras de notificaciones)                      │
-- │  §K   VISITANTES   (8 visitantes externos)                           │
-- │  §L   VISITAS + QR_ACCESOS + REGISTRO_VISITA + VEHICULOS_VISITA      │
-- │  §M   REGISTROS_ACCESO (entradas/salidas)                            │
-- │  §N   FRECUENTES_RESIDENTE                                           │
-- └──────────────────────────────────────────────────────────────────────┘

-- ══════════════════════════════════════════════════════════════════════
-- §A  APARTAMENTOS
-- Pisos 2, 3, 4, 5 — 4 unidades cada piso (A, B, C, D)
-- A = 3_HAB (72 m²) · B = 2_HAB (58 m²) · C = 2_HAB (58 m²) · D = 2_HAB (55 m²)
-- 12 contratos activos (pisos 2-4 completos) → OCUPADO
--  3 disponibles (piso 5: apts B, C, D)
--  1 en mantenimiento (501)
-- ══════════════════════════════════════════════════════════════════════

-- PISO 2
INSERT INTO APARTAMENTOS (numero, piso, tipo, area_m2, capacidad_maxima, estado)
VALUES ('201', 2, '3_HAB', 72.50, 5, 'OCUPADO');

INSERT INTO APARTAMENTOS (numero, piso, tipo, area_m2, capacidad_maxima, estado)
VALUES ('202', 2, '2_HAB', 58.00, 4, 'OCUPADO');

INSERT INTO APARTAMENTOS (numero, piso, tipo, area_m2, capacidad_maxima, estado)
VALUES ('203', 2, '2_HAB', 58.00, 4, 'OCUPADO');

INSERT INTO APARTAMENTOS (numero, piso, tipo, area_m2, capacidad_maxima, estado)
VALUES ('204', 2, '2_HAB', 55.00, 3, 'OCUPADO');

-- PISO 3
INSERT INTO APARTAMENTOS (numero, piso, tipo, area_m2, capacidad_maxima, estado)
VALUES ('301', 3, '3_HAB', 72.50, 5, 'OCUPADO');

INSERT INTO APARTAMENTOS (numero, piso, tipo, area_m2, capacidad_maxima, estado)
VALUES ('302', 3, '2_HAB', 58.00, 4, 'OCUPADO');

INSERT INTO APARTAMENTOS (numero, piso, tipo, area_m2, capacidad_maxima, estado)
VALUES ('303', 3, '2_HAB', 58.00, 4, 'OCUPADO');

INSERT INTO APARTAMENTOS (numero, piso, tipo, area_m2, capacidad_maxima, estado)
VALUES ('304', 3, '2_HAB', 55.00, 3, 'OCUPADO');

-- PISO 4
INSERT INTO APARTAMENTOS (numero, piso, tipo, area_m2, capacidad_maxima, estado)
VALUES ('401', 4, '3_HAB', 72.50, 5, 'OCUPADO');

INSERT INTO APARTAMENTOS (numero, piso, tipo, area_m2, capacidad_maxima, estado)
VALUES ('402', 4, '2_HAB', 58.00, 4, 'OCUPADO');

INSERT INTO APARTAMENTOS (numero, piso, tipo, area_m2, capacidad_maxima, estado)
VALUES ('403', 4, '2_HAB', 58.00, 4, 'OCUPADO');

INSERT INTO APARTAMENTOS (numero, piso, tipo, area_m2, capacidad_maxima, estado)
VALUES ('404', 4, '2_HAB', 55.00, 3, 'OCUPADO');

-- PISO 5
INSERT INTO APARTAMENTOS (numero, piso, tipo, area_m2, capacidad_maxima, estado)
VALUES ('501', 5, '3_HAB', 72.50, 5, 'EN_MANTENIMIENTO');

INSERT INTO APARTAMENTOS (numero, piso, tipo, area_m2, capacidad_maxima, estado)
VALUES ('502', 5, '2_HAB', 58.00, 4, 'DISPONIBLE');

INSERT INTO APARTAMENTOS (numero, piso, tipo, area_m2, capacidad_maxima, estado)
VALUES ('503', 5, '2_HAB', 58.00, 4, 'DISPONIBLE');

INSERT INTO APARTAMENTOS (numero, piso, tipo, area_m2, capacidad_maxima, estado)
VALUES ('504', 5, '2_HAB', 55.00, 3, 'DISPONIBLE');

COMMIT;

-- ══════════════════════════════════════════════════════════════════════
-- §B  PARQUEADEROS
-- Fijos (es_visitante=0): P-201 … P-404 (12 apts ocupados) + P-501 = 13
-- Rotativos visitantes  : VIS-01…VIS-04 (vehículo) = 4
--                         MV-01, MV-02  (moto)      = 2
--                         BV-01, BV-02  (bicicleta)  = 2
-- Total: 13 + 8 = 21 parqueaderos
-- Nota: UQ_PARQ_APARTAMENTO permite NULL múltiples (rotativos sin apt).
-- ══════════════════════════════════════════════════════════════════════

-- PARQUEADEROS FIJOS — piso 2
INSERT INTO PARQUEADEROS (codigo, tipo, estado, es_visitante, id_apartamento)
VALUES ('P-201', 'VEHICULO', 'OCUPADO', 0,
        (SELECT id_apartamento FROM APARTAMENTOS WHERE numero = '201'));

INSERT INTO PARQUEADEROS (codigo, tipo, estado, es_visitante, id_apartamento)
VALUES ('P-202', 'VEHICULO', 'DISPONIBLE', 0,
        (SELECT id_apartamento FROM APARTAMENTOS WHERE numero = '202'));

INSERT INTO PARQUEADEROS (codigo, tipo, estado, es_visitante, id_apartamento)
VALUES ('P-203', 'VEHICULO', 'DISPONIBLE', 0,
        (SELECT id_apartamento FROM APARTAMENTOS WHERE numero = '203'));

INSERT INTO PARQUEADEROS (codigo, tipo, estado, es_visitante, id_apartamento)
VALUES ('P-204', 'VEHICULO', 'DISPONIBLE', 0,
        (SELECT id_apartamento FROM APARTAMENTOS WHERE numero = '204'));

-- PARQUEADEROS FIJOS — piso 3
INSERT INTO PARQUEADEROS (codigo, tipo, estado, es_visitante, id_apartamento)
VALUES ('P-301', 'VEHICULO', 'DISPONIBLE', 0,
        (SELECT id_apartamento FROM APARTAMENTOS WHERE numero = '301'));

INSERT INTO PARQUEADEROS (codigo, tipo, estado, es_visitante, id_apartamento)
VALUES ('P-302', 'VEHICULO', 'DISPONIBLE', 0,
        (SELECT id_apartamento FROM APARTAMENTOS WHERE numero = '302'));

INSERT INTO PARQUEADEROS (codigo, tipo, estado, es_visitante, id_apartamento)
VALUES ('P-303', 'VEHICULO', 'DISPONIBLE', 0,
        (SELECT id_apartamento FROM APARTAMENTOS WHERE numero = '303'));

INSERT INTO PARQUEADEROS (codigo, tipo, estado, es_visitante, id_apartamento)
VALUES ('P-304', 'VEHICULO', 'DISPONIBLE', 0,
        (SELECT id_apartamento FROM APARTAMENTOS WHERE numero = '304'));

-- PARQUEADEROS FIJOS — piso 4
INSERT INTO PARQUEADEROS (codigo, tipo, estado, es_visitante, id_apartamento)
VALUES ('P-401', 'VEHICULO', 'DISPONIBLE', 0,
        (SELECT id_apartamento FROM APARTAMENTOS WHERE numero = '401'));

INSERT INTO PARQUEADEROS (codigo, tipo, estado, es_visitante, id_apartamento)
VALUES ('P-402', 'VEHICULO', 'DISPONIBLE', 0,
        (SELECT id_apartamento FROM APARTAMENTOS WHERE numero = '402'));

INSERT INTO PARQUEADEROS (codigo, tipo, estado, es_visitante, id_apartamento)
VALUES ('P-403', 'VEHICULO', 'DISPONIBLE', 0,
        (SELECT id_apartamento FROM APARTAMENTOS WHERE numero = '403'));

INSERT INTO PARQUEADEROS (codigo, tipo, estado, es_visitante, id_apartamento)
VALUES ('P-404', 'VEHICULO', 'DISPONIBLE', 0,
        (SELECT id_apartamento FROM APARTAMENTOS WHERE numero = '404'));

-- PARQUEADERO FIJO — apt 501 (en mantenimiento, sin ocupante)
INSERT INTO PARQUEADEROS (codigo, tipo, estado, es_visitante, id_apartamento)
VALUES ('P-501', 'VEHICULO', 'EN_MANTENIMIENTO', 0,
        (SELECT id_apartamento FROM APARTAMENTOS WHERE numero = '501'));

-- PARQUEADEROS ROTATIVOS PARA VISITANTES (id_apartamento NULL)
INSERT INTO PARQUEADEROS (codigo, tipo, estado, es_visitante, id_apartamento)
VALUES ('VIS-01', 'VEHICULO', 'DISPONIBLE', 1, NULL);

INSERT INTO PARQUEADEROS (codigo, tipo, estado, es_visitante, id_apartamento)
VALUES ('VIS-02', 'VEHICULO', 'DISPONIBLE', 1, NULL);

INSERT INTO PARQUEADEROS (codigo, tipo, estado, es_visitante, id_apartamento)
VALUES ('VIS-03', 'VEHICULO', 'DISPONIBLE', 1, NULL);

INSERT INTO PARQUEADEROS (codigo, tipo, estado, es_visitante, id_apartamento)
VALUES ('VIS-04', 'VEHICULO', 'DISPONIBLE', 1, NULL);

INSERT INTO PARQUEADEROS (codigo, tipo, estado, es_visitante, id_apartamento)
VALUES ('MV-01',  'MOTO',     'DISPONIBLE', 1, NULL);

INSERT INTO PARQUEADEROS (codigo, tipo, estado, es_visitante, id_apartamento)
VALUES ('MV-02',  'MOTO',     'DISPONIBLE', 1, NULL);

INSERT INTO PARQUEADEROS (codigo, tipo, estado, es_visitante, id_apartamento)
VALUES ('BV-01',  'BICICLETA','DISPONIBLE', 1, NULL);

INSERT INTO PARQUEADEROS (codigo, tipo, estado, es_visitante, id_apartamento)
VALUES ('BV-02',  'BICICLETA','DISPONIBLE', 1, NULL);

COMMIT;

-- ══════════════════════════════════════════════════════════════════════
-- §C  RESIDENTES
-- 12 contratos activos → 17 residentes totales (16 adultos + 1 menor).
--   Piso 2 : Carlos+Paola (201), Diana (202), José (203), Mafe (204)
--   Piso 3 : Andrés+Valentina (301), Roberto+Sofía (302), Luisa (303),
--            Juan (304)
--   Piso 4 : Camilo+Gloria (401), Natalia (402), Héctor+Jean Pierre (403),
--            Alejandra (404)
-- CC = id_tipo_doc con subquery desde catálogo.
-- ══════════════════════════════════════════════════════════════════════

-- ── PISO 2 ────────────────────────────────────────────────────────────

-- 201 — Carlos Andrés Martínez Ríos (arrendatario principal)
INSERT INTO RESIDENTES (id_tipo_doc, numero_documento, nombres, apellidos,
                        fecha_nacimiento, telefono, email, es_menor_edad)
VALUES ((SELECT id_tipo_doc FROM TIPOS_DOCUMENTO WHERE codigo = 'CC'),
        '1065432101', 'Carlos Andrés', 'Martínez Ríos',
        DATE '1985-03-14', '3001234501', 'camartinez@correo.co', 0);

-- 201 — Paola Andrea Gómez Nieto (cónyuge)
INSERT INTO RESIDENTES (id_tipo_doc, numero_documento, nombres, apellidos,
                        fecha_nacimiento, telefono, email, es_menor_edad)
VALUES ((SELECT id_tipo_doc FROM TIPOS_DOCUMENTO WHERE codigo = 'CC'),
        '1065432102', 'Paola Andrea', 'Gómez Nieto',
        DATE '1988-07-22', '3001234502', 'pagomez@correo.co', 0);

-- 202 — Diana Marcela Torres Vargas
INSERT INTO RESIDENTES (id_tipo_doc, numero_documento, nombres, apellidos,
                        fecha_nacimiento, telefono, email, es_menor_edad)
VALUES ((SELECT id_tipo_doc FROM TIPOS_DOCUMENTO WHERE codigo = 'CC'),
        '1065432103', 'Diana Marcela', 'Torres Vargas',
        DATE '1990-11-05', '3101234503', 'dmtorres@correo.co', 0);

-- 203 — José Luis Peña Suárez
INSERT INTO RESIDENTES (id_tipo_doc, numero_documento, nombres, apellidos,
                        fecha_nacimiento, telefono, email, es_menor_edad)
VALUES ((SELECT id_tipo_doc FROM TIPOS_DOCUMENTO WHERE codigo = 'CC'),
        '1065432104', 'José Luis', 'Peña Suárez',
        DATE '1978-06-18', '3201234504', 'jlpena@correo.co', 0);

-- 204 — María Fernanda Rojas Castro
INSERT INTO RESIDENTES (id_tipo_doc, numero_documento, nombres, apellidos,
                        fecha_nacimiento, telefono, email, es_menor_edad)
VALUES ((SELECT id_tipo_doc FROM TIPOS_DOCUMENTO WHERE codigo = 'CC'),
        '1065432105', 'María Fernanda', 'Rojas Castro',
        DATE '1995-02-28', '3101234505', 'mfrojas@correo.co', 0);

-- ── PISO 3 ────────────────────────────────────────────────────────────

-- 301 — Andrés Felipe Ospina Bedoya
INSERT INTO RESIDENTES (id_tipo_doc, numero_documento, nombres, apellidos,
                        fecha_nacimiento, telefono, email, es_menor_edad)
VALUES ((SELECT id_tipo_doc FROM TIPOS_DOCUMENTO WHERE codigo = 'CC'),
        '1065432106', 'Andrés Felipe', 'Ospina Bedoya',
        DATE '1982-09-30', '3001234506', 'afospina@correo.co', 0);

-- 301 — Valentina Ríos Morales (cónyuge)
INSERT INTO RESIDENTES (id_tipo_doc, numero_documento, nombres, apellidos,
                        fecha_nacimiento, telefono, email, es_menor_edad)
VALUES ((SELECT id_tipo_doc FROM TIPOS_DOCUMENTO WHERE codigo = 'CC'),
        '1065432107', 'Valentina', 'Ríos Morales',
        DATE '1984-12-01', '3001234507', 'vrios@correo.co', 0);

-- 302 — Roberto Emilio Sánchez Díaz (arrendatario)
INSERT INTO RESIDENTES (id_tipo_doc, numero_documento, nombres, apellidos,
                        fecha_nacimiento, telefono, email, es_menor_edad)
VALUES ((SELECT id_tipo_doc FROM TIPOS_DOCUMENTO WHERE codigo = 'CC'),
        '1065432108', 'Roberto Emilio', 'Sánchez Díaz',
        DATE '1975-04-10', '3151234508', 'resanchez@correo.co', 0);

-- 302 — Sofía Sánchez Mendoza (hija menor de edad — 14 años)
INSERT INTO RESIDENTES (id_tipo_doc, numero_documento, nombres, apellidos,
                        fecha_nacimiento, telefono, email, es_menor_edad)
VALUES ((SELECT id_tipo_doc FROM TIPOS_DOCUMENTO WHERE codigo = 'TI'),
        '1065499001', 'Sofía', 'Sánchez Mendoza',
        DATE '2011-08-15', NULL, NULL, 1);

-- 303 — Luisa Valentina Castro Herrera
INSERT INTO RESIDENTES (id_tipo_doc, numero_documento, nombres, apellidos,
                        fecha_nacimiento, telefono, email, es_menor_edad)
VALUES ((SELECT id_tipo_doc FROM TIPOS_DOCUMENTO WHERE codigo = 'CC'),
        '1065432109', 'Luisa Valentina', 'Castro Herrera',
        DATE '1992-05-17', '3201234509', 'lvcastro@correo.co', 0);

-- 304 — Juan Pablo Mora Quintero
INSERT INTO RESIDENTES (id_tipo_doc, numero_documento, nombres, apellidos,
                        fecha_nacimiento, telefono, email, es_menor_edad)
VALUES ((SELECT id_tipo_doc FROM TIPOS_DOCUMENTO WHERE codigo = 'CC'),
        '1065432110', 'Juan Pablo', 'Mora Quintero',
        DATE '1987-01-25', '3001234510', 'jpmora@correo.co', 0);

-- ── PISO 4 ────────────────────────────────────────────────────────────

-- 401 — Camilo Ernesto Vásquez López
INSERT INTO RESIDENTES (id_tipo_doc, numero_documento, nombres, apellidos,
                        fecha_nacimiento, telefono, email, es_menor_edad)
VALUES ((SELECT id_tipo_doc FROM TIPOS_DOCUMENTO WHERE codigo = 'CC'),
        '1065432111', 'Camilo Ernesto', 'Vásquez López',
        DATE '1980-07-04', '3101234511', 'cevazquez@correo.co', 0);

-- 401 — Gloria Patricia Flórez Ruiz (cónyuge)
INSERT INTO RESIDENTES (id_tipo_doc, numero_documento, nombres, apellidos,
                        fecha_nacimiento, telefono, email, es_menor_edad)
VALUES ((SELECT id_tipo_doc FROM TIPOS_DOCUMENTO WHERE codigo = 'CC'),
        '1065432112', 'Gloria Patricia', 'Flórez Ruiz',
        DATE '1983-11-19', '3101234512', 'gpflorez@correo.co', 0);

-- 402 — Natalia Johana Beltrán Prada
INSERT INTO RESIDENTES (id_tipo_doc, numero_documento, nombres, apellidos,
                        fecha_nacimiento, telefono, email, es_menor_edad)
VALUES ((SELECT id_tipo_doc FROM TIPOS_DOCUMENTO WHERE codigo = 'CC'),
        '1065432113', 'Natalia Johana', 'Beltrán Prada',
        DATE '1993-03-08', '3201234513', 'njbeltran@correo.co', 0);

-- 403 — Héctor Manuel Guerrero Navas
INSERT INTO RESIDENTES (id_tipo_doc, numero_documento, nombres, apellidos,
                        fecha_nacimiento, telefono, email, es_menor_edad)
VALUES ((SELECT id_tipo_doc FROM TIPOS_DOCUMENTO WHERE codigo = 'CC'),
        '1065432114', 'Héctor Manuel', 'Guerrero Navas',
        DATE '1970-10-31', '3001234514', 'hmguerrero@correo.co', 0);

-- 404 — Alejandra Milena Cardona Jiménez
INSERT INTO RESIDENTES (id_tipo_doc, numero_documento, nombres, apellidos,
                        fecha_nacimiento, telefono, email, es_menor_edad)
VALUES ((SELECT id_tipo_doc FROM TIPOS_DOCUMENTO WHERE codigo = 'CC'),
        '1065432115', 'Alejandra Milena', 'Cardona Jiménez',
        DATE '1997-06-12', '3151234515', 'amcardona@correo.co', 0);

-- 403 — Jean Pierre Dubois Leclair (codeudor extranjero, CE)
INSERT INTO RESIDENTES (id_tipo_doc, numero_documento, nombres, apellidos,
                        fecha_nacimiento, telefono, email, es_menor_edad)
VALUES ((SELECT id_tipo_doc FROM TIPOS_DOCUMENTO WHERE codigo = 'CE'),
        'CE-987654', 'Jean Pierre', 'Dubois Leclair',
        DATE '1985-02-14', '3001234516', 'jp.dubois@correo.co', 0);

COMMIT;

-- ══════════════════════════════════════════════════════════════════════
-- §D  TUTORES
-- Tutora de Sofía Sánchez Mendoza (residente menor en apto 302).
-- FIX v5: Carmen Inés usa su propio documento (1065499099), no el de
--         Roberto Sánchez (1065432108). La restricción UQ_TUTOR_DOC
--         (id_tipo_doc, numero_documento) es solo dentro de TUTORES,
--         pero asignar el CC ajeno es lógicamente incorrecto.
-- ══════════════════════════════════════════════════════════════════════

INSERT INTO TUTORES (id_residente_menor, id_tipo_doc, numero_documento,
                     nombres, apellidos, telefono, email, parentesco)
VALUES (
    (SELECT id_residente FROM RESIDENTES WHERE numero_documento = '1065499001'),
    (SELECT id_tipo_doc  FROM TIPOS_DOCUMENTO WHERE codigo = 'CC'),
    '1065499099',   -- CC propia de Carmen Inés Mendoza Ávila (madre)
    'Carmen Inés', 'Mendoza Ávila',
    '3151234599', 'cmendoza@correo.co',
    'MADRE'
);

COMMIT;

-- ══════════════════════════════════════════════════════════════════════
-- §E  USUARIOS
-- admin (id 1) ya creado en el DDL (§10) con placeholder; el UPDATE
-- de abajo lo reemplaza por el hash correcto para pruebas.
-- 3 porteros + 1 usuario RESIDENTE por cada arrendatario principal.
-- rol: 'ADMINISTRADOR' | 'PORTERO' | 'RESIDENTE'  (CHK_USR_ROL)
-- username: siempre minúsculas, mínimo 4 chars    (CHK_USR_USERNAME*)
--
-- CONTRASEÑAS DE PRUEBA (hashes BCrypt factor 12, jBCrypt 0.4):
--   ADMINISTRADOR  → Admin2026!
--     hash: $2a$12$7/e3JZhNQGXv949JtyHHBOojiprheNCb/FuOgdAA0vjKbFbHHsHpC
--   PORTERO        → Portero2026!
--     hash: $2a$12$f6gcgrlYG4gJZQE46EZhWuBa11/zLanfjzZBgB3Q1kjHDUEibX3oe
--   RESIDENTE      → Residente2026!
--     hash: $2a$12$9JNxP4mOySb1eMF6d3EMkecPrIBhulHtWIxzNU/XGBHtycWgbCOBS
-- ══════════════════════════════════════════════════════════════════════

-- Actualizar contraseña del admin creado en el DDL
UPDATE USUARIOS
   SET password_hash = '$2a$12$7/e3JZhNQGXv949JtyHHBOojiprheNCb/FuOgdAA0vjKbFbHHsHpC'
 WHERE username = 'admin';

-- PORTEROS
INSERT INTO USUARIOS (id_residente, username, password_hash, rol)
VALUES (NULL, 'portero01',
        '$2a$12$f6gcgrlYG4gJZQE46EZhWuBa11/zLanfjzZBgB3Q1kjHDUEibX3oe', 'PORTERO');

INSERT INTO USUARIOS (id_residente, username, password_hash, rol)
VALUES (NULL, 'portero02',
        '$2a$12$f6gcgrlYG4gJZQE46EZhWuBa11/zLanfjzZBgB3Q1kjHDUEibX3oe', 'PORTERO');

INSERT INTO USUARIOS (id_residente, username, password_hash, rol)
VALUES (NULL, 'portero03',
        '$2a$12$f6gcgrlYG4gJZQE46EZhWuBa11/zLanfjzZBgB3Q1kjHDUEibX3oe', 'PORTERO');

-- RESIDENTES (uno por arrendatario principal de cada contrato)
INSERT INTO USUARIOS (id_residente, username, password_hash, rol)
VALUES ((SELECT id_residente FROM RESIDENTES WHERE numero_documento = '1065432101'),
        'camartinez', '$2a$12$9JNxP4mOySb1eMF6d3EMkecPrIBhulHtWIxzNU/XGBHtycWgbCOBS', 'RESIDENTE');

INSERT INTO USUARIOS (id_residente, username, password_hash, rol)
VALUES ((SELECT id_residente FROM RESIDENTES WHERE numero_documento = '1065432103'),
        'dmtorres',   '$2a$12$9JNxP4mOySb1eMF6d3EMkecPrIBhulHtWIxzNU/XGBHtycWgbCOBS', 'RESIDENTE');

INSERT INTO USUARIOS (id_residente, username, password_hash, rol)
VALUES ((SELECT id_residente FROM RESIDENTES WHERE numero_documento = '1065432104'),
        'jlpena',     '$2a$12$9JNxP4mOySb1eMF6d3EMkecPrIBhulHtWIxzNU/XGBHtycWgbCOBS', 'RESIDENTE');

INSERT INTO USUARIOS (id_residente, username, password_hash, rol)
VALUES ((SELECT id_residente FROM RESIDENTES WHERE numero_documento = '1065432105'),
        'mfrojas',    '$2a$12$9JNxP4mOySb1eMF6d3EMkecPrIBhulHtWIxzNU/XGBHtycWgbCOBS', 'RESIDENTE');

INSERT INTO USUARIOS (id_residente, username, password_hash, rol)
VALUES ((SELECT id_residente FROM RESIDENTES WHERE numero_documento = '1065432106'),
        'afospina',   '$2a$12$9JNxP4mOySb1eMF6d3EMkecPrIBhulHtWIxzNU/XGBHtycWgbCOBS', 'RESIDENTE');

INSERT INTO USUARIOS (id_residente, username, password_hash, rol)
VALUES ((SELECT id_residente FROM RESIDENTES WHERE numero_documento = '1065432108'),
        'resanchez',  '$2a$12$9JNxP4mOySb1eMF6d3EMkecPrIBhulHtWIxzNU/XGBHtycWgbCOBS', 'RESIDENTE');

INSERT INTO USUARIOS (id_residente, username, password_hash, rol)
VALUES ((SELECT id_residente FROM RESIDENTES WHERE numero_documento = '1065432109'),
        'lvcastro',   '$2a$12$9JNxP4mOySb1eMF6d3EMkecPrIBhulHtWIxzNU/XGBHtycWgbCOBS', 'RESIDENTE');

INSERT INTO USUARIOS (id_residente, username, password_hash, rol)
VALUES ((SELECT id_residente FROM RESIDENTES WHERE numero_documento = '1065432110'),
        'jpmora',     '$2a$12$9JNxP4mOySb1eMF6d3EMkecPrIBhulHtWIxzNU/XGBHtycWgbCOBS', 'RESIDENTE');

INSERT INTO USUARIOS (id_residente, username, password_hash, rol)
VALUES ((SELECT id_residente FROM RESIDENTES WHERE numero_documento = '1065432111'),
        'cevazquez',  '$2a$12$9JNxP4mOySb1eMF6d3EMkecPrIBhulHtWIxzNU/XGBHtycWgbCOBS', 'RESIDENTE');

INSERT INTO USUARIOS (id_residente, username, password_hash, rol)
VALUES ((SELECT id_residente FROM RESIDENTES WHERE numero_documento = '1065432113'),
        'njbeltran',  '$2a$12$9JNxP4mOySb1eMF6d3EMkecPrIBhulHtWIxzNU/XGBHtycWgbCOBS', 'RESIDENTE');

INSERT INTO USUARIOS (id_residente, username, password_hash, rol)
VALUES ((SELECT id_residente FROM RESIDENTES WHERE numero_documento = '1065432114'),
        'hmguerrero', '$2a$12$9JNxP4mOySb1eMF6d3EMkecPrIBhulHtWIxzNU/XGBHtycWgbCOBS', 'RESIDENTE');

INSERT INTO USUARIOS (id_residente, username, password_hash, rol)
VALUES ((SELECT id_residente FROM RESIDENTES WHERE numero_documento = '1065432115'),
        'amcardona',  '$2a$12$9JNxP4mOySb1eMF6d3EMkecPrIBhulHtWIxzNU/XGBHtycWgbCOBS', 'RESIDENTE');

COMMIT;

-- ══════════════════════════════════════════════════════════════════════
-- §F  CONTRATOS
-- 12 contratos ACTIVOS (pisos 2-4), 1 EN_MANTENIMIENTO (apt 501 implícito
-- por estado del apartamento), 3 DISPONIBLES (502, 503, 504 sin contrato).
-- estado IN ('PENDIENTE_FIRMA','ACTIVO','VENCIDO','CANCELADO','SUSPENDIDO')
-- UIX_CONT_APT_ACTIVO garantiza 1 ACTIVO por apartamento.
-- id_registrado_por → admin (id 1 creado en el DDL §10).
-- ══════════════════════════════════════════════════════════════════════

-- CONTRATOS ACTIVOS — PISO 2
INSERT INTO CONTRATOS (id_apartamento, id_tutor, id_registrado_por,
                       fecha_inicio, fecha_fin, valor_mensual,
                       dia_pago, dias_gracia, porcentaje_mora, estado)
VALUES ((SELECT id_apartamento FROM APARTAMENTOS WHERE numero = '201'),
        NULL,
        (SELECT id_usuario FROM USUARIOS WHERE username = 'admin'),
        DATE '2024-01-01', NULL, 950000, 5, 5, 1.5, 'ACTIVO');

INSERT INTO CONTRATOS (id_apartamento, id_tutor, id_registrado_por,
                       fecha_inicio, fecha_fin, valor_mensual,
                       dia_pago, dias_gracia, porcentaje_mora, estado)
VALUES ((SELECT id_apartamento FROM APARTAMENTOS WHERE numero = '202'),
        NULL,
        (SELECT id_usuario FROM USUARIOS WHERE username = 'admin'),
        DATE '2024-02-01', NULL, 750000, 5, 5, 1.5, 'ACTIVO');

INSERT INTO CONTRATOS (id_apartamento, id_tutor, id_registrado_por,
                       fecha_inicio, fecha_fin, valor_mensual,
                       dia_pago, dias_gracia, porcentaje_mora, estado)
VALUES ((SELECT id_apartamento FROM APARTAMENTOS WHERE numero = '203'),
        NULL,
        (SELECT id_usuario FROM USUARIOS WHERE username = 'admin'),
        DATE '2024-03-01', NULL, 750000, 5, 5, 1.5, 'ACTIVO');

INSERT INTO CONTRATOS (id_apartamento, id_tutor, id_registrado_por,
                       fecha_inicio, fecha_fin, valor_mensual,
                       dia_pago, dias_gracia, porcentaje_mora, estado)
VALUES ((SELECT id_apartamento FROM APARTAMENTOS WHERE numero = '204'),
        NULL,
        (SELECT id_usuario FROM USUARIOS WHERE username = 'admin'),
        DATE '2024-03-15', NULL, 700000, 5, 5, 1.5, 'ACTIVO');

-- CONTRATOS ACTIVOS — PISO 3
INSERT INTO CONTRATOS (id_apartamento, id_tutor, id_registrado_por,
                       fecha_inicio, fecha_fin, valor_mensual,
                       dia_pago, dias_gracia, porcentaje_mora, estado)
VALUES ((SELECT id_apartamento FROM APARTAMENTOS WHERE numero = '301'),
        NULL,
        (SELECT id_usuario FROM USUARIOS WHERE username = 'admin'),
        DATE '2023-07-01', NULL, 950000, 5, 3, 1.5, 'ACTIVO');

-- Contrato 302 referencia al tutor de Sofía (menor de edad)
INSERT INTO CONTRATOS (id_apartamento, id_tutor, id_registrado_por,
                       fecha_inicio, fecha_fin, valor_mensual,
                       dia_pago, dias_gracia, porcentaje_mora, estado)
VALUES ((SELECT id_apartamento FROM APARTAMENTOS WHERE numero = '302'),
        (SELECT id_tutor FROM TUTORES
          WHERE id_residente_menor =
                (SELECT id_residente FROM RESIDENTES WHERE numero_documento = '1065499001')),
        (SELECT id_usuario FROM USUARIOS WHERE username = 'admin'),
        DATE '2023-08-01', NULL, 750000, 5, 5, 1.5, 'ACTIVO');

INSERT INTO CONTRATOS (id_apartamento, id_tutor, id_registrado_por,
                       fecha_inicio, fecha_fin, valor_mensual,
                       dia_pago, dias_gracia, porcentaje_mora, estado)
VALUES ((SELECT id_apartamento FROM APARTAMENTOS WHERE numero = '303'),
        NULL,
        (SELECT id_usuario FROM USUARIOS WHERE username = 'admin'),
        DATE '2024-01-15', NULL, 750000, 5, 5, 1.5, 'ACTIVO');

INSERT INTO CONTRATOS (id_apartamento, id_tutor, id_registrado_por,
                       fecha_inicio, fecha_fin, valor_mensual,
                       dia_pago, dias_gracia, porcentaje_mora, estado)
VALUES ((SELECT id_apartamento FROM APARTAMENTOS WHERE numero = '304'),
        NULL,
        (SELECT id_usuario FROM USUARIOS WHERE username = 'admin'),
        DATE '2024-04-01', NULL, 700000, 5, 5, 1.5, 'ACTIVO');

-- CONTRATOS ACTIVOS — PISO 4
INSERT INTO CONTRATOS (id_apartamento, id_tutor, id_registrado_por,
                       fecha_inicio, fecha_fin, valor_mensual,
                       dia_pago, dias_gracia, porcentaje_mora, estado)
VALUES ((SELECT id_apartamento FROM APARTAMENTOS WHERE numero = '401'),
        NULL,
        (SELECT id_usuario FROM USUARIOS WHERE username = 'admin'),
        DATE '2023-05-01', NULL, 950000, 5, 3, 1.5, 'ACTIVO');

INSERT INTO CONTRATOS (id_apartamento, id_tutor, id_registrado_por,
                       fecha_inicio, fecha_fin, valor_mensual,
                       dia_pago, dias_gracia, porcentaje_mora, estado)
VALUES ((SELECT id_apartamento FROM APARTAMENTOS WHERE numero = '402'),
        NULL,
        (SELECT id_usuario FROM USUARIOS WHERE username = 'admin'),
        DATE '2024-05-01', NULL, 750000, 5, 5, 1.5, 'ACTIVO');

INSERT INTO CONTRATOS (id_apartamento, id_tutor, id_registrado_por,
                       fecha_inicio, fecha_fin, valor_mensual,
                       dia_pago, dias_gracia, porcentaje_mora, estado)
VALUES ((SELECT id_apartamento FROM APARTAMENTOS WHERE numero = '403'),
        NULL,
        (SELECT id_usuario FROM USUARIOS WHERE username = 'admin'),
        DATE '2024-06-01', NULL, 750000, 5, 5, 1.5, 'ACTIVO');

INSERT INTO CONTRATOS (id_apartamento, id_tutor, id_registrado_por,
                       fecha_inicio, fecha_fin, valor_mensual,
                       dia_pago, dias_gracia, porcentaje_mora, estado)
VALUES ((SELECT id_apartamento FROM APARTAMENTOS WHERE numero = '404'),
        NULL,
        (SELECT id_usuario FROM USUARIOS WHERE username = 'admin'),
        DATE '2024-07-01', NULL, 700000, 5, 5, 1.5, 'ACTIVO');

COMMIT;

-- ══════════════════════════════════════════════════════════════════════
-- §G  CONTRATO_RESIDENTE
-- rol_en_contrato IN ('ARRENDATARIO','CODEUDOR','RESIDENTE_MENOR','OTRO')
-- Apts 201, 301, 401: arrendatario + cónyuge (OTRO)
-- Apt 302: arrendatario + menor (RESIDENTE_MENOR)
-- Apt 403: arrendatario + codeudor extranjero (CODEUDOR)
-- Resto: solo arrendatario
-- Nota: subquery sin filtro de estado es segura porque cada apartamento
--       tiene exactamente un contrato en este dataset.
-- ══════════════════════════════════════════════════════════════════════

-- 201: Carlos Martínez (arrendatario) + Paola Gómez (cónyuge → OTRO)
INSERT INTO CONTRATO_RESIDENTE (id_contrato, id_residente, rol_en_contrato)
VALUES ((SELECT id_contrato FROM CONTRATOS WHERE id_apartamento =
            (SELECT id_apartamento FROM APARTAMENTOS WHERE numero = '201')),
        (SELECT id_residente FROM RESIDENTES WHERE numero_documento = '1065432101'),
        'ARRENDATARIO');

INSERT INTO CONTRATO_RESIDENTE (id_contrato, id_residente, rol_en_contrato)
VALUES ((SELECT id_contrato FROM CONTRATOS WHERE id_apartamento =
            (SELECT id_apartamento FROM APARTAMENTOS WHERE numero = '201')),
        (SELECT id_residente FROM RESIDENTES WHERE numero_documento = '1065432102'),
        'OTRO');

-- 202: Diana Torres
INSERT INTO CONTRATO_RESIDENTE (id_contrato, id_residente, rol_en_contrato)
VALUES ((SELECT id_contrato FROM CONTRATOS WHERE id_apartamento =
            (SELECT id_apartamento FROM APARTAMENTOS WHERE numero = '202')),
        (SELECT id_residente FROM RESIDENTES WHERE numero_documento = '1065432103'),
        'ARRENDATARIO');

-- 203: José Luis Peña
INSERT INTO CONTRATO_RESIDENTE (id_contrato, id_residente, rol_en_contrato)
VALUES ((SELECT id_contrato FROM CONTRATOS WHERE id_apartamento =
            (SELECT id_apartamento FROM APARTAMENTOS WHERE numero = '203')),
        (SELECT id_residente FROM RESIDENTES WHERE numero_documento = '1065432104'),
        'ARRENDATARIO');

-- 204: María Fernanda Rojas
INSERT INTO CONTRATO_RESIDENTE (id_contrato, id_residente, rol_en_contrato)
VALUES ((SELECT id_contrato FROM CONTRATOS WHERE id_apartamento =
            (SELECT id_apartamento FROM APARTAMENTOS WHERE numero = '204')),
        (SELECT id_residente FROM RESIDENTES WHERE numero_documento = '1065432105'),
        'ARRENDATARIO');

-- 301: Andrés Ospina (arrendatario) + Valentina Ríos (cónyuge → OTRO)
INSERT INTO CONTRATO_RESIDENTE (id_contrato, id_residente, rol_en_contrato)
VALUES ((SELECT id_contrato FROM CONTRATOS WHERE id_apartamento =
            (SELECT id_apartamento FROM APARTAMENTOS WHERE numero = '301')),
        (SELECT id_residente FROM RESIDENTES WHERE numero_documento = '1065432106'),
        'ARRENDATARIO');

INSERT INTO CONTRATO_RESIDENTE (id_contrato, id_residente, rol_en_contrato)
VALUES ((SELECT id_contrato FROM CONTRATOS WHERE id_apartamento =
            (SELECT id_apartamento FROM APARTAMENTOS WHERE numero = '301')),
        (SELECT id_residente FROM RESIDENTES WHERE numero_documento = '1065432107'),
        'OTRO');

-- 302: Roberto Sánchez (arrendatario) + Sofía (menor → RESIDENTE_MENOR)
INSERT INTO CONTRATO_RESIDENTE (id_contrato, id_residente, rol_en_contrato)
VALUES ((SELECT id_contrato FROM CONTRATOS WHERE id_apartamento =
            (SELECT id_apartamento FROM APARTAMENTOS WHERE numero = '302')),
        (SELECT id_residente FROM RESIDENTES WHERE numero_documento = '1065432108'),
        'ARRENDATARIO');

INSERT INTO CONTRATO_RESIDENTE (id_contrato, id_residente, rol_en_contrato)
VALUES ((SELECT id_contrato FROM CONTRATOS WHERE id_apartamento =
            (SELECT id_apartamento FROM APARTAMENTOS WHERE numero = '302')),
        (SELECT id_residente FROM RESIDENTES WHERE numero_documento = '1065499001'),
        'RESIDENTE_MENOR');

-- 303: Luisa Castro
INSERT INTO CONTRATO_RESIDENTE (id_contrato, id_residente, rol_en_contrato)
VALUES ((SELECT id_contrato FROM CONTRATOS WHERE id_apartamento =
            (SELECT id_apartamento FROM APARTAMENTOS WHERE numero = '303')),
        (SELECT id_residente FROM RESIDENTES WHERE numero_documento = '1065432109'),
        'ARRENDATARIO');

-- 304: Juan Mora
INSERT INTO CONTRATO_RESIDENTE (id_contrato, id_residente, rol_en_contrato)
VALUES ((SELECT id_contrato FROM CONTRATOS WHERE id_apartamento =
            (SELECT id_apartamento FROM APARTAMENTOS WHERE numero = '304')),
        (SELECT id_residente FROM RESIDENTES WHERE numero_documento = '1065432110'),
        'ARRENDATARIO');

-- 401: Camilo Vásquez (arrendatario) + Gloria Flórez (cónyuge → OTRO)
INSERT INTO CONTRATO_RESIDENTE (id_contrato, id_residente, rol_en_contrato)
VALUES ((SELECT id_contrato FROM CONTRATOS WHERE id_apartamento =
            (SELECT id_apartamento FROM APARTAMENTOS WHERE numero = '401')),
        (SELECT id_residente FROM RESIDENTES WHERE numero_documento = '1065432111'),
        'ARRENDATARIO');

INSERT INTO CONTRATO_RESIDENTE (id_contrato, id_residente, rol_en_contrato)
VALUES ((SELECT id_contrato FROM CONTRATOS WHERE id_apartamento =
            (SELECT id_apartamento FROM APARTAMENTOS WHERE numero = '401')),
        (SELECT id_residente FROM RESIDENTES WHERE numero_documento = '1065432112'),
        'OTRO');

-- 402: Natalia Beltrán
INSERT INTO CONTRATO_RESIDENTE (id_contrato, id_residente, rol_en_contrato)
VALUES ((SELECT id_contrato FROM CONTRATOS WHERE id_apartamento =
            (SELECT id_apartamento FROM APARTAMENTOS WHERE numero = '402')),
        (SELECT id_residente FROM RESIDENTES WHERE numero_documento = '1065432113'),
        'ARRENDATARIO');

-- 403: Héctor Guerrero (arrendatario) + Jean Pierre Dubois (codeudor)
INSERT INTO CONTRATO_RESIDENTE (id_contrato, id_residente, rol_en_contrato)
VALUES ((SELECT id_contrato FROM CONTRATOS WHERE id_apartamento =
            (SELECT id_apartamento FROM APARTAMENTOS WHERE numero = '403')),
        (SELECT id_residente FROM RESIDENTES WHERE numero_documento = '1065432114'),
        'ARRENDATARIO');

INSERT INTO CONTRATO_RESIDENTE (id_contrato, id_residente, rol_en_contrato)
VALUES ((SELECT id_contrato FROM CONTRATOS WHERE id_apartamento =
            (SELECT id_apartamento FROM APARTAMENTOS WHERE numero = '403')),
        (SELECT id_residente FROM RESIDENTES WHERE numero_documento = 'CE-987654'),
        'CODEUDOR');

-- 404: Alejandra Cardona
INSERT INTO CONTRATO_RESIDENTE (id_contrato, id_residente, rol_en_contrato)
VALUES ((SELECT id_contrato FROM CONTRATOS WHERE id_apartamento =
            (SELECT id_apartamento FROM APARTAMENTOS WHERE numero = '404')),
        (SELECT id_residente FROM RESIDENTES WHERE numero_documento = '1065432115'),
        'ARRENDATARIO');

COMMIT;

-- ══════════════════════════════════════════════════════════════════════
-- §H  CUOTAS_ARRIENDO
-- 3 meses por cada uno de los 12 contratos activos = 36 cuotas.
-- Marzo 2025 → PAGADA · Abril 2025 → PAGADA · Mayo 2025 → PENDIENTE
-- TRG_CUOTAS_UPD (BEFORE INSERT OR UPDATE) recalcula valor_total
-- automáticamente como valor_base + valor_mora, por lo que el valor
-- explícito de valor_total en cada VALUES es redundante pero válido
-- (0 + valor_mensual = valor_mensual satisface CHK_CUOTA_TOTAL).
-- ══════════════════════════════════════════════════════════════════════

BEGIN
    FOR c IN (
        SELECT co.id_contrato, co.valor_mensual, co.dia_pago,
               ap.numero AS apt
          FROM CONTRATOS co
          JOIN APARTAMENTOS ap ON ap.id_apartamento = co.id_apartamento
         WHERE co.estado = 'ACTIVO'
    ) LOOP
        -- Marzo 2025
        INSERT INTO CUOTAS_ARRIENDO (id_contrato, anio, mes, fecha_limite,
                                     valor_base, valor_mora, valor_total, estado)
        VALUES (c.id_contrato, 2025, 3,
                TO_DATE('2025-03-' || LPAD(c.dia_pago, 2, '0'), 'YYYY-MM-DD'),
                c.valor_mensual, 0, c.valor_mensual, 'PAGADA');

        -- Abril 2025
        INSERT INTO CUOTAS_ARRIENDO (id_contrato, anio, mes, fecha_limite,
                                     valor_base, valor_mora, valor_total, estado)
        VALUES (c.id_contrato, 2025, 4,
                TO_DATE('2025-04-' || LPAD(c.dia_pago, 2, '0'), 'YYYY-MM-DD'),
                c.valor_mensual, 0, c.valor_mensual, 'PAGADA');

        -- Mayo 2025
        INSERT INTO CUOTAS_ARRIENDO (id_contrato, anio, mes, fecha_limite,
                                     valor_base, valor_mora, valor_total, estado)
        VALUES (c.id_contrato, 2025, 5,
                TO_DATE('2025-05-' || LPAD(c.dia_pago, 2, '0'), 'YYYY-MM-DD'),
                c.valor_mensual, 0, c.valor_mensual, 'PENDIENTE');
    END LOOP;
    COMMIT;
END;
/

-- ══════════════════════════════════════════════════════════════════════
-- §I  PAGOS
-- Registramos un pago completo por cada cuota PAGADA (marzo y abril).
-- metodo_pago IN ('EFECTIVO','TRANSFERENCIA','CHEQUE',
--                 'TARJETA','CONSIGNACION','OTRO')   [CHK_PAGO_METODO]
-- ══════════════════════════════════════════════════════════════════════

BEGIN
    FOR q IN (
        SELECT ca.id_cuota, ca.valor_base
          FROM CUOTAS_ARRIENDO ca
         WHERE ca.estado = 'PAGADA'
    ) LOOP
        INSERT INTO PAGOS (id_cuota, id_registrado_por, fecha_pago,
                           valor_pagado, metodo_pago, referencia)
        VALUES (q.id_cuota,
                (SELECT id_usuario FROM USUARIOS WHERE username = 'admin'),
                SYSDATE - 10,
                q.valor_base,
                'TRANSFERENCIA',
                'REF-' || q.id_cuota || '-2025');
    END LOOP;
    COMMIT;
END;
/

-- Cuota mayo del apt 202 → EN_MORA (caso de prueba)
-- TRG_CUOTAS_UPD recalculará valor_total = 750000 + 11250 = 761250.
UPDATE CUOTAS_ARRIENDO
   SET estado      = 'EN_MORA',
       valor_mora  = 11250,       -- 1.5% de 750 000
       valor_total = 761250       -- igual a valor_base + valor_mora (redundante; trigger lo fija)
 WHERE id_contrato = (SELECT id_contrato FROM CONTRATOS
                       WHERE id_apartamento =
                             (SELECT id_apartamento FROM APARTAMENTOS WHERE numero = '202'))
   AND anio = 2025 AND mes = 5;

COMMIT;

-- ══════════════════════════════════════════════════════════════════════
-- §J  ALERTAS_PAGO
-- tipo_alerta IN ('PROXIMO_VENCIMIENTO','VENCIDA','EN_MORA')
-- canal       IN ('SISTEMA','EMAIL','SMS','WHATSAPP')
-- UIX_ALERTA_CUOTA_DIA evita duplicado por (id_cuota, tipo, canal, día).
-- Ejecutar este script el mismo día no generará colisión porque
-- cada cuota recibe exactamente un registro de cada tipo.
-- ══════════════════════════════════════════════════════════════════════

-- Alerta de vencimiento próximo para todas las cuotas PENDIENTES
INSERT INTO ALERTAS_PAGO (id_cuota, tipo_alerta, canal, leida, enviada_en)
SELECT ca.id_cuota, 'PROXIMO_VENCIMIENTO', 'SISTEMA', 0, SYSTIMESTAMP
  FROM CUOTAS_ARRIENDO ca
 WHERE ca.estado = 'PENDIENTE';

-- Alerta de mora para la cuota del apt 202
INSERT INTO ALERTAS_PAGO (id_cuota, tipo_alerta, canal, leida, enviada_en)
SELECT ca.id_cuota, 'EN_MORA', 'SISTEMA', 0, SYSTIMESTAMP
  FROM CUOTAS_ARRIENDO ca
 WHERE ca.estado = 'EN_MORA';

COMMIT;

-- ══════════════════════════════════════════════════════════════════════
-- §K  VISITANTES
-- 8 visitantes externos reutilizables.
-- UQ_VIS_DOCUMENTO: único por (id_tipo_doc, numero_documento).
-- ══════════════════════════════════════════════════════════════════════

INSERT INTO VISITANTES (id_tipo_doc, numero_documento, nombres, apellidos,
                        telefono, email)
VALUES ((SELECT id_tipo_doc FROM TIPOS_DOCUMENTO WHERE codigo = 'CC'),
        '1070001001', 'Ricardo', 'Palomino García',
        '3005551001', 'rpalomino@correo.co');

INSERT INTO VISITANTES (id_tipo_doc, numero_documento, nombres, apellidos,
                        telefono, email)
VALUES ((SELECT id_tipo_doc FROM TIPOS_DOCUMENTO WHERE codigo = 'CC'),
        '1070001002', 'Marcela', 'Duarte Ospino',
        '3005551002', 'mduarte@correo.co');

INSERT INTO VISITANTES (id_tipo_doc, numero_documento, nombres, apellidos,
                        telefono, email)
VALUES ((SELECT id_tipo_doc FROM TIPOS_DOCUMENTO WHERE codigo = 'CC'),
        '1070001003', 'Pedro', 'Araujo Leal',
        '3005551003', NULL);

INSERT INTO VISITANTES (id_tipo_doc, numero_documento, nombres, apellidos,
                        telefono, email)
VALUES ((SELECT id_tipo_doc FROM TIPOS_DOCUMENTO WHERE codigo = 'CC'),
        '1070001004', 'Sandra Liliana', 'Molina Cepeda',
        '3005551004', 'smolina@correo.co');

INSERT INTO VISITANTES (id_tipo_doc, numero_documento, nombres, apellidos,
                        telefono, email)
VALUES ((SELECT id_tipo_doc FROM TIPOS_DOCUMENTO WHERE codigo = 'CC'),
        '1070001005', 'Luis Miguel', 'Acosta Barros',
        '3005551005', NULL);

INSERT INTO VISITANTES (id_tipo_doc, numero_documento, nombres, apellidos,
                        telefono, email)
VALUES ((SELECT id_tipo_doc FROM TIPOS_DOCUMENTO WHERE codigo = 'CC'),
        '1070001006', 'Claudia Esperanza', 'Mendoza López',
        '3005551006', 'cmendoza_vis@correo.co');

INSERT INTO VISITANTES (id_tipo_doc, numero_documento, nombres, apellidos,
                        telefono, email)
VALUES ((SELECT id_tipo_doc FROM TIPOS_DOCUMENTO WHERE codigo = 'CE'),
        'CE-111222', 'Arnaud', 'Moreau Lefebvre',
        '3005551007', 'arnaud.m@correo.co');

INSERT INTO VISITANTES (id_tipo_doc, numero_documento, nombres, apellidos,
                        telefono, email)
VALUES ((SELECT id_tipo_doc FROM TIPOS_DOCUMENTO WHERE codigo = 'CC'),
        '1070001008', 'Katherine', 'Vega Romero',
        '3005551008', 'kvega@correo.co');

COMMIT;

-- ══════════════════════════════════════════════════════════════════════
-- §L  VISITAS · QR_ACCESOS · REGISTRO_VISITA · VEHICULOS_VISITA
-- 5 escenarios representativos:
--   V1: FINALIZADA — Ricardo → apto 201 (con vehículo, VIS-01)
--   V2: FINALIZADA — Marcela → apto 301 (a pie)
--   V3: ACTIVA     — Pedro   → apto 202 (moto, MV-01)
--   V4: PENDIENTE  — Sandra  → apto 303 (QR sin escanear)
--   V5: EXPIRADA   — Luis    → apto 404 (QR vencido sin usar)
--
-- Notas de diseño:
--  · VISITAS se inserta directamente con el estado final deseado.
--    TRG_QR_USAR solo actúa al hacer UPDATE de QR_ACCESOS.usado 0→1,
--    no en INSERT, por lo que no sobreescribe el estado que ponemos aquí.
--  · TRG_VIS_VALIDAR_RESIDENTE valida que id_residente coincida con
--    el residente del id_contrato_res referenciado (BEFORE INSERT).
--  · TRG_AUTO_FRECUENTE inserta en FRECUENTES_RESIDENTE al hacer
--    INSERT en REGISTRO_VISITA (AFTER INSERT).
--  · tiempo_validez_min CHECK BETWEEN 5 AND 60.
--  · CHK_QR_FECHA_USO: usado=1 → fecha_uso NOT NULL; usado=0 → NULL.
-- ══════════════════════════════════════════════════════════════════════

-- ── V1: VISITA FINALIZADA — Ricardo → 201 ────────────────────────────
INSERT INTO VISITAS (id_contrato_res, id_residente, tiempo_validez_min,
                     cantidad_personas, notas, estado)
VALUES (
    (SELECT cr.id_contrato_res
       FROM CONTRATO_RESIDENTE cr
       JOIN CONTRATOS c ON c.id_contrato = cr.id_contrato
       JOIN APARTAMENTOS ap ON ap.id_apartamento = c.id_apartamento
      WHERE ap.numero = '201'
        AND cr.rol_en_contrato = 'ARRENDATARIO'),
    (SELECT id_residente FROM RESIDENTES WHERE numero_documento = '1065432101'),
    30, 1, 'Visita familiar', 'FINALIZADA'
);

INSERT INTO QR_ACCESOS (id_visita, codigo_qr, fecha_generacion,
                        fecha_expiracion, usado, fecha_uso,
                        id_vigilante_uso)
VALUES (
    (SELECT MAX(id_visita) FROM VISITAS),
    'QR-UUID-V1-FINALIZADA-0001',
    SYSTIMESTAMP - INTERVAL '2' DAY,
    SYSTIMESTAMP - INTERVAL '2' DAY + INTERVAL '30' MINUTE,
    1,
    SYSTIMESTAMP - INTERVAL '2' DAY + INTERVAL '5' MINUTE,
    (SELECT id_usuario FROM USUARIOS WHERE username = 'portero01')
);

INSERT INTO VEHICULOS_VISITA (id_visita, id_parqueadero, placa, tipo,
                               color, marca, hora_entrada, hora_salida)
VALUES (
    (SELECT MAX(id_visita) FROM VISITAS),
    (SELECT id_parqueadero FROM PARQUEADEROS WHERE codigo = 'VIS-01'),
    'ABC123', 'VEHICULO', 'Blanco', 'Toyota',
    SYSTIMESTAMP - INTERVAL '2' DAY + INTERVAL '5' MINUTE,
    SYSTIMESTAMP - INTERVAL '2' DAY + INTERVAL '65' MINUTE
);

-- TRG_AUTO_FRECUENTE dispara al insertar este registro
-- → FRECUENTES_RESIDENTE(Carlos, Ricardo) se crea automáticamente
INSERT INTO REGISTRO_VISITA (id_visita, id_visitante, id_vehiculo_visita, es_titular)
VALUES (
    (SELECT MAX(id_visita) FROM VISITAS),
    (SELECT id_visitante FROM VISITANTES WHERE numero_documento = '1070001001'),
    (SELECT MAX(id_vehiculo_visita) FROM VEHICULOS_VISITA),
    1
);

-- ── V2: VISITA FINALIZADA — Marcela → 301 (a pie) ────────────────────
INSERT INTO VISITAS (id_contrato_res, id_residente, tiempo_validez_min,
                     cantidad_personas, notas, estado)
VALUES (
    (SELECT cr.id_contrato_res
       FROM CONTRATO_RESIDENTE cr
       JOIN CONTRATOS c ON c.id_contrato = cr.id_contrato
       JOIN APARTAMENTOS ap ON ap.id_apartamento = c.id_apartamento
      WHERE ap.numero = '301'
        AND cr.rol_en_contrato = 'ARRENDATARIO'),
    (SELECT id_residente FROM RESIDENTES WHERE numero_documento = '1065432106'),
    15, 1, 'Amiga de la cónyuge', 'FINALIZADA'
);

INSERT INTO QR_ACCESOS (id_visita, codigo_qr, fecha_generacion,
                        fecha_expiracion, usado, fecha_uso,
                        id_vigilante_uso)
VALUES (
    (SELECT MAX(id_visita) FROM VISITAS),
    'QR-UUID-V2-FINALIZADA-0002',
    SYSTIMESTAMP - INTERVAL '1' DAY,
    SYSTIMESTAMP - INTERVAL '1' DAY + INTERVAL '15' MINUTE,
    1,
    SYSTIMESTAMP - INTERVAL '1' DAY + INTERVAL '3' MINUTE,
    (SELECT id_usuario FROM USUARIOS WHERE username = 'portero02')
);

-- TRG_AUTO_FRECUENTE → FRECUENTES_RESIDENTE(Andrés, Marcela)
INSERT INTO REGISTRO_VISITA (id_visita, id_visitante, id_vehiculo_visita, es_titular)
VALUES (
    (SELECT MAX(id_visita) FROM VISITAS),
    (SELECT id_visitante FROM VISITANTES WHERE numero_documento = '1070001002'),
    NULL,  -- llegó a pie
    1
);

-- ── V3: VISITA ACTIVA — Pedro → 202 (moto, parqueadero MV-01) ─────────
INSERT INTO VISITAS (id_contrato_res, id_residente, tiempo_validez_min,
                     cantidad_personas, notas, estado)
VALUES (
    (SELECT cr.id_contrato_res
       FROM CONTRATO_RESIDENTE cr
       JOIN CONTRATOS c ON c.id_contrato = cr.id_contrato
       JOIN APARTAMENTOS ap ON ap.id_apartamento = c.id_apartamento
      WHERE ap.numero = '202'
        AND cr.rol_en_contrato = 'ARRENDATARIO'),
    (SELECT id_residente FROM RESIDENTES WHERE numero_documento = '1065432103'),
    30, 1, 'Técnico de internet', 'ACTIVA'
);

INSERT INTO QR_ACCESOS (id_visita, codigo_qr, fecha_generacion,
                        fecha_expiracion, usado, fecha_uso,
                        id_vigilante_uso)
VALUES (
    (SELECT MAX(id_visita) FROM VISITAS),
    'QR-UUID-V3-ACTIVA-00003',
    SYSTIMESTAMP - INTERVAL '20' MINUTE,
    SYSTIMESTAMP + INTERVAL '10' MINUTE,
    1,
    SYSTIMESTAMP - INTERVAL '15' MINUTE,
    (SELECT id_usuario FROM USUARIOS WHERE username = 'portero01')
);

INSERT INTO VEHICULOS_VISITA (id_visita, id_parqueadero, placa, tipo,
                               color, marca, hora_entrada, hora_salida)
VALUES (
    (SELECT MAX(id_visita) FROM VISITAS),
    (SELECT id_parqueadero FROM PARQUEADEROS WHERE codigo = 'MV-01'),
    'MOT456', 'MOTO', 'Rojo', 'Honda',
    SYSTIMESTAMP - INTERVAL '15' MINUTE,
    NULL  -- aún dentro
);

-- Marcar ese parqueadero como OCUPADO
UPDATE PARQUEADEROS SET estado = 'OCUPADO' WHERE codigo = 'MV-01';

-- TRG_AUTO_FRECUENTE → FRECUENTES_RESIDENTE(Diana, Pedro)
INSERT INTO REGISTRO_VISITA (id_visita, id_visitante, id_vehiculo_visita, es_titular)
VALUES (
    (SELECT MAX(id_visita) FROM VISITAS),
    (SELECT id_visitante FROM VISITANTES WHERE numero_documento = '1070001003'),
    (SELECT MAX(id_vehiculo_visita) FROM VEHICULOS_VISITA),
    1
);

-- ── V4: VISITA PENDIENTE — Sandra → 303 (QR sin escanear) ────────────
INSERT INTO VISITAS (id_contrato_res, id_residente, tiempo_validez_min,
                     cantidad_personas, notas, estado)
VALUES (
    (SELECT cr.id_contrato_res
       FROM CONTRATO_RESIDENTE cr
       JOIN CONTRATOS c ON c.id_contrato = cr.id_contrato
       JOIN APARTAMENTOS ap ON ap.id_apartamento = c.id_apartamento
      WHERE ap.numero = '303'
        AND cr.rol_en_contrato = 'ARRENDATARIO'),
    (SELECT id_residente FROM RESIDENTES WHERE numero_documento = '1065432109'),
    60, 2, 'Visita con pareja', 'PENDIENTE'
);

INSERT INTO QR_ACCESOS (id_visita, codigo_qr, fecha_generacion,
                        fecha_expiracion, usado, fecha_uso,
                        id_vigilante_uso)
VALUES (
    (SELECT MAX(id_visita) FROM VISITAS),
    'QR-UUID-V4-PENDIENTE-0004',
    SYSTIMESTAMP,
    SYSTIMESTAMP + INTERVAL '60' MINUTE,
    0, NULL, NULL   -- sin escanear aún
);

-- TRG_AUTO_FRECUENTE → FRECUENTES_RESIDENTE(Luisa, Sandra)
INSERT INTO REGISTRO_VISITA (id_visita, id_visitante, id_vehiculo_visita, es_titular)
VALUES (
    (SELECT MAX(id_visita) FROM VISITAS),
    (SELECT id_visitante FROM VISITANTES WHERE numero_documento = '1070001004'),
    NULL,
    1
);

-- ── V5: VISITA EXPIRADA — Luis → 404 (QR vencido sin usar) ───────────
INSERT INTO VISITAS (id_contrato_res, id_residente, tiempo_validez_min,
                     cantidad_personas, notas, estado)
VALUES (
    (SELECT cr.id_contrato_res
       FROM CONTRATO_RESIDENTE cr
       JOIN CONTRATOS c ON c.id_contrato = cr.id_contrato
       JOIN APARTAMENTOS ap ON ap.id_apartamento = c.id_apartamento
      WHERE ap.numero = '404'
        AND cr.rol_en_contrato = 'ARRENDATARIO'),
    (SELECT id_residente FROM RESIDENTES WHERE numero_documento = '1065432115'),
    10, 1, NULL, 'EXPIRADA'
);

INSERT INTO QR_ACCESOS (id_visita, codigo_qr, fecha_generacion,
                        fecha_expiracion, usado, fecha_uso,
                        id_vigilante_uso)
VALUES (
    (SELECT MAX(id_visita) FROM VISITAS),
    'QR-UUID-V5-EXPIRADA-00005',
    SYSTIMESTAMP - INTERVAL '3' HOUR,
    SYSTIMESTAMP - INTERVAL '3' HOUR + INTERVAL '10' MINUTE,
    0, NULL, NULL   -- nunca se escaneó, ya venció
);

-- TRG_AUTO_FRECUENTE → FRECUENTES_RESIDENTE(Alejandra, Luis)
INSERT INTO REGISTRO_VISITA (id_visita, id_visitante, id_vehiculo_visita, es_titular)
VALUES (
    (SELECT MAX(id_visita) FROM VISITAS),
    (SELECT id_visitante FROM VISITANTES WHERE numero_documento = '1070001005'),
    NULL,
    1
);

COMMIT;

-- ══════════════════════════════════════════════════════════════════════
-- §M  REGISTROS_ACCESO
-- Solo las visitas con QR usado (FINALIZADA o ACTIVA) tienen registro.
-- UQ_ACCESO_VISITA garantiza un único registro por visita.
-- TRG_ACCESO_SALIDA (AFTER UPDATE hora_salida NULL→NOT NULL) cerraría
-- la visita y liberaría parqueaderos, pero aquí insertamos directamente
-- con hora_salida ya definida, por lo que ese trigger no dispara.
-- ══════════════════════════════════════════════════════════════════════

-- V1: entrada y salida (FINALIZADA)
INSERT INTO REGISTROS_ACCESO (id_visita, id_vigilante, hora_entrada,
                               hora_salida, observaciones)
VALUES (
    (SELECT id_visita FROM VISITAS WHERE notas = 'Visita familiar'),
    (SELECT id_usuario FROM USUARIOS WHERE username = 'portero01'),
    SYSTIMESTAMP - INTERVAL '2' DAY + INTERVAL '5' MINUTE,
    SYSTIMESTAMP - INTERVAL '2' DAY + INTERVAL '65' MINUTE,
    'Ingreso normal, sin novedad.'
);

-- V2: entrada y salida (FINALIZADA — a pie)
INSERT INTO REGISTROS_ACCESO (id_visita, id_vigilante, hora_entrada,
                               hora_salida, observaciones)
VALUES (
    (SELECT id_visita FROM VISITAS WHERE notas = 'Amiga de la cónyuge'),
    (SELECT id_usuario FROM USUARIOS WHERE username = 'portero02'),
    SYSTIMESTAMP - INTERVAL '1' DAY + INTERVAL '3' MINUTE,
    SYSTIMESTAMP - INTERVAL '1' DAY + INTERVAL '45' MINUTE,
    NULL
);

-- V3: solo entrada (ACTIVA — aún dentro)
INSERT INTO REGISTROS_ACCESO (id_visita, id_vigilante, hora_entrada,
                               hora_salida, observaciones)
VALUES (
    (SELECT id_visita FROM VISITAS WHERE notas = 'Técnico de internet'),
    (SELECT id_usuario FROM USUARIOS WHERE username = 'portero01'),
    SYSTIMESTAMP - INTERVAL '15' MINUTE,
    NULL,
    'Equipos de trabajo revisados en portería.'
);

COMMIT;

-- ══════════════════════════════════════════════════════════════════════
-- §N  FRECUENTES_RESIDENTE
-- TRG_AUTO_FRECUENTE ya creó automáticamente los pares de V1-V5 al
-- insertar en REGISTRO_VISITA. Este bloque añade pares adicionales
-- (visitantes que no han visitado aún pero están preautorizados).
-- UQ_FREC_RES_VIS evita duplicados. Los visitantes de V1-V5 ya tienen
-- su fila; aquí solo se insertan visitantes nuevos para esos residentes.
-- ══════════════════════════════════════════════════════════════════════

-- Claudia → Alejandra (apt 404) — no estuvo en ninguna visita V1-V5
INSERT INTO FRECUENTES_RESIDENTE (id_residente, id_visitante, activo)
VALUES (
    (SELECT id_residente FROM RESIDENTES WHERE numero_documento = '1065432115'),
    (SELECT id_visitante FROM VISITANTES WHERE numero_documento = '1070001006'),
    1
);

-- Katherine → Alejandra (apt 404)
INSERT INTO FRECUENTES_RESIDENTE (id_residente, id_visitante, activo)
VALUES (
    (SELECT id_residente FROM RESIDENTES WHERE numero_documento = '1065432115'),
    (SELECT id_visitante FROM VISITANTES WHERE numero_documento = '1070001008'),
    1
);

-- Arnaud → Héctor Guerrero (apt 403) — visitante frecuente extranjero
INSERT INTO FRECUENTES_RESIDENTE (id_residente, id_visitante, activo)
VALUES (
    (SELECT id_residente FROM RESIDENTES WHERE numero_documento = '1065432114'),
    (SELECT id_visitante FROM VISITANTES WHERE numero_documento = 'CE-111222'),
    1
);

COMMIT;

-- ══════════════════════════════════════════════════════════════════════
-- VERIFICACIÓN RÁPIDA — ejecutar por separado si se desea
-- ══════════════════════════════════════════════════════════════════════
/*
-- Contar filas por tabla
SELECT 'APARTAMENTOS'         AS tabla, COUNT(*) AS filas FROM APARTAMENTOS         UNION ALL
SELECT 'PARQUEADEROS',                  COUNT(*)           FROM PARQUEADEROS         UNION ALL
SELECT 'RESIDENTES',                    COUNT(*)           FROM RESIDENTES           UNION ALL
SELECT 'TUTORES',                       COUNT(*)           FROM TUTORES              UNION ALL
SELECT 'USUARIOS',                      COUNT(*)           FROM USUARIOS             UNION ALL
SELECT 'CONTRATOS',                     COUNT(*)           FROM CONTRATOS            UNION ALL
SELECT 'CONTRATO_RESIDENTE',            COUNT(*)           FROM CONTRATO_RESIDENTE   UNION ALL
SELECT 'CUOTAS_ARRIENDO',               COUNT(*)           FROM CUOTAS_ARRIENDO      UNION ALL
SELECT 'PAGOS',                         COUNT(*)           FROM PAGOS                UNION ALL
SELECT 'ALERTAS_PAGO',                  COUNT(*)           FROM ALERTAS_PAGO         UNION ALL
SELECT 'VISITANTES',                    COUNT(*)           FROM VISITANTES           UNION ALL
SELECT 'VISITAS',                       COUNT(*)           FROM VISITAS              UNION ALL
SELECT 'QR_ACCESOS',                    COUNT(*)           FROM QR_ACCESOS           UNION ALL
SELECT 'VEHICULOS_VISITA',              COUNT(*)           FROM VEHICULOS_VISITA     UNION ALL
SELECT 'REGISTRO_VISITA',               COUNT(*)           FROM REGISTRO_VISITA      UNION ALL
SELECT 'REGISTROS_ACCESO',              COUNT(*)           FROM REGISTROS_ACCESO     UNION ALL
SELECT 'FRECUENTES_RESIDENTE',          COUNT(*)           FROM FRECUENTES_RESIDENTE;

-- Probar SP_VALIDAR_QR con el QR de V4 (pendiente, debería validar)
DECLARE
    v_valido  NUMBER;
    v_mensaje VARCHAR2(500);
    v_cursor  SYS_REFCURSOR;
BEGIN
    SP_VALIDAR_QR('QR-UUID-V4-PENDIENTE-0004',
                  (SELECT id_usuario FROM USUARIOS WHERE username = 'portero01'),
                  v_valido, v_mensaje, v_cursor);
    DBMS_OUTPUT.PUT_LINE('Válido: ' || v_valido);
    DBMS_OUTPUT.PUT_LINE('Mensaje: ' || v_mensaje);
END;
/

-- Probar VW_VISITANTES_FRECUENTES
SELECT * FROM VW_VISITANTES_FRECUENTES ORDER BY id_residente, total_visitas DESC;

-- Ver visitas activas
SELECT * FROM VW_VISITAS_ACTIVAS;

-- Ver cartera pendiente
SELECT * FROM VW_CARTERA_PENDIENTE;
*/

-- ══════════════════════════════════════════════════════════════════════
-- FIN DEL SCRIPT — datos_prueba_v5.sql
-- Tablas pobladas : 18
-- Apartamentos    : 16 (pisos 2-5)
-- Parqueaderos    : 21 (13 fijos + 4 VIS + 2 MV + 2 BV)
-- Residentes      : 17 (16 adultos + 1 menor)
-- Tutores         : 1  (Carmen Inés, CC 1065499099 — FIX v5)
-- Usuarios        : 16 (1 admin del DDL + 3 porteros + 12 residentes)
-- Contratos       : 12 ACTIVOS
-- Cuotas          : 36 (3 por contrato)
-- Pagos           : 24 (meses PAGADOS × 12 contratos)
-- Visitantes      : 8
-- Visitas         : 5 (FINALIZADA×2, ACTIVA×1, PENDIENTE×1, EXPIRADA×1)
-- Frecuentes auto : 5 (por TRG_AUTO_FRECUENTE al insertar REGISTRO_VISITA)
-- Frecuentes manual: 3 (Claudia, Katherine → Alejandra; Arnaud → Héctor)
-- ══════════════════════════════════════════════════════════════════════
