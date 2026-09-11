package com.saed.backend.person.dto;

public record PersonaBatchFilaErrorDTO(
        int fila,
        String documento,
        String detalle
) {}
