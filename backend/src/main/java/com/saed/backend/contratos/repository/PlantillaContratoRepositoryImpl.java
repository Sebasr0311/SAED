package com.saed.backend.contratos.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.contratos.dto.PlantillaContratoDTO;
import com.saed.backend.contratos.dto.PlantillaContratoRequestDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Repository
public class PlantillaContratoRepositoryImpl implements PlantillaContratoRepository {

    private static final Logger log = LoggerFactory.getLogger(PlantillaContratoRepositoryImpl.class);

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public PlantillaContratoRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    private PlantillaContratoDTO mapRow(ResultSet rs, int rowNum) throws SQLException {
        PlantillaContratoDTO dto = new PlantillaContratoDTO();
        dto.setIdPlantilla(rs.getLong("id_plantilla"));
        dto.setIdOrganizacion(rs.getLong("id_organizacion"));
        dto.setCodigo(rs.getString("codigo"));
        dto.setNombre(rs.getString("nombre"));
        dto.setTipoContrato(rs.getString("tipo_contrato"));
        dto.setDescripcion(rs.getString("descripcion"));
        dto.setContenidoHtml(rs.getString("contenido_html"));

        String varsJson = rs.getString("variables_disponibles");
        if (varsJson != null && !varsJson.isBlank()) {
            try {
                dto.setVariablesDisponibles(objectMapper.readValue(varsJson, new TypeReference<List<String>>() {}));
            } catch (Exception e) {
                dto.setVariablesDisponibles(Collections.emptyList());
            }
        } else {
            dto.setVariablesDisponibles(Collections.emptyList());
        }

        String reqJson = rs.getString("campos_requeridos");
        if (reqJson != null && !reqJson.isBlank()) {
            try {
                dto.setCamposRequeridos(objectMapper.readValue(reqJson, new TypeReference<List<String>>() {}));
            } catch (Exception e) {
                dto.setCamposRequeridos(Collections.emptyList());
            }
        } else {
            dto.setCamposRequeridos(Collections.emptyList());
        }

        dto.setVersion(rs.getInt("version"));
        dto.setEstado(rs.getString("estado"));

        java.sql.Date vd = rs.getDate("vigencia_desde");
        if (vd != null) dto.setVigenciaDesde(vd.toLocalDate());

        java.sql.Date vh = rs.getDate("vigencia_hasta");
        if (vh != null) dto.setVigenciaHasta(vh.toLocalDate());

        long cp = rs.getLong("creado_por");
        if (!rs.wasNull()) dto.setCreadoPor(cp);

        java.sql.Timestamp fc = rs.getTimestamp("fecha_creacion");
        if (fc != null) dto.setFechaCreacion(fc.toInstant().atOffset(java.time.ZoneOffset.UTC));

        java.sql.Timestamp fa = rs.getTimestamp("fecha_actualizacion");
        if (fa != null) dto.setFechaActualizacion(fa.toInstant().atOffset(java.time.ZoneOffset.UTC));

        return dto;
    }

    @Override
    public List<PlantillaContratoDTO> findByOrganizacionId(Long orgId, String estado) {
        StringBuilder sql = new StringBuilder("""
            SELECT id_plantilla, id_organizacion, codigo, nombre, tipo_contrato, descripcion,
                   contenido_html, variables_disponibles, campos_requeridos, version, estado,
                   vigencia_desde, vigencia_hasta, creado_por, fecha_creacion, fecha_actualizacion
            FROM PLANTILLAS_CONTRATOS
            WHERE id_organizacion = :orgId
        """);

        MapSqlParameterSource params = new MapSqlParameterSource("orgId", orgId);
        if (estado != null && !estado.isBlank()) {
            sql.append(" AND estado = :estado");
            params.addValue("estado", estado.toUpperCase());
        }
        sql.append(" ORDER BY tipo_contrato, nombre, version DESC");

        return jdbcTemplate.query(sql.toString(), params, this::mapRow);
    }

    @Override
    public List<PlantillaContratoDTO> findActivasByOrganizacionId(Long orgId) {
        String sql = """
            SELECT id_plantilla, id_organizacion, codigo, nombre, tipo_contrato, descripcion,
                   contenido_html, variables_disponibles, campos_requeridos, version, estado,
                   vigencia_desde, vigencia_hasta, creado_por, fecha_creacion, fecha_actualizacion
            FROM PLANTILLAS_CONTRATOS
            WHERE id_organizacion = :orgId
              AND estado = 'ACTIVA'
              AND (vigencia_hasta IS NULL OR vigencia_hasta >= CURRENT_DATE)
            ORDER BY nombre
        """;
        return jdbcTemplate.query(sql, new MapSqlParameterSource("orgId", orgId), this::mapRow);
    }

    @Override
    public Optional<PlantillaContratoDTO> findById(Long id) {
        String sql = """
            SELECT id_plantilla, id_organizacion, codigo, nombre, tipo_contrato, descripcion,
                   contenido_html, variables_disponibles, campos_requeridos, version, estado,
                   vigencia_desde, vigencia_hasta, creado_por, fecha_creacion, fecha_actualizacion
            FROM PLANTILLAS_CONTRATOS
            WHERE id_plantilla = :id
        """;
        List<PlantillaContratoDTO> list = jdbcTemplate.query(sql, new MapSqlParameterSource("id", id), this::mapRow);
        return list.stream().findFirst();
    }

