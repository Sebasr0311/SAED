package com.saed.backend.authorization.repository;

import com.saed.backend.authorization.dto.PropertyDTO;
import com.saed.backend.authorization.dto.PropertyRequestDTO;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class PropertyRepositoryImpl implements PropertyRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PropertyRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Long create(PropertyRequestDTO request) {
        String sql = "INSERT INTO PROPIEDADES (id_organizacion, id_tipo_propiedad, nombre, direccion, ciudad, tipo_ocupacion_predominante) " +
                     "VALUES (:idOrganizacion, :idTipoPropiedad, :nombre, :direccion, :ciudad, :tipoOcupacion)";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("idOrganizacion", request.getIdOrganizacion())
                .addValue("idTipoPropiedad", request.getIdTipoPropiedad())
                .addValue("nombre", request.getNombre())
                .addValue("direccion", request.getDireccion())
                .addValue("ciudad", request.getCiudad())
                .addValue("tipoOcupacion", request.getTipoOcupacionPredominante());

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, params, keyHolder, new String[]{"ID_PROPIEDAD"});
        return keyHolder.getKey().longValue();
    }

    @Override
    public Optional<PropertyDTO> findById(Long id) {
        String sql = "SELECT id_propiedad, id_organizacion, nombre, direccion, ciudad, estado " +
                     "FROM PROPIEDADES WHERE id_propiedad = :id";
        List<PropertyDTO> results = jdbcTemplate.query(sql, new MapSqlParameterSource("id", id), (rs, rowNum) -> {
            PropertyDTO dto = new PropertyDTO();
            dto.setId(rs.getLong("id_propiedad"));
            dto.setNombre(rs.getString("nombre"));
            return dto;
        });
        return results.stream().findFirst();
    }

    @Override
    public List<PropertyDTO> findAll() {
        String sql = "SELECT id_propiedad, nombre, estado FROM PROPIEDADES";
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            PropertyDTO dto = new PropertyDTO();
            dto.setId(rs.getLong("id_propiedad"));
            dto.setNombre(rs.getString("nombre"));
            return dto;
        });
    }

    @Override
    public void update(Long id, PropertyRequestDTO request) {
        String sql = "UPDATE PROPIEDADES SET nombre = :nombre, direccion = :direccion, ciudad = :ciudad, " +
                     "tipo_ocupacion_predominante = :tipoOcupacion WHERE id_propiedad = :id";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("nombre", request.getNombre())
                .addValue("direccion", request.getDireccion())
                .addValue("ciudad", request.getCiudad())
                .addValue("tipoOcupacion", request.getTipoOcupacionPredominante());
        jdbcTemplate.update(sql, params);
    }
}
