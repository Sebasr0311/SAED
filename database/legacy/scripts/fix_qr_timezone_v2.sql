-- ============================================================
-- Fix v2: Explicit timezone conversion for QR_ACCESOS timestamps
-- ============================================================
-- Root cause: On ATP, CURRENT_TIMESTAMP may return UTC despite
-- ALTER SESSION SET TIME_ZONE. Using SYSTIMESTAMP AT TIME ZONE 'America/Bogota'
-- AT TIME ZONE 'America/Bogota' guarantees Bogota wall-clock
-- regardless of session timezone.
-- ============================================================

-- ============================================================
-- PARTE 1: SP_LIBERAR_VISITA_FRECUENTE
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
    v_bogota        TIMESTAMP;
BEGIN
    -- Current wall-clock in Bogota (works regardless of session TZ)
    SELECT CAST(SYSTIMESTAMP AT TIME ZONE 'America/Bogota' AS TIMESTAMP)
      INTO v_bogota FROM DUAL;

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
    v_fecha_exp := v_bogota + NUMTODSINTERVAL(p_tiempo_validez, 'MINUTE');

    INSERT INTO QR_ACCESOS (id_visita, codigo_qr, fecha_generacion, fecha_expiracion, usado)
    VALUES (v_id_visita, v_codigo_qr, v_bogota, v_fecha_exp, 0);

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
-- PARTE 2: SP_GENERAR_QR_VISITA (standalone procedure)
-- ============================================================
CREATE OR REPLACE PROCEDURE SP_GENERAR_QR_VISITA (
    p_id_visita  IN  NUMBER,
    p_tiempo_min IN  NUMBER,
    p_codigo_qr  OUT VARCHAR2,
    p_expiracion OUT TIMESTAMP,
    p_mensaje    OUT VARCHAR2
) IS
    v_estado   VISITAS.estado%TYPE;
    v_count    NUMBER;
    v_bogota   TIMESTAMP;
BEGIN
    SELECT CAST(SYSTIMESTAMP AT TIME ZONE 'America/Bogota' AS TIMESTAMP)
      INTO v_bogota FROM DUAL;

    SELECT estado INTO v_estado FROM VISITAS WHERE id_visita = p_id_visita;

    IF v_estado != 'PENDIENTE' THEN
        p_codigo_qr := NULL; p_expiracion := NULL;
        p_mensaje := 'Solo se puede generar QR para visitas PENDIENTE.';
        RETURN;
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM QR_ACCESOS
     WHERE id_visita = p_id_visita AND usado = 0
       AND fecha_expiracion > v_bogota;

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
    p_expiracion := v_bogota + NUMTODSINTERVAL(p_tiempo_min, 'MINUTE');

    INSERT INTO QR_ACCESOS (id_visita, codigo_qr, fecha_generacion, fecha_expiracion, usado)
    VALUES (p_id_visita, p_codigo_qr, v_bogota, p_expiracion, 0);

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
-- PARTE 3: SP_VALIDAR_QR - fix CURRENT_TIMESTAMP comparisons
-- ============================================================
CREATE OR REPLACE PROCEDURE SP_VALIDAR_QR (
    p_codigo_qr     IN  VARCHAR2,
    p_id_vigilante  IN  NUMBER,
    p_valido        OUT NUMBER,
    p_mensaje       OUT VARCHAR2,
    p_cursor        OUT SYS_REFCURSOR
)
AS
    v_id_qr        QR_ACCESOS.id_qr%TYPE;
    v_id_visita    QR_ACCESOS.id_visita%TYPE;
    v_usado        QR_ACCESOS.usado%TYPE;
    v_expiracion   QR_ACCESOS.fecha_expiracion%TYPE;
    v_bogota       TIMESTAMP;
