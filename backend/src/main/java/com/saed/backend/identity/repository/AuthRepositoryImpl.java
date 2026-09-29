package com.saed.backend.identity.repository;

import com.saed.backend.identity.dto.AuthData;
import com.saed.backend.identity.dto.AuthUserDTO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlOutParameter;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class AuthRepositoryImpl implements AuthRepository {

    private final JdbcTemplate jdbcTemplate;
    private final SimpleJdbcCall getAuthDataCall;
    private final SimpleJdbcCall getUserProfileCall;
    private final SimpleJdbcCall registerFailureCall;
    private final SimpleJdbcCall registerSuccessCall;

    public AuthRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;

        // Setup CallableStatements via SimpleJdbcCall
        this.getAuthDataCall = new SimpleJdbcCall(jdbcTemplate)
                .withCatalogName("SAED_SEC_MASTER.PKG_AUTH_BOOTSTRAP")
                .withProcedureName("GET_AUTH_DATA").withoutProcedureColumnMetaDataAccess()
                .declareParameters(
                        new SqlParameter("p_email", Types.VARCHAR),
                        new SqlOutParameter("p_id_usuario", Types.NUMERIC),
                        new SqlOutParameter("p_hash", Types.VARCHAR),
                        new SqlOutParameter("p_estado", Types.VARCHAR),
                        new SqlOutParameter("p_intentos", Types.NUMERIC)
                );

        this.getUserProfileCall = new SimpleJdbcCall(jdbcTemplate)
                .withCatalogName("SAED_SEC_MASTER.PKG_AUTH_BOOTSTRAP")
                .withProcedureName("GET_USER_PROFILE").withoutProcedureColumnMetaDataAccess()
                .declareParameters(
                        new SqlParameter("p_id_usuario", Types.NUMERIC),
                        new SqlOutParameter("p_nombre_usuario", Types.VARCHAR),
                        new SqlOutParameter("p_email", Types.VARCHAR),
                        new SqlOutParameter("p_rol_codigo", Types.VARCHAR),
                        new SqlOutParameter("p_alcance", Types.VARCHAR),
                        new SqlOutParameter("p_org_id", Types.NUMERIC),
                        new SqlOutParameter("p_prop_id", Types.NUMERIC),
                        new SqlOutParameter("p_unidad_id", Types.NUMERIC)
                );

        this.registerFailureCall = new SimpleJdbcCall(jdbcTemplate)
                .withCatalogName("SAED_SEC_MASTER.PKG_AUTH_BOOTSTRAP")
                .withProcedureName("REGISTER_LOGIN_FAILURE").withoutProcedureColumnMetaDataAccess()
                .declareParameters(
                        new SqlParameter("p_id_usuario", Types.NUMERIC),
                        new SqlParameter("p_ip_origen", Types.VARCHAR)
                );

        this.registerSuccessCall = new SimpleJdbcCall(jdbcTemplate)
                .withCatalogName("SAED_SEC_MASTER.PKG_AUTH_BOOTSTRAP")
                .withProcedureName("REGISTER_LOGIN_SUCCESS").withoutProcedureColumnMetaDataAccess()
                .declareParameters(
                        new SqlParameter("p_id_usuario", Types.NUMERIC),
                        new SqlParameter("p_ip_origen", Types.VARCHAR)
                );
    }

    @Override
    public Optional<AuthData> getAuthData(String username) {
        Map<String, Object> out = getAuthDataCall.execute(Map.of("p_email", username));

        Number idUsuario = (Number) out.get("p_id_usuario");
        if (idUsuario == null) {
            return Optional.empty();
        }

        return Optional.of(AuthData.builder()
                .idUsuario(idUsuario.longValue())
                .hashPassword((String) out.get("p_hash"))
                .estado((String) out.get("p_estado"))
                .intentosFallidos(((Number) out.get("p_intentos")).intValue())
                .build());
    }

    @Override
    public AuthUserDTO getUserProfile(Long userId) {
        Map<String, Object> out = getUserProfileCall.execute(Map.of("p_id_usuario", userId));
        String nombreUsuario = (String) out.get("p_nombre_usuario");
        if (nombreUsuario == null) {
            return null;
        }

        // Establecer contexto bootstrap temporal para que las consultas de identidad y asignación no sean bloqueadas por VPD
        try {
            jdbcTemplate.update("CALL PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(?)", userId);
        } catch (Exception ignored) {}

        Long idPersona = null;
        String nombreCompleto = null;
        try {
            java.util.List<Map<String, Object>> pList = jdbcTemplate.queryForList(
                "SELECT p.ID_PERSONA, TRIM(p.PRIMER_NOMBRE || ' ' || NVL(p.SEGUNDO_NOMBRE, '') || ' ' || p.PRIMER_APELLIDO || ' ' || NVL(p.SEGUNDO_APELLIDO, '')) AS NOMBRE_COMPLETO " +
                "FROM USUARIOS u JOIN PERSONAS p ON u.ID_PERSONA = p.ID_PERSONA WHERE u.ID_USUARIO = ?",
                userId
            );
            if (!pList.isEmpty()) {
                Map<String, Object> row = pList.get(0);
                if (row.get("ID_PERSONA") != null) {
                    idPersona = ((Number) row.get("ID_PERSONA")).longValue();
                }
                nombreCompleto = (String) row.get("NOMBRE_COMPLETO");
                if (nombreCompleto != null) {
                    nombreCompleto = nombreCompleto.replaceAll("\\s+", " ").trim();
                }
            } else {
                List<Long> fallbackPersona = jdbcTemplate.query(
                    "SELECT ID_PERSONA FROM USUARIOS WHERE ID_USUARIO = ?",
                    (rs, r) -> rs.getLong("ID_PERSONA"),
                    userId
                );
                if (!fallbackPersona.isEmpty()) {
                    idPersona = fallbackPersona.get(0);
                }
            }
        } catch (Exception ignored) {}

        String rolCodigo = (String) out.get("p_rol_codigo");
        String alcance = (String) out.get("p_alcance");
        String tipoResidente = null;
        Number unidadIdNum = (Number) out.get("p_unidad_id");
        Long unidadId = unidadIdNum != null ? unidadIdNum.longValue() : null;

        // Si la unidad no está en la asignación o apunta a una unidad desactualizada, consultar en RESIDENTES_UNIDAD
        if (idPersona != null) {
            try {
                java.util.List<Long> uList = jdbcTemplate.query(
                    "SELECT ID_UNIDAD FROM RESIDENTES_UNIDAD WHERE ID_PERSONA = ? AND ESTADO IN ('ACTIVO', 'ACTIVA') ORDER BY ID_RESIDENTE_UNIDAD DESC FETCH FIRST 1 ROWS ONLY",
                    (rs, rowNum) -> rs.getLong("ID_UNIDAD"),
                    idPersona
                );
                if (!uList.isEmpty()) {
                    unidadId = uList.get(0);
                } else if (unidadId == null) {
                    java.util.List<Long> puList = jdbcTemplate.query(
                        "SELECT ID_UNIDAD FROM PROPIETARIOS_UNIDAD WHERE ID_PERSONA = ? AND ESTADO = 'ACTIVO' ORDER BY ID_PROPIETARIO_UNIDAD DESC FETCH FIRST 1 ROWS ONLY",
                        (rs, rowNum) -> rs.getLong("ID_UNIDAD"),
                        idPersona
                    );
                    if (!puList.isEmpty()) {
                        unidadId = puList.get(0);
                    }
                }
            } catch (Exception ignored) {}
        }

        // Sincronizar en caliente la asignación en USUARIO_ASIGNACIONES si difiere de la unidad real habitada
        if (unidadId != null && idPersona != null) {
            try {
                jdbcTemplate.update("""
                    UPDATE USUARIO_ASIGNACIONES
                    SET ID_UNIDAD = ?,
                        ID_PROPIEDAD = COALESCE((SELECT ID_PROPIEDAD FROM UNIDADES WHERE ID_UNIDAD = ?), ID_PROPIEDAD),
                        ID_ORGANIZACION = COALESCE((SELECT p.ID_ORGANIZACION FROM UNIDADES u JOIN PROPIEDADES p ON u.ID_PROPIEDAD = p.ID_PROPIEDAD WHERE u.ID_UNIDAD = ?), ID_ORGANIZACION)
                    WHERE ID_USUARIO = ? AND ESTADO IN ('ACTIVO', 'ACTIVA') AND (ID_UNIDAD IS NULL OR ID_UNIDAD != ?)
                """, unidadId, unidadId, unidadId, userId, unidadId);
            } catch (Exception ignored) {}
        }

        boolean isHabitanteFisico = false;
        if (idPersona != null && unidadId != null) {
            try {
                java.util.List<String> tipList = jdbcTemplate.query(
                    "SELECT TIPO_RESIDENTE FROM RESIDENTES_UNIDAD WHERE ID_PERSONA = ? AND ID_UNIDAD = ? AND ESTADO IN ('ACTIVO', 'ACTIVA') ORDER BY ID_RESIDENTE_UNIDAD DESC FETCH FIRST 1 ROWS ONLY",
                    (rs, rowNum) -> rs.getString("TIPO_RESIDENTE"),
                    idPersona, unidadId
                );
                if (!tipList.isEmpty()) {
                    isHabitanteFisico = true;
                    tipoResidente = tipList.get(0);
                }
            } catch (Exception ignored) {}
        }

        if ("ARRENDATARIO".equalsIgnoreCase(tipoResidente)) {
            rolCodigo = "RESIDENTE";
            alcance = "UNIDAD";
        } else if ("PROPIETARIO".equalsIgnoreCase(tipoResidente) || "PROPIETARIO_RESIDENTE".equalsIgnoreCase(tipoResidente)) {
            rolCodigo = "RESIDENTE";
            alcance = "UNIDAD";
            tipoResidente = "PROPIETARIO_RESIDENTE";
        } else if ("CONVIVIENTE".equalsIgnoreCase(tipoResidente) || "FAMILIAR".equalsIgnoreCase(tipoResidente) || "RESIDENTE_CONVIVENCIA".equalsIgnoreCase(rolCodigo)) {
            rolCodigo = "RESIDENTE_CONVIVENCIA";
            alcance = "UNIDAD";
            tipoResidente = "CONVIVIENTE";

            try {
                jdbcTemplate.update("""
                    UPDATE USUARIO_ASIGNACIONES
                    SET ID_ROL = (SELECT ID_ROL FROM ROLES WHERE CODIGO = 'RESIDENTE_CONVIVENCIA' AND ESTADO = 'ACTIVO')
                    WHERE ID_USUARIO = ?
                      AND ID_UNIDAD = ?
                      AND ID_ROL = (SELECT ID_ROL FROM ROLES WHERE CODIGO = 'RESIDENTE' AND ESTADO = 'ACTIVO')
                      AND ESTADO IN ('ACTIVO', 'ACTIVA')
                """, userId, unidadId);
            } catch (Exception ignored) {}
        } else if ("PROPIETARIO".equalsIgnoreCase(rolCodigo) && isHabitanteFisico && !"PROPIETARIO_NO_RESIDENTE".equalsIgnoreCase(tipoResidente)) {
            rolCodigo = "RESIDENTE";
            alcance = "UNIDAD";
            tipoResidente = "PROPIETARIO_RESIDENTE";

            try {
                jdbcTemplate.update("""
                    UPDATE USUARIO_ASIGNACIONES
                    SET ID_ROL = (SELECT ID_ROL FROM ROLES WHERE CODIGO = 'RESIDENTE' AND ESTADO = 'ACTIVO'),
                        ID_UNIDAD = COALESCE(ID_UNIDAD, ?)
                    WHERE ID_USUARIO = ?
                      AND ID_ROL = (SELECT ID_ROL FROM ROLES WHERE CODIGO = 'PROPIETARIO' AND ESTADO = 'ACTIVO')
                      AND ESTADO IN ('ACTIVO', 'ACTIVA')
                """, unidadId, userId);
            } catch (Exception ignored) {}
        }

        Long propId = (Number) out.get("p_prop_id") != null ? ((Number) out.get("p_prop_id")).longValue() : null;
        Long orgId = (Number) out.get("p_org_id") != null ? ((Number) out.get("p_org_id")).longValue() : null;
        if (unidadId != null) {
            try {
                List<Map<String, Object>> uInfo = jdbcTemplate.queryForList(
                    "SELECT u.ID_PROPIEDAD, p.ID_ORGANIZACION FROM UNIDADES u JOIN PROPIEDADES p ON u.ID_PROPIEDAD = p.ID_PROPIEDAD WHERE u.ID_UNIDAD = ?",
                    unidadId
                );
                if (!uInfo.isEmpty()) {
                    Number pNum = (Number) uInfo.get(0).get("ID_PROPIEDAD");
                    Number oNum = (Number) uInfo.get(0).get("ID_ORGANIZACION");
                    if (pNum != null) propId = pNum.longValue();
                    if (oNum != null) orgId = oNum.longValue();
                }
            } catch (Exception ignored) {}
        }

        return new AuthUserDTO(
                userId,
                idPersona,
                nombreUsuario,
                nombreCompleto,
                (String) out.get("p_email"),
                rolCodigo,
                alcance,
                orgId,
                propId,
                unidadId,
                tipoResidente
        );
    }

    @Override
    public void registerLoginFailure(Long userId, String ipAddress) {
        registerFailureCall.execute(Map.of(
            "p_id_usuario", userId,
            "p_ip_origen", ipAddress != null ? ipAddress : ""
        ));
    }

    @Override
    public void registerLoginSuccess(Long userId, String ipAddress) {
        registerSuccessCall.execute(Map.of(
            "p_id_usuario", userId,
            "p_ip_origen", ipAddress != null ? ipAddress : ""
        ));
    }

    @Override
    public Optional<String> getPasswordHash(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        try {
            java.util.List<String> list = jdbcTemplate.query(
                "SELECT HASH_PASSWORD FROM USUARIOS WHERE ID_USUARIO = ?",
                (rs, rowNum) -> rs.getString("HASH_PASSWORD"),
                userId
            );
            if (!list.isEmpty() && list.get(0) != null) {
                return Optional.of(list.get(0));
            }
        } catch (Exception ignored) {}

        AuthUserDTO profile = getUserProfile(userId);
        if (profile != null && profile.getEmail() != null) {
            return getAuthData(profile.getEmail()).map(AuthData::getHashPassword);
        }
        return Optional.empty();
    }

    @Override
    public boolean isInactiveAdminPropiedadWithoutProperties(Long userId) {
        if (userId == null) {
            return false;
        }
        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(" + userId + "); EXCEPTION WHEN OTHERS THEN NULL; END;");

            // 1. ¿Tiene rol ADMIN_PROPIEDAD asignado?
            Integer adminPropCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM USUARIO_ASIGNACIONES ua JOIN ROLES r ON r.ID_ROL = ua.ID_ROL " +
                "WHERE ua.ID_USUARIO = ? AND r.CODIGO = 'ADMIN_PROPIEDAD'",
                Integer.class,
                userId
            );

            if (adminPropCount == null || adminPropCount == 0) {
                return false;
            }

            // 2. ¿Tiene algún rol superior activo (SUPERADMIN, ADMIN_ORGANIZACION)?
            Integer higherRoleCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM USUARIO_ASIGNACIONES ua JOIN ROLES r ON r.ID_ROL = ua.ID_ROL " +
                "WHERE ua.ID_USUARIO = ? AND r.CODIGO IN ('SUPERADMIN', 'ADMIN_ORGANIZACION') AND ua.ESTADO IN ('ACTIVO', 'ACTIVA')",
                Integer.class,
                userId
            );

            if (higherRoleCount != null && higherRoleCount > 0) {
                return false;
            }

            // 3. ¿Tiene alguna propiedad activa asignada bajo el rol ADMIN_PROPIEDAD?
            Integer activePropCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM USUARIO_ASIGNACIONES ua JOIN ROLES r ON r.ID_ROL = ua.ID_ROL " +
                "JOIN PROPIEDADES p ON p.ID_PROPIEDAD = ua.ID_PROPIEDAD " +
                "WHERE ua.ID_USUARIO = ? AND r.CODIGO = 'ADMIN_PROPIEDAD' " +
                "AND ua.ESTADO IN ('ACTIVO', 'ACTIVA') AND p.ESTADO IN ('ACTIVO', 'ACTIVA')",
                Integer.class,
                userId
            );

            return (activePropCount == null || activePropCount == 0);
        } catch (Exception e) {
            return false;
        } finally {
            try {
                jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; EXCEPTION WHEN OTHERS THEN NULL; END;");
            } catch (Exception ignored) {}
        }
    }
}