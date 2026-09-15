-- ══════════════════════════════════════════════════════════════════
-- fix_modelo_v5_2.sql  — Migración incremental a v5.2
-- Aplica sobre una base ya en estado v5.1 (fix_modelo_v5_1.sql ya
-- ejecutado).  Seguro para ejecutar en sqlplus como RESIDENCIAL.
-- ══════════════════════════════════════════════════════════════════
SET SQLBLANKLINES ON
SET DEFINE OFF

-- ──────────────────────────────────────────────────────────────────
-- BLOQUE 1: Purgar papelera de reciclaje
-- Elimina objetos BIN$ residuales de DROPs anteriores (BUZON v4.x).
-- ──────────────────────────────────────────────────────────────────
PURGE RECYCLEBIN;

-- ──────────────────────────────────────────────────────────────────
-- BLOQUE 2: Corrección de datos — estados de apartamentos
-- 203 (id=3) y 303 (id=7) estaban OCUPADO sin contrato ACTIVO.
-- Apt 203: tiene un contrato PENDIENTE_FIRMA (no activado).
-- Apt 303: tiene un contrato SUSPENDIDO.
-- Ambos se marcan DISPONIBLE hasta que exista un ACTIVO.
-- ──────────────────────────────────────────────────────────────────
UPDATE APARTAMENTOS SET estado = 'DISPONIBLE'
WHERE  id_apartamento = 3
AND    NOT EXISTS (
    SELECT 1 FROM CONTRATOS c
    WHERE  c.id_apartamento = 3 AND c.estado = 'ACTIVO'
);

UPDATE APARTAMENTOS SET estado = 'DISPONIBLE'
WHERE  id_apartamento = 7
AND    NOT EXISTS (
    SELECT 1 FROM CONTRATOS c
    WHERE  c.id_apartamento = 7 AND c.estado = 'ACTIVO'
);

-- ──────────────────────────────────────────────────────────────────
-- BLOQUE 3: Corrección de datos — multa #6 con id_mensaje incorrecto
-- id_mensaje apuntaba a una entrada PAQUETE del buzón (ajena a la
-- multa).  Se pone NULL para romper la referencia inválida.
-- ──────────────────────────────────────────────────────────────────
UPDATE MULTAS SET id_mensaje = NULL
WHERE  id_multa = 6
AND    id_mensaje IS NOT NULL
AND    EXISTS (
    SELECT 1 FROM BUZON b
    WHERE  b.id_mensaje = MULTAS.id_mensaje
    AND    b.tipo NOT IN ('QUEJA_RUIDO', 'AVISO')
);

COMMIT;

-- ──────────────────────────────────────────────────────────────────
-- BLOQUE 4: Índices FK faltantes (8 columnas sin índice)
-- Usan CREATE INDEX ... IF NOT EXISTS  NO está disponible en Oracle
-- 18c XE, por lo que se usa un bloque PL/SQL para ignorar ORA-00955
-- (el índice ya existe) si el script se ejecuta más de una vez.
-- ──────────────────────────────────────────────────────────────────
BEGIN
    EXECUTE IMMEDIATE 'CREATE INDEX IDX_BUZON_CREADO_POR ON BUZON(CREADO_POR)';
EXCEPTION WHEN OTHERS THEN IF SQLCODE != -955 THEN RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'CREATE INDEX IDX_CONTRATOS_REGISTRADO_POR ON CONTRATOS(ID_REGISTRADO_POR)';
EXCEPTION WHEN OTHERS THEN IF SQLCODE != -955 THEN RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'CREATE INDEX IDX_MULTAS_APARTAMENTO ON MULTAS(ID_APARTAMENTO)';
EXCEPTION WHEN OTHERS THEN IF SQLCODE != -955 THEN RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'CREATE INDEX IDX_MULTAS_CREADO_POR ON MULTAS(CREADO_POR)';
EXCEPTION WHEN OTHERS THEN IF SQLCODE != -955 THEN RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'CREATE INDEX IDX_MULTAS_MENSAJE ON MULTAS(ID_MENSAJE)';
EXCEPTION WHEN OTHERS THEN IF SQLCODE != -955 THEN RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'CREATE INDEX IDX_MULTAS_PAGO_POR ON MULTAS(REGISTRADO_PAGO_POR)';
EXCEPTION WHEN OTHERS THEN IF SQLCODE != -955 THEN RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'CREATE INDEX IDX_QR_VIGILANTE ON QR_ACCESOS(ID_VIGILANTE_USO)';
EXCEPTION WHEN OTHERS THEN IF SQLCODE != -955 THEN RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'CREATE INDEX IDX_QUEJAS_RESPONDIDO_POR ON QUEJAS_SUGERENCIAS(RESPONDIDO_POR)';
EXCEPTION WHEN OTHERS THEN IF SQLCODE != -955 THEN RAISE; END IF;
END;
/

-- ──────────────────────────────────────────────────────────────────
-- VERIFICACIÓN FINAL
-- ──────────────────────────────────────────────────────────────────
SET LINESIZE 120
SET PAGESIZE 80

PROMPT
PROMPT === Índices creados en v5.2 ===
SELECT index_name, table_name, status
FROM   user_indexes
WHERE  index_name IN (
    'IDX_BUZON_CREADO_POR',
    'IDX_CONTRATOS_REGISTRADO_POR',
    'IDX_MULTAS_APARTAMENTO',
    'IDX_MULTAS_CREADO_POR',
    'IDX_MULTAS_MENSAJE',
    'IDX_MULTAS_PAGO_POR',
    'IDX_QR_VIGILANTE',
    'IDX_QUEJAS_RESPONDIDO_POR'
)
ORDER BY table_name, index_name;

PROMPT
PROMPT === Estado apartamentos 203 y 303 ===
SELECT id_apartamento, numero, estado
FROM   APARTAMENTOS
WHERE  id_apartamento IN (3, 7);

PROMPT
PROMPT === Multa 6 — id_mensaje debe ser NULL ===
SELECT id_multa, tipo, estado, id_mensaje
FROM   MULTAS
WHERE  id_multa = 6;

PROMPT
PROMPT === Objetos en papelera (debe ser 0) ===
SELECT COUNT(*) AS recyclebin_objects FROM user_recyclebin;

PROMPT
PROMPT === Migración v5.2 completada. ===
EXIT
