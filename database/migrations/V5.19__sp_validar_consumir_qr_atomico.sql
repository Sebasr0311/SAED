-- ============================================================================
-- V5.19__sp_validar_consumir_qr_atomico.sql
-- SAED 2.0: Validación y Consumo Atómico de Códigos QR (GAP-F7-02)
-- Procedimiento Almacenado PL/SQL Transaccional con Bloqueo Pesimista
-- Concurrencia de Portería, Validación de Expiración, Estado y Multi-Tenant
-- ============================================================================

CREATE OR REPLACE EDITIONABLE PROCEDURE SP_VALIDAR_CONSUMIR_QR (
    p_token_qr          IN  VARCHAR2,
    p_id_porteria       IN  NUMBER,
    p_portero_usuario   IN  NUMBER,
    p_valido            OUT CHAR,
    p_mensaje           OUT VARCHAR2,
    p_id_visita         OUT NUMBER
) AS
    v_id_qr             QR_ACCESOS.id_qr%TYPE;
    v_fecha_exp         QR_ACCESOS.fecha_expiracion%TYPE;
    v_permitidos        QR_ACCESOS.usos_permitidos%TYPE;
    v_consumidos        QR_ACCESOS.usos_consumidos%TYPE;
    v_estado            QR_ACCESOS.estado%TYPE;
    v_id_unidad         VISITAS.id_unidad%TYPE;
    v_id_visitante      VISITAS.id_visitante%TYPE;
    v_id_persona        VISITANTES.id_persona%TYPE;
    v_id_propiedad      PROPIEDADES.id_propiedad%TYPE;
    v_porteria_count    NUMBER := 0;
    v_prev_state        VARCHAR2(30);
    v_prev_usr          VARCHAR2(30);
    v_prev_org          VARCHAR2(30);
    v_prev_prop         VARCHAR2(30);
    v_prev_rol          VARCHAR2(30);

    PROCEDURE restore_context IS
    BEGIN
        IF v_prev_org IS NOT NULL AND v_prev_usr IS NOT NULL THEN
            BEGIN
                PKG_SAED_SESSION.SET_CONTEXT(
                    p_id_usuario      => TO_NUMBER(v_prev_usr),
                    p_id_organizacion => TO_NUMBER(v_prev_org),
                    p_id_propiedad    => CASE WHEN v_prev_prop IS NOT NULL THEN TO_NUMBER(v_prev_prop) ELSE NULL END,
                    p_rol_codigo      => v_prev_rol
                );
            EXCEPTION WHEN OTHERS THEN NULL;
            END;
        ELSIF v_prev_usr IS NOT NULL THEN
            BEGIN
                PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(TO_NUMBER(v_prev_usr));
            EXCEPTION WHEN OTHERS THEN NULL;
            END;
        END IF;
    END restore_context;
