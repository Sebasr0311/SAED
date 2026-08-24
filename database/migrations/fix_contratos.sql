-- =============================================================================
-- Fix: CONTRATOS + dependientes (el INSERT...SELECT con NEXTVAL no funciona)
-- =============================================================================

-- ============================== CONTRATOS ===================================
DECLARE
  TYPE t_apto IS RECORD (numero VARCHAR2(3), valor NUMBER, dia NUMBER);
  TYPE t_aptos IS TABLE OF t_apto;
  v_aptos t_aptos := t_aptos(
    t_apto('201',1200000,5), t_apto('202',1800000,10), t_apto('301',1500000,5),
    t_apto('302',2200000,15), t_apto('401',2500000,8), t_apto('402',3500000,5),
    t_apto('501',1000000,10), t_apto('502',1100000,12)
  );
  v_id_apt APARTAMENTOS.id_apartamento%TYPE;
  v_id_admin USUARIOS.id_usuario%TYPE;
BEGIN
  SELECT id_usuario INTO v_id_admin FROM USUARIOS WHERE username='admin';
  FOR i IN 1..v_aptos.COUNT LOOP
    SELECT id_apartamento INTO v_id_apt FROM APARTAMENTOS WHERE numero=v_aptos(i).numero;
    INSERT INTO CONTRATOS (id_contrato,id_apartamento,id_registrado_por,fecha_inicio,valor_mensual,dia_pago,dias_gracia,porcentaje_mora,estado,fecha_registro,actualizado_en)
    VALUES (SEC_CONTRATOS.NEXTVAL, v_id_apt, v_id_admin, DATE '2024-01-01',
            v_aptos(i).valor, v_aptos(i).dia, 5, 1.5, 'ACTIVO', SYSTIMESTAMP, SYSTIMESTAMP);
  END LOOP;
  COMMIT;
END;
/

-- ============================== CONTRATO_RESIDENTE + TUTORES ================
DECLARE
  v_cont NUMBER;
  v_cr_id CONTRATO_RESIDENTE.id_contrato_res%TYPE;
