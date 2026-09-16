package com.saed.backend.authorization.repository;

import com.saed.backend.authorization.dto.PropertyConfigDTO;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class PropertyConfigRepositoryImpl implements PropertyConfigRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PropertyConfigRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final String BASE_SELECT = """
        SELECT id_prop_config, id_propiedad, clave, valor, descripcion,
               TO_CHAR(fecha_actualizacion, 'YYYY-MM-DD"T"HH24:MI:SS.FF3TZH:TZM') AS fecha_actualizacion_str
        FROM PROPIEDAD_CONFIGURACION
    """;

    @Override
    public List<PropertyConfigDTO> findByPropertyId(Long propertyId) {
        String sql = BASE_SELECT + " WHERE id_propiedad = :propId ORDER BY clave ASC";
        return jdbcTemplate.query(sql, new MapSqlParameterSource("propId", propertyId), this::mapRow);
    }

    @Override
    public Optional<PropertyConfigDTO> findByPropertyIdAndKey(Long propertyId, String clave) {
        String sql = BASE_SELECT + " WHERE id_propiedad = :propId AND clave = :clave";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("propId", propertyId)
                .addValue("clave", clave);
        List<PropertyConfigDTO> list = jdbcTemplate.query(sql, params, this::mapRow);
        return list.stream().findFirst();
    }

    @Override
    public void saveOrUpdate(Long propertyId, String clave, String valor, String descripcion) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("propId", propertyId)
                .addValue("clave", clave.trim().toUpperCase())
                .addValue("valor", valor.trim())
                .addValue("descripcion", descripcion != null ? descripcion.trim() : null);

        String updateSql = """
            UPDATE PROPIEDAD_CONFIGURACION
            SET valor = :valor,
                descripcion = COALESCE(:descripcion, descripcion),
                fecha_actualizacion = FROM_TZ(CAST(SYSTIMESTAMP AS TIMESTAMP), 'America/Bogota')
            WHERE id_propiedad = :propId AND clave = :clave
        """;
        int updated = jdbcTemplate.update(updateSql, params);
        if (updated == 0) {
            String insertSql = """
                INSERT INTO PROPIEDAD_CONFIGURACION (id_propiedad, clave, valor, descripcion)
                VALUES (:propId, :clave, :valor, :descripcion)
            """;
            jdbcTemplate.update(insertSql, params);
        }
    }

    @Override
    public void delete(Long propertyId, String clave) {
        String sql = "DELETE FROM PROPIEDAD_CONFIGURACION WHERE id_propiedad = :propId AND clave = :clave";
        jdbcTemplate.update(sql, new MapSqlParameterSource("propId", propertyId).addValue("clave", clave));
    }

    private PropertyConfigDTO mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        PropertyConfigDTO dto = new PropertyConfigDTO();
        dto.setId(rs.getLong("id_prop_config"));
        dto.setIdPropiedad(rs.getLong("id_propiedad"));
        dto.setClave(rs.getString("clave"));
        dto.setValor(rs.getString("valor"));
        dto.setDescripcion(rs.getString("descripcion"));
        dto.setFechaActualizacion(rs.getString("fecha_actualizacion_str"));
        return dto;
    }
}
