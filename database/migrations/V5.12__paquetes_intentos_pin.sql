-- Migración V5.12: Agregar columna de intentos de PIN para protección anti-fuerza bruta en PAQUETES
-- Compatible con RLS (se agrega sin DEFAULT inicial y luego se aplica DEFAULT 0)

ALTER TABLE PAQUETES ADD (INTENTOS_FALLIDOS_PIN NUMBER);
ALTER TABLE PAQUETES MODIFY (INTENTOS_FALLIDOS_PIN DEFAULT 0);

COMMIT;