BEGIN
  SELECT COUNT(*) INTO v_cont FROM CONTRATOS WHERE estado='ACTIVO';
  IF v_cont = 0 THEN
    DBMS_OUTPUT.PUT_LINE('ERROR: No hay contratos ACTIVOS');
    RETURN;
  END IF;

  -- AP-201: Carlos (ARRENDATARIO), María (OTRO)
  INSERT INTO CONTRATO_RESIDENTE (id_contrato_res,id_contrato,id_residente,rol_en_contrato)
  SELECT SEC_CONTRATO_RESIDENTE.NEXTVAL, c.id_contrato, r.id_residente, 'ARRENDATARIO'
  FROM CONTRATOS c JOIN APARTAMENTOS a ON c.id_apartamento=a.id_apartamento
  CROSS JOIN (SELECT id_residente FROM RESIDENTES WHERE numero_documento='10000001') r
  WHERE a.numero='201' AND c.estado='ACTIVO';
  SELECT SEC_CONTRATO_RESIDENTE.CURRVAL INTO v_cr_id FROM DUAL;
  INSERT INTO CONTRATO_RESIDENTE (id_contrato_res,id_contrato,id_residente,rol_en_contrato)
  SELECT SEC_CONTRATO_RESIDENTE.NEXTVAL, c.id_contrato, r.id_residente, 'OTRO'
  FROM CONTRATOS c JOIN APARTAMENTOS a ON c.id_apartamento=a.id_apartamento
  CROSS JOIN (SELECT id_residente FROM RESIDENTES WHERE numero_documento='10000002') r
  WHERE a.numero='201' AND c.estado='ACTIVO';

  -- AP-202: Jhon (ARRENDATARIO), Luisa (OTRO)
  INSERT INTO CONTRATO_RESIDENTE (id_contrato_res,id_contrato,id_residente,rol_en_contrato)
  SELECT SEC_CONTRATO_RESIDENTE.NEXTVAL, c.id_contrato, r.id_residente, 'ARRENDATARIO'
  FROM CONTRATOS c JOIN APARTAMENTOS a ON c.id_apartamento=a.id_apartamento
  CROSS JOIN (SELECT id_residente FROM RESIDENTES WHERE numero_documento='10000003') r
  WHERE a.numero='202' AND c.estado='ACTIVO';
  INSERT INTO CONTRATO_RESIDENTE (id_contrato_res,id_contrato,id_residente,rol_en_contrato)
  SELECT SEC_CONTRATO_RESIDENTE.NEXTVAL, c.id_contrato, r.id_residente, 'OTRO'
  FROM CONTRATOS c JOIN APARTAMENTOS a ON c.id_apartamento=a.id_apartamento
  CROSS JOIN (SELECT id_residente FROM RESIDENTES WHERE numero_documento='10000004') r
  WHERE a.numero='202' AND c.estado='ACTIVO';

  -- AP-301: Pedro (ARRENDATARIO), Miguel (RESIDENTE_MENOR)
  INSERT INTO CONTRATO_RESIDENTE (id_contrato_res,id_contrato,id_residente,rol_en_contrato)
  SELECT SEC_CONTRATO_RESIDENTE.NEXTVAL, c.id_contrato, r.id_residente, 'ARRENDATARIO'
  FROM CONTRATOS c JOIN APARTAMENTOS a ON c.id_apartamento=a.id_apartamento
  CROSS JOIN (SELECT id_residente FROM RESIDENTES WHERE numero_documento='10000005') r
  WHERE a.numero='301' AND c.estado='ACTIVO';
  INSERT INTO CONTRATO_RESIDENTE (id_contrato_res,id_contrato,id_residente,rol_en_contrato)
  SELECT SEC_CONTRATO_RESIDENTE.NEXTVAL, c.id_contrato, r.id_residente, 'RESIDENTE_MENOR'
  FROM CONTRATOS c JOIN APARTAMENTOS a ON c.id_apartamento=a.id_apartamento
  CROSS JOIN (SELECT id_residente FROM RESIDENTES WHERE numero_documento='50000001') r
  WHERE a.numero='301' AND c.estado='ACTIVO';
  INSERT INTO TUTORES (id_tutor,id_residente_menor,id_tipo_doc,numero_documento,nombres,apellidos,parentesco)
  SELECT SEC_TUTORES.NEXTVAL, r.id_residente, 1, '10000005', 'Pedro Antonio', 'Soto Vargas', 'PADRE'
  FROM RESIDENTES r WHERE r.numero_documento='50000001';

  -- AP-302: Andrés (ARRENDATARIO), Carolina (OTRO)
  INSERT INTO CONTRATO_RESIDENTE (id_contrato_res,id_contrato,id_residente,rol_en_contrato)
  SELECT SEC_CONTRATO_RESIDENTE.NEXTVAL, c.id_contrato, r.id_residente, 'ARRENDATARIO'
  FROM CONTRATOS c JOIN APARTAMENTOS a ON c.id_apartamento=a.id_apartamento
  CROSS JOIN (SELECT id_residente FROM RESIDENTES WHERE numero_documento='10000006') r
  WHERE a.numero='302' AND c.estado='ACTIVO';
  INSERT INTO CONTRATO_RESIDENTE (id_contrato_res,id_contrato,id_residente,rol_en_contrato)
  SELECT SEC_CONTRATO_RESIDENTE.NEXTVAL, c.id_contrato, r.id_residente, 'OTRO'
  FROM CONTRATOS c JOIN APARTAMENTOS a ON c.id_apartamento=a.id_apartamento
  CROSS JOIN (SELECT id_residente FROM RESIDENTES WHERE numero_documento='10000007') r
  WHERE a.numero='302' AND c.estado='ACTIVO';

  -- AP-401: Daniel (ARRENDATARIO), Laura (OTRO)
  INSERT INTO CONTRATO_RESIDENTE (id_contrato_res,id_contrato,id_residente,rol_en_contrato)
  SELECT SEC_CONTRATO_RESIDENTE.NEXTVAL, c.id_contrato, r.id_residente, 'ARRENDATARIO'
  FROM CONTRATOS c JOIN APARTAMENTOS a ON c.id_apartamento=a.id_apartamento
  CROSS JOIN (SELECT id_residente FROM RESIDENTES WHERE numero_documento='10000008') r
  WHERE a.numero='401' AND c.estado='ACTIVO';
  INSERT INTO CONTRATO_RESIDENTE (id_contrato_res,id_contrato,id_residente,rol_en_contrato)
  SELECT SEC_CONTRATO_RESIDENTE.NEXTVAL, c.id_contrato, r.id_residente, 'OTRO'
  FROM CONTRATOS c JOIN APARTAMENTOS a ON c.id_apartamento=a.id_apartamento
  CROSS JOIN (SELECT id_residente FROM RESIDENTES WHERE numero_documento='10000009') r
  WHERE a.numero='401' AND c.estado='ACTIVO';

  -- AP-402: Fernando (ARRENDATARIO), Ana Lucía (OTRO)
  INSERT INTO CONTRATO_RESIDENTE (id_contrato_res,id_contrato,id_residente,rol_en_contrato)
  SELECT SEC_CONTRATO_RESIDENTE.NEXTVAL, c.id_contrato, r.id_residente, 'ARRENDATARIO'
  FROM CONTRATOS c JOIN APARTAMENTOS a ON c.id_apartamento=a.id_apartamento
  CROSS JOIN (SELECT id_residente FROM RESIDENTES WHERE numero_documento='10000010') r
  WHERE a.numero='402' AND c.estado='ACTIVO';
  INSERT INTO CONTRATO_RESIDENTE (id_contrato_res,id_contrato,id_residente,rol_en_contrato)
  SELECT SEC_CONTRATO_RESIDENTE.NEXTVAL, c.id_contrato, r.id_residente, 'OTRO'
  FROM CONTRATOS c JOIN APARTAMENTOS a ON c.id_apartamento=a.id_apartamento
  CROSS JOIN (SELECT id_residente FROM RESIDENTES WHERE numero_documento='10000011') r
  WHERE a.numero='402' AND c.estado='ACTIVO';

  -- AP-501: Gabriel (ARRENDATARIO), Valentina (OTRO)
  INSERT INTO CONTRATO_RESIDENTE (id_contrato_res,id_contrato,id_residente,rol_en_contrato)
  SELECT SEC_CONTRATO_RESIDENTE.NEXTVAL, c.id_contrato, r.id_residente, 'ARRENDATARIO'
  FROM CONTRATOS c JOIN APARTAMENTOS a ON c.id_apartamento=a.id_apartamento
  CROSS JOIN (SELECT id_residente FROM RESIDENTES WHERE numero_documento='10000012') r
  WHERE a.numero='501' AND c.estado='ACTIVO';
  INSERT INTO CONTRATO_RESIDENTE (id_contrato_res,id_contrato,id_residente,rol_en_contrato)
  SELECT SEC_CONTRATO_RESIDENTE.NEXTVAL, c.id_contrato, r.id_residente, 'OTRO'
  FROM CONTRATOS c JOIN APARTAMENTOS a ON c.id_apartamento=a.id_apartamento
  CROSS JOIN (SELECT id_residente FROM RESIDENTES WHERE numero_documento='10000013') r
  WHERE a.numero='501' AND c.estado='ACTIVO';

  -- AP-502: Héctor (ARRENDATARIO), Diana (OTRO)
  INSERT INTO CONTRATO_RESIDENTE (id_contrato_res,id_contrato,id_residente,rol_en_contrato)
  SELECT SEC_CONTRATO_RESIDENTE.NEXTVAL, c.id_contrato, r.id_residente, 'ARRENDATARIO'
  FROM CONTRATOS c JOIN APARTAMENTOS a ON c.id_apartamento=a.id_apartamento
  CROSS JOIN (SELECT id_residente FROM RESIDENTES WHERE numero_documento='10000014') r
  WHERE a.numero='502' AND c.estado='ACTIVO';
  INSERT INTO CONTRATO_RESIDENTE (id_contrato_res,id_contrato,id_residente,rol_en_contrato)
  SELECT SEC_CONTRATO_RESIDENTE.NEXTVAL, c.id_contrato, r.id_residente, 'OTRO'
  FROM CONTRATOS c JOIN APARTAMENTOS a ON c.id_apartamento=a.id_apartamento
  CROSS JOIN (SELECT id_residente FROM RESIDENTES WHERE numero_documento='10000015') r
  WHERE a.numero='502' AND c.estado='ACTIVO';

  COMMIT;
