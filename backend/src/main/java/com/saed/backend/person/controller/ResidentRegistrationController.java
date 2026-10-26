package com.saed.backend.person.controller;

import com.saed.backend.common.dto.ApiResponse;
import com.saed.backend.person.dto.ResidentRegistrationRequest;
import com.saed.backend.person.dto.ResidentRegistrationResult;
import com.saed.backend.person.service.ResidentRegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Residentes", description = "Registro integral de residentes")
@RestController
@RequestMapping("/api/v1/residentes/registro-integral")
public class ResidentRegistrationController {

    private final ResidentRegistrationService registrationService;

    public ResidentRegistrationController(ResidentRegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @Operation(summary = "Registra persona, tutor opcional, vínculo con la unidad y cuenta opcional en una sola transacción")
    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_ORGANIZACION') or hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<ApiResponse<ResidentRegistrationResult>> register(
            @Valid @RequestBody ResidentRegistrationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(registrationService.register(request)));
    }
}
