package com.saed.backend.dashboard.dto;

public record PropertyOperationalTrendDTO(
    String periodo,
    long totalVisitas,
    long totalPaquetes,
    long pqrsRadicadas,
    Double pqrsResueltasEnSlaPct
) {}
