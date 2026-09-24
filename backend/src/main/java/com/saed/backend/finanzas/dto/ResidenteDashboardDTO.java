package com.saed.backend.finanzas.dto;

import java.util.List;

public record ResidenteDashboardDTO(
    Long idResidente,
    Long idUnidad,
    String identificadorUnidad,
    List<CuotaDTO> cuotas
) {
    public ResidenteDashboardDTO(Long idResidente, List<CuotaDTO> cuotas) {
        this(idResidente, null, null, cuotas);
    }

    public Long getIdUnidad() { return idUnidad; }
    public String getIdentificadorUnidad() { return identificadorUnidad; }
    public Long getIdResidente() { return idResidente; }
    public List<CuotaDTO> getCuotas() { return cuotas; }
}

