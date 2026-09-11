package com.saed.backend.identity.repository;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Repository
public class TokenActivacionRepositoryImpl implements TokenActivacionRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public TokenActivacionRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Long guardarToken(Long idUsuario, String tokenHash, String tipo, Instant fechaExpiracion, String ip) {
        String sql = """
            INSERT INTO TOKENS_ACTIVACION (ID_USUARIO, TOKEN_HASH, TIPO, FECHA_EXPIRACION, USADO, IP_SOLICITUD)
            VALUES (:idUsuario, :tokenHash, :tipo, :fechaExpiracion, 0, :ip)
        """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("idUsuario", idUsuario)
                .addValue("tokenHash", tokenHash)
                .addValue("tipo", tipo)
                .addValue("fechaExpiracion", Timestamp.from(fechaExpiracion))
                .addValue("ip", ip);

        KeyHolder kh = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, params, kh, new String[]{"ID_TOKEN"});
        Number key = kh.getKey();
        return key != null ? key.longValue() : null;
    }

    @Override
    public Optional<TokenRegistro> buscarPorHash(String tokenHash) {
        String sql = """
            SELECT ID_TOKEN, ID_USUARIO, TOKEN_HASH, TIPO, FECHA_EXPIRACION, USADO
            FROM TOKENS_ACTIVACION
            WHERE TOKEN_HASH = :hash
        """;
        try {
            TokenRegistro registro = jdbcTemplate.queryForObject(
                    sql,
                    new MapSqlParameterSource("hash", tokenHash),
                    (rs, rowNum) -> new TokenRegistro(
                            rs.getLong("ID_TOKEN"),
                            rs.getLong("ID_USUARIO"),
                            rs.getString("TOKEN_HASH"),
                            rs.getString("TIPO"),
                            rs.getTimestamp("FECHA_EXPIRACION").toInstant(),
                            rs.getInt("USADO") == 1
                    )
            );
            return Optional.ofNullable(registro);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public void marcarComoUsado(Long idToken) {
        String sql = """
            UPDATE TOKENS_ACTIVACION
            SET USADO = 1, FECHA_USO = CURRENT_TIMESTAMP
            WHERE ID_TOKEN = :idToken
        """;
        jdbcTemplate.update(sql, new MapSqlParameterSource("idToken", idToken));
    }

    @Override
    public void invalidarTokensPrevios(Long idUsuario, String tipo) {
        String sql = """
            UPDATE TOKENS_ACTIVACION
            SET USADO = 1, FECHA_USO = CURRENT_TIMESTAMP
            WHERE ID_USUARIO = :idUsuario AND TIPO = :tipo AND USADO = 0
        """;
        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("idUsuario", idUsuario)
                .addValue("tipo", tipo));
    }

    @Override
    public Optional<UsuarioInfo> obtenerUsuarioInfo(Long idUsuario) {
        String sql = """
            SELECT u.ID_USUARIO, u.NOMBRE_USUARIO, u.EMAIL, p.PRIMER_NOMBRE, u.ESTADO
            FROM USUARIOS u
            LEFT JOIN PERSONAS p ON u.ID_PERSONA = p.ID_PERSONA
            WHERE u.ID_USUARIO = :idUsuario
        """;
        try {
            UsuarioInfo info = jdbcTemplate.queryForObject(
                    sql,
                    new MapSqlParameterSource("idUsuario", idUsuario),
                    (rs, rowNum) -> new UsuarioInfo(
                            rs.getLong("ID_USUARIO"),
                            rs.getString("NOMBRE_USUARIO"),
                            rs.getString("EMAIL"),
                            rs.getString("PRIMER_NOMBRE") != null ? rs.getString("PRIMER_NOMBRE") : rs.getString("NOMBRE_USUARIO"),
                            rs.getString("ESTADO")
                    )
            );
            return Optional.ofNullable(info);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<Long> buscarIdUsuarioPorIdentificador(String identificador) {
        String sql = """
            SELECT ID_USUARIO
            FROM USUARIOS
            WHERE (LOWER(NOMBRE_USUARIO) = LOWER(:id) OR LOWER(EMAIL) = LOWER(:id))
              AND ROWNUM = 1
        """;
        try {
            Long id = jdbcTemplate.queryForObject(
                    sql,
                    new MapSqlParameterSource("id", identificador.trim()),
                    Long.class
            );
            return Optional.ofNullable(id);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public void actualizarPasswordYActivar(Long idUsuario, String hashPassword) {
        String sql = """
            UPDATE USUARIOS
            SET HASH_PASSWORD = :hash, ESTADO = 'ACTIVO', INTENTOS_FALLIDOS = 0
            WHERE ID_USUARIO = :idUsuario
        """;
        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("idUsuario", idUsuario)
                .addValue("hash", hashPassword));
    }
}
