package com.saed.backend.activos.repository.impl;

import com.saed.backend.activos.dto.ActivoCreateDTO;
import com.saed.backend.activos.dto.ActivoDTO;
import com.saed.backend.activos.dto.ActivoUpdateDTO;
import com.saed.backend.activos.repository.ActivoRepository;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class ActivoRepositoryImpl implements ActivoRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public ActivoRepositoryImpl(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private final RowMapper<ActivoDTO> rowMapper = new RowMapper<>() {
        @Override
        public ActivoDTO mapRow(ResultSet rs, int rowNum) throws SQLException {
            Date sqlDate = rs.getDate("FECHA_ADQUISICION");
            LocalDate fechaAdquisicion = (sqlDate != null) ? sqlDate.toLocalDate() : null;

            return new ActivoDTO(
                rs.getLong("ID_ACTIVO"),
                rs.getLong("ID_PROPIEDAD"),
                rs.getString("CODIGO_ACTIVO"),
                rs.getString("NOMBRE"),
                rs.getString("CATEGORIA"),
                fechaAdquisicion,
                rs.getBigDecimal("VALOR_ADQUISICION"),
                rs.getString("ESTADO")
            );
        }
    };

    @Override
    public ActivoDTO crear(Long idPropiedad, ActivoCreateDTO dto, String estadoFinal) {
        String sql = """
            INSERT INTO ACTIVOS (
                ID_PROPIEDAD, CODIGO_ACTIVO, NOMBRE, CATEGORIA,
                FECHA_ADQUISICION, VALOR_ADQUISICION, ESTADO
            ) VALUES (
                :idProp, :codigo, :nombre, :categoria,
                :fechaAdquisicion, :valorAdquisicion, :estado
            )
            """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("idProp", idPropiedad)
                .addValue("codigo", dto.codigoActivo().trim())
                .addValue("nombre", dto.nombre().trim())
                .addValue("categoria", dto.categoria() != null ? dto.categoria().trim() : null)
                .addValue("fechaAdquisicion", dto.fechaAdquisicion() != null ? Date.valueOf(dto.fechaAdquisicion()) : null, Types.DATE)
                .addValue("valorAdquisicion", dto.valorAdquisicion())
                .addValue("estado", estadoFinal);

        jdbc.update(sql, params, keyHolder, new String[]{"ID_ACTIVO"});
        Number key = keyHolder.getKey();
        Long id = (key != null) ? key.longValue() : null;

        return buscarPorId(id, idPropiedad).orElseThrow(() ->
                new IllegalStateException("No se pudo recuperar el activo recién creado con ID: " + id));
    }

    @Override
    public List<ActivoDTO> listar(Long idPropiedad, String estado, String categoria, String search) {
        StringBuilder sql = new StringBuilder("""
            SELECT ID_ACTIVO, ID_PROPIEDAD, CODIGO_ACTIVO, NOMBRE, CATEGORIA,
                   FECHA_ADQUISICION, VALOR_ADQUISICION, ESTADO
            FROM ACTIVOS
            WHERE ID_PROPIEDAD = :idProp
            """);

        MapSqlParameterSource params = new MapSqlParameterSource("idProp", idPropiedad);

        if (estado != null && !estado.isBlank()) {
            sql.append(" AND ESTADO = :estado");
            params.addValue("estado", estado.trim().toUpperCase());
        }

        if (categoria != null && !categoria.isBlank()) {
            sql.append(" AND UPPER(CATEGORIA) = UPPER(:categoria)");
            params.addValue("categoria", categoria.trim());
        }

        if (search != null && !search.isBlank()) {
            sql.append(" AND (UPPER(CODIGO_ACTIVO) LIKE :search OR UPPER(NOMBRE) LIKE :search OR UPPER(CATEGORIA) LIKE :search)");
            params.addValue("search", "%" + search.trim().toUpperCase() + "%");
        }

        sql.append(" ORDER BY NOMBRE ASC, ID_ACTIVO DESC");

        return jdbc.query(sql.toString(), params, rowMapper);
    }

    @Override
    public Optional<ActivoDTO> buscarPorId(Long idActivo, Long idPropiedad) {
        String sql = """
            SELECT ID_ACTIVO, ID_PROPIEDAD, CODIGO_ACTIVO, NOMBRE, CATEGORIA,
                   FECHA_ADQUISICION, VALOR_ADQUISICION, ESTADO
            FROM ACTIVOS
            WHERE ID_ACTIVO = :idActivo AND ID_PROPIEDAD = :idProp
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("idActivo", idActivo)
                .addValue("idProp", idPropiedad);

        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, params, rowMapper));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<ActivoDTO> buscarPorIdDirecto(Long idActivo) {
        String sql = """
            SELECT ID_ACTIVO, ID_PROPIEDAD, CODIGO_ACTIVO, NOMBRE, CATEGORIA,
                   FECHA_ADQUISICION, VALOR_ADQUISICION, ESTADO
            FROM ACTIVOS
            WHERE ID_ACTIVO = :idActivo
            """;

        MapSqlParameterSource params = new MapSqlParameterSource("idActivo", idActivo);

        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, params, rowMapper));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public boolean existePorCodigo(Long idPropiedad, String codigoActivo, Long excluirIdActivo) {
        StringBuilder sql = new StringBuilder("""
            SELECT COUNT(*) FROM ACTIVOS
            WHERE ID_PROPIEDAD = :idProp AND UPPER(CODIGO_ACTIVO) = UPPER(:codigo)
            """);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("idProp", idPropiedad)
                .addValue("codigo", codigoActivo.trim());

        if (excluirIdActivo != null) {
            sql.append(" AND ID_ACTIVO != :excluirId");
            params.addValue("excluirId", excluirIdActivo);
        }

        Number count = jdbc.queryForObject(sql.toString(), params, Number.class);
        return count != null && count.intValue() > 0;
    }

    @Override
    public void actualizar(Long idActivo, Long idPropiedad, ActivoUpdateDTO dto) {
        String sql = """
            UPDATE ACTIVOS SET
                CODIGO_ACTIVO = :codigo,
                NOMBRE = :nombre,
                CATEGORIA = :categoria,
                FECHA_ADQUISICION = :fechaAdquisicion,
                VALOR_ADQUISICION = :valorAdquisicion
            WHERE ID_ACTIVO = :idActivo AND ID_PROPIEDAD = :idProp
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("idActivo", idActivo)
                .addValue("idProp", idPropiedad)
                .addValue("codigo", dto.codigoActivo().trim())
                .addValue("nombre", dto.nombre().trim())
                .addValue("categoria", dto.categoria() != null ? dto.categoria().trim() : null)
                .addValue("fechaAdquisicion", dto.fechaAdquisicion() != null ? Date.valueOf(dto.fechaAdquisicion()) : null, Types.DATE)
                .addValue("valorAdquisicion", dto.valorAdquisicion());

        jdbc.update(sql, params);
    }

    @Override
    public void actualizarEstado(Long idActivo, Long idPropiedad, String nuevoEstado) {
        String sql = """
            UPDATE ACTIVOS SET ESTADO = :estado
            WHERE ID_ACTIVO = :idActivo AND ID_PROPIEDAD = :idProp
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("idActivo", idActivo)
                .addValue("idProp", idPropiedad)
                .addValue("estado", nuevoEstado.trim().toUpperCase());

        jdbc.update(sql, params);
    }
}
