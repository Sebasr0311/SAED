package com.saed.backend.finanzas.dto;
import java.math.BigDecimal;
import java.time.LocalDate;
public record CuotaDTO(Long id, Long idUnidad, String numeroApartamento, String nombreResidente, Long idContrato, String concepto, String periodo, BigDecimal valorBase, BigDecimal valorTotal, BigDecimal saldoPendiente, LocalDate fechaLimite, String estado) {}
