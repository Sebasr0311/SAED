ALTER SESSION SET CONTAINER=XEPDB1;
INSERT INTO SAED_V39_FINAL_TEST.TIPOS_DOCUMENTO(codigo, nombre) VALUES ('CC', 'CEDULA');
INSERT INTO SAED_V39_FINAL_TEST.PERSONAS(id_tipo_documento, numero_documento, primer_nombre, primer_apellido) VALUES (1, '123', 'A', 'B');
INSERT INTO SAED_V39_FINAL_TEST.USUARIOS(id_persona, nombre_usuario, email, hash_password, estado) VALUES (1, 'admin', 'admin@saed.com', 'hash', 'ACTIVO');
COMMIT;
EXIT;
