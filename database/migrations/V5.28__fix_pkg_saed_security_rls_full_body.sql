-- ============================================================================
-- V5.28__fix_pkg_saed_security_rls_full_body.sql
-- Resolución definitiva para ORA-28110 / PLS-00323:
-- Restaura la especificación y el cuerpo completo del paquete PKG_SAED_SECURITY_RLS
-- con las 7 funciones requeridas por las políticas VPD/RLS de SAED 2.0.
-- Incluye bloques EXCEPTION WHEN OTHERS defensivos en cada función de predicado.
-- ============================================================================

CREATE OR REPLACE PACKAGE PKG_SAED_SECURITY_RLS AS
    FUNCTION FN_FILTRO_ORGANIZACION (p_schema IN VARCHAR2, p_tab IN VARCHAR2) RETURN VARCHAR2;
    FUNCTION FN_FILTRO_PROPIEDAD    (p_schema IN VARCHAR2, p_tab IN VARCHAR2) RETURN VARCHAR2;
    FUNCTION FN_FILTRO_UNIDAD       (p_schema IN VARCHAR2, p_tab IN VARCHAR2) RETURN VARCHAR2;
    FUNCTION FN_FILTRO_USUARIOS     (p_schema IN VARCHAR2, p_tab IN VARCHAR2) RETURN VARCHAR2;
    FUNCTION FN_FILTRO_ASIGNACION   (p_schema IN VARCHAR2, p_tab IN VARCHAR2) RETURN VARCHAR2;
    FUNCTION FN_FILTRO_GLOBAL_READONLY (p_schema IN VARCHAR2, p_tab IN VARCHAR2) RETURN VARCHAR2;
    FUNCTION FN_FILTRO_GLOBAL_MUTATE   (p_schema IN VARCHAR2, p_tab IN VARCHAR2) RETURN VARCHAR2;
