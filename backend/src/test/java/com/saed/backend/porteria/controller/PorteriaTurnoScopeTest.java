package com.saed.backend.porteria.controller;

import com.saed.backend.common.service.EmailService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.porteria.dto.PorteroTurnoRequestDTO;
import com.saed.backend.porteria.service.PorteriaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Turno/portero endpoints must never fall back to property 1 and must only assign porteros of the
 * caller's property, and only to a porteria of the caller's property.
 */
class PorteriaTurnoScopeTest {
    private final PorteriaService porteriaService = mock(PorteriaService.class);
    private final EmailService emailService = mock(EmailService.class);
    private final NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
    private final PorteriaController controller = new PorteriaController(porteriaService, emailService, jdbcTemplate);

    @AfterEach
    void clearContext() {
        SaedContextHolder.clearContext();
    }

    private static void adminOf(long propId) {
        SaedContextHolder.setContext(SaedContext.builder()
            .userId(10L).organizationId(1L).propertyId(propId)
            .roleCode("ADMIN_PROPIEDAD").roleScope("PROPIEDAD").build());
    }

    @Test
    void turnosRejectMissingPropertyContext() {
        PorteroTurnoRequestDTO req = new PorteroTurnoRequestDTO(55L, "TURNO_8H_MANANA", "L-V");
        assertThrows(AccessDeniedException.class, () -> controller.listarTurnos(8L));
        assertThrows(AccessDeniedException.class, controller::listarPorterosDisponibles);
        assertThrows(AccessDeniedException.class, () -> controller.asignarTurno(8L, req));
        assertThrows(AccessDeniedException.class, () -> controller.desasignarTurno(8L, 3L));
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void turnosRejectContextWithoutProperty() {
        SaedContextHolder.setContext(SaedContext.builder()
            .userId(10L).organizationId(1L).roleCode("ADMIN_PROPIEDAD").roleScope("PROPIEDAD").build());
        assertThrows(AccessDeniedException.class, () -> controller.listarTurnos(8L));
        assertThrows(AccessDeniedException.class, controller::listarPorterosDisponibles);
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void asignarTurnoRejectsUserThatIsNotPorteroOfTheProperty() {
        adminOf(70L);
        List<String> sqlSeen = new ArrayList<>();
        when(jdbcTemplate.query(anyString(), anyMap(), any(RowMapper.class))).thenAnswer(inv -> {
            String sql = inv.getArgument(0);
            sqlSeen.add(sql);
            // Porteria 8 belongs to property 70, but user 55 is not an active PORTERO of property 70.
            if (sql.contains("FROM PORTERIAS")) {
                return new ArrayList<>(List.of(8L));
            }
            return new ArrayList<>();
        });

        assertThrows(AccessDeniedException.class,
            () -> controller.asignarTurno(8L, new PorteroTurnoRequestDTO(55L, "TURNO_8H_MANANA", "L-V")));
        verify(jdbcTemplate, never()).update(anyString(), anyMap());
        assertTrue(sqlSeen.stream().anyMatch(s -> s.contains("'PORTERO'")), "portero membership must be checked");
    }

    @Test
    @SuppressWarnings("unchecked")
    void asignarTurnoRejectsPorteriaOfAnotherProperty() {
        adminOf(70L);
        when(jdbcTemplate.query(anyString(), anyMap(), any(RowMapper.class))).thenAnswer(inv -> {
            String sql = inv.getArgument(0);
            if (sql.contains("FROM PORTERIAS")) {
                return new ArrayList<>(); // porteria 8 is not in property 70
            }
            return new ArrayList<>(List.of(55L));
        });

        assertThrows(AccessDeniedException.class,
            () -> controller.asignarTurno(8L, new PorteroTurnoRequestDTO(55L, "TURNO_8H_MANANA", "L-V")));
        verify(jdbcTemplate, never()).update(anyString(), anyMap());
    }

    @Test
    @SuppressWarnings("unchecked")
    void asignarTurnoMergeIsScopedToCallerProperty() {
        adminOf(70L);
        when(jdbcTemplate.query(anyString(), anyMap(), any(RowMapper.class))).thenAnswer(inv -> {
            String sql = inv.getArgument(0);
            if (sql.contains("FROM PORTERIAS")) {
                return new ArrayList<>(List.of(8L));
            }
            return new ArrayList<>(List.of(55L));
        });
        List<String> updates = new ArrayList<>();
        List<Map<String, ?>> updateParams = new ArrayList<>();
        when(jdbcTemplate.update(anyString(), anyMap())).thenAnswer(inv -> {
            updates.add(inv.getArgument(0));
            updateParams.add(inv.getArgument(1));
            return 0; // no existing assignment: the insert must follow
        });

        controller.asignarTurno(8L, new PorteroTurnoRequestDTO(55L, "TURNO_8H_MANANA", "L-V"));

        // PORTERIA_TURNOS has a VPD policy (V5.32): MERGE raises ORA-28132, so UPDATE then INSERT is used.
        assertTrue(updates.stream().noneMatch(s -> s.contains("MERGE")), "MERGE is not allowed on a VPD table");
        assertEquals(2, updates.size());
        assertTrue(updates.get(0).startsWith("UPDATE PORTERIA_TURNOS"));
        assertTrue(updates.get(0).contains("ID_PROPIEDAD = :propId"), "update must be scoped to the property");
        assertTrue(updates.get(1).startsWith("INSERT INTO PORTERIA_TURNOS"));
        assertEquals(70L, updateParams.get(0).get("propId"));
        assertEquals(70L, updateParams.get(1).get("propId"));
    }
}
