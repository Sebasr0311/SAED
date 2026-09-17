package com.saed.backend.platform.service.impl;

import com.saed.backend.platform.service.MembershipHistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Implementación centralizada del servicio de historial de membresías (GAP-ENT-05).
 * Garantiza atomicidad, validación canónica de estados conforme a CK_MEMBHIST_TIPO
 * y preservación de claves foráneas hacia USUARIOS y PLANES.
 */
@Service
public class MembershipHistoryServiceImpl implements MembershipHistoryService {

    private static final Logger log = LoggerFactory.getLogger(MembershipHistoryServiceImpl.class);

    private static final Set<String> TIPOS_VALIDOS = Set.of(
            "INICIO",
            "UPGRADE",
            "DOWNGRADE",
            "RENOVACION",
            "CANCELACION",
            "SUSPENSION",
            "REACTIVACION"
    );

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public MembershipHistoryServiceImpl(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void recordChange(Long idMembresia, Long idPlanAnterior, Long idPlanNuevo,
                             String tipoCambio, String observaciones, Long realizadoPor) {
        if (idMembresia == null) {
            throw new IllegalArgumentException("idMembresia es obligatorio para registrar historial");
        }
        if (idPlanNuevo == null) {
            throw new IllegalArgumentException("idPlanNuevo es obligatorio para registrar historial");
        }
        if (tipoCambio == null || tipoCambio.isBlank()) {
            throw new IllegalArgumentException("tipoCambio es obligatorio para registrar historial");
        }

        String tipoNorm = tipoCambio.trim().toUpperCase();
        if (!TIPOS_VALIDOS.contains(tipoNorm)) {
            throw new IllegalArgumentException("Tipo de cambio inválido '" + tipoCambio + "'. Valores permitidos: " + TIPOS_VALIDOS);
        }

        // Validar FK a USUARIOS para evitar ORA-02291 en caso de llamadas con IDs sintéticos o no existentes
        Long validRealizadoPor = null;
        if (realizadoPor != null && realizadoPor > 0) {
            try {
                Integer countUsr = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM USUARIOS WHERE ID_USUARIO = :usr",
                        new MapSqlParameterSource("usr", realizadoPor),
                        Integer.class
                );
                if (countUsr != null && countUsr > 0) {
                    validRealizadoPor = realizadoPor;
                } else {
                    log.warn("[MembershipHistory] Usuario realizadoPor={} no existe en USUARIOS. Se registra como NULL para preservar integridad FK.", realizadoPor);
                }
            } catch (Exception ex) {
                log.warn("[MembershipHistory] Error verificando existencia de usuario realizadoPor={}: {}", realizadoPor, ex.getMessage());
            }
        }

        // Validar FK opcional de plan anterior
        Long validPlanAnterior = null;
        if (idPlanAnterior != null && idPlanAnterior > 0) {
            try {
                Integer countPlan = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM PLANES WHERE ID_PLAN = :plan",
                        new MapSqlParameterSource("plan", idPlanAnterior),
                        Integer.class
                );
                if (countPlan != null && countPlan > 0) {
                    validPlanAnterior = idPlanAnterior;
                } else {
                    log.warn("[MembershipHistory] Plan anterior={} no existe en PLANES. Se registra como NULL.", idPlanAnterior);
                }
            } catch (Exception ex) {
                log.warn("[MembershipHistory] Error verificando plan anterior={}: {}", idPlanAnterior, ex.getMessage());
            }
        }

