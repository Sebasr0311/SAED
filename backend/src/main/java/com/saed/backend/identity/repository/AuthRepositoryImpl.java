package com.saed.backend.identity.repository;

import com.saed.backend.identity.dto.AuthData;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlOutParameter;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.util.Map;
import java.util.Optional;

@Repository
public class AuthRepositoryImpl implements AuthRepository {

    private final JdbcTemplate jdbcTemplate;
    private final SimpleJdbcCall getAuthDataCall;
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
    public Optional<AuthData> getAuthData(String email) {
        Map<String, Object> out = getAuthDataCall.execute(Map.of("p_email", email));
        
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
}
