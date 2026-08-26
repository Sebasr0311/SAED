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
        String sql = "SELECT id_unidad, id_propiedad, identificador, estado FROM UNIDADES WHERE id_unidad = :id";
        List<UnitDTO> results = jdbcTemplate.query(sql, new MapSqlParameterSource("id", id), (rs, rowNum) -> {
            UnitDTO dto = new UnitDTO();
            dto.setId(rs.getLong("id_unidad"));
            dto.setIdentificador(rs.getString("identificador"));
            return dto;
        });
        return results.stream().findFirst();
    }

    @Override
    public List<UnitDTO> findAll() {
        String sql = "SELECT id_unidad, identificador, estado FROM UNIDADES";
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            UnitDTO dto = new UnitDTO();
            dto.setId(rs.getLong("id_unidad"));
            dto.setIdentificador(rs.getString("identificador"));
            return dto;
        });
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
}
