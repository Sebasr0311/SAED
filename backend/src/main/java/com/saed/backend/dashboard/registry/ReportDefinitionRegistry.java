package com.saed.backend.dashboard.registry;

import org.springframework.stereotype.Component;

import java.util.*;

/**
 * ReportDefinitionRegistry — Registro central allowlistado de definiciones de reportes en SAED 2.0.
 *
 * REGLA DE SEGURIDAD CRÍTICA (F11-04 Block D):
 * NUNCA se ejecuta SQL arbitrario de base de datos.
 * Toda ejecución se canaliza únicamente a través de las definiciones Java registradas aquí.
 */
@Component
public class ReportDefinitionRegistry {

    private final Map<ReportDefinitionKey, ReportDefinition> registry = new EnumMap<>(ReportDefinitionKey.class);

    public ReportDefinitionRegistry(List<ReportDefinition> definitions) {
        for (ReportDefinition def : definitions) {
            registry.put(def.getKey(), def);
        }
    }

    public boolean isAllowlisted(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return false;
        }
        return ReportDefinitionKey.isValid(rawKey);
    }

    public ReportDefinition getDefinition(String rawKey) {
        if (!isAllowlisted(rawKey)) {
            throw new InvalidReportKeyException(rawKey);
        }
        ReportDefinitionKey key = ReportDefinitionKey.fromKey(rawKey)
                .orElseThrow(() -> new InvalidReportKeyException(rawKey));
        return getDefinition(key);
    }

    public ReportDefinition getDefinition(ReportDefinitionKey key) {
        ReportDefinition def = registry.get(key);
        if (def == null) {
            throw new InvalidReportKeyException(key != null ? key.name() : "NULL");
        }
        return def;
    }

    public List<ReportDefinitionKey> getAvailableKeys() {
        return List.copyOf(registry.keySet());
    }
}
