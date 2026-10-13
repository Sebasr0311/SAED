package com.saed.backend.identity.service;

import java.sql.SQLException;
import java.sql.Types;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Registers a cohabitant account through the validated security-schema procedure (V5.66). A resident session cannot
 * insert USUARIOS / USUARIO_ASIGNACIONES rows and their visibility must not be widened, so the database validates the
 * caller (titular of the unit in the context property), the cohabitant quota, and creates the rows.
 */
public final class CohabitantAccountRegistry {
    private static final String CALL = "{call SAED_SEC_MASTER.SP_REGISTRAR_CONVIVIENTE(?, ?, ?, ?, ?, ?, ?)}";
    private static final int QUOTA_EXCEEDED = 20476;
    private static final int FIRST_REFUSAL = 20471;
    private static final int LAST_REFUSAL = 20477;

    private final NamedParameterJdbcTemplate jdbc;

    public CohabitantAccountRegistry(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Returns the id of the cohabitant's user (new, or the supplied existing one). */
    public Long register(Long unitId, Long personId, Long existingUserId, String username, String email,
            String passwordHash) {
        try {
            return jdbc.getJdbcOperations().execute(CALL, (java.sql.CallableStatement cs) -> {
                cs.setLong(1, unitId);
                cs.setLong(2, personId);
                if (existingUserId == null) {
                    cs.setNull(3, Types.NUMERIC);
                } else {
                    cs.setLong(3, existingUserId);
                }
                cs.setString(4, username);
                cs.setString(5, email);
                if (passwordHash == null) {
                    cs.setNull(6, Types.VARCHAR);
                } else {
                    cs.setString(6, passwordHash);
                }
                cs.registerOutParameter(7, Types.NUMERIC);
                cs.execute();
                return cs.getLong(7);
            });
        } catch (DataAccessException ex) {
            if (ex.getCause() instanceof SQLException sql) {
                int code = sql.getErrorCode();
                if (code == QUOTA_EXCEEDED) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Límite máximo de convivientes alcanzado para esta unidad");
                }
                if (code >= FIRST_REFUSAL && code <= LAST_REFUSAL) {
                    throw new AccessDeniedException("No tiene autorización para registrar convivientes en esta unidad");
                }
            }
            throw ex;
        }
    }
}
