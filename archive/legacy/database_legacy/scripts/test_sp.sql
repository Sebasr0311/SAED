SET SERVEROUTPUT ON
DECLARE
    v_id_visita        NUMBER;
    v_codigo_qr        VARCHAR2(100);
    v_fecha_expiracion TIMESTAMP;
    v_mensaje          VARCHAR2(500);
BEGIN
    -- Probar el SP con datos dummy para ver qué hora genera
    SP_LIBERAR_VISITA_FRECUENTE(
        p_id_visitante      => 1,
        p_id_contrato_res   => 1,
        p_id_residente      => 1,
        p_cantidad_personas => 1,
        p_tiempo_validez    => 30,
        p_tipo_vehiculo     => NULL,
        p_placa             => NULL,
        p_descripcion_tipo  => NULL,
        p_notas             => NULL,
        p_id_visita         => v_id_visita,
        p_codigo_qr         => v_codigo_qr,
        p_fecha_expiracion  => v_fecha_expiracion,
        p_mensaje           => v_mensaje
    );
    DBMS_OUTPUT.put_line('Mensaje: ' || v_mensaje);
    DBMS_OUTPUT.put_line('fecha_expiracion (TIMESTAMP): ' || TO_CHAR(v_fecha_expiracion, 'YYYY-MM-DD HH24:MI:SS'));
    DBMS_OUTPUT.put_line('CURRENT_TIMESTAMP (sesion):   ' || TO_CHAR(CURRENT_TIMESTAMP, 'YYYY-MM-DD HH24:MI:SS TZR'));
    DBMS_OUTPUT.put_line('SYSTIMESTAMP (servidor):      ' || TO_CHAR(SYSTIMESTAMP, 'YYYY-MM-DD HH24:MI:SS TZR'));
END;
/
ROLLBACK;
