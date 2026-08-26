package com.saed.backend.person.service;

import com.saed.backend.person.dto.UnitOwnerDTO;
import com.saed.backend.person.dto.UnitOwnerRequestDTO;
import com.saed.backend.person.dto.UnitResidentDTO;
import com.saed.backend.person.dto.UnitResidentRequestDTO;
import java.util.List;

public interface UnitInhabitantService {
    List<UnitOwnerDTO> getOwnersByUnitId(Long unitId);
    Long addOwner(Long unitId, UnitOwnerRequestDTO request);
    
    List<UnitResidentDTO> getResidentsByUnitId(Long unitId);
    Long addResident(Long unitId, UnitResidentRequestDTO request);
}
