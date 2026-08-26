package com.saed.backend.authorization.service;

import com.saed.backend.authorization.dto.AssignmentResponseDTO;
import com.saed.backend.authorization.repository.AssignmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AssignmentServiceTest {

    @Mock
    private AssignmentRepository assignmentRepository;

    @InjectMocks
    private AssignmentService assignmentService;

    @Test
    public void testGetAssignmentsForUser() {
        AssignmentResponseDTO dto = new AssignmentResponseDTO();
        dto.setIdAsignacion(10L);
        when(assignmentRepository.findAssignmentsByUsuarioId(1L)).thenReturn(Collections.singletonList(dto));

        List<AssignmentResponseDTO> list = assignmentService.getAssignmentsForUser(1L);
        assertEquals(1, list.size());
        assertEquals(10L, list.get(0).getIdAsignacion());
        verify(assignmentRepository, times(1)).findAssignmentsByUsuarioId(1L);
    }

    @Test
    public void testValidateAssignment() {
        AssignmentResponseDTO dto = new AssignmentResponseDTO();
        when(assignmentRepository.findByIdAndUsuarioId(10L, 1L)).thenReturn(Optional.of(dto));

        Optional<AssignmentResponseDTO> result = assignmentService.validateAssignment(10L, 1L);
        assertTrue(result.isPresent());
        verify(assignmentRepository, times(1)).findByIdAndUsuarioId(10L, 1L);
    }
}
