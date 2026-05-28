-- =============================================================================
-- SAED — Datos de prueba masivos
-- 16 aptos (pisos 2-5, 4 x piso) | 48 parqueaderos | 32 residentes
-- =============================================================================

SET SERVEROUTPUT ON;

-- ============================== LIMPIEZA ====================================
DELETE FROM ALERTAS_PAGO;
DELETE FROM REGISTROS_ACCESO;
DELETE FROM QR_ACCESOS;
DELETE FROM REGISTRO_VISITA;
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

-- ============================== APARTAMENTOS ================================
-- 16 aptos: pisos 2-5, 4 por piso
-- Tipos variados: ESTUDIO, 1_HAB, 2_HAB, 3_HAB, PENTHOUSE
-- 8 OCUPADOS (contratos activos), 8 DISPONIBLES
-- OCUPADOS: 201,202,301,302,401,402,501,502
-- DISPONIBLES: 203,204,303,304,403,404,503,504
INSERT ALL
  INTO APARTAMENTOS (id_apartamento,numero,piso,tipo,area_m2,capacidad_maxima,estado) VALUES (SEC_APARTAMENTOS.NEXTVAL,'201',2,'1_HAB',   50,2,'OCUPADO')
  INTO APARTAMENTOS (id_apartamento,numero,piso,tipo,area_m2,capacidad_maxima,estado) VALUES (SEC_APARTAMENTOS.NEXTVAL,'202',2,'2_HAB',   70,3,'OCUPADO')
  INTO APARTAMENTOS (id_apartamento,numero,piso,tipo,area_m2,capacidad_maxima,estado) VALUES (SEC_APARTAMENTOS.NEXTVAL,'203',2,'ESTUDIO', 40,2,'DISPONIBLE')
  INTO APARTAMENTOS (id_apartamento,numero,piso,tipo,area_m2,capacidad_maxima,estado) VALUES (SEC_APARTAMENTOS.NEXTVAL,'204',2,'1_HAB',   50,2,'DISPONIBLE')
  INTO APARTAMENTOS (id_apartamento,numero,piso,tipo,area_m2,capacidad_maxima,estado) VALUES (SEC_APARTAMENTOS.NEXTVAL,'301',3,'2_HAB',   75,4,'OCUPADO')
  INTO APARTAMENTOS (id_apartamento,numero,piso,tipo,area_m2,capacidad_maxima,estado) VALUES (SEC_APARTAMENTOS.NEXTVAL,'302',3,'3_HAB',   95,5,'OCUPADO')
  INTO APARTAMENTOS (id_apartamento,numero,piso,tipo,area_m2,capacidad_maxima,estado) VALUES (SEC_APARTAMENTOS.NEXTVAL,'303',3,'1_HAB',   55,2,'DISPONIBLE')
  INTO APARTAMENTOS (id_apartamento,numero,piso,tipo,area_m2,capacidad_maxima,estado) VALUES (SEC_APARTAMENTOS.NEXTVAL,'304',3,'2_HAB',   70,3,'DISPONIBLE')
  INTO APARTAMENTOS (id_apartamento,numero,piso,tipo,area_m2,capacidad_maxima,estado) VALUES (SEC_APARTAMENTOS.NEXTVAL,'401',4,'3_HAB',   90,5,'OCUPADO')
  INTO APARTAMENTOS (id_apartamento,numero,piso,tipo,area_m2,capacidad_maxima,estado) VALUES (SEC_APARTAMENTOS.NEXTVAL,'402',4,'PENTHOUSE',140,6,'OCUPADO')
  INTO APARTAMENTOS (id_apartamento,numero,piso,tipo,area_m2,capacidad_maxima,estado) VALUES (SEC_APARTAMENTOS.NEXTVAL,'403',4,'2_HAB',   75,4,'DISPONIBLE')
  INTO APARTAMENTOS (id_apartamento,numero,piso,tipo,area_m2,capacidad_maxima,estado) VALUES (SEC_APARTAMENTOS.NEXTVAL,'404',4,'3_HAB',   85,4,'DISPONIBLE')
  INTO APARTAMENTOS (id_apartamento,numero,piso,tipo,area_m2,capacidad_maxima,estado) VALUES (SEC_APARTAMENTOS.NEXTVAL,'501',5,'1_HAB',   55,2,'OCUPADO')
  INTO APARTAMENTOS (id_apartamento,numero,piso,tipo,area_m2,capacidad_maxima,estado) VALUES (SEC_APARTAMENTOS.NEXTVAL,'502',5,'1_HAB',   60,3,'OCUPADO')
  INTO APARTAMENTOS (id_apartamento,numero,piso,tipo,area_m2,capacidad_maxima,estado) VALUES (SEC_APARTAMENTOS.NEXTVAL,'503',5,'ESTUDIO', 42,2,'DISPONIBLE')
  INTO APARTAMENTOS (id_apartamento,numero,piso,tipo,area_m2,capacidad_maxima,estado) VALUES (SEC_APARTAMENTOS.NEXTVAL,'504',5,'ESTUDIO', 38,2,'DISPONIBLE')
SELECT * FROM DUAL;
COMMIT;

-- ============================== PARQUEADEROS ================================
-- 16 fijos (uno x apto) VEHICULO + 10 visitantes VEHICULO + 12 visitantes MOTO = 38
-- Fijos: P01-P16, asignados a los aptos en orden (id_apartamento 1-16)
-- Visitantes carro: V01-V10
-- Visitantes moto:  M01-M12
INSERT INTO PARQUEADEROS (id_parqueadero,codigo,tipo,es_visitante,estado,id_apartamento)
SELECT SEC_PARQUEADEROS.NEXTVAL, 'P'||LPAD(LEVEL,2,'0'), 'VEHICULO', 0, 'DISPONIBLE',
       (SELECT id_apartamento FROM (SELECT id_apartamento, ROWNUM r FROM APARTAMENTOS ORDER BY numero) WHERE r = LEVEL)
