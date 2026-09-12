package com.saed.backend.person.repository;

import com.saed.backend.person.dto.UnitOwnerDTO;
import com.saed.backend.person.dto.UnitOwnerRequestDTO;
import com.saed.backend.person.dto.UnitResidentDTO;
import com.saed.backend.person.dto.UnitResidentRequestDTO;
import java.util.List;
import java.util.Optional;

public interface UnitInhabitantRepository {
    List<UnitOwnerDTO> findOwnersByUnitId(Long unitId);
    Long insertOwner(Long unitId, UnitOwnerRequestDTO request);
    
    List<UnitResidentDTO> findResidentsByUnitId(Long unitId);
    Optional<UnitResidentDTO> findResidentByIdAndUnitId(Long unitId, Long residentId);
    Long insertResident(Long unitId, UnitResidentRequestDTO request);

    int updateResidentStatus(Long unitId, Long residentId, String status);
    int unlinkResident(Long unitId, Long residentId);
}
