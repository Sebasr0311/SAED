package com.saed.backend.porteria.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

import com.saed.backend.common.service.EmailService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.dashboard.controller.DashboardController;
import com.saed.backend.porteria.dto.QrAccesoDTO;
import com.saed.backend.porteria.dto.VisitaDTO;
import com.saed.backend.porteria.dto.VisitaRequestDTO;
import com.saed.backend.porteria.service.PorteriaService;
import java.sql.CallableStatement;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.CallableStatementCallback;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.support.KeyHolder;

/**
 * DEC-01: a visitor created by a porter (walk-in) or pre-registered by a resident is a person of the organization of
 * the caller's context, even though the creation runs inside a temporary elevated block.
 */
class VisitorPersonaOrganizationTest {

    private static final long ORG = 7L;
    private static final long PROP = 70L;
    private static final long UNIT = 701L;

    private NamedParameterJdbcTemplate jdbc;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        JdbcOperations operations = mock(JdbcOperations.class);
        CallableStatement statement = mock(CallableStatement.class);
        when(jdbc.getJdbcOperations()).thenReturn(operations);
        when(statement.getLong(5)).thenReturn(10L);
        when(operations.execute(anyString(), any(CallableStatementCallback.class))).thenAnswer(invocation ->
                ((CallableStatementCallback<?>) invocation.getArgument(1)).doInCallableStatement(statement));
        when(jdbc.query(anyString(), anyMap(), any(RowMapper.class))).thenAnswer(invocation -> {
            String sql = ((String) invocation.getArgument(0)).toUpperCase();
            if (sql.contains("FROM UNIDADES WHERE ID_UNIDAD")) {
                return List.of(UNIT);
            }
            if (sql.contains("FROM USUARIOS WHERE ID_USUARIO")) {
                return List.of(42L);
            }
            return List.of();
        });
        when(jdbc.update(anyString(), any(SqlParameterSource.class), any(KeyHolder.class), any(String[].class)))
                .thenAnswer(invocation -> {
                    KeyHolder keys = invocation.getArgument(2);
                    keys.getKeyList().add(Map.of(((String[]) invocation.getArgument(3))[0], 55L));
                    return 1;
                });
    }

    @AfterEach
    void tearDown() {
        SaedContextHolder.clearContext();
    }

    private SqlParameterSource personaInsert() {
        var inserts = mockingDetails(jdbc).getInvocations().stream()
                .filter(call -> call.getMethod().getName().equals("update"))
                .filter(call -> ((String) call.getArgument(0)).toUpperCase().contains("INSERT INTO PERSONAS"))
                .toList();
        assertEquals(1, inserts.size());
        assertTrue(((String) inserts.get(0).getArgument(0)).toUpperCase().contains("ID_ORGANIZACION"));
        return inserts.get(0).getArgument(1);
    }

    @Test
    void porterWalkInVisitorIsAPersonOfTheContextOrganization() {
        PorteriaService service = mock(PorteriaService.class);
        when(service.programarVisita(any(VisitaRequestDTO.class))).thenAnswer(inv -> {
            VisitaRequestDTO r = inv.getArgument(0);
            return new VisitaDTO(5000L, r.unidadId(), r.visitanteId(), r.metodoIngreso(), r.motivo(),
                    r.autorizadoPor(), r.fechaProgramada(), r.estado(), ZonedDateTime.now());
        });
        when(service.generarQrAcceso(any())).thenReturn(new QrAccesoDTO(1L, 5000L, "t",
                ZonedDateTime.now(), ZonedDateTime.now().plusDays(1), 1, 0, null, null, null, "ACTIVO", 1L));
        PorteriaController controller = new PorteriaController(service, mock(EmailService.class), jdbc);
        SaedContextHolder.setContext(SaedContext.builder().userId(42L).organizationId(ORG).propertyId(PROP)
                .roleCode("PORTERO").roleScope("PROPIEDAD").build());
        Map<String, Object> body = new HashMap<>();
        body.put("unidadId", UNIT);
        body.put("motivo", "Walk-in");
        body.put("visitante", Map.of("numeroDocumento", "CC900", "nombres", "Walk", "apellidos", "In"));

        controller.programarVisita(body);

        assertEquals(ORG, personaInsert().getValue("org"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void residentFrequentVisitorIsAPersonOfTheContextOrganization() {
        DashboardController controller = new DashboardController(jdbc, mock(ObjectProvider.class), mock(ObjectProvider.class));
        SaedContextHolder.setContext(SaedContext.builder().userId(42L).organizationId(ORG).propertyId(PROP)
                .unitId(UNIT).roleCode("RESIDENTE").roleScope("UNIDAD").build());

        controller.crearFrecuente(8L, new HashMap<>(Map.of("numeroDocumento", "CC901", "nombres", "Freq",
                "apellidos", "Visitor")));

        assertEquals(ORG, personaInsert().getValue("org"));
    }
}
