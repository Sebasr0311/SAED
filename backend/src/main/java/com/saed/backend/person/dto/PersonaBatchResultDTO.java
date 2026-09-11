package com.saed.backend.person.dto;

import java.util.List;

public record PersonaBatchResultDTO(
        int total,
        int procesados,
        int fallidos,
        List<PersonaBatchFilaErrorDTO> errores
) {}
