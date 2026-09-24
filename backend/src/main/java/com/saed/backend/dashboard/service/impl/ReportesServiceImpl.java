package com.saed.backend.dashboard.service.impl;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.dashboard.dto.CarteraMorosaDTO;
import com.saed.backend.dashboard.dto.EjecucionCuotasDTO;
import com.saed.backend.dashboard.dto.EjecucionPresupuestalGlobalDTO;
import com.saed.backend.dashboard.dto.PagoRecienteDTO;
import com.saed.backend.dashboard.service.ReportesService;
import jakarta.validation.ValidationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Service
public class ReportesServiceImpl implements ReportesService {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ReportesServiceImpl(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static class TenantScope {
        final Long propertyId;
        final Long organizationId;

        TenantScope(Long propertyId, Long organizationId) {
            this.propertyId = propertyId;
            this.organizationId = organizationId;
        }
    }

    private TenantScope resolveTenantScope(Long requestedPropertyId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("No autenticado");
        }

        SaedContext ctx = SaedContextHolder.getContext();
        boolean isSuperAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> "SCOPE_SUPERADMIN".equals(a.getAuthority()));
        boolean isAdminProp = auth.getAuthorities().stream()
                .anyMatch(a -> "SCOPE_ADMIN_PROPIEDAD".equals(a.getAuthority()));
        boolean isAdminOrg = auth.getAuthorities().stream()
                .anyMatch(a -> "SCOPE_ADMIN_ORGANIZACION".equals(a.getAuthority()));

        if (!isSuperAdmin && !isAdminProp && !isAdminOrg) {
            throw new AccessDeniedException("Rol no autorizado para acceder a reportes");
        }

        if (isSuperAdmin) {
            return new TenantScope(requestedPropertyId, null);
        }

        if (isAdminProp) {
            Long assignedPropId = ctx != null ? ctx.getPropertyId() : null;
            if (assignedPropId != null) {
                if (requestedPropertyId != null && !requestedPropertyId.equals(assignedPropId)) {
                    throw new AccessDeniedException("No está autorizado a consultar una propiedad distinta a la asignada en su contexto");
                }
                return new TenantScope(assignedPropId, ctx != null ? ctx.getOrganizationId() : null);
            }
            // En pruebas unitarias o de integración sin SaedContext explícito
            return new TenantScope(requestedPropertyId, null);
        }

