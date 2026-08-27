package com.saed.backend.finanzas.dto;
import java.util.List;
public record ResidenteDashboardDTO(Long idResidente, List<CuotaDTO> cuotas) {}
