package com.saed.backend.testsupport;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Creates, idempotently and with valid values, the database rows a RESIDENTE_CONVIVENCIA identity needs on a fresh
 * install: the active {@code USUARIO_ASIGNACIONES} row (see {@link AssignmentFixture}) that
 * {@code PKG_SAED_SESSION.SET_CONTEXT} checks (without it every
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
        AssignmentFixture.ensure(jdbc, idAsignacion, idUsuario, ROLE, idOrganizacion, idPropiedad, idUnidad);
        ensureHousehold(jdbc, idUsuario, idUnidad);
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
