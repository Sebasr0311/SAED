-- ====================================================================
-- SAED 2.0 — V5.3: DESACOPLAMIENTO DE INTEGRIDAD REFERENCIAL EN AUDITORIA
-- Resuelve ORA-00060 (Self-Deadlock) entre transacciones operacionales
-- y transacciones autonomas de auditoria sobre PROPIEDADES.
-- ====================================================================

ALTER TABLE SAED_APP.AUDITORIA_LOG
DROP CONSTRAINT FK_AUDITORIA_PROPIEDAD;