    @Override
    public Optional<PlantillaContratoDTO> findByCodigoAndVersion(Long orgId, String codigo, Integer version) {
        String sql = """
            SELECT id_plantilla, id_organizacion, codigo, nombre, tipo_contrato, descripcion,
                   contenido_html, variables_disponibles, campos_requeridos, version, estado,
                   vigencia_desde, vigencia_hasta, creado_por, fecha_creacion, fecha_actualizacion
            FROM PLANTILLAS_CONTRATOS
            WHERE id_organizacion = :orgId AND codigo = :codigo AND version = :version
        """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("orgId", orgId)
                .addValue("codigo", codigo)
                .addValue("version", version);
        List<PlantillaContratoDTO> list = jdbcTemplate.query(sql, params, this::mapRow);
        return list.stream().findFirst();
    }

    @Override
    public Long create(PlantillaContratoRequestDTO dto, Long orgId, Long userId) {
        String sql = """
            INSERT INTO PLANTILLAS_CONTRATOS (
                id_organizacion, codigo, nombre, tipo_contrato, descripcion,
                contenido_html, variables_disponibles, campos_requeridos, version,
                estado, vigencia_desde, vigencia_hasta, creado_por
            ) VALUES (
                :orgId, :codigo, :nombre, :tipoContrato, :descripcion,
                :contenidoHtml, :variables, :camposRequeridos, :version,
                :estado, :vigenciaDesde, :vigenciaHasta, :userId
            )
        """;

        String varsJson = null;
        try {
            if (dto.getVariablesDisponibles() != null) {
                varsJson = objectMapper.writeValueAsString(dto.getVariablesDisponibles());
            }
        } catch (Exception ignored) {}

        String reqJson = null;
        try {
            if (dto.getCamposRequeridos() != null) {
                reqJson = objectMapper.writeValueAsString(dto.getCamposRequeridos());
            }
        } catch (Exception ignored) {}

        int ver = (dto.getVersion() != null && dto.getVersion() > 0) ? dto.getVersion() : 1;
        String est = (dto.getEstado() != null && !dto.getEstado().isBlank()) ? dto.getEstado().toUpperCase() : "ACTIVA";
        LocalDate desde = (dto.getVigenciaDesde() != null) ? dto.getVigenciaDesde() : LocalDate.now();

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("orgId", orgId)
                .addValue("codigo", dto.getCodigo().toUpperCase())
                .addValue("nombre", dto.getNombre())
                .addValue("tipoContrato", dto.getTipoContrato().toUpperCase())
                .addValue("descripcion", dto.getDescripcion())
                .addValue("contenidoHtml", dto.getContenidoHtml())
                .addValue("variables", varsJson)
                .addValue("camposRequeridos", reqJson)
                .addValue("version", ver)
                .addValue("estado", est)
                .addValue("vigenciaDesde", java.sql.Date.valueOf(desde))
                .addValue("vigenciaHasta", dto.getVigenciaHasta() != null ? java.sql.Date.valueOf(dto.getVigenciaHasta()) : null)
                .addValue("userId", userId);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, params, keyHolder, new String[]{"ID_PLANTILLA"});
        return keyHolder.getKey().longValue();
    }

    @Override
    public void update(Long id, PlantillaContratoRequestDTO dto) {
        String sql = """
            UPDATE PLANTILLAS_CONTRATOS SET
                nombre = :nombre,
                tipo_contrato = :tipoContrato,
                descripcion = :descripcion,
                contenido_html = :contenidoHtml,
                variables_disponibles = :variables,
                campos_requeridos = :camposRequeridos,
                vigencia_desde = :vigenciaDesde,
                vigencia_hasta = :vigenciaHasta,
                fecha_actualizacion = CURRENT_TIMESTAMP
            WHERE id_plantilla = :id
        """;

        String varsJson = null;
        try {
            if (dto.getVariablesDisponibles() != null) {
                varsJson = objectMapper.writeValueAsString(dto.getVariablesDisponibles());
            }
        } catch (Exception ignored) {}

        String reqJson = null;
        try {
            if (dto.getCamposRequeridos() != null) {
                reqJson = objectMapper.writeValueAsString(dto.getCamposRequeridos());
            }
        } catch (Exception ignored) {}

        LocalDate desde = (dto.getVigenciaDesde() != null) ? dto.getVigenciaDesde() : LocalDate.now();

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("nombre", dto.getNombre())
                .addValue("tipoContrato", dto.getTipoContrato().toUpperCase())
                .addValue("descripcion", dto.getDescripcion())
                .addValue("contenidoHtml", dto.getContenidoHtml())
                .addValue("variables", varsJson)
                .addValue("camposRequeridos", reqJson)
                .addValue("vigenciaDesde", java.sql.Date.valueOf(desde))
                .addValue("vigenciaHasta", dto.getVigenciaHasta() != null ? java.sql.Date.valueOf(dto.getVigenciaHasta()) : null);

        jdbcTemplate.update(sql, params);
    }

    @Override
    public void updateStatus(Long id, String estado) {
        String sql = "UPDATE PLANTILLAS_CONTRATOS SET estado = :estado, fecha_actualizacion = CURRENT_TIMESTAMP WHERE id_plantilla = :id";
        jdbcTemplate.update(sql, new MapSqlParameterSource("id", id).addValue("estado", estado.toUpperCase()));
    }

    @Override
    public Integer getMaxVersion(Long orgId, String codigo) {
        String sql = "SELECT COALESCE(MAX(version), 0) FROM PLANTILLAS_CONTRATOS WHERE id_organizacion = :orgId AND codigo = :codigo";
        Integer max = jdbcTemplate.queryForObject(sql, new MapSqlParameterSource("orgId", orgId).addValue("codigo", codigo), Integer.class);
        return max != null ? max : 0;
    }
}
