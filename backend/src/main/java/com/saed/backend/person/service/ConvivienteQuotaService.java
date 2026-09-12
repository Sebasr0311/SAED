package com.saed.backend.person.service;

import com.saed.backend.person.dto.ConvivienteQuotaDTO;

public interface ConvivienteQuotaService {

    /**
     * Consulta el estado actual de la cuota de convivientes para una unidad.
     * Valida permisos y aislamiento multi-tenant / unidad.
     */
    ConvivienteQuotaDTO getQuota(Long unitId);

    /**
     * Valida y bloquea la fila de la unidad (pessimistic lock) asegurando que no se exceda
     * el límite de convivientes permitido ante concurrencia.
     * Lanza ConvivienteLimitExceededException si el límite fue alcanzado.
     */
    void validateAndLockQuota(Long unitId);

    /**
     * Valida la reactivación de un conviviente existente, bloqueando la unidad
     * y comprobando si al pasar a ACTIVO no se supera el límite.
     */
    void validateAndLockQuotaForReactivation(Long unitId, Long residentId);

    /**
     * Obtiene el límite parametrizado para la propiedad (desde PROPIEDAD_CONFIGURACION o fallback 4).
     */
    int getLimitForProperty(Long propertyId);
}
