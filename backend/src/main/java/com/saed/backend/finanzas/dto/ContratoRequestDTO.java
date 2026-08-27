package com.saed.backend.finanzas.dto;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
public record ContratoRequestDTO(@NotNull Long idApartamento, @NotNull Long idResidente, @NotNull LocalDate fechaInicio, LocalDate fechaFin, @NotNull String tipoContrato, @NotNull BigDecimal canonMensual) {}