FROM DUAL CONNECT BY LEVEL <= 16;

INSERT INTO PARQUEADEROS (id_parqueadero,codigo,tipo,es_visitante,estado)
SELECT SEC_PARQUEADEROS.NEXTVAL, 'V'||LPAD(LEVEL,2,'0'), 'VEHICULO', 1, 'DISPONIBLE'
FROM DUAL CONNECT BY LEVEL <= 10;

INSERT INTO PARQUEADEROS (id_parqueadero,codigo,tipo,es_visitante,estado)
SELECT SEC_PARQUEADEROS.NEXTVAL, 'M'||LPAD(LEVEL,2,'0'), 'MOTO', 1, 'DISPONIBLE'
FROM DUAL CONNECT BY LEVEL <= 12;
COMMIT;

-- ============================== RESIDENTES ==================================
-- 32 residentes: 2 x apto (arrendatario + conviviente) para los 8 OCUPADOS
-- Tipos de documento: 1=CC, 2=CE, 4=PASAPORTE, 5=TI
INSERT ALL
  -- AP-201: Pérez / Ruiz
  INTO RESIDENTES (id_residente,id_tipo_doc,numero_documento,nombres,apellidos,fecha_nacimiento,telefono,email,activo) VALUES (SEC_RESIDENTES.NEXTVAL,1,'10000001','Carlos Andrés','Pérez Ramos',    DATE '1990-03-15','3001110001','carlos.perez@email.com',1)
  INTO RESIDENTES (id_residente,id_tipo_doc,numero_documento,nombres,apellidos,fecha_nacimiento,telefono,email,activo) VALUES (SEC_RESIDENTES.NEXTVAL,1,'10000002','María Fernanda','Ruiz Torres',   DATE '1993-07-22','3001110002','maria.ruiz@email.com',1)
  -- AP-202: Martínez / Gómez
  INTO RESIDENTES (id_residente,id_tipo_doc,numero_documento,nombres,apellidos,fecha_nacimiento,telefono,email,activo) VALUES (SEC_RESIDENTES.NEXTVAL,1,'10000003','Jhon Sebastián','Martínez López', DATE '1985-11-08','3001110003','jhon.martinez@email.com',1)
  INTO RESIDENTES (id_residente,id_tipo_doc,numero_documento,nombres,apellidos,fecha_nacimiento,telefono,email,activo) VALUES (SEC_RESIDENTES.NEXTVAL,1,'10000004','Luisa Valentina','Gómez Ríos',    DATE '1991-05-30','3001110004','luisa.gomez@email.com',1)
  -- AP-301: Soto / Torres (con menor)
  INTO RESIDENTES (id_residente,id_tipo_doc,numero_documento,nombres,apellidos,fecha_nacimiento,telefono,email,activo) VALUES (SEC_RESIDENTES.NEXTVAL,1,'10000005','Pedro Antonio','Soto Vargas',    DATE '1978-02-14','3001110005','pedro.soto@email.com',1)
  INTO RESIDENTES (id_residente,id_tipo_doc,numero_documento,nombres,apellidos,fecha_nacimiento,telefono,email,activo) VALUES (SEC_RESIDENTES.NEXTVAL,5,'50000001','Miguel Ángel','Torres Soto',   DATE '2010-06-12',NULL,NULL,1)
  -- AP-302: Ramírez / Morales
  INTO RESIDENTES (id_residente,id_tipo_doc,numero_documento,nombres,apellidos,fecha_nacimiento,telefono,email,activo) VALUES (SEC_RESIDENTES.NEXTVAL,1,'10000006','Andrés Felipe','Ramírez Ortiz',  DATE '1988-09-20','3001110006','andres.ramirez@email.com',1)
  INTO RESIDENTES (id_residente,id_tipo_doc,numero_documento,nombres,apellidos,fecha_nacimiento,telefono,email,activo) VALUES (SEC_RESIDENTES.NEXTVAL,1,'10000007','Carolina','Morales Peña',      DATE '1992-12-10','3001110007','carolina.morales@email.com',1)
  -- AP-401: Castro / López
  INTO RESIDENTES (id_residente,id_tipo_doc,numero_documento,nombres,apellidos,fecha_nacimiento,telefono,email,activo) VALUES (SEC_RESIDENTES.NEXTVAL,1,'10000008','Daniel','Castro Medina',        DATE '1980-04-05','3001110008','daniel.castro@email.com',1)
  INTO RESIDENTES (id_residente,id_tipo_doc,numero_documento,nombres,apellidos,fecha_nacimiento,telefono,email,activo) VALUES (SEC_RESIDENTES.NEXTVAL,1,'10000009','Laura','Londoño Vélez',        DATE '1985-08-15','3001110009','laura.londono@email.com',1)
  -- AP-402: Vargas / Ríos (PENTHOUSE)
  INTO RESIDENTES (id_residente,id_tipo_doc,numero_documento,nombres,apellidos,fecha_nacimiento,telefono,email,activo) VALUES (SEC_RESIDENTES.NEXTVAL,1,'10000010','Fernando','Vargas Uribe',       DATE '1975-11-30','3001110010','fernando.vargas@email.com',1)
  INTO RESIDENTES (id_residente,id_tipo_doc,numero_documento,nombres,apellidos,fecha_nacimiento,telefono,email,activo) VALUES (SEC_RESIDENTES.NEXTVAL,1,'10000011','Ana Lucía','Ríos Mejía',        DATE '1978-03-22','3001110011','ana.rios@email.com',1)
  -- AP-501: Torres / Herrera
  INTO RESIDENTES (id_residente,id_tipo_doc,numero_documento,nombres,apellidos,fecha_nacimiento,telefono,email,activo) VALUES (SEC_RESIDENTES.NEXTVAL,1,'10000012','Gabriel','Torres Pabón',        DATE '1995-06-18','3001110012','gabriel.torres@email.com',1)
  INTO RESIDENTES (id_residente,id_tipo_doc,numero_documento,nombres,apellidos,fecha_nacimiento,telefono,email,activo) VALUES (SEC_RESIDENTES.NEXTVAL,1,'10000013','Valentina','Herrera Díaz',     DATE '1997-01-25','3001110013','valentina.herrera@email.com',1)
  -- AP-502: Mendoza / Rojas
  INTO RESIDENTES (id_residente,id_tipo_doc,numero_documento,nombres,apellidos,fecha_nacimiento,telefono,email,activo) VALUES (SEC_RESIDENTES.NEXTVAL,1,'10000014','Héctor','Mendoza Salazar',      DATE '1982-09-08','3001110014','hector.mendoza@email.com',1)
  INTO RESIDENTES (id_residente,id_tipo_doc,numero_documento,nombres,apellidos,fecha_nacimiento,telefono,email,activo) VALUES (SEC_RESIDENTES.NEXTVAL,1,'10000015','Diana','Rojas Pineda',          DATE '1986-04-14','3001110015','diana.rojas@email.com',1)
  -- Residentes adicionales (sin contrato activo, para visitas frecuentes etc)
  INTO RESIDENTES (id_residente,id_tipo_doc,numero_documento,nombres,apellidos,fecha_nacimiento,telefono,email,activo) VALUES (SEC_RESIDENTES.NEXTVAL,1,'10000016','Ana','Morales Peña',            DATE '1970-09-25','3001110016','ana.morales2@email.com',1)
  INTO RESIDENTES (id_residente,id_tipo_doc,numero_documento,nombres,apellidos,fecha_nacimiento,telefono,email,activo) VALUES (SEC_RESIDENTES.NEXTVAL,2,'CE100001','Jean','Pierre Dubois',          DATE '1985-07-14','3001110017','jean.dubois@email.com',1)
