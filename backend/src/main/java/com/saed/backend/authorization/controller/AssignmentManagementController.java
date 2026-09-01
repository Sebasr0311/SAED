package com.saed.backend.authorization.controller;

import com.saed.backend.authorization.dto.AssignmentRequestDTO;
import com.saed.backend.authorization.dto.StatusUpdateRequestDTO;
import com.saed.backend.authorization.service.AssignmentManagementService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.Map;

@Tag(name = "AssignmentManagement", description = "API para la gestion de AssignmentManagement")
@RestController
@RequestMapping("/api/v1/assignments")
@PreAuthorize("hasAuthority('SCOPE_SUPERADMIN') or hasAuthority('SCOPE_ADMIN_ORGANIZACION') or hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
public class AssignmentManagementController {

    private final AssignmentManagementService assignmentManagementService;

    public AssignmentManagementController(AssignmentManagementService assignmentManagementService) {
        this.assignmentManagementService = assignmentManagementService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody AssignmentRequestDTO request) {
        Long id = assignmentManagementService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("success", true, "id", id));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> updateStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequestDTO request) {
        assignmentManagementService.updateStatus(id, request.getEstado());
        return ResponseEntity.ok(Map.of("success", true));
    }
}

