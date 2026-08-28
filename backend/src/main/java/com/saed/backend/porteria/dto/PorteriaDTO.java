package com.saed.backend.porteria.dto;

public record PorteriaDTO(
    Long idPorteria,
    String nombre,
    String ubicacion,
    String telefonoContacto,
    String estado
) {}
