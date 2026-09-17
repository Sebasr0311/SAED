package com.saed.backend.finanzas.dto;

import jakarta.validation.constraints.Pattern;

/**
 * DTO para solicitar la renovación de la membresía actual de la organización.
 * REGLA FUNDAMENTAL DE SEGURIDAD:
 * El frontend NUNCA es fuente de verdad para precios, montos o duraciones.
 * Solo puede especificar el ciclo de facturación (MENSUAL o ANUAL).
 */
public class MembresiaRenovacionRequestDTO {

    @Pattern(regexp = "(?i)MENSUAL|ANUAL", message = "El ciclo de facturación debe ser MENSUAL o ANUAL")
    private String cicloFacturacion;

    public MembresiaRenovacionRequestDTO() {}

    public MembresiaRenovacionRequestDTO(String cicloFacturacion) {
        this.cicloFacturacion = cicloFacturacion;
    }

    public String getCicloFacturacion() {
        return cicloFacturacion;
    }

    public void setCicloFacturacion(String cicloFacturacion) {
        this.cicloFacturacion = cicloFacturacion;
    }
}