END;
/

-- ============================== CUOTAS_ARRIENDO =============================
DECLARE
  v_valor CONTRATOS.valor_mensual%TYPE;
  v_dia   CONTRATOS.dia_pago%TYPE;
BEGIN
  FOR c IN (SELECT id_contrato, valor_mensual, dia_pago FROM CONTRATOS WHERE estado='ACTIVO') LOOP
    FOR mes IN 2..7 LOOP
      INSERT INTO CUOTAS_ARRIENDO (id_cuota,id_contrato,anio,mes,fecha_limite,valor_base,valor_mora,valor_total,estado,fecha_generacion,actualizado_en)
      VALUES (SEC_CUOTAS_ARRIENDO.NEXTVAL, c.id_contrato, 2025, mes,
              TO_DATE('2025-'||LPAD(mes,2,'0')||'-'||LPAD(c.dia_pago,2,'0'),'YYYY-MM-DD'),
              c.valor_mensual,
              CASE WHEN mes=4 THEN ROUND(c.valor_mensual*0.015) ELSE 0 END,
              CASE WHEN mes=4 THEN ROUND(c.valor_mensual*1.015) ELSE c.valor_mensual END,
              CASE WHEN mes IN (2,3) THEN 'PAGADA' WHEN mes=4 THEN 'EN_MORA' ELSE 'PENDIENTE' END,
              SYSTIMESTAMP, SYSTIMESTAMP);
    END LOOP;
  END LOOP;
  COMMIT;
