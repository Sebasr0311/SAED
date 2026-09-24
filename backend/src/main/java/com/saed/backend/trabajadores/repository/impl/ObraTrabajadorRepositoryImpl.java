package com.saed.backend.trabajadores.repository.impl;

import com.saed.backend.trabajadores.dto.ObraTrabajadorDTO;
import com.saed.backend.trabajadores.dto.TrabajadorDTO;
import com.saed.backend.trabajadores.repository.ObraTrabajadorRepository;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class ObraTrabajadorRepositoryImpl implements ObraTrabajadorRepository {

    private static final ZoneId BOGOTA_ZONE = ZoneId.of("America/Bogota");

    private final NamedParameterJdbcTemplate jdbc;

    public ObraTrabajadorRepositoryImpl(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private ZonedDateTime toZonedDateTime(ResultSet rs, String colName) {
        try {
            Timestamp ts = rs.getTimestamp(colName);
            if (ts != null) {
                return ts.toInstant().atZone(BOGOTA_ZONE);
            }
        } catch (Exception ignored) {}
        try {
            OffsetDateTime odt = rs.getObject(colName, OffsetDateTime.class);
            if (odt != null) {
                return odt.atZoneSameInstant(BOGOTA_ZONE);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private final RowMapper<ObraTrabajadorDTO> rowMapper = (rs, rowNum) -> {
        Date sqlAfiliacion = rs.getDate("ARL_FECHA_AFILIACION");
        LocalDate arlFechaAfiliacion = (sqlAfiliacion != null) ? sqlAfiliacion.toLocalDate() : null;

        Date sqlVencimiento = rs.getDate("ARL_FECHA_VENCIMIENTO");
        LocalDate arlFechaVencimiento = (sqlVencimiento != null) ? sqlVencimiento.toLocalDate() : null;

        Date sqlAlturas = rs.getDate("CERT_ALTURAS_VENCIMIENTO");
        LocalDate certAlturasVencimiento = (sqlAlturas != null) ? sqlAlturas.toLocalDate() : null;

        String pNom = rs.getString("PRIMER_NOMBRE");
        String sNom = rs.getString("SEGUNDO_NOMBRE");
        String pApe = rs.getString("PRIMER_APELLIDO");
        String sApe = rs.getString("SEGUNDO_APELLIDO");

        StringBuilder sb = new StringBuilder();
        if (pNom != null && !pNom.isBlank()) sb.append(pNom.trim());
        if (sNom != null && !sNom.isBlank()) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(sNom.trim());
        }
        if (pApe != null && !pApe.isBlank()) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(pApe.trim());
        }
        if (sApe != null && !sApe.isBlank()) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(sApe.trim());
        }
        String nombreCompleto = sb.toString();

        boolean arlVigente = TrabajadorDTO.calcularArlVigente(arlFechaVencimiento, arlFechaAfiliacion);

        Long idProv = rs.getLong("ID_PROVEEDOR");
        if (rs.wasNull()) idProv = null;

        TrabajadorDTO trabajador = new TrabajadorDTO(
                rs.getLong("ID_TRABAJADOR"),
                rs.getLong("ID_PERSONA"),
                idProv,
                rs.getString("RAZON_SOCIAL_PROVEEDOR"),
                rs.getString("TIPO_DOCUMENTO"),
                rs.getString("NUMERO_DOCUMENTO"),
                pNom,
                sNom,
                pApe,
                sApe,
                nombreCompleto,
                rs.getString("EMAIL"),
                rs.getString("TELEFONO"),
                rs.getString("OFICIO_ESPECIALIDAD"),
                rs.getString("ARL_ASEGURADORA"),
                arlFechaAfiliacion,
                arlFechaVencimiento,
                certAlturasVencimiento,
                rs.getString("EMPRESA_INDEPENDIENTE"),
                rs.getString("ESTADO"),
                arlVigente
        );

        Long autPor = rs.getLong("AUTORIZADO_POR");
        if (rs.wasNull()) autPor = null;

        return new ObraTrabajadorDTO(
                rs.getLong("ID_OBRA_TRABAJADOR"),
                rs.getLong("ID_OBRA"),
                rs.getLong("ID_TRABAJADOR"),
                rs.getString("AUTORIZADO"),
                toZonedDateTime(rs, "FECHA_AUTORIZACION"),
                toZonedDateTime(rs, "FECHA_REVOCACION"),
                autPor,
                trabajador
        );
    };

    private static final String BASE_SELECT = """
            SELECT ot.ID_OBRA_TRABAJADOR,
                   ot.ID_OBRA,
                   ot.ID_TRABAJADOR,
                   ot.AUTORIZADO,
                   ot.FECHA_AUTORIZACION,
                   ot.FECHA_REVOCACION,
                   ot.AUTORIZADO_POR,
                   t.ID_PERSONA,
                   t.ID_PROVEEDOR,
                   pr.RAZON_SOCIAL AS RAZON_SOCIAL_PROVEEDOR,
                   COALESCE(td.CODIGO, td.NOMBRE, 'CC') AS TIPO_DOCUMENTO,
                   p.NUMERO_DOCUMENTO,
                   p.PRIMER_NOMBRE,
                   p.SEGUNDO_NOMBRE,
                   p.PRIMER_APELLIDO,
                   p.SEGUNDO_APELLIDO,
                   p.EMAIL,
                   p.TELEFONO,
                   t.OFICIO_ESPECIALIDAD,
                   t.ARL_ASEGURADORA,
                   t.ARL_FECHA_AFILIACION,
                   t.ARL_FECHA_VENCIMIENTO,
                   t.CERT_ALTURAS_VENCIMIENTO,
                   t.EMPRESA_INDEPENDIENTE,
                   t.ESTADO
            FROM OBRA_TRABAJADORES ot
            JOIN TRABAJADORES t ON ot.ID_TRABAJADOR = t.ID_TRABAJADOR
            JOIN PERSONAS p ON t.ID_PERSONA = p.ID_PERSONA
            LEFT JOIN PROVEEDORES pr ON t.ID_PROVEEDOR = pr.ID_PROVEEDOR
            LEFT JOIN TIPOS_DOCUMENTO td ON p.ID_TIPO_DOCUMENTO = td.ID_TIPO_DOCUMENTO
            """;

    @Override
    public void asignarOActualizar(Long idObra, Long idTrabajador, String autorizado, Long autorizadoPor) {
        String aut = (autorizado != null && autorizado.equalsIgnoreCase("N")) ? "N" : "S";

        String checkSql = "SELECT COUNT(1) FROM OBRA_TRABAJADORES WHERE ID_OBRA = :idObra AND ID_TRABAJADOR = :idTrabajador";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("idObra", idObra, Types.NUMERIC)
                .addValue("idTrabajador", idTrabajador, Types.NUMERIC)
                .addValue("autorizado", aut, Types.VARCHAR)
                .addValue("autorizadoPor", autorizadoPor, Types.NUMERIC);

        Integer count = jdbc.queryForObject(checkSql, params, Integer.class);
        if (count != null && count > 0) {
            String updateSql = """
                    UPDATE OBRA_TRABAJADORES SET
                        AUTORIZADO = :autorizado,
                        FECHA_AUTORIZACION = CASE WHEN :autorizado = 'S' THEN CURRENT_TIMESTAMP ELSE NULL END,
                        FECHA_REVOCACION = CASE WHEN :autorizado = 'N' AND AUTORIZADO = 'S' THEN CURRENT_TIMESTAMP ELSE FECHA_REVOCACION END,
                        AUTORIZADO_POR = CASE WHEN :autorizado = 'S' THEN :autorizadoPor ELSE NULL END
                    WHERE ID_OBRA = :idObra AND ID_TRABAJADOR = :idTrabajador
                    """;
            jdbc.update(updateSql, params);
        } else {
            String insertSql = """
                    INSERT INTO OBRA_TRABAJADORES (
                        ID_OBRA, ID_TRABAJADOR, AUTORIZADO, FECHA_AUTORIZACION, FECHA_REVOCACION, AUTORIZADO_POR
                    ) VALUES (
                        :idObra, :idTrabajador, :autorizado,
                        CASE WHEN :autorizado = 'S' THEN CURRENT_TIMESTAMP ELSE NULL END,
                        NULL,
                        CASE WHEN :autorizado = 'S' THEN :autorizadoPor ELSE NULL END
                    )
                    """;
            jdbc.update(insertSql, params);
        }
    }

    @Override
    public void autorizar(Long idObra, Long idTrabajador, Long autorizadoPor) {
        String updateSql = """
                UPDATE OBRA_TRABAJADORES SET
                    AUTORIZADO = 'S',
                    FECHA_AUTORIZACION = CURRENT_TIMESTAMP,
                    FECHA_REVOCACION = NULL,
                    AUTORIZADO_POR = :autorizadoPor
                WHERE ID_OBRA = :idObra AND ID_TRABAJADOR = :idTrabajador
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("idObra", idObra, Types.NUMERIC)
                .addValue("idTrabajador", idTrabajador, Types.NUMERIC)
                .addValue("autorizadoPor", autorizadoPor, Types.NUMERIC);
        jdbc.update(updateSql, params);
    }

    @Override
    public void revocar(Long idObra, Long idTrabajador) {
        String updateSql = """
                UPDATE OBRA_TRABAJADORES SET
                    AUTORIZADO = 'N',
                    FECHA_REVOCACION = CURRENT_TIMESTAMP,
                    FECHA_AUTORIZACION = NULL,
                    AUTORIZADO_POR = NULL
                WHERE ID_OBRA = :idObra AND ID_TRABAJADOR = :idTrabajador
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("idObra", idObra, Types.NUMERIC)
                .addValue("idTrabajador", idTrabajador, Types.NUMERIC);
        jdbc.update(updateSql, params);
    }

    @Override
    public void desasignar(Long idObra, Long idTrabajador) {
        String sql = "DELETE FROM OBRA_TRABAJADORES WHERE ID_OBRA = :idObra AND ID_TRABAJADOR = :idTrabajador";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("idObra", idObra, Types.NUMERIC)
                .addValue("idTrabajador", idTrabajador, Types.NUMERIC);
        jdbc.update(sql, params);
    }

    @Override
    public List<ObraTrabajadorDTO> listarPorObra(Long idObra) {
        String sql = BASE_SELECT + " WHERE ot.ID_OBRA = :idObra ORDER BY ot.ID_OBRA_TRABAJADOR ASC";
        return jdbc.query(sql, new MapSqlParameterSource("idObra", idObra), rowMapper);
    }

    @Override
    public List<ObraTrabajadorDTO> listarAutorizadosPorObra(Long idObra) {
        String sql = BASE_SELECT + " WHERE ot.ID_OBRA = :idObra AND ot.AUTORIZADO = 'S' ORDER BY ot.ID_OBRA_TRABAJADOR ASC";
        return jdbc.query(sql, new MapSqlParameterSource("idObra", idObra), rowMapper);
    }

    @Override
    public Optional<ObraTrabajadorDTO> buscarPorObraYTrabajador(Long idObra, Long idTrabajador) {
        String sql = BASE_SELECT + " WHERE ot.ID_OBRA = :idObra AND ot.ID_TRABAJADOR = :idTrabajador";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("idObra", idObra)
                .addValue("idTrabajador", idTrabajador);
        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, params, rowMapper));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public long contarTotalAsignados(Long idObra) {
        String sql = "SELECT COUNT(1) FROM OBRA_TRABAJADORES WHERE ID_OBRA = :idObra";
        Long count = jdbc.queryForObject(sql, new MapSqlParameterSource("idObra", idObra), Long.class);
        return count != null ? count : 0L;
    }

    @Override
    public long contarAutorizados(Long idObra) {
        String sql = "SELECT COUNT(1) FROM OBRA_TRABAJADORES WHERE ID_OBRA = :idObra AND AUTORIZADO = 'S'";
        Long count = jdbc.queryForObject(sql, new MapSqlParameterSource("idObra", idObra), Long.class);
        return count != null ? count : 0L;
    }
}
