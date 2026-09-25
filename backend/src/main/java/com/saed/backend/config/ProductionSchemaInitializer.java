package com.saed.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Ensures critical database tables, constraints, indexes, and initial seeds
 * exist in all runtime profiles (including 'prod' on Render / Oracle Cloud ATP).
 * Fully idempotent.
 */
@Component
@Order(1)
public class ProductionSchemaInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProductionSchemaInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    public ProductionSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private void runElevated(String plsql) {
        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); " + plsql + "; END;");
        } catch (Exception e) {
            log.debug("Notice on elevated execution: {}", e.getMessage());
        }
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("[SchemaInit] Verificando integridad de esquema para producción...");

        initSaedContext();
        initSecurityRlsPackage();
        initCoreRlsPoliciesClean();
        initPlantillasContratos();
        initRoles();
        initSuperAdminUser();
        // cleanupLegacyTestData(); // Desactivado para no purgar usuarios reales en arranques del sistema
        initResidentesUnidadConstraints();
        initTokensActivacion();
        initOnboardingIntenciones();
        initMembresiaOrg1();
        initPaquetesIntentosPin();
        initPaquetesFotoClob();
        initModulosYPlanModulos();
        initContratosPipelineColumns();
        initTransaccionesPagoSaaSSegregation();
        initPazYSalvosPipelineColumns();
        initManualPaymentApprovalLifecycle();
        initSpValidarConsumirQr();
        initDomicilios();
        initMantenimientosYBloqueos();
        initTrabajadoresSchemaAndRls();
        initPqrsSlaConfig();
        initIncidentesPipeline();
        initReglamentosNormativaPipeline();
        initAsambleasGovernancePipeline();
        initPolizasSeguroPipeline();
        initReportesConfiguradosYHistorialPipeline();
        initPorteriaTurnos();
        initCarteraVirtualColumn();

        log.info("[SchemaInit] Verificación de esquema completada.");
    }

    private void initSaedContext() {
        try {
            jdbcTemplate.execute("CREATE OR REPLACE CONTEXT SAED_CTX USING PKG_SAED_SESSION");
            log.info("[SchemaInit] Contexto de seguridad SAED_CTX asegurado como session-local.");
        } catch (Exception e) {
            log.debug("[SchemaInit] Aviso al asegurar contexto SAED_CTX: {}", e.getMessage());
        }
    }

    private void initSecurityRlsPackage() {
        try {
            try {
                jdbcTemplate.execute("""
                    BEGIN
                        FOR r IN (SELECT object_name, policy_name, policy_group FROM user_policies WHERE object_name = 'PERSONAS') LOOP
                            BEGIN
                                DBMS_RLS.DROP_GROUPED_POLICY(USER, r.object_name, r.policy_group, r.policy_name);
                            EXCEPTION WHEN OTHERS THEN
                                BEGIN
                                    DBMS_RLS.DROP_POLICY(USER, r.object_name, r.policy_name);
                                EXCEPTION WHEN OTHERS THEN NULL;
                                END;
                            END;
                        END LOOP;
                    END;
                """);
            } catch (Exception ignored) {}

            try {
                Integer upToDate = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM USER_SOURCE WHERE NAME = 'PKG_SAED_SECURITY_RLS' AND TYPE = 'PACKAGE BODY' AND TEXT LIKE '%2026.09.25.V6_USUARIOS_FIX%'",
                    Integer.class
                );
                String rlsStatus = jdbcTemplate.queryForObject(
                    "SELECT STATUS FROM USER_OBJECTS WHERE OBJECT_NAME = 'PKG_SAED_SECURITY_RLS' AND OBJECT_TYPE = 'PACKAGE BODY'",
                    String.class
                );
                if (upToDate != null && upToDate > 0 && "VALID".equalsIgnoreCase(rlsStatus)) {
                    log.info("[SchemaInit] PKG_SAED_SECURITY_RLS ya se encuentra actualizado (versión 2026.09.25.V6) y en estado VALID. Se omite recompilación DDL.");
                    return;
                }
            } catch (Exception ignored) {}

            log.info("[SchemaInit] Compilando especificación y cuerpo completo de PKG_SAED_SECURITY_RLS...");
            jdbcTemplate.execute("""
                CREATE OR REPLACE PACKAGE PKG_SAED_SECURITY_RLS AS
                    FUNCTION FN_FILTRO_ORGANIZACION (p_schema IN VARCHAR2, p_tab IN VARCHAR2) RETURN VARCHAR2;
                    FUNCTION FN_FILTRO_PROPIEDAD    (p_schema IN VARCHAR2, p_tab IN VARCHAR2) RETURN VARCHAR2;
                    FUNCTION FN_FILTRO_UNIDAD       (p_schema IN VARCHAR2, p_tab IN VARCHAR2) RETURN VARCHAR2;
                    FUNCTION FN_FILTRO_USUARIOS     (p_schema IN VARCHAR2, p_tab IN VARCHAR2) RETURN VARCHAR2;
                    FUNCTION FN_FILTRO_ASIGNACION   (p_schema IN VARCHAR2, p_tab IN VARCHAR2) RETURN VARCHAR2;
                    FUNCTION FN_FILTRO_PERSONAS     (p_schema IN VARCHAR2, p_tab IN VARCHAR2) RETURN VARCHAR2;
                    FUNCTION FN_FILTRO_GLOBAL_READONLY (p_schema IN VARCHAR2, p_tab IN VARCHAR2) RETURN VARCHAR2;
                    FUNCTION FN_FILTRO_GLOBAL_MUTATE   (p_schema IN VARCHAR2, p_tab IN VARCHAR2) RETURN VARCHAR2;
                END PKG_SAED_SECURITY_RLS;
            """);

            jdbcTemplate.execute("""
                CREATE OR REPLACE PACKAGE BODY PKG_SAED_SECURITY_RLS AS
                    -- RLS_BUILD_VERSION: 2026.09.25.V6_USUARIOS_FIX

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

                        IF p_tab = 'PERSONAS' THEN
                            RETURN '1=1';
                        END IF;

                        IF v_rol IN ('RESIDENTE', 'PROPIETARIO_UNIDAD', 'RESIDENTE_CONVIVENCIA') THEN
                            RETURN 'id_organizacion = ' || v_org;
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

                        IF p_tab = 'TRABAJADORES' THEN
                            RETURN 'id_proveedor IN (SELECT id_proveedor FROM PROVEEDORES WHERE id_organizacion = ' || v_org || ')';
                        END IF;

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
                            IF p_tab = 'SANCION_DESCARGOS' THEN
                                RETURN 'id_sancion IN (SELECT id_sancion FROM SANCIONES WHERE id_propiedad IN (SELECT id_propiedad FROM USUARIO_ASIGNACIONES WHERE id_usuario = ' || v_usr || ' AND estado=''ACTIVA''))';
                            END IF;
                            IF p_tab IN ('ACTAS_ASAMBLEA', 'ASISTENCIAS_ASAMBLEA', 'PODERES_REPRESENTACION') THEN
                                RETURN 'id_asamblea IN (SELECT id_asamblea FROM ASAMBLEAS WHERE id_propiedad IN (SELECT id_propiedad FROM USUARIO_ASIGNACIONES WHERE id_usuario = ' || v_usr || ' AND estado=''ACTIVA''))';
                            END IF;
                            IF p_tab = 'ENCUESTA_RESPUESTAS' THEN
                                RETURN 'id_persona IN (SELECT id_persona FROM USUARIOS WHERE id_usuario = ' || v_usr || ')';
                            END IF;
                            IF p_tab = 'ACCESOS_CONFIGURADOS' THEN
                                RETURN 'id_porteria IN (SELECT id_porteria FROM PORTERIAS WHERE id_propiedad IN (SELECT id_propiedad FROM USUARIO_ASIGNACIONES WHERE id_usuario = ' || v_usr || ' AND estado=''ACTIVA''))';
                            END IF;
                            IF p_tab = 'CATALOGO_ZONAS' THEN
                                RETURN '1=1';
                            END IF;
                            IF p_tab = 'TRABAJADORES' THEN
                                RETURN 'id_trabajador IN (SELECT id_trabajador FROM OBRA_TRABAJADORES ot JOIN OBRAS o ON ot.id_obra = o.id_obra JOIN UNIDADES u ON o.id_unidad = u.id_unidad WHERE u.id_unidad IN (SELECT id_unidad FROM USUARIO_ASIGNACIONES WHERE id_usuario = ' || v_usr || ' AND estado=''ACTIVA'' AND id_unidad IS NOT NULL))';
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
                            IF p_tab = 'SANCION_DESCARGOS' THEN RETURN 'id_sancion IN (SELECT id_sancion FROM SANCIONES WHERE id_propiedad = ' || v_prop || ')'; END IF;
                            IF p_tab IN ('ACTAS_ASAMBLEA', 'ASISTENCIAS_ASAMBLEA', 'PODERES_REPRESENTACION') THEN RETURN 'id_asamblea IN (SELECT id_asamblea FROM ASAMBLEAS WHERE id_propiedad = ' || v_prop || ')'; END IF;
                            IF p_tab = 'ENCUESTA_RESPUESTAS' THEN RETURN 'id_opcion IN (SELECT id_opcion FROM ENCUESTA_OPCIONES eo JOIN ENCUESTAS e ON eo.id_encuesta = e.id_encuesta WHERE e.id_propiedad = ' || v_prop || ')'; END IF;
                            IF p_tab = 'ACCESOS_CONFIGURADOS' THEN RETURN 'id_porteria IN (SELECT id_porteria FROM PORTERIAS WHERE id_propiedad = ' || v_prop || ')'; END IF;
                            IF p_tab = 'CATALOGO_ZONAS' THEN RETURN '1=1'; END IF;
                            IF p_tab = 'TRABAJADORES' THEN RETURN 'id_trabajador IN (SELECT id_trabajador FROM OBRA_TRABAJADORES ot JOIN OBRAS o ON ot.id_obra = o.id_obra JOIN UNIDADES u ON o.id_unidad = u.id_unidad WHERE u.id_propiedad = ' || v_prop || ') OR id_proveedor IN (SELECT id_proveedor FROM PROVEEDORES WHERE id_organizacion = ' || v_org || ')'; END IF;
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
                            IF p_tab = 'SANCION_DESCARGOS' THEN RETURN 'id_sancion IN (SELECT id_sancion FROM SANCIONES WHERE id_propiedad IN (SELECT id_propiedad FROM PROPIEDADES WHERE id_organizacion = ' || v_org || '))'; END IF;
                            IF p_tab IN ('ACTAS_ASAMBLEA', 'ASISTENCIAS_ASAMBLEA', 'PODERES_REPRESENTACION') THEN RETURN 'id_asamblea IN (SELECT id_asamblea FROM ASAMBLEAS WHERE id_propiedad IN (SELECT id_propiedad FROM PROPIEDADES WHERE id_organizacion = ' || v_org || '))'; END IF;
                            IF p_tab = 'ENCUESTA_RESPUESTAS' THEN RETURN 'id_opcion IN (SELECT id_opcion FROM ENCUESTA_OPCIONES eo JOIN ENCUESTAS e ON eo.id_encuesta = e.id_encuesta JOIN PROPIEDADES p ON e.id_propiedad = p.id_propiedad WHERE p.id_organizacion = ' || v_org || ')'; END IF;
                            IF p_tab = 'ACCESOS_CONFIGURADOS' THEN RETURN 'id_porteria IN (SELECT id_porteria FROM PORTERIAS JOIN PROPIEDADES ON PORTERIAS.id_propiedad = PROPIEDADES.id_propiedad WHERE PROPIEDADES.id_organizacion = ' || v_org || ')'; END IF;
                            IF p_tab = 'CATALOGO_ZONAS' THEN RETURN '1=1'; END IF;
                            IF p_tab = 'TRABAJADORES' THEN RETURN 'id_proveedor IN (SELECT id_proveedor FROM PROVEEDORES WHERE id_organizacion = ' || v_org || ')'; END IF;
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
                                RETURN 'id_unidad IN (SELECT id_unidad FROM USUARIO_ASIGNACIONES WHERE id_usuario = ' || v_usr || ' AND estado=''ACTIVA'' AND id_unidad IS NOT NULL)';
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
                            IF p_tab = 'TRANSACCIONES_PAGO' THEN RETURN 'id_unidad IN (SELECT id_unidad FROM UNIDADES WHERE id_propiedad = ' || v_prop || ')'; END IF;
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
                        -- Version: 2026.09.25.V6_USUARIOS_FIX
                        IF v_state IN ('ANONYMOUS', 'CLEARING') THEN RETURN '1=0'; END IF;
                        IF v_state = 'BOOTSTRAP' THEN RETURN '1=1'; END IF;

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
                        IF v_state = 'BOOTSTRAP' THEN RETURN '1=1'; END IF;

                        IF v_rol = 'SUPERADMIN' THEN RETURN '1=1'; END IF;
                        IF v_org IS NULL OR v_org = '0' THEN RETURN '1=0'; END IF;
                        IF v_rol IN ('RESIDENTE', 'PROPIETARIO_UNIDAD', 'RESIDENTE_CONVIVENCIA') THEN RETURN 'id_usuario = ' || v_usr; END IF;
                        RETURN 'id_organizacion = ' || v_org;
                    EXCEPTION
                        WHEN OTHERS THEN
                            RETURN '1=0';
                    END FN_FILTRO_ASIGNACION;

                    FUNCTION FN_FILTRO_PERSONAS (p_schema IN VARCHAR2, p_tab IN VARCHAR2) RETURN VARCHAR2 AS
                    BEGIN
                        RETURN '1=1';
                    EXCEPTION
                        WHEN OTHERS THEN
                            RETURN '1=1';
                    END FN_FILTRO_PERSONAS;

                    FUNCTION FN_FILTRO_GLOBAL_READONLY (p_schema IN VARCHAR2, p_tab IN VARCHAR2) RETURN VARCHAR2 AS
                    BEGIN
                        RETURN '1=1';
                    EXCEPTION
                        WHEN OTHERS THEN
                            RETURN '1=1';
                    END FN_FILTRO_GLOBAL_READONLY;

                    FUNCTION FN_FILTRO_GLOBAL_MUTATE (p_schema IN VARCHAR2, p_tab IN VARCHAR2) RETURN VARCHAR2 AS
                        v_rol VARCHAR2(30) := SYS_CONTEXT('SAED_CTX', 'ROL_CODIGO');
                        v_state VARCHAR2(30) := NVL(SYS_CONTEXT('SAED_CTX', 'STATE'), 'ANONYMOUS');
                    BEGIN
                        IF v_state IN ('ANONYMOUS', 'CLEARING') THEN RETURN '1=0'; END IF;
                        IF v_state = 'BOOTSTRAP' THEN RETURN '1=1'; END IF;
                        IF v_rol = 'SUPERADMIN' THEN RETURN '1=1'; END IF;
                        RETURN '1=0';
                    EXCEPTION
                        WHEN OTHERS THEN
                            RETURN '1=0';
                    END FN_FILTRO_GLOBAL_MUTATE;

                END PKG_SAED_SECURITY_RLS;
            """);

            try {
                jdbcTemplate.execute("ALTER PACKAGE PKG_SAED_SECURITY_RLS COMPILE");
                jdbcTemplate.execute("ALTER PACKAGE PKG_SAED_SECURITY_RLS COMPILE BODY");
                jdbcTemplate.execute("ALTER PACKAGE PKG_SAED_SESSION COMPILE");
                jdbcTemplate.execute("ALTER PACKAGE PKG_SAED_SESSION COMPILE BODY");
                try { jdbcTemplate.execute("GRANT EXECUTE ON PKG_SAED_SECURITY_RLS TO PUBLIC"); } catch (Exception ignored) {}
                try { jdbcTemplate.execute("GRANT EXECUTE ON PKG_SAED_SESSION TO PUBLIC"); } catch (Exception ignored) {}
            } catch (Exception eCompile) {
                log.error("[SchemaInit] Error recompilando paquetes de sesión/RLS: {}", eCompile.getMessage(), eCompile);
            }

            try {
                jdbcTemplate.execute("""
                    BEGIN
                        BEGIN DBMS_RLS.DROP_POLICY(NULL, 'PERSONAS', 'POL_RLS_ORG_PERSONAS'); EXCEPTION WHEN OTHERS THEN NULL; END;
                        BEGIN DBMS_RLS.DROP_GROUPED_POLICY(NULL, 'PERSONAS', 'SYS_DEFAULT', 'POL_RLS_ORG_PERSONAS'); EXCEPTION WHEN OTHERS THEN NULL; END;
                    END;
                """);
                log.info("[SchemaInit] Política RLS POL_RLS_ORG_PERSONAS (agrupada y no agrupada) removida exitosamente.");
            } catch (Exception ex) {
                log.debug("[SchemaInit] Aviso al remover política POL_RLS_ORG_PERSONAS: {}", ex.getMessage());
            }

            try {
                String rlsStatus = jdbcTemplate.queryForObject(
                    "SELECT STATUS FROM USER_OBJECTS WHERE OBJECT_NAME = 'PKG_SAED_SECURITY_RLS' AND OBJECT_TYPE = 'PACKAGE BODY'",
                    String.class
                );
                String sessionStatus = jdbcTemplate.queryForObject(
                    "SELECT STATUS FROM USER_OBJECTS WHERE OBJECT_NAME = 'PKG_SAED_SESSION' AND OBJECT_TYPE = 'PACKAGE BODY'",
                    String.class
                );
                if ("VALID".equalsIgnoreCase(rlsStatus) && "VALID".equalsIgnoreCase(sessionStatus)) {
                    log.info("[SchemaInit] PKG_SAED_SECURITY_RLS ({}) y PKG_SAED_SESSION ({}) verificados exitosamente.", rlsStatus, sessionStatus);
                } else {
                    log.error("[SchemaInit] ¡ALERTA! Estado paquetes: PKG_SAED_SECURITY_RLS={}, PKG_SAED_SESSION={}", rlsStatus, sessionStatus);
                    var errors = jdbcTemplate.queryForList(
                        "SELECT NAME, TYPE, LINE, POSITION, TEXT FROM USER_ERRORS WHERE NAME IN ('PKG_SAED_SECURITY_RLS', 'PKG_SAED_SESSION')"
                    );
                    for (var err : errors) {
                        log.error("[SchemaInit] PL/SQL Error en {} ({}) L{}:{} - {}", 
                            err.get("NAME"), err.get("TYPE"), err.get("LINE"), err.get("POSITION"), err.get("TEXT"));
                    }
                }
            } catch (Exception ex) {
                log.debug("[SchemaInit] Aviso al verificar estado de paquetes RLS: {}", ex.getMessage());
            }
        } catch (Exception e) {
            log.warn("[SchemaInit] Aviso al compilar paquete PKG_SAED_SECURITY_RLS: {}", e.getMessage());
        }
    }

    private void refreshRlsPolicy(String tableName, String policyName, String policyFunction) {
        try {
            jdbcTemplate.execute(String.format("""
                BEGIN
                    BEGIN DBMS_RLS.DROP_POLICY(NULL, '%s', '%s'); EXCEPTION WHEN OTHERS THEN NULL; END;
                    BEGIN DBMS_RLS.DROP_GROUPED_POLICY(NULL, '%s', 'SYS_DEFAULT', '%s'); EXCEPTION WHEN OTHERS THEN NULL; END;
                    DBMS_RLS.ADD_GROUPED_POLICY(
                        object_schema   => NULL,
                        object_name     => '%s',
                        policy_group    => 'SYS_DEFAULT',
                        policy_name     => '%s',
                        function_schema => NULL,
                        policy_function => '%s',
                        update_check    => FALSE,
                        enable          => TRUE,
                        static_policy   => FALSE,
                        policy_type     => dbms_rls.DYNAMIC,
                        long_predicate  => FALSE
                    );
                END;
            """, tableName, policyName, tableName, policyName, tableName, policyName, policyFunction));
        } catch (Exception e) {
            log.debug("[SchemaInit] Aviso al refrescar política {} en {}: {}", policyName, tableName, e.getMessage());
        }
    }

    private void initCoreRlsPoliciesClean() {
        try {
            // Drop policy on PERSONAS permanently (PERSONAS has no direct org/prop column)
            try {
                jdbcTemplate.execute("""
                    BEGIN
                        FOR r IN (SELECT object_name, policy_name, policy_group FROM user_policies WHERE object_name = 'PERSONAS') LOOP
                            BEGIN
                                DBMS_RLS.DROP_GROUPED_POLICY(USER, r.object_name, r.policy_group, r.policy_name);
                            EXCEPTION WHEN OTHERS THEN
                                BEGIN
                                    DBMS_RLS.DROP_POLICY(USER, r.object_name, r.policy_name);
                                EXCEPTION WHEN OTHERS THEN NULL;
                                END;
                            END;
                        END LOOP;
                    END;
                """);
                log.info("[SchemaInit] Políticas RLS en PERSONAS eliminadas exitosamente mediante cursor dinámico.");
            } catch (Exception ignored) {}

            // Unidades & Financials
            refreshRlsPolicy("UNIDADES", "POL_RLS_UNI_UNIDADES", "PKG_SAED_SECURITY_RLS.FN_FILTRO_UNIDAD");
            refreshRlsPolicy("CUOTAS", "POL_RLS_UNI_CUOTAS", "PKG_SAED_SECURITY_RLS.FN_FILTRO_UNIDAD");
            refreshRlsPolicy("PAGOS", "POL_RLS_UNI_PAGOS", "PKG_SAED_SECURITY_RLS.FN_FILTRO_UNIDAD");
            refreshRlsPolicy("MULTAS", "POL_RLS_UNI_MULTAS", "PKG_SAED_SECURITY_RLS.FN_FILTRO_UNIDAD");
            refreshRlsPolicy("SANCIONES", "POL_RLS_PROP_SANCIONES", "PKG_SAED_SECURITY_RLS.FN_FILTRO_PROPIEDAD");
            refreshRlsPolicy("RESIDENTES_UNIDAD", "POL_RLS_UNI_RESIDENTES_UNID", "PKG_SAED_SECURITY_RLS.FN_FILTRO_UNIDAD");
            refreshRlsPolicy("PROPIETARIOS_UNIDAD", "POL_RLS_UNI_PROPIETARIOS_UN", "PKG_SAED_SECURITY_RLS.FN_FILTRO_UNIDAD");
            refreshRlsPolicy("CONTRATOS", "POL_RLS_PROP_CONTRATOS", "PKG_SAED_SECURITY_RLS.FN_FILTRO_PROPIEDAD");
            refreshRlsPolicy("PROPIEDADES", "POL_RLS_ORG_PROPIEDADES", "PKG_SAED_SECURITY_RLS.FN_FILTRO_ORGANIZACION");
            refreshRlsPolicy("ORGANIZACIONES", "POL_RLS_ORG_ORGANIZACIONES", "PKG_SAED_SECURITY_RLS.FN_FILTRO_ORGANIZACION");
            refreshRlsPolicy("PAQUETES", "POL_RLS_PROP_PAQUETES", "PKG_SAED_SECURITY_RLS.FN_FILTRO_PROPIEDAD");
            refreshRlsPolicy("VISITAS", "POL_RLS_PROP_VISITAS", "PKG_SAED_SECURITY_RLS.FN_FILTRO_PROPIEDAD");
            refreshRlsPolicy("PARQUEADEROS", "POL_RLS_PROP_PARQUEADEROS", "PKG_SAED_SECURITY_RLS.FN_FILTRO_PROPIEDAD");
            refreshRlsPolicy("PQRS_TICKETS", "POL_RLS_PROP_PQRS_TICKETS", "PKG_SAED_SECURITY_RLS.FN_FILTRO_PROPIEDAD");
            refreshRlsPolicy("VEHICULOS", "POL_RLS_UNI_VEHICULOS", "PKG_SAED_SECURITY_RLS.FN_FILTRO_UNIDAD");
            refreshRlsPolicy("MASCOTAS", "POL_RLS_UNI_MASCOTAS", "PKG_SAED_SECURITY_RLS.FN_FILTRO_UNIDAD");
            refreshRlsPolicy("BLOQUES", "POL_RLS_PROP_BLOQUES", "PKG_SAED_SECURITY_RLS.FN_FILTRO_PROPIEDAD");
            refreshRlsPolicy("MANTENIMIENTOS", "POL_RLS_PROP_MANTENIMIENTOS", "PKG_SAED_SECURITY_RLS.FN_FILTRO_PROPIEDAD");
            refreshRlsPolicy("USUARIOS", "POL_RLS_SEC_USR", "PKG_SAED_SECURITY_RLS.FN_FILTRO_USUARIOS");
            refreshRlsPolicy("USUARIO_ASIGNACIONES", "POL_RLS_SEC_UA", "PKG_SAED_SECURITY_RLS.FN_FILTRO_ASIGNACION");
            log.info("[SchemaInit] Políticas RLS limpias y normalizadas aplicadas a todas las tablas del sistema.");

            // Limpieza de personas o usuarios truncos sin asignaciones activas para documento 1066268898
            try {
                jdbcTemplate.execute("""
                    BEGIN
                        FOR r IN (
                            SELECT p.ID_PERSONA, u.ID_USUARIO 
                            FROM PERSONAS p
                            LEFT JOIN USUARIOS u ON u.ID_PERSONA = p.ID_PERSONA
                            LEFT JOIN USUARIO_ASIGNACIONES ua ON ua.ID_USUARIO = u.ID_USUARIO
                            WHERE p.NUMERO_DOCUMENTO = '1066268898'
                              AND ua.ID_ASIGNACION IS NULL
                        ) LOOP
                            IF r.ID_USUARIO IS NOT NULL THEN
                                DELETE FROM USUARIOS WHERE ID_USUARIO = r.ID_USUARIO;
                            END IF;
                            DELETE FROM PERSONAS WHERE ID_PERSONA = r.ID_PERSONA;
                        END LOOP;
                        COMMIT;
                    EXCEPTION WHEN OTHERS THEN NULL;
                    END;
                """);
            } catch (Exception ignored) {}
        } catch (Exception e) {
            log.warn("[SchemaInit] Aviso en initCoreRlsPoliciesClean: {}", e.getMessage());
        }
    }

    private void initPlantillasContratos() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM USER_TABLES WHERE TABLE_NAME = 'PLANTILLAS_CONTRATOS'",
                Integer.class
            );
            if (count == null || count == 0) {
                log.info("[SchemaInit] Creando tabla PLANTILLAS_CONTRATOS...");
                jdbcTemplate.execute("""
                    CREATE TABLE PLANTILLAS_CONTRATOS (
                        ID_PLANTILLA NUMBER GENERATED ALWAYS AS IDENTITY MINVALUE 1 MAXVALUE 9999999999999999999999999999 INCREMENT BY 1 START WITH 1 CACHE 20 NOORDER NOCYCLE NOT NULL ENABLE,
                        ID_ORGANIZACION NUMBER NOT NULL ENABLE,
                        CODIGO VARCHAR2(50 CHAR) NOT NULL ENABLE,
                        NOMBRE VARCHAR2(150 CHAR) NOT NULL ENABLE,
                        TIPO_CONTRATO VARCHAR2(50 CHAR) NOT NULL ENABLE,
                        DESCRIPCION VARCHAR2(500 CHAR),
                        CONTENIDO_HTML CLOB NOT NULL ENABLE,
                        VARIABLES_DISPONIBLES CLOB,
                        CAMPOS_REQUERIDOS CLOB,
                        VERSION NUMBER(5, 0) DEFAULT 1 NOT NULL ENABLE,
                        ESTADO VARCHAR2(30 CHAR) DEFAULT 'ACTIVA' NOT NULL ENABLE,
                        VIGENCIA_DESDE DATE DEFAULT CURRENT_DATE NOT NULL ENABLE,
                        VIGENCIA_HASTA DATE,
                        CREADO_POR NUMBER,
                        FECHA_CREACION TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ENABLE,
                        FECHA_ACTUALIZACION TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ENABLE,
                        CONSTRAINT PK_PLANTILLAS_CONTRATOS PRIMARY KEY (ID_PLANTILLA),
                        CONSTRAINT FK_PLANTILLAS_CONTRATOS_ORG FOREIGN KEY (ID_ORGANIZACION) REFERENCES ORGANIZACIONES(ID_ORGANIZACION) ON DELETE CASCADE,
                        CONSTRAINT UQ_PLANTILLAS_ORG_COD_VER UNIQUE (ID_ORGANIZACION, CODIGO, VERSION),
                        CONSTRAINT CK_PLANTILLAS_CONTR_TIPO CHECK (TIPO_CONTRATO IN ('INICIAL', 'RENOVACION', 'PERMANENCIA', 'COMERCIAL', 'OTRO')),
                        CONSTRAINT CK_PLANTILLAS_CONTR_ESTADO CHECK (ESTADO IN ('ACTIVA', 'BORRADOR', 'HISTORICA', 'SUSPENDIDA', 'INACTIVA'))
                    )
                """);
                try {
                    jdbcTemplate.execute("CREATE INDEX IX_PLANTILLAS_ORG_ESTADO ON PLANTILLAS_CONTRATOS (ID_ORGANIZACION, ESTADO)");
                    jdbcTemplate.execute("CREATE INDEX IX_PLANTILLAS_ORG_TIPO ON PLANTILLAS_CONTRATOS (ID_ORGANIZACION, TIPO_CONTRATO)");
                } catch (Exception ignored) {}

                try {
                    jdbcTemplate.execute("""
                        BEGIN
                            DBMS_RLS.ADD_GROUPED_POLICY(
                                object_schema   => NULL,
                                object_name     => 'PLANTILLAS_CONTRATOS',
                                policy_group    => 'SYS_DEFAULT',
                                policy_name     => 'POL_RLS_ORG_PLANTILLAS_CONTR',
                                function_schema => NULL,
                                policy_function => 'PKG_SAED_SECURITY_RLS.FN_FILTRO_ORGANIZACION',
                                statement_types => 'SELECT,INSERT,UPDATE,DELETE',
                                update_check    => TRUE,
                                enable          => TRUE,
                                static_policy   => FALSE,
                                policy_type     => DBMS_RLS.DYNAMIC
                            );
                        EXCEPTION
                            WHEN OTHERS THEN
                                NULL;
                        END;
                    """);
                } catch (Exception e) {
                    log.debug("[SchemaInit] Aviso RLS en PLANTILLAS_CONTRATOS: {}", e.getMessage());
                }
                log.info("[SchemaInit] Tabla PLANTILLAS_CONTRATOS creada exitosamente.");
            } else {
                try {
                    jdbcTemplate.execute("ALTER TABLE PLANTILLAS_CONTRATOS DROP CONSTRAINT CK_PLANTILLAS_CONTR_ESTADO");
                } catch (Exception ignored) {}
                try {
                    jdbcTemplate.execute("ALTER TABLE PLANTILLAS_CONTRATOS ADD CONSTRAINT CK_PLANTILLAS_CONTR_ESTADO CHECK (ESTADO IN ('ACTIVA', 'BORRADOR', 'HISTORICA', 'SUSPENDIDA', 'INACTIVA'))");
                } catch (Exception ignored) {}
            }

            // Seed initial template if table is empty for organization 1
            Integer countTemplates = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM PLANTILLAS_CONTRATOS WHERE ID_ORGANIZACION = 1",
                Integer.class
            );
            if (countTemplates == null || countTemplates == 0) {
                runElevated("NULL");
                String defaultHtml = "<h2>CONTRATO DE ARRENDAMIENTO DE VIVIENDA URBANA</h2><p>Entre ${propiedad.nombre} y ${inquilino.nombre_completo} para la unidad ${apartamento.numero}.</p>";
                jdbcTemplate.update("""
                    INSERT INTO PLANTILLAS_CONTRATOS (
                        ID_ORGANIZACION, CODIGO, NOMBRE, TIPO_CONTRATO, DESCRIPCION,
                        CONTENIDO_HTML, VARIABLES_DISPONIBLES, CAMPOS_REQUERIDOS, VERSION,
                        ESTADO, VIGENCIA_DESDE, CREADO_POR
                    ) VALUES (
                        1, 'CONTRATO_ESTANDAR_2026', 'Contrato Estándar Residencial', 'INICIAL',
                        'Plantilla base predeterminada para contratos de arrendamiento residencial.',
                        ?, '["propiedad.nombre","inquilino.nombre_completo","apartamento.numero","contrato.canon_mensual"]',
                        '["propiedad.nombre","inquilino.nombre_completo","apartamento.numero"]', 1,
                        'ACTIVA', TRUNC(SYSDATE), 1
                    )
                """, defaultHtml);
                log.info("[SchemaInit] Plantilla de contrato predeterminada creada para org 1.");
            }
        } catch (Exception e) {
            log.warn("[SchemaInit] Aviso al verificar PLANTILLAS_CONTRATOS: {}", e.getMessage());
        }
    }

    private void initResidentesUnidadConstraints() {
        try {
            jdbcTemplate.execute("ALTER TABLE RESIDENTES_UNIDAD DROP CONSTRAINT CK_RESIDUNIDAD_TIPO");
        } catch (Exception ignored) {}
        try {
            jdbcTemplate.execute("ALTER TABLE RESIDENTES_UNIDAD ADD CONSTRAINT CK_RESIDUNIDAD_TIPO CHECK (tipo_residente IN ('PROPIETARIO', 'ARRENDATARIO', 'FAMILIAR', 'CONVIVIENTE', 'OTRO', 'TITULAR'))");
            log.info("[SchemaInit] Restricción CK_RESIDUNIDAD_TIPO actualizada para soportar CONVIVIENTE y TITULAR.");
        } catch (Exception e) {
            log.debug("[SchemaInit] Aviso al actualizar CK_RESIDUNIDAD_TIPO: {}", e.getMessage());
        }
    }

    private void initTokensActivacion() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM USER_TABLES WHERE TABLE_NAME = 'TOKENS_ACTIVACION'",
                Integer.class
            );
            if (count == null || count == 0) {
                jdbcTemplate.execute("""
                    CREATE TABLE TOKENS_ACTIVACION (
                        ID_TOKEN NUMBER GENERATED ALWAYS AS IDENTITY MINVALUE 1 MAXVALUE 9999999999999999999999999999 INCREMENT BY 1 START WITH 1 CACHE 20 NOORDER NOCYCLE NOT NULL ENABLE,
                        ID_USUARIO NUMBER NOT NULL ENABLE,
                        TOKEN_HASH VARCHAR2(64 CHAR) NOT NULL ENABLE,
                        TIPO VARCHAR2(30 CHAR) DEFAULT 'ACTIVACION_INICIAL' NOT NULL ENABLE,
                        FECHA_EXPIRACION TIMESTAMP(6) WITH TIME ZONE NOT NULL ENABLE,
                        USADO NUMBER(1, 0) DEFAULT 0 NOT NULL ENABLE,
                        FECHA_USO TIMESTAMP(6) WITH TIME ZONE,
                        FECHA_CREACION TIMESTAMP(6) WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL ENABLE,
                        IP_SOLICITUD VARCHAR2(50 CHAR),
                        CONSTRAINT PK_TOKENS_ACTIVACION PRIMARY KEY (ID_TOKEN),
                        CONSTRAINT FK_TOKENS_ACT_USUARIO FOREIGN KEY (ID_USUARIO) REFERENCES USUARIOS(ID_USUARIO) ON DELETE CASCADE,
                        CONSTRAINT UQ_TOKENS_ACT_HASH UNIQUE (TOKEN_HASH),
                        CONSTRAINT CK_TOKENS_ACT_TIPO CHECK (TIPO IN ('ACTIVACION_INICIAL', 'RECUPERACION_PASSWORD')),
                        CONSTRAINT CK_TOKENS_ACT_USADO CHECK (USADO IN (0, 1))
                    )
                """);
                try {
                    jdbcTemplate.execute("CREATE INDEX IX_TOKENS_ACT_USER_TIPO ON TOKENS_ACTIVACION (ID_USUARIO, TIPO, USADO)");
                    jdbcTemplate.execute("CREATE INDEX IX_TOKENS_ACT_EXPIRACION ON TOKENS_ACTIVACION (FECHA_EXPIRACION, USADO)");
                } catch (Exception ignored) {}
                log.info("[SchemaInit] Tabla TOKENS_ACTIVACION creada exitosamente.");
            }
        } catch (Exception e) {
            log.debug("[SchemaInit] Aviso al verificar TOKENS_ACTIVACION: {}", e.getMessage());
        }
    }

    private void initOnboardingIntenciones() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM USER_TABLES WHERE TABLE_NAME = 'ONBOARDING_INTENCIONES'",
                Integer.class
            );
            if (count == null || count == 0) {
                jdbcTemplate.execute("""
                    CREATE TABLE ONBOARDING_INTENCIONES (
                        REFERENCIA VARCHAR2(100 CHAR) PRIMARY KEY,
                        DATOS_REGISTRO CLOB NOT NULL,
                        MONTO_CENTAVOS NUMBER(14,0) NOT NULL,
                        ESTADO VARCHAR2(30 CHAR) DEFAULT 'PENDIENTE' NOT NULL,
                        FECHA_CREACION TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
                        FECHA_ACTUALIZACION TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
                    )
                """);
                log.info("[SchemaInit] Tabla ONBOARDING_INTENCIONES creada exitosamente.");
            }

            // Asegurar columnas de tracking de correo y vinculacion
            String[] cols = {
                "ALTER TABLE ONBOARDING_INTENCIONES ADD (CORREO_ESTADO VARCHAR2(30 CHAR) DEFAULT 'SIMULADO')",
                "ALTER TABLE ONBOARDING_INTENCIONES ADD (CORREO_DETALLE VARCHAR2(500 CHAR))",
                "ALTER TABLE ONBOARDING_INTENCIONES ADD (CORREO_FECHA TIMESTAMP)",
                "ALTER TABLE ONBOARDING_INTENCIONES ADD (ID_ORGANIZACION NUMBER)",
                "ALTER TABLE ONBOARDING_INTENCIONES ADD (ID_USUARIO NUMBER)"
            };
            for (String colDdl : cols) {
                try {
                    jdbcTemplate.execute(colDdl);
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            log.debug("[SchemaInit] Aviso al verificar ONBOARDING_INTENCIONES: {}", e.getMessage());
        }
    }

    private void initSuperAdminUser() {
        try {
            jdbcTemplate.execute("""
                BEGIN
                    BEGIN EXECUTE IMMEDIATE 'ALTER SESSION DISABLE PARALLEL DML'; EXCEPTION WHEN OTHERS THEN NULL; END;
                    BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); EXCEPTION WHEN OTHERS THEN NULL; END;

                    MERGE INTO PERSONAS p
                    USING (SELECT 1 AS ID_PERSONA, 1 AS ID_TIPO_DOCUMENTO, 'DOC000' AS NUMERO_DOCUMENTO, 'NATURAL' AS TIPO_PERSONA, 'Admin' AS PRIMER_NOMBRE, 'Global' AS PRIMER_APELLIDO, 'admin_global@saed.com' AS EMAIL, 'ACTIVO' AS ESTADO FROM DUAL) src
                    ON (p.ID_PERSONA = src.ID_PERSONA)
                    WHEN NOT MATCHED THEN
                        INSERT (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TIPO_PERSONA, PRIMER_NOMBRE, PRIMER_APELLIDO, EMAIL, ESTADO)
                        VALUES (src.ID_PERSONA, src.ID_TIPO_DOCUMENTO, src.NUMERO_DOCUMENTO, src.TIPO_PERSONA, src.PRIMER_NOMBRE, src.PRIMER_APELLIDO, src.EMAIL, src.ESTADO);
                    COMMIT;

                    MERGE INTO USUARIOS u
                    USING (SELECT 1 AS ID_USUARIO, 1 AS ID_PERSONA, 'admin_global' AS NOMBRE_USUARIO, 'admin_global@saed.com' AS EMAIL, '$2a$10$3jMwzV4asc7476tUcNa2GeahAAIhOz0.R9Clt8FCC5Kq5al1TGdxC' AS HASH_PASSWORD, 'ACTIVO' AS ESTADO, 0 AS INTENTOS_FALLIDOS FROM DUAL) src
                    ON (u.ID_USUARIO = src.ID_USUARIO)
                    WHEN NOT MATCHED THEN
                        INSERT (ID_USUARIO, ID_PERSONA, NOMBRE_USUARIO, EMAIL, HASH_PASSWORD, ESTADO, INTENTOS_FALLIDOS)
                        VALUES (src.ID_USUARIO, src.ID_PERSONA, src.NOMBRE_USUARIO, src.EMAIL, src.HASH_PASSWORD, src.ESTADO, src.INTENTOS_FALLIDOS)
                    WHEN MATCHED THEN
                        UPDATE SET NOMBRE_USUARIO = src.NOMBRE_USUARIO, HASH_PASSWORD = src.HASH_PASSWORD, ESTADO = 'ACTIVO', INTENTOS_FALLIDOS = 0;
                    COMMIT;

                    MERGE INTO ADMINISTRADORES_SAED a
                    USING (SELECT 1 AS ID_ADMINISTRADOR_SAED, 1 AS ID_USUARIO, 'SUPERADMIN' AS NIVEL, 'ACTIVO' AS ESTADO FROM DUAL) src
                    ON (a.ID_ADMINISTRADOR_SAED = src.ID_ADMINISTRADOR_SAED)
                    WHEN NOT MATCHED THEN
                        INSERT (ID_ADMINISTRADOR_SAED, ID_USUARIO, NIVEL, ESTADO)
                        VALUES (src.ID_ADMINISTRADOR_SAED, src.ID_USUARIO, src.NIVEL, src.ESTADO)
                    WHEN MATCHED THEN
                        UPDATE SET ID_USUARIO = src.ID_USUARIO, NIVEL = src.NIVEL, ESTADO = 'ACTIVO';
                    COMMIT;

                    MERGE INTO USUARIO_ASIGNACIONES ua
                    USING (SELECT 1 AS ID_ASIGNACION, 1 AS ID_USUARIO, 1 AS ID_ROL, CAST(NULL AS NUMBER) AS ID_ORGANIZACION, CAST(NULL AS NUMBER) AS ID_PROPIEDAD, CAST(NULL AS NUMBER) AS ID_UNIDAD, 'ACTIVA' AS ESTADO FROM DUAL) src
                    ON (ua.ID_ASIGNACION = src.ID_ASIGNACION)
                    WHEN NOT MATCHED THEN
                        INSERT (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ID_UNIDAD, ESTADO)
                        VALUES (src.ID_ASIGNACION, src.ID_USUARIO, src.ID_ROL, src.ID_ORGANIZACION, src.ID_PROPIEDAD, src.ID_UNIDAD, src.ESTADO)
                    WHEN MATCHED THEN
                        UPDATE SET ID_USUARIO = src.ID_USUARIO, ID_ROL = src.ID_ROL, ESTADO = 'ACTIVA';
                    COMMIT;
                END;
            """);
            log.info("[SchemaInit] SuperAdmin 'admin_global' asegurado y normalizado con éxito.");
        } catch (Exception e) {
            log.warn("[SchemaInit] Aviso al asegurar superadmin: {}", e.getMessage());
        }
    }

    private void cleanupLegacyTestData() {
        try {
            jdbcTemplate.execute("""
                BEGIN
                    BEGIN EXECUTE IMMEDIATE 'ALTER SESSION DISABLE PARALLEL DML'; EXCEPTION WHEN OTHERS THEN NULL; END;
                    BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); EXCEPTION WHEN OTHERS THEN NULL; END;

                    -- 1. Liberar inmediatamente el correo juanrinconfarelo@gmail.com de cualquier cuenta vieja de prueba
                    BEGIN
                        UPDATE USUARIOS 
                        SET EMAIL = 'carlos.mendez.freed.' || ID_USUARIO || '@saed.com' 
                        WHERE LOWER(EMAIL) = 'juanrinconfarelo@gmail.com' AND ID_USUARIO != 163;
                        
                        UPDATE PERSONAS 
                        SET EMAIL = 'carlos.mendez.freed.' || ID_PERSONA || '@saed.com' 
                        WHERE LOWER(EMAIL) = 'juanrinconfarelo@gmail.com' 
                          AND ID_PERSONA NOT IN (SELECT ID_PERSONA FROM USUARIOS WHERE ID_USUARIO = 163);
                        COMMIT;
                    EXCEPTION WHEN OTHERS THEN NULL;
                    END;

                    -- 2. Eliminar intenciones de onboarding de prueba (mantener las reales como rincon farelo / 1790301596749)
                    BEGIN
                        DELETE FROM ONBOARDING_INTENCIONES WHERE REFERENCIA NOT LIKE '%1790301596749%';
                        COMMIT;
                    EXCEPTION WHEN OTHERS THEN NULL;
                    END;

                    -- 3. Eliminar asignaciones de organizaciones de prueba y usuarios de prueba
                    BEGIN
                        DELETE FROM USUARIO_ASIGNACIONES WHERE ID_ORGANIZACION IS NOT NULL AND ID_ORGANIZACION NOT IN (1, 201);
                        DELETE FROM USUARIO_ASIGNACIONES WHERE ID_USUARIO NOT IN (1, 2, 3, 4, 5, 6, 7, 8, 163);
                        COMMIT;
                    EXCEPTION WHEN OTHERS THEN NULL;
                    END;

                    -- 4. Eliminar membresías y transacciones de organizaciones de prueba
                    BEGIN
                        DELETE FROM MEMBRESIAS WHERE ID_ORGANIZACION IS NOT NULL AND ID_ORGANIZACION NOT IN (1, 201);
                        DELETE FROM TRANSACCIONES_PAGO WHERE ID_ORGANIZACION IS NOT NULL AND ID_ORGANIZACION NOT IN (1, 201);
                        DELETE FROM DOMICILIOS WHERE ID_ORGANIZACION IS NOT NULL AND ID_ORGANIZACION NOT IN (1, 201);
                        DELETE FROM REGLAMENTOS_NORMATIVA WHERE ID_ORGANIZACION IS NOT NULL AND ID_ORGANIZACION NOT IN (1, 201);
                        DELETE FROM ASAMBLEAS WHERE ID_ORGANIZACION IS NOT NULL AND ID_ORGANIZACION NOT IN (1, 201);
                        COMMIT;
                    EXCEPTION WHEN OTHERS THEN NULL;
                    END;

                    -- 5. Eliminar tokens y usuarios de prueba (excepto los canónicos 1..8 y 163)
                    BEGIN
                        DELETE FROM TOKENS_ACTIVACION_USUARIOS WHERE ID_USUARIO NOT IN (1, 2, 3, 4, 5, 6, 7, 8, 163);
                        DELETE FROM PORTERIA_TURNOS WHERE ID_USUARIO NOT IN (1, 2, 3, 4, 5, 6, 7, 8, 163);
                        DELETE FROM USUARIOS WHERE ID_USUARIO NOT IN (1, 2, 3, 4, 5, 6, 7, 8, 163);
                        COMMIT;
                    EXCEPTION WHEN OTHERS THEN NULL;
                    END;

                    -- 6. Eliminar o inactivar organizaciones de prueba
                    BEGIN
                        DELETE FROM ORGANIZACIONES WHERE ID_ORGANIZACION NOT IN (1, 201);
                        COMMIT;
                    EXCEPTION WHEN OTHERS THEN
                        UPDATE ORGANIZACIONES SET ESTADO = 'INACTIVA' WHERE ID_ORGANIZACION NOT IN (1, 201);
                        COMMIT;
                    END;

                    -- 7. Limpiar personas huérfanas de usuarios eliminados
                    BEGIN
                        DELETE FROM PERSONAS WHERE ID_PERSONA NOT IN (SELECT ID_PERSONA FROM USUARIOS WHERE ID_PERSONA IS NOT NULL)
                                              AND ID_PERSONA NOT IN (1, 2, 3, 4, 5, 6, 7, 8);
                        COMMIT;
                    EXCEPTION WHEN OTHERS THEN NULL;
                    END;
                END;
            """);
            log.info("[SchemaInit] Limpieza de datos de prueba completada exitosamente.");
        } catch (Exception e) {
            log.warn("[SchemaInit] Aviso durante la limpieza de datos de prueba: {}", e.getMessage());
        }
    }

    private void initRoles() {
        try {
            try {
                jdbcTemplate.execute("ALTER TABLE ROLES DROP CONSTRAINT CK_ROLES_CODIGO");
            } catch (Exception ignored) {}
            try {
                jdbcTemplate.execute("ALTER TABLE ROLES ADD CONSTRAINT CK_ROLES_CODIGO CHECK (codigo IN ('SUPERADMIN', 'ADMIN_ORGANIZACION', 'PROPIETARIO', 'ADMIN_GENERAL', 'ADMIN_PROPIEDAD', 'PORTERO', 'VIGILANTE', 'RESIDENTE', 'RESIDENTE_CONVIVENCIA', 'PROPIETARIO_UNIDAD'))");
            } catch (Exception ignored) {}

            // Ejecutar contexto y MERGE en el mismo bloque atómico para evitar ORA-28115 por RLS
            jdbcTemplate.execute("""
                BEGIN
                    BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); EXCEPTION WHEN OTHERS THEN NULL; END;
                    BEGIN PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); EXCEPTION WHEN OTHERS THEN NULL; END;

                    MERGE INTO ROLES r USING (
                        SELECT 'SUPERADMIN' AS CODIGO, 'Super Administrador' AS NOMBRE, 'GLOBAL' AS ALCANCE, 'ACTIVO' AS ESTADO FROM DUAL
                    ) s ON (r.CODIGO = s.CODIGO)
                    WHEN NOT MATCHED THEN INSERT (CODIGO, NOMBRE, ALCANCE, ESTADO) VALUES (s.CODIGO, s.NOMBRE, s.ALCANCE, s.ESTADO);

                    MERGE INTO ROLES r USING (
                        SELECT 'ADMIN_ORGANIZACION' AS CODIGO, 'Admin Organizacion' AS NOMBRE, 'ORGANIZACION' AS ALCANCE, 'ACTIVO' AS ESTADO FROM DUAL
                    ) s ON (r.CODIGO = s.CODIGO)
                    WHEN NOT MATCHED THEN INSERT (CODIGO, NOMBRE, ALCANCE, ESTADO) VALUES (s.CODIGO, s.NOMBRE, s.ALCANCE, s.ESTADO);

                    MERGE INTO ROLES r USING (
                        SELECT 'ADMIN_PROPIEDAD' AS CODIGO, 'Admin Propiedad' AS NOMBRE, 'PROPIEDAD' AS ALCANCE, 'ACTIVO' AS ESTADO FROM DUAL
                    ) s ON (r.CODIGO = s.CODIGO)
                    WHEN NOT MATCHED THEN INSERT (CODIGO, NOMBRE, ALCANCE, ESTADO) VALUES (s.CODIGO, s.NOMBRE, s.ALCANCE, s.ESTADO);

                    MERGE INTO ROLES r USING (
                        SELECT 'PORTERO' AS CODIGO, 'Portero / Vigilante' AS NOMBRE, 'PROPIEDAD' AS ALCANCE, 'ACTIVO' AS ESTADO FROM DUAL
                    ) s ON (r.CODIGO = s.CODIGO)
                    WHEN NOT MATCHED THEN INSERT (CODIGO, NOMBRE, ALCANCE, ESTADO) VALUES (s.CODIGO, s.NOMBRE, s.ALCANCE, s.ESTADO);

                    MERGE INTO ROLES r USING (
                        SELECT 'RESIDENTE' AS CODIGO, 'Residente' AS NOMBRE, 'UNIDAD' AS ALCANCE, 'ACTIVO' AS ESTADO FROM DUAL
                    ) s ON (r.CODIGO = s.CODIGO)
                    WHEN NOT MATCHED THEN INSERT (CODIGO, NOMBRE, ALCANCE, ESTADO) VALUES (s.CODIGO, s.NOMBRE, s.ALCANCE, s.ESTADO);

                    MERGE INTO ROLES r USING (
                        SELECT 'RESIDENTE_CONVIVENCIA' AS CODIGO,
                               'Residente Conviviente' AS NOMBRE,
                               'UNIDAD' AS ALCANCE,
                               'ACTIVO' AS ESTADO
                        FROM DUAL
                    ) s ON (r.CODIGO = s.CODIGO)
                    WHEN NOT MATCHED THEN
                        INSERT (CODIGO, NOMBRE, ALCANCE, ESTADO)
                        VALUES (s.CODIGO, s.NOMBRE, s.ALCANCE, s.ESTADO);

                    MERGE INTO ROLES r USING (
                        SELECT 'PROPIETARIO' AS CODIGO,
                               'Propietario No Residente' AS NOMBRE,
                               'UNIDAD' AS ALCANCE,
                               'ACTIVO' AS ESTADO
                        FROM DUAL
                    ) s ON (r.CODIGO = s.CODIGO)
                    WHEN NOT MATCHED THEN
                        INSERT (CODIGO, NOMBRE, ALCANCE, ESTADO)
                        VALUES (s.CODIGO, s.NOMBRE, s.ALCANCE, s.ESTADO);

                    UPDATE USUARIO_ASIGNACIONES ua
                    SET ua.ID_ROL = (SELECT ID_ROL FROM ROLES WHERE CODIGO = 'RESIDENTE_CONVIVENCIA' AND ESTADO = 'ACTIVO')
                    WHERE ua.ID_USUARIO IN (
                        SELECT u.ID_USUARIO FROM USUARIOS u
                        JOIN RESIDENTES_UNIDAD ru ON ru.ID_PERSONA = u.ID_PERSONA
                        WHERE ru.TIPO_RESIDENTE = 'CONVIVIENTE'
                          AND ru.ESTADO = 'ACTIVO'
                          AND ru.ID_UNIDAD = ua.ID_UNIDAD
                    )
                    AND ua.ID_ROL = (SELECT ID_ROL FROM ROLES WHERE CODIGO = 'RESIDENTE' AND ESTADO = 'ACTIVO')
                    AND ua.ESTADO IN ('ACTIVO', 'ACTIVA');
                END;
            """);

            log.info("[SchemaInit] Roles canónicos verificados exitosamente.");
        } catch (Exception e) {
            log.warn("[SchemaInit] Aviso al verificar roles canónicos: {}", e.getMessage());
        }
    }

    private void initMembresiaOrg1() {
        try {
            runElevated("""
                MERGE INTO MEMBRESIAS m USING (
                    SELECT 1 AS ID_ORGANIZACION, 3 AS ID_PLAN, TRUNC(SYSDATE) AS FECHA_INICIO,
                           ADD_MONTHS(TRUNC(SYSDATE), 120) AS FECHA_FIN, 'ACTIVA' AS ESTADO, 'N' AS ES_PRUEBA
                    FROM DUAL
                ) s ON (m.ID_ORGANIZACION = s.ID_ORGANIZACION AND m.ESTADO IN ('ACTIVA', 'PRUEBA'))
                WHEN MATCHED THEN
                    UPDATE SET m.ID_PLAN = s.ID_PLAN
                WHEN NOT MATCHED THEN
                    INSERT (ID_ORGANIZACION, ID_PLAN, FECHA_INICIO, FECHA_FIN, ESTADO, ES_PRUEBA)
                    VALUES (s.ID_ORGANIZACION, s.ID_PLAN, s.FECHA_INICIO, s.FECHA_FIN, s.ESTADO, s.ES_PRUEBA)
            """);
            log.info("[SchemaInit] Membresía activa para organización 1 verificada exitosamente.");
        } catch (Exception e) {
            log.warn("[SchemaInit] Aviso al verificar membresía organización 1: {}", e.getMessage());
        }
    }

    private void initPaquetesIntentosPin() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM USER_TAB_COLS WHERE TABLE_NAME = 'PAQUETES' AND COLUMN_NAME = 'INTENTOS_FALLIDOS_PIN'",
                Integer.class
            );
            if (count == null || count == 0) {
                log.info("[SchemaInit] Agregando columna INTENTOS_FALLIDOS_PIN a tabla PAQUETES...");
                jdbcTemplate.execute("ALTER TABLE PAQUETES ADD (INTENTOS_FALLIDOS_PIN NUMBER)");
                jdbcTemplate.execute("ALTER TABLE PAQUETES MODIFY (INTENTOS_FALLIDOS_PIN DEFAULT 0)");
            }
        } catch (Exception e) {
            log.debug("[SchemaInit] Aviso al verificar INTENTOS_FALLIDOS_PIN en PAQUETES: {}", e.getMessage());
        }
    }

    private void initPaquetesFotoClob() {
        try {
            java.util.List<java.util.Map<String, Object>> cols = jdbcTemplate.queryForList(
                "SELECT COLUMN_NAME, DATA_TYPE FROM USER_TAB_COLS WHERE TABLE_NAME = 'PAQUETES' AND COLUMN_NAME IN ('FOTO_PAQUETE_URL', 'FOTO_COMPROBANTE_URL')"
            );
            for (java.util.Map<String, Object> col : cols) {
                String name = (String) col.get("COLUMN_NAME");
                String type = (String) col.get("DATA_TYPE");
                if (!"CLOB".equalsIgnoreCase(type)) {
                    log.info("[SchemaInit] Migrando columna {} de PAQUETES a CLOB...", name);
                    String tempCol = name + "_CLOB";
                    jdbcTemplate.execute("ALTER TABLE PAQUETES ADD (" + tempCol + " CLOB)");
                    jdbcTemplate.execute("UPDATE PAQUETES SET " + tempCol + " = " + name);
                    jdbcTemplate.execute("ALTER TABLE PAQUETES DROP COLUMN " + name);
                    jdbcTemplate.execute("ALTER TABLE PAQUETES RENAME COLUMN " + tempCol + " TO " + name);
                    log.info("[SchemaInit] Columna {} migrada exitosamente a CLOB.", name);
                }
            }
        } catch (Exception e) {
            log.warn("[SchemaInit] Aviso al verificar/migrar columnas CLOB de PAQUETES: {}", e.getMessage());
        }
    }

    private void initModulosYPlanModulos() {
        try {
            // 1. Catálogo canónico de Módulos
            runElevated("""
                MERGE INTO MODULOS m USING (
                    SELECT 'ASAMBLEAS' AS CODIGO, 'Asambleas y Votaciones Ley 675' AS NOMBRE, 'Gestión de asambleas, votaciones en tiempo real y quórum con coeficientes.' AS DESCRIPCION FROM DUAL UNION ALL
                    SELECT 'OBRAS', 'Gestión de Obras y Reformas', 'Seguimiento, aprobación y control de obras y reformas en unidades privadas.' FROM DUAL UNION ALL
                    SELECT 'POLIZAS', 'Pólizas de Seguro', 'Control de coberturas, vencimientos y pólizas de seguro de copropiedad.' FROM DUAL UNION ALL
                    SELECT 'RESERVAS', 'Reservas de Zonas Comunes', 'Gestión, disponibilidad y reservas de zonas comunes y amenidades.' FROM DUAL UNION ALL
                    SELECT 'PAQUETES', 'Paquetería y Correspondencia', 'Custodia de paquetes con PIN de seguridad de 6 dígitos.' FROM DUAL UNION ALL
                    SELECT 'PARQUEADEROS', 'Control de Parqueaderos', 'Control de bahías de visitantes y asignación vehicular.' FROM DUAL UNION ALL
                    SELECT 'PQRS', 'PQRS y Convivencia', 'Radicación y seguimiento de peticiones, quejas, reclamos y solicitudes.' FROM DUAL UNION ALL
                    SELECT 'FINANZAS', 'Finanzas y Pagos', 'Emisión de cuotas, recaudos, conciliación y pasarela de pago.' FROM DUAL UNION ALL
                    SELECT 'INCIDENTES', 'Libro de Incidentes y Novedades', 'Gestión, investigación y escalamiento de novedades y seguridad en la copropiedad.' FROM DUAL
                ) s ON (m.CODIGO = s.CODIGO)
                WHEN MATCHED THEN
                    UPDATE SET m.NOMBRE = s.NOMBRE, m.DESCRIPCION = s.DESCRIPCION
                WHEN NOT MATCHED THEN
                    INSERT (CODIGO, NOMBRE, DESCRIPCION)
                    VALUES (s.CODIGO, s.NOMBRE, s.DESCRIPCION)
            """);

            // 2. Matriz de Entitlements canónica por Plan
            runElevated("""
                MERGE INTO PLAN_MODULOS pm USING (
                    -- FREE (1)
                    SELECT 1 AS ID_PLAN, m.ID_MODULO, 'N' AS HABILITADO FROM MODULOS m WHERE m.CODIGO IN ('ASAMBLEAS', 'OBRAS', 'POLIZAS', 'RESERVAS', 'PAQUETES', 'PARQUEADEROS', 'PQRS', 'FINANZAS', 'INCIDENTES')
                    UNION ALL
                    -- PRO (2)
                    SELECT 2 AS ID_PLAN, m.ID_MODULO, CASE WHEN m.CODIGO = 'ASAMBLEAS' THEN 'N' ELSE 'S' END AS HABILITADO FROM MODULOS m WHERE m.CODIGO IN ('ASAMBLEAS', 'OBRAS', 'POLIZAS', 'RESERVAS', 'PAQUETES', 'PARQUEADEROS', 'PQRS', 'FINANZAS', 'INCIDENTES')
                    UNION ALL
                    -- ENTERPRISE (3)
                    SELECT 3 AS ID_PLAN, m.ID_MODULO, 'S' AS HABILITADO FROM MODULOS m WHERE m.CODIGO IN ('ASAMBLEAS', 'OBRAS', 'POLIZAS', 'RESERVAS', 'PAQUETES', 'PARQUEADEROS', 'PQRS', 'FINANZAS', 'INCIDENTES')
                ) s ON (pm.ID_PLAN = s.ID_PLAN AND pm.ID_MODULO = s.ID_MODULO)
                WHEN MATCHED THEN
                    UPDATE SET pm.HABILITADO = s.HABILITADO
                WHEN NOT MATCHED THEN
                    INSERT (ID_PLAN, ID_MODULO, HABILITADO)
                    VALUES (s.ID_PLAN, s.ID_MODULO, s.HABILITADO)
            """);
            log.info("[SchemaInit] Catálogo canónico de MODULOS y PLAN_MODULOS inicializado exitosamente.");
        } catch (Exception e) {
            log.warn("[SchemaInit] Aviso al inicializar MODULOS y PLAN_MODULOS: {}", e.getMessage());
        }
    }

    private void initContratosPipelineColumns() {
        try {
            Integer tableExists = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM USER_TABLES WHERE TABLE_NAME = 'CONTRATOS'",
                Integer.class
            );
            if (tableExists == null || tableExists == 0) {
                return;
            }

            // Check and add DOCUMENTO_HASH
            Integer hasHash = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM USER_TAB_COLS WHERE TABLE_NAME = 'CONTRATOS' AND COLUMN_NAME = 'DOCUMENTO_HASH'",
                Integer.class
            );
            if (hasHash == null || hasHash == 0) {
                jdbcTemplate.execute("ALTER TABLE CONTRATOS ADD (DOCUMENTO_HASH VARCHAR2(64 CHAR))");
                log.info("[SchemaInit] Columna DOCUMENTO_HASH agregada a CONTRATOS.");
            }

            // Check and add DOCUMENTO_TAMANO_BYTES
            Integer hasSize = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM USER_TAB_COLS WHERE TABLE_NAME = 'CONTRATOS' AND COLUMN_NAME = 'DOCUMENTO_TAMANO_BYTES'",
                Integer.class
            );
            if (hasSize == null || hasSize == 0) {
                jdbcTemplate.execute("ALTER TABLE CONTRATOS ADD (DOCUMENTO_TAMANO_BYTES NUMBER)");
                log.info("[SchemaInit] Columna DOCUMENTO_TAMANO_BYTES agregada a CONTRATOS.");
            }

            // Check and add DOCUMENTO_FECHA_GENERACION
            Integer hasFecha = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM USER_TAB_COLS WHERE TABLE_NAME = 'CONTRATOS' AND COLUMN_NAME = 'DOCUMENTO_FECHA_GENERACION'",
                Integer.class
            );
            if (hasFecha == null || hasFecha == 0) {
                jdbcTemplate.execute("ALTER TABLE CONTRATOS ADD (DOCUMENTO_FECHA_GENERACION TIMESTAMP(6) WITH TIME ZONE)");
                log.info("[SchemaInit] Columna DOCUMENTO_FECHA_GENERACION agregada a CONTRATOS.");
            }

            // Check and add HTML_CONGELADO
            Integer hasHtml = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM USER_TAB_COLS WHERE TABLE_NAME = 'CONTRATOS' AND COLUMN_NAME = 'HTML_CONGELADO'",
                Integer.class
            );
            if (hasHtml == null || hasHtml == 0) {
                jdbcTemplate.execute("ALTER TABLE CONTRATOS ADD (HTML_CONGELADO CLOB)");
                log.info("[SchemaInit] Columna HTML_CONGELADO agregada a CONTRATOS.");
            }
        } catch (Exception e) {
            log.warn("[SchemaInit] Aviso al verificar columnas de pipeline documental en CONTRATOS: {}", e.getMessage());
        }
    }

    private void initTransaccionesPagoSaaSSegregation() {
        try {
            Integer tableExists = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM USER_TABLES WHERE TABLE_NAME = 'TRANSACCIONES_PAGO'",
                Integer.class
            );
            if (tableExists == null || tableExists == 0) {
                return;
            }

            // 1. Modificar ID_UNIDAD para permitir NULL (GAP-F6-03: Segregación SaaS)
            String nullable = jdbcTemplate.queryForObject(
                "SELECT NULLABLE FROM USER_TAB_COLS WHERE TABLE_NAME = 'TRANSACCIONES_PAGO' AND COLUMN_NAME = 'ID_UNIDAD'",
                String.class
            );
            if ("N".equalsIgnoreCase(nullable)) {
                jdbcTemplate.execute("ALTER TABLE TRANSACCIONES_PAGO MODIFY (ID_UNIDAD NULL)");
                log.info("[SchemaInit] Restricción NOT NULL removida de TRANSACCIONES_PAGO.ID_UNIDAD para permitir pagos de plataforma SaaS.");
            }

            // 2. Asegurar existencia de columna ID_ORGANIZACION
            Integer hasOrgCol = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM USER_TAB_COLS WHERE TABLE_NAME = 'TRANSACCIONES_PAGO' AND COLUMN_NAME = 'ID_ORGANIZACION'",
                Integer.class
            );
            if (hasOrgCol == null || hasOrgCol == 0) {
                jdbcTemplate.execute("ALTER TABLE TRANSACCIONES_PAGO ADD (ID_ORGANIZACION NUMBER)");
                try {
                    jdbcTemplate.execute("ALTER TABLE TRANSACCIONES_PAGO ADD CONSTRAINT FK_TRANSPAGO_ORG FOREIGN KEY (ID_ORGANIZACION) REFERENCES ORGANIZACIONES(ID_ORGANIZACION) ON DELETE CASCADE");
                } catch (Exception ignored) {}
                try {
                    jdbcTemplate.execute("CREATE INDEX IX_TRANSPAGO_ORG ON TRANSACCIONES_PAGO (ID_ORGANIZACION)");
                } catch (Exception ignored) {}
                log.info("[SchemaInit] Columna ID_ORGANIZACION agregada a TRANSACCIONES_PAGO para trazabilidad directa multi-tenant de transacciones SaaS.");
            }

            // 3. Auditoría de datos históricos (GAP-F6-03 Requirement #28)
            try {
                Integer historicalCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM TRANSACCIONES_PAGO " +
                    "WHERE (METODO_ORIGEN IN ('MEMBRESIA', 'RENOVACION', 'UPGRADE', 'ONBOARDING') " +
                    "   OR REFERENCIA_INTERNA LIKE 'SAED-RENOVACION-%' " +
                    "   OR REFERENCIA_INTERNA LIKE 'SAED-UPGRADE-%' " +
                    "   OR REFERENCIA_INTERNA LIKE 'SAED-MEMBRESIA-%') " +
                    "  AND ID_UNIDAD = 1",
                    Integer.class
                );
                if (historicalCount != null && historicalCount > 0) {
                    log.info("[SchemaInit] [GAP-F6-03] Se identificaron {} registros históricos de SaaS en TRANSACCIONES_PAGO con ID_UNIDAD sintético = 1.", historicalCount);
                }
            } catch (Exception ignored) {}
        } catch (Exception e) {
            log.warn("[SchemaInit] Aviso al verificar segregación SaaS en TRANSACCIONES_PAGO: {}", e.getMessage());
        }
    }

    private void initPazYSalvosPipelineColumns() {
        try {
            Integer tableExists = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM USER_TABLES WHERE TABLE_NAME = 'PAZ_Y_SALVOS'",
                Integer.class
            );
            if (tableExists == null || tableExists == 0) {
                return;
            }

            // 1. DOCUMENTO_HASH (SHA-256)
            Integer hasHash = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM USER_TAB_COLS WHERE TABLE_NAME = 'PAZ_Y_SALVOS' AND COLUMN_NAME = 'DOCUMENTO_HASH'",
                Integer.class
            );
            if (hasHash == null || hasHash == 0) {
                jdbcTemplate.execute("ALTER TABLE PAZ_Y_SALVOS ADD (DOCUMENTO_HASH VARCHAR2(64 CHAR))");
                log.info("[SchemaInit] Columna DOCUMENTO_HASH agregada a PAZ_Y_SALVOS.");
            }

            // 2. DOCUMENTO_TAMANO_BYTES
            Integer hasSize = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM USER_TAB_COLS WHERE TABLE_NAME = 'PAZ_Y_SALVOS' AND COLUMN_NAME = 'DOCUMENTO_TAMANO_BYTES'",
                Integer.class
            );
            if (hasSize == null || hasSize == 0) {
                jdbcTemplate.execute("ALTER TABLE PAZ_Y_SALVOS ADD (DOCUMENTO_TAMANO_BYTES NUMBER)");
                log.info("[SchemaInit] Columna DOCUMENTO_TAMANO_BYTES agregada a PAZ_Y_SALVOS.");
            }

            // 3. DOCUMENTO_FECHA_GENERACION
            Integer hasDate = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM USER_TAB_COLS WHERE TABLE_NAME = 'PAZ_Y_SALVOS' AND COLUMN_NAME = 'DOCUMENTO_FECHA_GENERACION'",
                Integer.class
            );
            if (hasDate == null || hasDate == 0) {
                jdbcTemplate.execute("ALTER TABLE PAZ_Y_SALVOS ADD (DOCUMENTO_FECHA_GENERACION TIMESTAMP(6) WITH TIME ZONE)");
                log.info("[SchemaInit] Columna DOCUMENTO_FECHA_GENERACION agregada a PAZ_Y_SALVOS.");
            }
        } catch (Exception e) {
            log.warn("[SchemaInit] Aviso al verificar columnas de pipeline documental en PAZ_Y_SALVOS: {}", e.getMessage());
        }
    }

    private void initManualPaymentApprovalLifecycle() {
        try {
            Integer tableExists = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM USER_TABLES WHERE TABLE_NAME = 'PAGOS'",
                Integer.class
            );
            if (tableExists == null || tableExists == 0) {
                return;
            }

            // 1. RECHAZADO_POR
            Integer hasRechazadoPor = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM USER_TAB_COLS WHERE TABLE_NAME = 'PAGOS' AND COLUMN_NAME = 'RECHAZADO_POR'",
                Integer.class
            );
            if (hasRechazadoPor == null || hasRechazadoPor == 0) {
                jdbcTemplate.execute("ALTER TABLE PAGOS ADD (RECHAZADO_POR NUMBER)");
                try {
                    jdbcTemplate.execute("ALTER TABLE PAGOS ADD CONSTRAINT FK_PAGOS_RECHAZADOR FOREIGN KEY (RECHAZADO_POR) REFERENCES USUARIOS(ID_USUARIO)");
                } catch (Exception ignored) {}
                log.info("[SchemaInit] Columna RECHAZADO_POR agregada a PAGOS.");
            }

            // 2. FECHA_RECHAZO
            Integer hasFechaRechazo = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM USER_TAB_COLS WHERE TABLE_NAME = 'PAGOS' AND COLUMN_NAME = 'FECHA_RECHAZO'",
                Integer.class
            );
            if (hasFechaRechazo == null || hasFechaRechazo == 0) {
                jdbcTemplate.execute("ALTER TABLE PAGOS ADD (FECHA_RECHAZO TIMESTAMP(6) WITH TIME ZONE)");
                log.info("[SchemaInit] Columna FECHA_RECHAZO agregada a PAGOS.");
            }

            // 3. COMPROBANTE_HASH
            Integer hasHash = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM USER_TAB_COLS WHERE TABLE_NAME = 'PAGOS' AND COLUMN_NAME = 'COMPROBANTE_HASH'",
                Integer.class
            );
            if (hasHash == null || hasHash == 0) {
                jdbcTemplate.execute("ALTER TABLE PAGOS ADD (COMPROBANTE_HASH VARCHAR2(64 CHAR))");
                log.info("[SchemaInit] Columna COMPROBANTE_HASH agregada a PAGOS.");
            }

            // 4. COMPROBANTE_TAMANO_BYTES
            Integer hasSize = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM USER_TAB_COLS WHERE TABLE_NAME = 'PAGOS' AND COLUMN_NAME = 'COMPROBANTE_TAMANO_BYTES'",
                Integer.class
            );
            if (hasSize == null || hasSize == 0) {
                jdbcTemplate.execute("ALTER TABLE PAGOS ADD (COMPROBANTE_TAMANO_BYTES NUMBER)");
                log.info("[SchemaInit] Columna COMPROBANTE_TAMANO_BYTES agregada a PAGOS.");
            }

            // 5. Actualizar TRG_APLICAR_PAGO_CUOTA para que solo aplique si PAGOS.ESTADO = 'APROBADO'
            String ddlTrigger = """
                CREATE OR REPLACE TRIGGER TRG_APLICAR_PAGO_CUOTA
                    AFTER INSERT ON PAGO_DETALLE
                    FOR EACH ROW
                DECLARE
                    v_estado VARCHAR2(25);
                BEGIN
                    SELECT ESTADO INTO v_estado FROM PAGOS WHERE ID_PAGO = :NEW.ID_PAGO;
                    IF v_estado = 'APROBADO' THEN
                        UPDATE CUOTAS
                        SET saldo_pendiente = GREATEST(0, saldo_pendiente - :NEW.monto_aplicado),
                            estado = CASE
                                WHEN (saldo_pendiente - :NEW.monto_aplicado) <= 0 THEN 'PAGADA'
                                ELSE 'PAGADA_PARCIAL'
                            END
                        WHERE id_cuota = :NEW.id_cuota;
                    END IF;
                EXCEPTION
                    WHEN NO_DATA_FOUND THEN
                        NULL;
                END;
                """;
            jdbcTemplate.execute(ddlTrigger);
            log.info("[SchemaInit] Trigger TRG_APLICAR_PAGO_CUOTA sincronizado para respetar PENDIENTE_APROBACION.");
        } catch (Exception e) {
            log.warn("[SchemaInit] Aviso al sincronizar esquema de ciclo de pagos manuales en PAGOS: {}", e.getMessage());
        }
    }

    private void initSpValidarConsumirQr() {
        try {
            log.info("[SchemaInit] Sincronizando y compilando procedimiento canonico SP_VALIDAR_CONSUMIR_QR...");
            String plsql = """
                CREATE OR REPLACE PROCEDURE SP_VALIDAR_CONSUMIR_QR (
                    p_token_qr          IN  VARCHAR2,
                    p_id_porteria       IN  NUMBER,
                    p_portero_usuario   IN  NUMBER,
                    p_valido            OUT CHAR,
                    p_mensaje           OUT VARCHAR2,
                    p_id_visita         OUT NUMBER
                ) AS
                    v_id_qr             QR_ACCESOS.id_qr%TYPE;
                    v_fecha_exp         QR_ACCESOS.fecha_expiracion%TYPE;
                    v_permitidos        QR_ACCESOS.usos_permitidos%TYPE;
                    v_consumidos        QR_ACCESOS.usos_consumidos%TYPE;
                    v_estado            QR_ACCESOS.estado%TYPE;
                    v_id_unidad         VISITAS.id_unidad%TYPE;
                    v_id_visitante      VISITAS.id_visitante%TYPE;
                    v_id_persona        VISITANTES.id_persona%TYPE;
                    v_id_propiedad      PROPIEDADES.id_propiedad%TYPE;
                    v_porteria_count    NUMBER := 0;
                    v_prev_state        VARCHAR2(30);
                    v_prev_usr          VARCHAR2(30);
                    v_prev_org          VARCHAR2(30);
                    v_prev_prop         VARCHAR2(30);
                    v_prev_rol          VARCHAR2(30);

                    PROCEDURE restore_context IS
                    BEGIN
                        IF v_prev_org IS NOT NULL AND v_prev_usr IS NOT NULL THEN
                            BEGIN
                                PKG_SAED_SESSION.SET_CONTEXT(
                                    p_id_usuario      => TO_NUMBER(v_prev_usr),
                                    p_id_organizacion => TO_NUMBER(v_prev_org),
                                    p_id_propiedad    => CASE WHEN v_prev_prop IS NOT NULL THEN TO_NUMBER(v_prev_prop) ELSE NULL END,
                                    p_rol_codigo      => v_prev_rol
                                );
                            EXCEPTION WHEN OTHERS THEN NULL;
                            END;
                        ELSIF v_prev_usr IS NOT NULL THEN
                            BEGIN
                                PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(TO_NUMBER(v_prev_usr));
                            EXCEPTION WHEN OTHERS THEN NULL;
                            END;
                        END IF;
                    END restore_context;
                BEGIN
                    p_valido := 'N';

                    v_prev_state := SYS_CONTEXT('SAED_CTX', 'STATE');
                    v_prev_usr   := SYS_CONTEXT('SAED_CTX', 'ID_USUARIO');
                    v_prev_org   := SYS_CONTEXT('SAED_CTX', 'ID_ORGANIZACION');
                    v_prev_prop  := SYS_CONTEXT('SAED_CTX', 'ID_PROPIEDAD');
                    v_prev_rol   := SYS_CONTEXT('SAED_CTX', 'ROL_CODIGO');

                    -- Elevación controlada a BOOTSTRAP para que el bloqueo pesimista y lectura
                    -- del token no sean filtrados por RLS antes de verificar la pertenencia a la portería
                    IF p_portero_usuario IS NOT NULL THEN
                        BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(p_portero_usuario); EXCEPTION WHEN OTHERS THEN NULL; END;
                    ELSE
                        BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); EXCEPTION WHEN OTHERS THEN NULL; END;
                    END IF;

                    -- Bloqueo pesimista de fila para evitar consumo concurrente en multiples puertas
                    SELECT q.id_qr, q.id_visita, q.fecha_expiracion, q.usos_permitidos, q.usos_consumidos, q.estado,
                           v.id_unidad, v.id_visitante, vis.id_persona, p.id_propiedad
                    INTO v_id_qr, p_id_visita, v_fecha_exp, v_permitidos, v_consumidos, v_estado,
                         v_id_unidad, v_id_visitante, v_id_persona, v_id_propiedad
                    FROM QR_ACCESOS q
                    JOIN VISITAS v ON v.id_visita = q.id_visita
                    JOIN VISITANTES vis ON vis.id_visitante = v.id_visitante
                    JOIN UNIDADES u ON u.id_unidad = v.id_unidad
                    JOIN PROPIEDADES p ON p.id_propiedad = u.id_propiedad
                    WHERE q.token_qr = p_token_qr
                    FOR UPDATE OF q.usos_consumidos;

                    IF v_estado != 'ACTIVO' THEN
                        restore_context;
                        p_mensaje := 'El codigo QR no se encuentra activo (Estado: ' || v_estado || ').';
                        RETURN;
                    END IF;

                    IF v_fecha_exp < FROM_TZ(CAST(SYSTIMESTAMP AS TIMESTAMP), 'America/Bogota') THEN
                        UPDATE QR_ACCESOS SET estado = 'EXPIRADO' WHERE id_qr = v_id_qr;
                        restore_context;
                        p_mensaje := 'El codigo QR ha expirado.';
                        RETURN;
                    END IF;

                    -- Validacion de aislamiento multi-tenant: la porteria debe pertenecer a la propiedad del QR
                    IF p_id_porteria IS NOT NULL THEN
                        SELECT COUNT(1) INTO v_porteria_count
                        FROM PORTERIAS
                        WHERE id_porteria = p_id_porteria AND id_propiedad = v_id_propiedad;

                        IF v_porteria_count = 0 THEN
                            restore_context;
                            p_mensaje := 'La porteria no pertenece a la propiedad del codigo QR.';
                            RETURN;
                        END IF;
                    END IF;

                    -- Validacion de limite de usos disponibles
                    IF v_consumidos >= v_permitidos THEN
                        UPDATE QR_ACCESOS SET estado = 'USADO' WHERE id_qr = v_id_qr;
                        restore_context;
                        p_mensaje := 'El codigo QR ha alcanzado el limite de usos permitidos.';
                        RETURN;
                    END IF;

                    -- Consumo atomico
                    v_consumidos := v_consumidos + 1;
                    UPDATE QR_ACCESOS
                    SET usos_consumidos = v_consumidos,
                        estado = CASE WHEN v_consumidos >= v_permitidos THEN 'USADO' ELSE 'ACTIVO' END
                    WHERE id_qr = v_id_qr;

                    -- Registro de acceso
                    INSERT INTO REGISTROS_ACCESO (
                        id_propiedad, id_porteria, id_visita, id_persona, id_unidad, id_qr,
                        tipo_movimiento, metodo_autorizacion, portero_operador
                    ) VALUES (
                        v_id_propiedad, p_id_porteria, p_id_visita, v_id_persona, v_id_unidad, v_id_qr,
                        'ENTRADA', 'QR_SCAN', p_portero_usuario
                    );

                    p_valido := 'S';
                    p_mensaje := 'Acceso autorizado exitosamente.';
                    restore_context;
                EXCEPTION
                    WHEN NO_DATA_FOUND THEN
                        restore_context;
                        p_mensaje := 'Codigo QR no encontrado o invalido.';
                    WHEN OTHERS THEN
                        restore_context;
                        p_mensaje := 'Error interno al validar QR: ' || SQLERRM;
                END SP_VALIDAR_CONSUMIR_QR;
                """;
            jdbcTemplate.execute(plsql);
            log.info("[SchemaInit] Procedimiento SP_VALIDAR_CONSUMIR_QR sincronizado y compilado exitosamente.");
        } catch (Exception e) {
            log.warn("[SchemaInit] Aviso al inicializar SP_VALIDAR_CONSUMIR_QR: {}", e.getMessage());
        }
    }

    private void initDomicilios() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM USER_TABLES WHERE TABLE_NAME = 'DOMICILIOS'",
                Integer.class
            );
            if (count == null || count == 0) {
                log.info("[SchemaInit] Creando tabla DOMICILIOS...");
                jdbcTemplate.execute("""
                    CREATE TABLE DOMICILIOS (
                        ID_DOMICILIO NUMBER GENERATED BY DEFAULT ON NULL AS IDENTITY MINVALUE 1 MAXVALUE 9999999999999999999999999999 INCREMENT BY 1 START WITH 1 CACHE 20 NOORDER NOCYCLE NOT NULL ENABLE,
                        ID_ORGANIZACION NUMBER NOT NULL ENABLE,
                        ID_PROPIEDAD NUMBER NOT NULL ENABLE,
                        ID_UNIDAD NUMBER NOT NULL ENABLE,
                        ID_PORTERIA NUMBER,
                        ID_PERSONA_DESTINATARIO NUMBER,
                        EMPRESA VARCHAR2(100 CHAR) NOT NULL ENABLE,
                        NOMBRE_DOMICILIARIO VARCHAR2(150 CHAR) NOT NULL ENABLE,
                        DOCUMENTO_DOMICILIARIO VARCHAR2(30 CHAR),
                        TELEFONO_DOMICILIARIO VARCHAR2(30 CHAR),
                        TIPO_DOMICILIO VARCHAR2(30 CHAR) DEFAULT 'COMIDA' NOT NULL ENABLE,
                        NUMERO_GUIA VARCHAR2(80 CHAR),
                        MEDIO_TRANSPORTE VARCHAR2(20 CHAR) DEFAULT 'MOTO' NOT NULL ENABLE,
                        PLACA_VEHICULO VARCHAR2(15 CHAR),
                        OBSERVACIONES VARCHAR2(400 CHAR),
                        ESTADO VARCHAR2(20 CHAR) DEFAULT 'EN_CURSO' NOT NULL ENABLE,
                        FECHA_ENTRADA TIMESTAMP(6) WITH TIME ZONE DEFAULT FROM_TZ(CAST(SYSTIMESTAMP AS TIMESTAMP), 'America/Bogota') NOT NULL ENABLE,
                        FECHA_SALIDA TIMESTAMP(6) WITH TIME ZONE,
                        REGISTRADO_POR NUMBER NOT NULL ENABLE,
                        FINALIZADO_POR NUMBER,
                        FECHA_CREACION TIMESTAMP(6) WITH TIME ZONE DEFAULT FROM_TZ(CAST(SYSTIMESTAMP AS TIMESTAMP), 'America/Bogota') NOT NULL ENABLE,
                        CONSTRAINT PK_DOMICILIOS PRIMARY KEY (ID_DOMICILIO),
                        CONSTRAINT FK_DOMICILIOS_ORG FOREIGN KEY (ID_ORGANIZACION) REFERENCES ORGANIZACIONES(ID_ORGANIZACION),
                        CONSTRAINT FK_DOMICILIOS_PROP FOREIGN KEY (ID_PROPIEDAD) REFERENCES PROPIEDADES(ID_PROPIEDAD),
                        CONSTRAINT FK_DOMICILIOS_UNIDAD FOREIGN KEY (ID_UNIDAD) REFERENCES UNIDADES(ID_UNIDAD),
                        CONSTRAINT FK_DOMICILIOS_PORTERIA FOREIGN KEY (ID_PORTERIA) REFERENCES PORTERIAS(ID_PORTERIA) ON DELETE SET NULL,
                        CONSTRAINT FK_DOMICILIOS_REGISTRADOR FOREIGN KEY (REGISTRADO_POR) REFERENCES USUARIOS(ID_USUARIO),
                        CONSTRAINT FK_DOMICILIOS_FINALIZADOR FOREIGN KEY (FINALIZADO_POR) REFERENCES USUARIOS(ID_USUARIO) ON DELETE SET NULL,
                        CONSTRAINT CK_DOMICILIOS_ESTADO CHECK (ESTADO IN ('EN_CURSO', 'FINALIZADO', 'CANCELADO')),
                        CONSTRAINT CK_DOMICILIOS_TIPO CHECK (TIPO_DOMICILIO IN ('COMIDA', 'MEDICAMENTOS', 'MENSAJERIA', 'SUPERMERCADO', 'PAQUETE_EXPRESS', 'OTRO')),
                        CONSTRAINT CK_DOMICILIOS_TRANSPORTE CHECK (MEDIO_TRANSPORTE IN ('MOTO', 'BICICLETA', 'CARRO', 'A_PIE', 'OTRO'))
                    )
                """);
                try {
                    jdbcTemplate.execute("CREATE INDEX IDX_DOMICILIOS_PROP_EST ON DOMICILIOS (ID_PROPIEDAD, ESTADO)");
                    jdbcTemplate.execute("CREATE INDEX IDX_DOMICILIOS_UNIDAD ON DOMICILIOS (ID_UNIDAD)");
                    jdbcTemplate.execute("CREATE INDEX IDX_DOMICILIOS_FECHA ON DOMICILIOS (FECHA_ENTRADA)");
                } catch (Exception ignored) {}

                try {
                    jdbcTemplate.execute("""
                        BEGIN
                            DBMS_RLS.ADD_GROUPED_POLICY(
                                object_schema   => NULL,
                                object_name     => 'DOMICILIOS',
                                policy_group    => 'SYS_DEFAULT',
                                policy_name     => 'POL_RLS_UNI_DOMICILIOS',
                                function_schema => NULL,
                                policy_function => 'PKG_SAED_SECURITY_RLS.FN_FILTRO_UNIDAD',
                                statement_types => 'SELECT,INSERT,UPDATE,DELETE',
                                update_check    => TRUE,
                                enable          => TRUE,
                                static_policy   => FALSE,
                                policy_type     => DBMS_RLS.DYNAMIC
                            );
                        EXCEPTION
                            WHEN OTHERS THEN
                                NULL;
                        END;
                    """);
                } catch (Exception e) {
                    log.debug("[SchemaInit] Aviso RLS en DOMICILIOS: {}", e.getMessage());
                }
                log.info("[SchemaInit] Tabla DOMICILIOS creada exitosamente.");
            }
        } catch (Exception e) {
            log.warn("[SchemaInit] Error verificando/creando DOMICILIOS: {}", e.getMessage());
        }
    }

    private void initMantenimientosYBloqueos() {
        try {
            Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM USER_TABLES WHERE TABLE_NAME = 'BLOQUEOS_ZONA'",
                Integer.class
            );
            if (tableCount != null && tableCount > 0) {
                // Ensure ID_PROPIEDAD column exists for RLS and multi-tenant integrity
                Integer countProp = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM USER_TAB_COLS WHERE TABLE_NAME = 'BLOQUEOS_ZONA' AND COLUMN_NAME = 'ID_PROPIEDAD'",
                    Integer.class
                );
                if (countProp == null || countProp == 0) {
                    jdbcTemplate.execute("ALTER TABLE BLOQUEOS_ZONA ADD (ID_PROPIEDAD NUMBER)");
                    try {
                        jdbcTemplate.execute("UPDATE BLOQUEOS_ZONA b SET ID_PROPIEDAD = (SELECT z.ID_PROPIEDAD FROM ZONAS_COMUNES z WHERE z.ID_ZONA = b.ID_ZONA) WHERE b.ID_PROPIEDAD IS NULL");
                    } catch (Exception ignored) {}
                    log.info("[SchemaInit] Agregada columna ID_PROPIEDAD a BLOQUEOS_ZONA.");
                }

                // Ensure ID_MANTENIMIENTO column exists for direct relation with MANTENIMIENTOS
                Integer countMant = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM USER_TAB_COLS WHERE TABLE_NAME = 'BLOQUEOS_ZONA' AND COLUMN_NAME = 'ID_MANTENIMIENTO'",
                    Integer.class
                );
                if (countMant == null || countMant == 0) {
                    jdbcTemplate.execute("ALTER TABLE BLOQUEOS_ZONA ADD (ID_MANTENIMIENTO NUMBER)");
                    log.info("[SchemaInit] Agregada columna ID_MANTENIMIENTO a BLOQUEOS_ZONA.");
                }

                try {
                    jdbcTemplate.execute("CREATE INDEX IX_BLOQUEO_MANT ON BLOQUEOS_ZONA (ID_MANTENIMIENTO)");
                } catch (Exception ignored) {}
                try {
                    jdbcTemplate.execute("CREATE INDEX IX_BLOQUEO_PROP ON BLOQUEOS_ZONA (ID_PROPIEDAD)");
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            log.warn("[SchemaInit] Aviso en initMantenimientosYBloqueos: {}", e.getMessage());
        }
    }

    private void initTrabajadoresSchemaAndRls() {
        try {
            // 1. Columnas en TRABAJADORES
            Integer colArlAfiliacion = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM USER_TAB_COLS WHERE TABLE_NAME = 'TRABAJADORES' AND COLUMN_NAME = 'ARL_FECHA_AFILIACION'",
                Integer.class
            );
            if (colArlAfiliacion == null || colArlAfiliacion == 0) {
                jdbcTemplate.execute("ALTER TABLE TRABAJADORES ADD (ARL_FECHA_AFILIACION DATE)");
                log.info("[SchemaInit] Columna ARL_FECHA_AFILIACION agregada a TRABAJADORES.");
            }

            // 2. Unicidad de persona por proveedor
            try {
                Integer uqCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM USER_CONSTRAINTS WHERE CONSTRAINT_NAME = 'UQ_TRABAJADOR_PROV_PERSONA'",
                    Integer.class
                );
                if (uqCount == null || uqCount == 0) {
                    jdbcTemplate.execute("ALTER TABLE TRABAJADORES ADD CONSTRAINT UQ_TRABAJADOR_PROV_PERSONA UNIQUE (ID_PROVEEDOR, ID_PERSONA)");
                    log.info("[SchemaInit] Restricción UQ_TRABAJADOR_PROV_PERSONA creada en TRABAJADORES.");
                }
            } catch (Exception ignored) {}

            // 3. Columnas de trazabilidad en OBRA_TRABAJADORES
            Integer colFechaAut = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM USER_TAB_COLS WHERE TABLE_NAME = 'OBRA_TRABAJADORES' AND COLUMN_NAME = 'FECHA_AUTORIZACION'",
                Integer.class
            );
            if (colFechaAut == null || colFechaAut == 0) {
                jdbcTemplate.execute("ALTER TABLE OBRA_TRABAJADORES ADD (FECHA_AUTORIZACION TIMESTAMP(6) WITH TIME ZONE)");
                try {
                    jdbcTemplate.execute("ALTER TABLE OBRA_TRABAJADORES MODIFY (FECHA_AUTORIZACION DEFAULT CURRENT_TIMESTAMP)");
                } catch (Exception ignored) {}
                log.info("[SchemaInit] Columna FECHA_AUTORIZACION agregada a OBRA_TRABAJADORES.");
            }

            Integer colFechaRev = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM USER_TAB_COLS WHERE TABLE_NAME = 'OBRA_TRABAJADORES' AND COLUMN_NAME = 'FECHA_REVOCACION'",
                Integer.class
            );
            if (colFechaRev == null || colFechaRev == 0) {
                jdbcTemplate.execute("ALTER TABLE OBRA_TRABAJADORES ADD (FECHA_REVOCACION TIMESTAMP(6) WITH TIME ZONE)");
                log.info("[SchemaInit] Columna FECHA_REVOCACION agregada a OBRA_TRABAJADORES.");
            }

            Integer colAutPor = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM USER_TAB_COLS WHERE TABLE_NAME = 'OBRA_TRABAJADORES' AND COLUMN_NAME = 'AUTORIZADO_POR'",
                Integer.class
            );
            if (colAutPor == null || colAutPor == 0) {
                jdbcTemplate.execute("ALTER TABLE OBRA_TRABAJADORES ADD (AUTORIZADO_POR NUMBER)");
                log.info("[SchemaInit] Columna AUTORIZADO_POR agregada a OBRA_TRABAJADORES.");
            }

            log.info("[SchemaInit] Esquema y RLS para TRABAJADORES y OBRA_TRABAJADORES sincronizados exitosamente.");
        } catch (Exception e) {
            log.warn("[SchemaInit] Aviso en initTrabajadoresSchemaAndRls: {}", e.getMessage());
        }
    }

    private void initPqrsSlaConfig() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM USER_TABLES WHERE TABLE_NAME = 'PQRS_SLA_CONFIGURACION'",
                Integer.class
            );
            if (count != null && count > 0) {
                runElevated("""
                    MERGE INTO PQRS_SLA_CONFIGURACION target
                    USING (
                        SELECT 1 AS ID_PROP, 'EMERGENCIA' AS PRIO, 12 AS HORAS, 4 AS ALERTA FROM DUAL UNION ALL
                        SELECT 1, 'ALTA', 24, 8 FROM DUAL UNION ALL
                        SELECT 1, 'MEDIA', 72, 24 FROM DUAL UNION ALL
                        SELECT 1, 'BAJA', 120, 48 FROM DUAL
                    ) src
                    ON (target.ID_PROPIEDAD = src.ID_PROP AND target.PRIORIDAD = src.PRIO)
                    WHEN NOT MATCHED THEN
                        INSERT (ID_PROPIEDAD, PRIORIDAD, TIEMPO_MAXIMO_HORAS, ALERTA_VENCIMIENTO_HORAS)
                        VALUES (src.ID_PROP, src.PRIO, src.HORAS, src.ALERTA)
                """);
                log.info("[SchemaInit] Configuración base de SLA para PQRS verificada exitosamente.");
            }
        } catch (Exception e) {
            log.warn("[SchemaInit] Aviso en initPqrsSlaConfig: {}", e.getMessage());
        }
    }

    private void initIncidentesPipeline() {
        try {
            // 1. Columnas de investigación y escalamiento en INCIDENTES
            String[] cols = {
                "INVESTIGADO_POR NUMBER",
                "FECHA_INICIO_INVESTIGACION TIMESTAMP(6) WITH TIME ZONE",
                "FECHA_FIN_INVESTIGACION TIMESTAMP(6) WITH TIME ZONE",
                "HALLAZGOS_INVESTIGACION VARCHAR2(1000 CHAR)",
                "ESCALADO_POR NUMBER",
                "FECHA_ESCALAMIENTO TIMESTAMP(6) WITH TIME ZONE",
                "MOTIVO_ESCALAMIENTO VARCHAR2(500 CHAR)",
                "SANCION_SUGERIDA VARCHAR2(250 CHAR)"
            };

            for (String colDef : cols) {
                String colName = colDef.split(" ")[0];
                Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM USER_TAB_COLS WHERE TABLE_NAME = 'INCIDENTES' AND COLUMN_NAME = ?",
                    Integer.class,
                    colName
                );
                if (count == null || count == 0) {
                    jdbcTemplate.execute("ALTER TABLE INCIDENTES ADD (" + colDef + ")");
                    log.info("[SchemaInit] Columna {} agregada a INCIDENTES.", colName);
                }
            }

            // 2. FKs para investigado_por y escalado_por
            try {
                Integer fkInv = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM USER_CONSTRAINTS WHERE CONSTRAINT_NAME = 'FK_INCIDENTES_INVESTIGADO'",
                    Integer.class
                );
                if (fkInv == null || fkInv == 0) {
                    jdbcTemplate.execute("ALTER TABLE INCIDENTES ADD CONSTRAINT FK_INCIDENTES_INVESTIGADO FOREIGN KEY (INVESTIGADO_POR) REFERENCES USUARIOS(ID_USUARIO)");
                }
            } catch (Exception ignored) {}

            try {
                Integer fkEsc = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM USER_CONSTRAINTS WHERE CONSTRAINT_NAME = 'FK_INCIDENTES_ESCALADO'",
                    Integer.class
                );
                if (fkEsc == null || fkEsc == 0) {
                    jdbcTemplate.execute("ALTER TABLE INCIDENTES ADD CONSTRAINT FK_INCIDENTES_ESCALADO FOREIGN KEY (ESCALADO_POR) REFERENCES USUARIOS(ID_USUARIO)");
                }
            } catch (Exception ignored) {}

            log.info("[SchemaInit] Pipeline de INCIDENTES verificado exitosamente.");
        } catch (Exception e) {
            log.warn("[SchemaInit] Aviso en initIncidentesPipeline: {}", e.getMessage());
        }
    }

    private void initReglamentosNormativaPipeline() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM USER_TABLES WHERE TABLE_NAME = 'REGLAMENTOS_NORMATIVA'",
                Integer.class
            );
            if (count == null || count == 0) {
                log.info("[SchemaInit] Creando tabla REGLAMENTOS_NORMATIVA...");
                jdbcTemplate.execute("""
                    CREATE TABLE REGLAMENTOS_NORMATIVA (
                        ID_REGLAMENTO NUMBER(19) GENERATED BY DEFAULT ON NULL AS IDENTITY PRIMARY KEY,
                        ID_ORGANIZACION NUMBER(19) NOT NULL,
                        ID_PROPIEDAD NUMBER(19) NOT NULL,
                        TIPO_NORMATIVA VARCHAR2(50 CHAR) NOT NULL,
                        TITULO VARCHAR2(255 CHAR) NOT NULL,
                        DESCRIPCION VARCHAR2(1000 CHAR),
                        ID_DOCUMENTO NUMBER(19) NOT NULL,
                        ESTADO VARCHAR2(25 CHAR) DEFAULT 'BORRADOR' NOT NULL,
                        FECHA_ENTRADA_EN_VIGOR DATE,
                        FECHA_PUBLICACION TIMESTAMP(6) WITH TIME ZONE,
                        CREADO_POR NUMBER(19),
                        PUBLICADO_POR NUMBER(19),
                        FECHA_CREACION TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
                        FECHA_MODIFICACION TIMESTAMP(6) WITH TIME ZONE,
                        CONSTRAINT CK_REGLAM_TIPO CHECK (TIPO_NORMATIVA IN ('REGLAMENTO_INTERNO', 'MANUAL_CONVIVENCIA', 'MANUAL_ZONAS_COMUNES', 'MANUAL_POLITICA_MASCOTAS', 'ESTATUTO_COPROPIEDAD', 'OTRO')),
                        CONSTRAINT CK_REGLAM_ESTADO CHECK (ESTADO IN ('BORRADOR', 'PUBLICADO', 'REEMPLAZADO', 'INACTIVO')),
                        CONSTRAINT FK_REGLAM_ORG FOREIGN KEY (ID_ORGANIZACION) REFERENCES ORGANIZACIONES(ID_ORGANIZACION),
                        CONSTRAINT FK_REGLAM_PROP FOREIGN KEY (ID_PROPIEDAD) REFERENCES PROPIEDADES(ID_PROPIEDAD),
                        CONSTRAINT FK_REGLAM_DOC FOREIGN KEY (ID_DOCUMENTO) REFERENCES DOCUMENTOS(ID_DOCUMENTO),
                        CONSTRAINT FK_REGLAM_CREADOR FOREIGN KEY (CREADO_POR) REFERENCES USUARIOS(ID_USUARIO),
                        CONSTRAINT FK_REGLAM_PUB FOREIGN KEY (PUBLICADO_POR) REFERENCES USUARIOS(ID_USUARIO)
                    )
                """);
                log.info("[SchemaInit] Tabla REGLAMENTOS_NORMATIVA creada exitosamente.");
            }

            // Conditional unique index: exactly one published regulation per property and type
            try {
                Integer uqCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM USER_INDEXES WHERE INDEX_NAME = 'UQ_REGLAM_PROP_TIPO_VIG'",
                    Integer.class
                );
                if (uqCount == null || uqCount == 0) {
                    jdbcTemplate.execute("""
                        CREATE UNIQUE INDEX UQ_REGLAM_PROP_TIPO_VIG ON REGLAMENTOS_NORMATIVA (
                            CASE WHEN ESTADO = 'PUBLICADO' THEN ID_PROPIEDAD ELSE NULL END,
                            CASE WHEN ESTADO = 'PUBLICADO' THEN TIPO_NORMATIVA ELSE NULL END
                        )
                    """);
                    log.info("[SchemaInit] Índice único condicional UQ_REGLAM_PROP_TIPO_VIG creado.");
                }
            } catch (Exception ignored) {}

            // Performance indexes
            try {
                Integer idx1 = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM USER_INDEXES WHERE INDEX_NAME = 'IX_REGLAM_PROP_EST'", Integer.class);
                if (idx1 == null || idx1 == 0) {
                    jdbcTemplate.execute("CREATE INDEX IX_REGLAM_PROP_EST ON REGLAMENTOS_NORMATIVA (ID_PROPIEDAD, ESTADO)");
                }
                Integer idx2 = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM USER_INDEXES WHERE INDEX_NAME = 'IX_REGLAM_DOC'", Integer.class);
                if (idx2 == null || idx2 == 0) {
                    jdbcTemplate.execute("CREATE INDEX IX_REGLAM_DOC ON REGLAMENTOS_NORMATIVA (ID_DOCUMENTO)");
                }
                Integer idx3 = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM USER_INDEXES WHERE INDEX_NAME = 'IX_REGLAM_ORG'", Integer.class);
                if (idx3 == null || idx3 == 0) {
                    jdbcTemplate.execute("CREATE INDEX IX_REGLAM_ORG ON REGLAMENTOS_NORMATIVA (ID_ORGANIZACION)");
                }
            } catch (Exception ignored) {}

            // RLS policy on REGLAMENTOS_NORMATIVA
            try {
                jdbcTemplate.execute("""
                    BEGIN
                        DBMS_RLS.ADD_GROUPED_POLICY(
                            object_schema   => NULL,
                            object_name     => 'REGLAMENTOS_NORMATIVA',
                            policy_group    => 'SYS_DEFAULT',
                            policy_name     => 'POL_RLS_PROP_REGLAMENTOS',
                            function_schema => NULL,
                            policy_function => 'PKG_SAED_SECURITY_RLS.FN_FILTRO_PROPIEDAD',
                            statement_types => 'SELECT,INSERT,UPDATE,DELETE',
                            update_check    => TRUE,
                            enable          => TRUE,
                            static_policy   => FALSE,
                            policy_type     => dbms_rls.DYNAMIC,
                            long_predicate  => FALSE,
                            sec_relevant_cols => '',
                            sec_relevant_cols_opt => NULL
                        );
                    EXCEPTION
                        WHEN OTHERS THEN NULL;
                    END;
                """);
                log.info("[SchemaInit] Política RLS POL_RLS_PROP_REGLAMENTOS registrada.");
            } catch (Exception ignored) {}

            log.info("[SchemaInit] Pipeline de REGLAMENTOS_NORMATIVA verificado exitosamente.");
        } catch (Exception e) {
            log.warn("[SchemaInit] Aviso en initReglamentosNormativaPipeline: {}", e.getMessage());
        }
    }

    private void initAsambleasGovernancePipeline() {
        try {
            // 1. Columnas ID_ORGANIZACION e ID_DOCUMENTO en ASAMBLEAS
            try {
                Integer orgCol = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM USER_TAB_COLS WHERE TABLE_NAME = 'ASAMBLEAS' AND COLUMN_NAME = 'ID_ORGANIZACION'",
                    Integer.class
                );
                if (orgCol == null || orgCol == 0) {
                    jdbcTemplate.execute("ALTER TABLE ASAMBLEAS ADD (ID_ORGANIZACION NUMBER(19))");
                    jdbcTemplate.execute("UPDATE ASAMBLEAS a SET a.ID_ORGANIZACION = (SELECT p.ID_ORGANIZACION FROM PROPIEDADES p WHERE p.ID_PROPIEDAD = a.ID_PROPIEDAD) WHERE a.ID_ORGANIZACION IS NULL");
                }

                Integer docCol = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM USER_TAB_COLS WHERE TABLE_NAME = 'ASAMBLEAS' AND COLUMN_NAME = 'ID_DOCUMENTO'",
                    Integer.class
                );
                if (docCol == null || docCol == 0) {
                    jdbcTemplate.execute("ALTER TABLE ASAMBLEAS ADD (ID_DOCUMENTO NUMBER(19))");
                }
            } catch (Exception ignored) {}

            // 2. Foreign keys e índices
            try {
                Integer fkOrg = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM USER_CONSTRAINTS WHERE CONSTRAINT_NAME = 'FK_ASAMBLEAS_ORG'",
                    Integer.class
                );
                if (fkOrg == null || fkOrg == 0) {
                    jdbcTemplate.execute("ALTER TABLE ASAMBLEAS ADD CONSTRAINT FK_ASAMBLEAS_ORG FOREIGN KEY (ID_ORGANIZACION) REFERENCES ORGANIZACIONES(ID_ORGANIZACION)");
                }

                Integer fkDoc = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM USER_CONSTRAINTS WHERE CONSTRAINT_NAME = 'FK_ASAMBLEAS_DOC'",
                    Integer.class
                );
                if (fkDoc == null || fkDoc == 0) {
                    jdbcTemplate.execute("ALTER TABLE ASAMBLEAS ADD CONSTRAINT FK_ASAMBLEAS_DOC FOREIGN KEY (ID_DOCUMENTO) REFERENCES DOCUMENTOS(ID_DOCUMENTO)");
                }

                Integer idxOrg = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM USER_INDEXES WHERE INDEX_NAME = 'IX_ASAMBLEAS_ORG'", Integer.class);
                if (idxOrg == null || idxOrg == 0) {
                    jdbcTemplate.execute("CREATE INDEX IX_ASAMBLEAS_ORG ON ASAMBLEAS (ID_ORGANIZACION)");
                }

                Integer idxDoc = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM USER_INDEXES WHERE INDEX_NAME = 'IX_ASAMBLEAS_DOC'", Integer.class);
                if (idxDoc == null || idxDoc == 0) {
                    jdbcTemplate.execute("CREATE INDEX IX_ASAMBLEAS_DOC ON ASAMBLEAS (ID_DOCUMENTO)");
                }

                Integer idxPropEst = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM USER_INDEXES WHERE INDEX_NAME = 'IX_ASAMBLEAS_PROP_EST'", Integer.class);
                if (idxPropEst == null || idxPropEst == 0) {
                    jdbcTemplate.execute("CREATE INDEX IX_ASAMBLEAS_PROP_EST ON ASAMBLEAS (ID_PROPIEDAD, ESTADO)");
                }

                Integer idxAsist = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM USER_INDEXES WHERE INDEX_NAME = 'IX_ASIST_ASAMBLEA'", Integer.class);
                if (idxAsist == null || idxAsist == 0) {
                    jdbcTemplate.execute("CREATE INDEX IX_ASIST_ASAMBLEA ON ASISTENCIAS_ASAMBLEA (ID_ASAMBLEA)");
                }

                Integer idxPoderes = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM USER_INDEXES WHERE INDEX_NAME = 'IX_PODERES_ASAMBLEA'", Integer.class);
                if (idxPoderes == null || idxPoderes == 0) {
                    jdbcTemplate.execute("CREATE INDEX IX_PODERES_ASAMBLEA ON PODERES_REPRESENTACION (ID_ASAMBLEA)");
                }

                // Actas de Asamblea (F10-05) - ID_DOCUMENTO & FK & Index
                Integer colDocActa = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM USER_TAB_COLS WHERE TABLE_NAME = 'ACTAS_ASAMBLEA' AND COLUMN_NAME = 'ID_DOCUMENTO'",
                    Integer.class
                );
                if (colDocActa == null || colDocActa == 0) {
                    jdbcTemplate.execute("ALTER TABLE ACTAS_ASAMBLEA ADD (ID_DOCUMENTO NUMBER(19))");
                }

                Integer fkDocActa = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM USER_CONSTRAINTS WHERE CONSTRAINT_NAME = 'FK_ACTAS_DOC'",
                    Integer.class
                );
                if (fkDocActa == null || fkDocActa == 0) {
                    jdbcTemplate.execute("ALTER TABLE ACTAS_ASAMBLEA ADD CONSTRAINT FK_ACTAS_DOC FOREIGN KEY (ID_DOCUMENTO) REFERENCES DOCUMENTOS(ID_DOCUMENTO) ON DELETE SET NULL");
                }

                Integer idxDocActa = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM USER_INDEXES WHERE INDEX_NAME = 'IX_ACTAS_DOC'",
                    Integer.class
                );
                if (idxDocActa == null || idxDocActa == 0) {
                    jdbcTemplate.execute("CREATE INDEX IX_ACTAS_DOC ON ACTAS_ASAMBLEA (ID_DOCUMENTO)");
                }
            } catch (Exception ignored) {}

            // 3. Políticas RLS
            try {
                jdbcTemplate.execute("""
                    BEGIN
                        BEGIN DBMS_RLS.DROP_GROUPED_POLICY(NULL, 'ASAMBLEAS', 'SYS_DEFAULT', 'POL_RLS_PROP_ASAMBLEAS'); EXCEPTION WHEN OTHERS THEN NULL; END;
                        DBMS_RLS.ADD_GROUPED_POLICY(
                            object_schema   => NULL,
                            object_name     => 'ASAMBLEAS',
                            policy_group    => 'SYS_DEFAULT',
                            policy_name     => 'POL_RLS_PROP_ASAMBLEAS',
                            function_schema => NULL,
                            policy_function => 'PKG_SAED_SECURITY_RLS.FN_FILTRO_PROPIEDAD',
                            update_check    => FALSE,
                            enable          => TRUE,
                            static_policy   => FALSE,
                            policy_type     => dbms_rls.DYNAMIC,
                            long_predicate  => FALSE
                        );
                    EXCEPTION
                        WHEN OTHERS THEN NULL;
                    END;
                """);
                jdbcTemplate.execute("""
                    BEGIN
                        BEGIN DBMS_RLS.DROP_GROUPED_POLICY(NULL, 'ASISTENCIAS_ASAMBLEA', 'SYS_DEFAULT', 'POL_RLS_PROP_ASISTENCIAS_ASA'); EXCEPTION WHEN OTHERS THEN NULL; END;
                        DBMS_RLS.ADD_GROUPED_POLICY(
                            object_schema   => NULL,
                            object_name     => 'ASISTENCIAS_ASAMBLEA',
                            policy_group    => 'SYS_DEFAULT',
                            policy_name     => 'POL_RLS_PROP_ASISTENCIAS_ASA',
                            function_schema => NULL,
                            policy_function => 'PKG_SAED_SECURITY_RLS.FN_FILTRO_PROPIEDAD',
                            update_check    => FALSE,
                            enable          => TRUE,
                            static_policy   => FALSE,
                            policy_type     => dbms_rls.DYNAMIC,
                            long_predicate  => FALSE
                        );
                    EXCEPTION
                        WHEN OTHERS THEN NULL;
                    END;
                """);
                jdbcTemplate.execute("""
                    BEGIN
                        BEGIN DBMS_RLS.DROP_GROUPED_POLICY(NULL, 'PODERES_REPRESENTACION', 'SYS_DEFAULT', 'POL_RLS_PROP_PODERES_REP'); EXCEPTION WHEN OTHERS THEN NULL; END;
                        DBMS_RLS.ADD_GROUPED_POLICY(
                            object_schema   => NULL,
                            object_name     => 'PODERES_REPRESENTACION',
                            policy_group    => 'SYS_DEFAULT',
                            policy_name     => 'POL_RLS_PROP_PODERES_REP',
                            function_schema => NULL,
                            policy_function => 'PKG_SAED_SECURITY_RLS.FN_FILTRO_PROPIEDAD',
                            update_check    => FALSE,
                            enable          => TRUE,
                            static_policy   => FALSE,
                            policy_type     => dbms_rls.DYNAMIC,
                            long_predicate  => FALSE
                        );
                    EXCEPTION
                        WHEN OTHERS THEN NULL;
                    END;
                """);
                jdbcTemplate.execute("""
                    BEGIN
                        BEGIN DBMS_RLS.DROP_GROUPED_POLICY(NULL, 'ACTAS_ASAMBLEA', 'SYS_DEFAULT', 'POL_RLS_PROP_ACTAS_ASAMBLEA'); EXCEPTION WHEN OTHERS THEN NULL; END;
                        DBMS_RLS.ADD_GROUPED_POLICY(
                            object_schema   => NULL,
                            object_name     => 'ACTAS_ASAMBLEA',
                            policy_group    => 'SYS_DEFAULT',
                            policy_name     => 'POL_RLS_PROP_ACTAS_ASAMBLEA',
                            function_schema => NULL,
                            policy_function => 'PKG_SAED_SECURITY_RLS.FN_FILTRO_PROPIEDAD',
                            update_check    => FALSE,
                            enable          => TRUE,
                            static_policy   => FALSE,
                            policy_type     => dbms_rls.DYNAMIC,
                            long_predicate  => FALSE
                        );
                    EXCEPTION
                        WHEN OTHERS THEN NULL;
                    END;
                """);
                log.info("[SchemaInit] Políticas RLS para ASAMBLEAS, ASISTENCIAS_ASAMBLEA, PODERES_REPRESENTACION y ACTAS_ASAMBLEA verificadas.");
            } catch (Exception ignored) {}

            log.info("[SchemaInit] Pipeline de gobernanza ASAMBLEAS verificado exitosamente.");
        } catch (Exception e) {
            log.warn("[SchemaInit] Aviso en initAsambleasGovernancePipeline: {}", e.getMessage());
        }
    }

    private void initPolizasSeguroPipeline() {
        try {
            // 1. Agregar DEDUCIBLE a POLIZAS_SEGURO si no existe
            try {
                Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM USER_TAB_COLS WHERE TABLE_NAME = 'POLIZAS_SEGURO' AND COLUMN_NAME = 'DEDUCIBLE'",
                    Integer.class
                );
                if (count == null || count == 0) {
                    log.info("[SchemaInit] Agregando columna DEDUCIBLE a POLIZAS_SEGURO...");
                    jdbcTemplate.execute("ALTER TABLE POLIZAS_SEGURO ADD (DEDUCIBLE VARCHAR2(255 CHAR))");
                }
            } catch (Exception e) {
                log.debug("[SchemaInit] Columna DEDUCIBLE ya existe o error menor: {}", e.getMessage());
            }

            // 2. Agregar ID_DOCUMENTO a POLIZAS_SEGURO si no existe
            try {
                Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM USER_TAB_COLS WHERE TABLE_NAME = 'POLIZAS_SEGURO' AND COLUMN_NAME = 'ID_DOCUMENTO'",
                    Integer.class
                );
                if (count == null || count == 0) {
                    log.info("[SchemaInit] Agregando columna ID_DOCUMENTO a POLIZAS_SEGURO...");
                    jdbcTemplate.execute("ALTER TABLE POLIZAS_SEGURO ADD (ID_DOCUMENTO NUMBER(19))");
                }
            } catch (Exception e) {
                log.debug("[SchemaInit] Columna ID_DOCUMENTO ya existe o error menor: {}", e.getMessage());
            }

            // 3. FK hacia DOCUMENTOS(ID_DOCUMENTO)
            try {
                Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM USER_CONSTRAINTS WHERE CONSTRAINT_NAME = 'FK_POLIZAS_DOC'",
                    Integer.class
                );
                if (count == null || count == 0) {
                    log.info("[SchemaInit] Creando FK_POLIZAS_DOC en POLIZAS_SEGURO...");
                    jdbcTemplate.execute("ALTER TABLE POLIZAS_SEGURO ADD CONSTRAINT FK_POLIZAS_DOC FOREIGN KEY (ID_DOCUMENTO) REFERENCES DOCUMENTOS(ID_DOCUMENTO) ON DELETE SET NULL");
                }
            } catch (Exception e) {
                log.debug("[SchemaInit] FK_POLIZAS_DOC ya existe o error menor: {}", e.getMessage());
            }

            // 4. Índice IX_POLIZAS_DOC
            try {
                Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM USER_INDEXES WHERE INDEX_NAME = 'IX_POLIZAS_DOC'",
                    Integer.class
                );
                if (count == null || count == 0) {
                    jdbcTemplate.execute("CREATE INDEX IX_POLIZAS_DOC ON POLIZAS_SEGURO (ID_DOCUMENTO)");
                }
            } catch (Exception e) {
                log.debug("[SchemaInit] IX_POLIZAS_DOC ya existe o error menor: {}", e.getMessage());
            }

            // 5. Verificar política RLS
            try {
                jdbcTemplate.execute("""
                    BEGIN
                        BEGIN DBMS_RLS.DROP_GROUPED_POLICY(NULL, 'POLIZAS_SEGURO', 'SYS_DEFAULT', 'POL_RLS_PROP_POLIZAS_SEGURO'); EXCEPTION WHEN OTHERS THEN NULL; END;
                        DBMS_RLS.ADD_GROUPED_POLICY(
                            object_schema   => NULL,
                            object_name     => 'POLIZAS_SEGURO',
                            policy_group    => 'SYS_DEFAULT',
                            policy_name     => 'POL_RLS_PROP_POLIZAS_SEGURO',
                            function_schema => NULL,
                            policy_function => 'PKG_SAED_SECURITY_RLS.FN_FILTRO_PROPIEDAD',
                            update_check    => FALSE,
                            enable          => TRUE,
                            static_policy   => FALSE,
                            policy_type     => dbms_rls.DYNAMIC,
                            long_predicate  => FALSE
                        );
                    EXCEPTION
                        WHEN OTHERS THEN NULL;
                    END;
                """);
            } catch (Exception ignored) {}

            // 6. Asegurar ID_POLIZA e índice en ALERTAS_ADMIN para deduplicación robusta (OBS-02)
            try {
                Integer hasCol = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM USER_TAB_COLS WHERE TABLE_NAME = 'ALERTAS_ADMIN' AND COLUMN_NAME = 'ID_POLIZA'",
                    Integer.class
                );
                if (hasCol == null || hasCol == 0) {
                    jdbcTemplate.execute("ALTER TABLE ALERTAS_ADMIN ADD (ID_POLIZA NUMBER(19))");
                }
                Integer hasIdx = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM USER_INDEXES WHERE INDEX_NAME = 'IX_ALERTAS_ADMIN_POLIZA'",
                    Integer.class
                );
                if (hasIdx == null || hasIdx == 0) {
                    jdbcTemplate.execute("CREATE INDEX IX_ALERTAS_ADMIN_POLIZA ON ALERTAS_ADMIN (ID_PROPIEDAD, TIPO_ALERTA, ID_POLIZA, FECHA_CREACION)");
                }
            } catch (Exception ignored) {}

            log.info("[SchemaInit] Pipeline de POLIZAS_SEGURO (F10-07) verificado exitosamente.");
        } catch (Exception e) {
            log.warn("[SchemaInit] Aviso en initPolizasSeguroPipeline: {}", e.getMessage());
        }
    }

    private void initReportesConfiguradosYHistorialPipeline() {
        try {
            // 1. Columnas multi-tenant en REPORTES_CONFIGURADOS
            String[] colsToAdd = {
                "ID_ORGANIZACION NUMBER(19)",
                "ID_PROPIEDAD NUMBER(19)",
                "PARAMETROS_FILTRO_JSON CLOB",
                "ID_USUARIO_CREO NUMBER(19)",
                "FECHA_CREACION TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP",
                "FECHA_ACTUALIZACION TIMESTAMP WITH TIME ZONE"
            };

            for (String colDef : colsToAdd) {
                String colName = colDef.split(" ")[0];
                try {
                    Integer count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(1) FROM USER_TAB_COLS WHERE TABLE_NAME = 'REPORTES_CONFIGURADOS' AND COLUMN_NAME = ?",
                        Integer.class,
                        colName
                    );
                    if (count == null || count == 0) {
                        log.info("[SchemaInit] Agregando columna {} a REPORTES_CONFIGURADOS...", colName);
                        jdbcTemplate.execute("ALTER TABLE REPORTES_CONFIGURADOS ADD (" + colDef + ")");
                    }
                } catch (Exception e) {
                    log.debug("[SchemaInit] Columna {} en REPORTES_CONFIGURADOS ya existe o error menor: {}", colName, e.getMessage());
                }
            }

            // 2. Foreign keys en REPORTES_CONFIGURADOS
            try {
                Integer fkOrg = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM USER_CONSTRAINTS WHERE CONSTRAINT_NAME = 'FK_REPCFG_ORG'",
                    Integer.class
                );
                if (fkOrg == null || fkOrg == 0) {
                    jdbcTemplate.execute("ALTER TABLE REPORTES_CONFIGURADOS ADD CONSTRAINT FK_REPCFG_ORG FOREIGN KEY (ID_ORGANIZACION) REFERENCES ORGANIZACIONES(ID_ORGANIZACION) ON DELETE CASCADE");
                }
            } catch (Exception ignored) {}

            try {
                Integer fkProp = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM USER_CONSTRAINTS WHERE CONSTRAINT_NAME = 'FK_REPCFG_PROP'",
                    Integer.class
                );
                if (fkProp == null || fkProp == 0) {
                    jdbcTemplate.execute("ALTER TABLE REPORTES_CONFIGURADOS ADD CONSTRAINT FK_REPCFG_PROP FOREIGN KEY (ID_PROPIEDAD) REFERENCES PROPIEDADES(ID_PROPIEDAD) ON DELETE CASCADE");
                }
            } catch (Exception ignored) {}

            try {
                Integer fkUser = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM USER_CONSTRAINTS WHERE CONSTRAINT_NAME = 'FK_REPCFG_USR'",
                    Integer.class
                );
                if (fkUser == null || fkUser == 0) {
                    jdbcTemplate.execute("ALTER TABLE REPORTES_CONFIGURADOS ADD CONSTRAINT FK_REPCFG_USR FOREIGN KEY (ID_USUARIO_CREO) REFERENCES USUARIOS(ID_USUARIO) ON DELETE SET NULL");
                }
            } catch (Exception ignored) {}

            // 3. Índices de rendimiento
            try {
                Integer idx1 = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM USER_INDEXES WHERE INDEX_NAME = 'IX_REPCFG_ORG_PROP'",
                    Integer.class
                );
                if (idx1 == null || idx1 == 0) {
                    jdbcTemplate.execute("CREATE INDEX IX_REPCFG_ORG_PROP ON REPORTES_CONFIGURADOS (ID_ORGANIZACION, ID_PROPIEDAD)");
                }
            } catch (Exception ignored) {}

            try {
                Integer idx2 = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM USER_INDEXES WHERE INDEX_NAME = 'IX_REPCFG_CLAVE'",
                    Integer.class
                );
                if (idx2 == null || idx2 == 0) {
                    jdbcTemplate.execute("CREATE INDEX IX_REPCFG_CLAVE ON REPORTES_CONFIGURADOS (CONSULTA_ORIGEN_CLAVE)");
                }
            } catch (Exception ignored) {}

            try {
                Integer idxHist = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM USER_INDEXES WHERE INDEX_NAME = 'IX_HISTREP_ORG_PROP'",
                    Integer.class
                );
                if (idxHist == null || idxHist == 0) {
                    jdbcTemplate.execute("CREATE INDEX IX_HISTREP_ORG_PROP ON HISTORIAL_REPORTES (ID_ORGANIZACION, ID_PROPIEDAD, FECHA_EJECUCION)");
                }
            } catch (Exception ignored) {}

            // 4. Semillas canónicas para plantillas de reporte del sistema (ID_ORGANIZACION = NULL)
            try {
                jdbcTemplate.execute("""
                    MERGE INTO REPORTES_CONFIGURADOS rc
                    USING (
                        SELECT 'CARTERA_MOROSA' AS CODIGO, 'Reporte de Cartera Morosa' AS NOMBRE, 'FINANZAS' AS MODULO,
                               'Estado de cuentas por cobrar y morosidad de unidades residenciales o comerciales.' AS DESCRIPCION,
                               'PDF' AS FORMATO_SALIDA_DEFECTO, 'CARTERA_MOROSA' AS CONSULTA_ORIGEN_CLAVE,
                               'ADMIN_PROPIEDAD' AS ROL_MINIMO_EJECUCION, 'ACTIVO' AS ESTADO FROM DUAL
                        UNION ALL
                        SELECT 'EJECUCION_CUOTAS' AS CODIGO, 'Reporte de Ejecución de Cuotas' AS NOMBRE, 'FINANZAS' AS MODULO,
                               'Resumen mensual y estado de recaudo de cuotas emitidas por unidad.' AS DESCRIPCION,
                               'PDF' AS FORMATO_SALIDA_DEFECTO, 'EJECUCION_CUOTAS' AS CONSULTA_ORIGEN_CLAVE,
                               'ADMIN_PROPIEDAD' AS ROL_MINIMO_EJECUCION, 'ACTIVO' AS ESTADO FROM DUAL
                        UNION ALL
                        SELECT 'PAGOS_RECIENTES' AS CODIGO, 'Reporte de Pagos Recientes' AS NOMBRE, 'FINANZAS' AS MODULO,
                               'Detalle cronológico de recaudos, métodos de pago y comprobantes.' AS DESCRIPCION,
                               'PDF' AS FORMATO_SALIDA_DEFECTO, 'PAGOS_RECIENTES' AS CONSULTA_ORIGEN_CLAVE,
                               'ADMIN_PROPIEDAD' AS ROL_MINIMO_EJECUCION, 'ACTIVO' AS ESTADO FROM DUAL
                        UNION ALL
                        SELECT 'EJECUCION_PRESUPUESTAL' AS CODIGO, 'Reporte de Ejecución Presupuestal' AS NOMBRE, 'FINANZAS' AS MODULO,
                               'Comparativo analítico entre presupuesto aprobado y pagos reales del período.' AS DESCRIPCION,
                               'PDF' AS FORMATO_SALIDA_DEFECTO, 'EJECUCION_PRESUPUESTAL' AS CONSULTA_ORIGEN_CLAVE,
                               'ADMIN_PROPIEDAD' AS ROL_MINIMO_EJECUCION, 'ACTIVO' AS ESTADO FROM DUAL
                    ) s ON (rc.CODIGO = s.CODIGO AND rc.ID_ORGANIZACION IS NULL)
                    WHEN MATCHED THEN
                        UPDATE SET rc.NOMBRE = s.NOMBRE, rc.DESCRIPCION = s.DESCRIPCION,
                                   rc.FORMATO_SALIDA_DEFECTO = s.FORMATO_SALIDA_DEFECTO,
                                   rc.CONSULTA_ORIGEN_CLAVE = s.CONSULTA_ORIGEN_CLAVE,
                                   rc.ROL_MINIMO_EJECUCION = s.ROL_MINIMO_EJECUCION,
                                   rc.ESTADO = s.ESTADO
                    WHEN NOT MATCHED THEN
                        INSERT (CODIGO, NOMBRE, MODULO, DESCRIPCION, FORMATO_SALIDA_DEFECTO, CONSULTA_ORIGEN_CLAVE, ROL_MINIMO_EJECUCION, ESTADO)
                        VALUES (s.CODIGO, s.NOMBRE, s.MODULO, s.DESCRIPCION, s.FORMATO_SALIDA_DEFECTO, s.CONSULTA_ORIGEN_CLAVE, s.ROL_MINIMO_EJECUCION, s.ESTADO)
                """);
                log.info("[SchemaInit] Plantillas de reportes del sistema registradas exitosamente.");
            } catch (Exception e) {
                log.warn("[SchemaInit] Aviso al registrar plantillas de reportes: {}", e.getMessage());
            }

            // 5. Inmutabilidad en HISTORIAL_REPORTES (trigger TRG_HISTREP_INMUTABLE)
            try {
                jdbcTemplate.execute("""
                    CREATE OR REPLACE TRIGGER TRG_HISTREP_INMUTABLE
                    BEFORE UPDATE OR DELETE ON HISTORIAL_REPORTES
                    FOR EACH ROW
                    BEGIN
                        RAISE_APPLICATION_ERROR(-20060, 'HISTORIAL_REPORTES es inmutable. No se permite modificar ni eliminar registros de auditoria.');
                    END;
                """);
                log.info("[SchemaInit] Trigger TRG_HISTREP_INMUTABLE verificado.");
            } catch (Exception e) {
                log.warn("[SchemaInit] Aviso al verificar TRG_HISTREP_INMUTABLE: {}", e.getMessage());
            }

            // 6. Políticas RLS para REPORTES_CONFIGURADOS y HISTORIAL_REPORTES
            try {
                jdbcTemplate.execute("""
                    BEGIN
                        BEGIN DBMS_RLS.DROP_GROUPED_POLICY(NULL, 'REPORTES_CONFIGURADOS', 'SYS_DEFAULT', 'POL_RLS_PROP_REPORTES_CONFIG'); EXCEPTION WHEN OTHERS THEN NULL; END;
                        DBMS_RLS.ADD_GROUPED_POLICY(
                            object_schema   => NULL,
                            object_name     => 'REPORTES_CONFIGURADOS',
                            policy_group    => 'SYS_DEFAULT',
                            policy_name     => 'POL_RLS_PROP_REPORTES_CONFIG',
                            function_schema => NULL,
                            policy_function => 'PKG_SAED_SECURITY_RLS.FN_FILTRO_PROPIEDAD',
                            statement_types => 'SELECT,INSERT,UPDATE,DELETE',
                            update_check    => FALSE,
                            enable          => TRUE,
                            static_policy   => FALSE,
                            policy_type     => dbms_rls.DYNAMIC,
                            long_predicate  => FALSE
                        );
                    EXCEPTION
                        WHEN OTHERS THEN NULL;
                    END;
                """);

                jdbcTemplate.execute("""
                    BEGIN
                        BEGIN DBMS_RLS.DROP_GROUPED_POLICY(NULL, 'HISTORIAL_REPORTES', 'SYS_DEFAULT', 'POL_RLS_PROP_HISTORIAL_REPORTES'); EXCEPTION WHEN OTHERS THEN NULL; END;
                        DBMS_RLS.ADD_GROUPED_POLICY(
                            object_schema   => NULL,
                            object_name     => 'HISTORIAL_REPORTES',
                            policy_group    => 'SYS_DEFAULT',
                            policy_name     => 'POL_RLS_PROP_HISTORIAL_REPORTES',
                            function_schema => NULL,
                            policy_function => 'PKG_SAED_SECURITY_RLS.FN_FILTRO_PROPIEDAD',
                            statement_types => 'SELECT,INSERT',
                            update_check    => FALSE,
                            enable          => TRUE,
                            static_policy   => FALSE,
                            policy_type     => dbms_rls.DYNAMIC,
                            long_predicate  => FALSE
                        );
                    EXCEPTION
                        WHEN OTHERS THEN NULL;
                    END;
                """);
                log.info("[SchemaInit] Políticas RLS para REPORTES_CONFIGURADOS y HISTORIAL_REPORTES configuradas.");
            } catch (Exception ignored) {}

            log.info("[SchemaInit] Pipeline de REPORTES_CONFIGURADOS e HISTORIAL_REPORTES (F11-04 Block D) verificado exitosamente.");
        } catch (Exception e) {
            log.warn("[SchemaInit] Aviso general en initReportesConfiguradosYHistorialPipeline: {}", e.getMessage());
        }
    }

    private void initPorteriaTurnos() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM USER_TABLES WHERE TABLE_NAME = 'PORTERIA_TURNOS'",
                Integer.class
            );
            if (count == null || count == 0) {
                log.info("[SchemaInit] Creando tabla PORTERIA_TURNOS (Ley 1920 de 2018)...");
                jdbcTemplate.execute("""
                    CREATE TABLE PORTERIA_TURNOS (
                        ID_ASIGNACION_TURNO NUMBER GENERATED BY DEFAULT ON NULL AS IDENTITY PRIMARY KEY,
                        ID_PROPIEDAD NUMBER NOT NULL,
                        ID_PORTERIA NUMBER NOT NULL,
                        ID_USUARIO NUMBER NOT NULL,
                        CODIGO_TURNO VARCHAR2(50 CHAR) NOT NULL,
                        NOMBRE_TURNO VARCHAR2(100 CHAR),
                        HORA_INICIO VARCHAR2(10 CHAR),
                        HORA_FIN VARCHAR2(10 CHAR),
                        DIAS_SEMANA VARCHAR2(100 CHAR) DEFAULT 'L-D',
                        ESTADO VARCHAR2(20 CHAR) DEFAULT 'ACTIVO',
                        FECHA_ASIGNACION TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                        CONSTRAINT FK_PT_PROPIEDAD FOREIGN KEY (ID_PROPIEDAD) REFERENCES PROPIEDADES(ID_PROPIEDAD) ON DELETE CASCADE,
                        CONSTRAINT FK_PT_PORTERIA FOREIGN KEY (ID_PORTERIA) REFERENCES PORTERIAS(ID_PORTERIA) ON DELETE CASCADE,
                        CONSTRAINT FK_PT_USUARIO FOREIGN KEY (ID_USUARIO) REFERENCES USUARIOS(ID_USUARIO) ON DELETE CASCADE,
                        CONSTRAINT UK_PT_PORTERO_PORTERIA UNIQUE (ID_PORTERIA, ID_USUARIO)
                    )
                """);
            }

            try {
                jdbcTemplate.execute("""
                    BEGIN
                        BEGIN DBMS_RLS.DROP_GROUPED_POLICY(NULL, 'PORTERIA_TURNOS', 'SYS_DEFAULT', 'POL_RLS_PROP_PORT_TURNOS'); EXCEPTION WHEN OTHERS THEN NULL; END;
                        DBMS_RLS.ADD_GROUPED_POLICY(
                            object_schema   => NULL,
                            object_name     => 'PORTERIA_TURNOS',
                            policy_group    => 'SYS_DEFAULT',
                            policy_name     => 'POL_RLS_PROP_PORT_TURNOS',
                            function_schema => NULL,
                            policy_function => 'PKG_SAED_SECURITY_RLS.FN_FILTRO_PROPIEDAD',
                            statement_types => 'SELECT,INSERT,UPDATE,DELETE',
                            update_check    => FALSE,
                            enable          => TRUE,
                            static_policy   => FALSE,
                            policy_type     => dbms_rls.DYNAMIC,
                            long_predicate  => FALSE
                        );
                    EXCEPTION
                        WHEN OTHERS THEN NULL;
                    END;
                """);
            } catch (Exception ignored) {}

            log.info("[SchemaInit] Pipeline de PORTERIA_TURNOS verificado exitosamente.");
        } catch (Exception e) {
            log.warn("[SchemaInit] Aviso en initPorteriaTurnos: {}", e.getMessage());
        }
    }

    private void initCarteraVirtualColumn() {
        try {
            Integer tableExists = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM USER_TABLES WHERE TABLE_NAME = 'CARTERA'",
                Integer.class
            );
            if (tableExists == null || tableExists == 0) {
                return;
            }

            boolean needsFix = false;
            try {
                String dataDefault = jdbcTemplate.queryForObject(
                    "SELECT DATA_DEFAULT FROM USER_TAB_COLS WHERE TABLE_NAME = 'CARTERA' AND COLUMN_NAME = 'SALDO_TOTAL'",
                    String.class
                );
                if (dataDefault != null && (dataDefault.trim().equals("7") || !dataDefault.contains("SALDO_MORA_30"))) {
                    needsFix = true;
                }
            } catch (Exception e) {
                log.debug("[SchemaInit] No se pudo leer DATA_DEFAULT de CARTERA.SALDO_TOTAL directamente: {}", e.getMessage());
                try {
                    Integer count7 = jdbcTemplate.queryForObject(
                        "SELECT COUNT(1) FROM CARTERA WHERE (SALDO_CORRIENTE + SALDO_MORA_30 + SALDO_MORA_60 + SALDO_MORA_90_MAS) = 0 AND SALDO_TOTAL = 7",
                        Integer.class
                    );
                    if (count7 != null && count7 > 0) {
                        needsFix = true;
                    }
                } catch (Exception ignored) {}
            }

            if (needsFix) {
                log.info("[SchemaInit] Corrigiendo columna virtual CARTERA.SALDO_TOTAL...");
                jdbcTemplate.execute("ALTER TABLE CARTERA DROP COLUMN SALDO_TOTAL");
                jdbcTemplate.execute("ALTER TABLE CARTERA ADD (SALDO_TOTAL AS (SALDO_CORRIENTE + SALDO_MORA_30 + SALDO_MORA_60 + SALDO_MORA_90_MAS))");
                log.info("[SchemaInit] Columna virtual CARTERA.SALDO_TOTAL corregida exitosamente a suma de saldos.");
            }
        } catch (Exception e) {
            log.warn("[SchemaInit] Aviso al verificar/corregir CARTERA.SALDO_TOTAL: {}", e.getMessage());
        }
    }
}



