package com.saed.backend.automatizaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReglaDTO {
    private Long idRegla;
    private Long idOrganizacion;
    private Long idPropiedad;
    private String nombrePropiedad;
    private Long idEvento;
    private String codigoEvento;
    private String nombreEvento;
    private String moduloOrigen;
    private String variablesPayload;
    private String nombre;
    private String descripcion;
    private String condicionJson;
    private String estado;
    private OffsetDateTime fechaCreacion;
    private Long creadoPor;
    private String creadorNombre;
    private List<AccionDTO> acciones;
}
