package com.saed.backend.authorization.repository;

import com.saed.backend.authorization.dto.UnitDTO;
import com.saed.backend.authorization.dto.UnitRequestDTO;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class UnitRepositoryImpl implements UnitRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public UnitRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final String BASE_SELECT = """
        SELECT u.id_unidad, u.id_propiedad, u.id_bloque, u.id_tipo_unidad,
               u.identificador, u.area_m2, u.coeficiente_copropiedad, u.estado,
               tu.codigo AS tipo_unidad_codigo, tu.nombre AS tipo_unidad_nombre,
               b.codigo AS bloque_codigo, b.nombre AS bloque_nombre
        FROM UNIDADES u
        LEFT JOIN TIPOS_UNIDAD tu ON tu.id_tipo_unidad = u.id_tipo_unidad
        LEFT JOIN BLOQUES b ON b.id_bloque = u.id_bloque
    """;

    @Override
    public Long create(UnitRequestDTO request) {
        String sql = "INSERT INTO UNIDADES (id_propiedad, id_tipo_unidad, id_bloque, identificador, area_m2, coeficiente_copropiedad) " +
                     "VALUES (:idPropiedad, :idTipoUnidad, :idBloque, :identificador, :areaM2, :coeficienteCopropiedad)";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("idPropiedad", request.getIdPropiedad())
                .addValue("idTipoUnidad", request.getIdTipoUnidad())
                .addValue("idBloque", request.getIdBloque())
                .addValue("identificador", request.getIdentificador())
                .addValue("areaM2", request.getAreaM2())
                .addValue("coeficienteCopropiedad", request.getCoeficienteCopropiedad());

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, params, keyHolder, new String[]{"ID_UNIDAD"});
        return keyHolder.getKey().longValue();
    }

    @Override
    public Optional<UnitDTO> findById(Long id) {
        String sql = BASE_SELECT + " WHERE u.id_unidad = :id";
        List<UnitDTO> results = jdbcTemplate.query(sql, new MapSqlParameterSource("id", id), this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public List<UnitDTO> findAll() {
        return jdbcTemplate.query(BASE_SELECT + " ORDER BY u.identificador", this::mapRow);
    }

    @Override
    public void update(Long id, UnitRequestDTO request) {
        String sql = "UPDATE UNIDADES SET identificador = :identificador, area_m2 = :areaM2, " +
                     "coeficiente_copropiedad = :coeficienteCopropiedad WHERE id_unidad = :id";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("identificador", request.getIdentificador())
                .addValue("areaM2", request.getAreaM2())
                .addValue("coeficienteCopropiedad", request.getCoeficienteCopropiedad());
        jdbcTemplate.update(sql, params);
    }

    private UnitDTO mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        UnitDTO dto = new UnitDTO();
        dto.setId(rs.getLong("id_unidad"));
        dto.setIdPropiedad(rs.getLong("id_propiedad"));
        long idBloque = rs.getLong("id_bloque");
        if (!rs.wasNull()) dto.setIdBloque(idBloque);
        long idTipo = rs.getLong("id_tipo_unidad");
        if (!rs.wasNull()) dto.setIdTipoUnidad(idTipo);
        dto.setIdentificador(rs.getString("identificador"));
        dto.setTipoUnidadCodigo(rs.getString("tipo_unidad_codigo"));
        dto.setTipoUnidadNombre(rs.getString("tipo_unidad_nombre"));
        dto.setBloqueCodigo(rs.getString("bloque_codigo"));
        dto.setBloqueNombre(rs.getString("bloque_nombre"));
        java.math.BigDecimal area = rs.getBigDecimal("area_m2");
        if (!rs.wasNull()) dto.setAreaM2(area);
        java.math.BigDecimal coef = rs.getBigDecimal("coeficiente_copropiedad");
        if (!rs.wasNull()) dto.setCoeficienteCopropiedad(coef);
        dto.setEstado(rs.getString("estado"));
        return dto;
    }
}