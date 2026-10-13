package com.saed.backend.identity.service;

import java.sql.SQLException;
import java.sql.Types;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

/**
 * Creates the account row of a new identity through the validated security-schema procedure (V5.68). The USUARIOS
 * row-level policy only admits accounts that already hold an assignment in the organization, so an organization or
 * property administrator cannot insert the first row of a new account; the database validates the caller (an active
 * administrator of the context organization/property) and creates it. The assignment is inserted afterwards by the
 * administrator under the row-level policies.
 */
public final class AdministratorAccountRegistry {
    private static final String CALL = "{call SAED_SEC_MASTER.SP_REGISTRAR_CUENTA(?, ?, ?, ?, ?)}";
    private static final int FIRST_REFUSAL = 20491;
    private static final int LAST_REFUSAL = 20495;

    private final NamedParameterJdbcTemplate jdbc;

    public AdministratorAccountRegistry(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Returns the id of the new user. */
    public Long create(Long personId, String username, String email, String passwordHash) {
        try {
            return jdbc.getJdbcOperations().execute(CALL, (java.sql.CallableStatement cs) -> {
                cs.setLong(1, personId);
                cs.setString(2, username);
                if (email == null) {
                    cs.setNull(3, Types.VARCHAR);
                } else {
                    cs.setString(3, email);
                }
                cs.setString(4, passwordHash);
                cs.registerOutParameter(5, Types.NUMERIC);
                cs.execute();
                return cs.getLong(5);
            });
        } catch (DataAccessException ex) {
            if (ex.getCause() instanceof SQLException sql
                    && sql.getErrorCode() >= FIRST_REFUSAL && sql.getErrorCode() <= LAST_REFUSAL) {
                throw new AccessDeniedException("No tiene autorización para crear esta cuenta");
            }
            throw ex;
        }
    }
}
