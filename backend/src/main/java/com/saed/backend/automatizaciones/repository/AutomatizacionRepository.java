package com.saed.backend.automatizaciones.repository;

import com.saed.backend.automatizaciones.dto.AccionDTO;
import com.saed.backend.automatizaciones.dto.AutomatizacionesSummaryDTO;
import com.saed.backend.automatizaciones.dto.EjecucionDTO;
import com.saed.backend.automatizaciones.dto.EventoDTO;
import com.saed.backend.automatizaciones.dto.ReglaDTO;

import java.util.List;
import java.util.Optional;

public interface AutomatizacionRepository {

    List<EventoDTO> findAllEventos();

    Optional<EventoDTO> findEventoById(Long id);

    Optional<EventoDTO> findEventoByCodigo(String codigo);

    List<ReglaDTO> findReglas(Long orgId, Long propId, String estado, Long eventoId);

    Optional<ReglaDTO> findReglaById(Long id, Long orgId);

    Long createRegla(Long orgId, Long propId, Long eventoId, String nombre, String descripcion, String condicionJson, String estado, Long creadoPor);

    void updateRegla(Long idRegla, Long propId, Long eventoId, String nombre, String descripcion, String condicionJson, String estado);

    void updateEstadoRegla(Long idRegla, String estado);

    void deleteRegla(Long idRegla);

    List<AccionDTO> findAccionesByReglaId(Long idRegla);

    Long createAccion(Long idRegla, String tipoAccion, String parametrosJson, Integer orden);

    void deleteAccionesByReglaId(Long idRegla);

    Long createEjecucion(Long idRegla, Long entidadOrigenId, String tipoEntidadOrigen, String resultado, String logDetalle, Integer tiempoMs);

    int countEjecucionesByReglaId(Long idRegla);

    List<EjecucionDTO> findEjecuciones(Long orgId, Long propId, Long reglaId, int limit);

    AutomatizacionesSummaryDTO getSummary(Long orgId, Long propId);
}
