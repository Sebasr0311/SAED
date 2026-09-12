package com.saed.backend.automatizaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventoDTO {
    private Long idEvento;
    private String codigo;
    private String nombre;
    private String moduloOrigen;
    private String descripcion;
    private String variablesPayload;
}
