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

    private static final String BASE_SELECT = """
        SELECT p.id_propiedad, p.id_organizacion, p.id_tipo_propiedad, p.nombre,
               p.direccion, p.ciudad, p.tipo_ocupacion_predominante, p.estado,
               o.nombre AS organizacion_nombre,
               tp.codigo AS tipo_propiedad_codigo, tp.nombre AS tipo_propiedad_nombre
        FROM PROPIEDADES p
        LEFT JOIN ORGANIZACIONES o ON o.id_organizacion = p.id_organizacion
        LEFT JOIN TIPOS_PROPIEDAD tp ON tp.id_tipo_propiedad = p.id_tipo_propiedad
    """;

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
        String sql = BASE_SELECT + " WHERE p.id_propiedad = :id";
        List<PropertyDTO> results = jdbcTemplate.query(sql, new MapSqlParameterSource("id", id), this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public List<PropertyDTO> findAll() {
        return jdbcTemplate.query(BASE_SELECT + " ORDER BY p.nombre", this::mapRow);
    }

    private PropertyDTO mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        PropertyDTO dto = new PropertyDTO();
        dto.setId(rs.getLong("id_propiedad"));
        dto.setIdOrganizacion(rs.getLong("id_organizacion"));
        long idTipo = rs.getLong("id_tipo_propiedad");
        if (!rs.wasNull()) dto.setIdTipoPropiedad(idTipo);
        dto.setNombre(rs.getString("nombre"));
        dto.setDireccion(rs.getString("direccion"));
        dto.setCiudad(rs.getString("ciudad"));
        dto.setTipoOcupacionPredominante(rs.getString("tipo_ocupacion_predominante"));
        dto.setEstado(rs.getString("estado"));
        dto.setOrganizacionNombre(rs.getString("organizacion_nombre"));
        dto.setTipoPropiedadCodigo(rs.getString("tipo_propiedad_codigo"));
        dto.setTipoPropiedadNombre(rs.getString("tipo_propiedad_nombre"));
        return dto;
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
