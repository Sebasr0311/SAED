package com.saed.backend.activos.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ActivoDTO(
    Long idActivo,
    Long idPropiedad,
    String codigoActivo,
    String nombre,
    String categoria,
    LocalDate fechaAdquisicion,
    BigDecimal valorAdquisicion,
    String estado
) {}
