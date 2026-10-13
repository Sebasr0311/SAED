package com.saed.backend.identity.repository;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
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

        String rolCodigo = (String) out.get("p_rol_codigo");
        String alcance = (String) out.get("p_alcance");
        Number unidadIdNum = (Number) out.get("p_unidad_id");
        Long unidadId = unidadIdNum != null ? unidadIdNum.longValue() : null;
        Number propIdNum = (Number) out.get("p_prop_id");
        Long propId = propIdNum != null ? propIdNum.longValue() : null;
        Number orgIdNum = (Number) out.get("p_org_id");
        Long orgId = orgIdNum != null ? orgIdNum.longValue() : null;
        Long idPersona = null;
        String nombreCompleto = null;
        String telefono = null;
        String tipoResidente = null;
        String identificadorUnidad = null;

        SaedContext previousContext = SaedContextHolder.getContext();
        try {
            SaedContextHolder.setContext(SaedContext.builder().userId(userId).build());
            // Bootstrap and enrichment are optional. Neither may change authorization.
            try {
                jdbcTemplate.update("CALL PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(?)", userId);
                List<Map<String, Object>> personas = jdbcTemplate.queryForList(
                        "SELECT p.ID_PERSONA, p.TELEFONO, "
                                + "TRIM(p.PRIMER_NOMBRE || ' ' || NVL(p.SEGUNDO_NOMBRE, '') || ' ' "
                                + "|| p.PRIMER_APELLIDO || ' ' || NVL(p.SEGUNDO_APELLIDO, '')) AS NOMBRE_COMPLETO "
                                + "FROM USUARIOS u JOIN PERSONAS p ON u.ID_PERSONA = p.ID_PERSONA WHERE u.ID_USUARIO = ?",
                        userId);
                if (!personas.isEmpty()) {
                    Map<String, Object> persona = personas.get(0);
                    Number personaIdNum = (Number) persona.get("ID_PERSONA");
                    idPersona = personaIdNum != null ? personaIdNum.longValue() : null;
                    telefono = (String) persona.get("TELEFONO");
                    nombreCompleto = (String) persona.get("NOMBRE_COMPLETO");
                    if (nombreCompleto != null) {
                        nombreCompleto = nombreCompleto.replaceAll("\\s+", " ").trim();
                    }
                }
                if (idPersona != null && unidadId != null) {
                    List<String> tipos = jdbcTemplate.query(
                            "SELECT TIPO_RESIDENTE FROM RESIDENTES_UNIDAD WHERE ID_PERSONA = ? AND ID_UNIDAD = ? "
                                    + "AND ESTADO IN ('ACTIVO', 'ACTIVA') "
                                    + "ORDER BY ID_RESIDENTE_UNIDAD DESC FETCH FIRST 1 ROWS ONLY",
                            (rs, rowNum) -> rs.getString("TIPO_RESIDENTE"), idPersona, unidadId);
                    if (!tipos.isEmpty()) {
                        tipoResidente = tipos.get(0);
                    }
                }
                if (unidadId != null) {
                    List<Map<String, Object>> unidades = jdbcTemplate.queryForList(
                            "SELECT IDENTIFICADOR FROM UNIDADES WHERE ID_UNIDAD = ?", unidadId);
                    if (!unidades.isEmpty()) {
                        identificadorUnidad = (String) unidades.get(0).get("IDENTIFICADOR");
                    }
                }
            } catch (Exception ignored) {
                // Missing descriptive data does not replace the package-selected assignment.
            }
            return new AuthUserDTO(userId, idPersona, nombreUsuario, nombreCompleto, (String) out.get("p_email"),
                    rolCodigo, alcance, orgId, propId, unidadId, tipoResidente, identificadorUnidad, telefono);
        } finally {
            if (previousContext != null) {
                SaedContextHolder.setContext(previousContext);
            } else {
                SaedContextHolder.clearContext();
            }
        }
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