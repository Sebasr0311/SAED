package com.saed.backend.person.repository;

import com.saed.backend.person.dto.UnitOwnerDTO;
import com.saed.backend.person.dto.UnitOwnerRequestDTO;
import com.saed.backend.person.dto.UnitResidentDTO;
import com.saed.backend.person.dto.UnitResidentRequestDTO;
import java.util.List;

public interface UnitInhabitantRepository {
    List<UnitOwnerDTO> findOwnersByUnitId(Long unitId);
    Long insertOwner(Long unitId, UnitOwnerRequestDTO request);
    
    List<UnitResidentDTO> findResidentsByUnitId(Long unitId);
    Long insertResident(Long unitId, UnitResidentRequestDTO request);
}