END;
/

-- ============================== PAGOS =======================================
BEGIN
  FOR c IN (SELECT id_contrato FROM CONTRATOS WHERE estado='ACTIVO') LOOP
    INSERT INTO PAGOS (id_pago,id_cuota,id_registrado_por,fecha_pago,valor_pagado,metodo_pago,referencia,fecha_registro)
    SELECT SEC_PAGOS.NEXTVAL, q.id_cuota, u.id_usuario, q.fecha_limite-1, q.valor_total, 'TRANSFERENCIA', 'TXN-'||q.id_cuota, SYSTIMESTAMP
    FROM CUOTAS_ARRIENDO q
    CROSS JOIN (SELECT id_usuario FROM USUARIOS WHERE username='admin') u
    WHERE q.id_contrato=c.id_contrato AND q.estado='PAGADA';
  END LOOP;
  COMMIT;
END;
/

-- ============================== VISITAS =====================================
-- V1: ACTIVA — Carlos (AP-201) recibe a Roberto
INSERT INTO VISITAS (id_visita,id_contrato_res,id_residente,tiempo_validez_min,cantidad_personas,estado,notas,fecha_registro,actualizado_en)
SELECT SEC_VISITAS.NEXTVAL, cr.id_contrato_res, r.id_residente, 60, 2, 'ACTIVA', 'Amigos con carro', SYSTIMESTAMP - INTERVAL '1' HOUR, SYSTIMESTAMP
FROM RESIDENTES r
JOIN CONTRATO_RESIDENTE cr ON cr.id_residente=r.id_residente AND cr.rol_en_contrato='ARRENDATARIO'
WHERE r.numero_documento='10000001';

-- V2: PENDIENTE — Jhon (AP-202) autoriza a Fernando
INSERT INTO VISITAS (id_visita,id_contrato_res,id_residente,tiempo_validez_min,cantidad_personas,estado,notas,fecha_registro,actualizado_en)
SELECT SEC_VISITAS.NEXTVAL, cr.id_contrato_res, r.id_residente, 30, 1, 'PENDIENTE', 'Técnico de internet', SYSTIMESTAMP - INTERVAL '30' MINUTE, SYSTIMESTAMP
FROM RESIDENTES r
JOIN CONTRATO_RESIDENTE cr ON cr.id_residente=r.id_residente AND cr.rol_en_contrato='ARRENDATARIO'
WHERE r.numero_documento='10000003';

