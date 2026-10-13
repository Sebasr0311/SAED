package com.saed.backend.person.service;

import java.sql.SQLException;
import java.sql.Types;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

/**
 * Resolves a visitor identity through the security-schema procedure SP_RESOLVER_VISITANTE (V5.67). A visitor is a
 * person (UQ_VISITANTES_PERSONA is global) while the row-level policy only shows a caller its own visitors, so an
 * application INSERT would collide with a visitor the caller cannot see. The procedure returns only the visitor id.
 * It must be called under the caller's own tenant context, never under an elevated one.
 */
public final class VisitorIdentityRegistry {
    private static final String CALL = "{call SAED_SEC_MASTER.SP_RESOLVER_VISITANTE(?, ?, ?, ?, ?)}";
    private static final int FIRST_REFUSAL = 20481;
    private static final int LAST_REFUSAL = 20483;

    private final NamedParameterJdbcTemplate jdbc;

    public VisitorIdentityRegistry(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Existing or new visitor of a person, created in the context property when it does not exist yet. */
    public Long resolveByPerson(Long personId, boolean frequent, String company) {
        return call(null, personId, frequent, company);
    }

    /** A visitor the row-level policy hides from the caller, only when nobody has claimed it yet; else null. */
    public Long claimUnclaimed(Long visitorId) {
        return call(visitorId, null, false, null);
    }

    private Long call(Long visitorId, Long personId, boolean frequent, String company) {
        try {
            return jdbc.getJdbcOperations().execute(CALL, (java.sql.CallableStatement cs) -> {
                if (visitorId == null) {
                    cs.setNull(1, Types.NUMERIC);
                } else {
                    cs.setLong(1, visitorId);
                }
                if (personId == null) {
                    cs.setNull(2, Types.NUMERIC);
                } else {
                    cs.setLong(2, personId);
                }
                cs.setString(3, frequent ? "S" : "N");
                if (company == null) {
                    cs.setNull(4, Types.VARCHAR);
                } else {
                    cs.setString(4, company);
                }
                cs.registerOutParameter(5, Types.NUMERIC);
                cs.execute();
                long id = cs.getLong(5);
                return cs.wasNull() ? null : id;
            });
        } catch (DataAccessException ex) {
            if (ex.getCause() instanceof SQLException sql
                    && sql.getErrorCode() >= FIRST_REFUSAL && sql.getErrorCode() <= LAST_REFUSAL) {
                throw new AccessDeniedException("No tiene autorización para registrar visitantes en esta propiedad");
            }
            throw ex;
        }
    }
}
