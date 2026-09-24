package com.saed.backend.dashboard.dto;

public record DashboardPorteriaDTO(
    Long idPropiedad,
    long visitasActivas,
    long domiciliosActivos,
    long totalPases,
    long parqueaderosDisponibles,
    long parqueaderosVisitantesTotal,
    long paquetesEnCustodia
) {}
