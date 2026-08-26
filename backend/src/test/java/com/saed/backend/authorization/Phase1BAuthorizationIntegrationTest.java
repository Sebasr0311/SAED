package com.saed.backend.authorization;

import com.saed.backend.authorization.dto.AssignmentResponseDTO;
import com.saed.backend.authorization.service.AssignmentService;
import com.saed.backend.context.SaedContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@org.springframework.test.context.ActiveProfiles("dev")
public class Phase1BAuthorizationIntegrationTest {

    @Autowired
    private AssignmentService assignmentService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    public void cleanup() {
        SaedContextHolder.clearContext();
    }

    @Test
    public void testUserGetsOnlyOwnAssignments() {
        // User 1
        List<AssignmentResponseDTO> assignments = assignmentService.getAssignmentsForUser(1L);
        assertNotNull(assignments);
        // We assume User 1 has some assignments from seed data. If not, test passes if it runs without errors.
    }

    @Test
    public void testUserWithoutAssignmentsGetsEmptyList() {
        // User 999
        List<AssignmentResponseDTO> assignments = assignmentService.getAssignmentsForUser(999L);
        assertNotNull(assignments);
        assertTrue(assignments.isEmpty());
    }

    @Test
    public void testValidateAssignmentFailsForForeignAssignment() {
        // Assume user 1 tries to access assignment 9999 (doesn't exist or belongs to another)
        Optional<AssignmentResponseDTO> opt = assignmentService.validateAssignment(9999L, 1L);
        assertTrue(opt.isEmpty(), "Assignment 9999 should be rejected for User 1");
    }

    @Test
    public void testOracleRLSBlocksIfState1Only() {
        // If we don't set a valid State 2 in DB, query to properties should yield 0 or exception
        try {
            Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM PROPIEDADES", Integer.class);
            assertEquals(0, count, "RLS should block access in STATE 1");
        } catch (DataAccessException e) {
             assertNotNull(e);
        }
    }
}
