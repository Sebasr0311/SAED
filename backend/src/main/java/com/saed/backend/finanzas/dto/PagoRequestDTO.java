package com.saed.backend.finanzas.dto;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
public record PagoRequestDTO(@NotNull Long idCuota, LocalDate fechaPago, @NotNull BigDecimal valorPagado, @NotNull String metodoPago, String referencia) {}