-- V3: FINALIZADA — Carlos recibió a Laura
INSERT INTO VISITAS (id_visita,id_contrato_res,id_residente,tiempo_validez_min,cantidad_personas,estado,notas,fecha_registro,actualizado_en)
SELECT SEC_VISITAS.NEXTVAL, cr.id_contrato_res, r.id_residente, 30, 1, 'FINALIZADA', 'Visita frecuente', SYSTIMESTAMP - INTERVAL '2' DAY, SYSTIMESTAMP
FROM RESIDENTES r
JOIN CONTRATO_RESIDENTE cr ON cr.id_residente=r.id_residente AND cr.rol_en_contrato='ARRENDATARIO'
WHERE r.numero_documento='10000001';

-- V4: CANCELADA — Jhon canceló a Emily
INSERT INTO VISITAS (id_visita,id_contrato_res,id_residente,tiempo_validez_min,cantidad_personas,estado,notas,fecha_registro,actualizado_en)
SELECT SEC_VISITAS.NEXTVAL, cr.id_contrato_res, r.id_residente, 30, 1, 'CANCELADA', 'Cambio de planes', SYSTIMESTAMP - INTERVAL '5' DAY, SYSTIMESTAMP
FROM RESIDENTES r
JOIN CONTRATO_RESIDENTE cr ON cr.id_residente=r.id_residente AND cr.rol_en_contrato='ARRENDATARIO'
WHERE r.numero_documento='10000003';

-- V5: EXPIRADA — QR sin usar
INSERT INTO VISITAS (id_visita,id_contrato_res,id_residente,tiempo_validez_min,cantidad_personas,estado,notas,fecha_registro,actualizado_en)
SELECT SEC_VISITAS.NEXTVAL, cr.id_contrato_res, r.id_residente, 15, 1, 'EXPIRADA', 'QR expiró', SYSTIMESTAMP - INTERVAL '3' DAY, SYSTIMESTAMP
FROM RESIDENTES r
JOIN CONTRATO_RESIDENTE cr ON cr.id_residente=r.id_residente AND cr.rol_en_contrato='ARRENDATARIO'
WHERE r.numero_documento='10000001';

-- Visitas finalizadas para los otros 6 arrendatarios
INSERT INTO VISITAS (id_visita,id_contrato_res,id_residente,tiempo_validez_min,cantidad_personas,estado,notas,fecha_registro,actualizado_en)
SELECT SEC_VISITAS.NEXTVAL, cr.id_contrato_res, r.id_residente, 30, 2, 'FINALIZADA', 'Visita social', SYSTIMESTAMP - INTERVAL '1' DAY, SYSTIMESTAMP
FROM RESIDENTES r
JOIN CONTRATO_RESIDENTE cr ON cr.id_residente=r.id_residente AND cr.rol_en_contrato='ARRENDATARIO'
WHERE r.numero_documento IN ('10000005','10000006','10000008','10000010','10000012','10000014');
COMMIT;

-- ============================== VEHICULOS_VISITA ============================
INSERT INTO VEHICULOS_VISITA (id_vehiculo_visita,id_visita,tipo,placa,descripcion_tipo,marca,id_parqueadero)
SELECT SEC_VEHICULOS_VISITA.NEXTVAL, v.id_visita, 'VEHICULO', 'ABC-123', 'Toyota Corolla gris', 'Toyota', p.id_parqueadero
FROM VISITAS v
CROSS JOIN (SELECT id_parqueadero FROM PARQUEADEROS WHERE codigo='V01') p
WHERE v.estado='ACTIVA';
COMMIT;

-- ============================== REGISTRO_VISITA =============================
-- V1 (ACTIVA): Roberto
INSERT INTO REGISTRO_VISITA (id_registro_visita,id_visita,id_visitante,id_vehiculo_visita,es_titular)
SELECT SEC_REGISTRO_VISITA.NEXTVAL, v.id_visita, vis.id_visitante, vv.id_vehiculo_visita, 1
FROM VISITAS v
JOIN VEHICULOS_VISITA vv ON vv.id_visita=v.id_visita
CROSS JOIN (SELECT id_visitante FROM VISITANTES WHERE numero_documento='20000002') vis
WHERE v.estado='ACTIVA';

-- V2 (PENDIENTE): Fernando
INSERT INTO REGISTRO_VISITA (id_registro_visita,id_visita,id_visitante,es_titular)
SELECT SEC_REGISTRO_VISITA.NEXTVAL, v.id_visita, vis.id_visitante, 1
FROM VISITAS v
CROSS JOIN (SELECT id_visitante FROM VISITANTES WHERE numero_documento='20000003') vis
WHERE v.estado='PENDIENTE';

