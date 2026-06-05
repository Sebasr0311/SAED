-- ============================================================
-- Fix: SP_LIBERAR_VISITA_FRECUENTE y SP_GENERAR_QR_VISITA
-- ============================================================
-- 1. SYSTIMESTAMP -> CURRENT_TIMESTAMP en fecha_expiracion
-- 2. Agregar fecha_generacion = CURRENT_TIMESTAMP al INSERT
-- 3. Eliminar QRs bugueados (usado=0 con timestamp UTC)
-- ============================================================

-- ============================================================
-- PARTE 1: Actualizar SP_LIBERAR_VISITA_FRECUENTE
-- ============================================================
CREATE OR REPLACE PROCEDURE SP_LIBERAR_VISITA_FRECUENTE (
    p_id_visitante      IN  NUMBER,
    p_id_contrato_res   IN  NUMBER,
    p_id_residente      IN  NUMBER,
    p_cantidad_personas IN  NUMBER,
    p_tiempo_validez    IN  NUMBER,
    p_tipo_vehiculo     IN  VARCHAR2,
    p_placa             IN  VARCHAR2,
    p_descripcion_tipo  IN  VARCHAR2,
    p_notas             IN  VARCHAR2,
    p_id_visita         OUT NUMBER,
    p_codigo_qr         OUT VARCHAR2,
    p_fecha_expiracion  OUT TIMESTAMP,
    p_mensaje           OUT VARCHAR2
)
AS
    v_id_visita     VISITAS.id_visita%TYPE;
    v_count         NUMBER;
    v_codigo_qr     VARCHAR2(255);
    v_fecha_exp     TIMESTAMP;
BEGIN
    SELECT COUNT(*) INTO v_count
      FROM FRECUENTES_RESIDENTE
     WHERE id_residente = p_id_residente
       AND id_visitante = p_id_visitante
       AND activo       = 1;

    IF v_count = 0 THEN
        p_id_visita := NULL; p_codigo_qr := NULL; p_fecha_expiracion := NULL;
        p_mensaje := 'El visitante no figura como frecuente activo de este residente.';
        RETURN;
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM CONTRATO_RESIDENTE cr
      JOIN CONTRATOS          c  ON c.id_contrato = cr.id_contrato
     WHERE cr.id_contrato_res = p_id_contrato_res
       AND cr.id_residente    = p_id_residente
       AND c.estado           = 'ACTIVO';

    IF v_count = 0 THEN
        p_id_visita := NULL; p_codigo_qr := NULL; p_fecha_expiracion := NULL;
        p_mensaje := 'No se encontro un contrato ACTIVO para este residente.';
        RETURN;
    END IF;

    IF p_tiempo_validez NOT BETWEEN 5 AND 60 THEN
        p_id_visita := NULL; p_codigo_qr := NULL; p_fecha_expiracion := NULL;
        p_mensaje := 'El tiempo de validez del QR debe estar entre 5 y 60 minutos.';
        RETURN;
    END IF;

    IF p_tipo_vehiculo = 'OTRO'
       AND (p_descripcion_tipo IS NULL OR TRIM(p_descripcion_tipo) IS NULL) THEN
        p_id_visita := NULL; p_codigo_qr := NULL; p_fecha_expiracion := NULL;
        p_mensaje := 'Debe especificar la descripcion del vehiculo cuando el tipo es OTRO.';
        RETURN;
    END IF;

    INSERT INTO VISITAS (
        id_contrato_res, id_residente, tiempo_validez_min,
        cantidad_personas, notas, estado
    ) VALUES (
        p_id_contrato_res, p_id_residente, p_tiempo_validez,
        p_cantidad_personas, p_notas, 'PENDIENTE'
    ) RETURNING id_visita INTO v_id_visita;

    INSERT INTO REGISTRO_VISITA (id_visita, id_visitante, es_titular)
    VALUES (v_id_visita, p_id_visitante, 1);

    IF p_tipo_vehiculo IS NOT NULL AND p_placa IS NOT NULL THEN
        INSERT INTO VEHICULOS_VISITA (id_visita, placa, tipo, descripcion_tipo)
        VALUES (v_id_visita, UPPER(TRIM(p_placa)), p_tipo_vehiculo,
                CASE WHEN p_tipo_vehiculo = 'OTRO' THEN p_descripcion_tipo ELSE NULL END);
    END IF;

    v_codigo_qr := LOWER(RAWTOHEX(SYS_GUID()));
    v_fecha_exp := CURRENT_TIMESTAMP + NUMTODSINTERVAL(p_tiempo_validez, 'MINUTE');

    INSERT INTO QR_ACCESOS (id_visita, codigo_qr, fecha_generacion, fecha_expiracion, usado)
    VALUES (v_id_visita, v_codigo_qr, CURRENT_TIMESTAMP, v_fecha_exp, 0);

    COMMIT;

    p_id_visita        := v_id_visita;
    p_codigo_qr        := v_codigo_qr;
    p_fecha_expiracion := v_fecha_exp;
    p_mensaje := 'Visita frecuente registrada. QR generado correctamente.';