        String sql = """
            INSERT INTO MEMBRESIAS_HISTORIAL (
                ID_MEMBRESIA, ID_PLAN_ANTERIOR, ID_PLAN_NUEVO, TIPO_CAMBIO, OBSERVACIONES, FECHA_CAMBIO, REALIZADO_POR
            ) VALUES (
                :idMembresia, :idPlanAnterior, :idPlanNuevo, :tipoCambio, :observaciones, SYSTIMESTAMP, :realizadoPor
            )
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("idMembresia", idMembresia)
                .addValue("idPlanAnterior", validPlanAnterior)
                .addValue("idPlanNuevo", idPlanNuevo)
                .addValue("tipoCambio", tipoNorm)
                .addValue("observaciones", observaciones != null ? observaciones.trim() : null)
                .addValue("realizadoPor", validRealizadoPor);

        jdbcTemplate.update(sql, params);
        log.info("[MembershipHistory] Registrado evento {} para membresía {} (Plan {} -> {})",
                tipoNorm, idMembresia, validPlanAnterior, idPlanNuevo);
    }

    @Override
    public List<Map<String, Object>> getHistoryForMembership(Long idMembresia) {
        if (idMembresia == null) {
            return Collections.emptyList();
        }

        String sql = """
            SELECT h.ID_HISTORIAL AS "idHistorial",
                   h.ID_MEMBRESIA AS "idMembresia",
                   h.ID_PLAN_ANTERIOR AS "idPlanAnterior",
                   pa.NOMBRE AS "planAnteriorNombre",
                   pa.CODIGO AS "planAnteriorCodigo",
                   h.ID_PLAN_NUEVO AS "idPlanNuevo",
                   pn.NOMBRE AS "planNuevoNombre",
                   pn.CODIGO AS "planNuevoCodigo",
                   h.TIPO_CAMBIO AS "tipoCambio",
                   h.OBSERVACIONES AS "observaciones",
                   h.FECHA_CAMBIO AS "fechaCambio",
                   h.REALIZADO_POR AS "realizadoPor",
                   u.NOMBRE_USUARIO AS "realizadoPorUsername"
            FROM MEMBRESIAS_HISTORIAL h
            JOIN PLANES pn ON h.ID_PLAN_NUEVO = pn.ID_PLAN
            LEFT JOIN PLANES pa ON h.ID_PLAN_ANTERIOR = pa.ID_PLAN
            LEFT JOIN USUARIOS u ON h.REALIZADO_POR = u.ID_USUARIO
            WHERE h.ID_MEMBRESIA = :idMembresia
            ORDER BY h.FECHA_CAMBIO DESC, h.ID_HISTORIAL DESC
            """;

        return jdbcTemplate.queryForList(sql, new MapSqlParameterSource("idMembresia", idMembresia));
    }

    @Override
    public List<Map<String, Object>> getHistoryForOrganization(Long idOrganizacion) {
        if (idOrganizacion == null) {
            return Collections.emptyList();
        }

        String sql = """
            SELECT h.ID_HISTORIAL AS "idHistorial",
                   h.ID_MEMBRESIA AS "idMembresia",
                   h.ID_PLAN_ANTERIOR AS "idPlanAnterior",
                   pa.NOMBRE AS "planAnteriorNombre",
                   pa.CODIGO AS "planAnteriorCodigo",
                   h.ID_PLAN_NUEVO AS "idPlanNuevo",
                   pn.NOMBRE AS "planNuevoNombre",
                   pn.CODIGO AS "planNuevoCodigo",
                   h.TIPO_CAMBIO AS "tipoCambio",
                   h.OBSERVACIONES AS "observaciones",
                   h.FECHA_CAMBIO AS "fechaCambio",
                   h.REALIZADO_POR AS "realizadoPor",
                   u.NOMBRE_USUARIO AS "realizadoPorUsername"
            FROM MEMBRESIAS_HISTORIAL h
            JOIN MEMBRESIAS m ON h.ID_MEMBRESIA = m.ID_MEMBRESIA
            JOIN PLANES pn ON h.ID_PLAN_NUEVO = pn.ID_PLAN
            LEFT JOIN PLANES pa ON h.ID_PLAN_ANTERIOR = pa.ID_PLAN
            LEFT JOIN USUARIOS u ON h.REALIZADO_POR = u.ID_USUARIO
            WHERE m.ID_ORGANIZACION = :idOrganizacion
            ORDER BY h.FECHA_CAMBIO DESC, h.ID_HISTORIAL DESC
            """;

        return jdbcTemplate.queryForList(sql, new MapSqlParameterSource("idOrganizacion", idOrganizacion));
    }
}