END PKG_SAED_SECURITY_RLS;
/

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

        IF v_rol IN ('RESIDENTE', 'PROPIETARIO_UNIDAD', 'RESIDENTE_CONVIVENCIA') THEN
            IF p_tab = 'PERSONAS' THEN
                RETURN 'id_persona IN (SELECT id_persona FROM USUARIOS WHERE id_usuario = ' || v_usr || ') OR id_persona IN (SELECT id_persona FROM RESIDENTES_UNIDAD WHERE id_unidad IN (SELECT id_unidad FROM USUARIO_ASIGNACIONES WHERE id_usuario = ' || v_usr || ' AND estado=''ACTIVA'' AND id_unidad IS NOT NULL)) OR id_persona IN (SELECT id_persona FROM VISITANTES WHERE id_visitante IN (SELECT id_visitante FROM VISITAS WHERE id_unidad IN (SELECT id_unidad FROM USUARIO_ASIGNACIONES WHERE id_usuario = ' || v_usr || ' AND estado=''ACTIVA'' AND id_unidad IS NOT NULL))) OR id_persona IN (SELECT t.id_persona FROM TRABAJADORES t JOIN PROVEEDORES prov ON t.id_proveedor = prov.id_proveedor WHERE prov.id_organizacion = ' || v_org || ')';
            END IF;
            RETURN 'id_organizacion = ' || v_org;
        END IF;

        IF p_tab = 'PERSONAS' THEN
            RETURN 'id_persona IN (SELECT id_persona FROM USUARIOS WHERE id_usuario IN (SELECT id_usuario FROM USUARIO_ASIGNACIONES WHERE id_organizacion = ' || v_org || ')) OR id_persona IN (SELECT id_persona FROM VISITANTES) OR id_persona IN (SELECT pu.id_persona FROM PROPIETARIOS_UNIDAD pu JOIN UNIDADES u ON pu.id_unidad = u.id_unidad JOIN PROPIEDADES pr ON u.id_propiedad = pr.id_propiedad WHERE pr.id_organizacion = ' || v_org || ') OR id_persona IN (SELECT t.id_persona FROM TRABAJADORES t JOIN PROVEEDORES prov ON t.id_proveedor = prov.id_proveedor WHERE prov.id_organizacion = ' || v_org || ')';
        END IF;

        RETURN 'id_organizacion = ' || v_org;
    EXCEPTION
        WHEN OTHERS THEN
            RETURN '1=0';
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

        -- Tablas hijas de ASAMBLEAS sin columna directa id_propiedad
        IF p_tab IN ('ASISTENCIAS_ASAMBLEA', 'PODERES_REPRESENTACION', 'VOTACIONES', 'ACTAS_ASAMBLEA') THEN
            IF v_rol IN ('RESIDENTE', 'PROPIETARIO_UNIDAD', 'RESIDENTE_CONVIVENCIA') THEN
                RETURN 'id_asamblea IN (SELECT id_asamblea FROM ASAMBLEAS WHERE id_propiedad IN (SELECT id_propiedad FROM USUARIO_ASIGNACIONES WHERE id_usuario = ' || v_usr || ' AND estado=''ACTIVA''))';
            ELSIF v_prop IS NOT NULL THEN
                RETURN 'id_asamblea IN (SELECT id_asamblea FROM ASAMBLEAS WHERE id_propiedad = ' || v_prop || ')';
            ELSE
                RETURN 'id_asamblea IN (SELECT id_asamblea FROM ASAMBLEAS WHERE id_propiedad IN (SELECT id_propiedad FROM PROPIEDADES WHERE id_organizacion = ' || v_org || '))';
            END IF;
        END IF;

        IF v_rol IN ('RESIDENTE', 'PORTERO', 'PROPIETARIO_UNIDAD', 'RESIDENTE_CONVIVENCIA') THEN
            IF p_tab IN ('REPORTES_CONFIGURADOS', 'HISTORIAL_REPORTES') THEN
                RETURN '1=0';
            END IF;
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
            IF p_tab = 'REGLAMENTOS_NORMATIVA' THEN
                RETURN 'id_propiedad IN (SELECT id_propiedad FROM USUARIO_ASIGNACIONES WHERE id_usuario = ' || v_usr || ' AND estado=''ACTIVA'') AND estado = ''PUBLICADO''';
            END IF;
            RETURN 'id_propiedad IN (SELECT id_propiedad FROM USUARIO_ASIGNACIONES WHERE id_usuario = ' || v_usr || ' AND estado=''ACTIVA'')';
        END IF;

        IF v_prop IS NOT NULL THEN
            IF p_tab = 'PROPIEDADES' THEN RETURN 'id_propiedad = ' || v_prop; END IF;
            IF p_tab = 'HISTORIAL_REPORTES' THEN RETURN 'id_organizacion = ' || v_org || ' AND id_propiedad = ' || v_prop; END IF;
            IF p_tab = 'REPORTES_CONFIGURADOS' THEN RETURN '(id_organizacion = ' || v_org || ' AND (id_propiedad = ' || v_prop || ' OR id_propiedad IS NULL)) OR id_organizacion IS NULL'; END IF;
            IF p_tab = 'INCIDENTE_INVOLUCRADOS' THEN RETURN 'id_incidente IN (SELECT id_incidente FROM INCIDENTES WHERE id_propiedad = ' || v_prop || ')'; END IF;
            IF p_tab = 'VERSIONES_DOCUMENTO' THEN RETURN 'id_documento IN (SELECT id_documento FROM DOCUMENTOS WHERE id_propiedad = ' || v_prop || ' OR id_propiedad IS NULL)'; END IF;
            IF p_tab = 'PQRS_TRAZABILIDAD' THEN RETURN 'id_ticket IN (SELECT id_ticket FROM PQRS_TICKETS WHERE id_propiedad = ' || v_prop || ')'; END IF;
            IF p_tab = 'NOTIFICACIONES' THEN RETURN 'id_comunicado IN (SELECT id_comunicado FROM COMUNICADOS WHERE id_propiedad = ' || v_prop || ')'; END IF;
            IF p_tab = 'ENCUESTA_OPCIONES' THEN RETURN 'id_encuesta IN (SELECT id_encuesta FROM ENCUESTAS WHERE id_propiedad = ' || v_prop || ')'; END IF;
            IF p_tab = 'VISITANTES' THEN RETURN 'id_visitante IN (SELECT id_visitante FROM VISITAS JOIN UNIDADES ON VISITAS.id_unidad = UNIDADES.id_unidad WHERE UNIDADES.id_propiedad = ' || v_prop || ')'; END IF;
            RETURN 'id_propiedad = ' || v_prop;
        ELSE
            IF p_tab = 'PROPIEDADES' THEN RETURN 'id_organizacion = ' || v_org; END IF;
            IF p_tab = 'HISTORIAL_REPORTES' THEN RETURN 'id_organizacion = ' || v_org; END IF;
            IF p_tab = 'REPORTES_CONFIGURADOS' THEN RETURN 'id_organizacion = ' || v_org || ' OR id_organizacion IS NULL'; END IF;
            IF p_tab = 'INCIDENTE_INVOLUCRADOS' THEN RETURN 'id_incidente IN (SELECT id_incidente FROM INCIDENTES WHERE id_propiedad IN (SELECT id_propiedad FROM PROPIEDADES WHERE id_organizacion = ' || v_org || '))'; END IF;
            IF p_tab = 'VERSIONES_DOCUMENTO' THEN RETURN 'id_documento IN (SELECT id_documento FROM DOCUMENTOS WHERE id_organizacion = ' || v_org || ')'; END IF;
            IF p_tab = 'PQRS_TRAZABILIDAD' THEN RETURN 'id_ticket IN (SELECT id_ticket FROM PQRS_TICKETS WHERE id_propiedad IN (SELECT id_propiedad FROM PROPIEDADES WHERE id_organizacion = ' || v_org || '))'; END IF;
            IF p_tab = 'NOTIFICACIONES' THEN RETURN 'id_comunicado IN (SELECT id_comunicado FROM COMUNICADOS WHERE id_propiedad IN (SELECT id_propiedad FROM PROPIEDADES WHERE id_organizacion = ' || v_org || '))'; END IF;
            IF p_tab = 'ENCUESTA_OPCIONES' THEN RETURN 'id_encuesta IN (SELECT id_encuesta FROM ENCUESTAS WHERE id_propiedad IN (SELECT id_propiedad FROM PROPIEDADES WHERE id_organizacion = ' || v_org || '))'; END IF;
            IF p_tab = 'VISITANTES' THEN RETURN 'id_visitante IN (SELECT id_visitante FROM VISITAS JOIN UNIDADES ON VISITAS.id_unidad = UNIDADES.id_unidad JOIN PROPIEDADES ON UNIDADES.id_propiedad = PROPIEDADES.id_propiedad WHERE PROPIEDADES.id_organizacion = ' || v_org || ')'; END IF;
            RETURN 'id_propiedad IN (SELECT id_propiedad FROM PROPIEDADES WHERE id_organizacion = ' || v_org || ')';
        END IF;
    EXCEPTION
        WHEN OTHERS THEN
            RETURN '1=0';
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

        IF v_rol IN ('RESIDENTE', 'PROPIETARIO_UNIDAD', 'RESIDENTE_CONVIVENCIA') THEN
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
                RETURN '(id_unidad IN (SELECT id_unidad FROM USUARIO_ASIGNACIONES WHERE id_usuario = ' || v_usr || ' AND estado=''ACTIVA'' AND id_unidad IS NOT NULL) OR (id_unidad IS NULL AND id_propiedad IN (SELECT id_propiedad FROM USUARIO_ASIGNACIONES WHERE id_usuario = ' || v_usr || ' AND estado=''ACTIVA'')))';
            ELSIF p_tab = 'DOMICILIOS' THEN
                RETURN 'id_unidad IN (SELECT id_unidad FROM USUARIO_ASIGNACIONES WHERE id_usuario = ' || v_usr || ' AND estado = ''ACTIVA'' AND id_unidad IS NOT NULL)';
            ELSIF p_tab = 'VEHICULOS' THEN
                RETURN 'id_unidad IN (SELECT id_unidad FROM USUARIO_ASIGNACIONES WHERE id_usuario = ' || v_usr || ' AND estado=''ACTIVA'' AND id_unidad IS NOT NULL)';
            ELSIF p_tab = 'MASCOTAS' THEN
                RETURN 'id_unidad IN (SELECT id_unidad FROM USUARIO_ASIGNACIONES WHERE id_usuario = ' || v_usr || ' AND estado=''ACTIVA'' AND id_unidad IS NOT NULL)';
            ELSIF p_tab = 'OBRAS' THEN
                RETURN 'id_unidad IN (SELECT id_unidad FROM USUARIO_ASIGNACIONES WHERE id_usuario = ' || v_usr || ' AND estado=''ACTIVA'' AND id_unidad IS NOT NULL)';
            ELSIF p_tab = 'PAQUETES' THEN
                RETURN 'id_unidad IN (SELECT id_unidad FROM USUARIO_ASIGNACIONES WHERE id_usuario = ' || v_usr || ' AND estado=''ACTIVA'' AND id_unidad IS NOT NULL)';
            ELSIF p_tab = 'VISITAS' THEN
                RETURN 'id_unidad IN (SELECT id_unidad FROM USUARIO_ASIGNACIONES WHERE id_usuario = ' || v_usr || ' AND estado=''ACTIVA'' AND id_unidad IS NOT NULL)';
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
            IF p_tab = 'TRANSACCIONES_PAGO' THEN RETURN 'id_propiedad = ' || v_prop; END IF;
            IF p_tab = 'DOMICILIOS' THEN RETURN 'id_propiedad = ' || v_prop; END IF;
            IF p_tab = 'PAQUETES' THEN RETURN 'id_propiedad = ' || v_prop; END IF;
            IF p_tab = 'VISITAS' THEN RETURN 'id_unidad IN (SELECT id_unidad FROM UNIDADES WHERE id_propiedad = ' || v_prop || ')'; END IF;
            IF p_tab = 'VEHICULOS' THEN RETURN 'id_unidad IN (SELECT id_unidad FROM UNIDADES WHERE id_propiedad = ' || v_prop || ')'; END IF;
            IF p_tab = 'MASCOTAS' THEN RETURN 'id_unidad IN (SELECT id_unidad FROM UNIDADES WHERE id_propiedad = ' || v_prop || ')'; END IF;
            IF p_tab = 'OBRAS' THEN RETURN 'id_unidad IN (SELECT id_unidad FROM UNIDADES WHERE id_propiedad = ' || v_prop || ')'; END IF;
            RETURN 'id_unidad IN (SELECT id_unidad FROM UNIDADES WHERE id_propiedad = ' || v_prop || ')';
        ELSE
            IF p_tab = 'UNIDADES' THEN RETURN 'id_propiedad IN (SELECT id_propiedad FROM PROPIEDADES WHERE id_organizacion = ' || v_org || ')'; END IF;
            IF p_tab = 'TUTORES' THEN RETURN 'id_persona_menor IN (SELECT id_persona FROM RESIDENTES_UNIDAD JOIN UNIDADES ON RESIDENTES_UNIDAD.id_unidad = UNIDADES.id_unidad JOIN PROPIEDADES ON UNIDADES.id_propiedad = PROPIEDADES.id_propiedad WHERE PROPIEDADES.id_organizacion = ' || v_org || ')'; END IF;
            IF p_tab = 'PAGO_DETALLE' THEN RETURN 'id_cuota IN (SELECT id_cuota FROM CUOTAS WHERE id_unidad IN (SELECT id_unidad FROM UNIDADES WHERE id_propiedad IN (SELECT id_propiedad FROM PROPIEDADES WHERE id_organizacion = ' || v_org || ')))'; END IF;
            IF p_tab = 'OBRA_TRABAJADORES' THEN RETURN 'id_obra IN (SELECT id_obra FROM OBRAS WHERE id_unidad IN (SELECT id_unidad FROM UNIDADES WHERE id_propiedad IN (SELECT id_propiedad FROM PROPIEDADES WHERE id_organizacion = ' || v_org || ')))'; END IF;
            IF p_tab = 'CONTRATO_RESIDENTE' THEN RETURN 'id_contrato IN (SELECT id_contrato FROM CONTRATOS WHERE id_unidad IN (SELECT id_unidad FROM UNIDADES WHERE id_propiedad IN (SELECT id_propiedad FROM PROPIEDADES WHERE id_organizacion = ' || v_org || ')))'; END IF;
            IF p_tab IN ('QR_ACCESOS', 'VEHICULOS_VISITA') THEN RETURN 'id_visita IN (SELECT id_visita FROM VISITAS JOIN UNIDADES ON VISITAS.id_unidad = UNIDADES.id_unidad JOIN PROPIEDADES ON UNIDADES.id_propiedad = PROPIEDADES.id_propiedad WHERE PROPIEDADES.id_organizacion = ' || v_org || ')'; END IF;
            IF p_tab = 'TRANSACCIONES_PAGO' THEN RETURN 'id_propiedad IN (SELECT id_propiedad FROM PROPIEDADES WHERE id_organizacion = ' || v_org || ')'; END IF;
            IF p_tab = 'DOMICILIOS' THEN RETURN 'id_propiedad IN (SELECT id_propiedad FROM PROPIEDADES WHERE id_organizacion = ' || v_org || ')'; END IF;
            IF p_tab = 'PAQUETES' THEN RETURN 'id_propiedad IN (SELECT id_propiedad FROM PROPIEDADES WHERE id_organizacion = ' || v_org || ')'; END IF;
            IF p_tab = 'VISITAS' THEN RETURN 'id_unidad IN (SELECT id_unidad FROM UNIDADES WHERE id_propiedad IN (SELECT id_propiedad FROM PROPIEDADES WHERE id_organizacion = ' || v_org || '))'; END IF;
            IF p_tab = 'VEHICULOS' THEN RETURN 'id_unidad IN (SELECT id_unidad FROM UNIDADES WHERE id_propiedad IN (SELECT id_propiedad FROM PROPIEDADES WHERE id_organizacion = ' || v_org || '))'; END IF;
            IF p_tab = 'MASCOTAS' THEN RETURN 'id_unidad IN (SELECT id_unidad FROM UNIDADES WHERE id_propiedad IN (SELECT id_propiedad FROM PROPIEDADES WHERE id_organizacion = ' || v_org || '))'; END IF;
            IF p_tab = 'OBRAS' THEN RETURN 'id_unidad IN (SELECT id_unidad FROM UNIDADES WHERE id_propiedad IN (SELECT id_propiedad FROM PROPIEDADES WHERE id_organizacion = ' || v_org || '))'; END IF;
            RETURN 'id_unidad IN (SELECT id_unidad FROM UNIDADES WHERE id_propiedad IN (SELECT id_propiedad FROM PROPIEDADES WHERE id_organizacion = ' || v_org || '))';
        END IF;
    EXCEPTION
        WHEN OTHERS THEN
            RETURN '1=0';
    END FN_FILTRO_UNIDAD;

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
        IF v_rol IN ('RESIDENTE', 'PROPIETARIO_UNIDAD', 'RESIDENTE_CONVIVENCIA') THEN RETURN 'id_usuario = ' || v_usr; END IF;
        RETURN 'id_usuario IN (SELECT id_usuario FROM USUARIO_ASIGNACIONES WHERE id_organizacion = ' || v_org || ')';
    EXCEPTION
        WHEN OTHERS THEN
            RETURN '1=0';
    END FN_FILTRO_USUARIOS;

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
        IF v_rol IN ('RESIDENTE', 'PROPIETARIO_UNIDAD', 'RESIDENTE_CONVIVENCIA') THEN RETURN 'id_usuario = ' || v_usr; END IF;
        RETURN 'id_organizacion = ' || v_org;
    EXCEPTION
        WHEN OTHERS THEN
            RETURN '1=0';
    END FN_FILTRO_ASIGNACION;

    FUNCTION FN_FILTRO_GLOBAL_READONLY (p_schema IN VARCHAR2, p_tab IN VARCHAR2) RETURN VARCHAR2 AS
    BEGIN
        RETURN '1=1';
    EXCEPTION
        WHEN OTHERS THEN
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
    EXCEPTION
        WHEN OTHERS THEN
            RETURN '1=0';
    END FN_FILTRO_GLOBAL_MUTATE;

END PKG_SAED_SECURITY_RLS;
/

-- Validación final de compilación
DECLARE
    v_status VARCHAR2(30);
BEGIN
    SELECT STATUS INTO v_status 
    FROM USER_OBJECTS 
    WHERE OBJECT_NAME = 'PKG_SAED_SECURITY_RLS' AND OBJECT_TYPE = 'PACKAGE BODY';
    
    IF v_status != 'VALID' THEN
        RAISE_APPLICATION_ERROR(-20099, 'Error crítico: PKG_SAED_SECURITY_RLS no compiló con estado VALID.');
    END IF;
END;
/
