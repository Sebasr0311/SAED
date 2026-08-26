package com.saed.backend.identity.repository;

import com.saed.backend.identity.model.User;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.ZoneId;
import java.util.Optional;

@Repository
public class UserRepositoryImpl implements UserRepository {

    private final JdbcTemplate jdbcTemplate;

    public UserRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<User> userRowMapper = (rs, rowNum) -> {
        User user = new User();
        user.setIdUsuario(rs.getLong("id_usuario"));
        user.setIdPersona(rs.getLong("id_persona"));
        user.setNombreUsuario(rs.getString("nombre_usuario"));
        user.setEmail(rs.getString("email"));
        user.setHashPassword(rs.getString("hash_password"));
        user.setEstado(rs.getString("estado"));
        user.setIntentosFallidos(rs.getInt("intentos_fallidos"));
        
        Timestamp bloqueo = rs.getTimestamp("fecha_bloqueo");
        if (bloqueo != null) user.setFechaBloqueo(bloqueo.toInstant().atZone(ZoneId.systemDefault()));
        
        Timestamp ultimoLogin = rs.getTimestamp("ultimo_login");
        if (ultimoLogin != null) user.setUltimoLogin(ultimoLogin.toInstant().atZone(ZoneId.systemDefault()));
        
        user.setRequiereCambioPassword(rs.getString("requiere_cambio_password"));
        return user;
    };

    @Override
    public Optional<User> findByEmail(String email) {
        try {
            // NOTE: If RLS blocks this query because the context is not set, 
            // a database change will be required to create a secure AUTH view.
            String sql = "SELECT * FROM USUARIOS WHERE email = ?";
            User user = jdbcTemplate.queryForObject(sql, userRowMapper, email);
            return Optional.ofNullable(user);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public void updateFailedAttempts(Long userId, int attempts) {
        jdbcTemplate.update("UPDATE USUARIOS SET intentos_fallidos = ? WHERE id_usuario = ?", attempts, userId);
    }

    @Override
    public void updateLastLogin(Long userId) {
        jdbcTemplate.update("UPDATE USUARIOS SET ultimo_login = CURRENT_TIMESTAMP WHERE id_usuario = ?", userId);
    }

    @Override
    public void lockUser(Long userId) {
        jdbcTemplate.update("UPDATE USUARIOS SET estado = 'BLOQUEADO', fecha_bloqueo = CURRENT_TIMESTAMP WHERE id_usuario = ?", userId);
    }
}
