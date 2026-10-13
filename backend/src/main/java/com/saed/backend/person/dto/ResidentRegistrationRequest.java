package com.saed.backend.person.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * F05-01: one request that registers a resident completely or not at all: the person, an optional guardian,
 * the link to the unit and an optional access account.
 *
 * @param unitId       unit of the caller's property (or organization) the person is linked to
 * @param tipoRelacion PROPIETARIO_RESIDENTE, PROPIETARIO_NO_RESIDENTE, ARRENDATARIO, CONVIVIENTE, FAMILIAR or OTRO
 */
public record ResidentRegistrationRequest(
        @NotNull(message = "La unidad es obligatoria") Long unitId,
        @NotBlank(message = "El tipo de relación es obligatorio") String tipoRelacion,
        @NotNull @Valid PersonaRequestDTO person,
        @Valid Guardian guardian,
        @Valid Account account,
        @Valid Contract contract
) {

    /** Guardian of a minor: either an existing person of the organization or a new one, plus the relationship. */
    public record Guardian(
            Long existingPersonId,
            @Valid PersonaRequestDTO person,
            @NotBlank(message = "El parentesco del tutor es obligatorio") String parentesco
    ) {
    }

    /** Optional access account; the e-mail of the person is used. */
    public record Account(
            @NotBlank(message = "El nombre de usuario es obligatorio") String username,
            String password,
            Boolean enviarCorreoActivacion
    ) {
    }

    /** Lease data, required for ARRENDATARIO. */
    public record Contract(
            @NotNull BigDecimal canonMensual,
            LocalDate fechaInicio,
            LocalDate fechaFin,
            String tipoContrato,
            Long idPlantilla
    ) {
    }
}
