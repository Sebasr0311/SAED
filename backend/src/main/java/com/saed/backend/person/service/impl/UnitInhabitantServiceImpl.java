package com.saed.backend.person.service.impl;

import com.saed.backend.person.dto.UnitOwnerDTO;
import com.saed.backend.person.dto.UnitOwnerRequestDTO;
import com.saed.backend.person.dto.UnitResidentDTO;
import com.saed.backend.person.dto.UnitResidentRequestDTO;
import com.saed.backend.person.repository.UnitInhabitantRepository;
import com.saed.backend.person.service.UnitInhabitantService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UnitInhabitantServiceImpl implements UnitInhabitantService {
    
    private final UnitInhabitantRepository unitInhabitantRepository;

    public UnitInhabitantServiceImpl(UnitInhabitantRepository unitInhabitantRepository) {
        this.unitInhabitantRepository = unitInhabitantRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UnitOwnerDTO> getOwnersByUnitId(Long unitId) {
        return unitInhabitantRepository.findOwnersByUnitId(unitId);
    }

    @Override
    @Transactional
    public Long addOwner(Long unitId, UnitOwnerRequestDTO request) {
        return unitInhabitantRepository.insertOwner(unitId, request);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UnitResidentDTO> getResidentsByUnitId(Long unitId) {
        return unitInhabitantRepository.findResidentsByUnitId(unitId);
    }

    @Override
    @Transactional
    public Long addResident(Long unitId, UnitResidentRequestDTO request) {
        return unitInhabitantRepository.insertResident(unitId, request);
    }
}
