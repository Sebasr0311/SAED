package com.saed.backend.mantenimiento.repository;

import com.saed.backend.mantenimiento.dto.MantenimientoCreateDTO;
import com.saed.backend.mantenimiento.dto.MantenimientoDTO;
import com.saed.backend.mantenimiento.dto.MantenimientoUpdateDTO;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface MantenimientoRepository {

    Optional<MantenimientoDTO> findByIdAndPropiedad(Long id, Long idPropiedad);

    List<MantenimientoDTO> findAllByPropiedad(Long idPropiedad, String estado, String tipo, String prioridad, Long idActivo, String search);

    Long create(MantenimientoCreateDTO dto, Long idPropiedad, Long solicitadoPor);

    void update(Long id, Long idPropiedad, MantenimientoUpdateDTO dto);

    void updateEstado(Long id, Long idPropiedad, String nuevoEstado, BigDecimal costoReal, Instant fechaEjecucion, String notasCierre, String informeTecnicoUrl, String evidenciaDespuesUrl);

    void reprogramar(Long id, Long idPropiedad, LocalDate nuevaFechaProgramada, String notas);

    boolean lockMantenimientoForUpdate(Long id, Long idPropiedad);

    boolean lockActivoForUpdate(Long idActivo, Long idPropiedad);

    void updateEstadoActivo(Long idActivo, Long idPropiedad, String nuevoEstado);

    Optional<String> getEstadoActivo(Long idActivo, Long idPropiedad);

    boolean hasActiveMaintenanceOnActivo(Long idActivo, Long idPropiedad, Long excludeIdMantenimiento);

    Long createBloqueoZona(Long idZona, Long idPropiedad, Long idMantenimiento, String motivo, Instant fechaInicio, Instant fechaFin, Long bloqueadoPor);

    void updateBloqueoFechas(Long idMantenimiento, Long idPropiedad, Instant fechaInicio, Instant fechaFin);

    void deleteBloqueoByMantenimiento(Long idMantenimiento, Long idPropiedad);

    Optional<Long> findBloqueoIdByMantenimiento(Long idMantenimiento, Long idPropiedad);

    boolean hasOverlappingBloqueo(Long idZona, Instant inicio, Instant fin, Long excludeIdMantenimiento);

    Map<String, Object> getKpis(Long idPropiedad);
}
