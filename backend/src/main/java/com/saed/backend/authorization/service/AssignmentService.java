package com.saed.backend.authorization.service;

import com.saed.backend.authorization.dto.AssignmentResponseDTO;
import com.saed.backend.authorization.repository.AssignmentRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class AssignmentService {

    private final AssignmentRepository assignmentRepository;

    public AssignmentService(AssignmentRepository assignmentRepository) {
        this.assignmentRepository = assignmentRepository;
    }

    public List<AssignmentResponseDTO> getAssignmentsForUser(Long idUsuario) {
        return assignmentRepository.findAssignmentsByUsuarioId(idUsuario);
    }

    public Optional<AssignmentResponseDTO> validateAssignment(Long idAsignacion, Long idUsuario) {
        return assignmentRepository.findByIdAndUsuarioId(idAsignacion, idUsuario);
    }
}
