package com.saed.backend.dashboard.registry;

import java.util.Arrays;
import java.util.Optional;

/**
 * ReportDefinitionKey — Catálogo allowlistado de claves de reporte seguras en SAED 2.0.
 *
 * REGLA DE SEGURIDAD CRÍTICA (F11-04 Block D):
 * NUNCA se ejecuta SQL arbitrario almacenado en BD.
 * Toda clave debe pertenecer a este enum cerrado y mapear exclusivamente a métodos certificados en Java.
 */
public enum ReportDefinitionKey {

    CARTERA_MOROSA("CARTERA_MOROSA", "Cartera Morosa", "FINANZAS", "ADMIN_PROPIEDAD"),
    EJECUCION_CUOTAS("EJECUCION_CUOTAS", "Ejecución de Cuotas", "FINANZAS", "ADMIN_PROPIEDAD"),
    PAGOS_RECIENTES("PAGOS_RECIENTES", "Pagos Recientes", "FINANZAS", "ADMIN_PROPIEDAD"),
    EJECUCION_PRESUPUESTAL("EJECUCION_PRESUPUESTAL", "Ejecución Presupuestal Global", "FINANZAS", "ADMIN_PROPIEDAD");

    private final String key;
    private final String defaultName;
    private final String modulo;
    private final String rolMinimo;

    ReportDefinitionKey(String key, String defaultName, String modulo, String rolMinimo) {
        this.key = key;
        this.defaultName = defaultName;
        this.modulo = modulo;
        this.rolMinimo = rolMinimo;
    }

    public String getKey() {
        return key;
    }

    public String getDefaultName() {
        return defaultName;
    }

    public String getModulo() {
        return modulo;
    }

    public String getRolMinimo() {
        return rolMinimo;
    }

    public static boolean isValid(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return false;
        }
        String normalized = rawKey.trim().toUpperCase();
        return Arrays.stream(values()).anyMatch(k -> k.name().equals(normalized));
    }

    public static Optional<ReportDefinitionKey> fromKey(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return Optional.empty();
        }
        String normalized = rawKey.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(k -> k.name().equals(normalized))
                .findFirst();
    }
}
