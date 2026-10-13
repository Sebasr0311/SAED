package com.saed.backend.testsupport;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * Creates, idempotently and with valid values, the database rows a RESIDENTE_CONVIVENCIA identity needs on a fresh
 * install: the active {@code USUARIO_ASIGNACIONES} row that {@code PKG_SAED_SESSION.SET_CONTEXT} checks (without it every
 * request fails with ORA-20080 "Asignacion no autorizada" and the API answers 500) and the household link in
 * {@code RESIDENTES_UNIDAD}.
 *
 * <p>The demo seed only creates the assignments 101-105, so a test that mocks {@code validateAssignment(106, 6)} must
 * create the matching row itself instead of relying on rows left behind by other classes. The caller runs inside an
 * elevated (SUPERADMIN/bootstrap) database context and has already created the persona and the user.
 */
public final class CohabitantFixture {

    private static final String ROLE = "RESIDENTE_CONVIVENCIA";

    private CohabitantFixture() {
    }

    public static void ensure(JdbcTemplate jdbc, long idAsignacion, long idUsuario,
                              long idOrganizacion, long idPropiedad, long idUnidad) {
        Long idRol = jdbc.queryForObject("SELECT ID_ROL FROM ROLES WHERE CODIGO = ?", Long.class, ROLE);
        if (idRol == null) {
            throw new IllegalStateException("Role " + ROLE + " is missing from the catalog");
        }
        ensureAssignment(jdbc, idAsignacion, idUsuario, idRol, idOrganizacion, idPropiedad, idUnidad);
        ensureHousehold(jdbc, idUsuario, idUnidad);
    }

    private static void ensureAssignment(JdbcTemplate jdbc, long idAsignacion, long idUsuario, long idRol,
                                         long idOrganizacion, long idPropiedad, long idUnidad) {
        // UIX_ASIGNACION_UNICA is (user, role, organization, property, unit): reuse a row that already carries the key.
        List<Long> sameKey = jdbc.queryForList(
                "SELECT ID_ASIGNACION FROM USUARIO_ASIGNACIONES WHERE ID_USUARIO = ? AND ID_ROL = ? "
                        + "AND ID_ORGANIZACION = ? AND ID_PROPIEDAD = ? AND ID_UNIDAD = ?",
                Long.class, idUsuario, idRol, idOrganizacion, idPropiedad, idUnidad);
        if (!sameKey.isEmpty()) {
            jdbc.update("UPDATE USUARIO_ASIGNACIONES SET ESTADO = 'ACTIVA', FECHA_FIN = NULL WHERE ID_ASIGNACION = ?",
                    sameKey.get(0));
            return;
        }
        Integer byId = jdbc.queryForObject(
                "SELECT COUNT(*) FROM USUARIO_ASIGNACIONES WHERE ID_ASIGNACION = ?", Integer.class, idAsignacion);
        if (byId != null && byId > 0) {
            jdbc.update("UPDATE USUARIO_ASIGNACIONES SET ID_USUARIO = ?, ID_ROL = ?, ID_ORGANIZACION = ?, "
                            + "ID_PROPIEDAD = ?, ID_UNIDAD = ?, ESTADO = 'ACTIVA', FECHA_FIN = NULL WHERE ID_ASIGNACION = ?",
                    idUsuario, idRol, idOrganizacion, idPropiedad, idUnidad, idAsignacion);
        } else {
            jdbc.update("INSERT INTO USUARIO_ASIGNACIONES (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, "
                            + "ID_PROPIEDAD, ID_UNIDAD, ESTADO, FECHA_INICIO) VALUES (?, ?, ?, ?, ?, ?, 'ACTIVA', TRUNC(SYSDATE))",
                    idAsignacion, idUsuario, idRol, idOrganizacion, idPropiedad, idUnidad);
        }
    }

    private static void ensureHousehold(JdbcTemplate jdbc, long idUsuario, long idUnidad) {
        Long idPersona = jdbc.queryForObject("SELECT ID_PERSONA FROM USUARIOS WHERE ID_USUARIO = ?", Long.class, idUsuario);
        Integer active = jdbc.queryForObject(
                "SELECT COUNT(*) FROM RESIDENTES_UNIDAD WHERE ID_UNIDAD = ? AND ID_PERSONA = ? AND ESTADO = 'ACTIVO'",
                Integer.class, idUnidad, idPersona);
        if (active != null && active > 0) {
            jdbc.update("UPDATE RESIDENTES_UNIDAD SET TIPO_RESIDENTE = 'CONVIVIENTE', FECHA_FIN = NULL "
                    + "WHERE ID_UNIDAD = ? AND ID_PERSONA = ? AND ESTADO = 'ACTIVO'", idUnidad, idPersona);
        } else {
            jdbc.update("INSERT INTO RESIDENTES_UNIDAD (ID_UNIDAD, ID_PERSONA, TIPO_RESIDENTE, ESTADO) "
                    + "VALUES (?, ?, 'CONVIVIENTE', 'ACTIVO')", idUnidad, idPersona);
        }
    }
}
