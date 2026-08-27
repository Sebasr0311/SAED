package com.saed.backend.person.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record MascotaRequestDTO(
    @NotNull(message = "El ID de la unidad es requerido")
    Long unidadId,
    
    @NotNull(message = "El ID del responsable es requerido")
    Long responsableId,
    
    @NotBlank(message = "El nombre es requerido")
    String nombre,
    
    @NotBlank(message = "La especie es requerida")
    String especie,
    
    String raza,
    String color,
    
    @NotBlank(message = "El genero es requerido (M/F)")
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
