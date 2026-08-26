package com.saed.backend.identity.controller;

import com.saed.backend.identity.dto.UserAssignmentDTO;
import com.saed.backend.identity.service.ContextService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final ContextService contextService;

    public MeController(ContextService contextService) {
        this.contextService = contextService;
    }

    @GetMapping
    public ResponseEntity<String> getProfile(@AuthenticationPrincipal Long userId) {
        // Return dummy profile for now, next steps will fetch real person data
        return ResponseEntity.ok("Profile info for user " + userId);
    }

    @GetMapping("/contexts")
    public ResponseEntity<List<UserAssignmentDTO>> getContexts(@AuthenticationPrincipal Long userId) {
        List<UserAssignmentDTO> contexts = contextService.getUserContexts(userId);
        return ResponseEntity.ok(contexts);
    }
}
