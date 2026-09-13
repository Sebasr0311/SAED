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

        initPlantillasContratos();
        initResidentesUnidadConstraints();
        initTokensActivacion();
        initOnboardingIntenciones();

        log.info("[SchemaInit] Verificación de esquema completada.");
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
        } catch (Exception e) {
            log.debug("[SchemaInit] Aviso al verificar ONBOARDING_INTENCIONES: {}", e.getMessage());
        }
    }
}
