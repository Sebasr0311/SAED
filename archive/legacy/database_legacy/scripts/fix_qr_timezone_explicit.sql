-- Fix robusto de zona horaria para QR_ACCESOS en ATP.
-- En ATP la sesion puede quedar en UTC a pesar del ALTER SESSION,
-- o CURRENT_TIMESTAMP puede comportarse como SYSTIMESTAMP en PL/SQL.
-- Usamos conversion explicita UTC -> America/Bogota.

-- ===================================================================
-- PARTE 1: Recompilar SP_LIBERAR_VISITA_FRECUENTE
-- ===================================================================
CREATE OR REPLACE PROCEDURE RESIDENCIAL.SP_LIBERAR_VISITA_FRECUENTE (
    p_id_residente       IN  NUMBER,
    p_id_visitante       IN  NUMBER,
    p_tiempo_validez     IN  NUMBER,
    p_cantidad_personas  IN  NUMBER,
    p_tipo_documento     IN  VARCHAR2,
    p_numero_documento   IN  VARCHAR2,
    p_nombres            IN  VARCHAR2,
    p_apellidos          IN  VARCHAR2,
    p_telefono           IN  VARCHAR2,
    p_tipo_vehiculo      IN  VARCHAR2,
    p_placa              IN  VARCHAR2,
    p_notas              IN  VARCHAR2,
    p_fecha_expiracion   OUT TIMESTAMP,
    p_codigo_qr          OUT VARCHAR2,
    p_id_visita          OUT NUMBER,
    p_mensaje            OUT VARCHAR2
) AS
    v_id_visita         VISITAS.id_visita%TYPE;
    v_codigo_qr         QR_ACCESOS.codigo_qr%TYPE;
    v_fecha_exp         QR_ACCESOS.fecha_expiracion%TYPE;
    v_bogota            TIMESTAMP;
BEGIN
    -- Obtener timestamp Bogota explicitamente (funciona incluso si session TZ = UTC)
    SELECT CAST(SYSTIMESTAMP AT TIME ZONE 'America/Bogota' AS TIMESTAMP) INTO v_bogota FROM DUAL;

    -- 1. Buscar o crear visitante en VISITANTES
    --    1a. Buscar por numero de documento
    DECLARE
        v_id_visitante_existente VISITANTES.id_visitante%TYPE;
    BEGIN
        SELECT id_visitante INTO v_id_visitante_existente
        FROM   VISITANTES
        WHERE  numero_documento = p_numero_documento AND ROWNUM = 1;

        -- 2. Crear la visita usando el id del visitante existente
        INSERT INTO VISITAS (
            id_residente, fecha_registro, cantidad_personas, notas
        ) VALUES (
            p_id_residente, v_bogota, p_cantidad_personas, p_notas
        ) RETURNING id_visita INTO v_id_visita;

        INSERT INTO REGISTRO_VISITA (
            id_visita, id_visitante, es_titular
        ) VALUES (
            v_id_visita, v_id_visitante_existente, 1
        );

    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            -- 1b. No existe → crear visitante
            INSERT INTO VISITANTES (
                tipo_documento, numero_documento, nombres, apellidos, telefono
            ) VALUES (
                p_tipo_documento, p_numero_documento, p_nombres, p_apellidos, p_telefono
            ) RETURNING id_visitante INTO v_id_visitante_existente;

            -- 2b. Crear la visita
            INSERT INTO VISITAS (
                id_residente, fecha_registro, cantidad_personas, notas
            ) VALUES (
                p_id_residente, v_bogota, p_cantidad_personas, p_notas
            ) RETURNING id_visita INTO v_id_visita;

            INSERT INTO REGISTRO_VISITA (
                id_visita, id_visitante, es_titular
            ) VALUES (
                v_id_visita, v_id_visitante_existente, 1
            );
    END;

    -- 3. Asociar visitante frecuente si no existe
    BEGIN
        INSERT INTO VISITANTES_FRECUENTES (id_visitante, id_residente)
        VALUES (v_id_visitante_existente, p_id_residente);
    EXCEPTION
        WHEN DUP_VAL_ON_INDEX THEN NULL;
    END;

    -- 4. Vehiculo
    IF p_tipo_vehiculo IS NOT NULL AND p_placa IS NOT NULL THEN
        INSERT INTO VEHICULOS_VISITA (
            id_visita, placa, tipo, descripcion_tipo
        ) VALUES (
            v_id_visita, UPPER(TRIM(p_placa)), p_tipo_vehiculo,
            CASE WHEN p_tipo_vehiculo = 'OTRO' THEN p_descripcion_tipo ELSE NULL END
        );
    END IF;

    -- 5. QR
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

