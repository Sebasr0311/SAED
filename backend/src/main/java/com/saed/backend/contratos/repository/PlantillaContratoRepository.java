package com.saed.backend.contratos.repository;

import com.saed.backend.contratos.dto.PlantillaContratoDTO;
import com.saed.backend.contratos.dto.PlantillaContratoRequestDTO;
import java.util.List;
import java.util.Optional;

public interface PlantillaContratoRepository {

    List<PlantillaContratoDTO> findByOrganizacionId(Long orgId, String estado);

    List<PlantillaContratoDTO> findActivasByOrganizacionId(Long orgId);

    Optional<PlantillaContratoDTO> findById(Long id);

    Optional<PlantillaContratoDTO> findByCodigoAndVersion(Long orgId, String codigo, Integer version);

    Long create(PlantillaContratoRequestDTO dto, Long orgId, Long userId);

    void update(Long id, PlantillaContratoRequestDTO dto);

    void updateStatus(Long id, String estado);

    Integer getMaxVersion(Long orgId, String codigo);
}