SELECT * FROM DUAL;
COMMIT;

-- ============================== USUARIOS ====================================
INSERT ALL
  INTO USUARIOS (id_usuario,id_residente,username,password_hash,rol) VALUES (SEC_USUARIOS.NEXTVAL,NULL,'admin','$2b$12$eImiTXuWVxfM37uY4JANjOeH/y3bFMPqV5oRqF2LYqHBH0X3hZBfO','ADMINISTRADOR')
  INTO USUARIOS (id_usuario,id_residente,username,password_hash,rol) VALUES (SEC_USUARIOS.NEXTVAL,NULL,'portero1','$2b$12$eImiTXuWVxfM37uY4JANjOeH/y3bFMPqV5oRqF2LYqHBH0X3hZBfO','PORTERO')
  INTO USUARIOS (id_usuario,id_residente,username,password_hash,rol) VALUES (SEC_USUARIOS.NEXTVAL,NULL,'portero2','$2b$12$eImiTXuWVxfM37uY4JANjOeH/y3bFMPqV5oRqF2LYqHBH0X3hZBfO','PORTERO')
  INTO USUARIOS (id_usuario,id_residente,username,password_hash,rol) VALUES (SEC_USUARIOS.NEXTVAL,(SELECT id_residente FROM RESIDENTES WHERE numero_documento='10000001'),'carlos','$2b$12$eImiTXuWVxfM37uY4JANjOeH/y3bFMPqV5oRqF2LYqHBH0X3hZBfO','RESIDENTE')
  INTO USUARIOS (id_usuario,id_residente,username,password_hash,rol) VALUES (SEC_USUARIOS.NEXTVAL,(SELECT id_residente FROM RESIDENTES WHERE numero_documento='10000003'),'jhon','$2b$12$eImiTXuWVxfM37uY4JANjOeH/y3bFMPqV5oRqF2LYqHBH0X3hZBfO','RESIDENTE')
SELECT * FROM DUAL;
COMMIT;

-- ============================== CONTRATOS ===================================
-- 8 contratos ACTIVO para los 8 aptos OCUPADOS
-- C1: AP-201, $1.200.000, dia_pago=5
-- C2: AP-202, $1.800.000, dia_pago=10
-- C3: AP-301, $1.500.000, dia_pago=5
-- C4: AP-302, $2.200.000, dia_pago=15
-- C5: AP-401, $2.500.000, dia_pago=8
-- C6: AP-402, $3.500.000, dia_pago=5
-- C7: AP-501, $1.000.000, dia_pago=10
-- C8: AP-502, $1.100.000, dia_pago=12
INSERT INTO CONTRATOS (id_contrato,id_apartamento,id_registrado_por,fecha_inicio,valor_mensual,dia_pago,estado)
SELECT SEC_CONTRATOS.NEXTVAL, id_apartamento, (SELECT id_usuario FROM USUARIOS WHERE username='admin'),
       DATE '2024-01-01',
       CASE ROWNUM
         WHEN 1 THEN 1200000 WHEN 2 THEN 1800000 WHEN 3 THEN 1500000
         WHEN 4 THEN 2200000 WHEN 5 THEN 2500000 WHEN 6 THEN 3500000
         WHEN 7 THEN 1000000 WHEN 8 THEN 1100000
       END,
       CASE ROWNUM
         WHEN 1 THEN 5 WHEN 2 THEN 10 WHEN 3 THEN 5
         WHEN 4 THEN 15 WHEN 5 THEN 8 WHEN 6 THEN 5
         WHEN 7 THEN 10 WHEN 8 THEN 12
       END,
       'ACTIVO'