-- ===================================================================
-- PARTE 2: Recompilar SP_GENERAR_QR_VISITA (dentro de PKG_VISITAS)
-- ===================================================================
CREATE OR REPLACE PACKAGE BODY RESIDENCIAL.PKG_VISITAS AS

    FUNCTION FN_TIENE_QR_ACTIVO (
        p_id_visitante IN NUMBER,
        p_id_residente IN NUMBER
    ) RETURN NUMBER
    IS
        v_count NUMBER := 0;
    BEGIN
        SELECT COUNT(*)
          INTO v_count
          FROM QR_ACCESOS q
          JOIN VISITAS v ON q.id_visita = v.id_visita
          JOIN REGISTRO_VISITA rv ON v.id_visita = rv.id_visita
         WHERE rv.id_visitante = p_id_visitante
           AND v.id_residente = p_id_residente
           AND q.usado = 0
           AND q.fecha_expiracion > CAST(SYSTIMESTAMP AT TIME ZONE 'America/Bogota' AS TIMESTAMP);

        RETURN v_count;
    END FN_TIENE_QR_ACTIVO;

    FUNCTION FN_MINUTOS_RESTANTES_QR (
        p_id_visita IN NUMBER
    ) RETURN NUMBER
    IS
        v_exp QR_ACCESOS.fecha_expiracion%TYPE;
        v_ahora TIMESTAMP;
    BEGIN
        SELECT CAST(SYSTIMESTAMP AT TIME ZONE 'America/Bogota' AS TIMESTAMP) INTO v_ahora FROM DUAL;
        SELECT fecha_expiracion INTO v_exp FROM QR_ACCESOS WHERE id_visita = p_id_visita;
        RETURN ROUND((CAST(v_exp AS DATE) - CAST(v_ahora AS DATE)) * 1440, 1);
    END FN_MINUTOS_RESTANTES_QR;

    PROCEDURE SP_CANCELAR_VISITA (
        p_id_visita IN NUMBER,
        p_mensaje   OUT VARCHAR2
    )
    IS
    BEGIN
        UPDATE QR_ACCESOS SET usado = 1 WHERE id_visita = p_id_visita;
        p_mensaje := 'Visita cancelada exitosamente.';
    EXCEPTION
        WHEN OTHERS THEN
            p_mensaje := 'Error al cancelar: ' || SQLERRM;
    END SP_CANCELAR_VISITA;

    PROCEDURE SP_GENERAR_QR_VISITA (
        p_id_visita     IN  NUMBER,
        p_tiempo_validez IN NUMBER,
        p_codigo_qr     OUT VARCHAR2,
        p_expiracion    OUT TIMESTAMP
    )
    IS
        v_codigo   QR_ACCESOS.codigo_qr%TYPE;
        v_exp      QR_ACCESOS.fecha_expiracion%TYPE;
        v_activo   NUMBER;
        v_bogota   TIMESTAMP;
    BEGIN
        SELECT CAST(SYSTIMESTAMP AT TIME ZONE 'America/Bogota' AS TIMESTAMP) INTO v_bogota FROM DUAL;

        SELECT COUNT(*) INTO v_activo
          FROM QR_ACCESOS
         WHERE id_visita = p_id_visita
           AND usado = 0
           AND fecha_expiracion > v_bogota;

        IF v_activo > 0 THEN
            p_codigo_qr := NULL;
            p_expiracion := NULL;
            RETURN;
        END IF;

        v_codigo := LOWER(RAWTOHEX(SYS_GUID()));
        v_exp    := v_bogota + NUMTODSINTERVAL(p_tiempo_validez, 'MINUTE');

        INSERT INTO QR_ACCESOS (id_visita, codigo_qr, fecha_generacion, fecha_expiracion, usado)
        VALUES (p_id_visita, v_codigo, v_bogota, v_exp, 0);

        COMMIT;

        p_codigo_qr  := v_codigo;
        p_expiracion := v_exp;
    END SP_GENERAR_QR_VISITA;

END PKG_VISITAS;
/

-- ===================================================================
-- PARTE 3: Cambiar DEFAULT SYSTIMESTAMP -> CURRENT_TIMESTAMP en QR_ACCESOS
--          para que cualquier INSERT que omita fecha_generacion use session TZ
-- ===================================================================
ALTER TABLE QR_ACCESOS MODIFY (fecha_generacion DEFAULT CURRENT_TIMESTAMP);

-- ===================================================================
-- PARTE 4: Corregir filas existentes con fecha_generacion en UTC
--          (fecha_generacion > fecha_expiracion indica UTC vs Bogota)
-- ===================================================================
UPDATE QR_ACCESOS
   SET fecha_generacion = fecha_generacion - INTERVAL '5' HOUR
 WHERE usado = 0
   AND fecha_generacion > fecha_expiracion;

COMMIT;

-- ===================================================================
-- PARTE 5: Verificacion
-- ===================================================================
SELECT SESSIONTIMEZONE, DBTIMEZONE FROM DUAL;
SELECT table_name, column_name, data_default
FROM   user_tab_columns
WHERE  table_name = 'QR_ACCESOS' AND column_name = 'FECHA_GENERACION';
SELECT COUNT(*) AS qrs_corregidos FROM QR_ACCESOS WHERE usado = 0 AND fecha_generacion > fecha_expiracion;
