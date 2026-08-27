package com.saed.backend.person.dto;

import java.time.ZonedDateTime;

public record TutorDTO(
    Long id,
    Long personaMenorId,
    Long personaTutorId,
    String parentesco,
    String documentoSoporteUrl,
    String estado,
    ZonedDateTime fechaRegistro
) {}
