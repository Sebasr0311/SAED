package com.saed.backend.authorization.repository;

import com.saed.backend.authorization.dto.RoleDTO;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class RoleRepositoryImpl implements RoleRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public RoleRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<RoleDTO> findById(Long id) {
        String sql = "SELECT id_rol, codigo, nombre, alcance, estado FROM ROLES WHERE id_rol = :id AND estado = 'ACTIVO'";
        List<RoleDTO> results = jdbcTemplate.query(sql, new MapSqlParameterSource("id", id), (rs, rowNum) -> {
            RoleDTO dto = new RoleDTO();
            dto.setIdRol(rs.getLong("id_rol"));
            dto.setCodigo(rs.getString("codigo"));
            dto.setAlcance(rs.getString("alcance"));
            return dto;
        });
        return results.stream().findFirst();
    }
}
