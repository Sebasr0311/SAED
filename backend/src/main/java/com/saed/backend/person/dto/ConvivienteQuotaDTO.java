package com.saed.backend.person.dto;

public record ConvivienteQuotaDTO(
        Long unitId,
        Long propertyId,
        int limiteConfigurado,
        int convivientesActivos,
        int cuposDisponibles,
        boolean limiteAlcanzado
) {
}
