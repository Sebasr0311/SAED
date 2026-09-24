package com.saed.backend.convivencia.repository.impl;

import com.saed.backend.convivencia.dto.MultaDTO;
import com.saed.backend.convivencia.repository.MultaRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public class MultaRepositoryImpl implements MultaRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public MultaRepositoryImpl(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }


    @Override
    public List<MultaDTO> findAll() {
        String sql = "SELECT m.ID_MULTA, u.IDENTIFICADOR as numeroApartamento, p.PRIMER_NOMBRE || ' ' || p.PRIMER_APELLIDO as nombreResidente, " +
                     "c.NOMBRE as tipo, m.MONTO, m.ESTADO, m.FECHA_IMPOSICION as FECHA_CREACION, " +
                     "m.MOTIVO, m.EVIDENCIA_URL, m.ID_SANCION_ORIGEN " +
                     "FROM MULTAS m " +
                     "JOIN UNIDADES u ON m.ID_UNIDAD = u.ID_UNIDAD " +
                     "LEFT JOIN PERSONAS p ON m.ID_PERSONA_INFRACTORA = p.ID_PERSONA " +
                     "JOIN CONCEPTOS_COBRO c ON m.ID_CONCEPTO = c.ID_CONCEPTO " +
                     "ORDER BY m.FECHA_IMPOSICION DESC";
        return jdbc.query(sql, (rs, rowNum) -> {
            MultaDTO dto = new MultaDTO();
            dto.setIdMulta(rs.getLong("ID_MULTA"));
            dto.setNumeroApartamento(rs.getString("numeroApartamento"));
            dto.setNombreResidente(rs.getString("nombreResidente"));
            dto.setTipo(rs.getString("tipo"));
            dto.setMonto(rs.getBigDecimal("MONTO"));
            dto.setEstado(rs.getString("ESTADO"));
            dto.setMotivo(rs.getString("MOTIVO"));
            dto.setEvidenciaUrl(rs.getString("EVIDENCIA_URL"));
            long sancionId = rs.getLong("ID_SANCION_ORIGEN");
            if (!rs.wasNull()) dto.setIdSancionOrigen(sancionId);
            if (rs.getTimestamp("FECHA_CREACION") != null) {
                dto.setFechaCreacion(rs.getTimestamp("FECHA_CREACION").toLocalDateTime());
            }
            return dto;
        });
    }

    @Override
    public MultaDTO findById(Long id) {
        String sql = "SELECT m.ID_MULTA, u.IDENTIFICADOR as numeroApartamento, p.PRIMER_NOMBRE || ' ' || p.PRIMER_APELLIDO as nombreResidente, " +
                     "c.NOMBRE as tipo, m.MONTO, m.ESTADO, m.FECHA_IMPOSICION as FECHA_CREACION, " +
                     "m.MOTIVO, m.EVIDENCIA_URL, m.ID_SANCION_ORIGEN " +
                     "FROM MULTAS m " +
                     "JOIN UNIDADES u ON m.ID_UNIDAD = u.ID_UNIDAD " +
                     "LEFT JOIN PERSONAS p ON m.ID_PERSONA_INFRACTORA = p.ID_PERSONA " +
                     "JOIN CONCEPTOS_COBRO c ON m.ID_CONCEPTO = c.ID_CONCEPTO " +
                     "WHERE m.ID_MULTA = :id";
        List<MultaDTO> list = jdbc.query(sql, new MapSqlParameterSource("id", id), (rs, rowNum) -> {
            MultaDTO dto = new MultaDTO();
            dto.setIdMulta(rs.getLong("ID_MULTA"));
            dto.setNumeroApartamento(rs.getString("numeroApartamento"));
            dto.setNombreResidente(rs.getString("nombreResidente"));
            dto.setTipo(rs.getString("tipo"));
            dto.setMonto(rs.getBigDecimal("MONTO"));
            dto.setEstado(rs.getString("ESTADO"));
            dto.setMotivo(rs.getString("MOTIVO"));
            dto.setEvidenciaUrl(rs.getString("EVIDENCIA_URL"));
            long sancionId = rs.getLong("ID_SANCION_ORIGEN");
            if (!rs.wasNull()) dto.setIdSancionOrigen(sancionId);
            if (rs.getTimestamp("FECHA_CREACION") != null) {
                dto.setFechaCreacion(rs.getTimestamp("FECHA_CREACION").toLocalDateTime());
            }
            return dto;
        });
        return list.stream().findFirst().orElse(null);
    }

    @Override
    public List<MultaDTO> findMisMultas(Long idPersona, List<Long> unidadesIds, boolean esSoloConviviente) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT m.ID_MULTA, u.IDENTIFICADOR as numeroApartamento, ")
           .append("TRIM(NVL(p.PRIMER_NOMBRE, '') || ' ' || NVL(p.PRIMER_APELLIDO, '')) as nombreResidente, ")
           .append("c.NOMBRE as tipo, m.MONTO, m.ESTADO, m.FECHA_IMPOSICION as FECHA_CREACION, ")
           .append("m.MOTIVO, m.EVIDENCIA_URL, m.ID_SANCION_ORIGEN ")
           .append("FROM MULTAS m ")
           .append("JOIN UNIDADES u ON m.ID_UNIDAD = u.ID_UNIDAD ")
           .append("LEFT JOIN PERSONAS p ON m.ID_PERSONA_INFRACTORA = p.ID_PERSONA ")
           .append("JOIN CONCEPTOS_COBRO c ON m.ID_CONCEPTO = c.ID_CONCEPTO ");

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("idPersona", idPersona);

        if (esSoloConviviente || unidadesIds == null || unidadesIds.isEmpty()) {
            sql.append("WHERE m.ID_PERSONA_INFRACTORA = :idPersona ");
        } else {
            sql.append("WHERE (m.ID_PERSONA_INFRACTORA = :idPersona OR m.ID_UNIDAD IN (:unidadesIds)) ");
            params.addValue("unidadesIds", unidadesIds);
        }
        sql.append("ORDER BY m.FECHA_IMPOSICION DESC");

        return jdbc.query(sql.toString(), params, (rs, rowNum) -> {
            MultaDTO dto = new MultaDTO();
            dto.setIdMulta(rs.getLong("ID_MULTA"));
            dto.setNumeroApartamento(rs.getString("numeroApartamento"));
            dto.setNombreResidente(rs.getString("nombreResidente"));
            dto.setTipo(rs.getString("tipo"));
            dto.setMonto(rs.getBigDecimal("MONTO"));
            dto.setEstado(rs.getString("ESTADO"));
            dto.setMotivo(rs.getString("MOTIVO"));
            dto.setEvidenciaUrl(rs.getString("EVIDENCIA_URL"));
            long sancionId = rs.getLong("ID_SANCION_ORIGEN");
            if (!rs.wasNull()) dto.setIdSancionOrigen(sancionId);
            if (rs.getTimestamp("FECHA_CREACION") != null) {
                dto.setFechaCreacion(rs.getTimestamp("FECHA_CREACION").toLocalDateTime());
            }
            return dto;
        });
    }

    @Override
    public void updateEstado(Long id, String estado) {
        String sql = "UPDATE MULTAS SET ESTADO = :estado WHERE ID_MULTA = :id";
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("estado", estado)
                .addValue("id", id));
    }
}
