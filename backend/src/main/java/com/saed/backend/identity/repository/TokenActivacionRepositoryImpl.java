package com.saed.backend.identity.repository;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Repository;

import java.sql.CallableStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.Optional;

@Repository
public class TokenActivacionRepositoryImpl implements TokenActivacionRepository {

    private static final String EMIT_CALL = "{call SAED_SEC_MASTER.SP_EMITIR_TOKEN_ACTIVACION(?, ?, ?, ?, ?, ?, ?, ?)}";
    private static final String RESEND_CALL = "{call SAED_SEC_MASTER.SP_REENVIAR_TOKEN_ACTIVACION(?, ?, ?, ?, ?, ?, ?, ?)}";
    private static final String QUERY_CALL = "{call SAED_SEC_MASTER.SP_CONSULTAR_TOKEN_ACTIVACION(?, ?, ?, ?, ?, ?, ?, ?)}";
    private static final String ACTIVATE_CALL = "{call SAED_SEC_MASTER.SP_ACTIVAR_CUENTA(?, ?, ?, ?, ?)}";
    private static final int FIRST_REFUSAL = 20501;
    private static final int LAST_REFUSAL = 20505;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public TokenActivacionRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<UsuarioInfo> emitirToken(Long idUsuario, String tipo, String tokenHash, Instant fechaExpiracion,
                                             String ip) {
        try {
            return jdbcTemplate.getJdbcOperations().execute(EMIT_CALL, (CallableStatement cs) -> {
                cs.setLong(1, idUsuario);
                cs.setString(2, tokenHash);
                cs.setString(3, tipo);
                cs.setTimestamp(4, Timestamp.from(fechaExpiracion));
                setNullable(cs, 5, ip);
                cs.registerOutParameter(6, Types.VARCHAR);
                cs.registerOutParameter(7, Types.VARCHAR);
                cs.registerOutParameter(8, Types.VARCHAR);
                cs.execute();
                return Optional.of(new UsuarioInfo(idUsuario, cs.getString(6), cs.getString(7), cs.getString(8), null));
            });
        } catch (DataAccessException ex) {
            if (isRefusal(ex)) {
                throw new AccessDeniedException("No tiene autorización para emitir un enlace de activación para esta cuenta");
            }
            throw ex;
        }
    }

    @Override
    public Optional<UsuarioInfo> emitirTokenReenvio(String identificador, String tokenHash, Instant fechaExpiracion,
                                                    String ip) {
        return jdbcTemplate.getJdbcOperations().execute(RESEND_CALL, (CallableStatement cs) -> {
            cs.setString(1, identificador);
            cs.setString(2, tokenHash);
            cs.setTimestamp(3, Timestamp.from(fechaExpiracion));
            setNullable(cs, 4, ip);
            cs.registerOutParameter(5, Types.NUMERIC);
            cs.registerOutParameter(6, Types.VARCHAR);
            cs.registerOutParameter(7, Types.VARCHAR);
            cs.registerOutParameter(8, Types.VARCHAR);
            cs.execute();
            long id = cs.getLong(5);
            if (cs.wasNull()) {
                return Optional.<UsuarioInfo>empty();
            }
            return Optional.of(new UsuarioInfo(id, cs.getString(6), cs.getString(7), cs.getString(8), null));
        });
    }

    @Override
    public Consulta consultar(String tokenHash, Instant ahora) {
        return jdbcTemplate.getJdbcOperations().execute(QUERY_CALL, (CallableStatement cs) -> {
            cs.setString(1, tokenHash);
            cs.setTimestamp(2, Timestamp.from(ahora));
            cs.registerOutParameter(3, Types.VARCHAR);
            cs.registerOutParameter(4, Types.NUMERIC);
            cs.registerOutParameter(5, Types.VARCHAR);
            cs.registerOutParameter(6, Types.VARCHAR);
            cs.registerOutParameter(7, Types.VARCHAR);
            cs.registerOutParameter(8, Types.TIMESTAMP_WITH_TIMEZONE);
            cs.execute();
            String estado = cs.getString(3);
            if (!"VALIDO".equals(estado)) {
                return new Consulta(estado, null, null);
            }
            Timestamp expira = cs.getTimestamp(8);
            UsuarioInfo user = new UsuarioInfo(cs.getLong(4), cs.getString(5), cs.getString(6), cs.getString(7), null);
            return new Consulta(estado, user, expira == null ? null : expira.toInstant());
        });
    }

    @Override
    public String activar(String tokenHash, String hashPassword, Instant ahora) {
        return jdbcTemplate.getJdbcOperations().execute(ACTIVATE_CALL, (CallableStatement cs) -> {
            cs.setString(1, tokenHash);
            cs.setString(2, hashPassword);
            cs.setTimestamp(3, Timestamp.from(ahora));
            cs.registerOutParameter(4, Types.VARCHAR);
            cs.registerOutParameter(5, Types.NUMERIC);
            cs.execute();
            return cs.getString(4);
        });
    }

    @Override
    public Optional<UsuarioInfo> obtenerUsuarioInfo(Long idUsuario) {
        String sql = """
            SELECT u.ID_USUARIO, u.NOMBRE_USUARIO, u.EMAIL, p.PRIMER_NOMBRE, u.ESTADO
            FROM USUARIOS u
            LEFT JOIN PERSONAS p ON u.ID_PERSONA = p.ID_PERSONA
            WHERE u.ID_USUARIO = :idUsuario
        """;
        return jdbcTemplate.query(sql, new MapSqlParameterSource("idUsuario", idUsuario),
                (rs, rowNum) -> new UsuarioInfo(
                        rs.getLong("ID_USUARIO"),
                        rs.getString("NOMBRE_USUARIO"),
                        rs.getString("EMAIL"),
                        rs.getString("PRIMER_NOMBRE") != null ? rs.getString("PRIMER_NOMBRE") : rs.getString("NOMBRE_USUARIO"),
                        rs.getString("ESTADO"))).stream().findFirst();
    }

    private static void setNullable(CallableStatement cs, int index, String value) throws SQLException {
        if (value == null) {
            cs.setNull(index, Types.VARCHAR);
        } else {
            cs.setString(index, value);
        }
    }

    private static boolean isRefusal(DataAccessException ex) {
        return ex.getCause() instanceof SQLException sql
                && sql.getErrorCode() >= FIRST_REFUSAL && sql.getErrorCode() <= LAST_REFUSAL;
    }
}
