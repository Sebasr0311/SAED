-- Migración: Agregar columnas foto_url y doc_pdf_url faltantes
-- Ejecutar en SQL Developer conectado al usuario AdministracionPrueba

ALTER TABLE VISITANTES ADD foto_url VARCHAR2(500);
ALTER TABLE VISITANTES ADD doc_pdf_url VARCHAR2(500);

ALTER TABLE TUTORES ADD doc_pdf_url VARCHAR2(500);

COMMIT;

SELECT 'Columnas agregadas correctamente' AS mensaje FROM dual;
