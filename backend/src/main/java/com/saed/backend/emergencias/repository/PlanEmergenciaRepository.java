package com.saed.backend.emergencias.repository;

import com.saed.backend.emergencias.dto.PlanEmergenciaDTO;
import com.saed.backend.emergencias.dto.PlanEmergenciaRequestDTO;

import java.util.List;
import java.util.Optional;

public interface PlanEmergenciaRepository {
    List<PlanEmergenciaDTO> findAllByPropiedad(Long idPropiedad);
    Optional<PlanEmergenciaDTO> findByIdAndPropiedad(Long idPlanEmergencia, Long idPropiedad);
    Long insert(Long idPropiedad, PlanEmergenciaRequestDTO dto);
    void update(Long idPlanEmergencia, Long idPropiedad, PlanEmergenciaRequestDTO dto);
    void delete(Long idPlanEmergencia, Long idPropiedad);
    int countTotalByPropiedad(Long idPropiedad);
    int countActivosByPropiedad(Long idPropiedad);
}
