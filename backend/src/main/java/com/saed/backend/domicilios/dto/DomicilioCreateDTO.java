package com.saed.backend.domicilios.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DomicilioCreateDTO(
    @NotNull(message = "El idUnidad es obligatorio")
    Long idUnidad,

    @NotBlank(message = "La empresa es obligatoria")
    @Size(max = 100, message = "La empresa no puede superar 100 caracteres")
    String empresa,

    @NotBlank(message = "El nombre del domiciliario es obligatorio")
    @Size(max = 150, message = "El nombre del domiciliario no puede superar 150 caracteres")
    String nombreDomiciliario,

    @Size(max = 30, message = "El documento no puede superar 30 caracteres")
    String documentoDomiciliario,

    @Size(max = 30, message = "El teléfono no puede superar 30 caracteres")
    String telefonoDomiciliario,

    @Size(max = 30, message = "El tipo de domicilio no puede superar 30 caracteres")
    String tipoDomicilio,

    @Size(max = 80, message = "El número de guía no puede superar 80 caracteres")
    String numeroGuia,

    @Size(max = 20, message = "El medio de transporte no puede superar 20 caracteres")
    String medioTransporte,

    @Size(max = 15, message = "La placa no puede superar 15 caracteres")
    String placaVehiculo,

    @Size(max = 400, message = "Las observaciones no pueden superar 400 caracteres")
    String observaciones,

    Long idPorteria,
    Long idPersonaDestinatario
) {}
