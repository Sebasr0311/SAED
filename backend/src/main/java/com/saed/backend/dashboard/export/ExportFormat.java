package com.saed.backend.dashboard.export;

import java.util.Locale;

/**
 * Formatos de exportación soportados para reportes operativos y financieros en SAED 2.0.
 */
public enum ExportFormat {
    JSON,
    PDF,
    CSV,
    XLSX;

    /**
     * Resuelve el formato de exportación a partir de un valor textual insensible a mayúsculas.
     * Un valor nulo o en blanco significa JSON; un valor no soportado se rechaza (F11-01).
     *
     * @param value Nombre del formato (ej. "json", "pdf", "csv", "xlsx").
     * @return ExportFormat correspondiente, o JSON si no se indicó formato.
     * @throws IllegalArgumentException si el formato no está soportado (HTTP 400).
     */
    public static ExportFormat fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return JSON;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        for (ExportFormat format : values()) {
            if (format.name().equals(normalized)) {
                return format;
            }
        }
        throw new IllegalArgumentException("Formato de exportación no soportado. Valores permitidos: JSON, PDF, CSV, XLSX.");
    }
}