FROM APARTAMENTOS WHERE estado='OCUPADO' ORDER BY numero;
COMMIT;

-- ============================== CONTRATO_RESIDENTE ==========================
-- Cada apto OCUPADO: arrendatario + conviviente (o RESIDENTE_MENOR para AP-301)
-- Arrendatarios: doc 10000001,10000003,10000005,10000006,10000008,10000010,10000012,10000014
-- Convivientes:  doc 10000002,10000004,50000001(m),10000007,10000009,10000011,10000013,10000015
BEGIN
  FOR r IN (
    SELECT cr.id_contrato, a.numero,
           (SELECT id_residente FROM RESIDENTES WHERE numero_documento='10000001') r1,
           (SELECT id_residente FROM RESIDENTES WHERE numero_documento='10000002') r2,
           (SELECT id_residente FROM RESIDENTES WHERE numero_documento='10000003') r3,
           (SELECT id_residente FROM RESIDENTES WHERE numero_documento='10000004') r4,
           (SELECT id_residente FROM RESIDENTES WHERE numero_documento='10000005') r5,
           (SELECT id_residente FROM RESIDENTES WHERE numero_documento='50000001') r5m,
           (SELECT id_residente FROM RESIDENTES WHERE numero_documento='10000006') r6,
           (SELECT id_residente FROM RESIDENTES WHERE numero_documento='10000007') r7,
           (SELECT id_residente FROM RESIDENTES WHERE numero_documento='10000008') r8,
           (SELECT id_residente FROM RESIDENTES WHERE numero_documento='10000009') r9,
           (SELECT id_residente FROM RESIDENTES WHERE numero_documento='10000010') r10,
           (SELECT id_residente FROM RESIDENTES WHERE numero_documento='10000011') r11,
           (SELECT id_residente FROM RESIDENTES WHERE numero_documento='10000012') r12,
           (SELECT id_residente FROM RESIDENTES WHERE numero_documento='10000013') r13,
           (SELECT id_residente FROM RESIDENTES WHERE numero_documento='10000014') r14,
           (SELECT id_residente FROM RESIDENTES WHERE numero_documento='10000015') r15
    FROM CONTRATOS c
    JOIN APARTAMENTOS a ON c.id_apartamento = a.id_apartamento
    WHERE c.estado='ACTIVO' ORDER BY a.numero
  ) LOOP
    INSERT INTO CONTRATO_RESIDENTE VALUES (SEC_CONTRATO_RESIDENTE.NEXTVAL, r.id_contrato, r.r1, 'ARRENDATARIO');
    IF r.numero = '301' THEN
      INSERT INTO CONTRATO_RESIDENTE VALUES (SEC_CONTRATO_RESIDENTE.NEXTVAL, r.id_contrato, r.r5m, 'RESIDENTE_MENOR');
      -- Tutor Pedro para Miguel
      INSERT INTO TUTORES (id_tutor,id_residente_menor,id_tipo_doc,numero_documento,nombres,apellidos,parentesco)
      VALUES (SEC_TUTORES.NEXTVAL, r.r5m, 1, '10000005', 'Pedro Antonio', 'Soto Vargas', 'PADRE');
    ELSE
      INSERT INTO CONTRATO_RESIDENTE VALUES (SEC_CONTRATO_RESIDENTE.NEXTVAL, r.id_contrato,
        CASE r.numero
          WHEN '201' THEN r.r2 WHEN '202' THEN r.r4 WHEN '302' THEN r.r7
          WHEN '401' THEN r.r9 WHEN '402' THEN r.r11 WHEN '501' THEN r.r13
          WHEN '502' THEN r.r15
        END, 'OTRO');
    END IF;
  END LOOP;
  COMMIT;
END;
/

-- ============================== CUOTAS_ARRIENDO =============================
-- Por cada contrato ACTIVO: 6 cuotas (Feb25 - Jul25), algunas PAGADAS, EN_MORA, PENDIENTE
BEGIN
  FOR c IN (SELECT id_contrato, valor_mensual, dia_pago FROM CONTRATOS WHERE estado='ACTIVO') LOOP
    -- Feb25: PAGADA
    INSERT INTO CUOTAS_ARRIENDO VALUES (SEC_CUOTAS_ARRIENDO.NEXTVAL, c.id_contrato, 2025, 2,
      TO_DATE('2025-'||LPAD(c.dia_pago,2,'0')||'-01','YYYY-MM-DD'), c.valor_mensual, 0, c.valor_mensual, 'PAGADA', SYSTIMESTAMP, SYSTIMESTAMP);
    -- Mar25: PAGADA
    INSERT INTO CUOTAS_ARRIENDO VALUES (SEC_CUOTAS_ARRIENDO.NEXTVAL, c.id_contrato, 2025, 3,
      TO_DATE('2025-'||LPAD(c.dia_pago,2,'0')||'-01','YYYY-MM-DD'), c.valor_mensual, 0, c.valor_mensual, 'PAGADA', SYSTIMESTAMP, SYSTIMESTAMP);
    -- Abr25: EN_MORA (con mora)
    INSERT INTO CUOTAS_ARRIENDO VALUES (SEC_CUOTAS_ARRIENDO.NEXTVAL, c.id_contrato, 2025, 4,
      TO_DATE('2025-'||LPAD(c.dia_pago,2,'0')||'-01','YYYY-MM-DD'), c.valor_mensual, ROUND(c.valor_mensual*0.015), ROUND(c.valor_mensual*1.015), 'EN_MORA', SYSTIMESTAMP, SYSTIMESTAMP);
    -- May25: PENDIENTE
    INSERT INTO CUOTAS_ARRIENDO VALUES (SEC_CUOTAS_ARRIENDO.NEXTVAL, c.id_contrato, 2025, 5,
      TO_DATE('2025-'||LPAD(c.dia_pago,2,'0')||'-01','YYYY-MM-DD'), c.valor_mensual, 0, c.valor_mensual, 'PENDIENTE', SYSTIMESTAMP, SYSTIMESTAMP);
    -- Jun25: PENDIENTE
    INSERT INTO CUOTAS_ARRIENDO VALUES (SEC_CUOTAS_ARRIENDO.NEXTVAL, c.id_contrato, 2025, 6,
      TO_DATE('2025-'||LPAD(c.dia_pago,2,'0')||'-01','YYYY-MM-DD'), c.valor_mensual, 0, c.valor_mensual, 'PENDIENTE', SYSTIMESTAMP, SYSTIMESTAMP);
    -- Jul25: PENDIENTE
    INSERT INTO CUOTAS_ARRIENDO VALUES (SEC_CUOTAS_ARRIENDO.NEXTVAL, c.id_contrato, 2025, 7,
      TO_DATE('2025-'||LPAD(c.dia_pago,2,'0')||'-01','YYYY-MM-DD'), c.valor_mensual, 0, c.valor_mensual, 'PENDIENTE', SYSTIMESTAMP, SYSTIMESTAMP);
  END LOOP;
  COMMIT;
