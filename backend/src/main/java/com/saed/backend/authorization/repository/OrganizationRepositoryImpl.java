package com.saed.backend.authorization.repository;

import com.saed.backend.authorization.dto.OrganizationDTO;
import com.saed.backend.authorization.dto.OrganizationRequestDTO;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository("phase1cOrganizationRepository")
public class OrganizationRepositoryImpl implements OrganizationRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public OrganizationRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Long create(OrganizationRequestDTO request, Long creadoPor) {
        String sql = "INSERT INTO ORGANIZACIONES (nombre, identificacion_fiscal, email_contacto, " +
                     "telefono_contacto, direccion, ciudad, creado_por) " +
                     "VALUES (:nombre, :identificacionFiscal, :emailContacto, " +
                     ":telefonoContacto, :direccion, :ciudad, :creadoPor)";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("nombre", request.getNombre())
                .addValue("identificacionFiscal", request.getIdentificacionFiscal())
                .addValue("emailContacto", request.getEmailContacto())
                .addValue("telefonoContacto", request.getTelefonoContacto())
                .addValue("direccion", request.getDireccion())
                .addValue("ciudad", request.getCiudad())
                .addValue("creadoPor", creadoPor);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, params, keyHolder, new String[]{"ID_ORGANIZACION"});
        return keyHolder.getKey().longValue();
    }

    @Override
    public Optional<OrganizationDTO> findById(Long id) {
        String sql = "SELECT id_organizacion, nombre, identificacion_fiscal, email_contacto, " +
                     "telefono_contacto, direccion, ciudad, pais, estado " +
                     "FROM ORGANIZACIONES WHERE id_organizacion = :id";
        List<OrganizationDTO> results = jdbcTemplate.query(sql, new MapSqlParameterSource("id", id), (rs, rowNum) -> {
            OrganizationDTO dto = new OrganizationDTO();
            dto.setId(rs.getLong("id_organizacion"));
            dto.setNombre(rs.getString("nombre"));
            dto.setIdentificacionFiscal(rs.getString("identificacion_fiscal"));
            dto.setEmailContacto(rs.getString("email_contacto"));
            dto.setTelefonoContacto(rs.getString("telefono_contacto"));
            dto.setDireccion(rs.getString("direccion"));
            dto.setCiudad(rs.getString("ciudad"));
            dto.setPais(rs.getString("pais"));
            dto.setEstado(rs.getString("estado"));
            return dto;
        });
        return results.stream().findFirst();
    }

    @Override
    public List<OrganizationDTO> findAll() {
        String sql = "SELECT id_organizacion, nombre, identificacion_fiscal, estado FROM ORGANIZACIONES";
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            OrganizationDTO dto = new OrganizationDTO();
            dto.setId(rs.getLong("id_organizacion"));
            dto.setNombre(rs.getString("nombre"));
            dto.setIdentificacionFiscal(rs.getString("identificacion_fiscal"));
            dto.setEstado(rs.getString("estado"));
            return dto;
        });
    }

    @Override
    public void update(Long id, OrganizationRequestDTO request) {
        String sql = "UPDATE ORGANIZACIONES SET nombre = :nombre, identificacion_fiscal = :identificacionFiscal, " +
                     "email_contacto = :emailContacto, telefono_contacto = :telefonoContacto, " +
                     "direccion = :direccion, ciudad = :ciudad " +
                     "WHERE id_organizacion = :id";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("nombre", request.getNombre())
                .addValue("identificacionFiscal", request.getIdentificacionFiscal())
                .addValue("emailContacto", request.getEmailContacto())
                .addValue("telefonoContacto", request.getTelefonoContacto())
                .addValue("direccion", request.getDireccion())
                .addValue("ciudad", request.getCiudad());
        jdbcTemplate.update(sql, params);
    }

    @Override
    public void updateStatus(Long id, String status) {
        String sql = "UPDATE ORGANIZACIONES SET estado = :estado WHERE id_organizacion = :id";
        jdbcTemplate.update(sql, new MapSqlParameterSource("id", id).addValue("estado", status));
    }
}
