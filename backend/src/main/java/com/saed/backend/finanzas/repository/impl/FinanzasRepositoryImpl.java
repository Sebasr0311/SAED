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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.sql.Timestamp;

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

        StringBuilder cols = new StringBuilder("ID_UNIDAD, ID_ARRENDATARIO_PRINCIPAL, NUMERO_CONTRATO, CANON_MENSUAL, FECHA_INICIO, FECHA_FIN, TIPO_CONTRATO, ESTADO");
        StringBuilder vals = new StringBuilder(":idUnidad, :idArrendatario, :numContrato, :canon, :fInicio, :fFin, :tipo, 'ACTIVO'");

        if (req.idPlantilla() != null) {
            cols.append(", ID_PLANTILLA");
            vals.append(", :idPlantilla");
            params.addValue("idPlantilla", req.idPlantilla());
        }

        if (req.idTutor() != null) {
            cols.append(", ID_TUTOR");
            vals.append(", :idTutor");
            params.addValue("idTutor", req.idTutor());
        }

        String sql = "INSERT INTO CONTRATOS (" + cols + ") VALUES (" + vals + ")";

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, params, keyHolder, new String[]{"ID_CONTRATO"});
        Long idContrato = keyHolder.getKey() != null ? keyHolder.getKey().longValue() : null;

        // Sincronizar arrendatario principal en CONTRATO_RESIDENTE
        jdbcTemplate.update(
            "MERGE INTO CONTRATO_RESIDENTE cr " +
            "USING (SELECT :idContrato AS id_c, :idArrendatario AS id_p FROM DUAL) src " +
            "ON (cr.ID_CONTRATO = src.id_c AND cr.ID_PERSONA = src.id_p) " +
            "WHEN MATCHED THEN " +
            "    UPDATE SET cr.ESTADO = 'ACTIVO', cr.TIPO_VINCULO = 'ARRENDATARIO_PRINCIPAL', cr.ES_RESPONSABLE_PAGO = 'S' " +
            "WHEN NOT MATCHED THEN " +
            "    INSERT (ID_CONTRATO, ID_PERSONA, TIPO_VINCULO, ES_RESPONSABLE_PAGO, ESTADO, FECHA_VINCULACION) " +
            "    VALUES (src.id_c, src.id_p, 'ARRENDATARIO_PRINCIPAL', 'S', 'ACTIVO', TRUNC(SYSDATE))",
            new MapSqlParameterSource()
                .addValue("idContrato", idContrato)
                .addValue("idArrendatario", req.idResidente())
        );

        // Registrar coarrendatarios en CONTRATO_RESIDENTE
        if (req.coarrendatarios() != null && !req.coarrendatarios().isEmpty()) {
            for (CoarrendatarioCreateDTO co : req.coarrendatarios()) {
                if (co.idPersona() == null || co.idPersona().equals(req.idResidente())) continue;
                String tipoVinculo = co.tipoVinculo() != null ? co.tipoVinculo().trim().toUpperCase() : "COARRENDATARIO";
                if (!"COARRENDATARIO".equals(tipoVinculo) && !"OCUPANTE_AUTORIZADO".equals(tipoVinculo)) {
                    tipoVinculo = "COARRENDATARIO";
                }
                String esResp = co.esResponsablePago() != null && "S".equalsIgnoreCase(co.esResponsablePago()) ? "S" : "N";

                jdbcTemplate.update(
                    "MERGE INTO CONTRATO_RESIDENTE cr " +
                    "USING (SELECT :idContrato AS id_c, :idP AS id_p FROM DUAL) src " +
                    "ON (cr.ID_CONTRATO = src.id_c AND cr.ID_PERSONA = src.id_p) " +
                    "WHEN MATCHED THEN " +
                    "    UPDATE SET cr.ESTADO = 'ACTIVO', cr.TIPO_VINCULO = :tipoV, cr.ES_RESPONSABLE_PAGO = :esResp " +
                    "WHEN NOT MATCHED THEN " +
                    "    INSERT (ID_CONTRATO, ID_PERSONA, TIPO_VINCULO, ES_RESPONSABLE_PAGO, ESTADO, FECHA_VINCULACION) " +
                    "    VALUES (src.id_c, src.id_p, :tipoV, :esResp, 'ACTIVO', TRUNC(SYSDATE))",
                    new MapSqlParameterSource()
                        .addValue("idContrato", idContrato)
                        .addValue("idP", co.idPersona())
                        .addValue("tipoV", tipoVinculo)
                        .addValue("esResp", esResp)
                );

                if (Boolean.TRUE.equals(req.sincronizarHabitabilidad()) || Boolean.TRUE.equals(co.sincronizarHabitabilidad())) {
                    jdbcTemplate.update(
                        "MERGE INTO RESIDENTES_UNIDAD r " +
                        "USING (SELECT :idUnidad AS id_u, :idP AS id_p FROM DUAL) src " +
                        "ON (r.ID_UNIDAD = src.id_u AND r.ID_PERSONA = src.id_p) " +
                        "WHEN MATCHED THEN " +
                        "    UPDATE SET r.ESTADO = 'ACTIVO', r.TIPO_RESIDENTE = 'CONVIVIENTE', r.FECHA_INICIO = :fInicio " +
                        "WHEN NOT MATCHED THEN " +
                        "    INSERT (ID_UNIDAD, ID_PERSONA, TIPO_RESIDENTE, FECHA_INICIO, ESTADO) " +
                        "    VALUES (src.id_u, src.id_p, 'CONVIVIENTE', :fInicio, 'ACTIVO')",
                        new MapSqlParameterSource()
                            .addValue("idUnidad", req.idApartamento())
                            .addValue("idP", co.idPersona())
                            .addValue("fInicio", req.fechaInicio())
                    );
                }
            }
        }

        // Sincronizar habitante físico principal en RESIDENTES_UNIDAD de forma inmediata y consistente
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
        return registrarPago(req, idUnidad, null, null, null);
    }

    @Override
    public Long registrarPago(PagoRequestDTO req, Long idUnidad, String estadoInicial, String hash, Long tamanoBytes) {
        String estado;
        if (estadoInicial != null && !estadoInicial.isBlank()) {
            estado = estadoInicial;
        } else if ("PASARELA_WOMPI".equalsIgnoreCase(req.metodoPago())) {
            estado = "APROBADO";
        } else {
            estado = "PENDIENTE_APROBACION";
        }

        String sql = "INSERT INTO PAGOS (ID_UNIDAD, MONTO_TOTAL, METODO_PAGO, REFERENCIA_COMPROBANTE, ESTADO, " +
                     "COMPROBANTE_URL, COMPROBANTE_HASH, COMPROBANTE_TAMANO_BYTES, OBSERVACIONES, IDEMPOTENCY_KEY, FECHA_PAGO) " +
                     "VALUES (:unidad, :monto, :metodo, :ref, :estado, :compUrl, :hash, :tamano, :obs, :idempotency, :fechaPago)";
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("unidad", idUnidad)
            .addValue("monto", req.valorPagado())
            .addValue("metodo", req.metodoPago())
            .addValue("ref", req.referencia())
            .addValue("estado", estado)
            .addValue("compUrl", req.comprobanteUrl())
            .addValue("hash", hash)
            .addValue("tamano", tamanoBytes)
            .addValue("obs", req.notas())
            .addValue("idempotency", req.idempotencyKey())
            .addValue("fechaPago", req.fechaPago() != null ? java.sql.Date.valueOf(req.fechaPago()) : new java.sql.Date(System.currentTimeMillis()));

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, params, keyHolder, new String[]{"ID_PAGO"});
        Long idPago = keyHolder.getKey().longValue();

        if (req.idCuota() != null) {
            String sqlDetalle = "INSERT INTO PAGO_DETALLE (ID_PAGO, ID_CUOTA, MONTO_APLICADO) VALUES (:pago, :cuota, :monto)";
            jdbcTemplate.update(sqlDetalle, new MapSqlParameterSource()
                .addValue("pago", idPago).addValue("cuota", req.idCuota()).addValue("monto", req.valorPagado()));
        }

        return idPago;
    }

    @Override
    public void actualizarSaldoCuota(Long idCuota, BigDecimal montoAplicado) {
        Long propId = (SaedContextHolder.getContext() != null) ? SaedContextHolder.getContext().getPropertyId() : null;
        String sql = "UPDATE CUOTAS SET SALDO_PENDIENTE = GREATEST(SALDO_PENDIENTE - :monto, 0), " +
                     "ESTADO = CASE WHEN SALDO_PENDIENTE - :monto <= 0 THEN 'PAGADA' ELSE 'PAGADA_PARCIAL' END " +
                     "WHERE ID_CUOTA = :idCuota" +
                     (propId != null ? " AND ID_UNIDAD IN (SELECT ID_UNIDAD FROM UNIDADES WHERE ID_PROPIEDAD = :propId)" : "");
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("monto", montoAplicado)
                .addValue("idCuota", idCuota);
        if (propId != null) params.addValue("propId", propId);
        jdbcTemplate.update(sql, params);
    }

    @Override
    public List<PagoResponseDTO> getPagos(String estado, Long idUnidad, LocalDate fechaDesde, LocalDate fechaHasta, String metodoPago) {
        com.saed.backend.context.SaedContext ctx = SaedContextHolder.getContext();
        Long propId = (ctx != null) ? ctx.getPropertyId() : null;
        Long orgId = (ctx != null) ? ctx.getOrganizationId() : null;

        MapSqlParameterSource params = new MapSqlParameterSource();
        StringBuilder sql = new StringBuilder(
            "SELECT p.ID_PAGO, p.ID_UNIDAD, u.IDENTIFICADOR as numeroApartamento, " +
            "NVL(per.PRIMER_NOMBRE || ' ' || per.PRIMER_APELLIDO, NVL(res_per.PRIMER_NOMBRE || ' ' || res_per.PRIMER_APELLIDO, 'Residente')) as nombreResidente, " +
            "pd.ID_CUOTA, co.NOMBRE as conceptoCuota, c.PERIODO as periodoCuota, " +
            "p.MONTO_TOTAL, p.METODO_PAGO, p.REFERENCIA_COMPROBANTE, p.COMPROBANTE_URL, " +
            "p.COMPROBANTE_HASH, p.COMPROBANTE_TAMANO_BYTES, p.FECHA_PAGO, p.ESTADO, " +
            "p.APROBADO_POR, u_aprob.NOMBRE_USUARIO as nombreAprobador, p.FECHA_APROBACION, " +
            "p.RECHAZADO_POR, u_rech.NOMBRE_USUARIO as nombreRechazador, p.FECHA_RECHAZO, " +
            "p.OBSERVACIONES " +
            "FROM PAGOS p " +
            "JOIN UNIDADES u ON p.ID_UNIDAD = u.ID_UNIDAD " +
            "JOIN PROPIEDADES prop ON u.ID_PROPIEDAD = prop.ID_PROPIEDAD " +
            "LEFT JOIN PAGO_DETALLE pd ON p.ID_PAGO = pd.ID_PAGO " +
            "LEFT JOIN CUOTAS c ON pd.ID_CUOTA = c.ID_CUOTA " +
            "LEFT JOIN CONCEPTOS_COBRO co ON c.ID_CONCEPTO = co.ID_CONCEPTO " +
            "LEFT JOIN PERSONAS per ON p.ID_PERSONA_PAGA = per.ID_PERSONA " +
            "LEFT JOIN ( " +
            "    SELECT ru.ID_UNIDAD, p2.PRIMER_NOMBRE, p2.PRIMER_APELLIDO, " +
            "           ROW_NUMBER() OVER (PARTITION BY ru.ID_UNIDAD ORDER BY ru.ID_RESIDENTE_UNIDAD ASC) rn " +
            "    FROM RESIDENTES_UNIDAD ru " +
            "    JOIN PERSONAS p2 ON ru.ID_PERSONA = p2.ID_PERSONA " +
            "    WHERE ru.ESTADO = 'ACTIVO' " +
            ") res_per ON u.ID_UNIDAD = res_per.ID_UNIDAD AND res_per.rn = 1 " +
            "LEFT JOIN USUARIOS u_aprob ON p.APROBADO_POR = u_aprob.ID_USUARIO " +
            "LEFT JOIN USUARIOS u_rech ON p.RECHAZADO_POR = u_rech.ID_USUARIO " +
            "WHERE 1=1 "
        );

        if (propId != null) {
            sql.append("AND u.ID_PROPIEDAD = :propId ");
            params.addValue("propId", propId);
        } else if (orgId != null) {
            sql.append("AND prop.ID_ORGANIZACION = :orgId ");
            params.addValue("orgId", orgId);
        }

        if (estado != null && !estado.isBlank()) {
            sql.append("AND p.ESTADO = :estado ");
            params.addValue("estado", estado.trim().toUpperCase());
        }
        if (idUnidad != null) {
            sql.append("AND p.ID_UNIDAD = :idUnidad ");
            params.addValue("idUnidad", idUnidad);
        }
        if (fechaDesde != null) {
            sql.append("AND TRUNC(p.FECHA_PAGO) >= :fechaDesde ");
            params.addValue("fechaDesde", java.sql.Date.valueOf(fechaDesde));
        }
        if (fechaHasta != null) {
            sql.append("AND TRUNC(p.FECHA_PAGO) <= :fechaHasta ");
            params.addValue("fechaHasta", java.sql.Date.valueOf(fechaHasta));
        }
        if (metodoPago != null && !metodoPago.isBlank()) {
            sql.append("AND p.METODO_PAGO = :metodoPago ");
            params.addValue("metodoPago", metodoPago.trim());
        }

        sql.append("ORDER BY p.FECHA_PAGO DESC, p.ID_PAGO DESC");
        return jdbcTemplate.query(sql.toString(), params, this::mapRowToPagoResponse);
    }

    @Override
    public PagoResponseDTO getPagoById(Long idPago) {
        com.saed.backend.context.SaedContext ctx = SaedContextHolder.getContext();
        Long propId = (ctx != null) ? ctx.getPropertyId() : null;
        Long orgId = (ctx != null) ? ctx.getOrganizationId() : null;

        MapSqlParameterSource params = new MapSqlParameterSource("idPago", idPago);
        StringBuilder sql = new StringBuilder(
            "SELECT p.ID_PAGO, p.ID_UNIDAD, u.IDENTIFICADOR as numeroApartamento, " +
            "NVL(per.PRIMER_NOMBRE || ' ' || per.PRIMER_APELLIDO, NVL(res_per.PRIMER_NOMBRE || ' ' || res_per.PRIMER_APELLIDO, 'Residente')) as nombreResidente, " +
            "pd.ID_CUOTA, co.NOMBRE as conceptoCuota, c.PERIODO as periodoCuota, " +
            "p.MONTO_TOTAL, p.METODO_PAGO, p.REFERENCIA_COMPROBANTE, p.COMPROBANTE_URL, " +
            "p.COMPROBANTE_HASH, p.COMPROBANTE_TAMANO_BYTES, p.FECHA_PAGO, p.ESTADO, " +
            "p.APROBADO_POR, u_aprob.NOMBRE_USUARIO as nombreAprobador, p.FECHA_APROBACION, " +
            "p.RECHAZADO_POR, u_rech.NOMBRE_USUARIO as nombreRechazador, p.FECHA_RECHAZO, " +
            "p.OBSERVACIONES " +
            "FROM PAGOS p " +
            "JOIN UNIDADES u ON p.ID_UNIDAD = u.ID_UNIDAD " +
            "JOIN PROPIEDADES prop ON u.ID_PROPIEDAD = prop.ID_PROPIEDAD " +
            "LEFT JOIN PAGO_DETALLE pd ON p.ID_PAGO = pd.ID_PAGO " +
            "LEFT JOIN CUOTAS c ON pd.ID_CUOTA = c.ID_CUOTA " +
            "LEFT JOIN CONCEPTOS_COBRO co ON c.ID_CONCEPTO = co.ID_CONCEPTO " +
            "LEFT JOIN PERSONAS per ON p.ID_PERSONA_PAGA = per.ID_PERSONA " +
            "LEFT JOIN ( " +
            "    SELECT ru.ID_UNIDAD, p2.PRIMER_NOMBRE, p2.PRIMER_APELLIDO, " +
            "           ROW_NUMBER() OVER (PARTITION BY ru.ID_UNIDAD ORDER BY ru.ID_RESIDENTE_UNIDAD ASC) rn " +
            "    FROM RESIDENTES_UNIDAD ru " +
            "    JOIN PERSONAS p2 ON ru.ID_PERSONA = p2.ID_PERSONA " +
            "    WHERE ru.ESTADO = 'ACTIVO' " +
            ") res_per ON u.ID_UNIDAD = res_per.ID_UNIDAD AND res_per.rn = 1 " +
            "LEFT JOIN USUARIOS u_aprob ON p.APROBADO_POR = u_aprob.ID_USUARIO " +
            "LEFT JOIN USUARIOS u_rech ON p.RECHAZADO_POR = u_rech.ID_USUARIO " +
            "WHERE p.ID_PAGO = :idPago "
        );

        if (propId != null) {
            sql.append("AND u.ID_PROPIEDAD = :propId ");
            params.addValue("propId", propId);
        } else if (orgId != null) {
            sql.append("AND prop.ID_ORGANIZACION = :orgId ");
            params.addValue("orgId", orgId);
        }

        List<PagoResponseDTO> results = jdbcTemplate.query(sql.toString(), params, this::mapRowToPagoResponse);
        return results.isEmpty() ? null : results.get(0);
    }

    @Override
    public void aprobarPago(Long idPago, Long idAprobador) {
        String sql = "UPDATE PAGOS SET ESTADO = 'APROBADO', APROBADO_POR = :aprobador, " +
                     "FECHA_APROBACION = SYSTIMESTAMP WHERE ID_PAGO = :idPago";
        jdbcTemplate.update(sql, new MapSqlParameterSource()
            .addValue("idPago", idPago)
            .addValue("aprobador", idAprobador));
    }

    @Override
    public void rechazarPago(Long idPago, Long idRechazador, String motivoRechazo) {
        String sql = "UPDATE PAGOS SET ESTADO = 'RECHAZADO', RECHAZADO_POR = :rechazador, " +
                     "FECHA_RECHAZO = SYSTIMESTAMP, OBSERVACIONES = :motivo WHERE ID_PAGO = :idPago";
        jdbcTemplate.update(sql, new MapSqlParameterSource()
            .addValue("idPago", idPago)
            .addValue("rechazador", idRechazador)
            .addValue("motivo", motivoRechazo));
    }

    @Override
    public void recalcularCarteraUnidad(Long idUnidad) {
        String sqlRecalc = "MERGE INTO CARTERA dest " +
                "USING ( " +
                "  SELECT u.ID_UNIDAD, " +
                "    NVL(SUM(CASE WHEN c.FECHA_VENCIMIENTO >= TRUNC(SYSDATE) THEN c.SALDO_PENDIENTE ELSE 0 END), 0) AS SALDO_CORRIENTE, " +
                "    NVL(SUM(CASE WHEN c.FECHA_VENCIMIENTO < TRUNC(SYSDATE) AND TRUNC(SYSDATE) - c.FECHA_VENCIMIENTO <= 30 THEN c.SALDO_PENDIENTE ELSE 0 END), 0) AS SALDO_MORA_30, " +
                "    NVL(SUM(CASE WHEN TRUNC(SYSDATE) - c.FECHA_VENCIMIENTO BETWEEN 31 AND 60 THEN c.SALDO_PENDIENTE ELSE 0 END), 0) AS SALDO_MORA_60, " +
                "    NVL(SUM(CASE WHEN TRUNC(SYSDATE) - c.FECHA_VENCIMIENTO > 60 THEN c.SALDO_PENDIENTE ELSE 0 END), 0) AS SALDO_MORA_90_MAS " +
                "  FROM UNIDADES u " +
                "  LEFT JOIN CUOTAS c ON u.ID_UNIDAD = c.ID_UNIDAD AND c.ESTADO IN ('PENDIENTE', 'VENCIDA', 'PAGADA_PARCIAL') " +
                "  WHERE u.ID_UNIDAD = :idUnidad " +
                "  GROUP BY u.ID_UNIDAD " +
                ") src " +
                "ON (dest.ID_UNIDAD = src.ID_UNIDAD) " +
                "WHEN MATCHED THEN UPDATE SET " +
                "  dest.SALDO_CORRIENTE = src.SALDO_CORRIENTE, " +
                "  dest.SALDO_MORA_30 = src.SALDO_MORA_30, " +
                "  dest.SALDO_MORA_60 = src.SALDO_MORA_60, " +
                "  dest.SALDO_MORA_90_MAS = src.SALDO_MORA_90_MAS, " +
                "  dest.FECHA_CORTE = TRUNC(SYSDATE), " +
                "  dest.ESTADO_CARTERA = CASE " +
                "    WHEN src.SALDO_MORA_90_MAS > 0 THEN 'MORA_CRITICA' " +
                "    WHEN src.SALDO_MORA_60 > 0 THEN 'MORA_60' " +
                "    WHEN src.SALDO_MORA_30 > 0 THEN 'MORA_30' " +
                "    ELSE 'AL_DIA' " +
                "  END " +
                "WHEN NOT MATCHED THEN INSERT (ID_UNIDAD, SALDO_CORRIENTE, SALDO_MORA_30, SALDO_MORA_60, SALDO_MORA_90_MAS, FECHA_CORTE, ESTADO_CARTERA) " +
                "  VALUES (src.ID_UNIDAD, src.SALDO_CORRIENTE, src.SALDO_MORA_30, src.SALDO_MORA_60, src.SALDO_MORA_90_MAS, TRUNC(SYSDATE), " +
                "    CASE " +
                "      WHEN src.SALDO_MORA_90_MAS > 0 THEN 'MORA_CRITICA' " +
                "      WHEN src.SALDO_MORA_60 > 0 THEN 'MORA_60' " +
                "      WHEN src.SALDO_MORA_30 > 0 THEN 'MORA_30' " +
                "      ELSE 'AL_DIA' " +
                "    END)";
        jdbcTemplate.update(sqlRecalc, new MapSqlParameterSource("idUnidad", idUnidad));
    }

    private PagoResponseDTO mapRowToPagoResponse(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        Timestamp tsPago = rs.getTimestamp("FECHA_PAGO");
        LocalDate fechaPago = tsPago != null ? tsPago.toLocalDateTime().toLocalDate() : null;
        Timestamp tsAprob = rs.getTimestamp("FECHA_APROBACION");
        OffsetDateTime fechaAprobacion = tsAprob != null ? tsAprob.toInstant().atZone(ZoneId.of("America/Bogota")).toOffsetDateTime() : null;
        Timestamp tsRech = rs.getTimestamp("FECHA_RECHAZO");
        OffsetDateTime fechaRechazo = tsRech != null ? tsRech.toInstant().atZone(ZoneId.of("America/Bogota")).toOffsetDateTime() : null;
        Long aprobadoPor = rs.getObject("APROBADO_POR") != null ? rs.getLong("APROBADO_POR") : null;
        Long rechazadoPor = rs.getObject("RECHAZADO_POR") != null ? rs.getLong("RECHAZADO_POR") : null;
        Long tamanoBytes = rs.getObject("COMPROBANTE_TAMANO_BYTES") != null ? rs.getLong("COMPROBANTE_TAMANO_BYTES") : null;
        Long idCuota = rs.getObject("ID_CUOTA") != null ? rs.getLong("ID_CUOTA") : null;

        return new PagoResponseDTO(
            rs.getLong("ID_PAGO"),
            rs.getLong("ID_UNIDAD"),
            rs.getString("numeroApartamento"),
            rs.getString("nombreResidente"),
            idCuota,
            rs.getString("conceptoCuota"),
            rs.getString("periodoCuota"),
            rs.getBigDecimal("MONTO_TOTAL"),
            rs.getString("METODO_PAGO"),
            rs.getString("REFERENCIA_COMPROBANTE"),
            rs.getString("COMPROBANTE_URL"),
            rs.getString("COMPROBANTE_HASH"),
            tamanoBytes,
            fechaPago,
            rs.getString("ESTADO"),
            aprobadoPor,
            rs.getString("nombreAprobador"),
            fechaAprobacion,
            rechazadoPor,
            rs.getString("nombreRechazador"),
            fechaRechazo,
            rs.getString("OBSERVACIONES")
        );
    }

    @Override
    public ContratoDetalleDTO getContratoDetalle(Long idContrato) {
        String sql = "SELECT c.ID_CONTRATO, c.NUMERO_CONTRATO, c.FECHA_INICIO, c.FECHA_FIN, c.TIPO_CONTRATO, c.ESTADO, " +
                     "c.ID_UNIDAD, c.ID_ARRENDATARIO_PRINCIPAL, c.ID_PLANTILLA, c.ID_TUTOR, c.DOCUMENTO_URL, c.DOCUMENTO_HASH, " +
                     "c.DOCUMENTO_TAMANO_BYTES, c.DOCUMENTO_FECHA_GENERACION, c.HTML_CONGELADO, " +
                     "u.IDENTIFICADOR as numeroApartamento, u.ID_PROPIEDAD, pr.ID_ORGANIZACION, " +
                     "pr.NOMBRE as nombreEdificio, pr.DIRECCION as direccionEdificio, pr.CIUDAD as ciudadEdificio, " +
                     "o.NOMBRE as nombreOrganizacion, o.IDENTIFICACION_FISCAL as nitAdministrador, " +
                     "p.PRIMER_NOMBRE as nombresResidente, p.PRIMER_APELLIDO as apellidosResidente, " +
                     "p.NUMERO_DOCUMENTO as numeroDocumentoResidente, p.TELEFONO as telefonoResidente, p.EMAIL as correoResidente, " +
                     "tut.PRIMER_NOMBRE as nombresTutor, tut.PRIMER_APELLIDO as apellidosTutor, " +
                     "tut.NUMERO_DOCUMENTO as cedulaTutor, tut.TELEFONO as telefonoTutor, tut.EMAIL as correoTutor, " +
                     "t.PARENTESCO as parentescoTutor, " +
                     "c.CANON_MENSUAL, c.DIA_CORTE_PAGO " +
                     "FROM CONTRATOS c " +
                     "JOIN UNIDADES u ON c.ID_UNIDAD = u.ID_UNIDAD " +
                     "JOIN PROPIEDADES pr ON u.ID_PROPIEDAD = pr.ID_PROPIEDAD " +
                     "JOIN ORGANIZACIONES o ON pr.ID_ORGANIZACION = o.ID_ORGANIZACION " +
                     "JOIN PERSONAS p ON c.ID_ARRENDATARIO_PRINCIPAL = p.ID_PERSONA " +
                     "LEFT JOIN PERSONAS tut ON c.ID_TUTOR = tut.ID_PERSONA " +
                     "LEFT JOIN TUTORES t ON (t.ID_PERSONA_MENOR = c.ID_ARRENDATARIO_PRINCIPAL AND t.ID_PERSONA_TUTOR = c.ID_TUTOR) " +
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
            dto.setIdUnidad(rs.getLong("ID_UNIDAD"));
            dto.setIdArrendatarioPrincipal(rs.getLong("ID_ARRENDATARIO_PRINCIPAL"));

            long tpl = rs.getLong("ID_PLANTILLA");
            if (!rs.wasNull()) dto.setIdPlantilla(tpl);

            dto.setIdPropiedad(rs.getLong("ID_PROPIEDAD"));
            dto.setIdOrganizacion(rs.getLong("ID_ORGANIZACION"));
            dto.setNombreOrganizacion(rs.getString("nombreOrganizacion"));
            dto.setNitAdministrador(rs.getString("nitAdministrador"));
            dto.setNombreEdificio(rs.getString("nombreEdificio"));
            dto.setDireccionEdificio(rs.getString("direccionEdificio"));
            dto.setCiudadEdificio(rs.getString("ciudadEdificio"));

            dto.setDocumentoUrl(rs.getString("DOCUMENTO_URL"));
            dto.setDocumentoHash(rs.getString("DOCUMENTO_HASH"));
            long sz = rs.getLong("DOCUMENTO_TAMANO_BYTES");
            if (!rs.wasNull()) dto.setDocumentoTamanoBytes(sz);

            java.sql.Timestamp ts = rs.getTimestamp("DOCUMENTO_FECHA_GENERACION");
            if (ts != null) {
                dto.setDocumentoFechaGeneracion(ts.toInstant().atOffset(java.time.ZoneOffset.UTC));
            }
            dto.setHtmlCongelado(rs.getString("HTML_CONGELADO"));

            dto.setNumeroApartamento(rs.getString("numeroApartamento"));
            dto.setNombreCompletoResidente(rs.getString("nombresResidente") + " " + rs.getString("apellidosResidente"));
            dto.setNombresResidente(rs.getString("nombresResidente"));
            dto.setApellidosResidente(rs.getString("apellidosResidente"));
            dto.setNumeroDocumentoResidente(rs.getString("numeroDocumentoResidente"));
            dto.setTelefonoResidente(rs.getString("telefonoResidente"));
            dto.setCorreoResidente(rs.getString("correoResidente"));

            // Tutor fields
            long idTut = rs.getLong("ID_TUTOR");
            if (!rs.wasNull()) {
                dto.setIdTutor(idTut);
                String nomTut = rs.getString("nombresTutor");
                String apeTut = rs.getString("apellidosTutor");
                if (nomTut != null || apeTut != null) {
                    dto.setNombreTutor(((nomTut != null ? nomTut : "") + " " + (apeTut != null ? apeTut : "")).trim());
                }
                dto.setCedulaTutor(rs.getString("cedulaTutor"));
                dto.setTelefonoTutor(rs.getString("telefonoTutor"));
                dto.setEmailTutor(rs.getString("correoTutor"));
                String parentesco = rs.getString("parentescoTutor");
                dto.setParentescoTutor(parentesco != null ? parentesco : "TUTOR LEGAL");
                dto.setRelacionTutor(parentesco != null ? parentesco : "TUTOR LEGAL");
            }

            dto.setValorCanon(rs.getBigDecimal("CANON_MENSUAL"));
            dto.setDiaPago(rs.getInt("DIA_CORTE_PAGO"));
            return dto;
        });

        if (list.isEmpty()) {
            return null;
        }

        ContratoDetalleDTO dto = list.get(0);

        // Fetch coarrendatarios
        String sqlCoarr = "SELECT cr.ID_CONTRATO_RESIDENTE, cr.ID_CONTRATO, cr.ID_PERSONA, cr.TIPO_VINCULO, " +
                          "cr.ES_RESPONSABLE_PAGO, TO_CHAR(cr.FECHA_VINCULACION, 'YYYY-MM-DD') AS FECHA_VINC, cr.ESTADO, " +
                          "p.PRIMER_NOMBRE || ' ' || p.PRIMER_APELLIDO AS NOMBRE_PERSONA, " +
                          "p.NUMERO_DOCUMENTO, p.TELEFONO, p.EMAIL " +
                          "FROM CONTRATO_RESIDENTE cr " +
                          "JOIN PERSONAS p ON cr.ID_PERSONA = p.ID_PERSONA " +
                          "WHERE cr.ID_CONTRATO = :id AND cr.TIPO_VINCULO != 'ARRENDATARIO_PRINCIPAL' " +
                          "ORDER BY cr.ID_CONTRATO_RESIDENTE ASC";

        List<CoarrendatarioDTO> coarrendatarios = jdbcTemplate.query(sqlCoarr, new MapSqlParameterSource("id", idContrato), (crs, cRow) -> new CoarrendatarioDTO(
            crs.getLong("ID_CONTRATO_RESIDENTE"),
            crs.getLong("ID_CONTRATO"),
            crs.getLong("ID_PERSONA"),
            crs.getString("TIPO_VINCULO"),
            crs.getString("ES_RESPONSABLE_PAGO"),
            crs.getString("FECHA_VINC"),
            crs.getString("ESTADO"),
            crs.getString("NOMBRE_PERSONA"),
            crs.getString("NUMERO_DOCUMENTO"),
            crs.getString("TELEFONO"),
            crs.getString("EMAIL")
        ));
        dto.setCoarrendatarios(coarrendatarios);

        return dto;
    }

    @Override
    public void actualizarDocumentoContrato(Long idContrato, String documentoUrl, String documentoHash, long tamanoBytes, String htmlCongelado) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", idContrato)
                .addValue("url", documentoUrl)
                .addValue("hash", documentoHash)
                .addValue("tamano", tamanoBytes)
                .addValue("html", htmlCongelado);

        jdbcTemplate.update(
                "UPDATE CONTRATOS SET " +
                "DOCUMENTO_URL = :url, " +
                "DOCUMENTO_HASH = :hash, " +
                "DOCUMENTO_TAMANO_BYTES = :tamano, " +
                "DOCUMENTO_FECHA_GENERACION = CURRENT_TIMESTAMP, " +
                "HTML_CONGELADO = :html " +
                "WHERE ID_CONTRATO = :id",
                params
        );
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