BEGIN
    p_valido := 'N';

    v_prev_state := SYS_CONTEXT('SAED_CTX', 'STATE');
    v_prev_usr   := SYS_CONTEXT('SAED_CTX', 'ID_USUARIO');
    v_prev_org   := SYS_CONTEXT('SAED_CTX', 'ID_ORGANIZACION');
    v_prev_prop  := SYS_CONTEXT('SAED_CTX', 'ID_PROPIEDAD');
    v_prev_rol   := SYS_CONTEXT('SAED_CTX', 'ROL_CODIGO');

    -- Elevación controlada a BOOTSTRAP para que el bloqueo pesimista y lectura
    -- del token no sean filtrados por RLS antes de verificar la pertenencia a la portería
    IF p_portero_usuario IS NOT NULL THEN
        BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(p_portero_usuario); EXCEPTION WHEN OTHERS THEN NULL; END;
    ELSE
        BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); EXCEPTION WHEN OTHERS THEN NULL; END;
    END IF;

    -- 1. Bloqueo pesimista de fila (FOR UPDATE) para serializar consumos concurrentes
    SELECT q.id_qr, q.id_visita, q.fecha_expiracion, q.usos_permitidos, q.usos_consumidos, q.estado,
           v.id_unidad, v.id_visitante, vis.id_persona, p.id_propiedad
    INTO v_id_qr, p_id_visita, v_fecha_exp, v_permitidos, v_consumidos, v_estado,
         v_id_unidad, v_id_visitante, v_id_persona, v_id_propiedad
    FROM QR_ACCESOS q
    JOIN VISITAS v ON v.id_visita = q.id_visita
    JOIN VISITANTES vis ON vis.id_visitante = v.id_visitante
    JOIN UNIDADES u ON u.id_unidad = v.id_unidad
    JOIN PROPIEDADES p ON p.id_propiedad = u.id_propiedad
    WHERE q.token_qr = p_token_qr
    FOR UPDATE OF q.usos_consumidos;

    -- 2. Validación de Estado de QR
    IF v_estado != 'ACTIVO' THEN
        restore_context;
        p_mensaje := 'El código QR no se encuentra activo (Estado: ' || v_estado || ').';
        RETURN;
    END IF;

    -- 3. Validación de Expiración Temporal (Zona horaria Colombia America/Bogota)
    IF v_fecha_exp < FROM_TZ(CAST(SYSTIMESTAMP AS TIMESTAMP), 'America/Bogota') THEN
        UPDATE QR_ACCESOS SET estado = 'EXPIRADO' WHERE id_qr = v_id_qr;
        restore_context;
        p_mensaje := 'El código QR ha expirado.';
        RETURN;
    END IF;

    -- 4. Validación de Aislamiento Multi-Tenant: La portería debe pertenecer a la propiedad del QR
    IF p_id_porteria IS NOT NULL THEN
        SELECT COUNT(1) INTO v_porteria_count
        FROM PORTERIAS
        WHERE id_porteria = p_id_porteria AND id_propiedad = v_id_propiedad;

        IF v_porteria_count = 0 THEN
            restore_context;
            p_mensaje := 'La portería no pertenece a la propiedad del código QR.';
            RETURN;
        END IF;
    END IF;

    -- 5. Validación de Usos Disponibles (Defensa en profundidad contra desbordamiento de CK_QR_USOS)
    IF v_consumidos >= v_permitidos THEN
        UPDATE QR_ACCESOS SET estado = 'USADO' WHERE id_qr = v_id_qr;
        restore_context;
        p_mensaje := 'El código QR ha alcanzado el límite de usos permitidos.';
        RETURN;
    END IF;

    -- 6. Consumo Atómico de Uso
    v_consumidos := v_consumidos + 1;
    UPDATE QR_ACCESOS
    SET usos_consumidos = v_consumidos,
        estado = CASE WHEN v_consumidos >= v_permitidos THEN 'USADO' ELSE 'ACTIVO' END
    WHERE id_qr = v_id_qr;

    -- 7. Registro de Auditoría de Acceso en REGISTROS_ACCESO
    INSERT INTO REGISTROS_ACCESO (
        id_propiedad, id_porteria, id_visita, id_persona, id_unidad, id_qr,
        tipo_movimiento, metodo_autorizacion, portero_operador
    ) VALUES (
        v_id_propiedad, p_id_porteria, p_id_visita, v_id_persona, v_id_unidad, v_id_qr,
        'ENTRADA', 'QR_SCAN', p_portero_usuario
    );

    p_valido := 'S';
    p_mensaje := 'Acceso autorizado exitosamente.';
    restore_context;

EXCEPTION
    WHEN NO_DATA_FOUND THEN
        restore_context;
        p_mensaje := 'Código QR no encontrado o inválido.';
    WHEN OTHERS THEN
        restore_context;
        p_mensaje := 'Error interno al validar QR: ' || SQLERRM;
END SP_VALIDAR_CONSUMIR_QR;
/
