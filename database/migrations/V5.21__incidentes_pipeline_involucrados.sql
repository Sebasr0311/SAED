-- =============================================================================
-- SAED 2.0 - V5.21: INCIDENTES INVESTIGACION, ESCALAMIENTO Y RLS (GAP-F9-07)
-- =============================================================================

-- Establecer contexto administrativo para DDL seguro bajo RLS
BEGIN
    PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1);
    PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN');
EXCEPTION
    WHEN OTHERS THEN
        NULL;
END;
/

-- 1. Agregar columnas de investigacion y escalamiento a INCIDENTES si no existen
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(1) INTO v_count FROM USER_TAB_COLS WHERE TABLE_NAME = 'INCIDENTES' AND COLUMN_NAME = 'INVESTIGADO_POR';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE INCIDENTES ADD (INVESTIGADO_POR NUMBER)';
    END IF;

    SELECT COUNT(1) INTO v_count FROM USER_TAB_COLS WHERE TABLE_NAME = 'INCIDENTES' AND COLUMN_NAME = 'FECHA_INICIO_INVESTIGACION';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE INCIDENTES ADD (FECHA_INICIO_INVESTIGACION TIMESTAMP(6) WITH TIME ZONE)';
    END IF;

    SELECT COUNT(1) INTO v_count FROM USER_TAB_COLS WHERE TABLE_NAME = 'INCIDENTES' AND COLUMN_NAME = 'FECHA_FIN_INVESTIGACION';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE INCIDENTES ADD (FECHA_FIN_INVESTIGACION TIMESTAMP(6) WITH TIME ZONE)';
    END IF;

    SELECT COUNT(1) INTO v_count FROM USER_TAB_COLS WHERE TABLE_NAME = 'INCIDENTES' AND COLUMN_NAME = 'HALLAZGOS_INVESTIGACION';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE INCIDENTES ADD (HALLAZGOS_INVESTIGACION VARCHAR2(1000 CHAR))';
    END IF;

    SELECT COUNT(1) INTO v_count FROM USER_TAB_COLS WHERE TABLE_NAME = 'INCIDENTES' AND COLUMN_NAME = 'ESCALADO_POR';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE INCIDENTES ADD (ESCALADO_POR NUMBER)';
    END IF;

    SELECT COUNT(1) INTO v_count FROM USER_TAB_COLS WHERE TABLE_NAME = 'INCIDENTES' AND COLUMN_NAME = 'FECHA_ESCALAMIENTO';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE INCIDENTES ADD (FECHA_ESCALAMIENTO TIMESTAMP(6) WITH TIME ZONE)';
    END IF;

    SELECT COUNT(1) INTO v_count FROM USER_TAB_COLS WHERE TABLE_NAME = 'INCIDENTES' AND COLUMN_NAME = 'MOTIVO_ESCALAMIENTO';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE INCIDENTES ADD (MOTIVO_ESCALAMIENTO VARCHAR2(500 CHAR))';
    END IF;

    SELECT COUNT(1) INTO v_count FROM USER_TAB_COLS WHERE TABLE_NAME = 'INCIDENTES' AND COLUMN_NAME = 'SANCION_SUGERIDA';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE INCIDENTES ADD (SANCION_SUGERIDA VARCHAR2(250 CHAR))';
    END IF;
END;
/

-- 2. Foreign Keys en INCIDENTES (INVESTIGADO_POR, ESCALADO_POR -> USUARIOS)
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(1) INTO v_count FROM USER_CONSTRAINTS WHERE CONSTRAINT_NAME = 'FK_INCIDENTES_INVESTIGADO';
    IF v_count = 0 THEN
        BEGIN
            EXECUTE IMMEDIATE 'ALTER TABLE INCIDENTES ADD CONSTRAINT FK_INCIDENTES_INVESTIGADO FOREIGN KEY (INVESTIGADO_POR) REFERENCES USUARIOS(ID_USUARIO)';
        EXCEPTION WHEN OTHERS THEN NULL;
        END;
    END IF;

    SELECT COUNT(1) INTO v_count FROM USER_CONSTRAINTS WHERE CONSTRAINT_NAME = 'FK_INCIDENTES_ESCALADO';
    IF v_count = 0 THEN
        BEGIN
            EXECUTE IMMEDIATE 'ALTER TABLE INCIDENTES ADD CONSTRAINT FK_INCIDENTES_ESCALADO FOREIGN KEY (ESCALADO_POR) REFERENCES USUARIOS(ID_USUARIO)';
        EXCEPTION WHEN OTHERS THEN NULL;
        END;
    END IF;
