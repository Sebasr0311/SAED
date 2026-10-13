package com.saed.backend.testsupport;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * Idempotently guarantees the active {@code USUARIO_ASIGNACIONES} row that {@code PKG_SAED_SESSION.SET_CONTEXT}
 * validates for a mocked {@code AssignmentService.validateAssignment(id, user)}.
 *
 * <p>The database only checks the key (user, role, organization, property, unit), enforced unique by
 * {@code UIX_ASIGNACION_UNICA}; the numeric id is only meaningful to the mock. The helper therefore reuses a row that
 * already carries the key (for example the demo seed's assignments 101-107) instead of inserting a duplicate, then
 * falls back to re-pointing the row that owns the requested id, and finally inserts it. The caller runs inside an
 * elevated (SUPERADMIN/bootstrap) database context.
 */
public final class AssignmentFixture {

    private AssignmentFixture() {
    }

    public static void ensure(JdbcTemplate jdbc, long idAsignacion, long idUsuario, String roleCode,
                              long idOrganizacion, Long idPropiedad, Long idUnidad) {
        Long idRol = jdbc.queryForObject("SELECT ID_ROL FROM ROLES WHERE CODIGO = ?", Long.class, roleCode);
        if (idRol == null) {
            throw new IllegalStateException("Role " + roleCode + " is missing from the catalog");
        }
        List<Long> sameKey = jdbc.queryForList(
                "SELECT ID_ASIGNACION FROM USUARIO_ASIGNACIONES WHERE ID_USUARIO = ? AND ID_ROL = ? "
                        + "AND NVL(ID_ORGANIZACION, -1) = ? AND NVL(ID_PROPIEDAD, -1) = ? AND NVL(ID_UNIDAD, -1) = ?",
                Long.class, idUsuario, idRol, idOrganizacion, nvl(idPropiedad), nvl(idUnidad));
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

    private static long nvl(Long value) {
        return value == null ? -1L : value;
    }
}