-- V3 (FINALIZADA): Laura
INSERT INTO REGISTRO_VISITA (id_registro_visita,id_visita,id_visitante,es_titular)
SELECT SEC_REGISTRO_VISITA.NEXTVAL, v.id_visita, vis.id_visitante, 1
FROM VISITAS v
CROSS JOIN (SELECT id_visitante FROM VISITANTES WHERE numero_documento='20000001') vis
WHERE v.notas='Visita frecuente';

-- V4 (CANCELADA): Emily
INSERT INTO REGISTRO_VISITA (id_registro_visita,id_visita,id_visitante,es_titular)
SELECT SEC_REGISTRO_VISITA.NEXTVAL, v.id_visita, vis.id_visitante, 1
FROM VISITAS v
CROSS JOIN (SELECT id_visitante FROM VISITANTES WHERE numero_documento='PAS00001') vis
WHERE v.estado='CANCELADA';

-- V5 (EXPIRADA): Jean Pierre
INSERT INTO REGISTRO_VISITA (id_registro_visita,id_visita,id_visitante,es_titular)
SELECT SEC_REGISTRO_VISITA.NEXTVAL, v.id_visita, vis.id_visitante, 1
FROM VISITAS v
CROSS JOIN (SELECT id_visitante FROM VISITANTES WHERE numero_documento='CE200001') vis
WHERE v.estado='EXPIRADA';

-- Visitas sociales finalizadas
INSERT INTO REGISTRO_VISITA (id_registro_visita,id_visita,id_visitante,es_titular)
SELECT SEC_REGISTRO_VISITA.NEXTVAL, v.id_visita, vis.id_visitante, 1
FROM (SELECT v.id_visita, ROWNUM rn FROM VISITAS v WHERE v.notas='Visita social' ORDER BY v.id_visita) v
JOIN (SELECT vis.id_visitante, ROWNUM rn FROM VISITANTES vis WHERE vis.numero_documento IN ('20000004','20000005','20000006','20000007','20000008') ORDER BY vis.id_visitante) vis ON vis.rn = v.rn;
COMMIT;

-- ============================== QR_ACCESOS ==============================
INSERT INTO QR_ACCESOS (id_qr,id_visita,codigo_qr,fecha_generacion,fecha_expiracion,usado,fecha_uso,id_vigilante_uso)
SELECT SEC_QR_ACCESOS.NEXTVAL, v.id_visita, 'QR-'||RAWTOHEX(SYS_GUID()),
       SYSTIMESTAMP - INTERVAL '1' HOUR, SYSTIMESTAMP + INTERVAL '59' MINUTE, 1,
       SYSTIMESTAMP - INTERVAL '55' MINUTE, u.id_usuario
FROM VISITAS v
CROSS JOIN (SELECT id_usuario FROM USUARIOS WHERE username='portero1') u
WHERE v.estado='ACTIVA';

INSERT INTO QR_ACCESOS (id_qr,id_visita,codigo_qr,fecha_generacion,fecha_expiracion,usado)
SELECT SEC_QR_ACCESOS.NEXTVAL, v.id_visita, 'QR-'||RAWTOHEX(SYS_GUID()),
       SYSTIMESTAMP - INTERVAL '30' MINUTE, SYSTIMESTAMP + INTERVAL '15' MINUTE, 0
FROM VISITAS v WHERE v.estado='PENDIENTE';

INSERT INTO QR_ACCESOS (id_qr,id_visita,codigo_qr,fecha_generacion,fecha_expiracion,usado,fecha_uso,id_vigilante_uso)
SELECT SEC_QR_ACCESOS.NEXTVAL, v.id_visita, 'QR-'||RAWTOHEX(SYS_GUID()),
       SYSTIMESTAMP - INTERVAL '2' DAY, SYSTIMESTAMP - INTERVAL '2' DAY + INTERVAL '30' MINUTE, 1,
       SYSTIMESTAMP - INTERVAL '2' DAY + INTERVAL '10' MINUTE, u.id_usuario
