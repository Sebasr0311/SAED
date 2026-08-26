package com.saed.backend.authorization.controller;

import com.saed.backend.authorization.dto.AssignmentResponseDTO;
import com.saed.backend.authorization.service.AssignmentService;
import com.saed.backend.common.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/auth/assignments")
public class AssignmentController {

    private final AssignmentService assignmentService;

    public AssignmentController(AssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AssignmentResponseDTO>>> getAssignments(@AuthenticationPrincipal Long userId) {
        if (userId == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Unauthorized"));
        }
        
        List<AssignmentResponseDTO> assignments = assignmentService.getAssignmentsForUser(userId);
        return ResponseEntity.ok(ApiResponse.success(assignments));
    }
}
