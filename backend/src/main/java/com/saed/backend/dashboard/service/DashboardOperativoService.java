package com.saed.backend.dashboard.service;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.dashboard.dto.DashboardPorteriaDTO;
import com.saed.backend.dashboard.dto.DashboardPropiedadDTO;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

@Service
public class DashboardOperativoService {

    private final NamedParameterJdbcTemplate jdbc;

    public DashboardOperativoService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private Long resolvePropertyId() {
        SaedContext ctx = SaedContextHolder.getContext();
        Long propId = ctx != null ? ctx.getPropertyId() : null;
        if (propId == null) {
            throw new AccessDeniedException("No se ha establecido un contexto de propiedad activo");
        }
        return propId;
    }

    public DashboardPropiedadDTO getDashboardPropiedad() {
        Long propId = resolvePropertyId();
        MapSqlParameterSource params = new MapSqlParameterSource("propId", propId);

        // 1. Total Unidades
        long totalUnidades = 0;
        try {
            Long val = jdbc.queryForObject(
                "SELECT COUNT(*) FROM UNIDADES WHERE ID_PROPIEDAD = :propId AND ESTADO != 'ELIMINADA'",
                params, Long.class);
            totalUnidades = val != null ? val : 0;
        } catch (Exception ignored) {}

        // 2. Total Residentes Censados
        long totalPersonas = 0;
        try {
            Long val = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT ru.ID_PERSONA) FROM RESIDENTES_UNIDAD ru " +
                "JOIN UNIDADES u ON ru.ID_UNIDAD = u.ID_UNIDAD " +
                "WHERE u.ID_PROPIEDAD = :propId AND ru.ESTADO IN ('ACTIVO', 'ACTIVA')",
                params, Long.class);
            totalPersonas = val != null ? val : 0;
        } catch (Exception ignored) {}

        // 3. Cartera y Mora
        BigDecimal carteraTotal = BigDecimal.ZERO;
        BigDecimal moraTotal = BigDecimal.ZERO;
        long cuotasPendientesCount = 0;
        try {
            Map<String, Object> cartRow = jdbc.queryForMap(
                "SELECT NVL(SUM(c.SALDO_TOTAL), 0) AS TOTAL_CARTERA, " +
                "NVL(SUM(c.SALDO_MORA_30 + c.SALDO_MORA_60 + c.SALDO_MORA_90_MAS), 0) AS TOTAL_MORA " +
                "FROM CARTERA c " +
                "JOIN UNIDADES u ON c.ID_UNIDAD = u.ID_UNIDAD " +
                "WHERE u.ID_PROPIEDAD = :propId",
                params);
            carteraTotal = cartRow.get("TOTAL_CARTERA") instanceof Number n ? BigDecimal.valueOf(n.doubleValue()) : BigDecimal.ZERO;
            moraTotal = cartRow.get("TOTAL_MORA") instanceof Number n ? BigDecimal.valueOf(n.doubleValue()) : BigDecimal.ZERO;
        } catch (Exception ignored) {}

        try {
            Map<String, Object> cuotasRow = jdbc.queryForMap(
                "SELECT COUNT(*) AS CNT, NVL(SUM(c.SALDO_PENDIENTE), 0) AS SUM_PEND " +
                "FROM CUOTAS c " +
                "JOIN UNIDADES u ON c.ID_UNIDAD = u.ID_UNIDAD " +
                "WHERE u.ID_PROPIEDAD = :propId AND c.ESTADO IN ('PENDIENTE', 'VENCIDA')",
                params);
            if (cuotasRow.get("CNT") instanceof Number n) {
                cuotasPendientesCount = n.longValue();
            }
            if (carteraTotal.compareTo(BigDecimal.ZERO) == 0 && cuotasRow.get("SUM_PEND") instanceof Number n) {
                carteraTotal = BigDecimal.valueOf(n.doubleValue());
            }
        } catch (Exception ignored) {}

        // 4. Paquetes Pendientes
        long paquetesPendientes = 0;
        try {
            Long val = jdbc.queryForObject(
                "SELECT COUNT(*) FROM PAQUETES p " +
                "JOIN UNIDADES u ON p.ID_UNIDAD = u.ID_UNIDAD " +
                "WHERE u.ID_PROPIEDAD = :propId AND p.ESTADO IN ('RECIBIDO', 'PENDIENTE')",
                params, Long.class);
            paquetesPendientes = val != null ? val : 0;
        } catch (Exception ignored) {}

        // 5. Visitas Activas
        long visitasActivas = 0;
        try {
            Long val = jdbc.queryForObject(
                "SELECT COUNT(*) FROM VISITAS v " +
                "JOIN UNIDADES u ON v.ID_UNIDAD = u.ID_UNIDAD " +
                "WHERE u.ID_PROPIEDAD = :propId AND v.ESTADO IN ('ACTIVA', 'EN_CURSO', 'INGRESADO')",
                params, Long.class);
            visitasActivas = val != null ? val : 0;
        } catch (Exception ignored) {}

        // 6. Multas Pendientes
        long multasPendientes = 0;
        try {
            Long val = jdbc.queryForObject(
                "SELECT COUNT(*) FROM MULTAS m " +
                "JOIN UNIDADES u ON m.ID_UNIDAD = u.ID_UNIDAD " +
                "WHERE u.ID_PROPIEDAD = :propId AND m.ESTADO = 'PENDIENTE'",
                params, Long.class);
            multasPendientes = val != null ? val : 0;
        } catch (Exception ignored) {}

        return new DashboardPropiedadDTO(
            propId,
            totalUnidades,
            totalPersonas,
            carteraTotal,
            moraTotal,
            cuotasPendientesCount,
            paquetesPendientes,
            visitasActivas,
            multasPendientes
        );
    }

    public DashboardPorteriaDTO getDashboardPorteria() {
        Long propId = resolvePropertyId();
        MapSqlParameterSource params = new MapSqlParameterSource("propId", propId);

        // 1. Visitas Activas / En Curso / Programadas
        long visitasActivas = 0;
        try {
            Long val = jdbc.queryForObject(
                "SELECT COUNT(*) FROM VISITAS v " +
                "JOIN UNIDADES u ON v.ID_UNIDAD = u.ID_UNIDAD " +
                "WHERE u.ID_PROPIEDAD = :propId AND v.ESTADO IN ('ACTIVA', 'EN_CURSO', 'PENDIENTE', 'INGRESADO')",
                params, Long.class);
            visitasActivas = val != null ? val : 0;
        } catch (Exception ignored) {}

        // 2. Domicilios en curso
        long domiciliosActivos = 0;
        try {
            Long val = jdbc.queryForObject(
                "SELECT COUNT(*) FROM DOMICILIOS d WHERE d.ID_PROPIEDAD = :propId AND d.ESTADO = 'EN_CURSO'",
                params, Long.class);
            domiciliosActivos = val != null ? val : 0;
        } catch (Exception ignored) {}

        // 3. Total Pases Registrados
        long totalPases = 0;
        try {
            Long val = jdbc.queryForObject(
                "SELECT COUNT(*) FROM VISITAS v " +
                "JOIN UNIDADES u ON v.ID_UNIDAD = u.ID_UNIDAD " +
                "WHERE u.ID_PROPIEDAD = :propId",
                params, Long.class);
            totalPases = val != null ? val : 0;
        } catch (Exception ignored) {}

        // 4. Parqueaderos Visitantes
        long parqueaderosDisponibles = 0;
        long parqueaderosVisitantesTotal = 0;
        try {
            Map<String, Object> parqRow = jdbc.queryForMap(
                "SELECT COUNT(*) AS TOTAL_VISITANTES, " +
                "NVL(SUM(CASE WHEN ESTADO = 'DISPONIBLE' THEN 1 ELSE 0 END), 0) AS DISPONIBLES " +
                "FROM PARQUEADEROS " +
                "WHERE ID_PROPIEDAD = :propId AND UPPER(TIPO) IN ('VISITANTE', 'VISITANTES')",
                params);
            if (parqRow.get("TOTAL_VISITANTES") instanceof Number n) {
                parqueaderosVisitantesTotal = n.longValue();
            }
            if (parqRow.get("DISPONIBLES") instanceof Number n) {
                parqueaderosDisponibles = n.longValue();
            }
        } catch (Exception ignored) {}

        // 5. Paquetes en Custodia
        long paquetesEnCustodia = 0;
        try {
            Long val = jdbc.queryForObject(
                "SELECT COUNT(*) FROM PAQUETES p " +
                "JOIN UNIDADES u ON p.ID_UNIDAD = u.ID_UNIDAD " +
                "WHERE u.ID_PROPIEDAD = :propId AND p.ESTADO IN ('RECIBIDO', 'PENDIENTE')",
                params, Long.class);
            paquetesEnCustodia = val != null ? val : 0;
        } catch (Exception ignored) {}

        return new DashboardPorteriaDTO(
            propId,
            visitasActivas,
            domiciliosActivos,
            totalPases,
            parqueaderosDisponibles,
            parqueaderosVisitantesTotal,
            paquetesEnCustodia
        );
    }
}