EXCEPTION
    WHEN OTHERS THEN
        ROLLBACK;
        p_id_visita := NULL; p_codigo_qr := NULL; p_fecha_expiracion := NULL;
        p_mensaje := 'Error interno: ' || SQLERRM;
END SP_LIBERAR_VISITA_FRECUENTE;
/

-- ============================================================
-- PARTE 2: Actualizar SP_GENERAR_QR_VISITA
-- ============================================================
CREATE OR REPLACE PROCEDURE SP_GENERAR_QR_VISITA (
    p_id_visita  IN  NUMBER,
    p_tiempo_min IN  NUMBER,
    p_codigo_qr  OUT VARCHAR2,
    p_expiracion OUT TIMESTAMP,
    p_mensaje    OUT VARCHAR2
) IS
    v_estado VISITAS.estado%TYPE;
    v_count  NUMBER;
BEGIN
    SELECT estado INTO v_estado FROM VISITAS WHERE id_visita = p_id_visita;

    IF v_estado != 'PENDIENTE' THEN
        p_codigo_qr := NULL; p_expiracion := NULL;
        p_mensaje := 'Solo se puede generar QR para visitas PENDIENTE.';
        RETURN;
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM QR_ACCESOS
     WHERE id_visita = p_id_visita AND usado = 0
       AND fecha_expiracion > CURRENT_TIMESTAMP;

    IF v_count > 0 THEN
        p_codigo_qr := NULL; p_expiracion := NULL;
        p_mensaje := 'Ya existe un QR activo para esta visita.';
        RETURN;
    END IF;

    IF p_tiempo_min NOT BETWEEN 5 AND 60 THEN
        p_codigo_qr := NULL; p_expiracion := NULL;
        p_mensaje := 'El tiempo de validez debe estar entre 5 y 60 minutos.';
        RETURN;
    END IF;

    p_codigo_qr  := LOWER(RAWTOHEX(SYS_GUID()));
    p_expiracion := CURRENT_TIMESTAMP + NUMTODSINTERVAL(p_tiempo_min, 'MINUTE');

    INSERT INTO QR_ACCESOS (id_visita, codigo_qr, fecha_generacion, fecha_expiracion, usado)
    VALUES (p_id_visita, p_codigo_qr, CURRENT_TIMESTAMP, p_expiracion, 0);

    COMMIT;
    p_mensaje := 'QR generado. Vigencia: ' || p_tiempo_min || ' minuto(s).';
EXCEPTION
    WHEN NO_DATA_FOUND THEN
        p_codigo_qr := NULL; p_expiracion := NULL;
        p_mensaje := 'Visita no encontrada (id_visita=' || p_id_visita || ').';
    WHEN OTHERS THEN
        ROLLBACK;
        p_codigo_qr := NULL; p_expiracion := NULL;
        p_mensaje := 'Error interno: ' || SQLERRM;
END SP_GENERAR_QR_VISITA;
/

-- ============================================================
-- PARTE 3: Eliminar QRs bugueados (generados con SYSTIMESTAMP)
-- ============================================================
-- Identificamos QRs no usados donde fecha_expiracion al ser
-- interpretada como Bogota por JDBC queda en "futuro" (> SYSTIMESTAMP + 3h).
-- Esto sucede porque el valor almacenado esta en UTC (5h adelante).
-- ============================================================
DELETE FROM QR_ACCESOS q
WHERE q.usado = 0
  AND q.fecha_expiracion > SYSTIMESTAMP + INTERVAL '3' HOUR;

PROMPT 'SPs actualizados + QRs bugueados eliminados.'