END;
/

-- ============================== PAGOS =======================================
-- Pagos de Feb25 y Mar25 para cada contrato
BEGIN
  FOR c IN (SELECT id_contrato, valor_mensual FROM CONTRATOS WHERE estado='ACTIVO') LOOP
    INSERT INTO PAGOS (id_pago,id_cuota,id_registrado_por,fecha_pago,valor_pagado,metodo_pago,referencia,fecha_registro)
    SELECT SEC_PAGOS.NEXTVAL, id_cuota, (SELECT id_usuario FROM USUARIOS WHERE username='admin'),
           fecha_limite - 1, valor_total, 'TRANSFERENCIA', 'TXN-'||id_cuota, SYSTIMESTAMP
    FROM CUOTAS_ARRIENDO WHERE id_contrato=c.id_contrato AND estado='PAGADA';
  END LOOP;
  COMMIT;
END;
/

-- ============================== VISITANTES ==================================
INSERT ALL
  INTO VISITANTES (id_visitante,id_tipo_doc,numero_documento,nombres,apellidos,telefono,activo) VALUES (SEC_VISITANTES.NEXTVAL,1,'20000001','Laura Camila','Díaz Suárez','3111000001',1)
  INTO VISITANTES (id_visitante,id_tipo_doc,numero_documento,nombres,apellidos,telefono,activo) VALUES (SEC_VISITANTES.NEXTVAL,1,'20000002','Roberto','Castillo Medina','3111000002',1)
  INTO VISITANTES (id_visitante,id_tipo_doc,numero_documento,nombres,apellidos,telefono,activo) VALUES (SEC_VISITANTES.NEXTVAL,1,'20000003','Fernando','Ospina Lara','3111000003',1)
  INTO VISITANTES (id_visitante,id_tipo_doc,numero_documento,nombres,apellidos,telefono,activo) VALUES (SEC_VISITANTES.NEXTVAL,4,'PAS00001','Emily','Johnson',NULL,1)
  INTO VISITANTES (id_visitante,id_tipo_doc,numero_documento,nombres,apellidos,telefono,activo) VALUES (SEC_VISITANTES.NEXTVAL,2,'CE200001','Jean Pierre','Dupont','3111000004',1)
  INTO VISITANTES (id_visitante,id_tipo_doc,numero_documento,nombres,apellidos,telefono,activo) VALUES (SEC_VISITANTES.NEXTVAL,1,'20000004','Marcela','Giraldo Pérez','3111000005',1)
  INTO VISITANTES (id_visitante,id_tipo_doc,numero_documento,nombres,apellidos,telefono,activo) VALUES (SEC_VISITANTES.NEXTVAL,1,'20000005','Sergio','Londoño Arias','3111000006',1)
  INTO VISITANTES (id_visitante,id_tipo_doc,numero_documento,nombres,apellidos,telefono,activo) VALUES (SEC_VISITANTES.NEXTVAL,1,'20000006','Paola','Montoya Gil','3111000007',1)
  INTO VISITANTES (id_visitante,id_tipo_doc,numero_documento,nombres,apellidos,telefono,activo) VALUES (SEC_VISITANTES.NEXTVAL,1,'20000007','Camilo','Restrepo Zuluaga','3111000008',1)
  INTO VISITANTES (id_visitante,id_tipo_doc,numero_documento,nombres,apellidos,telefono,activo) VALUES (SEC_VISITANTES.NEXTVAL,1,'20000008','Andrea','Cifuentes Duque','3111000009',1)
SELECT * FROM DUAL;
COMMIT;

-- ============================== VISITAS =====================================
-- V1: ACTIVA — Carlos (AP-201) recibe a Roberto con carro
INSERT INTO VISITAS (id_visita,id_contrato_res,id_residente,tiempo_validez_min,cantidad_personas,estado,notas)
VALUES (SEC_VISITAS.NEXTVAL,
  (SELECT id_contrato_res FROM CONTRATO_RESIDENTE cr JOIN RESIDENTES r ON cr.id_residente=r.id_residente WHERE r.numero_documento='10000001' AND cr.rol_en_contrato='ARRENDATARIO'),
  (SELECT id_residente FROM RESIDENTES WHERE numero_documento='10000001'),
  60,2,'ACTIVA','Amigos, vienen con carro');

