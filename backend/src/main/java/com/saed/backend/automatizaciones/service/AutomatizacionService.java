package com.saed.backend.automatizaciones.service;

import com.saed.backend.automatizaciones.dto.AutomatizacionesSummaryDTO;
import com.saed.backend.automatizaciones.dto.EjecucionDTO;
import com.saed.backend.automatizaciones.dto.EventoDTO;
import com.saed.backend.automatizaciones.dto.ReglaDTO;
import com.saed.backend.automatizaciones.dto.ReglaRequestDTO;
import com.saed.backend.automatizaciones.dto.SimulacionReglaRequestDTO;

import java.util.List;

public interface AutomatizacionService {

    List<EventoDTO> getEventos();

    List<ReglaDTO> getReglas(String estado, Long eventoId);

    ReglaDTO getReglaById(Long id);

    ReglaDTO createRegla(ReglaRequestDTO request);

    ReglaDTO updateRegla(Long id, ReglaRequestDTO request);

    void toggleEstado(Long id);

    void deleteRegla(Long id);

    EjecucionDTO simularRegla(Long idRegla, SimulacionReglaRequestDTO simulacion);

    List<EjecucionDTO> getHistorialEjecuciones(Long reglaId, int limit);

    AutomatizacionesSummaryDTO getSummary();
}
