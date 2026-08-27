package com.saed.backend.finanzas.repository.impl;
import com.saed.backend.finanzas.dto.*;
import com.saed.backend.finanzas.repository.FinanzasRepository;
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
        String sql = "SELECT c.ID_CONTRATO, c.ID_UNIDAD, u.IDENTIFICADOR as numeroApartamento, c.ID_ARRENDATARIO_PRINCIPAL, " +
                     "p.PRIMER_NOMBRE || ' ' || p.PRIMER_APELLIDO as nombreArrendatario, c.NUMERO_CONTRATO, c.CANON_MENSUAL, c.DIA_CORTE_PAGO, " +
                     "c.FECHA_INICIO, c.FECHA_FIN, c.FECHA_TERMINACION_ANTICIPADA, c.ESTADO, c.TIPO_CONTRATO " +
                     "FROM CONTRATOS c " +
                     "JOIN UNIDADES u ON c.ID_UNIDAD = u.ID_UNIDAD " +
                     "JOIN PERSONAS p ON c.ID_ARRENDATARIO_PRINCIPAL = p.ID_PERSONA " +
                     "ORDER BY c.ID_CONTRATO DESC";
        return jdbcTemplate.query(sql, (rs, rowNum) -> new ContratoDTO(
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
        String sql = "INSERT INTO CONTRATOS (ID_UNIDAD, ID_ARRENDATARIO_PRINCIPAL, NUMERO_CONTRATO, CANON_MENSUAL, FECHA_INICIO, FECHA_FIN, TIPO_CONTRATO, ESTADO) " +
                     "VALUES (:idUnidad, :idArrendatario, :numContrato, :canon, :fInicio, :fFin, :tipo, 'ACTIVO')";
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("idUnidad", req.idApartamento())
            .addValue("idArrendatario", req.idResidente())
            .addValue("numContrato", numContrato)
            .addValue("canon", req.canonMensual())
            .addValue("fInicio", req.fechaInicio())
            .addValue("fFin", req.fechaFin())
            .addValue("tipo", req.tipoContrato());
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, params, keyHolder, new String[]{"ID_CONTRATO"});
        return keyHolder.getKey().longValue();
    }

    @Override
    public void updateEstadoContrato(Long id, String estado) {
        jdbcTemplate.update("UPDATE CONTRATOS SET ESTADO = :est WHERE ID_CONTRATO = :id",
            new MapSqlParameterSource().addValue("est", estado).addValue("id", id));
    }

    @Override
    public List<CuotaDTO> getCuotasPendientes() {
        String sql = "SELECT c.ID_CUOTA, c.ID_UNIDAD, u.IDENTIFICADOR as numeroApartamento, p.PRIMER_NOMBRE || ' ' || p.PRIMER_APELLIDO as nombreResidente, c.ID_CONTRATO, " +
                     "co.NOMBRE as concepto, c.PERIODO, c.VALOR_BASE, c.VALOR_TOTAL, c.SALDO_PENDIENTE, c.FECHA_VENCIMIENTO as FECHA_LIMITE, c.ESTADO " +
                     "FROM CUOTAS c " +
                     "JOIN UNIDADES u ON c.ID_UNIDAD = u.ID_UNIDAD " +
                     "JOIN CONCEPTOS_COBRO co ON c.ID_CONCEPTO = co.ID_CONCEPTO " +
                     "LEFT JOIN CONTRATOS con ON c.ID_CONTRATO = con.ID_CONTRATO " +
                     "LEFT JOIN PERSONAS p ON con.ID_ARRENDATARIO_PRINCIPAL = p.ID_PERSONA " +
                     "WHERE c.ESTADO = 'PENDIENTE'";
        return jdbcTemplate.query(sql, (rs, rowNum) -> new CuotaDTO(
            rs.getLong("ID_CUOTA"), rs.getLong("ID_UNIDAD"), rs.getString("numeroApartamento"), rs.getString("nombreResidente"),
            rs.getLong("ID_CONTRATO"), rs.getString("concepto"), rs.getString("PERIODO"),
            rs.getBigDecimal("VALOR_BASE"), rs.getBigDecimal("VALOR_TOTAL"), rs.getBigDecimal("SALDO_PENDIENTE"),
            rs.getDate("FECHA_LIMITE") != null ? rs.getDate("FECHA_LIMITE").toLocalDate() : null, rs.getString("ESTADO")
        ));
    }

    @Override
    public List<CuotaDTO> getCuotasByResidente(Long idResidente) {
        String sql = "SELECT c.ID_CUOTA, c.ID_UNIDAD, u.IDENTIFICADOR as numeroApartamento, p.PRIMER_NOMBRE || ' ' || p.PRIMER_APELLIDO as nombreResidente, c.ID_CONTRATO, " +
                     "co.NOMBRE as concepto, c.PERIODO, c.VALOR_BASE, c.VALOR_TOTAL, c.SALDO_PENDIENTE, c.FECHA_VENCIMIENTO as FECHA_LIMITE, c.ESTADO " +
                     "FROM CUOTAS c " +
                     "JOIN UNIDADES u ON c.ID_UNIDAD = u.ID_UNIDAD " +
                     "JOIN CONCEPTOS_COBRO co ON c.ID_CONCEPTO = co.ID_CONCEPTO " +
                     "JOIN CONTRATOS con ON c.ID_CONTRATO = con.ID_CONTRATO " +
                     "JOIN PERSONAS p ON con.ID_ARRENDATARIO_PRINCIPAL = p.ID_PERSONA " +
                     "WHERE con.ID_ARRENDATARIO_PRINCIPAL = :idRes " +
                     "ORDER BY c.FECHA_VENCIMIENTO as FECHA_LIMITE DESC";
        return jdbcTemplate.query(sql, new MapSqlParameterSource("idRes", idResidente), (rs, rowNum) -> new CuotaDTO(
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
        String sql = "UPDATE CUOTAS SET SALDO_PENDIENTE = GREATEST(SALDO_PENDIENTE - :monto, 0), " +
                     "ESTADO = CASE WHEN SALDO_PENDIENTE - :monto <= 0 THEN 'PAGADA' ELSE ESTADO END " +
                     "WHERE ID_CUOTA = :idCuota";
        jdbcTemplate.update(sql, new MapSqlParameterSource().addValue("monto", montoAplicado).addValue("idCuota", idCuota));
    }

    @Override
    public ContratoDetalleDTO getContratoDetalle(Long idContrato) {
        String sql = "SELECT c.ID_CONTRATO, c.NUMERO_CONTRATO, c.FECHA_INICIO, c.FECHA_FIN, c.TIPO_CONTRATO, c.ESTADO, " +
                     "u.IDENTIFICADOR as numeroApartamento, p.PRIMER_NOMBRE as nombresResidente, p.PRIMER_APELLIDO as apellidosResidente, " +
                     "p.DOCUMENTO as numeroDocumentoResidente, p.TELEFONO as telefonoResidente, p.EMAIL as correoResidente, " +
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
        String sql = "INSERT INTO CUOTAS (ID_UNIDAD, ID_CONCEPTO, ID_CONTRATO, PERIODO, VALOR_BASE, VALOR_TOTAL, SALDO_PENDIENTE, FECHA_VENCIMIENTO, ESTADO) " +
                     "SELECT c.ID_UNIDAD, " +
                     "(SELECT ID_CONCEPTO FROM CONCEPTOS_COBRO WHERE TIPO = 'ARRIENDO' AND ID_PROPIEDAD = u.ID_PROPIEDAD FETCH FIRST 1 ROWS ONLY), " +
                     "c.ID_CONTRATO, " +
                     "TO_CHAR(c.FECHA_INICIO, 'YYYY-MM'), " +
                     "c.CANON_MENSUAL, c.CANON_MENSUAL, c.CANON_MENSUAL, " +
                     "c.FECHA_INICIO + (CASE WHEN c.DIA_CORTE_PAGO > 0 THEN c.DIA_CORTE_PAGO ELSE 5 END), " +
                     "'PENDIENTE' " +
                     "FROM CONTRATOS c " +
                     "JOIN UNIDADES u ON c.ID_UNIDAD = u.ID_UNIDAD " +
                     "WHERE c.ID_CONTRATO = :id AND NOT EXISTS (SELECT 1 FROM CUOTAS cu WHERE cu.ID_CONTRATO = c.ID_CONTRATO)";
        jdbcTemplate.update(sql, new MapSqlParameterSource("id", idContrato));
    }
}
