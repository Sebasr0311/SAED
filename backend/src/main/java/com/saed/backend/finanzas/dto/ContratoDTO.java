package com.saed.backend.finanzas.dto;
import java.math.BigDecimal;
import java.time.LocalDate;
public record ContratoDTO(Long id, Long idUnidad, String numeroApartamento, Long idArrendatario, String nombreArrendatario, String numeroContrato, BigDecimal canonMensual, Integer diaCortePago, LocalDate fechaInicio, LocalDate fechaFin, LocalDate fechaTerminacion, String estado, String tipoContrato) {}