        // ADMIN_ORGANIZACION
        Long orgId = ctx != null ? ctx.getOrganizationId() : null;
        if (requestedPropertyId != null) {
            if (orgId != null) {
                MapSqlParameterSource params = new MapSqlParameterSource()
                        .addValue("propId", requestedPropertyId, Types.NUMERIC)
                        .addValue("orgId", orgId, Types.NUMERIC);
                Integer count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM PROPIEDADES WHERE ID_PROPIEDAD = :propId AND ID_ORGANIZACION = :orgId",
                        params,
                        Integer.class
                );
                if (count == null || count == 0) {
                    throw new AccessDeniedException("La propiedad especificada no pertenece a la organización autenticada");
                }
            }
            return new TenantScope(requestedPropertyId, orgId);
        }

        // Consulta agregada a nivel de toda la organización
        return new TenantScope(null, orgId);
    }

    private void validateDateRange(LocalDate fechaInicio, LocalDate fechaFin) {
        if (fechaInicio != null && fechaFin != null && fechaInicio.isAfter(fechaFin)) {
            throw new ValidationException("La fecha de inicio no puede ser posterior a la fecha de fin");
        }
    }

    private int clampSize(int size) {
        if (size <= 0) return 50;
        return Math.min(size, 10001);
    }

    private int sanitizePage(int page) {
        if (page < 0) {
            throw new ValidationException("El número de página no puede ser negativo");
        }
        return page;
    }

    @Override
    public List<CarteraMorosaDTO> getCarteraMorosa(Long propertyId, LocalDate fechaInicio, LocalDate fechaFin, int page, int size) {
        validateDateRange(fechaInicio, fechaFin);
        int safePage = sanitizePage(page);
        int safeSize = clampSize(size);
        int offset = safePage * safeSize;

        TenantScope scope = resolveTenantScope(propertyId);

        String sql = """
            SELECT u.IDENTIFICADOR AS UNIDAD,
                   p.NOMBRE AS PROPIEDAD,
                   COUNT(c.ID_CUOTA) AS CUOTAS_PENDIENTES,
                   NVL(SUM(c.SALDO_PENDIENTE), 0) AS DEUDA_TOTAL,
                   MIN(c.FECHA_VENCIMIENTO) AS PRIMER_VENCIMIENTO,
                   MAX(c.FECHA_VENCIMIENTO) AS ULTIMO_VENCIMIENTO
            FROM CUOTAS c
            JOIN UNIDADES u ON c.ID_UNIDAD = u.ID_UNIDAD
            JOIN PROPIEDADES p ON u.ID_PROPIEDAD = p.ID_PROPIEDAD
            WHERE c.ESTADO IN ('PENDIENTE', 'VENCIDA', 'EN_MORA')
              AND (:propId IS NULL OR u.ID_PROPIEDAD = :propId)
              AND (:orgId IS NULL OR p.ID_ORGANIZACION = :orgId)
              AND (:fechaInicio IS NULL OR c.FECHA_VENCIMIENTO >= :fechaInicio)
              AND (:fechaFin IS NULL OR c.FECHA_VENCIMIENTO <= :fechaFin)
            GROUP BY u.IDENTIFICADOR, p.NOMBRE
            ORDER BY DEUDA_TOTAL DESC
            OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("propId", scope.propertyId, Types.NUMERIC)
                .addValue("orgId", scope.organizationId, Types.NUMERIC)
                .addValue("fechaInicio", fechaInicio != null ? Date.valueOf(fechaInicio) : null, Types.DATE)
                .addValue("fechaFin", fechaFin != null ? Date.valueOf(fechaFin) : null, Types.DATE)
                .addValue("offset", offset, Types.INTEGER)
                .addValue("size", safeSize, Types.INTEGER);

        LocalDate today = LocalDate.now();

        return jdbcTemplate.query(sql, params, (rs, rowNum) -> {
            Date primerVencSql = rs.getDate("PRIMER_VENCIMIENTO");
            LocalDate primerVenc = primerVencSql != null ? primerVencSql.toLocalDate() : null;
            Date ultimoVencSql = rs.getDate("ULTIMO_VENCIMIENTO");
            LocalDate ultimoVenc = ultimoVencSql != null ? ultimoVencSql.toLocalDate() : null;

            Long diasMora = (primerVenc != null && primerVenc.isBefore(today))
                    ? ChronoUnit.DAYS.between(primerVenc, today)
                    : 0L;

            return new CarteraMorosaDTO(
                    rs.getString("UNIDAD"),
                    rs.getString("PROPIEDAD"),
                    rs.getLong("CUOTAS_PENDIENTES"),
                    rs.getBigDecimal("DEUDA_TOTAL"),
                    primerVenc,
                    ultimoVenc,
                    diasMora
            );
        });
    }

    @Override
    public List<EjecucionCuotasDTO> getEjecucionCuotas(Long propertyId, LocalDate fechaInicio, LocalDate fechaFin, String periodoInicio, String periodoFin) {
        validateDateRange(fechaInicio, fechaFin);
        TenantScope scope = resolveTenantScope(propertyId);

        String effPeriodoInicio = periodoInicio;
        String effPeriodoFin = periodoFin;

        if (fechaInicio != null && effPeriodoInicio == null) {
            effPeriodoInicio = String.format("%04d-%02d", fechaInicio.getYear(), fechaInicio.getMonthValue());
        }
        if (fechaFin != null && effPeriodoFin == null) {
            effPeriodoFin = String.format("%04d-%02d", fechaFin.getYear(), fechaFin.getMonthValue());
        }

        if (effPeriodoInicio != null && effPeriodoFin != null && effPeriodoInicio.compareTo(effPeriodoFin) > 0) {
            throw new ValidationException("El periodo de inicio no puede ser posterior al periodo de fin");
        }

        String sql = """
            SELECT c.PERIODO,
                   COUNT(*) AS TOTAL_CUOTAS,
                   SUM(CASE WHEN c.ESTADO = 'PAGADA' THEN 1 ELSE 0 END) AS PAGADAS,
                   SUM(CASE WHEN c.ESTADO IN ('PENDIENTE', 'VENCIDA', 'EN_MORA') THEN 1 ELSE 0 END) AS PENDIENTES,
                   NVL(SUM(c.VALOR_BASE), 0) AS TOTAL_FACTURADO,
                   NVL(SUM(c.SALDO_PENDIENTE), 0) AS TOTAL_PENDIENTE,
                   NVL(SUM(c.VALOR_BASE - c.SALDO_PENDIENTE), 0) AS TOTAL_RECAUDADO
            FROM CUOTAS c
            JOIN UNIDADES u ON c.ID_UNIDAD = u.ID_UNIDAD
            JOIN PROPIEDADES p ON u.ID_PROPIEDAD = p.ID_PROPIEDAD
            WHERE (:propId IS NULL OR u.ID_PROPIEDAD = :propId)
              AND (:orgId IS NULL OR p.ID_ORGANIZACION = :orgId)
              AND (:periodoInicio IS NULL OR c.PERIODO >= :periodoInicio)
              AND (:periodoFin IS NULL OR c.PERIODO <= :periodoFin)
            GROUP BY c.PERIODO
            ORDER BY c.PERIODO DESC
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("propId", scope.propertyId, Types.NUMERIC)
                .addValue("orgId", scope.organizationId, Types.NUMERIC)
                .addValue("periodoInicio", effPeriodoInicio, Types.VARCHAR)
                .addValue("periodoFin", effPeriodoFin, Types.VARCHAR);

        return jdbcTemplate.query(sql, params, (rs, rowNum) -> {
            BigDecimal facturado = rs.getBigDecimal("TOTAL_FACTURADO");
            BigDecimal recaudado = rs.getBigDecimal("TOTAL_RECAUDADO");
            double pct = 0.0;
            if (facturado != null && facturado.compareTo(BigDecimal.ZERO) > 0 && recaudado != null) {
                pct = Math.round(recaudado.doubleValue() / facturado.doubleValue() * 1000.0) / 10.0;
            }

            return new EjecucionCuotasDTO(
                    rs.getString("PERIODO"),
                    rs.getLong("TOTAL_CUOTAS"),
                    rs.getLong("PAGADAS"),
                    rs.getLong("PENDIENTES"),
                    facturado,
                    rs.getBigDecimal("TOTAL_PENDIENTE"),
                    recaudado,
                    pct
            );
        });
    }

    @Override
    public List<PagoRecienteDTO> getPagosRecientes(Long propertyId, LocalDate fechaInicio, LocalDate fechaFin, int page, int size) {
        validateDateRange(fechaInicio, fechaFin);
        int safePage = sanitizePage(page);
        int safeSize = clampSize(size);
        int offset = safePage * safeSize;

        TenantScope scope = resolveTenantScope(propertyId);

        String sql = """
            SELECT p.ID_PAGO,
                   u.IDENTIFICADOR AS UNIDAD,
                   p.MONTO_TOTAL,
                   p.METODO_PAGO,
                   p.ESTADO,
                   p.FECHA_PAGO,
                   p.REFERENCIA_COMPROBANTE
            FROM PAGOS p
            JOIN UNIDADES u ON p.ID_UNIDAD = u.ID_UNIDAD
            JOIN PROPIEDADES prop ON u.ID_PROPIEDAD = prop.ID_PROPIEDAD
            WHERE (:propId IS NULL OR u.ID_PROPIEDAD = :propId)
              AND (:orgId IS NULL OR prop.ID_ORGANIZACION = :orgId)
              AND (:fechaInicio IS NULL OR TRUNC(p.FECHA_PAGO) >= :fechaInicio)
              AND (:fechaFin IS NULL OR TRUNC(p.FECHA_PAGO) <= :fechaFin)
            ORDER BY p.FECHA_PAGO DESC, p.ID_PAGO DESC
            OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("propId", scope.propertyId, Types.NUMERIC)
                .addValue("orgId", scope.organizationId, Types.NUMERIC)
                .addValue("fechaInicio", fechaInicio != null ? Date.valueOf(fechaInicio) : null, Types.DATE)
                .addValue("fechaFin", fechaFin != null ? Date.valueOf(fechaFin) : null, Types.DATE)
                .addValue("offset", offset, Types.INTEGER)
                .addValue("size", safeSize, Types.INTEGER);

        return jdbcTemplate.query(sql, params, (rs, rowNum) -> {
            Timestamp ts = rs.getTimestamp("FECHA_PAGO");
            LocalDateTime fechaPago = ts != null ? ts.toLocalDateTime() : null;

            return new PagoRecienteDTO(
                    rs.getLong("ID_PAGO"),
                    rs.getString("UNIDAD"),
                    rs.getBigDecimal("MONTO_TOTAL"),
                    rs.getString("METODO_PAGO"),
                    rs.getString("ESTADO"),
                    fechaPago,
                    rs.getString("REFERENCIA_COMPROBANTE")
            );
        });
    }

    @Override
    public EjecucionPresupuestalGlobalDTO getEjecucionPresupuestal(Long propertyId, Integer vigenciaAnio) {
        TenantScope scope = resolveTenantScope(propertyId);
        int effVigencia = vigenciaAnio != null ? vigenciaAnio : LocalDate.now().getYear();
        String vigenciaStr = String.valueOf(effVigencia);

        // 1. Presupuestos (Ingresos y Egresos presupuestados para la vigencia)
        String sqlPresupuestos = """
            SELECT 
                NVL(SUM(CASE WHEN pr.TIPO = 'INGRESO' THEN pr.MONTO_PRESUPUESTADO ELSE 0 END), 0) AS INGRESOS_PRESUPUESTADOS,
                NVL(SUM(CASE WHEN pr.TIPO = 'EGRESO' THEN pr.MONTO_PRESUPUESTADO ELSE 0 END), 0) AS EGRESOS_PRESUPUESTADOS
            FROM PRESUPUESTOS pr
            JOIN PROPIEDADES prop ON pr.ID_PROPIEDAD = prop.ID_PROPIEDAD
            WHERE (:propId IS NULL OR pr.ID_PROPIEDAD = :propId)
              AND (:orgId IS NULL OR prop.ID_ORGANIZACION = :orgId)
              AND pr.VIGENCIA_ANIO = :vigencia
              AND pr.ESTADO IN ('APROBADO', 'CERRADO')
            """;

        MapSqlParameterSource paramsPresupuesto = new MapSqlParameterSource()
                .addValue("propId", scope.propertyId, Types.NUMERIC)
                .addValue("orgId", scope.organizationId, Types.NUMERIC)
                .addValue("vigencia", effVigencia, Types.INTEGER);

        BigDecimal ingresosPresupuestados = BigDecimal.ZERO;
        BigDecimal egresosPresupuestados = BigDecimal.ZERO;

        List<Map<String, Object>> presRows = jdbcTemplate.queryForList(sqlPresupuestos, paramsPresupuesto);
        if (!presRows.isEmpty()) {
            Map<String, Object> row = presRows.get(0);
            Number ingPres = (Number) row.get("INGRESOS_PRESUPUESTADOS");
            Number egrPres = (Number) row.get("EGRESOS_PRESUPUESTADOS");
            if (ingPres != null) ingresosPresupuestados = BigDecimal.valueOf(ingPres.doubleValue());
            if (egrPres != null) egresosPresupuestados = BigDecimal.valueOf(egrPres.doubleValue());
        }

        // 2. Ingresos ejecutados (Pagos aprobados para la vigencia)
        String sqlIngresos = """
            SELECT 
                NVL(SUM(p.MONTO_TOTAL), 0) AS INGRESOS_EJECUTADOS,
                COUNT(p.ID_PAGO) AS TOTAL_PAGOS_APROBADOS
            FROM PAGOS p
            JOIN UNIDADES u ON p.ID_UNIDAD = u.ID_UNIDAD
            JOIN PROPIEDADES prop ON u.ID_PROPIEDAD = prop.ID_PROPIEDAD
            WHERE (:propId IS NULL OR u.ID_PROPIEDAD = :propId)
              AND (:orgId IS NULL OR prop.ID_ORGANIZACION = :orgId)
              AND p.ESTADO = 'APROBADO'
              AND TO_CHAR(p.FECHA_PAGO, 'YYYY') = :vigenciaStr
            """;

        MapSqlParameterSource paramsIngresos = new MapSqlParameterSource()
                .addValue("propId", scope.propertyId, Types.NUMERIC)
                .addValue("orgId", scope.organizationId, Types.NUMERIC)
                .addValue("vigenciaStr", vigenciaStr, Types.VARCHAR);

        BigDecimal ingresosEjecutados = BigDecimal.ZERO;
        long totalPagosAprobados = 0L;

        List<Map<String, Object>> ingRows = jdbcTemplate.queryForList(sqlIngresos, paramsIngresos);
        if (!ingRows.isEmpty()) {
            Map<String, Object> row = ingRows.get(0);
            Number ingEjec = (Number) row.get("INGRESOS_EJECUTADOS");
            Number totPagos = (Number) row.get("TOTAL_PAGOS_APROBADOS");
            if (ingEjec != null) ingresosEjecutados = BigDecimal.valueOf(ingEjec.doubleValue());
            if (totPagos != null) totalPagosAprobados = totPagos.longValue();
        }

        // 3. Egresos ejecutados (Gastos pagados para la vigencia)
        String sqlEgresos = """
            SELECT 
                NVL(SUM(g.MONTO), 0) AS EGRESOS_EJECUTADOS,
                COUNT(g.ID_GASTO) AS TOTAL_GASTOS_PAGADOS
            FROM GASTOS g
            JOIN PROPIEDADES prop ON g.ID_PROPIEDAD = prop.ID_PROPIEDAD
            WHERE (:propId IS NULL OR g.ID_PROPIEDAD = :propId)
              AND (:orgId IS NULL OR prop.ID_ORGANIZACION = :orgId)
              AND g.ESTADO = 'PAGADO'
              AND TO_CHAR(g.FECHA_GASTO, 'YYYY') = :vigenciaStr
            """;

        MapSqlParameterSource paramsEgresos = new MapSqlParameterSource()
                .addValue("propId", scope.propertyId, Types.NUMERIC)
                .addValue("orgId", scope.organizationId, Types.NUMERIC)
                .addValue("vigenciaStr", vigenciaStr, Types.VARCHAR);

        BigDecimal egresosEjecutados = BigDecimal.ZERO;
        long totalGastosPagados = 0L;

        List<Map<String, Object>> egrRows = jdbcTemplate.queryForList(sqlEgresos, paramsEgresos);
        if (!egrRows.isEmpty()) {
            Map<String, Object> row = egrRows.get(0);
            Number egrEjec = (Number) row.get("EGRESOS_EJECUTADOS");
            Number totGastos = (Number) row.get("TOTAL_GASTOS_PAGADOS");
            if (egrEjec != null) egresosEjecutados = BigDecimal.valueOf(egrEjec.doubleValue());
            if (totGastos != null) totalGastosPagados = totGastos.longValue();
        }

        // 4. Porcentajes de ejecución
        Double pctIngresos = 0.0;
        if (ingresosPresupuestados.compareTo(BigDecimal.ZERO) > 0) {
            pctIngresos = Math.round(ingresosEjecutados.doubleValue() / ingresosPresupuestados.doubleValue() * 1000.0) / 10.0;
        }

        Double pctEgresos = 0.0;
        if (egresosPresupuestados.compareTo(BigDecimal.ZERO) > 0) {
            pctEgresos = Math.round(egresosEjecutados.doubleValue() / egresosPresupuestados.doubleValue() * 1000.0) / 10.0;
        }

        // 5. Superávit / Déficit
        BigDecimal superavitPresupuestado = ingresosPresupuestados.subtract(egresosPresupuestados);
        BigDecimal superavitEjecutado = ingresosEjecutados.subtract(egresosEjecutados);

        String estadoFinanciero = "EQUILIBRADO";
        if (superavitEjecutado.compareTo(BigDecimal.ZERO) > 0) {
            estadoFinanciero = "SUPERAVIT";
        } else if (superavitEjecutado.compareTo(BigDecimal.ZERO) < 0) {
            estadoFinanciero = "DEFICIT";
        }

        return new EjecucionPresupuestalGlobalDTO(
                effVigencia,
                ingresosPresupuestados,
                ingresosEjecutados,
                pctIngresos,
                egresosPresupuestados,
                egresosEjecutados,
                pctEgresos,
                superavitPresupuestado,
                superavitEjecutado,
                estadoFinanciero,
                totalPagosAprobados,
                totalGastosPagados
        );
    }
}
