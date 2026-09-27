package com.saed.backend.dashboard.service.impl;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.dashboard.dto.*;
import com.saed.backend.dashboard.service.AnalyticsService;
import com.saed.backend.platform.dto.PlatformAnalyticsDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@Transactional(readOnly = true)
public class AnalyticsServiceImpl implements AnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsServiceImpl.class);

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

        // 3. Facturado, Cartera Periodo y Cartera Total Actual (consolidados en una sola consulta)
        String sqlCuotasConsolidadas = """
            SELECT u.ID_PROPIEDAD,
                   NVL(SUM(CASE WHEN c.PERIODO IN (:periodos) AND c.ESTADO IN ('PENDIENTE', 'PAGADA_PARCIAL', 'PAGADA', 'VENCIDA') THEN c.VALOR_BASE ELSE 0 END), 0) AS FACTURADO,
                   NVL(SUM(CASE WHEN c.PERIODO IN (:periodos) AND c.ESTADO IN ('PENDIENTE', 'PAGADA_PARCIAL', 'VENCIDA') THEN c.SALDO_PENDIENTE ELSE 0 END), 0) AS CARTERA_PERIODO,
                   NVL(SUM(CASE WHEN c.ESTADO IN ('PENDIENTE', 'PAGADA_PARCIAL', 'VENCIDA') THEN c.SALDO_PENDIENTE ELSE 0 END), 0) AS CARTERA_ACTUAL
            FROM CUOTAS c
            JOIN UNIDADES u ON c.ID_UNIDAD = u.ID_UNIDAD
            WHERE u.ID_PROPIEDAD IN (:propIds)
              AND (c.PERIODO IN (:periodos) OR c.ESTADO IN ('PENDIENTE', 'PAGADA_PARCIAL', 'VENCIDA'))
            GROUP BY u.ID_PROPIEDAD
        """;
        Map<Long, BigDecimal> facturadoMap = new HashMap<>();
        Map<Long, BigDecimal> carteraPeriodoMap = new HashMap<>();
        Map<Long, BigDecimal> carteraActualMap = new HashMap<>();

        for (Map<String, Object> r : jdbc.queryForList(sqlCuotasConsolidadas, batchParams)) {
            Long pid = ((Number) r.get("ID_PROPIEDAD")).longValue();
            facturadoMap.put(pid, BigDecimal.valueOf(((Number) r.get("FACTURADO")).doubleValue()).setScale(2, RoundingMode.HALF_UP));
            carteraPeriodoMap.put(pid, BigDecimal.valueOf(((Number) r.get("CARTERA_PERIODO")).doubleValue()).setScale(2, RoundingMode.HALF_UP));
            carteraActualMap.put(pid, BigDecimal.valueOf(((Number) r.get("CARTERA_ACTUAL")).doubleValue()).setScale(2, RoundingMode.HALF_UP));
        }

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

        // 10. Tendencia Mensual Global (Facturado y Cartera consolidados)
        String sqlTrendCuotas = """
            SELECT c.PERIODO,
                   NVL(SUM(CASE WHEN c.ESTADO IN ('PENDIENTE', 'PAGADA_PARCIAL', 'PAGADA', 'VENCIDA') THEN c.VALOR_BASE ELSE 0 END), 0) AS FACTURADO,
                   NVL(SUM(CASE WHEN c.ESTADO IN ('PENDIENTE', 'PAGADA_PARCIAL', 'VENCIDA') THEN c.SALDO_PENDIENTE ELSE 0 END), 0) AS CARTERA_PERIODO
            FROM CUOTAS c
            JOIN UNIDADES u ON c.ID_UNIDAD = u.ID_UNIDAD
            JOIN PROPIEDADES p ON u.ID_PROPIEDAD = p.ID_PROPIEDAD
            WHERE p.ID_ORGANIZACION = :orgId
              AND c.PERIODO IN (:periodos)
            GROUP BY c.PERIODO
        """;
        Map<String, BigDecimal> trendFactMap = new HashMap<>();
        Map<String, BigDecimal> trendCartMap = new HashMap<>();

        for (Map<String, Object> r : jdbc.queryForList(sqlTrendCuotas, batchParams)) {
            String p = (String) r.get("PERIODO");
            trendFactMap.put(p, BigDecimal.valueOf(((Number) r.get("FACTURADO")).doubleValue()).setScale(2, RoundingMode.HALF_UP));
            trendCartMap.put(p, BigDecimal.valueOf(((Number) r.get("CARTERA_PERIODO")).doubleValue()).setScale(2, RoundingMode.HALF_UP));
        }

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
        return getPlatformAnalytics(meses != null ? meses + "m" : "30d", null);
    }

    @Override
    public PlatformAnalyticsDTO getPlatformAnalytics(String periodo, Long idOrganizacion) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        boolean hasOrg = idOrganizacion != null;
        if (hasOrg) {
            params.addValue("orgId", idOrganizacion, java.sql.Types.NUMERIC);
        }

        LocalDate fechaInicio = null;
        String effPeriodo = periodo != null ? periodo.toLowerCase().trim() : "30d";
        LocalDate hoy = LocalDate.now();
        if ("hoy".equals(effPeriodo)) {
            fechaInicio = hoy;
        } else if ("7d".equals(effPeriodo)) {
            fechaInicio = hoy.minusDays(7);
        } else if ("30d".equals(effPeriodo)) {
            fechaInicio = hoy.minusDays(30);
        } else if ("90d".equals(effPeriodo) || "3m".equals(effPeriodo)) {
            fechaInicio = hoy.minusDays(90);
        } else if ("1y".equals(effPeriodo) || "anio".equals(effPeriodo)) {
            fechaInicio = hoy.minusYears(1);
        }
        java.sql.Timestamp fechaInicioTs = fechaInicio != null ? java.sql.Timestamp.valueOf(fechaInicio.atStartOfDay()) : null;
        boolean hasFecha = fechaInicioTs != null;
        if (hasFecha) {
            params.addValue("fechaInicioTs", fechaInicioTs, java.sql.Types.TIMESTAMP);
        }

        String orgF = hasOrg ? "ID_ORGANIZACION = :orgId" : "1=1";
        String pOrgF = hasOrg ? "p.ID_ORGANIZACION = :orgId" : "1=1";
        String uaOrgF = hasOrg ? "ua.ID_ORGANIZACION = :orgId" : "1=1";
        String prOrgF = hasOrg ? "pr.ID_ORGANIZACION = :orgId" : "1=1";
        String mOrgF = hasOrg ? "m.ID_ORGANIZACION = :orgId" : "1=1";
        String fechaF_v = hasFecha ? "v.FECHA_INGRESO >= :fechaInicioTs" : "1=1";
        String fechaF_pq = hasFecha ? "pq.FECHA_RECEPCION >= :fechaInicioTs" : "1=1";
        String fechaF_tk = hasFecha ? "tk.FECHA_RADICACION >= :fechaInicioTs" : "1=1";
        String fechaF_mt = hasFecha ? "mt.FECHA_CREACION >= :fechaInicioTs" : "1=1";
        String fechaF_rs = hasFecha ? "rs.FECHA_SOLICITUD >= :fechaInicioTs" : "1=1";
        String fechaF_cm = hasFecha ? "cm.FECHA_CREACION >= :fechaInicioTs" : "1=1";
        String fechaF_tx = hasFecha ? "FECHA_REGISTRO >= :fechaInicioTs" : "1=1";
        String fechaF_sec = hasFecha ? "FECHA_HORA >= :fechaInicioTs" : "1=1";

        // 1. Resumen Global Consolidado (1 solo round-trip a Oracle)
        Map<String, Object> resumenGlobal = new HashMap<>();
        long totalOrg = 0L;
        long activasOrg = 0L;
        try {
            String sqlResumen = String.format("""
                SELECT
                    (SELECT COUNT(*) FROM ORGANIZACIONES WHERE %s) AS TOTAL_ORG,
                    (SELECT COUNT(*) FROM ORGANIZACIONES WHERE ESTADO = 'ACTIVA' AND %s) AS ACTIVAS_ORG,
                    (SELECT COUNT(*) FROM PROPIEDADES WHERE %s) AS TOTAL_PROP,
                    (SELECT COUNT(*) FROM PROPIEDADES WHERE ESTADO = 'ACTIVA' AND %s) AS ACTIVAS_PROP,
                    (SELECT COUNT(*) FROM UNIDADES u JOIN PROPIEDADES p ON u.ID_PROPIEDAD = p.ID_PROPIEDAD WHERE %s) AS TOTAL_UNIDADES,
                    (SELECT COUNT(DISTINCT u.ID_USUARIO) FROM USUARIOS u LEFT JOIN USUARIO_ASIGNACIONES ua ON u.ID_USUARIO = ua.ID_USUARIO WHERE %s) AS TOTAL_USERS,
                    (SELECT COUNT(DISTINCT u.ID_USUARIO) FROM USUARIOS u LEFT JOIN USUARIO_ASIGNACIONES ua ON u.ID_USUARIO = ua.ID_USUARIO WHERE u.ESTADO = 'ACTIVO' AND %s) AS ACTIVE_USERS,
                    (SELECT COUNT(*) FROM RESIDENTES_UNIDAD ru JOIN UNIDADES u ON ru.ID_UNIDAD = u.ID_UNIDAD JOIN PROPIEDADES p ON u.ID_PROPIEDAD = p.ID_PROPIEDAD WHERE %s) AS TOTAL_RESID,
                    (SELECT COUNT(*) FROM RESIDENTES_UNIDAD ru JOIN UNIDADES u ON ru.ID_UNIDAD = u.ID_UNIDAD JOIN PROPIEDADES p ON u.ID_PROPIEDAD = p.ID_PROPIEDAD WHERE ru.ESTADO = 'ACTIVO' AND %s) AS ACTIVE_RESID,
                    (SELECT COUNT(*) FROM TRABAJADORES t LEFT JOIN PROVEEDORES pr ON t.ID_PROVEEDOR = pr.ID_PROVEEDOR WHERE %s) AS TOTAL_TRAB,
                    (SELECT COUNT(*) FROM TRABAJADORES t LEFT JOIN PROVEEDORES pr ON t.ID_PROVEEDOR = pr.ID_PROVEEDOR WHERE t.ESTADO = 'ACTIVO' AND %s) AS ACTIVE_TRAB
                FROM DUAL
            """, orgF, orgF, orgF, orgF, pOrgF, uaOrgF, uaOrgF, pOrgF, pOrgF, prOrgF, prOrgF);
            Map<String, Object> r = jdbc.queryForMap(sqlResumen, params);

            totalOrg = ((Number) r.getOrDefault("TOTAL_ORG", 0)).longValue();
            activasOrg = ((Number) r.getOrDefault("ACTIVAS_ORG", 0)).longValue();
            resumenGlobal.put("organizaciones", Map.of("total", totalOrg, "activas", activasOrg, "inactivas", Math.max(0, totalOrg - activasOrg)));

            long totalProp = ((Number) r.getOrDefault("TOTAL_PROP", 0)).longValue();
            long activasProp = ((Number) r.getOrDefault("ACTIVAS_PROP", 0)).longValue();
            resumenGlobal.put("propiedades", Map.of("total", totalProp, "activas", activasProp, "inactivas", Math.max(0, totalProp - activasProp)));

            long totalUnidades = ((Number) r.getOrDefault("TOTAL_UNIDADES", 0)).longValue();
            resumenGlobal.put("unidades", Map.of("total", totalUnidades));

            long totalUsers = ((Number) r.getOrDefault("TOTAL_USERS", 0)).longValue();
            long activeUsers = ((Number) r.getOrDefault("ACTIVE_USERS", 0)).longValue();
            resumenGlobal.put("usuarios", Map.of("total", totalUsers, "activos", activeUsers, "inactivos", Math.max(0, totalUsers - activeUsers)));

            long totalResid = ((Number) r.getOrDefault("TOTAL_RESID", 0)).longValue();
            long activeResid = ((Number) r.getOrDefault("ACTIVE_RESID", 0)).longValue();
            resumenGlobal.put("residentes", Map.of("total", totalResid, "activos", activeResid, "inactivos", Math.max(0, totalResid - activeResid)));

            long totalTrab = ((Number) r.getOrDefault("TOTAL_TRAB", 0)).longValue();
            long activeTrab = ((Number) r.getOrDefault("ACTIVE_TRAB", 0)).longValue();
            resumenGlobal.put("trabajadores", Map.of("total", totalTrab, "activos", activeTrab, "inactivos", Math.max(0, totalTrab - activeTrab)));
        } catch (Exception e) {
            log.error("Error cargando resumenGlobal en analítica: {}", e.getMessage());
        }

        // 2. Tasa de Retención
        Double tasaRetencion = totalOrg == 0 ? 100.0 :
            Math.round(((double) activasOrg / totalOrg * 100.0) * 100.0) / 100.0;

        // 3. Distribución de Propiedades por Tipo
        List<Map<String, Object>> porTipoPropiedad = List.of();
        try {
            String sqlTipoProp = String.format("""
                SELECT tp.CODIGO AS "codigo", tp.NOMBRE AS "tipo", COUNT(p.ID_PROPIEDAD) AS "cantidad"
                FROM TIPOS_PROPIEDAD tp
                LEFT JOIN PROPIEDADES p ON tp.ID_TIPO_PROPIEDAD = p.ID_TIPO_PROPIEDAD AND %s
                GROUP BY tp.CODIGO, tp.NOMBRE
                ORDER BY "cantidad" DESC
            """, pOrgF);
            porTipoPropiedad = jdbc.queryForList(sqlTipoProp, params);
        } catch (Exception e) {
            log.error("Error cargando distribucionTipoPropiedad: {}", e.getMessage());
        }

        // 4. Distribución de Propiedades por Ciudad
        List<Map<String, Object>> porCiudad = List.of();
        try {
            String sqlCiudad = String.format("""
                SELECT NVL(CIUDAD, 'Sin Ciudad') AS "ciudad", COUNT(*) AS "total"
                FROM PROPIEDADES
                WHERE ESTADO = 'ACTIVA' AND %s
                GROUP BY NVL(CIUDAD, 'Sin Ciudad')
                ORDER BY "total" DESC
            """, orgF);
            porCiudad = jdbc.queryForList(sqlCiudad, params);
        } catch (Exception e) {
            log.error("Error cargando distribucionPropiedadesCiudad: {}", e.getMessage());
        }

        // 5. Distribución de Unidades por Tipo
        List<Map<String, Object>> porTipoUnidad = List.of();
        try {
            String sqlTipoUnidad = String.format("""
                SELECT tu.CODIGO AS "codigo", tu.NOMBRE AS "tipo", COUNT(u.ID_UNIDAD) AS "cantidad"
                FROM TIPOS_UNIDAD tu
                LEFT JOIN UNIDADES u ON tu.ID_TIPO_UNIDAD = u.ID_TIPO_UNIDAD
                LEFT JOIN PROPIEDADES p ON u.ID_PROPIEDAD = p.ID_PROPIEDAD
                WHERE %s
                GROUP BY tu.CODIGO, tu.NOMBRE
                ORDER BY "cantidad" DESC
            """, pOrgF);
            porTipoUnidad = jdbc.queryForList(sqlTipoUnidad, params);
        } catch (Exception e) {
            log.error("Error cargando distribucionTipoUnidad: {}", e.getMessage());
        }

        // 6. Distribución de Roles
        List<Map<String, Object>> distribucionRoles = List.of();
        try {
            String sqlRoles = String.format("""
                SELECT r.CODIGO AS "rol", r.NOMBRE AS "rolNombre", COUNT(DISTINCT ua.ID_USUARIO) AS "cantidad"
                FROM ROLES r
                LEFT JOIN USUARIO_ASIGNACIONES ua ON r.ID_ROL = ua.ID_ROL AND ua.ESTADO = 'ACTIVA' AND %s
                GROUP BY r.CODIGO, r.NOMBRE
                ORDER BY "cantidad" DESC
            """, uaOrgF);
            distribucionRoles = jdbc.queryForList(sqlRoles, params);
        } catch (Exception e) {
            log.error("Error cargando distribucionRoles: {}", e.getMessage());
        }

        // 7. Distribución de Planes
        List<Map<String, Object>> distribucionPlanes = List.of();
        try {
            String sqlPlanes = String.format("""
                SELECT pl.CODIGO AS "planCodigo", pl.NOMBRE AS "planNombre", pl.PRECIO_MENSUAL AS "precioMensual", COUNT(m.ID_MEMBRESIA) AS "organizaciones"
                FROM PLANES pl
                LEFT JOIN MEMBRESIAS m ON pl.ID_PLAN = m.ID_PLAN AND m.ESTADO = 'ACTIVA' AND %s
                GROUP BY pl.CODIGO, pl.NOMBRE, pl.PRECIO_MENSUAL
                ORDER BY pl.PRECIO_MENSUAL ASC
            """, mOrgF);
            distribucionPlanes = jdbc.queryForList(sqlPlanes, params);
        } catch (Exception e) {
            log.error("Error cargando distribucionPlanes: {}", e.getMessage());
        }

        // 8. Membresías por Estado
        List<Map<String, Object>> membresiasPorEstado = List.of();
        try {
            String sqlMemb = String.format("""
                SELECT ESTADO AS "estado", COUNT(*) AS "cantidad"
                FROM MEMBRESIAS
                WHERE %s
                GROUP BY ESTADO
                ORDER BY "cantidad" DESC
            """, orgF);
            membresiasPorEstado = jdbc.queryForList(sqlMemb, params);
        } catch (Exception e) {
            log.error("Error cargando membresiasPorEstado: {}", e.getMessage());
        }

        // 9. Entitlements de Módulos
        List<Map<String, Object>> entitlementsModulos = List.of();
        try {
            String sqlModulos = """
                SELECT m.CODIGO AS "codigo", m.NOMBRE AS "nombre", NVL(m.CATEGORIA, 'GENERAL') AS "categoria", COUNT(DISTINCT pm.ID_PLAN) AS "planesHabilitados"
                FROM MODULOS m
                LEFT JOIN PLAN_MODULOS pm ON m.ID_MODULO = pm.ID_MODULO AND pm.ESTADO = 'ACTIVO'
                WHERE m.ESTADO = 'ACTIVO'
                GROUP BY m.CODIGO, m.NOMBRE, m.CATEGORIA
                ORDER BY "categoria", "nombre"
            """;
            entitlementsModulos = jdbc.queryForList(sqlModulos, params);
        } catch (Exception e) {
            log.error("Error cargando entitlementsModulos: {}", e.getMessage());
        }

        // 10. Actividad Operativa Global Consolidada (1 solo round-trip a Oracle)
        Map<String, Object> actividadOperativa = new HashMap<>();
        try {
            String sqlOperativa = String.format("""
                SELECT
                    (SELECT COUNT(*) FROM VISITAS v JOIN UNIDADES u ON v.ID_UNIDAD = u.ID_UNIDAD JOIN PROPIEDADES p ON u.ID_PROPIEDAD = p.ID_PROPIEDAD WHERE %s AND %s) AS VISITAS,
                    (SELECT COUNT(*) FROM PAQUETES pq JOIN PROPIEDADES p ON pq.ID_PROPIEDAD = p.ID_PROPIEDAD WHERE %s AND %s) AS PAQUETES,
                    (SELECT COUNT(*) FROM PQRS_TICKETS tk JOIN PROPIEDADES p ON tk.ID_PROPIEDAD = p.ID_PROPIEDAD WHERE %s AND %s) AS PQRS,
                    (SELECT COUNT(*) FROM MANTENIMIENTOS mt JOIN PROPIEDADES p ON mt.ID_PROPIEDAD = p.ID_PROPIEDAD WHERE %s AND %s) AS MANTENIMIENTOS,
                    (SELECT COUNT(*) FROM RESERVAS rs JOIN ZONAS_COMUNES zc ON rs.ID_ZONA = zc.ID_ZONA JOIN PROPIEDADES p ON zc.ID_PROPIEDAD = p.ID_PROPIEDAD WHERE %s AND %s) AS RESERVAS,
                    (SELECT COUNT(*) FROM COMUNICADOS cm JOIN PROPIEDADES p ON cm.ID_PROPIEDAD = p.ID_PROPIEDAD WHERE %s AND %s) AS COMUNICADOS
                FROM DUAL
            """, pOrgF, fechaF_v, pOrgF, fechaF_pq, pOrgF, fechaF_tk, pOrgF, fechaF_mt, pOrgF, fechaF_rs, pOrgF, fechaF_cm);
            Map<String, Object> opRow = jdbc.queryForMap(sqlOperativa, params);
            actividadOperativa.put("visitas", ((Number) opRow.getOrDefault("VISITAS", 0)).longValue());
            actividadOperativa.put("paquetes", ((Number) opRow.getOrDefault("PAQUETES", 0)).longValue());
            actividadOperativa.put("pqrs", ((Number) opRow.getOrDefault("PQRS", 0)).longValue());
            actividadOperativa.put("mantenimientos", ((Number) opRow.getOrDefault("MANTENIMIENTOS", 0)).longValue());
            actividadOperativa.put("reservas", ((Number) opRow.getOrDefault("RESERVAS", 0)).longValue());
            actividadOperativa.put("comunicados", ((Number) opRow.getOrDefault("COMUNICADOS", 0)).longValue());
        } catch (Exception e) {
            log.error("Error cargando actividadOperativa: {}", e.getMessage());
            actividadOperativa.put("visitas", 0L); actividadOperativa.put("paquetes", 0L);
            actividadOperativa.put("pqrs", 0L); actividadOperativa.put("mantenimientos", 0L);
            actividadOperativa.put("reservas", 0L); actividadOperativa.put("comunicados", 0L);
        }

        // 11. Transacciones Sandbox Wompi Consolidadas (1 solo round-trip a Oracle)
        Map<String, Object> transaccionesSandbox = new HashMap<>();
        try {
            String sqlTx = String.format("""
                SELECT
                    COUNT(*) AS TOTAL,
                    COUNT(CASE WHEN UPPER(ESTADO_PASARELA) = 'APPROVED' THEN 1 END) AS APROBADAS,
                    COUNT(CASE WHEN UPPER(ESTADO_PASARELA) IN ('DECLINED', 'ERROR') THEN 1 END) AS RECHAZADAS,
                    COUNT(CASE WHEN UPPER(ESTADO_PASARELA) = 'PENDING' THEN 1 END) AS PENDIENTES,
                    NVL(SUM(CASE WHEN UPPER(ESTADO_PASARELA) = 'APPROVED' THEN MONTO_CENTAVOS ELSE 0 END), 0) AS MONTO_APROBADO
                FROM TRANSACCIONES_PAGO
                WHERE %s AND %s
            """, orgF, fechaF_tx);
            Map<String, Object> txRow = jdbc.queryForMap(sqlTx, params);
            transaccionesSandbox.put("total", ((Number) txRow.getOrDefault("TOTAL", 0)).longValue());
            transaccionesSandbox.put("aprobadas", ((Number) txRow.getOrDefault("APROBADAS", 0)).longValue());
            transaccionesSandbox.put("rechazadas", ((Number) txRow.getOrDefault("RECHAZADAS", 0)).longValue());
            transaccionesSandbox.put("pendientes", ((Number) txRow.getOrDefault("PENDIENTES", 0)).longValue());
            transaccionesSandbox.put("montoAprobadoCentavos", ((Number) txRow.getOrDefault("MONTO_APROBADO", 0)).longValue());
            transaccionesSandbox.put("aviso", "Entorno Sandbox Wompi activo — Datos de prueba y validación técnica, no facturación comercial.");
        } catch (Exception e) {
            log.error("Error cargando transaccionesSandbox: {}", e.getMessage());
            transaccionesSandbox.put("total", 0L); transaccionesSandbox.put("aprobadas", 0L);
            transaccionesSandbox.put("rechazadas", 0L); transaccionesSandbox.put("pendientes", 0L);
            transaccionesSandbox.put("montoAprobadoCentavos", 0L);
            transaccionesSandbox.put("aviso", "Entorno Sandbox Wompi activo.");
        }

        // 12. Métricas de Seguridad y Auditoría Consolidadas (1 solo round-trip a Oracle)
        Map<String, Object> metricasSeguridad = new HashMap<>();
        try {
            String sqlSec = String.format("""
                SELECT
                    COUNT(CASE WHEN ACCION LIKE '%%LOGIN%%SUCCESS%%' THEN 1 END) AS LOGINS_EXITOSOS,
                    COUNT(CASE WHEN ACCION LIKE '%%LOGIN%%FAIL%%' THEN 1 END) AS LOGINS_FALLIDOS,
                    COUNT(*) AS ACCIONES_AUDITADAS
                FROM AUDITORIA_LOG
                WHERE %s AND %s
            """, orgF, fechaF_sec);
            Map<String, Object> secRow = jdbc.queryForMap(sqlSec, params);
            metricasSeguridad.put("loginsExitosos", ((Number) secRow.getOrDefault("LOGINS_EXITOSOS", 0)).longValue());
            metricasSeguridad.put("loginsFallidos", ((Number) secRow.getOrDefault("LOGINS_FALLIDOS", 0)).longValue());
            metricasSeguridad.put("accionesAuditadas", ((Number) secRow.getOrDefault("ACCIONES_AUDITADAS", 0)).longValue());
        } catch (Exception e) {
            log.error("Error cargando metricasSeguridad: {}", e.getMessage());
            metricasSeguridad.put("loginsExitosos", 0L);
            metricasSeguridad.put("loginsFallidos", 0L);
            metricasSeguridad.put("accionesAuditadas", 0L);
        }

        // 13. Crecimiento mensual de organizaciones (últimos 12 meses)
        List<Map<String, Object>> crecimientoMensual = List.of();
        try {
            YearMonth now = YearMonth.now();
            YearMonth start = now.minusMonths(11);
            List<String> mesesList = new ArrayList<>();
            for (int i = 11; i >= 0; i--) {
                mesesList.add(now.minusMonths(i).toString());
            }
            LocalDate fechaInicio12m = start.atDay(1);

            String sqlCrecimiento = """
                SELECT TO_CHAR(TRUNC(FECHA_CREACION, 'MM'), 'YYYY-MM') AS MES, COUNT(*) AS TOTAL
                FROM ORGANIZACIONES
                WHERE TRUNC(FECHA_CREACION) >= :fechaInicio12m
                GROUP BY TO_CHAR(TRUNC(FECHA_CREACION, 'MM'), 'YYYY-MM')
                ORDER BY MES ASC
            """;
            Map<String, Long> crecMap = jdbc.queryForList(sqlCrecimiento, new MapSqlParameterSource("fechaInicio12m", fechaInicio12m))
                .stream()
                .collect(Collectors.toMap(
                    r -> (String) r.get("MES"),
                    r -> ((Number) r.get("TOTAL")).longValue()
                ));

            crecimientoMensual = mesesList.stream()
                .map(m -> Map.<String, Object>of("mes", m, "nuevasOrganizaciones", crecMap.getOrDefault(m, 0L)))
                .toList();
        } catch (Exception ignored) {}

        return new PlatformAnalyticsDTO(
            tasaRetencion,
            porCiudad,
            crecimientoMensual,
            resumenGlobal,
            porTipoPropiedad,
            porTipoUnidad,
            distribucionRoles,
            distribucionPlanes,
            membresiasPorEstado,
            entitlementsModulos,
            actividadOperativa,
            transaccionesSandbox,
            metricasSeguridad
        );
    }
}
