package com.saed.backend.reservas.service.impl;

import com.saed.backend.reservas.service.CommonAreaRestrictionChecker;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.LocalDate;

/**
 * Default F10-07 restriction check until the sanctions module exposes {@code hasActiveCommonAreaRestriction}:
 * an APLICADA sanction of type SUSPENSION_ZONAS_COMUNES resolved on or before the date restricts the unit.
 * SANCIONES has no end date yet, so an applied suspension stays in force until it is annulled.
 */
@Component
public class JdbcCommonAreaRestrictionChecker implements CommonAreaRestrictionChecker {

    private final JdbcTemplate jdbcTemplate;

    public JdbcCommonAreaRestrictionChecker(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean hasActiveCommonAreaRestriction(Long unitId, LocalDate date) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM SANCIONES WHERE ID_UNIDAD = ? AND TIPO_SANCION_PROPUESTA = 'SUSPENSION_ZONAS_COMUNES' "
                + "AND ESTADO = 'APLICADA' AND (FECHA_RESOLUCION IS NULL OR TRUNC(FECHA_RESOLUCION) <= ?)",
            Integer.class, unitId, Date.valueOf(date));
        return count != null && count > 0;
    }
}
