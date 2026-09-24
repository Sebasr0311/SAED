package com.saed.backend.finanzas.repository;

import com.saed.backend.finanzas.dto.CoarrendatarioCreateDTO;
import com.saed.backend.finanzas.dto.CoarrendatarioDTO;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class CoarrendatarioRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public CoarrendatarioRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<CoarrendatarioDTO> listarPorContrato(Long idContrato) {
        String sql = "SELECT cr.ID_CONTRATO_RESIDENTE, cr.ID_CONTRATO, cr.ID_PERSONA, cr.TIPO_VINCULO, " +
                     "cr.ES_RESPONSABLE_PAGO, TO_CHAR(cr.FECHA_VINCULACION, 'YYYY-MM-DD') AS FECHA_VINC, cr.ESTADO, " +
                     "p.PRIMER_NOMBRE || ' ' || p.PRIMER_APELLIDO AS NOMBRE_PERSONA, " +
                     "p.NUMERO_DOCUMENTO, p.TELEFONO, p.EMAIL " +
                     "FROM CONTRATO_RESIDENTE cr " +
                     "JOIN PERSONAS p ON cr.ID_PERSONA = p.ID_PERSONA " +
                     "WHERE cr.ID_CONTRATO = :idContrato " +
                     "ORDER BY cr.ID_CONTRATO_RESIDENTE ASC";

        return jdbc.query(sql, Map.of("idContrato", idContrato), (rs, rowNum) -> new CoarrendatarioDTO(
            rs.getLong("ID_CONTRATO_RESIDENTE"),
            rs.getLong("ID_CONTRATO"),
            rs.getLong("ID_PERSONA"),
            rs.getString("TIPO_VINCULO"),
            rs.getString("ES_RESPONSABLE_PAGO"),
            rs.getString("FECHA_VINC"),
            rs.getString("ESTADO"),
            rs.getString("NOMBRE_PERSONA"),
            rs.getString("NUMERO_DOCUMENTO"),
            rs.getString("TELEFONO"),
            rs.getString("EMAIL")
        ));
    }

    public Optional<CoarrendatarioDTO> buscarPorId(Long idContratoResidente) {
        String sql = "SELECT cr.ID_CONTRATO_RESIDENTE, cr.ID_CONTRATO, cr.ID_PERSONA, cr.TIPO_VINCULO, " +
                     "cr.ES_RESPONSABLE_PAGO, TO_CHAR(cr.FECHA_VINCULACION, 'YYYY-MM-DD') AS FECHA_VINC, cr.ESTADO, " +
                     "p.PRIMER_NOMBRE || ' ' || p.PRIMER_APELLIDO AS NOMBRE_PERSONA, " +
                     "p.NUMERO_DOCUMENTO, p.TELEFONO, p.EMAIL " +
                     "FROM CONTRATO_RESIDENTE cr " +
                     "JOIN PERSONAS p ON cr.ID_PERSONA = p.ID_PERSONA " +
                     "WHERE cr.ID_CONTRATO_RESIDENTE = :id";

        try {
            CoarrendatarioDTO dto = jdbc.queryForObject(sql, Map.of("id", idContratoResidente), (rs, rowNum) -> new CoarrendatarioDTO(
                rs.getLong("ID_CONTRATO_RESIDENTE"),
                rs.getLong("ID_CONTRATO"),
                rs.getLong("ID_PERSONA"),
                rs.getString("TIPO_VINCULO"),
                rs.getString("ES_RESPONSABLE_PAGO"),
                rs.getString("FECHA_VINC"),
                rs.getString("ESTADO"),
                rs.getString("NOMBRE_PERSONA"),
                rs.getString("NUMERO_DOCUMENTO"),
                rs.getString("TELEFONO"),
                rs.getString("EMAIL")
            ));
            return Optional.ofNullable(dto);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public boolean existeEnContrato(Long idContrato, Long idPersona) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(1) FROM CONTRATO_RESIDENTE WHERE ID_CONTRATO = :idContrato AND ID_PERSONA = :idPersona",
            Map.of("idContrato", idContrato, "idPersona", idPersona),
            Integer.class
        );
        return count != null && count > 0;
    }

    public CoarrendatarioDTO crear(CoarrendatarioCreateDTO req) {
        String tipoVinculo = req.tipoVinculo() != null ? req.tipoVinculo().trim().toUpperCase() : "COARRENDATARIO";
        if (!"COARRENDATARIO".equals(tipoVinculo) && !"OCUPANTE_AUTORIZADO".equals(tipoVinculo) && !"ARRENDATARIO_PRINCIPAL".equals(tipoVinculo)) {
            tipoVinculo = "COARRENDATARIO";
        }
        String esResponsable = req.esResponsablePago() != null && "S".equalsIgnoreCase(req.esResponsablePago()) ? "S" : "N";

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(
            "INSERT INTO CONTRATO_RESIDENTE (ID_CONTRATO, ID_PERSONA, TIPO_VINCULO, ES_RESPONSABLE_PAGO, ESTADO, FECHA_VINCULACION) " +
            "VALUES (:idContrato, :idPersona, :tipoVinculo, :esResponsable, 'ACTIVO', TRUNC(SYSDATE))",
            new MapSqlParameterSource()
                .addValue("idContrato", req.idContrato())
                .addValue("idPersona", req.idPersona())
                .addValue("tipoVinculo", tipoVinculo)
                .addValue("esResponsable", esResponsable),
            keyHolder,
            new String[]{"ID_CONTRATO_RESIDENTE"}
        );

        Long id = keyHolder.getKey() != null ? keyHolder.getKey().longValue() : null;

        // Si se solicitó sincronizar habitabilidad, vincular a RESIDENTES_UNIDAD
        if (Boolean.TRUE.equals(req.sincronizarHabitabilidad())) {
            sincronizarHabitanteFisico(req.idContrato(), req.idPersona(), tipoVinculo);
        }

        return buscarPorId(id).orElseThrow(() -> new IllegalStateException("Error recuperando coarrendatario recién creado"));
    }

    public void sincronizarHabitanteFisico(Long idContrato, Long idPersona, String tipoVinculo) {
        try {
            Long idUnidad = jdbc.queryForObject(
                "SELECT ID_UNIDAD FROM CONTRATOS WHERE ID_CONTRATO = :idContrato",
                Map.of("idContrato", idContrato),
                Long.class
            );
            if (idUnidad != null) {
                String tipoRes = "OCUPANTE_AUTORIZADO".equalsIgnoreCase(tipoVinculo) ? "CONVIVIENTE" : "CONVIVIENTE";
                jdbc.update(
                    "MERGE INTO RESIDENTES_UNIDAD r " +
                    "USING (SELECT :idUnidad AS id_u, :idPersona AS id_p FROM DUAL) src " +
                    "ON (r.ID_UNIDAD = src.id_u AND r.ID_PERSONA = src.id_p) " +
                    "WHEN MATCHED THEN " +
                    "    UPDATE SET r.ESTADO = 'ACTIVO', r.TIPO_RESIDENTE = :tipoRes, r.FECHA_INICIO = TRUNC(SYSDATE) " +
                    "WHEN NOT MATCHED THEN " +
                    "    INSERT (ID_UNIDAD, ID_PERSONA, TIPO_RESIDENTE, FECHA_INICIO, ESTADO) " +
                    "    VALUES (src.id_u, src.id_p, :tipoRes, TRUNC(SYSDATE), 'ACTIVO')",
                    Map.of("idUnidad", idUnidad, "idPersona", idPersona, "tipoRes", tipoRes)
                );
            }
        } catch (Exception ignored) {}
    }

    public void actualizarEstado(Long id, String estado) {
        String est = "ACTIVO".equalsIgnoreCase(estado) ? "ACTIVO" : "INACTIVO";
        jdbc.update("UPDATE CONTRATO_RESIDENTE SET ESTADO = :estado WHERE ID_CONTRATO_RESIDENTE = :id",
            Map.of("id", id, "estado", est));
    }

    public void eliminar(Long id) {
        jdbc.update("DELETE FROM CONTRATO_RESIDENTE WHERE ID_CONTRATO_RESIDENTE = :id", Map.of("id", id));
    }

    public void eliminarPorContratoYPersona(Long idContrato, Long idPersona) {
        jdbc.update(
            "DELETE FROM CONTRATO_RESIDENTE WHERE ID_CONTRATO = :idContrato AND ID_PERSONA = :idPersona AND TIPO_VINCULO != 'ARRENDATARIO_PRINCIPAL'",
            Map.of("idContrato", idContrato, "idPersona", idPersona)
        );
    }
}