-- V2: PENDIENTE — Jhon (AP-202) autoriza a Fernando
INSERT INTO VISITAS (id_visita,id_contrato_res,id_residente,tiempo_validez_min,cantidad_personas,estado,notas)
VALUES (SEC_VISITAS.NEXTVAL,
  (SELECT id_contrato_res FROM CONTRATO_RESIDENTE cr JOIN RESIDENTES r ON cr.id_residente=r.id_residente WHERE r.numero_documento='10000003' AND cr.rol_en_contrato='ARRENDATARIO'),
  (SELECT id_residente FROM RESIDENTES WHERE numero_documento='10000003'),
  30,1,'PENDIENTE','Técnico de internet');

-- V3: FINALIZADA — Carlos recibió a Laura
INSERT INTO VISITAS (id_visita,id_contrato_res,id_residente,tiempo_validez_min,cantidad_personas,estado,notas)
VALUES (SEC_VISITAS.NEXTVAL,
  (SELECT id_contrato_res FROM CONTRATO_RESIDENTE cr JOIN RESIDENTES r ON cr.id_residente=r.id_residente WHERE r.numero_documento='10000001' AND cr.rol_en_contrato='ARRENDATARIO'),
  (SELECT id_residente FROM RESIDENTES WHERE numero_documento='10000001'),
  30,1,'FINALIZADA','Visita frecuente');

-- V4: CANCELADA — Jhon canceló a Emily
INSERT INTO VISITAS (id_visita,id_contrato_res,id_residente,tiempo_validez_min,cantidad_personas,estado,notas)
VALUES (SEC_VISITAS.NEXTVAL,
  (SELECT id_contrato_res FROM CONTRATO_RESIDENTE cr JOIN RESIDENTES r ON cr.id_residente=r.id_residente WHERE r.numero_documento='10000003' AND cr.rol_en_contrato='ARRENDATARIO'),
  (SELECT id_residente FROM RESIDENTES WHERE numero_documento='10000003'),
  30,1,'CANCELADA','Cambio de planes');

-- V5: EXPIRADA — QR sin usar
INSERT INTO VISITAS (id_visita,id_contrato_res,id_residente,tiempo_validez_min,cantidad_personas,estado,notas)
VALUES (SEC_VISITAS.NEXTVAL,
  (SELECT id_contrato_res FROM CONTRATO_RESIDENTE cr JOIN RESIDENTES r ON cr.id_residente=r.id_residente WHERE r.numero_documento='10000001' AND cr.rol_en_contrato='ARRENDATARIO'),
  (SELECT id_residente FROM RESIDENTES WHERE numero_documento='10000001'),
  15,1,'EXPIRADA','QR expiró sin usarse');

-- Más visitas para otros residentes
INSERT INTO VISITAS (id_visita,id_contrato_res,id_residente,tiempo_validez_min,cantidad_personas,estado,notas)
SELECT SEC_VISITAS.NEXTVAL, cr.id_contrato_res, r.id_residente, 30, 2, 'FINALIZADA', 'Visita social'
FROM RESIDENTES r
JOIN CONTRATO_RESIDENTE cr ON cr.id_residente=r.id_residente AND cr.rol_en_contrato='ARRENDATARIO'
WHERE r.numero_documento IN ('10000005','10000006','10000008','10000010','10000012','10000014');
COMMIT;

-- ============================== VEHICULOS_VISITA ============================
-- V1 (ACTIVA) con carro, parqueadero V01
INSERT INTO VEHICULOS_VISITA (id_vehiculo_visita,id_visita,tipo,placa,descripcion_tipo,marca)
VALUES (SEC_VEHICULOS_VISITA.NEXTVAL,
  (SELECT id_visita FROM VISITAS WHERE estado='ACTIVA' AND ROWNUM=1),
  'VEHICULO', 'ABC-123', 'Toyota Corolla gris', 'Toyota');
COMMIT;

-- Asignar parqueadero al vehículo activo
UPDATE VEHICULOS_VISITA vv SET id_parqueadero = (SELECT id_parqueadero FROM PARQUEADEROS WHERE codigo='V01')
WHERE id_vehiculo_visita = (SELECT id_vehiculo_visita FROM VEHICULOS_VISITA WHERE id_visita=(SELECT id_visita FROM VISITAS WHERE estado='ACTIVA' AND ROWNUM=1) AND ROWNUM=1);
COMMIT;

-- ============================== REGISTRO_VISITA =============================
INSERT INTO REGISTRO_VISITA (id_registro_visita,id_visita,id_visitante,es_titular)
SELECT SEC_REGISTRO_VISITA.NEXTVAL, v.id_visita, vis.id_visitante, 1
FROM VISITAS v
CROSS JOIN (SELECT id_visitante, ROWNUM r FROM VISITANTES) vis
WHERE v.estado='FINALIZADA' AND vis.r = 1;

INSERT INTO REGISTRO_VISITA (id_registro_visita,id_visita,id_visitante,id_vehiculo_visita,es_titular)
SELECT SEC_REGISTRO_VISITA.NEXTVAL, v.id_visita, (SELECT id_visitante FROM VISITANTES WHERE numero_documento='20000002'), vv.id_vehiculo_visita, 1
FROM VISITAS v
JOIN VEHICULOS_VISITA vv ON vv.id_visita=v.id_visita
WHERE v.estado='ACTIVA';

