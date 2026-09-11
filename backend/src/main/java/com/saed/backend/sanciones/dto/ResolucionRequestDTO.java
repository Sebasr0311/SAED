package com.saed.backend.sanciones.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

public class ResolucionRequestDTO {
    @NotBlank(message = "La decisión es obligatoria (APLICADA, ABSUELTA, ANULADA)")
    private String decision;

    @NotBlank(message = "La resolución final motivada es obligatoria")
    private String resolucionFinal;

    private BigDecimal montoMulta;

    public ResolucionRequestDTO() {}

    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }

    public String getResolucionFinal() { return resolucionFinal; }
    public void setResolucionFinal(String resolucionFinal) { this.resolucionFinal = resolucionFinal; }

    public BigDecimal getMontoMulta() { return montoMulta; }
    public void setMontoMulta(BigDecimal montoMulta) { this.montoMulta = montoMulta; }
}