BEGIN
    SELECT CAST(SYSTIMESTAMP AT TIME ZONE 'America/Bogota' AS TIMESTAMP) INTO v_bogota FROM DUAL;

    BEGIN
        SELECT id_qr, id_visita, usado, fecha_expiracion
          INTO v_id_qr, v_id_visita, v_usado, v_expiracion
          FROM QR_ACCESOS
         WHERE codigo_qr = p_codigo_qr
           FOR UPDATE;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            p_valido  := 0;
            p_mensaje := 'QR no encontrado en el sistema.';
            OPEN p_cursor FOR SELECT NULL FROM DUAL WHERE 1=0;
            RETURN;
    END;

    IF v_bogota > v_expiracion THEN
        UPDATE QR_ACCESOS SET usado = 1, fecha_uso = v_bogota
         WHERE id_qr = v_id_qr;
        UPDATE VISITAS SET estado = 'EXPIRADA'
         WHERE id_visita = v_id_visita;
        COMMIT;
        p_valido  := 0;
        p_mensaje := 'QR expirado. Solicite un nuevo codigo al residente.';
        OPEN p_cursor FOR SELECT NULL FROM DUAL WHERE 1=0;
        RETURN;
    END IF;

    IF v_usado = 1 THEN
        p_valido  := 0;
        p_mensaje := 'QR ya fue utilizado. Acceso denegado.';
        OPEN p_cursor FOR SELECT NULL FROM DUAL WHERE 1=0;
        RETURN;
    END IF;

    UPDATE QR_ACCESOS SET
           usado            = 1,
           fecha_uso        = v_bogota,
           id_vigilante_uso = p_id_vigilante
     WHERE id_qr = v_id_qr;

    INSERT INTO REGISTROS_ACCESO (id_visita, id_vigilante, hora_entrada)
    VALUES (v_id_visita, p_id_vigilante, v_bogota);

    COMMIT;

    OPEN p_cursor FOR
        SELECT
            r.nombres  || ' ' || r.apellidos               AS residente_nombre,
            td.codigo  || '-' || r.numero_documento        AS residente_documento,
            a.numero                                        AS numero_apartamento,
            q.fecha_expiracion,
            v.tiempo_validez_min,
            vt.nombres  || ' ' || vt.apellidos             AS visitante_nombre,
            td2.codigo || '-' || vt.numero_documento       AS visitante_documento,
            vh.placa,
            vh.tipo                                         AS vehiculo_tipo
        FROM QR_ACCESOS q
        JOIN VISITAS v ON q.id_visita = v.id_visita
        JOIN RESIDENTES r ON v.id_residente = r.id_residente
        JOIN CONTRATO_RESIDENTE cr ON v.id_contrato_res = cr.id_contrato_res
        JOIN CONTRATOS c ON cr.id_contrato = c.id_contrato
        JOIN APARTAMENTOS a ON c.id_apartamento = a.id_apartamento
        JOIN REGISTRO_VISITA rv ON v.id_visita = rv.id_visita AND rv.es_titular = 1
        JOIN VISITANTES vt ON rv.id_visitante = vt.id_visitante
        JOIN TIPOS_DOCUMENTO td ON td.id_tipo_doc = r.id_tipo_doc
        JOIN TIPOS_DOCUMENTO td2 ON td2.id_tipo_doc = vt.id_tipo_doc
        LEFT JOIN VEHICULOS_VISITA vh ON v.id_visita = vh.id_visita
        WHERE q.id_qr = v_id_qr;

    p_valido  := 1;
    p_mensaje := 'QR valido. Acceso autorizado.';
END SP_VALIDAR_QR;
/

-- ============================================================
-- PARTE 4: Change DEFAULT on fecha_generacion
-- ============================================================
ALTER TABLE QR_ACCESOS MODIFY (fecha_generacion DEFAULT CURRENT_TIMESTAMP);

-- ============================================================
-- PARTE 5: Fix existing records (UTC -> Bogota)
-- ============================================================
UPDATE QR_ACCESOS
   SET fecha_generacion = fecha_generacion - INTERVAL '5' HOUR
 WHERE usado = 0
   AND fecha_generacion > fecha_expiracion;

COMMIT;

-- ============================================================
-- PARTE 6: Verification
-- ============================================================
SELECT SESSIONTIMEZONE, DBTIMEZONE FROM DUAL;
SELECT COUNT(*) AS qrs_corregidos FROM QR_ACCESOS WHERE usado = 0 AND fecha_generacion > fecha_expiracion;
