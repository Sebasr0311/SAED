package com.saed.backend.identity.controller;

import com.saed.backend.identity.dto.UserAssignmentDTO;
import com.saed.backend.identity.service.ContextService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@Tag(name = "Me", description = "API para la gestion de Me")
@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final ContextService contextService;

    public MeController(ContextService contextService) {
        this.contextService = contextService;
    }

    @GetMapping
    public ResponseEntity<java.util.Map<String, Object>> getProfile(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(java.util.Map.of("id", userId));
    }

    @GetMapping("/contexts")
    public ResponseEntity<List<UserAssignmentDTO>> getContexts(@AuthenticationPrincipal Long userId) {
        List<UserAssignmentDTO> contexts = contextService.getUserContexts(userId);
        return ResponseEntity.ok(contexts);
    }
}