FROM VISITAS v
CROSS JOIN (SELECT id_usuario FROM USUARIOS WHERE username='portero1') u
WHERE v.notas='Visita frecuente';
COMMIT;

-- ============================== REGISTROS_ACCESO ============================
INSERT INTO REGISTROS_ACCESO (id_acceso,id_visita,id_vigilante,hora_entrada,observaciones)
SELECT SEC_REGISTROS_ACCESO.NEXTVAL, v.id_visita, u.id_usuario,
       SYSTIMESTAMP - INTERVAL '55' MINUTE, 'Ingreso QR, vehículo V01'
FROM VISITAS v
CROSS JOIN (SELECT id_usuario FROM USUARIOS WHERE username='portero1') u
WHERE v.estado='ACTIVA';

INSERT INTO REGISTROS_ACCESO (id_acceso,id_visita,id_vigilante,hora_entrada,hora_salida,observaciones)
SELECT SEC_REGISTROS_ACCESO.NEXTVAL, v.id_visita, u.id_usuario,
       SYSTIMESTAMP - INTERVAL '2' DAY, SYSTIMESTAMP - INTERVAL '2' DAY + INTERVAL '90' MINUTE, 'Visita completada'
FROM VISITAS v
CROSS JOIN (SELECT id_usuario FROM USUARIOS WHERE username='portero1') u
WHERE v.notas='Visita frecuente';
COMMIT;

-- ============================== ALERTAS_PAGO ================================
INSERT INTO ALERTAS_PAGO (id_alerta,id_cuota,tipo_alerta,canal,leida,enviada_en)
SELECT SEC_ALERTAS_PAGO.NEXTVAL, q.id_cuota, 'EN_MORA', 'SISTEMA', 0, SYSTIMESTAMP - INTERVAL '5' DAY
FROM CUOTAS_ARRIENDO q WHERE q.estado='EN_MORA';

INSERT INTO ALERTAS_PAGO (id_alerta,id_cuota,tipo_alerta,canal,leida,enviada_en)
SELECT SEC_ALERTAS_PAGO.NEXTVAL, q.id_cuota, 'PROXIMO_VENCIMIENTO', 'SISTEMA', 0, SYSTIMESTAMP
FROM CUOTAS_ARRIENDO q WHERE q.estado='PENDIENTE' AND q.mes=5;
COMMIT;

-- ============================== VERIFICACIÓN ================================
SELECT LPAD(' ',2)||'TOTAL' e, COUNT(*) t FROM APARTAMENTOS
UNION ALL SELECT 'APARTAMENTOS', COUNT(*) FROM APARTAMENTOS
UNION ALL SELECT 'PARQUEADEROS', COUNT(*) FROM PARQUEADEROS
UNION ALL SELECT 'RESIDENTES', COUNT(*) FROM RESIDENTES
UNION ALL SELECT 'USUARIOS', COUNT(*) FROM USUARIOS
UNION ALL SELECT 'CONTRATOS', COUNT(*) FROM CONTRATOS
UNION ALL SELECT 'CONTRATO_RES', COUNT(*) FROM CONTRATO_RESIDENTE
UNION ALL SELECT 'TUTORES', COUNT(*) FROM TUTORES
UNION ALL SELECT 'CUOTAS', COUNT(*) FROM CUOTAS_ARRIENDO
UNION ALL SELECT 'PAGOS', COUNT(*) FROM PAGOS
UNION ALL SELECT 'VISITANTES', COUNT(*) FROM VISITANTES
UNION ALL SELECT 'VISITAS', COUNT(*) FROM VISITAS
UNION ALL SELECT 'VEH_VISITA', COUNT(*) FROM VEHICULOS_VISITA
UNION ALL SELECT 'REG_VISITA', COUNT(*) FROM REGISTRO_VISITA
UNION ALL SELECT 'QR_ACCESOS', COUNT(*) FROM QR_ACCESOS
UNION ALL SELECT 'REG_ACCESO', COUNT(*) FROM REGISTROS_ACCESO
UNION ALL SELECT 'BUZON', COUNT(*) FROM BUZON
UNION ALL SELECT 'MULTAS', COUNT(*) FROM MULTAS
UNION ALL SELECT 'ALERTAS_PAGO', COUNT(*) FROM ALERTAS_PAGO;
