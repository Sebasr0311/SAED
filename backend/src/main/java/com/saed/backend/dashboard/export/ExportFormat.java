package com.saed.backend.dashboard.export;

/**
 * Formatos de exportación soportados para reportes operativos y financieros en SAED 2.0.
 */
public enum ExportFormat {
    JSON,
    PDF,
    CSV;

    /**
     * Resuelve el formato de exportación a partir de un valor textual insensible a mayúsculas.
     * Retorna JSON por defecto si el valor es nulo, en blanco o no reconocido.
     *
     * @param value Nombre del formato (ej. "json", "pdf", "csv").
     * @return ExportFormat correspondiente o JSON por defecto.
     */
    public static ExportFormat fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return JSON;
        }
        try {
            return ExportFormat.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return JSON;
        }
    }
}
