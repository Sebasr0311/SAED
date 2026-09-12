package com.saed.backend.platform.service;

import com.saed.backend.platform.dto.OnboardingRegistroRequestDTO;

import java.util.List;
import java.util.Map;

public interface OnboardingService {

    List<Map<String, Object>> listarPlanesPublicos();

    Map<String, Object> registrar(OnboardingRegistroRequestDTO request);

    Map<String, Object> consultarEstadoPago(String referencia);

    boolean materializarOrganizacion(String referencia, Long expectedCentavos, String idTransaccionPasarela);

    int purgarRegistrosFalsos();
}
