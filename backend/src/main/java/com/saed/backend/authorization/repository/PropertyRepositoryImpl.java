package com.saed.backend.authorization.repository;

import com.saed.backend.authorization.dto.PropertyDTO;
import com.saed.backend.authorization.dto.PropertyRequestDTO;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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
                     "tipo_ocupacion_predominante = :tipoOcupacion, id_tipo_propiedad = :idTipoPropiedad, " +
                     "id_organizacion = :idOrganizacion WHERE id_propiedad = :id";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("nombre", request.getNombre())
                .addValue("direccion", request.getDireccion())
                .addValue("ciudad", request.getCiudad())
                .addValue("tipoOcupacion", request.getTipoOcupacionPredominante())
                .addValue("idTipoPropiedad", request.getIdTipoPropiedad())
                .addValue("idOrganizacion", request.getIdOrganizacion());
        jdbcTemplate.update(sql, params);
    }

    @Override
    public void updateStatus(Long id, String estado) {
        String sql = "UPDATE PROPIEDADES SET estado = :estado WHERE id_propiedad = :id";
        jdbcTemplate.update(sql, new MapSqlParameterSource("id", id).addValue("estado", estado));
    }

    @Override
    public long countByOrganization(Long orgId) {
        String sql = "SELECT COUNT(*) FROM PROPIEDADES WHERE id_organizacion = :orgId AND estado = 'ACTIVA'";
        Number n = jdbcTemplate.queryForObject(sql, new MapSqlParameterSource("orgId", orgId), Number.class);
        return n != null ? n.longValue() : 0L;
    }

    @Override
    public Optional<Long> getPropertyLimit(Long orgId) {
        String sql = """
            SELECT p.limite_propiedades
            FROM MEMBRESIAS m
            JOIN PLANES p ON m.id_plan = p.id_plan
            WHERE m.id_organizacion = :orgId AND m.estado IN ('ACTIVA', 'PRUEBA')
            ORDER BY m.fecha_inicio DESC
            FETCH FIRST 1 ROWS ONLY
        """;
        List<Long> limits = jdbcTemplate.query(sql, new MapSqlParameterSource("orgId", orgId), (rs, i) -> rs.getLong("limite_propiedades"));
        return limits.stream().findFirst();
    }

    @Override
    public Optional<String> getPropertyStatus(Long id) {
        String sql = "SELECT estado FROM PROPIEDADES WHERE id_propiedad = :id";
        List<String> list = jdbcTemplate.query(
            sql,
            new MapSqlParameterSource("id", id),
            (rs, rowNum) -> rs.getString("estado")
        );
        return list.stream().findFirst();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deletePropertyCascade(Long propertyId, Long organizationId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("propId", propertyId)
                .addValue("orgId", organizationId);

        String[] cleanupQueries = new String[] {
            "DELETE FROM CONCILIACIONES WHERE ID_PROPIEDAD = :propId",
            "DELETE FROM GASTOS WHERE ID_PROPIEDAD = :propId",
            "DELETE FROM REGISTROS_ACCESO WHERE ID_PROPIEDAD = :propId",
            "DELETE FROM PAQUETES WHERE ID_PROPIEDAD = :propId",
            "DELETE FROM TRANSACCIONES_PAGO WHERE ID_UNIDAD IN (SELECT ID_UNIDAD FROM UNIDADES WHERE ID_PROPIEDAD = :propId)",
            "DELETE FROM PAGO_DETALLE WHERE ID_PAGO IN (SELECT ID_PAGO FROM PAGOS WHERE ID_UNIDAD IN (SELECT ID_UNIDAD FROM UNIDADES WHERE ID_PROPIEDAD = :propId)) OR ID_CUOTA IN (SELECT ID_CUOTA FROM CUOTAS WHERE ID_UNIDAD IN (SELECT ID_UNIDAD FROM UNIDADES WHERE ID_PROPIEDAD = :propId))",
            "DELETE FROM PAGOS WHERE ID_UNIDAD IN (SELECT ID_UNIDAD FROM UNIDADES WHERE ID_PROPIEDAD = :propId)",
            "DELETE FROM CUOTAS WHERE ID_UNIDAD IN (SELECT ID_UNIDAD FROM UNIDADES WHERE ID_PROPIEDAD = :propId)",
            "DELETE FROM MULTAS WHERE ID_UNIDAD IN (SELECT ID_UNIDAD FROM UNIDADES WHERE ID_PROPIEDAD = :propId)",
            "DELETE FROM SANCIONES WHERE ID_PROPIEDAD = :propId OR ID_UNIDAD IN (SELECT ID_UNIDAD FROM UNIDADES WHERE ID_PROPIEDAD = :propId)",
            "DELETE FROM CONTRATOS WHERE ID_UNIDAD IN (SELECT ID_UNIDAD FROM UNIDADES WHERE ID_PROPIEDAD = :propId)",
            "DELETE FROM RESERVAS WHERE ID_UNIDAD IN (SELECT ID_UNIDAD FROM UNIDADES WHERE ID_PROPIEDAD = :propId)",
            "DELETE FROM PAZ_Y_SALVOS WHERE ID_UNIDAD IN (SELECT ID_UNIDAD FROM UNIDADES WHERE ID_PROPIEDAD = :propId)",
            "DELETE FROM ASISTENCIAS_ASAMBLEA WHERE ID_UNIDAD IN (SELECT ID_UNIDAD FROM UNIDADES WHERE ID_PROPIEDAD = :propId)",
            "DELETE FROM VOTOS WHERE ID_UNIDAD IN (SELECT ID_UNIDAD FROM UNIDADES WHERE ID_PROPIEDAD = :propId)",
            "DELETE FROM PODERES_REPRESENTACION WHERE ID_UNIDAD IN (SELECT ID_UNIDAD FROM UNIDADES WHERE ID_PROPIEDAD = :propId)",
            "DELETE FROM UNIDADES WHERE ID_PROPIEDAD = :propId"
        };

        for (String q : cleanupQueries) {
            try {
                jdbcTemplate.update(q, params);
            } catch (Exception ignored) {
                // Tablas opcionales o sin registros
            }
        }

        String deleteSql = "DELETE FROM PROPIEDADES WHERE ID_PROPIEDAD = :propId AND ID_ORGANIZACION = :orgId";
        int affected = jdbcTemplate.update(deleteSql, params);
        return affected > 0;
    }
}
