package com.saed.backend.automatizaciones.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReglaRequestDTO {

    @NotNull(message = "El evento disparador es obligatorio")
    private Long idEvento;

    private Long idPropiedad;

    @NotBlank(message = "El nombre de la regla es obligatorio")
    @Size(max = 120, message = "El nombre no puede exceder los 120 caracteres")
    private String nombre;

    @Size(max = 300, message = "La descripción no puede exceder los 300 caracteres")
    private String descripcion;

    private String condicionJson;

    @Pattern(regexp = "ACTIVA|INACTIVA", message = "El estado debe ser ACTIVA o INACTIVA")
    private String estado;

    @Valid
    private List<AccionRequestDTO> acciones;
}
