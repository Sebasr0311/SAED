package com.saed.backend.trabajadores.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record TrabajadorCreateDTO(
        @NotNull(message = "El proveedor es obligatorio")
        Long idProveedor,

        Long idPersona,

        Long tipoDocumentoId,
        String numeroDocumento,
        String primerNombre,
        String segundoNombre,
        String primerApellido,
        String segundoApellido,
        String email,
        String telefono,

        @NotBlank(message = "El oficio o especialidad es obligatorio")
        String oficioEspecialidad,

        @NotBlank(message = "La aseguradora ARL es obligatoria")
        String arlAseguradora,

        LocalDate arlFechaAfiliacion,

        @NotNull(message = "La fecha de vencimiento de la ARL es obligatoria")
        LocalDate arlFechaVencimiento,

        LocalDate certAlturasVencimiento,
        String empresaIndependiente,
        String estado
) {}
