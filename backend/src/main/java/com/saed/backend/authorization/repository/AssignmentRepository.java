package com.saed.backend.authorization.repository;

import com.saed.backend.authorization.dto.AssignmentRequestDTO;
import com.saed.backend.authorization.dto.AssignmentResponseDTO;
import java.util.List;
import java.util.Optional;

public interface AssignmentRepository {
    List<AssignmentResponseDTO> findAssignmentsByUsuarioId(Long idUsuario);
    Optional<AssignmentResponseDTO> findByIdAndUsuarioId(Long idAsignacion, Long idUsuario);
    Long create(AssignmentRequestDTO request, Long asignadoPor);
    void updateStatus(Long id, String estado);
}
