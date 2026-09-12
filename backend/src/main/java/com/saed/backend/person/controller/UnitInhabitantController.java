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
    private final com.saed.backend.person.service.ConvivienteQuotaService convivienteQuotaService;

    public UnitInhabitantController(UnitInhabitantService unitInhabitantService,
                                    com.saed.backend.person.service.ConvivienteQuotaService convivienteQuotaService) {
        this.unitInhabitantService = unitInhabitantService;
        this.convivienteQuotaService = convivienteQuotaService;
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

    @GetMapping("/residents/quota")
    @PreAuthorize("hasAuthority('SCOPE_SUPERADMIN') or hasAuthority('SCOPE_ADMIN_ORGANIZACION') or hasAuthority('SCOPE_ADMIN_PROPIEDAD') or hasAuthority('SCOPE_RESIDENTE')")
    public ResponseEntity<com.saed.backend.person.dto.ConvivienteQuotaDTO> getQuota(@PathVariable Long unitId) {
        return ResponseEntity.ok(convivienteQuotaService.getQuota(unitId));
    }

    @PostMapping("/residents")
    @PreAuthorize("hasAuthority('SCOPE_SUPERADMIN') or hasAuthority('SCOPE_ADMIN_ORGANIZACION') or hasAuthority('SCOPE_ADMIN_PROPIEDAD') or hasAuthority('SCOPE_RESIDENTE')")
    public ResponseEntity<Long> addResident(
            @PathVariable Long unitId, 
            @Valid @RequestBody UnitResidentRequestDTO request) {
        Long residentId = unitInhabitantService.addResident(unitId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(residentId);
    }

    @PatchMapping("/residents/{residentId}/status")
    @PreAuthorize("hasAuthority('SCOPE_SUPERADMIN') or hasAuthority('SCOPE_ADMIN_ORGANIZACION') or hasAuthority('SCOPE_ADMIN_PROPIEDAD') or hasAuthority('SCOPE_RESIDENTE')")
    public ResponseEntity<Void> updateResidentStatus(
            @PathVariable Long unitId,
            @PathVariable Long residentId,
            @Valid @RequestBody com.saed.backend.person.dto.UpdateResidentStatusRequestDTO request) {
        unitInhabitantService.updateResidentStatus(unitId, residentId, request.estado());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/residents/{residentId}")
    @PreAuthorize("hasAuthority('SCOPE_SUPERADMIN') or hasAuthority('SCOPE_ADMIN_ORGANIZACION') or hasAuthority('SCOPE_ADMIN_PROPIEDAD') or hasAuthority('SCOPE_RESIDENTE')")
    public ResponseEntity<Void> unlinkResident(
            @PathVariable Long unitId,
            @PathVariable Long residentId) {
        unitInhabitantService.unlinkResident(unitId, residentId);
        return ResponseEntity.noContent().build();
    }
}