INSERT INTO REGISTRO_VISITA (id_registro_visita,id_visita,id_visitante,es_titular)
SELECT SEC_REGISTRO_VISITA.NEXTVAL, v.id_visita, (SELECT id_visitante FROM VISITANTES WHERE numero_documento='20000003'), 1
FROM VISITAS v WHERE v.estado='PENDIENTE';

INSERT INTO REGISTRO_VISITA (id_registro_visita,id_visita,id_visitante,es_titular)
SELECT SEC_REGISTRO_VISITA.NEXTVAL, v.id_visita, (SELECT id_visitante FROM VISITANTES WHERE numero_documento='PAS00001'), 1
FROM VISITAS v WHERE v.estado='CANCELADA';

INSERT INTO REGISTRO_VISITA (id_registro_visita,id_visita,id_visitante,es_titular)
SELECT SEC_REGISTRO_VISITA.NEXTVAL, v.id_visita, (SELECT id_visitante FROM VISITANTES WHERE numero_documento='CE200001'), 1
FROM VISITAS v WHERE v.estado='EXPIRADA';

-- Las visitas FINALIZADAS adicionales: asignarles visitantes
INSERT INTO REGISTRO_VISITA (id_registro_visita,id_visita,id_visitante,es_titular)
SELECT SEC_REGISTRO_VISITA.NEXTVAL, v.id_visita,
       (SELECT id_visitante FROM VISITANTES WHERE numero_documento=CASE MOD(ROWNUM,5)+1
          WHEN 1 THEN '20000004' WHEN 2 THEN '20000005' WHEN 3 THEN '20000006'
          WHEN 4 THEN '20000007' WHEN 5 THEN '20000008' END), 1
FROM (SELECT id_visita FROM VISITAS WHERE notas='Visita social' ORDER BY id_visita) v;
COMMIT;

-- ============================== QR_ACCESOS ==================================
-- QR para visita ACTIVA
INSERT INTO QR_ACCESOS (id_qr,id_visita,codigo_qr,fecha_generacion,fecha_expiracion,usado,fecha_uso,id_vigilante_uso)
SELECT SEC_QR_ACCESOS.NEXTVAL, id_visita, 'QR-'||RAWTOHEX(SYS_GUID()),
       SYSTIMESTAMP - INTERVAL '1' HOUR, SYSTIMESTAMP + INTERVAL '59' MINUTE, 1,
       SYSTIMESTAMP - INTERVAL '55' MINUTE, (SELECT id_usuario FROM USUARIOS WHERE username='portero1')
FROM VISITAS WHERE estado='ACTIVA';

-- QR para visita PENDIENTE
INSERT INTO QR_ACCESOS (id_qr,id_visita,codigo_qr,fecha_generacion,fecha_expiracion,usado)
SELECT SEC_QR_ACCESOS.NEXTVAL, id_visita, 'QR-'||RAWTOHEX(SYS_GUID()),
       SYSTIMESTAMP - INTERVAL '30' MINUTE, SYSTIMESTAMP + INTERVAL '15' MINUTE, 0
FROM VISITAS WHERE estado='PENDIENTE';

-- QR para visitas FINALIZADAS
INSERT INTO QR_ACCESOS (id_qr,id_visita,codigo_qr,fecha_generacion,fecha_expiracion,usado,fecha_uso,id_vigilante_uso)
SELECT SEC_QR_ACCESOS.NEXTVAL, id_visita, 'QR-'||RAWTOHEX(SYS_GUID()),
       SYSTIMESTAMP - INTERVAL '2' DAY, SYSTIMESTAMP - INTERVAL '2' DAY + INTERVAL '30' MINUTE, 1,
       SYSTIMESTAMP - INTERVAL '2' DAY + INTERVAL '10' MINUTE, (SELECT id_usuario FROM USUARIOS WHERE username='portero1')
FROM VISITAS WHERE estado='FINALIZADA' AND notas='Visita frecuente';

COMMIT;

-- ============================== REGISTROS_ACCESO ============================
-- Visita ACTIVA: entró sin salir
INSERT INTO REGISTROS_ACCESO (id_acceso,id_visita,id_vigilante,hora_entrada,observaciones)
SELECT SEC_REGISTROS_ACCESO.NEXTVAL, id_visita, (SELECT id_usuario FROM USUARIOS WHERE username='portero1'),
       SYSTIMESTAMP - INTERVAL '55' MINUTE, 'Ingreso con QR, vehículo asignado V01'
FROM VISITAS WHERE estado='ACTIVA';

-- Visitas FINALIZADAS: entraron y salieron
INSERT INTO REGISTROS_ACCESO (id_acceso,id_visita,id_vigilante,hora_entrada,hora_salida,observaciones)
SELECT SEC_REGISTROS_ACCESO.NEXTVAL, id_visita, (SELECT id_usuario FROM USUARIOS WHERE username='portero1'),
       SYSTIMESTAMP - INTERVAL '2' DAY, SYSTIMESTAMP - INTERVAL '2' DAY + INTERVAL '90' MINUTE, 'Visita completada'
FROM VISITAS WHERE estado='FINALIZADA' AND notas='Visita frecuente';

COMMIT;

-- ============================== BUZON =======================================
-- Aviso general para todos los aptos OCUPADOS
INSERT INTO BUZON (id_mensaje,id_apartamento,tipo,titulo,cuerpo,creado_por,entregado)
SELECT SEC_BUZON.NEXTVAL, id_apartamento, 'AVISO', 'Corte de agua programado',
       'El 30 de mayo de 8am a 2pm se realizará mantenimiento. Suspensión del servicio.',
       (SELECT id_usuario FROM USUARIOS WHERE username='admin'), 0
FROM APARTAMENTOS WHERE estado='OCUPADO';

