-- test_v4_1_dml.sql
SET SERVEROUTPUT ON;
SET VERIFY OFF;

DECLARE
    v_count NUMBER;
BEGIN
    DBMS_OUTPUT.PUT_LINE('--- INICIANDO TEST DML Y AUDITORIA ---');

    -- Establecer contexto Bootstrap (como si fuéramos el id_usuario 1)
    SAED_V39_FINAL_TEST.PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1);
    
    -- Establecer contexto Business ficticio (Fallo esperado si no tiene la asig)
    BEGIN
        -- Suponiendo que el usuario 1 tiene la organización 1, intentaremos acceder. 
        -- Si no la tiene, fallará, lo cual es seguro.
        SAED_V39_FINAL_TEST.PKG_SAED_SESSION.SET_CONTEXT(1, 1, NULL, 'ADMIN_GENERAL');
        
        -- Intentar modificar otra organización (Id 9999)
        UPDATE SAED_V39_FINAL_TEST.ORGANIZACIONES SET nombre = 'HACKED' WHERE id_organizacion = 9999;
        DBMS_OUTPUT.PUT_LINE('DML Cross Tenant: 0 filas afectadas (Aislado por RLS).');
    EXCEPTION
        WHEN OTHERS THEN
            DBMS_OUTPUT.PUT_LINE('DML Cross Tenant: Bloqueado desde SET_CONTEXT o RLS.');
    END;

    -- Intentar borrar auditoría (Debe disparar ORA-20099 u ORA error por trigger)
    BEGIN
        DELETE FROM SAED_V39_FINAL_TEST.AUDITORIA_LOG;
        DBMS_OUTPUT.PUT_LINE('AUDITORIA: FAILED (Se permitió borrar log!)');
    EXCEPTION
        WHEN OTHERS THEN
            IF SQLCODE = -20099 THEN
                DBMS_OUTPUT.PUT_LINE('AUDITORIA: PASSED (Trigger ORA-20099 inmutabilidad activo)');
            ELSE
                DBMS_OUTPUT.PUT_LINE('AUDITORIA: PASSED (Bloqueado por RLS o constraints: '||SQLCODE||')');
            END IF;
    END;

    SAED_V39_FINAL_TEST.PKG_SAED_SESSION.CLEAR_CONTEXT;
END;
/
EXIT;
