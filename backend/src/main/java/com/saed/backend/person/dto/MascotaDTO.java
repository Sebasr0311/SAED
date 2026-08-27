package com.saed.backend.person.dto;

import java.time.LocalDate;

public record MascotaDTO(
    Long id,
    Long unidadId,
    Long responsableId,
    String nombre,
    String especie,
    String raza,
    String color,
    String genero,
    LocalDate fechaNacimientoAprox,
    Double pesoKg,
    String numeroMicrochip,
    String esRazaManejoEspecial,
    String polizaResponsabilidadUrl,
    String carnetVacunacionUrl,
    String fotoUrl,
    String estado
) {}
