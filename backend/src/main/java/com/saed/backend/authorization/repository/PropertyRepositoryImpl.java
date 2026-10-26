package com.saed.backend.authorization.repository;

import com.saed.backend.authorization.dto.PropertyDTO;
import com.saed.backend.authorization.dto.PropertyRequestDTO;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class PropertyRepositoryImpl implements PropertyRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PropertyRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final String BASE_SELECT = """
        SELECT p.id_propiedad, p.id_organizacion, p.id_tipo_propiedad, p.nombre,
               p.direccion, p.ciudad, p.departamento, p.pais, p.tipo_ocupacion_predominante, p.estado,
               o.nombre AS organizacion_nombre,
               tp.codigo AS tipo_propiedad_codigo, tp.nombre AS tipo_propiedad_nombre
        FROM PROPIEDADES p
        LEFT JOIN ORGANIZACIONES o ON o.id_organizacion = p.id_organizacion
        LEFT JOIN TIPOS_PROPIEDAD tp ON tp.id_tipo_propiedad = p.id_tipo_propiedad
    """;

    @Override
    public Long create(PropertyRequestDTO request) {
        String sql = "INSERT INTO PROPIEDADES (id_organizacion, id_tipo_propiedad, nombre, direccion, ciudad, departamento, pais, "
                     + "tipo_ocupacion_predominante) VALUES (:idOrganizacion, :idTipoPropiedad, :nombre, :direccion, :ciudad, "
                     + ":departamento, :pais, :tipoOcupacion)";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("idOrganizacion", request.getIdOrganizacion())
                .addValue("idTipoPropiedad", request.getIdTipoPropiedad())
                .addValue("nombre", request.getNombre())
                .addValue("direccion", request.getDireccion())
                .addValue("ciudad", request.getCiudad())
                .addValue("departamento", request.getDepartamento())
                .addValue("pais", countryOrDefault(request.getPais()))
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
        dto.setDepartamento(rs.getString("departamento"));
        dto.setPais(rs.getString("pais"));
        dto.setTipoOcupacionPredominante(rs.getString("tipo_ocupacion_predominante"));
        dto.setEstado(rs.getString("estado"));
        dto.setOrganizacionNombre(rs.getString("organizacion_nombre"));
        dto.setTipoPropiedadCodigo(rs.getString("tipo_propiedad_codigo"));
        dto.setTipoPropiedadNombre(rs.getString("tipo_propiedad_nombre"));
        return dto;
    }

    /** PAIS is NOT NULL with default 'Colombia'; an absent value keeps that default. */
    private static String countryOrDefault(String pais) {
        return pais == null || pais.isBlank() ? "Colombia" : pais.trim();
    }

    @Override
    public void update(Long id, PropertyRequestDTO request) {
        String sql = "UPDATE PROPIEDADES SET nombre = :nombre, direccion = :direccion, ciudad = :ciudad, " +
                     "departamento = :departamento, pais = :pais, " +
                     "tipo_ocupacion_predominante = :tipoOcupacion, id_tipo_propiedad = :idTipoPropiedad, " +
                     "id_organizacion = NVL(:idOrganizacion, id_organizacion) WHERE id_propiedad = :id";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("nombre", request.getNombre())
                .addValue("direccion", request.getDireccion())
                .addValue("ciudad", request.getCiudad())
                .addValue("departamento", request.getDepartamento())
                .addValue("pais", countryOrDefault(request.getPais()))
                .addValue("tipoOcupacion", request.getTipoOcupacionPredominante())
                .addValue("idTipoPropiedad", request.getIdTipoPropiedad())
                .addValue("idOrganizacion", request.getIdOrganizacion());
        // F4-H07: a row hidden by RLS or absent must not be reported as updated.
        if (jdbcTemplate.update(sql, params) == 0) {
            throw new java.util.NoSuchElementException("Property not found");
        }
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

    private static final String UNITS_OF_PROPERTY = "(SELECT ID_UNIDAD FROM UNIDADES WHERE ID_PROPIEDAD = :propId)";

    /**
     * Financial, legal and audit history that must never be destroyed by a property hard delete
     * (decision D07). Each entry maps a table name to the predicate that links it to the property.
     */
    private static final Map<String, String> PROTECTED_HISTORY = buildProtectedHistory();

    private static Map<String, String> buildProtectedHistory() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("CUOTAS", "ID_UNIDAD IN " + UNITS_OF_PROPERTY);
        m.put("PAGOS", "ID_UNIDAD IN " + UNITS_OF_PROPERTY);
        m.put("TRANSACCIONES_PAGO", "ID_UNIDAD IN " + UNITS_OF_PROPERTY);
        // CARTERA has no ID_PROPIEDAD column: it is linked to the property through its unit only.
        m.put("CARTERA", "ID_UNIDAD IN " + UNITS_OF_PROPERTY);
        m.put("MULTAS", "ID_UNIDAD IN " + UNITS_OF_PROPERTY);
        m.put("SANCIONES", "ID_PROPIEDAD = :propId OR ID_UNIDAD IN " + UNITS_OF_PROPERTY);
        m.put("CONTRATOS", "ID_UNIDAD IN " + UNITS_OF_PROPERTY);
        m.put("PAZ_Y_SALVOS", "ID_UNIDAD IN " + UNITS_OF_PROPERTY);
        m.put("ASAMBLEAS", "ID_PROPIEDAD = :propId");
        m.put("VOTOS", "ID_UNIDAD IN " + UNITS_OF_PROPERTY);
        m.put("ASISTENCIAS_ASAMBLEA", "ID_UNIDAD IN " + UNITS_OF_PROPERTY);
        m.put("PODERES_REPRESENTACION", "ID_UNIDAD IN " + UNITS_OF_PROPERTY);
        m.put("GASTOS", "ID_PROPIEDAD = :propId");
        m.put("CONCILIACIONES", "ID_PROPIEDAD = :propId");
        m.put("REGISTROS_ACCESO", "ID_PROPIEDAD = :propId");
        return Collections.unmodifiableMap(m);
    }

    /** Table-to-predicate view of the protected history checks, exposed to the schema consistency test. */
    static Map<String, String> protectedHistoryPredicates() {
        return PROTECTED_HISTORY;
    }

    @Override
    public List<String> findProtectedHistory(Long propertyId) {
        MapSqlParameterSource params = new MapSqlParameterSource("propId", propertyId);
        List<String> found = new ArrayList<>();
        // No exception is swallowed: if a check cannot run, deletion must not proceed.
        for (Map.Entry<String, String> entry : PROTECTED_HISTORY.entrySet()) {
            String sql = "SELECT COUNT(*) FROM DUAL WHERE EXISTS (SELECT 1 FROM " + entry.getKey()
                    + " WHERE " + entry.getValue() + ")";
            Integer exists = jdbcTemplate.queryForObject(sql, params, Integer.class);
            if (exists != null && exists > 0) {
                found.add(entry.getKey());
            }
        }
        return found;
    }

    /**
     * Hard-deletes a property that has NO protected history (callers must check
     * {@link #findProtectedHistory(Long)} first). Only operational, non-historical rows are
     * removed. All-or-nothing: any failure propagates and rolls the transaction back.
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deletePropertyCascade(Long propertyId, Long organizationId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("propId", propertyId)
                .addValue("orgId", organizationId);

        String[] cleanupQueries = new String[] {
            "DELETE FROM PAQUETES WHERE ID_PROPIEDAD = :propId",
            "DELETE FROM RESERVAS WHERE ID_UNIDAD IN " + UNITS_OF_PROPERTY,
            "DELETE FROM UNIDADES WHERE ID_PROPIEDAD = :propId"
        };

        for (String q : cleanupQueries) {
            jdbcTemplate.update(q, params);
        }

        String deleteSql = "DELETE FROM PROPIEDADES WHERE ID_PROPIEDAD = :propId AND ID_ORGANIZACION = :orgId";
        int affected = jdbcTemplate.update(deleteSql, params);
        return affected > 0;
    }
}
