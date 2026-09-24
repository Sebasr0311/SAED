package com.saed.backend.finanzas.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ContratoRequestDTO(
    @NotNull Long idApartamento,
    @NotNull Long idResidente,
    @NotNull LocalDate fechaInicio,
    LocalDate fechaFin,
    @NotNull String tipoContrato,
    @NotNull BigDecimal canonMensual,
    Long idPlantilla,
    Long idTutor,
    List<CoarrendatarioCreateDTO> coarrendatarios,
    Boolean sincronizarHabitabilidad
) {
    public ContratoRequestDTO(Long idApartamento, Long idResidente, LocalDate fechaInicio, LocalDate fechaFin, String tipoContrato, BigDecimal canonMensual) {
        this(idApartamento, idResidente, fechaInicio, fechaFin, tipoContrato, canonMensual, null, null, null, false);
    }

    public ContratoRequestDTO(Long idApartamento, Long idResidente, LocalDate fechaInicio, LocalDate fechaFin, String tipoContrato, BigDecimal canonMensual, Long idPlantilla) {
        this(idApartamento, idResidente, fechaInicio, fechaFin, tipoContrato, canonMensual, idPlantilla, null, null, false);
    }

    public ContratoRequestDTO(Long idApartamento, Long idResidente, LocalDate fechaInicio, LocalDate fechaFin, String tipoContrato, BigDecimal canonMensual, Long idPlantilla, Long idTutor) {
        this(idApartamento, idResidente, fechaInicio, fechaFin, tipoContrato, canonMensual, idPlantilla, idTutor, null, false);
    }
}
