package com.saed.backend.platform.service;

import com.saed.backend.platform.dto.ActualizarCredencialesRequestDTO;
import com.saed.backend.platform.dto.EmailDispatchResponseDTO;
import com.saed.backend.platform.dto.OnboardingAdminSummaryDTO;
import com.saed.backend.platform.dto.ReenviarCredencialesRequestDTO;

import java.util.List;
import java.util.Map;

public interface PlatformOnboardingAdminService {
    List<OnboardingAdminSummaryDTO> listarSolicitudes();
    EmailDispatchResponseDTO reenviarCredenciales(ReenviarCredencialesRequestDTO request);
    Map<String, Object> actualizarCredenciales(ActualizarCredencialesRequestDTO request);
    Map<String, Object> aprobarManualmente(String referencia);
}
