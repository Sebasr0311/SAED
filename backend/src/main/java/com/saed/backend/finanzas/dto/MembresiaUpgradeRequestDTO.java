package com.saed.backend.finanzas.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * DTO para solicitar el upgrade de la membresía actual de la organización hacia un plan superior.
 * REGLA FUNDAMENTAL DE SEGURIDAD:
 * El frontend NUNCA es fuente de verdad para precios, montos o duraciones.
 * Solo puede especificar el idPlanNuevo y el ciclo de facturación (MENSUAL o ANUAL).
 */
public class MembresiaUpgradeRequestDTO {

    @NotNull(message = "El identificador del plan destino (idPlanNuevo) es obligatorio")
    private Long idPlanNuevo;

    @Pattern(regexp = "(?i)MENSUAL|ANUAL", message = "El ciclo de facturación debe ser MENSUAL o ANUAL")
    private String cicloFacturacion;

    public MembresiaUpgradeRequestDTO() {}

    public MembresiaUpgradeRequestDTO(Long idPlanNuevo, String cicloFacturacion) {
        this.idPlanNuevo = idPlanNuevo;
        this.cicloFacturacion = cicloFacturacion;
    }

    public Long getIdPlanNuevo() {
        return idPlanNuevo;
    }

    public void setIdPlanNuevo(Long idPlanNuevo) {
        this.idPlanNuevo = idPlanNuevo;
    }

    public String getCicloFacturacion() {
        return cicloFacturacion;
    }

    public void setCicloFacturacion(String cicloFacturacion) {
        this.cicloFacturacion = cicloFacturacion;
    }
}
