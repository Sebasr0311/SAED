package com.saed.backend.dashboard.service.impl;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.dashboard.dto.*;
import com.saed.backend.dashboard.service.AnalyticsService;
import com.saed.backend.platform.dto.PlatformAnalyticsDTO;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class AnalyticsServiceImpl implements AnalyticsService {

    private final NamedParameterJdbcTemplate jdbc;

    public AnalyticsServiceImpl(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private record TemporalWindow(
        String horizonte,
        int mesesEvaluados,
        List<String> periodos,
        LocalDate fechaInicio,
        LocalDate fechaFin
    ) {}

    private TemporalWindow resolveTemporalWindow(Integer meses, Integer anio) {
        if (meses != null && meses != 6 && meses != 12) {
            throw new IllegalArgumentException("El parámetro 'meses' debe ser 6 o 12");
        }
        if (anio != null && (anio < 2000 || anio > 2100)) {
            throw new IllegalArgumentException("El parámetro 'anio' debe estar entre 2000 y 2100");
        }

        if (anio != null) {
            String horizonte = anio + "-01 a " + anio + "-12";
            List<String> periodos = IntStream.rangeClosed(1, 12)
                .mapToObj(m -> String.format("%04d-%02d", anio, m))
                .toList();
            LocalDate fechaInicio = LocalDate.of(anio, 1, 1);
            LocalDate fechaFin = LocalDate.of(anio, 12, 31);
            return new TemporalWindow(horizonte, 12, periodos, fechaInicio, fechaFin);
        } else {
            int effMeses = (meses != null ? meses : 12);
            YearMonth now = YearMonth.now();
            YearMonth start = now.minusMonths(effMeses - 1);
            String horizonte = start.toString() + " a " + now.toString();
            List<String> periodos = new ArrayList<>();
            for (int i = effMeses - 1; i >= 0; i--) {
                periodos.add(now.minusMonths(i).toString());
            }
            LocalDate fechaInicio = start.atDay(1);
            LocalDate fechaFin = now.atEndOfMonth();
            return new TemporalWindow(horizonte, effMeses, periodos, fechaInicio, fechaFin);
        }
    }

    private static class PropertyBenchmarkBuilder {
        Long idPropiedad;
        String nombre;
        String ciudad;
        long totalUnidades;
        long unidadesOcupadas;
        Double ocupacionActualPct;
        BigDecimal facturadoPeriodo = BigDecimal.ZERO;
        BigDecimal recaudadoPeriodo = BigDecimal.ZERO;
        BigDecimal carteraPeriodo = BigDecimal.ZERO;
        BigDecimal carteraTotalActual = BigDecimal.ZERO;
        Double efectividadRecaudoPct = 100.0;
        Double indiceMorosidadPct = 0.0;
        Integer rankingEfectividad;
        Integer rankingMorosidad;
        Integer rankingOcupacion;

        public String getNombre() {
            return nombre != null ? nombre : "";
        }

        public Double getEfectividadRecaudoPct() {
            return efectividadRecaudoPct != null ? efectividadRecaudoPct : 0.0;
        }

        public BigDecimal getRecaudadoPeriodo() {
            return recaudadoPeriodo != null ? recaudadoPeriodo : BigDecimal.ZERO;
        }

        public Double getIndiceMorosidadPct() {
            return indiceMorosidadPct != null ? indiceMorosidadPct : 0.0;
        }

        public BigDecimal getCarteraPeriodo() {
            return carteraPeriodo != null ? carteraPeriodo : BigDecimal.ZERO;
        }

        public Double getOcupacionActualPct() {
            return ocupacionActualPct != null ? ocupacionActualPct : 0.0;
        }

        public long getTotalUnidades() {
            return totalUnidades;
        }

        public Long getIdPropiedad() {
            return idPropiedad != null ? idPropiedad : 0L;
        }

        public PropertyBenchmarkDTO build() {
            return new PropertyBenchmarkDTO(
                idPropiedad,
                nombre,
                ciudad,
                totalUnidades,
                unidadesOcupadas,
                ocupacionActualPct,
                facturadoPeriodo,
                recaudadoPeriodo,
                carteraPeriodo,
                carteraTotalActual,
                efectividadRecaudoPct,
                indiceMorosidadPct,
                rankingEfectividad,
                rankingMorosidad,
                rankingOcupacion
            );
        }
    }

    @Override
    public OrgAnalyticsDTO getOrgAnalytics(Long orgId, Integer meses, Integer anio) {
        SaedContext ctx = SaedContextHolder.getContext();
        Long contextOrgId = ctx != null ? ctx.getOrganizationId() : null;

        Long effectiveOrgId;
        if (contextOrgId != null) {
            effectiveOrgId = contextOrgId;
        } else if (orgId != null) {
            effectiveOrgId = orgId;
        } else {
            throw new AccessDeniedException("No se encontró contexto de organización activo");
        }

        TemporalWindow window = resolveTemporalWindow(meses, anio);

        // 1. Consultar propiedades de la organización
        MapSqlParameterSource orgParams = new MapSqlParameterSource("orgId", effectiveOrgId);
        String sqlProps = """
            SELECT p.ID_PROPIEDAD, p.NOMBRE, p.CIUDAD, p.ESTADO
            FROM PROPIEDADES p
            WHERE p.ID_ORGANIZACION = :orgId
            ORDER BY p.ID_PROPIEDAD ASC
        """;
        List<Map<String, Object>> propRows = jdbc.queryForList(sqlProps, orgParams);

        if (propRows.isEmpty()) {
            OrgAnalyticsKpisDTO emptyKpis = new OrgAnalyticsKpisDTO(
                0, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                100.0, 0.0, 0.0
            );
            List<MonthlyTrendDTO> emptyTrend = window.periodos.stream()
                .map(p -> new MonthlyTrendDTO(p, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO))
                .toList();
            return new OrgAnalyticsDTO(window.horizonte, window.mesesEvaluados, emptyKpis, List.of(), emptyTrend);
        }

        List<Long> propIds = propRows.stream()
            .map(r -> ((Number) r.get("ID_PROPIEDAD")).longValue())
            .toList();

        MapSqlParameterSource batchParams = new MapSqlParameterSource()
            .addValue("orgId", effectiveOrgId)
            .addValue("propIds", propIds)
            .addValue("periodos", window.periodos)
            .addValue("fechaInicio", window.fechaInicio)
            .addValue("fechaFin", window.fechaFin);

        // 2. Unidades y Ocupación
        String sqlOcupacion = """
            SELECT u.ID_PROPIEDAD,
                   COUNT(u.ID_UNIDAD) AS TOTAL_UNIDADES,
                   COUNT(DISTINCT CASE WHEN ru.ESTADO IN ('ACTIVO', 'ACTIVA') THEN u.ID_UNIDAD END) AS UNIDADES_OCUPADAS
            FROM UNIDADES u
            LEFT JOIN RESIDENTES_UNIDAD ru ON u.ID_UNIDAD = ru.ID_UNIDAD
            WHERE u.ID_PROPIEDAD IN (:propIds) AND u.ESTADO != 'ELIMINADA'
            GROUP BY u.ID_PROPIEDAD
        """;
        Map<Long, Map<String, Object>> ocupacionMap = jdbc.queryForList(sqlOcupacion, batchParams)
            .stream()
            .collect(Collectors.toMap(r -> ((Number) r.get("ID_PROPIEDAD")).longValue(), r -> r));

        // 3. Facturado Periodo
        String sqlFacturado = """
            SELECT u.ID_PROPIEDAD,
                   NVL(SUM(c.VALOR_BASE), 0) AS FACTURADO
            FROM CUOTAS c
            JOIN UNIDADES u ON c.ID_UNIDAD = u.ID_UNIDAD
            WHERE u.ID_PROPIEDAD IN (:propIds)
              AND c.PERIODO IN (:periodos)
              AND c.ESTADO IN ('PENDIENTE', 'PAGADA_PARCIAL', 'PAGADA', 'VENCIDA')
            GROUP BY u.ID_PROPIEDAD
        """;
        Map<Long, BigDecimal> facturadoMap = jdbc.queryForList(sqlFacturado, batchParams)
            .stream()
            .collect(Collectors.toMap(
                r -> ((Number) r.get("ID_PROPIEDAD")).longValue(),
                r -> BigDecimal.valueOf(((Number) r.get("FACTURADO")).doubleValue()).setScale(2, RoundingMode.HALF_UP)
            ));

        // 4. Recaudado Periodo
        String sqlRecaudado = """
            SELECT u.ID_PROPIEDAD,
                   NVL(SUM(pg.MONTO_TOTAL), 0) AS RECAUDADO
            FROM PAGOS pg
            JOIN UNIDADES u ON pg.ID_UNIDAD = u.ID_UNIDAD
            WHERE u.ID_PROPIEDAD IN (:propIds)
              AND pg.ESTADO = 'APROBADO'
              AND TRUNC(pg.FECHA_PAGO) >= :fechaInicio
              AND TRUNC(pg.FECHA_PAGO) <= :fechaFin
            GROUP BY u.ID_PROPIEDAD
        """;
        Map<Long, BigDecimal> recaudadoMap = jdbc.queryForList(sqlRecaudado, batchParams)
            .stream()
            .collect(Collectors.toMap(
                r -> ((Number) r.get("ID_PROPIEDAD")).longValue(),
                r -> BigDecimal.valueOf(((Number) r.get("RECAUDADO")).doubleValue()).setScale(2, RoundingMode.HALF_UP)
            ));

        // 5. Cartera Periodo
        String sqlCarteraPeriodo = """
            SELECT u.ID_PROPIEDAD,
                   NVL(SUM(c.SALDO_PENDIENTE), 0) AS CARTERA_PERIODO
            FROM CUOTAS c
            JOIN UNIDADES u ON c.ID_UNIDAD = u.ID_UNIDAD
            WHERE u.ID_PROPIEDAD IN (:propIds)
              AND c.PERIODO IN (:periodos)
              AND c.ESTADO IN ('PENDIENTE', 'PAGADA_PARCIAL', 'VENCIDA')
            GROUP BY u.ID_PROPIEDAD
        """;
        Map<Long, BigDecimal> carteraPeriodoMap = jdbc.queryForList(sqlCarteraPeriodo, batchParams)
            .stream()
            .collect(Collectors.toMap(
                r -> ((Number) r.get("ID_PROPIEDAD")).longValue(),
                r -> BigDecimal.valueOf(((Number) r.get("CARTERA_PERIODO")).doubleValue()).setScale(2, RoundingMode.HALF_UP)
            ));

        // 6. Cartera Total Actual (viva)
        String sqlCarteraActual = """
            SELECT u.ID_PROPIEDAD,
                   NVL(SUM(c.SALDO_PENDIENTE), 0) AS CARTERA_ACTUAL
            FROM CUOTAS c
            JOIN UNIDADES u ON c.ID_UNIDAD = u.ID_UNIDAD
            WHERE u.ID_PROPIEDAD IN (:propIds)
              AND c.ESTADO IN ('PENDIENTE', 'PAGADA_PARCIAL', 'VENCIDA')
            GROUP BY u.ID_PROPIEDAD
        """;
        Map<Long, BigDecimal> carteraActualMap = jdbc.queryForList(sqlCarteraActual, batchParams)
            .stream()
            .collect(Collectors.toMap(
                r -> ((Number) r.get("ID_PROPIEDAD")).longValue(),
                r -> BigDecimal.valueOf(((Number) r.get("CARTERA_ACTUAL")).doubleValue()).setScale(2, RoundingMode.HALF_UP)
            ));

        // 7. Construir Benchmark por propiedad
        List<PropertyBenchmarkBuilder> builders = new ArrayList<>();
        long totalOrgUnidades = 0;
        long totalOrgOcupadas = 0;
        BigDecimal totalOrgFacturado = BigDecimal.ZERO;
        BigDecimal totalOrgRecaudado = BigDecimal.ZERO;
        BigDecimal totalOrgCarteraPeriodo = BigDecimal.ZERO;
        BigDecimal totalOrgCarteraViva = BigDecimal.ZERO;

        for (Map<String, Object> row : propRows) {
            Long pid = ((Number) row.get("ID_PROPIEDAD")).longValue();
            String nombre = (String) row.get("NOMBRE");
            String ciudad = (String) row.get("CIUDAD");

            PropertyBenchmarkBuilder b = new PropertyBenchmarkBuilder();
            b.idPropiedad = pid;
            b.nombre = nombre;
            b.ciudad = ciudad;

            Map<String, Object> oc = ocupacionMap.get(pid);
            if (oc != null) {
                b.totalUnidades = ((Number) oc.get("TOTAL_UNIDADES")).longValue();
                b.unidadesOcupadas = ((Number) oc.get("UNIDADES_OCUPADAS")).longValue();
            } else {
                b.totalUnidades = 0;
                b.unidadesOcupadas = 0;
            }

            b.ocupacionActualPct = b.totalUnidades == 0 ? 0.0 :
                Math.round(((double) b.unidadesOcupadas / b.totalUnidades * 100.0) * 100.0) / 100.0;

            b.facturadoPeriodo = facturadoMap.getOrDefault(pid, BigDecimal.ZERO);
            b.recaudadoPeriodo = recaudadoMap.getOrDefault(pid, BigDecimal.ZERO);
            b.carteraPeriodo = carteraPeriodoMap.getOrDefault(pid, BigDecimal.ZERO);
            b.carteraTotalActual = carteraActualMap.getOrDefault(pid, BigDecimal.ZERO);

            // Efectividad de recaudo
            if (b.facturadoPeriodo.compareTo(BigDecimal.ZERO) == 0) {
                b.efectividadRecaudoPct = 100.0;
            } else {
                double raw = (b.recaudadoPeriodo.doubleValue() / b.facturadoPeriodo.doubleValue()) * 100.0;
                b.efectividadRecaudoPct = Math.round(Math.min(100.0, Math.max(0.0, raw)) * 100.0) / 100.0;
            }

            // Morosidad
            if (b.facturadoPeriodo.compareTo(BigDecimal.ZERO) == 0) {
                b.indiceMorosidadPct = 0.0;
            } else {
                double raw = (b.carteraPeriodo.doubleValue() / b.facturadoPeriodo.doubleValue()) * 100.0;
                b.indiceMorosidadPct = Math.round(Math.min(100.0, Math.max(0.0, raw)) * 100.0) / 100.0;
            }

            totalOrgUnidades += b.totalUnidades;
            totalOrgOcupadas += b.unidadesOcupadas;
            totalOrgFacturado = totalOrgFacturado.add(b.facturadoPeriodo);
            totalOrgRecaudado = totalOrgRecaudado.add(b.recaudadoPeriodo);
            totalOrgCarteraPeriodo = totalOrgCarteraPeriodo.add(b.carteraPeriodo);
            totalOrgCarteraViva = totalOrgCarteraViva.add(b.carteraTotalActual);

            builders.add(b);
        }

        // 8. Rankings independientes deterministas
        // 8.1 Ranking Efectividad: efectividadRecaudoPct DESC, recaudoPeriodo DESC, nombre ASC, idPropiedad ASC
        List<PropertyBenchmarkBuilder> porEfectividad = new ArrayList<>(builders);
        porEfectividad.sort(
            Comparator.comparing(PropertyBenchmarkBuilder::getEfectividadRecaudoPct, Comparator.reverseOrder())
                .thenComparing(PropertyBenchmarkBuilder::getRecaudadoPeriodo, Comparator.reverseOrder())
                .thenComparing(PropertyBenchmarkBuilder::getNombre)
                .thenComparing(PropertyBenchmarkBuilder::getIdPropiedad)
        );
        for (int i = 0; i < porEfectividad.size(); i++) {
            porEfectividad.get(i).rankingEfectividad = i + 1;
        }

        // 8.2 Ranking Morosidad: indiceMorosidadPct ASC, carteraPeriodo ASC, nombre ASC, idPropiedad ASC
        List<PropertyBenchmarkBuilder> porMorosidad = new ArrayList<>(builders);
        porMorosidad.sort(
            Comparator.comparing(PropertyBenchmarkBuilder::getIndiceMorosidadPct)
                .thenComparing(PropertyBenchmarkBuilder::getCarteraPeriodo)
                .thenComparing(PropertyBenchmarkBuilder::getNombre)
                .thenComparing(PropertyBenchmarkBuilder::getIdPropiedad)
        );
        for (int i = 0; i < porMorosidad.size(); i++) {
            porMorosidad.get(i).rankingMorosidad = i + 1;
        }

        // 8.3 Ranking Ocupación: ocupacionActualPct DESC, totalUnidades DESC, nombre ASC, idPropiedad ASC
        List<PropertyBenchmarkBuilder> porOcupacion = new ArrayList<>(builders);
        porOcupacion.sort(
            Comparator.comparing(PropertyBenchmarkBuilder::getOcupacionActualPct, Comparator.reverseOrder())
                .thenComparing(PropertyBenchmarkBuilder::getTotalUnidades, Comparator.reverseOrder())
                .thenComparing(PropertyBenchmarkBuilder::getNombre)
                .thenComparing(PropertyBenchmarkBuilder::getIdPropiedad)
        );
        for (int i = 0; i < porOcupacion.size(); i++) {
            porOcupacion.get(i).rankingOcupacion = i + 1;
        }

        List<PropertyBenchmarkDTO> benchmarkList = builders.stream()
            .map(PropertyBenchmarkBuilder::build)
            .toList();

        // 9. KPIs Globales de la Organización
        Double efectividadGlobal;
        if (totalOrgFacturado.compareTo(BigDecimal.ZERO) == 0) {
            efectividadGlobal = 100.0;
        } else {
            double raw = (totalOrgRecaudado.doubleValue() / totalOrgFacturado.doubleValue()) * 100.0;
            efectividadGlobal = Math.round(Math.min(100.0, Math.max(0.0, raw)) * 100.0) / 100.0;
        }

        Double morosidadGlobal;
        if (totalOrgFacturado.compareTo(BigDecimal.ZERO) == 0) {
            morosidadGlobal = 0.0;
        } else {
            double raw = (totalOrgCarteraPeriodo.doubleValue() / totalOrgFacturado.doubleValue()) * 100.0;
            morosidadGlobal = Math.round(Math.min(100.0, Math.max(0.0, raw)) * 100.0) / 100.0;
        }

        Double ocupacionPromedio = totalOrgUnidades == 0 ? 0.0 :
            Math.round(((double) totalOrgOcupadas / totalOrgUnidades * 100.0) * 100.0) / 100.0;

        OrgAnalyticsKpisDTO kpisGlobales = new OrgAnalyticsKpisDTO(
            builders.size(),
            totalOrgUnidades,
            totalOrgFacturado,
            totalOrgRecaudado,
            totalOrgCarteraPeriodo,
            totalOrgCarteraViva,
            efectividadGlobal,
            morosidadGlobal,
            ocupacionPromedio
        );

        // 10. Tendencia Mensual Global
        String sqlTrendFact = """
            SELECT c.PERIODO, NVL(SUM(c.VALOR_BASE), 0) AS FACTURADO
            FROM CUOTAS c
            JOIN UNIDADES u ON c.ID_UNIDAD = u.ID_UNIDAD
            JOIN PROPIEDADES p ON u.ID_PROPIEDAD = p.ID_PROPIEDAD
            WHERE p.ID_ORGANIZACION = :orgId
              AND c.PERIODO IN (:periodos)
              AND c.ESTADO IN ('PENDIENTE', 'PAGADA_PARCIAL', 'PAGADA', 'VENCIDA')
            GROUP BY c.PERIODO
        """;
        Map<String, BigDecimal> trendFactMap = jdbc.queryForList(sqlTrendFact, batchParams)
            .stream()
            .collect(Collectors.toMap(
                r -> (String) r.get("PERIODO"),
                r -> BigDecimal.valueOf(((Number) r.get("FACTURADO")).doubleValue()).setScale(2, RoundingMode.HALF_UP)
            ));

        String sqlTrendRec = """
            SELECT TO_CHAR(pg.FECHA_PAGO, 'YYYY-MM') AS PERIODO, NVL(SUM(pg.MONTO_TOTAL), 0) AS RECAUDADO
            FROM PAGOS pg
            JOIN UNIDADES u ON pg.ID_UNIDAD = u.ID_UNIDAD
            JOIN PROPIEDADES p ON u.ID_PROPIEDAD = p.ID_PROPIEDAD
            WHERE p.ID_ORGANIZACION = :orgId
              AND pg.ESTADO = 'APROBADO'
              AND TRUNC(pg.FECHA_PAGO) >= :fechaInicio
              AND TRUNC(pg.FECHA_PAGO) <= :fechaFin
            GROUP BY TO_CHAR(pg.FECHA_PAGO, 'YYYY-MM')
        """;
        Map<String, BigDecimal> trendRecMap = jdbc.queryForList(sqlTrendRec, batchParams)
            .stream()
            .collect(Collectors.toMap(
                r -> (String) r.get("PERIODO"),
                r -> BigDecimal.valueOf(((Number) r.get("RECAUDADO")).doubleValue()).setScale(2, RoundingMode.HALF_UP)
            ));

        String sqlTrendCart = """
            SELECT c.PERIODO, NVL(SUM(c.SALDO_PENDIENTE), 0) AS CARTERA_PERIODO
            FROM CUOTAS c
            JOIN UNIDADES u ON c.ID_UNIDAD = u.ID_UNIDAD
            JOIN PROPIEDADES p ON u.ID_PROPIEDAD = p.ID_PROPIEDAD
            WHERE p.ID_ORGANIZACION = :orgId
              AND c.PERIODO IN (:periodos)
              AND c.ESTADO IN ('PENDIENTE', 'PAGADA_PARCIAL', 'VENCIDA')
            GROUP BY c.PERIODO
        """;
        Map<String, BigDecimal> trendCartMap = jdbc.queryForList(sqlTrendCart, batchParams)
            .stream()
            .collect(Collectors.toMap(
                r -> (String) r.get("PERIODO"),
                r -> BigDecimal.valueOf(((Number) r.get("CARTERA_PERIODO")).doubleValue()).setScale(2, RoundingMode.HALF_UP)
            ));

        List<MonthlyTrendDTO> tendenciaMensual = window.periodos.stream()
            .map(p -> new MonthlyTrendDTO(
                p,
                trendFactMap.getOrDefault(p, BigDecimal.ZERO),
                trendRecMap.getOrDefault(p, BigDecimal.ZERO),
                trendCartMap.getOrDefault(p, BigDecimal.ZERO)
            ))
            .toList();

        return new OrgAnalyticsDTO(window.horizonte, window.mesesEvaluados, kpisGlobales, benchmarkList, tendenciaMensual);
    }

    @Override
    public PropertyAnalyticsDTO getPropertyAnalytics(Integer meses) {
        SaedContext ctx = SaedContextHolder.getContext();
        Long propId = ctx != null ? ctx.getPropertyId() : null;
        if (propId == null) {
            throw new AccessDeniedException("No se ha establecido un contexto de propiedad activo");
        }

        TemporalWindow window = resolveTemporalWindow(meses, null);
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("propId", propId)
            .addValue("periodos", window.periodos)
            .addValue("fechaInicio", window.fechaInicio)
            .addValue("fechaFin", window.fechaFin);

        // 1. Ocupación actual de la propiedad
        String sqlOcupacion = """
            SELECT COUNT(u.ID_UNIDAD) AS TOTAL_UNIDADES,
                   COUNT(DISTINCT CASE WHEN ru.ESTADO IN ('ACTIVO', 'ACTIVA') THEN u.ID_UNIDAD END) AS UNIDADES_OCUPADAS
            FROM UNIDADES u
            LEFT JOIN RESIDENTES_UNIDAD ru ON u.ID_UNIDAD = ru.ID_UNIDAD
            WHERE u.ID_PROPIEDAD = :propId AND u.ESTADO != 'ELIMINADA'
        """;
        Map<String, Object> ocRow = jdbc.queryForMap(sqlOcupacion, params);
        long totalUnidades = ocRow.get("TOTAL_UNIDADES") != null ? ((Number) ocRow.get("TOTAL_UNIDADES")).longValue() : 0;
        long unidadesOcupadas = ocRow.get("UNIDADES_OCUPADAS") != null ? ((Number) ocRow.get("UNIDADES_OCUPADAS")).longValue() : 0;
        Double ocupacionActualPct = totalUnidades == 0 ? 0.0 :
            Math.round(((double) unidadesOcupadas / totalUnidades * 100.0) * 100.0) / 100.0;

        // 2. Tendencia Financiera (Facturado, Recaudado, Gastos, Balance)
        String sqlFact = """
            SELECT c.PERIODO, NVL(SUM(c.VALOR_BASE), 0) AS FACTURADO
            FROM CUOTAS c
            JOIN UNIDADES u ON c.ID_UNIDAD = u.ID_UNIDAD
            WHERE u.ID_PROPIEDAD = :propId
              AND c.PERIODO IN (:periodos)
              AND c.ESTADO IN ('PENDIENTE', 'PAGADA_PARCIAL', 'PAGADA', 'VENCIDA')
            GROUP BY c.PERIODO
        """;
        Map<String, BigDecimal> factMap = jdbc.queryForList(sqlFact, params).stream()
            .collect(Collectors.toMap(
                r -> (String) r.get("PERIODO"),
                r -> BigDecimal.valueOf(((Number) r.get("FACTURADO")).doubleValue()).setScale(2, RoundingMode.HALF_UP)
            ));

        String sqlRec = """
            SELECT TO_CHAR(pg.FECHA_PAGO, 'YYYY-MM') AS PERIODO, NVL(SUM(pg.MONTO_TOTAL), 0) AS RECAUDADO
            FROM PAGOS pg
            JOIN UNIDADES u ON pg.ID_UNIDAD = u.ID_UNIDAD
            WHERE u.ID_PROPIEDAD = :propId
              AND pg.ESTADO = 'APROBADO'
              AND TRUNC(pg.FECHA_PAGO) >= :fechaInicio
              AND TRUNC(pg.FECHA_PAGO) <= :fechaFin
            GROUP BY TO_CHAR(pg.FECHA_PAGO, 'YYYY-MM')
        """;
        Map<String, BigDecimal> recMap = jdbc.queryForList(sqlRec, params).stream()
            .collect(Collectors.toMap(
                r -> (String) r.get("PERIODO"),
                r -> BigDecimal.valueOf(((Number) r.get("RECAUDADO")).doubleValue()).setScale(2, RoundingMode.HALF_UP)
            ));

        String sqlGastos = """
            SELECT TO_CHAR(g.FECHA_GASTO, 'YYYY-MM') AS PERIODO, NVL(SUM(g.MONTO), 0) AS TOTAL_GASTO
            FROM GASTOS g
            WHERE g.ID_PROPIEDAD = :propId
              AND g.ESTADO = 'PAGADO'
              AND g.FECHA_GASTO >= :fechaInicio
              AND g.FECHA_GASTO <= :fechaFin
            GROUP BY TO_CHAR(g.FECHA_GASTO, 'YYYY-MM')
        """;
        Map<String, BigDecimal> gastosMap = jdbc.queryForList(sqlGastos, params).stream()
            .collect(Collectors.toMap(
                r -> (String) r.get("PERIODO"),
                r -> BigDecimal.valueOf(((Number) r.get("TOTAL_GASTO")).doubleValue()).setScale(2, RoundingMode.HALF_UP)
            ));

        List<PropertyFinancialTrendDTO> tendenciaFinanciera = window.periodos.stream()
            .map(p -> {
                BigDecimal fact = factMap.getOrDefault(p, BigDecimal.ZERO);
                BigDecimal rec = recMap.getOrDefault(p, BigDecimal.ZERO);
                BigDecimal gas = gastosMap.getOrDefault(p, BigDecimal.ZERO);
                BigDecimal bal = rec.subtract(gas);
                return new PropertyFinancialTrendDTO(p, fact, rec, gas, bal);
            })
            .toList();

        // 3. Tendencia Operativa (Visitas, Paquetes, PQRS, SLA %)
        String sqlVisitas = """
            SELECT TO_CHAR(v.FECHA_CREACION, 'YYYY-MM') AS PERIODO, COUNT(*) AS TOTAL
            FROM VISITAS v
            JOIN UNIDADES u ON v.ID_UNIDAD = u.ID_UNIDAD
            WHERE u.ID_PROPIEDAD = :propId
              AND TRUNC(v.FECHA_CREACION) >= :fechaInicio
              AND TRUNC(v.FECHA_CREACION) <= :fechaFin
            GROUP BY TO_CHAR(v.FECHA_CREACION, 'YYYY-MM')
        """;
        Map<String, Long> visitasMap = jdbc.queryForList(sqlVisitas, params).stream()
            .collect(Collectors.toMap(
                r -> (String) r.get("PERIODO"),
                r -> ((Number) r.get("TOTAL")).longValue()
            ));

        String sqlPaquetes = """
            SELECT TO_CHAR(p.FECHA_RECEPCION, 'YYYY-MM') AS PERIODO, COUNT(*) AS TOTAL
            FROM PAQUETES p
            WHERE p.ID_PROPIEDAD = :propId
              AND TRUNC(p.FECHA_RECEPCION) >= :fechaInicio
              AND TRUNC(p.FECHA_RECEPCION) <= :fechaFin
            GROUP BY TO_CHAR(p.FECHA_RECEPCION, 'YYYY-MM')
        """;
        Map<String, Long> paquetesMap = jdbc.queryForList(sqlPaquetes, params).stream()
            .collect(Collectors.toMap(
                r -> (String) r.get("PERIODO"),
                r -> ((Number) r.get("TOTAL")).longValue()
            ));

        String sqlPqrs = """
            SELECT TO_CHAR(q.FECHA_RADICACION, 'YYYY-MM') AS PERIODO,
                   COUNT(*) AS TOTAL_RADICADAS,
                   COUNT(CASE WHEN q.FECHA_CIERRE IS NOT NULL AND q.FECHA_CIERRE <= q.FECHA_LIMITE_SLA THEN 1 END) AS EN_SLA
            FROM PQRS_TICKETS q
            WHERE q.ID_PROPIEDAD = :propId
              AND TRUNC(q.FECHA_RADICACION) >= :fechaInicio
              AND TRUNC(q.FECHA_RADICACION) <= :fechaFin
            GROUP BY TO_CHAR(q.FECHA_RADICACION, 'YYYY-MM')
        """;
        Map<String, Map<String, Object>> pqrsMap = jdbc.queryForList(sqlPqrs, params).stream()
            .collect(Collectors.toMap(
                r -> (String) r.get("PERIODO"),
                r -> r
            ));

        List<PropertyOperationalTrendDTO> tendenciaOperativa = window.periodos.stream()
            .map(p -> {
                long vis = visitasMap.getOrDefault(p, 0L);
                long paq = paquetesMap.getOrDefault(p, 0L);
                Map<String, Object> pq = pqrsMap.get(p);
                long rad = 0L;
                long enSla = 0L;
                if (pq != null) {
                    rad = ((Number) pq.get("TOTAL_RADICADAS")).longValue();
                    enSla = ((Number) pq.get("EN_SLA")).longValue();
                }
                Double slaPct = rad == 0 ? 100.0 :
                    Math.round(Math.min(100.0, Math.max(0.0, ((double) enSla / rad) * 100.0)) * 100.0) / 100.0;
                return new PropertyOperationalTrendDTO(p, vis, paq, rad, slaPct);
            })
            .toList();

        return new PropertyAnalyticsDTO(propId, window.horizonte, ocupacionActualPct, tendenciaFinanciera, tendenciaOperativa);
    }

    @Override
    public PlatformAnalyticsDTO getPlatformAnalytics(Integer meses) {
        // 1. Tasa de Retención Organizacional
        Number totalOrgNum = jdbc.queryForObject(
            "SELECT COUNT(*) FROM ORGANIZACIONES", new MapSqlParameterSource(), Number.class);
        Number activasOrgNum = jdbc.queryForObject(
            "SELECT COUNT(*) FROM ORGANIZACIONES WHERE ESTADO = 'ACTIVA'", new MapSqlParameterSource(), Number.class);
        long totalOrg = totalOrgNum != null ? totalOrgNum.longValue() : 0L;
        long activasOrg = activasOrgNum != null ? activasOrgNum.longValue() : 0L;
        Double tasaRetencion = totalOrg == 0 ? 100.0 :
            Math.round(((double) activasOrg / totalOrg * 100.0) * 100.0) / 100.0;

        // 2. Distribución de Propiedades por Ciudad
        String sqlCiudad = """
            SELECT NVL(CIUDAD, 'Sin Ciudad') AS CIUDAD, COUNT(*) AS TOTAL
            FROM PROPIEDADES
            WHERE ESTADO = 'ACTIVA'
            GROUP BY NVL(CIUDAD, 'Sin Ciudad')
            ORDER BY TOTAL DESC, CIUDAD ASC
        """;
        List<Map<String, Object>> porCiudad = jdbc.queryForList(sqlCiudad, new MapSqlParameterSource());

        // 3. Crecimiento mensual de organizaciones (últimos 12 meses)
        YearMonth now = YearMonth.now();
        YearMonth start = now.minusMonths(11);
        List<String> mesesList = new ArrayList<>();
        for (int i = 11; i >= 0; i--) {
            mesesList.add(now.minusMonths(i).toString());
        }
        LocalDate fechaInicio = start.atDay(1);

        String sqlCrecimiento = """
            SELECT TO_CHAR(TRUNC(FECHA_CREACION, 'MM'), 'YYYY-MM') AS MES, COUNT(*) AS TOTAL
            FROM ORGANIZACIONES
            WHERE TRUNC(FECHA_CREACION) >= :fechaInicio
            GROUP BY TO_CHAR(TRUNC(FECHA_CREACION, 'MM'), 'YYYY-MM')
            ORDER BY MES ASC
        """;
        Map<String, Long> crecMap = jdbc.queryForList(sqlCrecimiento, new MapSqlParameterSource("fechaInicio", fechaInicio))
            .stream()
            .collect(Collectors.toMap(
                r -> (String) r.get("MES"),
                r -> ((Number) r.get("TOTAL")).longValue()
            ));

        List<Map<String, Object>> crecimientoMensual = mesesList.stream()
            .map(m -> Map.<String, Object>of("mes", m, "nuevasOrganizaciones", crecMap.getOrDefault(m, 0L)))
            .toList();

        return new PlatformAnalyticsDTO(tasaRetencion, porCiudad, crecimientoMensual);
    }
}
