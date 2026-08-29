package com.saed.backend.authorization.controller;

import com.saed.backend.authorization.dto.OrganizationDTO;
import com.saed.backend.authorization.dto.OrganizationRequestDTO;
import com.saed.backend.authorization.dto.StatusUpdateRequestDTO;
import com.saed.backend.authorization.service.OrganizationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import java.util.Map;

@Tag(name = "Organization", description = "API para la gestion de Organization")
@RestController
@RequestMapping("/api/v1/organizations")
@PreAuthorize("hasAuthority('SCOPE_SUPERADMIN')")
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @GetMapping
    public ResponseEntity<List<OrganizationDTO>> findAll() {
        return ResponseEntity.ok(organizationService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrganizationDTO> findById(@PathVariable Long id) {
        return ResponseEntity.ok(organizationService.findById(id));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody OrganizationRequestDTO request) {
        Long id = organizationService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("success", true, "id", id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long id, @Valid @RequestBody OrganizationRequestDTO request) {
        organizationService.update(id, request);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> updateStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequestDTO request) {
        organizationService.updateStatus(id, request.getEstado());
        return ResponseEntity.ok(Map.of("success", true));
    }
}

