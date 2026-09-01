package com.saed.backend.person.controller;

import com.saed.backend.person.dto.UnitOwnerDTO;
import com.saed.backend.person.dto.UnitOwnerRequestDTO;
import com.saed.backend.person.dto.UnitResidentDTO;
import com.saed.backend.person.dto.UnitResidentRequestDTO;
import com.saed.backend.person.service.UnitInhabitantService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

@Tag(name = "UnitInhabitant", description = "API para la gestion de UnitInhabitant")
@RestController
@RequestMapping("/api/v1/units/{unitId}")
public class UnitInhabitantController {
    
    private final UnitInhabitantService unitInhabitantService;

    public UnitInhabitantController(UnitInhabitantService unitInhabitantService) {
        this.unitInhabitantService = unitInhabitantService;
    }

    @GetMapping("/owners")
    @PreAuthorize("hasAuthority('SCOPE_SUPERADMIN') or hasAuthority('SCOPE_ADMIN_ORGANIZACION') or hasAuthority('SCOPE_ADMIN_PROPIEDAD') or hasAuthority('SCOPE_PORTERO') or hasAuthority('SCOPE_RESIDENTE')")
    public ResponseEntity<List<UnitOwnerDTO>> getOwners(@PathVariable Long unitId) {
        return ResponseEntity.ok(unitInhabitantService.getOwnersByUnitId(unitId));
    }

    @PostMapping("/owners")
    @PreAuthorize("hasAuthority('SCOPE_SUPERADMIN') or hasAuthority('SCOPE_ADMIN_ORGANIZACION') or hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<Long> addOwner(
            @PathVariable Long unitId, 
            @Valid @RequestBody UnitOwnerRequestDTO request) {
        Long ownerId = unitInhabitantService.addOwner(unitId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ownerId);
    }

    @GetMapping("/residents")
    @PreAuthorize("hasAuthority('SCOPE_SUPERADMIN') or hasAuthority('SCOPE_ADMIN_ORGANIZACION') or hasAuthority('SCOPE_ADMIN_PROPIEDAD') or hasAuthority('SCOPE_PORTERO') or hasAuthority('SCOPE_RESIDENTE')")
    public ResponseEntity<List<UnitResidentDTO>> getResidents(@PathVariable Long unitId) {
        return ResponseEntity.ok(unitInhabitantService.getResidentsByUnitId(unitId));
    }

    @PostMapping("/residents")
    @PreAuthorize("hasAuthority('SCOPE_SUPERADMIN') or hasAuthority('SCOPE_ADMIN_ORGANIZACION') or hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<Long> addResident(
            @PathVariable Long unitId, 
            @Valid @RequestBody UnitResidentRequestDTO request) {
        Long residentId = unitInhabitantService.addResident(unitId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(residentId);
    }
}