-- Paquete para AP-201
INSERT INTO BUZON (id_mensaje,id_apartamento,tipo,titulo,cuerpo,creado_por,fecha_creacion,entregado,entregado_en)
SELECT SEC_BUZON.NEXTVAL, id_apartamento, 'PAQUETE', 'Paquete Amazon',
       'Se recibió paquete de Amazon. Peso 2.5kg.',
       (SELECT id_usuario FROM USUARIOS WHERE username='portero1'),
       SYSTIMESTAMP - INTERVAL '5' HOUR, 1, SYSTIMESTAMP - INTERVAL '3' HOUR
FROM APARTAMENTOS WHERE numero='201';

-- Paquete para AP-301 (no entregado)
INSERT INTO BUZON (id_mensaje,id_apartamento,tipo,titulo,cuerpo,creado_por,entregado)
SELECT SEC_BUZON.NEXTVAL, id_apartamento, 'PAQUETE', 'Paquete Rappi',
       'Domicilio de Rappi en portería.',
       (SELECT id_usuario FROM USUARIOS WHERE username='portero1'), 0
FROM APARTAMENTOS WHERE numero='301';
COMMIT;

-- ============================== MULTAS ======================================
INSERT ALL
  INTO MULTAS (id_multa,id_apartamento,tipo,monto,estado,creado_por) VALUES (SEC_MULTAS.NEXTVAL,(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='201'),'RUIDO',100000,'PENDIENTE',(SELECT id_usuario FROM USUARIOS WHERE username='admin'))
  INTO MULTAS (id_multa,id_apartamento,tipo,monto,estado,creado_por) VALUES (SEC_MULTAS.NEXTVAL,(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='202'),'PARQUEADERO',50000,'PAGADA',(SELECT id_usuario FROM USUARIOS WHERE username='portero1'))
  INTO MULTAS (id_multa,id_apartamento,tipo,monto,estado,creado_por) VALUES (SEC_MULTAS.NEXTVAL,(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='301'),'RUIDO',100000,'PENDIENTE',(SELECT id_usuario FROM USUARIOS WHERE username='admin'))
  INTO MULTAS (id_multa,id_apartamento,tipo,monto,estado,creado_por) VALUES (SEC_MULTAS.NEXTVAL,(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='302'),'PARQUEADERO',50000,'ANULADA',(SELECT id_usuario FROM USUARIOS WHERE username='portero1'))
  INTO MULTAS (id_multa,id_apartamento,tipo,monto,estado,creado_por) VALUES (SEC_MULTAS.NEXTVAL,(SELECT id_apartamento FROM APARTAMENTOS WHERE numero='401'),'RUIDO',100000,'PENDIENTE',(SELECT id_usuario FROM USUARIOS WHERE username='admin'))
SELECT * FROM DUAL;
COMMIT;

-- ============================== ALERTAS_PAGO ================================
-- Alertas para cuotas EN_MORA de Abr25 y PENDIENTES de May25
INSERT INTO ALERTAS_PAGO (id_alerta,id_cuota,tipo_alerta,canal,leida,enviada_en,leida_en)
SELECT SEC_ALERTAS_PAGO.NEXTVAL, id_cuota, 'EN_MORA', 'SISTEMA', 0, SYSTIMESTAMP - INTERVAL '5' DAY, NULL
FROM CUOTAS_ARRIENDO WHERE estado='EN_MORA';

INSERT INTO ALERTAS_PAGO (id_alerta,id_cuota,tipo_alerta,canal,leida,enviada_en)
SELECT SEC_ALERTAS_PAGO.NEXTVAL, id_cuota, 'PROXIMO_VENCIMIENTO', 'SISTEMA', 0, SYSTIMESTAMP
FROM CUOTAS_ARRIENDO WHERE estado='PENDIENTE' AND mes=5;
COMMIT;

-- ============================== VERIFICACIÓN ================================
SELECT 'APARTAMENTOS'     e, COUNT(*) t FROM APARTAMENTOS   UNION ALL
SELECT 'PARQUEADEROS',    COUNT(*) FROM PARQUEADEROS        UNION ALL
SELECT 'RESIDENTES',      COUNT(*) FROM RESIDENTES          UNION ALL
SELECT 'USUARIOS',        COUNT(*) FROM USUARIOS            UNION ALL
SELECT 'CONTRATOS',       COUNT(*) FROM CONTRATOS           UNION ALL
SELECT 'CONTRATO_RES',    COUNT(*) FROM CONTRATO_RESIDENTE  UNION ALL
SELECT 'TUTORES',         COUNT(*) FROM TUTORES             UNION ALL
SELECT 'CUOTAS',          COUNT(*) FROM CUOTAS_ARRIENDO     UNION ALL
SELECT 'PAGOS',           COUNT(*) FROM PAGOS               UNION ALL
SELECT 'VISITANTES',      COUNT(*) FROM VISITANTES          UNION ALL
SELECT 'VISITAS',         COUNT(*) FROM VISITAS             UNION ALL
SELECT 'VEH_VISITA',      COUNT(*) FROM VEHICULOS_VISITA    UNION ALL
SELECT 'REG_VISITA',      COUNT(*) FROM REGISTRO_VISITA     UNION ALL
SELECT 'QR_ACCESOS',      COUNT(*) FROM QR_ACCESOS          UNION ALL
SELECT 'REG_ACCESO',      COUNT(*) FROM REGISTROS_ACCESO    UNION ALL
SELECT 'BUZON',           COUNT(*) FROM BUZON               UNION ALL
SELECT 'MULTAS',          COUNT(*) FROM MULTAS              UNION ALL
SELECT 'ALERTAS_PAGO',    COUNT(*) FROM ALERTAS_PAGO;
COMMIT;
