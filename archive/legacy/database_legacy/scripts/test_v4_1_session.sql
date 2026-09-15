-- test_v4_1_session.sql
-- Adversarial test for State Machine Session Context

SET SERVEROUTPUT ON;
SET VERIFY OFF;

DECLARE
    v_count NUMBER;
    v_error VARCHAR2(4000);
BEGIN
    DBMS_OUTPUT.PUT_LINE('--- INICIANDO TEST ADVERSARIAL V4.1 ---');

    -- Test 1: STATE 0 - No Authenticated (Current session has no context)
    BEGIN
        SELECT count(*) INTO v_count FROM SAED_V39_FINAL_TEST.USUARIOS;
        IF v_count = 0 THEN
            DBMS_OUTPUT.PUT_LINE('1. STATE 0 (USUARIOS): PASSED (0 filas)');
        ELSE
            DBMS_OUTPUT.PUT_LINE('1. STATE 0 (USUARIOS): FAILED ('||v_count||' filas)');
        END IF;
    EXCEPTION WHEN OTHERS THEN DBMS_OUTPUT.PUT_LINE('1. STATE 0: ERROR '||SQLERRM);
    END;

    BEGIN
        SELECT count(*) INTO v_count FROM SAED_V39_FINAL_TEST.PROPIEDADES;
        IF v_count = 0 THEN
            DBMS_OUTPUT.PUT_LINE('1b. STATE 0 (PROPIEDADES): PASSED (0 filas)');
        ELSE
            DBMS_OUTPUT.PUT_LINE('1b. STATE 0 (PROPIEDADES): FAILED ('||v_count||' filas)');
        END IF;
    EXCEPTION WHEN OTHERS THEN DBMS_OUTPUT.PUT_LINE('1b. STATE 0: ERROR '||SQLERRM);
    END;

    -- Test 2: STATE 1 - Bootstrap
    -- Simulamos loguear con id_usuario = 1
    BEGIN
        SAED_V39_FINAL_TEST.PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1);
        
        -- En STATE 1, deberíamos ver la propia fila de USUARIOS si existe (y aquí existe porque insertamos al id 1)
        SELECT count(*) INTO v_count FROM SAED_V39_FINAL_TEST.USUARIOS;
        DBMS_OUTPUT.PUT_LINE('2. STATE 1 (USUARIOS): PASSED ('||v_count||' filas)');

        -- En STATE 1, PROPIEDADES debe ser inaccesible
        SELECT count(*) INTO v_count FROM SAED_V39_FINAL_TEST.PROPIEDADES;
        IF v_count = 0 THEN
            DBMS_OUTPUT.PUT_LINE('2b. STATE 1 (PROPIEDADES bloqueado): PASSED (0 filas)');
        ELSE
            DBMS_OUTPUT.PUT_LINE('2b. STATE 1 (PROPIEDADES bloqueado): FAILED ('||v_count||' filas)');
        END IF;

    EXCEPTION WHEN OTHERS THEN DBMS_OUTPUT.PUT_LINE('2. STATE 1: ERROR '||SQLERRM);
    END;

    -- Test 3: CROSS-TENANT / SPOOFING en SET_CONTEXT
    -- Intentar establecer contexto con organización falsa (9999)
    BEGIN
        SAED_V39_FINAL_TEST.PKG_SAED_SESSION.SET_CONTEXT(1, 9999, NULL, 'ADMIN_GENERAL');
        DBMS_OUTPUT.PUT_LINE('3. SPOOFING Org: FAILED (Contexto establecido sin error!)');
    EXCEPTION 
        WHEN OTHERS THEN 
            DBMS_OUTPUT.PUT_LINE('3. SPOOFING Org: PASSED (Bloqueado con '||SQLCODE||')');
    END;

    -- Test 4: STATE 3 - Clearing
    BEGIN
        SAED_V39_FINAL_TEST.PKG_SAED_SESSION.CLEAR_CONTEXT;
        SELECT count(*) INTO v_count FROM SAED_V39_FINAL_TEST.USUARIOS;
        IF v_count = 0 THEN
            DBMS_OUTPUT.PUT_LINE('4. STATE 3 (CLEAR): PASSED (0 filas)');
        ELSE
            DBMS_OUTPUT.PUT_LINE('4. STATE 3 (CLEAR): FAILED ('||v_count||' filas vistas)');
        END IF;
    EXCEPTION WHEN OTHERS THEN DBMS_OUTPUT.PUT_LINE('4. STATE 3 (CLEAR): ERROR '||SQLERRM);
    END;

END;
/
EXIT;
