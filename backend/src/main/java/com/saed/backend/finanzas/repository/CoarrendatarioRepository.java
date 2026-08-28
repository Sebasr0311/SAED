package com.saed.backend.finanzas.repository;

import com.saed.backend.finanzas.dto.*;
import com.saed.backend.context.SaedContextHolder;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class CoarrendatarioRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public CoarrendatarioRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<CoarrendatarioDTO> listarPorContrato(Long idContrato) {
        return jdbc.query(
            "SELECT cr.*, p.PRIMER_NOMBRE || ' ' || p.PRIMER_APELLIDO AS NOMBRE_PERSONA " +
            "FROM CONTRATO_RESIDENTE cr " +
            "JOIN PERSONAS p ON cr.ID_PERSONA = p.ID_PERSONA " +
            "WHERE cr.ID_CONTRATO = :idContrato ORDER BY cr.FECHA_VINCULACION DESC",
            Map.of("idContrato", idContrato),
            (rs, rowNum) -> new CoarrendatarioDTO(
                rs.getLong("ID_CONTRATO_RESIDENTE"),
                rs.getLong("ID_CONTRATO"),
                rs.getLong("ID_PERSONA"),
                rs.getString("TIPO_VINCULO"),
                rs.getString("ES_RESPONSABLE_PAGO"),
                rs.getDate("FECHA_VINCULACION") != null ? rs.getDate("FECHA_VINCULACION").toString() : null,
                rs.getString("ESTADO")
            )
        );
    }

    public CoarrendatarioDTO crear(CoarrendatarioCreateDTO req) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(
            "INSERT INTO CONTRATO_RESIDENTE (ID_CONTRATO, ID_PERSONA, TIPO_VINCULO, ES_RESPONSABLE_PAGO) " +
            "VALUES (:idContrato, :idPersona, :tipoVinculo, :esResponsable)",
            new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                .addValue("idContrato", req.idContrato())
                .addValue("idPersona", req.idPersona())
                .addValue("tipoVinculo", req.tipoVinculo() != null ? req.tipoVinculo() : "COARRENDATARIO")
                .addValue("esResponsable", req.esResponsablePago() != null ? req.esResponsablePago() : "N"),
            keyHolder,
            new String[]{"ID_CONTRATO_RESIDENTE"}
        );
        Long id = keyHolder.getKey().longValue();
        return jdbc.queryForObject(
            "SELECT cr.*, p.PRIMER_NOMBRE || ' ' || p.PRIMER_APELLIDO AS NOMBRE_PERSONA " +
            "FROM CONTRATO_RESIDENTE cr JOIN PERSONAS p ON cr.ID_PERSONA = p.ID_PERSONA " +
            "WHERE cr.ID_CONTRATO_RESIDENTE = :id",
            Map.of("id", id),
            (rs, rowNum) -> new CoarrendatarioDTO(
                rs.getLong("ID_CONTRATO_RESIDENTE"), rs.getLong("ID_CONTRATO"),
                rs.getLong("ID_PERSONA"), rs.getString("TIPO_VINCULO"),
                rs.getString("ES_RESPONSABLE_PAGO"),
                rs.getDate("FECHA_VINCULACION") != null ? rs.getDate("FECHA_VINCULACION").toString() : null,
                rs.getString("ESTADO")
            )
        );
    }

    public void actualizarEstado(Long id, String estado) {
        jdbc.update("UPDATE CONTRATO_RESIDENTE SET ESTADO = :estado WHERE ID_CONTRATO_RESIDENTE = :id",
            Map.of("id", id, "estado", estado));
    }

    public void eliminar(Long id) {
        jdbc.update("DELETE FROM CONTRATO_RESIDENTE WHERE ID_CONTRATO_RESIDENTE = :id", Map.of("id", id));
    }
}