END;
/

-- 3. Actualizar PKG_SAED_SECURITY_RLS con soporte RLS para INCIDENTE_INVOLUCRADOS
CREATE OR REPLACE PACKAGE BODY PKG_SAED_SECURITY_RLS AS

    FUNCTION FN_FILTRO_ORGANIZACION (p_schema IN VARCHAR2, p_tab IN VARCHAR2) RETURN VARCHAR2 AS
        v_org VARCHAR2(30) := SYS_CONTEXT('SAED_CTX', 'ID_ORGANIZACION');
        v_prop VARCHAR2(30) := SYS_CONTEXT('SAED_CTX', 'ID_PROPIEDAD');
        v_rol VARCHAR2(30) := SYS_CONTEXT('SAED_CTX', 'ROL_CODIGO');
        v_usr VARCHAR2(30) := SYS_CONTEXT('SAED_CTX', 'ID_USUARIO');
        v_state VARCHAR2(30) := NVL(SYS_CONTEXT('SAED_CTX', 'STATE'), 'ANONYMOUS');
    BEGIN
        IF v_state IN ('ANONYMOUS', 'CLEARING') THEN RETURN '1=0'; END IF;
        IF v_state = 'BOOTSTRAP' THEN RETURN '1=1'; END IF;
        IF v_rol = 'SUPERADMIN' THEN RETURN '1=1'; END IF;
        IF v_org IS NULL OR v_org = '0' THEN RETURN '1=0'; END IF;

        IF v_rol IN ('RESIDENTE', 'PROPIETARIO_UNIDAD') THEN
            IF p_tab = 'PERSONAS' THEN
                RETURN 'id_persona IN (SELECT id_persona FROM USUARIOS WHERE id_usuario = ' || v_usr || ') OR id_persona IN (SELECT id_persona FROM RESIDENTES_UNIDAD WHERE id_unidad IN (SELECT id_unidad FROM USUARIO_ASIGNACIONES WHERE id_usuario = ' || v_usr || ' AND estado=''ACTIVA'' AND id_unidad IS NOT NULL)) OR id_persona IN (SELECT id_persona FROM VISITANTES WHERE id_visitante IN (SELECT id_visitante FROM VISITAS WHERE id_unidad IN (SELECT id_unidad FROM USUARIO_ASIGNACIONES WHERE id_usuario = ' || v_usr || ' AND estado=''ACTIVA'' AND id_unidad IS NOT NULL))) OR id_persona IN (SELECT t.id_persona FROM TRABAJADORES t JOIN PROVEEDORES prov ON t.id_proveedor = prov.id_proveedor WHERE prov.id_organizacion = ' || v_org || ')';
            END IF;
            RETURN 'id_organizacion = ' || v_org;
        END IF;

        IF p_tab = 'PERSONAS' THEN
            RETURN 'id_persona IN (SELECT id_persona FROM USUARIOS WHERE id_usuario IN (SELECT id_usuario FROM USUARIO_ASIGNACIONES WHERE id_organizacion = ' || v_org || ')) OR id_persona IN (SELECT id_persona FROM VISITANTES) OR id_persona IN (SELECT pu.id_persona FROM PROPIETARIOS_UNIDAD pu JOIN UNIDADES u ON pu.id_unidad = u.id_unidad JOIN PROPIEDADES pr ON u.id_propiedad = pr.id_propiedad WHERE pr.id_organizacion = ' || v_org || ') OR id_persona IN (SELECT t.id_persona FROM TRABAJADORES t JOIN PROVEEDORES prov ON t.id_proveedor = prov.id_proveedor WHERE prov.id_organizacion = ' || v_org || ')';
        END IF;
        RETURN 'id_organizacion = ' || v_org;
    END FN_FILTRO_ORGANIZACION;

    FUNCTION FN_FILTRO_PROPIEDAD (p_schema IN VARCHAR2, p_tab IN VARCHAR2) RETURN VARCHAR2 AS
        v_org VARCHAR2(30) := SYS_CONTEXT('SAED_CTX', 'ID_ORGANIZACION');
        v_prop VARCHAR2(30) := SYS_CONTEXT('SAED_CTX', 'ID_PROPIEDAD');
        v_rol VARCHAR2(30) := SYS_CONTEXT('SAED_CTX', 'ROL_CODIGO');
        v_usr VARCHAR2(30) := SYS_CONTEXT('SAED_CTX', 'ID_USUARIO');
        v_state VARCHAR2(30) := NVL(SYS_CONTEXT('SAED_CTX', 'STATE'), 'ANONYMOUS');
    BEGIN
        IF v_state IN ('ANONYMOUS', 'CLEARING') THEN RETURN '1=0'; END IF;
        IF v_state = 'BOOTSTRAP' THEN RETURN '1=1'; END IF;
        IF v_rol = 'SUPERADMIN' THEN RETURN '1=1'; END IF;
        IF v_org IS NULL OR v_org = '0' THEN RETURN '1=0'; END IF;

        -- Regla especial: TRABAJADORES no tiene id_propiedad; se filtra por la organizacion de sus proveedores
        IF p_tab = 'TRABAJADORES' THEN
            RETURN 'id_proveedor IN (SELECT id_proveedor FROM PROVEEDORES WHERE id_organizacion = ' || v_org || ')';
        END IF;

        IF v_rol IN ('RESIDENTE', 'PROPIETARIO_UNIDAD') THEN
            IF p_tab = 'INCIDENTE_INVOLUCRADOS' THEN
                RETURN 'id_incidente IN (SELECT id_incidente FROM INCIDENTES WHERE id_propiedad IN (SELECT id_propiedad FROM USUARIO_ASIGNACIONES WHERE id_usuario = ' || v_usr || ' AND estado=''ACTIVA''))';
            END IF;
            IF p_tab = 'VISITANTES' THEN
                RETURN 'id_visitante IN (SELECT id_visitante FROM VISITAS WHERE id_unidad IN (SELECT id_unidad FROM USUARIO_ASIGNACIONES WHERE id_usuario = ' || v_usr || ' AND estado=''ACTIVA'' AND id_unidad IS NOT NULL))';
            END IF;
            IF p_tab = 'PQRS_TRAZABILIDAD' THEN
                RETURN 'id_ticket IN (SELECT id_ticket FROM PQRS_TICKETS WHERE id_unidad IN (SELECT id_unidad FROM USUARIO_ASIGNACIONES WHERE id_usuario = ' || v_usr || ' AND estado=''ACTIVA'' AND id_unidad IS NOT NULL))';
            END IF;
            IF p_tab = 'NOTIFICACIONES' THEN
                RETURN 'id_comunicado IN (SELECT id_comunicado FROM COMUNICADOS WHERE id_propiedad IN (SELECT id_propiedad FROM USUARIO_ASIGNACIONES WHERE id_usuario = ' || v_usr || ' AND estado=''ACTIVA''))';
            END IF;
            IF p_tab = 'ENCUESTA_OPCIONES' THEN
                RETURN 'id_encuesta IN (SELECT id_encuesta FROM ENCUESTAS WHERE id_propiedad IN (SELECT id_propiedad FROM USUARIO_ASIGNACIONES WHERE id_usuario = ' || v_usr || ' AND estado=''ACTIVA''))';
            END IF;
            IF p_tab = 'VERSIONES_DOCUMENTO' THEN
                RETURN 'id_documento IN (SELECT id_documento FROM DOCUMENTOS WHERE (id_propiedad IN (SELECT id_propiedad FROM USUARIO_ASIGNACIONES WHERE id_usuario = ' || v_usr || ' AND estado=''ACTIVA'') OR id_propiedad IS NULL))';
            END IF;
            RETURN 'id_propiedad IN (SELECT id_propiedad FROM USUARIO_ASIGNACIONES WHERE id_usuario = ' || v_usr || ' AND estado=''ACTIVA'')';
        END IF;

        IF v_prop IS NOT NULL THEN
            IF p_tab = 'PROPIEDADES' THEN RETURN 'id_propiedad = ' || v_prop; END IF;
            IF p_tab = 'INCIDENTE_INVOLUCRADOS' THEN RETURN 'id_incidente IN (SELECT id_incidente FROM INCIDENTES WHERE id_propiedad = ' || v_prop || ')'; END IF;
            IF p_tab = 'VERSIONES_DOCUMENTO' THEN RETURN 'id_documento IN (SELECT id_documento FROM DOCUMENTOS WHERE id_propiedad = ' || v_prop || ' OR id_propiedad IS NULL)'; END IF;
            IF p_tab = 'PQRS_TRAZABILIDAD' THEN RETURN 'id_ticket IN (SELECT id_ticket FROM PQRS_TICKETS WHERE id_propiedad = ' || v_prop || ')'; END IF;
            IF p_tab = 'NOTIFICACIONES' THEN RETURN 'id_comunicado IN (SELECT id_comunicado FROM COMUNICADOS WHERE id_propiedad = ' || v_prop || ')'; END IF;
            IF p_tab = 'ENCUESTA_OPCIONES' THEN RETURN 'id_encuesta IN (SELECT id_encuesta FROM ENCUESTAS WHERE id_propiedad = ' || v_prop || ')'; END IF;
            IF p_tab = 'VISITANTES' THEN RETURN 'id_visitante IN (SELECT id_visitante FROM VISITAS JOIN UNIDADES ON VISITAS.id_unidad = UNIDADES.id_unidad WHERE UNIDADES.id_propiedad = ' || v_prop || ')'; END IF;
            RETURN 'id_propiedad = ' || v_prop;
        ELSE
            IF p_tab = 'PROPIEDADES' THEN RETURN 'id_organizacion = ' || v_org; END IF;
            IF p_tab = 'INCIDENTE_INVOLUCRADOS' THEN RETURN 'id_incidente IN (SELECT id_incidente FROM INCIDENTES WHERE id_propiedad IN (SELECT id_propiedad FROM PROPIEDADES WHERE id_organizacion = ' || v_org || '))'; END IF;
            IF p_tab = 'VERSIONES_DOCUMENTO' THEN RETURN 'id_documento IN (SELECT id_documento FROM DOCUMENTOS WHERE id_organizacion = ' || v_org || ')'; END IF;
            IF p_tab = 'PQRS_TRAZABILIDAD' THEN RETURN 'id_ticket IN (SELECT id_ticket FROM PQRS_TICKETS WHERE id_propiedad IN (SELECT id_propiedad FROM PROPIEDADES WHERE id_organizacion = ' || v_org || '))'; END IF;
            IF p_tab = 'NOTIFICACIONES' THEN RETURN 'id_comunicado IN (SELECT id_comunicado FROM COMUNICADOS WHERE id_propiedad IN (SELECT id_propiedad FROM PROPIEDADES WHERE id_organizacion = ' || v_org || '))'; END IF;
            IF p_tab = 'ENCUESTA_OPCIONES' THEN RETURN 'id_encuesta IN (SELECT id_encuesta FROM ENCUESTAS WHERE id_propiedad IN (SELECT id_propiedad FROM PROPIEDADES WHERE id_organizacion = ' || v_org || '))'; END IF;
            IF p_tab = 'VISITANTES' THEN RETURN 'id_visitante IN (SELECT id_visitante FROM VISITAS JOIN UNIDADES ON VISITAS.id_unidad = UNIDADES.id_unidad JOIN PROPIEDADES ON UNIDADES.id_propiedad = PROPIEDADES.id_propiedad WHERE PROPIEDADES.id_organizacion = ' || v_org || ')'; END IF;
            RETURN 'id_propiedad IN (SELECT id_propiedad FROM PROPIEDADES WHERE id_organizacion = ' || v_org || ')';
        END IF;
    END FN_FILTRO_PROPIEDAD;

    FUNCTION FN_FILTRO_UNIDAD (p_schema IN VARCHAR2, p_tab IN VARCHAR2) RETURN VARCHAR2 AS
        v_org VARCHAR2(30) := SYS_CONTEXT('SAED_CTX', 'ID_ORGANIZACION');
        v_prop VARCHAR2(30) := SYS_CONTEXT('SAED_CTX', 'ID_PROPIEDAD');
        v_rol VARCHAR2(30) := SYS_CONTEXT('SAED_CTX', 'ROL_CODIGO');
        v_usr VARCHAR2(30) := SYS_CONTEXT('SAED_CTX', 'ID_USUARIO');
        v_state VARCHAR2(30) := NVL(SYS_CONTEXT('SAED_CTX', 'STATE'), 'ANONYMOUS');
    BEGIN
        IF v_state IN ('ANONYMOUS', 'CLEARING') THEN RETURN '1=0'; END IF;
        IF v_state = 'BOOTSTRAP' THEN RETURN '1=1'; END IF;
        IF v_rol = 'SUPERADMIN' THEN RETURN '1=1'; END IF;
        IF v_org IS NULL OR v_org = '0' THEN RETURN '1=0'; END IF;

        IF v_rol IN ('RESIDENTE', 'PROPIETARIO_UNIDAD') THEN
            IF p_tab = 'UNIDADES' THEN
                RETURN 'id_unidad IN (SELECT id_unidad FROM USUARIO_ASIGNACIONES WHERE id_usuario = ' || v_usr || ' AND estado = ''ACTIVA'' AND id_unidad IS NOT NULL)';
            ELSIF p_tab = 'TUTORES' THEN
                RETURN 'id_persona_menor IN (SELECT id_persona FROM RESIDENTES_UNIDAD WHERE id_unidad IN (SELECT id_unidad FROM USUARIO_ASIGNACIONES WHERE id_usuario = ' || v_usr || ' AND estado = ''ACTIVA'' AND id_unidad IS NOT NULL))';
            ELSIF p_tab = 'PAGO_DETALLE' THEN
                RETURN 'id_cuota IN (SELECT id_cuota FROM CUOTAS WHERE id_unidad IN (SELECT id_unidad FROM USUARIO_ASIGNACIONES WHERE id_usuario = ' || v_usr || ' AND estado = ''ACTIVA'' AND id_unidad IS NOT NULL))';
            ELSIF p_tab = 'OBRA_TRABAJADORES' THEN
                RETURN 'id_obra IN (SELECT id_obra FROM OBRAS WHERE id_unidad IN (SELECT id_unidad FROM USUARIO_ASIGNACIONES WHERE id_usuario = ' || v_usr || ' AND estado = ''ACTIVA'' AND id_unidad IS NOT NULL))';
            ELSIF p_tab = 'CONTRATO_RESIDENTE' THEN
                RETURN 'id_contrato IN (SELECT id_contrato FROM CONTRATOS WHERE id_unidad IN (SELECT id_unidad FROM USUARIO_ASIGNACIONES WHERE id_usuario = ' || v_usr || ' AND estado = ''ACTIVA'' AND id_unidad IS NOT NULL))';
            ELSIF p_tab IN ('QR_ACCESOS', 'VEHICULOS_VISITA') THEN
                RETURN 'id_visita IN (SELECT id_visita FROM VISITAS WHERE id_unidad IN (SELECT id_unidad FROM USUARIO_ASIGNACIONES WHERE id_usuario = ' || v_usr || ' AND estado = ''ACTIVA'' AND id_unidad IS NOT NULL))';
            ELSIF p_tab = 'TRANSACCIONES_PAGO' THEN
                RETURN 'id_unidad IS NOT NULL AND id_unidad IN (SELECT id_unidad FROM USUARIO_ASIGNACIONES WHERE id_usuario = ' || v_usr || ' AND estado = ''ACTIVA'' AND id_unidad IS NOT NULL)';
            ELSIF p_tab = 'DOMICILIOS' THEN
                RETURN 'id_unidad IN (SELECT id_unidad FROM USUARIO_ASIGNACIONES WHERE id_usuario = ' || v_usr || ' AND estado = ''ACTIVA'' AND id_unidad IS NOT NULL)';
            ELSE
                RETURN 'id_unidad IN (SELECT id_unidad FROM USUARIO_ASIGNACIONES WHERE id_usuario = ' || v_usr || ' AND estado = ''ACTIVA'' AND id_unidad IS NOT NULL)';
            END IF;
        END IF;

        IF v_prop IS NOT NULL THEN
            IF p_tab = 'UNIDADES' THEN RETURN 'id_propiedad = ' || v_prop; END IF;
            IF p_tab = 'TUTORES' THEN RETURN 'id_persona_menor IN (SELECT id_persona FROM RESIDENTES_UNIDAD JOIN UNIDADES ON RESIDENTES_UNIDAD.id_unidad = UNIDADES.id_unidad WHERE UNIDADES.id_propiedad = ' || v_prop || ')'; END IF;
            IF p_tab = 'PAGO_DETALLE' THEN RETURN 'id_cuota IN (SELECT id_cuota FROM CUOTAS WHERE id_unidad IN (SELECT id_unidad FROM UNIDADES WHERE id_propiedad = ' || v_prop || '))'; END IF;
            IF p_tab = 'OBRA_TRABAJADORES' THEN RETURN 'id_obra IN (SELECT id_obra FROM OBRAS WHERE id_unidad IN (SELECT id_unidad FROM UNIDADES WHERE id_propiedad = ' || v_prop || '))'; END IF;
            IF p_tab = 'CONTRATO_RESIDENTE' THEN RETURN 'id_contrato IN (SELECT id_contrato FROM CONTRATOS WHERE id_unidad IN (SELECT id_unidad FROM UNIDADES WHERE id_propiedad = ' || v_prop || '))'; END IF;
            IF p_tab IN ('QR_ACCESOS', 'VEHICULOS_VISITA') THEN RETURN 'id_visita IN (SELECT id_visita FROM VISITAS JOIN UNIDADES ON VISITAS.id_unidad = UNIDADES.id_unidad WHERE UNIDADES.id_propiedad = ' || v_prop || ')'; END IF;
            IF p_tab = 'TRANSACCIONES_PAGO' THEN
                RETURN 'id_unidad IS NOT NULL AND id_unidad IN (SELECT id_unidad FROM UNIDADES WHERE id_propiedad = ' || v_prop || ')';
            END IF;
            IF p_tab = 'DOMICILIOS' THEN RETURN 'id_propiedad = ' || v_prop; END IF;
            RETURN 'id_unidad IN (SELECT id_unidad FROM UNIDADES WHERE id_propiedad = ' || v_prop || ')';
        ELSE
            IF p_tab = 'UNIDADES' THEN RETURN 'id_propiedad IN (SELECT id_propiedad FROM PROPIEDADES WHERE id_organizacion = ' || v_org || ')'; END IF;
            IF p_tab = 'TUTORES' THEN RETURN 'id_persona_menor IN (SELECT id_persona FROM RESIDENTES_UNIDAD JOIN UNIDADES ON RESIDENTES_UNIDAD.id_unidad = UNIDADES.id_unidad JOIN PROPIEDADES ON UNIDADES.id_propiedad = PROPIEDADES.id_propiedad WHERE PROPIEDADES.id_organizacion = ' || v_org || ')'; END IF;
            IF p_tab = 'PAGO_DETALLE' THEN RETURN 'id_cuota IN (SELECT id_cuota FROM CUOTAS WHERE id_unidad IN (SELECT id_unidad FROM UNIDADES WHERE id_propiedad IN (SELECT id_propiedad FROM PROPIEDADES WHERE id_organizacion = ' || v_org || ')))'; END IF;
            IF p_tab = 'OBRA_TRABAJADORES' THEN RETURN 'id_obra IN (SELECT id_obra FROM OBRAS WHERE id_unidad IN (SELECT id_unidad FROM UNIDADES WHERE id_propiedad IN (SELECT id_propiedad FROM PROPIEDADES WHERE id_organizacion = ' || v_org || ')))'; END IF;
            IF p_tab = 'CONTRATO_RESIDENTE' THEN RETURN 'id_contrato IN (SELECT id_contrato FROM CONTRATOS WHERE id_unidad IN (SELECT id_unidad FROM UNIDADES WHERE id_propiedad IN (SELECT id_propiedad FROM PROPIEDADES WHERE id_organizacion = ' || v_org || ')))'; END IF;
            IF p_tab IN ('QR_ACCESOS', 'VEHICULOS_VISITA') THEN RETURN 'id_visita IN (SELECT id_visita FROM VISITAS JOIN UNIDADES ON VISITAS.id_unidad = UNIDADES.id_unidad JOIN PROPIEDADES ON UNIDADES.id_propiedad = PROPIEDADES.id_propiedad WHERE PROPIEDADES.id_organizacion = ' || v_org || ')'; END IF;
            IF p_tab = 'TRANSACCIONES_PAGO' THEN
                RETURN '((id_unidad IS NOT NULL AND id_unidad IN (SELECT id_unidad FROM UNIDADES WHERE id_propiedad IN (SELECT id_propiedad FROM PROPIEDADES WHERE id_organizacion = ' || v_org || '))) OR (id_unidad IS NULL AND id_organizacion = ' || v_org || '))';
            END IF;
            IF p_tab = 'DOMICILIOS' THEN RETURN 'id_propiedad IN (SELECT id_propiedad FROM PROPIEDADES WHERE id_organizacion = ' || v_org || ')'; END IF;
            RETURN 'id_unidad IN (SELECT id_unidad FROM UNIDADES WHERE id_propiedad IN (SELECT id_propiedad FROM PROPIEDADES WHERE id_organizacion = ' || v_org || '))';
        END IF;
    END FN_FILTRO_UNIDAD;

    FUNCTION FN_FILTRO_ASIGNACION (p_schema IN VARCHAR2, p_tab IN VARCHAR2) RETURN VARCHAR2 AS
        v_org VARCHAR2(30) := SYS_CONTEXT('SAED_CTX', 'ID_ORGANIZACION');
        v_rol VARCHAR2(30) := SYS_CONTEXT('SAED_CTX', 'ROL_CODIGO');
        v_usr VARCHAR2(30) := SYS_CONTEXT('SAED_CTX', 'ID_USUARIO');
        v_state VARCHAR2(30) := NVL(SYS_CONTEXT('SAED_CTX', 'STATE'), 'ANONYMOUS');
    BEGIN
        IF v_state IN ('ANONYMOUS', 'CLEARING') THEN RETURN '1=0'; END IF;
        IF v_state = 'BOOTSTRAP' THEN RETURN 'id_usuario = ' || v_usr; END IF;

        IF v_rol = 'SUPERADMIN' THEN RETURN '1=1'; END IF;
        IF v_org IS NULL OR v_org = '0' THEN RETURN '1=0'; END IF;
        IF v_rol = 'RESIDENTE' THEN RETURN 'id_usuario = ' || v_usr; END IF;
        RETURN 'id_organizacion = ' || v_org;
    END FN_FILTRO_ASIGNACION;

    FUNCTION FN_FILTRO_USUARIOS (p_schema IN VARCHAR2, p_tab IN VARCHAR2) RETURN VARCHAR2 AS
        v_org VARCHAR2(30) := SYS_CONTEXT('SAED_CTX', 'ID_ORGANIZACION');
        v_rol VARCHAR2(30) := SYS_CONTEXT('SAED_CTX', 'ROL_CODIGO');
        v_usr VARCHAR2(30) := SYS_CONTEXT('SAED_CTX', 'ID_USUARIO');
        v_state VARCHAR2(30) := NVL(SYS_CONTEXT('SAED_CTX', 'STATE'), 'ANONYMOUS');
    BEGIN
        IF v_state IN ('ANONYMOUS', 'CLEARING') THEN RETURN '1=0'; END IF;
        IF v_state = 'BOOTSTRAP' THEN RETURN 'id_usuario = ' || v_usr; END IF;

        IF v_rol = 'SUPERADMIN' THEN RETURN '1=1'; END IF;
        IF v_org IS NULL OR v_org = '0' THEN RETURN '1=0'; END IF;
        IF v_rol = 'RESIDENTE' THEN RETURN 'id_usuario = ' || v_usr; END IF;
        RETURN 'id_usuario IN (SELECT id_usuario FROM USUARIO_ASIGNACIONES WHERE id_organizacion = ' || v_org || ')';
    END FN_FILTRO_USUARIOS;

    FUNCTION FN_FILTRO_GLOBAL_READONLY (p_schema IN VARCHAR2, p_tab IN VARCHAR2) RETURN VARCHAR2 AS
    BEGIN
        RETURN '1=1';
    END FN_FILTRO_GLOBAL_READONLY;

    FUNCTION FN_FILTRO_GLOBAL_MUTATE (p_schema IN VARCHAR2, p_tab IN VARCHAR2) RETURN VARCHAR2 AS
        v_rol VARCHAR2(30) := SYS_CONTEXT('SAED_CTX', 'ROL_CODIGO');
        v_usr VARCHAR2(30) := SYS_CONTEXT('SAED_CTX', 'ID_USUARIO');
        v_state VARCHAR2(30) := NVL(SYS_CONTEXT('SAED_CTX', 'STATE'), 'ANONYMOUS');
    BEGIN
        IF v_state IN ('ANONYMOUS', 'CLEARING') THEN RETURN '1=0'; END IF;
        IF v_state = 'BOOTSTRAP' THEN RETURN 'id_usuario = ' || v_usr; END IF;
        IF v_rol = 'SUPERADMIN' THEN RETURN '1=1'; END IF;
        RETURN '1=0';
    END FN_FILTRO_GLOBAL_MUTATE;

END PKG_SAED_SECURITY_RLS;
/
