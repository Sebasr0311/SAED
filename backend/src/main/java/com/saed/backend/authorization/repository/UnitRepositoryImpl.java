package com.saed.backend.authorization.repository;

import com.saed.backend.authorization.dto.UnitDTO;
import com.saed.backend.authorization.dto.UnitRequestDTO;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
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
               u.identificador, u.piso, u.area_m2, u.coeficiente_copropiedad, u.estado,
               tu.codigo AS tipo_unidad_codigo, tu.nombre AS tipo_unidad_nombre,
               b.codigo AS bloque_codigo, b.nombre AS bloque_nombre
        FROM UNIDADES u
        LEFT JOIN TIPOS_UNIDAD tu ON tu.id_tipo_unidad = u.id_tipo_unidad
        LEFT JOIN BLOQUES b ON b.id_bloque = u.id_bloque
    """;

    @Override
    public Long create(UnitRequestDTO request) {
        String sql = "INSERT INTO UNIDADES (id_propiedad, id_tipo_unidad, id_bloque, identificador, piso, area_m2, "
                     + "coeficiente_copropiedad) VALUES (:idPropiedad, :idTipoUnidad, :idBloque, :identificador, :piso, "
                     + ":areaM2, :coeficienteCopropiedad)";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("idPropiedad", request.getIdPropiedad())
                .addValue("idTipoUnidad", request.getIdTipoUnidad())
                .addValue("idBloque", request.getIdBloque())
                .addValue("identificador", request.getIdentificador())
                .addValue("piso", request.getPiso())
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
        return findAll(null);
    }

    @Override
    public List<UnitDTO> findAll(Long propertyId) {
        SaedContext ctx = SaedContextHolder.getContext();
        Long effectivePropId = propertyId != null ? propertyId : (ctx != null ? ctx.getPropertyId() : null);
        if (effectivePropId != null) {
            return jdbcTemplate.query(BASE_SELECT + " WHERE u.id_propiedad = :propId ORDER BY u.identificador",
                    new MapSqlParameterSource("propId", effectivePropId), this::mapRow);
        }
        return jdbcTemplate.query(BASE_SELECT + " ORDER BY u.identificador", this::mapRow);
    }

    @Override
    public void update(Long id, UnitRequestDTO request) {
        String sql = "UPDATE UNIDADES SET identificador = :identificador, piso = :piso, area_m2 = :areaM2, " +
                     "coeficiente_copropiedad = :coeficienteCopropiedad, id_bloque = :idBloque, " +
                     "id_tipo_unidad = :idTipoUnidad WHERE id_unidad = :id";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("identificador", request.getIdentificador())
                .addValue("areaM2", request.getAreaM2())
                .addValue("coeficienteCopropiedad", request.getCoeficienteCopropiedad())
                .addValue("idBloque", request.getIdBloque())
                .addValue("piso", request.getPiso())
                .addValue("idTipoUnidad", request.getIdTipoUnidad());
        if (jdbcTemplate.update(sql, params) == 0) {
            throw new java.util.NoSuchElementException("Unit not found");
        }
    }

    @Override
    public boolean existsIdentifier(Long propertyId, Long blockId, String identifier, Long excludeUnitId) {
        String sql = "SELECT COUNT(*) FROM UNIDADES WHERE ID_PROPIEDAD = :idPropiedad "
                + "AND NVL(ID_BLOQUE, -1) = NVL(:idBloque, -1) "
                + "AND UPPER(IDENTIFICADOR) = UPPER(:identificador) "
                + "AND (:excludeId IS NULL OR ID_UNIDAD <> :excludeId)";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("idPropiedad", propertyId)
                .addValue("idBloque", blockId)
                .addValue("identificador", identifier == null ? null : identifier.trim())
                .addValue("excludeId", excludeUnitId);
        Integer count = jdbcTemplate.queryForObject(sql, params, Integer.class);
        return count != null && count > 0;
    }

    @Override
    public java.math.BigDecimal lockPropertyAndSumCoefficients(Long propertyId, Long excludeUnitId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("idPropiedad", propertyId)
                .addValue("excludeId", excludeUnitId);
        jdbcTemplate.query("SELECT ID_PROPIEDAD FROM PROPIEDADES WHERE ID_PROPIEDAD = :idPropiedad FOR UPDATE",
                params, (rs, i) -> rs.getLong(1));
        java.math.BigDecimal sum = jdbcTemplate.queryForObject(
                "SELECT NVL(SUM(COEFICIENTE_COPROPIEDAD), 0) FROM UNIDADES WHERE ID_PROPIEDAD = :idPropiedad "
                        + "AND ESTADO <> 'INACTIVA' AND (:excludeId IS NULL OR ID_UNIDAD <> :excludeId)",
                params, java.math.BigDecimal.class);
        return sum == null ? java.math.BigDecimal.ZERO : sum;
    }

    @Override
    public com.saed.backend.authorization.dto.UnitCoefficientSummaryDTO coefficientSummary(Long propertyId) {
        String sql = "SELECT COUNT(*) AS total, "
                + "SUM(CASE WHEN COEFICIENTE_COPROPIEDAD IS NULL THEN 1 ELSE 0 END) AS sin_coef, "
                + "NVL(SUM(COEFICIENTE_COPROPIEDAD), 0) AS suma "
                + "FROM UNIDADES WHERE ID_PROPIEDAD = :idPropiedad AND ESTADO <> 'INACTIVA'";
        return jdbcTemplate.queryForObject(sql, new MapSqlParameterSource("idPropiedad", propertyId), (rs, i) -> {
            com.saed.backend.authorization.dto.UnitCoefficientSummaryDTO dto =
                    new com.saed.backend.authorization.dto.UnitCoefficientSummaryDTO();
            dto.setIdPropiedad(propertyId);
            dto.setTotalUnidades(rs.getLong("total"));
            dto.setUnidadesSinCoeficiente(rs.getLong("sin_coef"));
            java.math.BigDecimal suma = rs.getBigDecimal("suma");
            dto.setSumaCoeficientes(suma == null ? java.math.BigDecimal.ZERO : suma);
            return dto;
        });
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
        dto.setPiso(rs.getString("piso"));
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