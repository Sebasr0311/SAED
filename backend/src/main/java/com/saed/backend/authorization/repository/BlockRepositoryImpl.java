package com.saed.backend.authorization.repository;

import com.saed.backend.authorization.dto.BlockDTO;
import com.saed.backend.authorization.dto.BlockRequestDTO;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class BlockRepositoryImpl implements BlockRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public BlockRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final String BASE_SELECT = """
        SELECT b.id_bloque, b.id_propiedad, b.id_bloque_padre, b.tipo, b.codigo, b.nombre,
               b.orden, b.estado,
               bp.nombre AS bloque_padre_nombre,
               (SELECT COUNT(*) FROM BLOQUES h WHERE h.id_bloque_padre = b.id_bloque) AS total_hijos,
               (SELECT COUNT(*) FROM UNIDADES u WHERE u.id_bloque = b.id_bloque) AS total_unidades
        FROM BLOQUES b
        LEFT JOIN BLOQUES bp ON bp.id_bloque = b.id_bloque_padre
    """;

    @Override
    public Long create(BlockRequestDTO request) {
        String sql = """
            INSERT INTO BLOQUES (id_propiedad, id_bloque_padre, tipo, codigo, nombre, orden, estado)
            VALUES (:idPropiedad, :idBloquePadre, :tipo, :codigo, :nombre, :orden, :estado)
        """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("idPropiedad", request.getIdPropiedad())
                .addValue("idBloquePadre", request.getIdBloquePadre())
                .addValue("tipo", request.getTipo().toUpperCase())
                .addValue("codigo", request.getCodigo().trim())
                .addValue("nombre", request.getNombre() != null ? request.getNombre().trim() : null)
                .addValue("orden", request.getOrden() != null ? request.getOrden() : 0)
                .addValue("estado", request.getEstado() != null ? request.getEstado().toUpperCase() : "ACTIVO");

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, params, keyHolder, new String[]{"ID_BLOQUE"});
        return keyHolder.getKey().longValue();
    }

    @Override
    public Optional<BlockDTO> findById(Long id) {
        String sql = BASE_SELECT + " WHERE b.id_bloque = :id";
        List<BlockDTO> list = jdbcTemplate.query(sql, new MapSqlParameterSource("id", id), this::mapRow);
        return list.stream().findFirst();
    }

    @Override
    public List<BlockDTO> findByPropertyId(Long propertyId) {
        String sql = BASE_SELECT + " WHERE b.id_propiedad = :propId ORDER BY b.orden ASC, b.codigo ASC";
        return jdbcTemplate.query(sql, new MapSqlParameterSource("propId", propertyId), this::mapRow);
    }

    @Override
    public void update(Long id, BlockRequestDTO request) {
        String sql = """
            UPDATE BLOQUES
            SET id_bloque_padre = :idBloquePadre,
                tipo = :tipo,
                codigo = :codigo,
                nombre = :nombre,
                orden = :orden
            WHERE id_bloque = :id
        """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("idBloquePadre", request.getIdBloquePadre())
                .addValue("tipo", request.getTipo().toUpperCase())
                .addValue("codigo", request.getCodigo().trim())
                .addValue("nombre", request.getNombre() != null ? request.getNombre().trim() : null)
                .addValue("orden", request.getOrden() != null ? request.getOrden() : 0);
        jdbcTemplate.update(sql, params);
    }

    @Override
    public void updateStatus(Long id, String estado) {
        String sql = "UPDATE BLOQUES SET estado = :estado WHERE id_bloque = :id";
        jdbcTemplate.update(sql, new MapSqlParameterSource("id", id).addValue("estado", estado.toUpperCase()));
    }

    @Override
    public void delete(Long id) {
        String sql = "DELETE FROM BLOQUES WHERE id_bloque = :id";
        jdbcTemplate.update(sql, new MapSqlParameterSource("id", id));
    }

    @Override
    public int countChildren(Long id) {
        String sql = "SELECT COUNT(*) FROM BLOQUES WHERE id_bloque_padre = :id";
        Integer count = jdbcTemplate.queryForObject(sql, new MapSqlParameterSource("id", id), Integer.class);
        return count != null ? count : 0;
    }

    @Override
    public int countUnits(Long id) {
        String sql = "SELECT COUNT(*) FROM UNIDADES WHERE id_bloque = :id";
        Integer count = jdbcTemplate.queryForObject(sql, new MapSqlParameterSource("id", id), Integer.class);
        return count != null ? count : 0;
    }

    @Override
    public boolean existsByIdAndPropertyId(Long id, Long propertyId) {
        String sql = "SELECT COUNT(*) FROM BLOQUES WHERE id_bloque = :id AND id_propiedad = :propId";
        Integer count = jdbcTemplate.queryForObject(
                sql,
                new MapSqlParameterSource("id", id).addValue("propId", propertyId),
                Integer.class
        );
        return count != null && count > 0;
    }

    private BlockDTO mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        BlockDTO dto = new BlockDTO();
        dto.setId(rs.getLong("id_bloque"));
        dto.setIdPropiedad(rs.getLong("id_propiedad"));
        long padre = rs.getLong("id_bloque_padre");
        if (!rs.wasNull()) {
            dto.setIdBloquePadre(padre);
        }
        dto.setTipo(rs.getString("tipo"));
        dto.setCodigo(rs.getString("codigo"));
        dto.setNombre(rs.getString("nombre"));
        dto.setOrden(rs.getInt("orden"));
        dto.setEstado(rs.getString("estado"));
        dto.setBloquePadreNombre(rs.getString("bloque_padre_nombre"));
        dto.setTotalHijos(rs.getInt("total_hijos"));
        dto.setTotalUnidades(rs.getInt("total_unidades"));
        return dto;
    }
}
