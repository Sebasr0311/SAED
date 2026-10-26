-- =============================================================================
-- SAED 2.0 - V5.31: REPORTES_CONFIGURADOS TENANT SCOPE (F01-01 / F11-01)
-- =============================================================================
-- Versions the schema that ProductionSchemaInitializer.initReportesConfiguradosYHistorialPipeline
-- previously created only at application startup. V5.28 already installs RLS predicates that
-- reference ID_ORGANIZACION / ID_PROPIEDAD on this table, so a migration-only schema without
-- these columns fails every query on REPORTES_CONFIGURADOS.
--
-- Idempotent: existing environments that already received these objects at startup are unchanged.
-- Avoids patterns that fail under active VPD for a non-exempt owner:
--   * no ADD COLUMN with a non-constant DEFAULT (full-table rewrite, ORA-28133);
--   * no MERGE on a policy-protected table (ORA-28132);
--   * index creation tolerates only ORA-01408 (equivalent column list already indexed).

BEGIN
    PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1);
    PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN');
END;
/

-- 1. Tenant-scope and audit columns (metadata-only additions)
DECLARE
    PROCEDURE add_column(p_column IN VARCHAR2, p_definition IN VARCHAR2) IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(1) INTO v_count FROM USER_TAB_COLS
        WHERE TABLE_NAME = 'REPORTES_CONFIGURADOS' AND COLUMN_NAME = p_column;
        IF v_count = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE REPORTES_CONFIGURADOS ADD (' || p_column || ' ' || p_definition || ')';
        END IF;
    END;
BEGIN
    add_column('ID_ORGANIZACION', 'NUMBER(19)');
    add_column('ID_PROPIEDAD', 'NUMBER(19)');
    add_column('PARAMETROS_FILTRO_JSON', 'CLOB');
    add_column('ID_USUARIO_CREO', 'NUMBER(19)');
    add_column('FECHA_CREACION', 'TIMESTAMP');
    add_column('FECHA_ACTUALIZACION', 'TIMESTAMP');
END;
/

-- 2. Default for future inserts only (does not rewrite existing rows)
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(1) INTO v_count FROM USER_TAB_COLS
    WHERE TABLE_NAME = 'REPORTES_CONFIGURADOS' AND COLUMN_NAME = 'FECHA_CREACION' AND DATA_DEFAULT IS NULL;
    IF v_count = 1 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE REPORTES_CONFIGURADOS MODIFY (FECHA_CREACION DEFAULT CURRENT_TIMESTAMP)';
    END IF;
END;
/

-- 3. Foreign keys (same names and actions as the former startup DDL)
DECLARE
    PROCEDURE add_fk(p_name IN VARCHAR2, p_definition IN VARCHAR2) IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(1) INTO v_count FROM USER_CONSTRAINTS WHERE CONSTRAINT_NAME = p_name;
        IF v_count = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE REPORTES_CONFIGURADOS ADD CONSTRAINT ' || p_name || ' ' || p_definition;
        END IF;
    END;
BEGIN
    add_fk('FK_REPCFG_ORG', 'FOREIGN KEY (ID_ORGANIZACION) REFERENCES ORGANIZACIONES(ID_ORGANIZACION) ON DELETE CASCADE');
    add_fk('FK_REPCFG_PROP', 'FOREIGN KEY (ID_PROPIEDAD) REFERENCES PROPIEDADES(ID_PROPIEDAD) ON DELETE CASCADE');
    add_fk('FK_REPCFG_USR', 'FOREIGN KEY (ID_USUARIO_CREO) REFERENCES USUARIOS(ID_USUARIO) ON DELETE SET NULL');
END;
/

-- 4. Indexes
DECLARE
    PROCEDURE add_index(p_name IN VARCHAR2, p_definition IN VARCHAR2) IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(1) INTO v_count FROM USER_INDEXES WHERE INDEX_NAME = p_name;
        IF v_count = 0 THEN
            BEGIN
                EXECUTE IMMEDIATE 'CREATE INDEX ' || p_name || ' ON ' || p_definition;
            EXCEPTION
                WHEN OTHERS THEN
                    IF SQLCODE != -1408 THEN RAISE; END IF;
            END;
        END IF;
    END;
BEGIN
    add_index('IX_REPCFG_ORG_PROP', 'REPORTES_CONFIGURADOS (ID_ORGANIZACION, ID_PROPIEDAD)');
    add_index('IX_REPCFG_CLAVE', 'REPORTES_CONFIGURADOS (CONSULTA_ORIGEN_CLAVE)');
    add_index('IX_HISTREP_ORG_PROP', 'HISTORIAL_REPORTES (ID_ORGANIZACION, ID_PROPIEDAD, FECHA_EJECUCION)');
END;
/

-- 5. System report templates (ID_ORGANIZACION = NULL); CODIGO is globally unique (UQ_REPORTESCONF_COD)
INSERT INTO REPORTES_CONFIGURADOS (CODIGO, NOMBRE, MODULO, DESCRIPCION, FORMATO_SALIDA_DEFECTO, CONSULTA_ORIGEN_CLAVE, ROL_MINIMO_EJECUCION, ESTADO)
SELECT s.CODIGO, s.NOMBRE, s.MODULO, s.DESCRIPCION, s.FORMATO_SALIDA_DEFECTO, s.CONSULTA_ORIGEN_CLAVE, s.ROL_MINIMO_EJECUCION, s.ESTADO
FROM (
    SELECT 'CARTERA_MOROSA' AS CODIGO, 'Reporte de Cartera Morosa' AS NOMBRE, 'FINANZAS' AS MODULO,
           'Estado de cuentas por cobrar y morosidad de unidades residenciales o comerciales.' AS DESCRIPCION,
           'PDF' AS FORMATO_SALIDA_DEFECTO, 'CARTERA_MOROSA' AS CONSULTA_ORIGEN_CLAVE,
           'ADMIN_PROPIEDAD' AS ROL_MINIMO_EJECUCION, 'ACTIVO' AS ESTADO FROM DUAL
    UNION ALL
    SELECT 'EJECUCION_CUOTAS', 'Reporte de Ejecución de Cuotas', 'FINANZAS',
           'Resumen mensual y estado de recaudo de cuotas emitidas por unidad.',
           'PDF', 'EJECUCION_CUOTAS', 'ADMIN_PROPIEDAD', 'ACTIVO' FROM DUAL
    UNION ALL
    SELECT 'PAGOS_RECIENTES', 'Reporte de Pagos Recientes', 'FINANZAS',
           'Detalle cronológico de recaudos, métodos de pago y comprobantes.',
           'PDF', 'PAGOS_RECIENTES', 'ADMIN_PROPIEDAD', 'ACTIVO' FROM DUAL
    UNION ALL
    SELECT 'EJECUCION_PRESUPUESTAL', 'Reporte de Ejecución Presupuestal', 'FINANZAS',
           'Comparativo analítico entre presupuesto aprobado y pagos reales del período.',
           'PDF', 'EJECUCION_PRESUPUESTAL', 'ADMIN_PROPIEDAD', 'ACTIVO' FROM DUAL
) s
WHERE NOT EXISTS (SELECT 1 FROM REPORTES_CONFIGURADOS rc WHERE rc.CODIGO = s.CODIGO);

COMMIT;
