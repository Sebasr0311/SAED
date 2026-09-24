package com.saed.backend.finanzas.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

public record PazYSalvoEstadoFinancieroDTO(
    Long idUnidad,
    String identificadorUnidad,
    BigDecimal saldoCartera,
    BigDecimal saldoMultasNoFacturadas,
    BigDecimal saldoTotalExigible,
    boolean pazYSalvo,
    List<String> motivosBloqueo
) {
    @JsonProperty("enMora")
    public boolean enMora() {
        return !pazYSalvo;
    }
}
