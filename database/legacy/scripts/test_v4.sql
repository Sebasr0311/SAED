SET SERVEROUTPUT ON;
SET VERIFY OFF;

-- Tests de V4.0 Authentication Bootstrap

DECLARE
    v_count NUMBER;
    v_id NUMBER;
    v_hash VARCHAR2(200);
    v_estado VARCHAR2(30);
    v_intentos NUMBER;
    
    v_org NUMBER;
    v_prop NUMBER;
    v_uni NUMBER;
    v_rol VARCHAR2(30);
    v_alcance VARCHAR2(30);
    
    v_status VARCHAR2(50) := 'FAIL';
BEGIN
    DBMS_OUTPUT.PUT_LINE('--- INICIANDO BATERIA DE PRUEBAS V4 AUTH BOOTSTRAP ---');

    -- Test 1: SELECT directo sobre USUARIOS (Debe retornar 0 por contexto nulo)
    BEGIN
        SELECT COUNT(*) INTO v_count FROM SAED_V39_FINAL_TEST.USUARIOS;
        IF v_count = 0 THEN
            DBMS_OUTPUT.PUT_LINE('1. SELECT directo USUARIOS: PASSED (DENEGADO)');
        ELSE
            DBMS_OUTPUT.PUT_LINE('1. SELECT directo USUARIOS: FAILED (ACCESO CONCEDIDO)');
        END IF;
    EXCEPTION WHEN OTHERS THEN
        DBMS_OUTPUT.PUT_LINE('1. SELECT directo USUARIOS: PASSED (ERROR)');
    END;

    -- Test 2: SELECT directo sobre PERSONAS (Debe retornar 0 por contexto nulo)
    BEGIN
        SELECT COUNT(*) INTO v_count FROM SAED_V39_FINAL_TEST.PERSONAS;
        IF v_count = 0 THEN
            DBMS_OUTPUT.PUT_LINE('2. SELECT directo PERSONAS: PASSED (DENEGADO)');
        ELSE
            DBMS_OUTPUT.PUT_LINE('2. SELECT directo PERSONAS: FAILED (ACCESO CONCEDIDO)');
        END IF;
    EXCEPTION WHEN OTHERS THEN
        DBMS_OUTPUT.PUT_LINE('2. SELECT directo PERSONAS: PASSED (ERROR)');
    END;

    -- Test 3: UPDATE USUARIOS directo (Debe actualizar 0 filas por el RLS o lanzar error)
    BEGIN
        EXECUTE IMMEDIATE 'UPDATE SAED_V39_FINAL_TEST.USUARIOS SET intentos_fallidos = 0';
        v_count := SQL%ROWCOUNT;
        IF v_count = 0 THEN
            DBMS_OUTPUT.PUT_LINE('3. UPDATE directo USUARIOS: PASSED (DENEGADO)');
        ELSE
            DBMS_OUTPUT.PUT_LINE('3. UPDATE directo USUARIOS: FAILED ('||v_count||' ACTUALIZADAS)');
        END IF;
    EXCEPTION WHEN OTHERS THEN
         DBMS_OUTPUT.PUT_LINE('3. UPDATE directo USUARIOS: PASSED (ERROR)');
    END;

    -- Test 4: DELETE USUARIOS directo
    BEGIN
        EXECUTE IMMEDIATE 'DELETE FROM SAED_V39_FINAL_TEST.USUARIOS';
        v_count := SQL%ROWCOUNT;
        IF v_count = 0 THEN
            DBMS_OUTPUT.PUT_LINE('4. DELETE directo USUARIOS: PASSED (DENEGADO)');
        ELSE
            DBMS_OUTPUT.PUT_LINE('4. DELETE directo USUARIOS: FAILED ('||v_count||' BORRADAS)');
        END IF;
    EXCEPTION WHEN OTHERS THEN
         DBMS_OUTPUT.PUT_LINE('4. DELETE directo USUARIOS: PASSED (ERROR)');
    END;

    -- Test 5: GET_AUTH_DATA con usuario válido
    BEGIN
        -- Suponiendo que admin@saed.com existe por test_auth.sql
        SAED_SEC_MASTER.PKG_AUTH_BOOTSTRAP.GET_AUTH_DATA('admin@saed.com', v_id, v_hash, v_estado, v_intentos);
        IF v_id IS NOT NULL THEN
            DBMS_OUTPUT.PUT_LINE('5. GET_AUTH_DATA válido: PASSED (PERMITIDO)');
        ELSE
            DBMS_OUTPUT.PUT_LINE('5. GET_AUTH_DATA válido: FAILED (NO ENCONTRADO)');
        END IF;
    EXCEPTION WHEN OTHERS THEN
        DBMS_OUTPUT.PUT_LINE('5. GET_AUTH_DATA válido: FAILED ('||SQLERRM||')');
    END;

    -- Test 6: GET_AUTH_DATA con usuario inexistente
    BEGIN
        SAED_SEC_MASTER.PKG_AUTH_BOOTSTRAP.GET_AUTH_DATA('noexiste@saed.com', v_id, v_hash, v_estado, v_intentos);
        IF v_id IS NULL THEN
            DBMS_OUTPUT.PUT_LINE('6. GET_AUTH_DATA inexistente: PASSED (NULL DEVUELTO)');
        ELSE
            DBMS_OUTPUT.PUT_LINE('6. GET_AUTH_DATA inexistente: FAILED (INFO DEVUELTA)');
        END IF;
    EXCEPTION WHEN OTHERS THEN
        DBMS_OUTPUT.PUT_LINE('6. GET_AUTH_DATA inexistente: FAILED ('||SQLERRM||')');
    END;
    
    -- Test 7: GET_ASSIGNMENT_CONTEXT con assignment inexistente (v_id = 9999)
    BEGIN
        SAED_SEC_MASTER.PKG_AUTH_BOOTSTRAP.GET_ASSIGNMENT_CONTEXT(1, 9999, v_org, v_prop, v_uni, v_rol, v_alcance);
        IF v_org IS NULL AND v_rol IS NULL THEN
            DBMS_OUTPUT.PUT_LINE('7. GET_ASSIGNMENT_CONTEXT inexistente: PASSED (NULL)');
        ELSE
            DBMS_OUTPUT.PUT_LINE('7. GET_ASSIGNMENT_CONTEXT inexistente: FAILED (INFO DEVUELTA)');
        END IF;
    EXCEPTION WHEN OTHERS THEN
        DBMS_OUTPUT.PUT_LINE('7. GET_ASSIGNMENT_CONTEXT inexistente: FAILED ('||SQLERRM||')');
    END;

    -- Comprobar EXEMPT ACCESS POLICY en el esquema actual
    BEGIN
        SELECT COUNT(*) INTO v_count FROM USER_SYS_PRIVS WHERE PRIVILEGE = 'EXEMPT ACCESS POLICY';
        IF v_count = 0 THEN
            DBMS_OUTPUT.PUT_LINE('8. Verificar EXEMPT ACCESS POLICY: PASSED (NO POSEE)');
        ELSE
            DBMS_OUTPUT.PUT_LINE('8. Verificar EXEMPT ACCESS POLICY: FAILED (POSEE PRIVILEGIO)');
        END IF;
    END;

END;
/
EXIT;
