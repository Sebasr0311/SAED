package com.saed.backend.sanciones.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.LocalDate;

public class ResolucionRequestDTO {
    @NotBlank(message = "La decisión es obligatoria (APLICADA, ABSUELTA, ANULADA)")
    private String decision;

    @NotBlank(message = "La resolución final motivada es obligatoria")
    private String resolucionFinal;

    private BigDecimal montoMulta;

    /** First day of a common-area suspension; defaults to the resolution date. */
    private LocalDate restriccionDesde;

    /** Last day (inclusive) of a common-area suspension; required for SUSPENSION_ZONAS_COMUNES. */
    private LocalDate restriccionHasta;

    public ResolucionRequestDTO() {}

    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }

    public String getResolucionFinal() { return resolucionFinal; }
    public void setResolucionFinal(String resolucionFinal) { this.resolucionFinal = resolucionFinal; }

    public BigDecimal getMontoMulta() { return montoMulta; }
    public void setMontoMulta(BigDecimal montoMulta) { this.montoMulta = montoMulta; }

    public LocalDate getRestriccionDesde() { return restriccionDesde; }
    public void setRestriccionDesde(LocalDate restriccionDesde) { this.restriccionDesde = restriccionDesde; }

    public LocalDate getRestriccionHasta() { return restriccionHasta; }
    public void setRestriccionHasta(LocalDate restriccionHasta) { this.restriccionHasta = restriccionHasta; }
}
