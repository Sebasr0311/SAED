package com.saed.backend.platform.service;

import com.saed.backend.platform.dto.OnboardingRegistroRequestDTO;

import java.util.List;
import java.util.Map;

public interface OnboardingService {

    List<Map<String, Object>> listarPlanesPublicos();

    Map<String, Object> registrar(OnboardingRegistroRequestDTO request);

    Map<String, Object> consultarEstadoPago(String referencia);

    /**
     * Provisions the organization of a paid intention after a verified gateway payment. Returns
     * false when the payment could not be provisioned; the intention is then left in
     * PAGO_REQUIERE_REVISION for platform recovery (F3-08).
     */
    boolean materializarOrganizacion(String referencia, Long expectedCentavos, String idTransaccionPasarela);

    /**
     * Platform administrator approval (DEC-06): provisions a PENDIENTE or PAGO_REQUIERE_REVISION
     * intention without marking any gateway transaction as approved.
     */
    boolean aprobarManualmente(String referencia, String motivo);
}
