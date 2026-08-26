INSERT INTO PERSONAS (tipo_identificacion, numero_identificacion, nombres, apellidos, email) VALUES ('CC', '123456', 'Juan', 'Perez', 'admin@saed.com');
INSERT INTO USUARIOS (id_persona, nombre_usuario, email, hash_password, estado) VALUES ((SELECT id_persona FROM PERSONAS WHERE numero_identificacion='123456'), 'admin', 'admin@saed.com', 'hash', 'ACTIVO');
INSERT INTO ADMINISTRADORES_SAED (id_usuario, nivel) VALUES ((SELECT id_usuario FROM USUARIOS WHERE email='admin@saed.com'), 'SOPORTE');
COMMIT;

SET SERVEROUTPUT ON;
BEGIN
    PKG_SAED_SESSION.SET_CONTEXT((SELECT id_usuario FROM USUARIOS WHERE email='admin@saed.com'), 0, NULL, 'SUPERADMIN');
    DBMS_OUTPUT.PUT_LINE('EXITO');
EXCEPTION
    WHEN OTHERS THEN
        DBMS_OUTPUT.PUT_LINE('ERROR: ' || SQLERRM);
END;
/
exit;
