package com.saed.backend.emergencias.repository.impl;

import com.saed.backend.emergencias.dto.ContactoEmergenciaDTO;
import com.saed.backend.emergencias.dto.ContactoEmergenciaRequestDTO;
import com.saed.backend.emergencias.repository.ContactoEmergenciaRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
public class ContactoEmergenciaRepositoryImpl implements ContactoEmergenciaRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ContactoEmergenciaRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<ContactoEmergenciaDTO> rowMapper = new RowMapper<ContactoEmergenciaDTO>() {
        @Override
        public ContactoEmergenciaDTO mapRow(ResultSet rs, int rowNum) throws SQLException {
            ContactoEmergenciaDTO dto = new ContactoEmergenciaDTO();
            dto.setIdContactoEmergencia(rs.getLong("ID_CONTACTO_EMERGENCIA"));
            dto.setIdPropiedad(rs.getLong("ID_PROPIEDAD"));
            dto.setEntidad(rs.getString("ENTIDAD"));
            dto.setTipoServicio(rs.getString("TIPO_SERVICIO"));
            dto.setTelefonoPrincipal(rs.getString("TELEFONO_PRINCIPAL"));
            dto.setTelefonoAlterno(rs.getString("TELEFONO_ALTERNO"));
            dto.setDireccion(rs.getString("DIRECCION"));
            dto.setEsPrioritarioMinuta(rs.getString("ES_PRIORITARIO_MINUTA"));
            dto.setOrdenVisualizacion(rs.getInt("ORDEN_VISUALIZACION"));
            return dto;
        }
    };

    @Override
    public List<ContactoEmergenciaDTO> findAllByPropiedad(Long idPropiedad) {
        String sql = "SELECT * FROM CONTACTOS_EMERGENCIA WHERE ID_PROPIEDAD = :idPropiedad ORDER BY ORDEN_VISUALIZACION ASC, ID_CONTACTO_EMERGENCIA ASC";
        MapSqlParameterSource params = new MapSqlParameterSource("idPropiedad", idPropiedad);
        return jdbcTemplate.query(sql, params, rowMapper);
    }

    @Override
    public List<ContactoEmergenciaDTO> findPrioritariosByPropiedad(Long idPropiedad) {
        String sql = "SELECT * FROM CONTACTOS_EMERGENCIA WHERE ID_PROPIEDAD = :idPropiedad AND ES_PRIORITARIO_MINUTA = 'S' ORDER BY ORDEN_VISUALIZACION ASC, ID_CONTACTO_EMERGENCIA ASC";
        MapSqlParameterSource params = new MapSqlParameterSource("idPropiedad", idPropiedad);
        return jdbcTemplate.query(sql, params, rowMapper);
    }

    @Override
    public Optional<ContactoEmergenciaDTO> findByIdAndPropiedad(Long idContactoEmergencia, Long idPropiedad) {
        String sql = "SELECT * FROM CONTACTOS_EMERGENCIA WHERE ID_CONTACTO_EMERGENCIA = :idContacto AND ID_PROPIEDAD = :idPropiedad";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("idContacto", idContactoEmergencia)
                .addValue("idPropiedad", idPropiedad);
        List<ContactoEmergenciaDTO> list = jdbcTemplate.query(sql, params, rowMapper);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public Long insert(Long idPropiedad, ContactoEmergenciaRequestDTO dto) {
        String sql = "INSERT INTO CONTACTOS_EMERGENCIA (ID_PROPIEDAD, ENTIDAD, TIPO_SERVICIO, TELEFONO_PRINCIPAL, " +
                     "TELEFONO_ALTERNO, DIRECCION, ES_PRIORITARIO_MINUTA, ORDEN_VISUALIZACION) " +
                     "VALUES (:idPropiedad, :entidad, :tipoServicio, :telPrincipal, :telAlterno, :direccion, :prioritario, :orden)";

        String prioritario = (dto.getEsPrioritarioMinuta() != null && "S".equalsIgnoreCase(dto.getEsPrioritarioMinuta())) ? "S" : "N";
        int orden = (dto.getOrdenVisualizacion() != null && dto.getOrdenVisualizacion() > 0) ? dto.getOrdenVisualizacion() : 1;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("idPropiedad", idPropiedad)
                .addValue("entidad", dto.getEntidad())
                .addValue("tipoServicio", dto.getTipoServicio().toUpperCase())
                .addValue("telPrincipal", dto.getTelefonoPrincipal())
                .addValue("telAlterno", dto.getTelefonoAlterno())
                .addValue("direccion", dto.getDireccion())
                .addValue("prioritario", prioritario)
                .addValue("orden", orden);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, params, keyHolder, new String[]{"ID_CONTACTO_EMERGENCIA"});
        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : null;
    }

    @Override
    public void update(Long idContactoEmergencia, Long idPropiedad, ContactoEmergenciaRequestDTO dto) {
        String sql = "UPDATE CONTACTOS_EMERGENCIA SET ENTIDAD = :entidad, TIPO_SERVICIO = :tipoServicio, " +
                     "TELEFONO_PRINCIPAL = :telPrincipal, TELEFONO_ALTERNO = :telAlterno, DIRECCION = :direccion, " +
                     "ES_PRIORITARIO_MINUTA = :prioritario, ORDEN_VISUALIZACION = :orden " +
                     "WHERE ID_CONTACTO_EMERGENCIA = :idContacto AND ID_PROPIEDAD = :idPropiedad";

        String prioritario = (dto.getEsPrioritarioMinuta() != null && "S".equalsIgnoreCase(dto.getEsPrioritarioMinuta())) ? "S" : "N";
        int orden = (dto.getOrdenVisualizacion() != null && dto.getOrdenVisualizacion() > 0) ? dto.getOrdenVisualizacion() : 1;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("idContacto", idContactoEmergencia)
                .addValue("idPropiedad", idPropiedad)
                .addValue("entidad", dto.getEntidad())
                .addValue("tipoServicio", dto.getTipoServicio().toUpperCase())
                .addValue("telPrincipal", dto.getTelefonoPrincipal())
                .addValue("telAlterno", dto.getTelefonoAlterno())
                .addValue("direccion", dto.getDireccion())
                .addValue("prioritario", prioritario)
                .addValue("orden", orden);

        jdbcTemplate.update(sql, params);
    }

    @Override
    public void delete(Long idContactoEmergencia, Long idPropiedad) {
        String sql = "DELETE FROM CONTACTOS_EMERGENCIA WHERE ID_CONTACTO_EMERGENCIA = :idContacto AND ID_PROPIEDAD = :idPropiedad";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("idContacto", idContactoEmergencia)
                .addValue("idPropiedad", idPropiedad);
        jdbcTemplate.update(sql, params);
    }

    @Override
    public int countTotalByPropiedad(Long idPropiedad) {
        String sql = "SELECT COUNT(*) FROM CONTACTOS_EMERGENCIA WHERE ID_PROPIEDAD = :idPropiedad";
        Integer count = jdbcTemplate.queryForObject(sql, new MapSqlParameterSource("idPropiedad", idPropiedad), Integer.class);
        return count != null ? count : 0;
    }

    @Override
    public int countPrioritariosByPropiedad(Long idPropiedad) {
        String sql = "SELECT COUNT(*) FROM CONTACTOS_EMERGENCIA WHERE ID_PROPIEDAD = :idPropiedad AND ES_PRIORITARIO_MINUTA = 'S'";
        Integer count = jdbcTemplate.queryForObject(sql, new MapSqlParameterSource("idPropiedad", idPropiedad), Integer.class);
        return count != null ? count : 0;
    }
}
