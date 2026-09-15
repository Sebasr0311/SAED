package com.saed.backend.finanzas.repository.impl;
import com.saed.backend.finanzas.dto.*;
import com.saed.backend.finanzas.repository.FinanzasRepository;
import com.saed.backend.context.SaedContextHolder;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.math.BigDecimal;

@Repository
public class FinanzasRepositoryImpl implements FinanzasRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    public FinanzasRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    @Override
    public List<ContratoDTO> getContratos() {
        com.saed.backend.context.SaedContext ctx = SaedContextHolder.getContext();
        Long propId = (ctx != null) ? ctx.getPropertyId() : null;
        Long orgId = (ctx != null) ? ctx.getOrganizationId() : null;
        MapSqlParameterSource params = new MapSqlParameterSource();
        StringBuilder sql = new StringBuilder(
            "SELECT c.ID_CONTRATO, c.ID_UNIDAD, u.IDENTIFICADOR as numeroApartamento, c.ID_ARRENDATARIO_PRINCIPAL, " +
            "p.PRIMER_NOMBRE || ' ' || p.PRIMER_APELLIDO as nombreArrendatario, c.NUMERO_CONTRATO, c.CANON_MENSUAL, c.DIA_CORTE_PAGO, " +
            "c.FECHA_INICIO, c.FECHA_FIN, c.FECHA_TERMINACION_ANTICIPADA, c.ESTADO, c.TIPO_CONTRATO " +
            "FROM CONTRATOS c " +
            "JOIN UNIDADES u ON c.ID_UNIDAD = u.ID_UNIDAD " +
            "JOIN PROPIEDADES pr ON u.ID_PROPIEDAD = pr.ID_PROPIEDAD " +
            "JOIN PERSONAS p ON c.ID_ARRENDATARIO_PRINCIPAL = p.ID_PERSONA "
        );
        if (propId != null) {
            sql.append("WHERE u.ID_PROPIEDAD = :propId ");
            params.addValue("propId", propId);
        } else if (orgId != null) {
            sql.append("WHERE pr.ID_ORGANIZACION = :orgId ");
            params.addValue("orgId", orgId);
        }
        sql.append("ORDER BY c.ID_CONTRATO DESC");

        return jdbcTemplate.query(sql.toString(), params, (rs, rowNum) -> new ContratoDTO(
            rs.getLong("ID_CONTRATO"), rs.getLong("ID_UNIDAD"), rs.getString("numeroApartamento"),
            rs.getLong("ID_ARRENDATARIO_PRINCIPAL"), rs.getString("nombreArrendatario"), rs.getString("NUMERO_CONTRATO"),
            rs.getBigDecimal("CANON_MENSUAL"), rs.getInt("DIA_CORTE_PAGO"),
            rs.getDate("FECHA_INICIO") != null ? rs.getDate("FECHA_INICIO").toLocalDate() : null,
            rs.getDate("FECHA_FIN") != null ? rs.getDate("FECHA_FIN").toLocalDate() : null,
            rs.getDate("FECHA_TERMINACION_ANTICIPADA") != null ? rs.getDate("FECHA_TERMINACION_ANTICIPADA").toLocalDate() : null,
            rs.getString("ESTADO"), rs.getString("TIPO_CONTRATO")
        ));
    }

    @Override
    public Long createContrato(ContratoRequestDTO req, String numContrato) {
        java.time.LocalDate fechaFinCalc = req.fechaFin() != null ? req.fechaFin() : req.fechaInicio().plusYears(1);
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("idUnidad", req.idApartamento())
            .addValue("idArrendatario", req.idResidente())
            .addValue("numContrato", numContrato)
            .addValue("canon", req.canonMensual())
            .addValue("fInicio", req.fechaInicio())
            .addValue("fFin", fechaFinCalc)
            .addValue("tipo", req.tipoContrato());

        String sql;
        if (req.idPlantilla() != null) {
            sql = "INSERT INTO CONTRATOS (ID_UNIDAD, ID_ARRENDATARIO_PRINCIPAL, NUMERO_CONTRATO, CANON_MENSUAL, FECHA_INICIO, FECHA_FIN, TIPO_CONTRATO, ESTADO, ID_PLANTILLA) " +
                  "VALUES (:idUnidad, :idArrendatario, :numContrato, :canon, :fInicio, :fFin, :tipo, 'ACTIVO', :idPlantilla)";
            params.addValue("idPlantilla", req.idPlantilla());
        } else {
            sql = "INSERT INTO CONTRATOS (ID_UNIDAD, ID_ARRENDATARIO_PRINCIPAL, NUMERO_CONTRATO, CANON_MENSUAL, FECHA_INICIO, FECHA_FIN, TIPO_CONTRATO, ESTADO) " +
                  "VALUES (:idUnidad, :idArrendatario, :numContrato, :canon, :fInicio, :fFin, :tipo, 'ACTIVO')";
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, params, keyHolder, new String[]{"ID_CONTRATO"});
        Long idContrato = keyHolder.getKey() != null ? keyHolder.getKey().longValue() : null;

        // Sincronizar habitante físico en RESIDENTES_UNIDAD de forma inmediata y consistente
        jdbcTemplate.update(
            "MERGE INTO RESIDENTES_UNIDAD r " +
            "USING (SELECT :idUnidad AS id_u, :idArrendatario AS id_p FROM DUAL) src " +
            "ON (r.ID_UNIDAD = src.id_u AND r.ID_PERSONA = src.id_p) " +
            "WHEN MATCHED THEN " +
            "    UPDATE SET r.ESTADO = 'ACTIVO', r.TIPO_RESIDENTE = 'ARRENDATARIO', r.FECHA_INICIO = :fInicio " +
            "WHEN NOT MATCHED THEN " +
            "    INSERT (ID_UNIDAD, ID_PERSONA, TIPO_RESIDENTE, FECHA_INICIO, ESTADO) " +
            "    VALUES (src.id_u, src.id_p, 'ARRENDATARIO', :fInicio, 'ACTIVO')",
            params
        );

        return idContrato;
    }

    @Override
    public void updateEstadoContrato(Long id, String estado) {
        jdbcTemplate.update("UPDATE CONTRATOS SET ESTADO = :est WHERE ID_CONTRATO = :id",
            new MapSqlParameterSource().addValue("est", estado).addValue("id", id));
    }

    @Override
    public List<CuotaDTO> getCuotasPendientes() {
        Long propId = (SaedContextHolder.getContext() != null) ? SaedContextHolder.getContext().getPropertyId() : null;
        String sql = "SELECT c.ID_CUOTA, c.ID_UNIDAD, u.IDENTIFICADOR as numeroApartamento, p.PRIMER_NOMBRE || ' ' || p.PRIMER_APELLIDO as nombreResidente, c.ID_CONTRATO, " +
                     "co.NOMBRE as concepto, c.PERIODO, c.VALOR_BASE, c.VALOR_TOTAL, c.SALDO_PENDIENTE, c.FECHA_VENCIMIENTO as FECHA_LIMITE, c.ESTADO " +
                     "FROM CUOTAS c " +
                     "JOIN UNIDADES u ON c.ID_UNIDAD = u.ID_UNIDAD " +
                     "JOIN CONCEPTOS_COBRO co ON c.ID_CONCEPTO = co.ID_CONCEPTO " +
                     "LEFT JOIN CONTRATOS con ON c.ID_CONTRATO = con.ID_CONTRATO " +
                     "LEFT JOIN PERSONAS p ON con.ID_ARRENDATARIO_PRINCIPAL = p.ID_PERSONA " +
                     "WHERE c.ESTADO = 'PENDIENTE'" + (propId != null ? " AND u.ID_PROPIEDAD = :propId" : "");
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (propId != null) params.addValue("propId", propId);
        return jdbcTemplate.query(sql, params, (rs, rowNum) -> new CuotaDTO(
            rs.getLong("ID_CUOTA"), rs.getLong("ID_UNIDAD"), rs.getString("numeroApartamento"), rs.getString("nombreResidente"),
            rs.getLong("ID_CONTRATO"), rs.getString("concepto"), rs.getString("PERIODO"),
            rs.getBigDecimal("VALOR_BASE"), rs.getBigDecimal("VALOR_TOTAL"), rs.getBigDecimal("SALDO_PENDIENTE"),
            rs.getDate("FECHA_LIMITE") != null ? rs.getDate("FECHA_LIMITE").toLocalDate() : null, rs.getString("ESTADO")
        ));
    }

    @Override
    public List<CuotaDTO> getCuotasByResidente(Long idResidente) {
        Long propId = (SaedContextHolder.getContext() != null) ? SaedContextHolder.getContext().getPropertyId() : null;
        String sql = "SELECT c.ID_CUOTA, c.ID_UNIDAD, u.IDENTIFICADOR as numeroApartamento, p.PRIMER_NOMBRE || ' ' || p.PRIMER_APELLIDO as nombreResidente, c.ID_CONTRATO, " +
                     "co.NOMBRE as concepto, c.PERIODO, c.VALOR_BASE, c.VALOR_TOTAL, c.SALDO_PENDIENTE, c.FECHA_VENCIMIENTO as FECHA_LIMITE, c.ESTADO " +
                     "FROM CUOTAS c " +
                     "JOIN UNIDADES u ON c.ID_UNIDAD = u.ID_UNIDAD " +
                     "JOIN CONCEPTOS_COBRO co ON c.ID_CONCEPTO = co.ID_CONCEPTO " +
                     "JOIN CONTRATOS con ON c.ID_CONTRATO = con.ID_CONTRATO " +
                     "JOIN PERSONAS p ON con.ID_ARRENDATARIO_PRINCIPAL = p.ID_PERSONA " +
                     "WHERE con.ID_ARRENDATARIO_PRINCIPAL = :idRes" +
                     (propId != null ? " AND u.ID_PROPIEDAD = :propId " : " ") +
                     "ORDER BY c.FECHA_VENCIMIENTO DESC";
        MapSqlParameterSource params = new MapSqlParameterSource("idRes", idResidente);
        if (propId != null) params.addValue("propId", propId);
        return jdbcTemplate.query(sql, params, (rs, rowNum) -> new CuotaDTO(
            rs.getLong("ID_CUOTA"), rs.getLong("ID_UNIDAD"), rs.getString("numeroApartamento"), rs.getString("nombreResidente"),
            rs.getLong("ID_CONTRATO"), rs.getString("concepto"), rs.getString("PERIODO"),
            rs.getBigDecimal("VALOR_BASE"), rs.getBigDecimal("VALOR_TOTAL"), rs.getBigDecimal("SALDO_PENDIENTE"),
            rs.getDate("FECHA_LIMITE") != null ? rs.getDate("FECHA_LIMITE").toLocalDate() : null, rs.getString("ESTADO")
        ));
    }

    @Override
    public Long registrarPago(PagoRequestDTO req, Long idUnidad) {
        String sql = "INSERT INTO PAGOS (ID_UNIDAD, MONTO_TOTAL, METODO_PAGO, REFERENCIA_COMPROBANTE, ESTADO) " +
                     "VALUES (:unidad, :monto, :metodo, :ref, 'APROBADO')";
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("unidad", idUnidad)
            .addValue("monto", req.valorPagado())
            .addValue("metodo", req.metodoPago())
            .addValue("ref", req.referencia());
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, params, keyHolder, new String[]{"ID_PAGO"});
        Long idPago = keyHolder.getKey().longValue();

        String sqlDetalle = "INSERT INTO PAGO_DETALLE (ID_PAGO, ID_CUOTA, MONTO_APLICADO) VALUES (:pago, :cuota, :monto)";
        jdbcTemplate.update(sqlDetalle, new MapSqlParameterSource()
            .addValue("pago", idPago).addValue("cuota", req.idCuota()).addValue("monto", req.valorPagado()));
            
        return idPago;
    }

    @Override
    public void actualizarSaldoCuota(Long idCuota, BigDecimal montoAplicado) {
        Long propId = (SaedContextHolder.getContext() != null) ? SaedContextHolder.getContext().getPropertyId() : null;
        String sql = "UPDATE CUOTAS SET SALDO_PENDIENTE = GREATEST(SALDO_PENDIENTE - :monto, 0), " +
                     "ESTADO = CASE WHEN SALDO_PENDIENTE - :monto <= 0 THEN 'PAGADA' ELSE ESTADO END " +
                     "WHERE ID_CUOTA = :idCuota" +
                     (propId != null ? " AND ID_UNIDAD IN (SELECT ID_UNIDAD FROM UNIDADES WHERE ID_PROPIEDAD = :propId)" : "");
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("monto", montoAplicado)
                .addValue("idCuota", idCuota);
        if (propId != null) params.addValue("propId", propId);
        jdbcTemplate.update(sql, params);
    }

    @Override
    public ContratoDetalleDTO getContratoDetalle(Long idContrato) {
        String sql = "SELECT c.ID_CONTRATO, c.NUMERO_CONTRATO, c.FECHA_INICIO, c.FECHA_FIN, c.TIPO_CONTRATO, c.ESTADO, " +
                     "u.IDENTIFICADOR as numeroApartamento, p.PRIMER_NOMBRE as nombresResidente, p.PRIMER_APELLIDO as apellidosResidente, " +
                     "p.NUMERO_DOCUMENTO as numeroDocumentoResidente, p.TELEFONO as telefonoResidente, p.EMAIL as correoResidente, " +
                     "c.CANON_MENSUAL, c.DIA_CORTE_PAGO " +
                     "FROM CONTRATOS c " +
                     "JOIN UNIDADES u ON c.ID_UNIDAD = u.ID_UNIDAD " +
                     "JOIN PERSONAS p ON c.ID_ARRENDATARIO_PRINCIPAL = p.ID_PERSONA " +
                     "WHERE c.ID_CONTRATO = :id";
                     
        List<ContratoDetalleDTO> list = jdbcTemplate.query(sql, new MapSqlParameterSource("id", idContrato), (rs, rowNum) -> {
            ContratoDetalleDTO dto = new ContratoDetalleDTO();
            dto.setIdContrato(rs.getInt("ID_CONTRATO"));
            dto.setNumeroContrato(rs.getString("NUMERO_CONTRATO"));
            dto.setFechaGeneracion(java.time.LocalDate.now());
            if (rs.getDate("FECHA_INICIO") != null) dto.setFechaInicio(rs.getDate("FECHA_INICIO").toLocalDate());
            if (rs.getDate("FECHA_FIN") != null) dto.setFechaFin(rs.getDate("FECHA_FIN").toLocalDate());
            dto.setTipoContrato(rs.getString("TIPO_CONTRATO"));
            dto.setEstado(rs.getString("ESTADO"));
            dto.setNumeroApartamento(rs.getString("numeroApartamento"));
            dto.setNombreCompletoResidente(rs.getString("nombresResidente") + " " + rs.getString("apellidosResidente"));
            dto.setNombresResidente(rs.getString("nombresResidente"));
            dto.setApellidosResidente(rs.getString("apellidosResidente"));
            dto.setNumeroDocumentoResidente(rs.getString("numeroDocumentoResidente"));
            dto.setTelefonoResidente(rs.getString("telefonoResidente"));
            dto.setCorreoResidente(rs.getString("correoResidente"));
            dto.setValorCanon(rs.getBigDecimal("CANON_MENSUAL"));
            dto.setDiaPago(rs.getInt("DIA_CORTE_PAGO"));
            return dto;
        });
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public void generarCuotasIniciales(Long idContrato) {
        try {
            jdbcTemplate.update(
                "MERGE INTO CONCEPTOS_COBRO cc " +
                "USING (SELECT u.ID_PROPIEDAD as p, p.ID_ORGANIZACION as o, 'CANON_ARRIENDO' as c, 'Canon de Arriendo' as n, 'ARRIENDO' as t " +
                "       FROM CONTRATOS con JOIN UNIDADES u ON con.ID_UNIDAD = u.ID_UNIDAD JOIN PROPIEDADES p ON u.ID_PROPIEDAD = p.ID_PROPIEDAD " +
                "       WHERE con.ID_CONTRATO = :id) s " +
                "ON (cc.ID_PROPIEDAD = s.p AND cc.TIPO = s.t) " +
                "WHEN NOT MATCHED THEN INSERT (ID_ORGANIZACION, ID_PROPIEDAD, CODIGO, NOMBRE, TIPO, APLICA_MORA, ESTADO) " +
                "VALUES (s.o, s.p, s.c, s.n, s.t, 'S', 'ACTIVO')",
                new MapSqlParameterSource("id", idContrato)
            );
        } catch (Exception ignored) {}

        String sql = "INSERT INTO CUOTAS (ID_UNIDAD, ID_CONCEPTO, ID_CONTRATO, PERIODO, VALOR_BASE, SALDO_PENDIENTE, FECHA_VENCIMIENTO, ESTADO) " +
                     "SELECT c.ID_UNIDAD, " +
                     "COALESCE(" +
                     "    (SELECT ID_CONCEPTO FROM CONCEPTOS_COBRO WHERE TIPO = 'ARRIENDO' AND ID_PROPIEDAD = u.ID_PROPIEDAD FETCH FIRST 1 ROWS ONLY), " +
                     "    (SELECT ID_CONCEPTO FROM CONCEPTOS_COBRO WHERE ID_PROPIEDAD = u.ID_PROPIEDAD FETCH FIRST 1 ROWS ONLY), " +
                     "    1" +
                     "), " +
                     "c.ID_CONTRATO, " +
                     "TO_CHAR(c.FECHA_INICIO, 'YYYY-MM'), " +
                     "c.CANON_MENSUAL, c.CANON_MENSUAL, " +
                     "c.FECHA_INICIO + (CASE WHEN c.DIA_CORTE_PAGO > 0 THEN c.DIA_CORTE_PAGO ELSE 5 END), " +
                     "'PENDIENTE' " +
                     "FROM CONTRATOS c " +
                     "JOIN UNIDADES u ON c.ID_UNIDAD = u.ID_UNIDAD " +
                     "WHERE c.ID_CONTRATO = :id " +
                     "  AND NOT EXISTS (SELECT 1 FROM CUOTAS cu WHERE cu.ID_CONTRATO = c.ID_CONTRATO) " +
                     "  AND NOT EXISTS (SELECT 1 FROM CUOTAS cu2 WHERE cu2.ID_UNIDAD = c.ID_UNIDAD " +
                     "                    AND cu2.PERIODO = TO_CHAR(c.FECHA_INICIO, 'YYYY-MM') " +
                     "                    AND cu2.ID_CONCEPTO = COALESCE(" +
                     "                        (SELECT ID_CONCEPTO FROM CONCEPTOS_COBRO WHERE TIPO = 'ARRIENDO' AND ID_PROPIEDAD = u.ID_PROPIEDAD FETCH FIRST 1 ROWS ONLY), " +
                     "                        (SELECT ID_CONCEPTO FROM CONCEPTOS_COBRO WHERE ID_PROPIEDAD = u.ID_PROPIEDAD FETCH FIRST 1 ROWS ONLY), 1))";
        try {
            jdbcTemplate.update(sql, new MapSqlParameterSource("id", idContrato));
        } catch (Exception ignored) {}
    }
}
