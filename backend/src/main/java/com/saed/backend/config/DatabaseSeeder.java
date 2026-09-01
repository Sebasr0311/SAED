package com.saed.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Idempotent Database Seeder.
 * Executes on application startup to ensure that core catalog data,
 * default admin roles, and seed users exist with valid BCrypt passwords.
 */
@Component
public class DatabaseSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSeeder.class);

    private final JdbcTemplate jdbcTemplate;

    public DatabaseSeeder(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private void runSqlSafe(String sql) {
        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); " + sql + "; END;");
        } catch (Exception e) {
            log.debug("Seeder notice for SQL [{}]: {}", sql, e.getMessage());
        }
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Starting SAED Database Seeder...");
        try {
            BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
            String hashAdminGlobal = encoder.encode("admin_global123");
            String hashGeneral = encoder.encode("admin123");

            // 1. TIPOS_DOCUMENTO
            runSqlSafe("INSERT INTO TIPOS_DOCUMENTO (ID_TIPO_DOCUMENTO, CODIGO, NOMBRE, APLICA_PERSONA_NATURAL, APLICA_PERSONA_JURIDICA, ESTADO) " +
                   "SELECT 1, 'CC', 'Cedula de Ciudadania', 'S', 'N', 'ACTIVO' FROM DUAL " +
                   "WHERE NOT EXISTS (SELECT 1 FROM TIPOS_DOCUMENTO WHERE CODIGO = 'CC')");

            // 2. TIPOS_PROPIEDAD
            runSqlSafe("INSERT INTO TIPOS_PROPIEDAD (ID_TIPO_PROPIEDAD, CODIGO, NOMBRE, ESTADO) " +
                   "SELECT 1, 'EDIFICIO', 'Edificio Residencial', 'ACTIVO' FROM DUAL " +
                   "WHERE NOT EXISTS (SELECT 1 FROM TIPOS_PROPIEDAD WHERE CODIGO = 'EDIFICIO')");

            // 3. TIPOS_UNIDAD
            runSqlSafe("INSERT INTO TIPOS_UNIDAD (ID_TIPO_UNIDAD, CODIGO, NOMBRE) " +
                   "SELECT 1, 'APARTAMENTO', 'Apartamento' FROM DUAL " +
                   "WHERE NOT EXISTS (SELECT 1 FROM TIPOS_UNIDAD WHERE CODIGO = 'APARTAMENTO')");

            // 4. ROLES
            runSqlSafe("INSERT INTO ROLES (ID_ROL, CODIGO, NOMBRE, ALCANCE, ESTADO) SELECT 1, 'SUPERADMIN', 'Super Administrador', 'GLOBAL', 'ACTIVO' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ROLES WHERE CODIGO = 'SUPERADMIN')");
            runSqlSafe("INSERT INTO ROLES (ID_ROL, CODIGO, NOMBRE, ALCANCE, ESTADO) SELECT 2, 'ADMIN_ORGANIZACION', 'Admin Organizacion', 'ORGANIZACION', 'ACTIVO' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ROLES WHERE CODIGO = 'ADMIN_ORGANIZACION')");
            runSqlSafe("INSERT INTO ROLES (ID_ROL, CODIGO, NOMBRE, ALCANCE, ESTADO) SELECT 3, 'ADMIN_PROPIEDAD', 'Admin Propiedad', 'PROPIEDAD', 'ACTIVO' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ROLES WHERE CODIGO = 'ADMIN_PROPIEDAD')");
            runSqlSafe("INSERT INTO ROLES (ID_ROL, CODIGO, NOMBRE, ALCANCE, ESTADO) SELECT 4, 'PORTERO', 'Portero / Vigilante', 'PROPIEDAD', 'ACTIVO' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ROLES WHERE CODIGO = 'PORTERO')");
            runSqlSafe("INSERT INTO ROLES (ID_ROL, CODIGO, NOMBRE, ALCANCE, ESTADO) SELECT 5, 'RESIDENTE', 'Residente', 'UNIDAD', 'ACTIVO' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ROLES WHERE CODIGO = 'RESIDENTE')");

            // 5. ORGANIZACIONES
            runSqlSafe("INSERT INTO ORGANIZACIONES (ID_ORGANIZACION, NOMBRE, IDENTIFICACION_FISCAL, EMAIL_CONTACTO, PAIS, ESTADO) " +
                   "SELECT 1, 'SAED Global S.A.S.', '900123456-1', 'contacto@saed.com', 'Colombia', 'ACTIVA' FROM DUAL " +
                   "WHERE NOT EXISTS (SELECT 1 FROM ORGANIZACIONES WHERE ID_ORGANIZACION = 1)");

            // 6. PROPIEDADES
            runSqlSafe("INSERT INTO PROPIEDADES (ID_PROPIEDAD, ID_ORGANIZACION, ID_TIPO_PROPIEDAD, NOMBRE, DIRECCION, CIUDAD, PAIS, TIPO_OCUPACION_PREDOMINANTE, ESTADO) " +
                   "SELECT 1, 1, 1, 'Edificio Residencial SAED', 'Calle 100 # 15-20', 'Bogota', 'Colombia', 'MIXTA', 'ACTIVA' FROM DUAL " +
                   "WHERE NOT EXISTS (SELECT 1 FROM PROPIEDADES WHERE ID_PROPIEDAD = 1)");

            // 7. UNIDADES
            runSqlSafe("INSERT INTO UNIDADES (ID_UNIDAD, ID_PROPIEDAD, ID_TIPO_UNIDAD, IDENTIFICADOR, ESTADO) " +
                   "SELECT 1, 1, 1, 'Apto 201', 'ACTIVA' FROM DUAL " +
                   "WHERE NOT EXISTS (SELECT 1 FROM UNIDADES WHERE ID_UNIDAD = 1)");

            // 8. PERSONAS
            runSqlSafe("INSERT INTO PERSONAS (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TIPO_PERSONA, PRIMER_NOMBRE, PRIMER_APELLIDO, EMAIL) " +
                   "SELECT 1, 1, '1000000001', 'NATURAL', 'Super', 'Admin', 'admin_global@saed.com' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM PERSONAS WHERE ID_PERSONA = 1)");
            runSqlSafe("UPDATE PERSONAS SET EMAIL = 'admin_global@saed.com' WHERE ID_PERSONA = 1");

            runSqlSafe("INSERT INTO PERSONAS (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TIPO_PERSONA, PRIMER_NOMBRE, PRIMER_APELLIDO, EMAIL) " +
                   "SELECT 2, 1, '1000000002', 'NATURAL', 'Admin', 'Propiedad', 'admin@saed.com' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM PERSONAS WHERE ID_PERSONA = 2)");
            runSqlSafe("UPDATE PERSONAS SET EMAIL = 'admin@saed.com' WHERE ID_PERSONA = 2");

            runSqlSafe("INSERT INTO PERSONAS (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TIPO_PERSONA, PRIMER_NOMBRE, PRIMER_APELLIDO, EMAIL) " +
                   "SELECT 3, 1, '1000000003', 'NATURAL', 'Portero', 'Principal', 'portero01@saed.com' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM PERSONAS WHERE ID_PERSONA = 3)");
            runSqlSafe("UPDATE PERSONAS SET EMAIL = 'portero01@saed.com' WHERE ID_PERSONA = 3");

            runSqlSafe("INSERT INTO PERSONAS (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TIPO_PERSONA, PRIMER_NOMBRE, PRIMER_APELLIDO, EMAIL) " +
                   "SELECT 4, 1, '1000000004', 'NATURAL', 'Carlos', 'Martinez', 'camartinez@saed.com' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM PERSONAS WHERE ID_PERSONA = 4)");
            runSqlSafe("UPDATE PERSONAS SET EMAIL = 'camartinez@saed.com' WHERE ID_PERSONA = 4");

            // 9. USUARIOS (admin_global -> admin_global123 | others -> admin123)
            runSqlSafe("INSERT INTO USUARIOS (ID_USUARIO, ID_PERSONA, NOMBRE_USUARIO, EMAIL, HASH_PASSWORD, ESTADO, INTENTOS_FALLIDOS) " +
                   "SELECT 1, 1, 'admin_global', 'admin_global@saed.com', '" + hashAdminGlobal + "', 'ACTIVO', 0 FROM DUAL " +
                   "WHERE NOT EXISTS (SELECT 1 FROM USUARIOS WHERE ID_USUARIO = 1)");
            runSqlSafe("UPDATE USUARIOS SET NOMBRE_USUARIO = 'admin_global', EMAIL = 'admin_global@saed.com', HASH_PASSWORD = '" + hashAdminGlobal + "', ESTADO = 'ACTIVO', INTENTOS_FALLIDOS = 0 WHERE ID_USUARIO = 1 OR LOWER(NOMBRE_USUARIO) = 'admin_global'");

            runSqlSafe("INSERT INTO USUARIOS (ID_USUARIO, ID_PERSONA, NOMBRE_USUARIO, EMAIL, HASH_PASSWORD, ESTADO, INTENTOS_FALLIDOS) " +
                   "SELECT 2, 2, 'admin', 'admin@saed.com', '" + hashGeneral + "', 'ACTIVO', 0 FROM DUAL " +
                   "WHERE NOT EXISTS (SELECT 1 FROM USUARIOS WHERE ID_USUARIO = 2)");
            runSqlSafe("UPDATE USUARIOS SET NOMBRE_USUARIO = 'admin', EMAIL = 'admin@saed.com', HASH_PASSWORD = '" + hashGeneral + "', ESTADO = 'ACTIVO', INTENTOS_FALLIDOS = 0 WHERE ID_USUARIO = 2 OR LOWER(NOMBRE_USUARIO) = 'admin'");

            runSqlSafe("INSERT INTO USUARIOS (ID_USUARIO, ID_PERSONA, NOMBRE_USUARIO, EMAIL, HASH_PASSWORD, ESTADO, INTENTOS_FALLIDOS) " +
                   "SELECT 3, 3, 'portero01', 'portero01@saed.com', '" + hashGeneral + "', 'ACTIVO', 0 FROM DUAL " +
                   "WHERE NOT EXISTS (SELECT 1 FROM USUARIOS WHERE ID_USUARIO = 3)");
            runSqlSafe("UPDATE USUARIOS SET NOMBRE_USUARIO = 'portero01', EMAIL = 'portero01@saed.com', HASH_PASSWORD = '" + hashGeneral + "', ESTADO = 'ACTIVO', INTENTOS_FALLIDOS = 0 WHERE ID_USUARIO = 3 OR LOWER(NOMBRE_USUARIO) = 'portero01'");

            runSqlSafe("INSERT INTO USUARIOS (ID_USUARIO, ID_PERSONA, NOMBRE_USUARIO, EMAIL, HASH_PASSWORD, ESTADO, INTENTOS_FALLIDOS) " +
                   "SELECT 4, 4, 'camartinez', 'camartinez@saed.com', '" + hashGeneral + "', 'ACTIVO', 0 FROM DUAL " +
                   "WHERE NOT EXISTS (SELECT 1 FROM USUARIOS WHERE ID_USUARIO = 4)");
            runSqlSafe("UPDATE USUARIOS SET NOMBRE_USUARIO = 'camartinez', EMAIL = 'camartinez@saed.com', HASH_PASSWORD = '" + hashGeneral + "', ESTADO = 'ACTIVO', INTENTOS_FALLIDOS = 0 WHERE ID_USUARIO = 4 OR LOWER(NOMBRE_USUARIO) = 'camartinez'");

            // 10. ADMINISTRADORES_SAED
            runSqlSafe("INSERT INTO ADMINISTRADORES_SAED (ID_ADMINISTRADOR_SAED, ID_USUARIO, NIVEL, ESTADO) " +
                   "SELECT 1, 1, 'SUPERADMIN', 'ACTIVO' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ADMINISTRADORES_SAED WHERE ID_USUARIO = 1)");

            // 11. USUARIO_ASIGNACIONES
            runSqlSafe("INSERT INTO USUARIO_ASIGNACIONES (ID_ASIGNACION, ID_USUARIO, ID_ROL, ESTADO, FECHA_INICIO) " +
                   "SELECT 101, 1, 1, 'ACTIVA', TRUNC(SYSDATE) FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM USUARIO_ASIGNACIONES WHERE ID_USUARIO = 1 AND ID_ROL = 1)");

            runSqlSafe("INSERT INTO USUARIO_ASIGNACIONES (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ESTADO, FECHA_INICIO) " +
                   "SELECT 102, 2, 3, 1, 1, 'ACTIVA', TRUNC(SYSDATE) FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM USUARIO_ASIGNACIONES WHERE ID_USUARIO = 2 AND ID_ROL = 3)");

            runSqlSafe("INSERT INTO USUARIO_ASIGNACIONES (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ESTADO, FECHA_INICIO) " +
                   "SELECT 103, 3, 4, 1, 1, 'ACTIVA', TRUNC(SYSDATE) FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM USUARIO_ASIGNACIONES WHERE ID_USUARIO = 3 AND ID_ROL = 4)");

            runSqlSafe("INSERT INTO USUARIO_ASIGNACIONES (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ID_UNIDAD, ESTADO, FECHA_INICIO) " +
                   "SELECT 104, 4, 5, 1, 1, 1, 'ACTIVA', TRUNC(SYSDATE) FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM USUARIO_ASIGNACIONES WHERE ID_USUARIO = 4 AND ID_ROL = 5)");

            log.info("SAED Database Seeder completed successfully.");
        } catch (Exception ex) {
            log.warn("Database seeding encountered a non-fatal exception: {}", ex.getMessage());
        }
    }
}
