package com.saed.backend.porteria.controller;

import com.saed.backend.common.service.EmailService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.porteria.dto.QrAccesoDTO;
import com.saed.backend.porteria.dto.VisitaDTO;
import com.saed.backend.porteria.dto.VisitaRequestDTO;
import com.saed.backend.porteria.service.PorteriaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for C-06 (visitor lookup) and F7-02 (visit scheduling scope) in {@link PorteriaController}.
 * JDBC is mocked: these tests prove the controller's explicit scoping and the absence of
 * SUPERADMIN elevation on the lookup path, not Oracle RLS behavior.
 */
class PorteriaVisitorIsolationTest {

    private static final long CALLER_ORG = 7L;
    private static final long CALLER_PROP = 70L;
    private static final long OWN_UNIT = 701L;
    private static final long FOREIGN_UNIT = 999L;

    private NamedParameterJdbcTemplate jdbc;
    private PorteriaService porteriaService;
    private PorteriaController controller;

    /** Roles observed in SaedContextHolder while any JDBC call executed. */
    private final List<String> rolesSeenDuringJdbc = new ArrayList<>();
    /** SQL text and params seen by query(...) calls. */
    private final List<String> sqlSeen = new ArrayList<>();
    private final List<Map<String, ?>> paramsSeen = new ArrayList<>();
    /** Pluggable answer for query(sql, params, rowMapper): returns ids for the given SQL. */
    private BiFunction<String, Map<String, ?>, List<Long>> idQueryAnswer = (sql, p) -> List.of();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        porteriaService = mock(PorteriaService.class);
        controller = new PorteriaController(porteriaService, mock(EmailService.class), jdbc);

        when(jdbc.query(anyString(), anyMap(), any(RowMapper.class))).thenAnswer(inv -> {
            String sql = inv.getArgument(0);
            Map<String, ?> params = inv.getArgument(1);
            recordCall(sql, params);
            return new ArrayList<>(idQueryAnswer.apply(sql, params));
        });

        when(porteriaService.programarVisita(any(VisitaRequestDTO.class))).thenAnswer(inv -> {
            VisitaRequestDTO r = inv.getArgument(0);
            return new VisitaDTO(5000L, r.unidadId(), r.visitanteId(), r.metodoIngreso(), r.motivo(),
                    r.autorizadoPor(), r.fechaProgramada(), r.estado(), ZonedDateTime.now());
        });
        when(porteriaService.generarQrAcceso(any())).thenReturn(new QrAccesoDTO(1L, 5000L, "t",
                ZonedDateTime.now(), ZonedDateTime.now().plusDays(1), 1, 0, null, null, null, "ACTIVO", 1L));
    }

    @AfterEach
    void tearDown() {
        SaedContextHolder.clearContext();
    }

    private void recordCall(String sql, Map<String, ?> params) {
        SaedContext ctx = SaedContextHolder.getContext();
        rolesSeenDuringJdbc.add(ctx != null ? ctx.getRoleCode() : null);
        sqlSeen.add(sql);
        paramsSeen.add(params);
    }

    private static void asCaller(String role, String scope, Long unitId) {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(42L)
                .organizationId(CALLER_ORG)
                .propertyId(CALLER_PROP)
                .unitId(unitId)
                .roleCode(role)
                .roleScope(scope)
                .build());
    }

    // ---------------------------------------------------------------------
    // C-06: GET /porteria/visitas/buscar
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("C-06: portero lookup runs under caller context, scoped to caller property, without email")
    @SuppressWarnings("unchecked")
    void porteroLookupRunsUnderCallerContextAndOmitsEmail() {
        asCaller("PORTERO", "PROPIEDAD", null);
        Map<String, Object> row = new HashMap<>();
        row.put("ID_PERSONA", 11L);
        row.put("ID_VISITANTE", 22L);
        row.put("ID_TIPO_DOCUMENTO", 1L);
        row.put("NUMERO_DOCUMENTO", "123456");
        row.put("PRIMER_NOMBRE", "Ana");
        row.put("PRIMER_APELLIDO", "Diaz");
        row.put("TELEFONO", "3001234567");
        row.put("EMAIL", "ana@example.com");
        when(jdbc.queryForList(anyString(), anyMap())).thenAnswer(inv -> {
            recordCall(inv.getArgument(0), inv.getArgument(1));
            return List.of(row);
        });

        ResponseEntity<Map<String, Object>> res = controller.buscarVisitantePorDocumento("123456");

        Map<String, Object> body = res.getBody();
        assertEquals("Ana", body.get("nombres"));
        assertEquals("123456", body.get("numeroDocumento"));
        assertFalse(body.containsKey("email"), "lookup must never return email");
        assertNotEquals("3001234567", body.get("telefono"), "phone must be masked");
        assertTrue(String.valueOf(body.get("telefono")).endsWith("4567"));

        // No elevation: no PL/SQL context switch and every query ran as the caller.
        verify(jdbc, never()).getJdbcOperations();
        assertFalse(rolesSeenDuringJdbc.contains("SUPERADMIN"));
        assertTrue(rolesSeenDuringJdbc.stream().allMatch("PORTERO"::equals));
        assertEquals("PORTERO", SaedContextHolder.getContext().getRoleCode());

        // Explicit WHERE scoping to the caller's property.
        String sql = sqlSeen.get(sqlSeen.size() - 1).toUpperCase();
        assertTrue(sql.contains("ID_PROPIEDAD = :PROPID"), "lookup SQL must be scoped by property: " + sql);
        assertFalse(sql.contains("EMAIL"), "lookup SQL must not read EMAIL");
        assertEquals(CALLER_PROP, ((Number) paramsSeen.get(paramsSeen.size() - 1).get("propId")).longValue());
    }

    @Test
    @DisplayName("C-06: person outside caller scope returns the same empty response as a nonexistent one")
    @SuppressWarnings("unchecked")
    void foreignTenantPersonReturnsNotFound() {
        asCaller("ADMIN_PROPIEDAD", "PROPIEDAD", null);
        when(jdbc.queryForList(anyString(), anyMap())).thenAnswer(inv -> {
            recordCall(inv.getArgument(0), inv.getArgument(1));
            return List.of();
        });

        ResponseEntity<Map<String, Object>> foreign = controller.buscarVisitantePorDocumento("FOREIGN-DOC");
        ResponseEntity<Map<String, Object>> missing = controller.buscarVisitantePorDocumento("NO-SUCH-DOC");

        assertEquals(missing.getStatusCode(), foreign.getStatusCode());
        assertEquals(missing.getBody(), foreign.getBody());
        assertTrue(foreign.getBody().isEmpty());
        verify(jdbc, never()).getJdbcOperations();
    }

    @Test
    @DisplayName("C-06: caller without property context gets not-found and no query runs")
    void lookupWithoutPropertyContextReturnsEmpty() {
        SaedContextHolder.setContext(SaedContext.builder().userId(42L).organizationId(CALLER_ORG)
                .roleCode("PORTERO").roleScope("PROPIEDAD").build());

        ResponseEntity<Map<String, Object>> res = controller.buscarVisitantePorDocumento("123456");

        assertTrue(res.getBody().isEmpty());
        verify(jdbc, never()).queryForList(anyString(), anyMap());
        verify(jdbc, never()).getJdbcOperations();
    }

    // ---------------------------------------------------------------------
    // F7-02: POST /porteria/visitas
    // ---------------------------------------------------------------------

    private static Map<String, Object> visitBody(Object unidadId, Object visitanteId) {
        Map<String, Object> body = new HashMap<>();
        if (unidadId != null) body.put("unidadId", unidadId);
        if (visitanteId != null) body.put("visitanteId", visitanteId);
        body.put("motivo", "Test");
        return body;
    }

    /** Answers: visitor 10 visible, OWN_UNIT belongs to caller property, foreign unit does not. */
    private void defaultScopeAnswers() {
        idQueryAnswer = (sql, p) -> {
            String s = sql.toUpperCase();
            if (s.contains("FROM VISITANTES WHERE ID_VISITANTE")) {
                return Long.valueOf(10L).equals(asLong(p.get("v"))) ? List.of(10L) : List.of();
            }
            if (s.contains("FROM UNIDADES WHERE ID_UNIDAD")) {
                return Long.valueOf(OWN_UNIT).equals(asLong(p.get("u"))) && Long.valueOf(CALLER_PROP).equals(asLong(p.get("p")))
                        ? List.of(OWN_UNIT) : List.of();
            }
            if (s.contains("FROM USUARIOS WHERE ID_USUARIO")) {
                return List.of(42L);
            }
            return List.of();
        };
    }

    private static Long asLong(Object o) {
        return o instanceof Number n ? n.longValue() : null;
    }

    @Test
    @DisplayName("F7-02: resident scheduling for a foreign unit gets 403 and nothing is inserted")
    void residentForeignUnitIsDenied() {
        asCaller("RESIDENTE", "UNIDAD", OWN_UNIT);
        defaultScopeAnswers();

        assertThrows(AccessDeniedException.class, () -> controller.programarVisita(visitBody(FOREIGN_UNIT, 10L)));

        verify(porteriaService, never()).programarVisita(any());
        verify(jdbc, never()).getJdbcOperations();
    }

    @Test
    @DisplayName("F7-02: resident sending a foreign idUnidad alias gets 403")
    void residentForeignIdUnidadAliasIsDenied() {
        asCaller("RESIDENTE", "UNIDAD", OWN_UNIT);
        defaultScopeAnswers();
        Map<String, Object> body = visitBody(null, 10L);
        body.put("idUnidad", FOREIGN_UNIT);

        assertThrows(AccessDeniedException.class, () -> controller.programarVisita(body));
        verify(porteriaService, never()).programarVisita(any());
    }

    @Test
    @DisplayName("F7-02: resident sending a foreign idPropiedad gets 403")
    void residentForeignPropertyIsDenied() {
        asCaller("RESIDENTE", "UNIDAD", OWN_UNIT);
        defaultScopeAnswers();
        Map<String, Object> body = visitBody(OWN_UNIT, 10L);
        body.put("idPropiedad", 12345L);

        assertThrows(AccessDeniedException.class, () -> controller.programarVisita(body));
        verify(porteriaService, never()).programarVisita(any());
    }

    @Test
    @DisplayName("F7-02: resident scheduling for own unit succeeds without SUPERADMIN context")
    void residentOwnUnitSucceeds() {
        asCaller("RESIDENTE", "UNIDAD", OWN_UNIT);
        defaultScopeAnswers();

        Map<String, Object> res = controller.programarVisita(visitBody(OWN_UNIT, 10L));

        ArgumentCaptor<VisitaRequestDTO> captor = ArgumentCaptor.forClass(VisitaRequestDTO.class);
        verify(porteriaService).programarVisita(captor.capture());
        assertEquals(OWN_UNIT, captor.getValue().unidadId());
        assertEquals(10L, captor.getValue().visitanteId());
        assertEquals(5000L, res.get("idVisita"));
        assertFalse(rolesSeenDuringJdbc.contains("SUPERADMIN"));
        verify(jdbc, never()).getJdbcOperations();
    }

    @Test
    @DisplayName("F07-01: response carries the server-issued QR token and the validity is bounded")
    void responseCarriesServerIssuedTokenAndBoundedValidity() {
        asCaller("RESIDENTE", "UNIDAD", OWN_UNIT);
        defaultScopeAnswers();
        when(porteriaService.generarQrAcceso(any())).thenReturn(new QrAccesoDTO(1L, 5000L, "server-token",
                ZonedDateTime.now(), ZonedDateTime.now().plusDays(1), 1, 0, null, null, null, "ACTIVO", 42L));
        Map<String, Object> body = visitBody(OWN_UNIT, 10L);
        body.put("tiempoValidezMin", "99999999");

        Map<String, Object> res = controller.programarVisita(body);

        assertEquals("server-token", res.get("codigoQr"));
        assertEquals("server-token", res.get("token"));
        ArgumentCaptor<com.saed.backend.porteria.dto.QrAccesoRequestDTO> qr =
                ArgumentCaptor.forClass(com.saed.backend.porteria.dto.QrAccesoRequestDTO.class);
        verify(porteriaService).generarQrAcceso(qr.capture());
        assertTrue(qr.getValue().fechaExpiracion().isBefore(ZonedDateTime.now().plusDays(8)));
    }

    @Test
    @DisplayName("F07-01: cancel and revoke endpoints delegate to the scoped service operations")
    void cancelAndRevokeDelegate() {
        asCaller("RESIDENTE", "UNIDAD", OWN_UNIT);
        controller.cancelarVisita(5000L);
        controller.revocarQr(77L, Map.of("motivo", "no viene"));
        verify(porteriaService).cancelarVisita(5000L);
        verify(porteriaService).revocarQr(77L, "no viene");
    }

    @Test
    @DisplayName("F7-02: portero scheduling for a unit outside the caller property gets 403")
    void porteroForeignUnitIsDenied() {
        asCaller("PORTERO", "PROPIEDAD", null);
        defaultScopeAnswers();

        assertThrows(AccessDeniedException.class, () -> controller.programarVisita(visitBody(FOREIGN_UNIT, 10L)));
        verify(porteriaService, never()).programarVisita(any());
    }

    @Test
    @DisplayName("F7-02: admin resolving idResidente to a unit outside the property gets 403")
    void adminForeignResidentUnitIsDenied() {
        asCaller("ADMIN_PROPIEDAD", "PROPIEDAD", null);
        idQueryAnswer = (sql, p) -> {
            String s = sql.toUpperCase();
            if (s.contains("FROM VISITANTES WHERE ID_VISITANTE")) return List.of(10L);
            if (s.contains("FROM RESIDENTES_UNIDAD")) return List.of(FOREIGN_UNIT);
            return List.of();
        };
        Map<String, Object> body = visitBody(null, 10L);
        body.put("idResidente", 555L);

        assertThrows(AccessDeniedException.class, () -> controller.programarVisita(body));
        verify(porteriaService, never()).programarVisita(any());
    }

    @Test
    @DisplayName("F7-02: no resolvable unit no longer falls back to unit 1")
    void unresolvableUnitIsRejectedInsteadOfDefaultingToUnitOne() {
        asCaller("ADMIN_PROPIEDAD", "PROPIEDAD", null);
        idQueryAnswer = (sql, p) -> sql.toUpperCase().contains("FROM VISITANTES WHERE ID_VISITANTE") ? List.of(10L) : List.of();

        assertThrows(IllegalArgumentException.class, () -> controller.programarVisita(visitBody(null, 10L)));
        verify(porteriaService, never()).programarVisita(any());
    }

    @Test
    @DisplayName("F7-02: no resolvable unit is rejected instead of defaulting to an arbitrary unit of the property")
    void unresolvableUnitIsRejectedEvenWhenPropertyHasUnits() {
        asCaller("ADMIN_PROPIEDAD", "PROPIEDAD", null);
        idQueryAnswer = (sql, p) -> {
            String s = sql.toUpperCase();
            if (s.contains("FROM VISITANTES WHERE ID_VISITANTE")) return List.of(10L);
            if (s.contains("FROM UNIDADES WHERE ID_PROPIEDAD")) return List.of(OWN_UNIT);
            return List.of();
        };

        assertThrows(IllegalArgumentException.class, () -> controller.programarVisita(visitBody(null, 10L)));
        verify(porteriaService, never()).programarVisita(any());
    }

    @Test
    @DisplayName("F7-02: a client-supplied visitor id not visible in caller scope is never used")
    void invisibleVisitorIdIsNotUsed() {
        asCaller("RESIDENTE", "UNIDAD", OWN_UNIT);
        defaultScopeAnswers();

        assertThrows(IllegalStateException.class, () -> controller.programarVisita(visitBody(OWN_UNIT, 777L)));
        verify(porteriaService, never()).programarVisita(any());
    }

    @Test
    @DisplayName("F7-02: client-supplied idPersona inside visitante payload is ignored; persona resolved by document")
    void clientSuppliedPersonaIdIsIgnored() {
        asCaller("RESIDENTE", "UNIDAD", OWN_UNIT);
        idQueryAnswer = (sql, p) -> {
            String s = sql.toUpperCase();
            if (s.contains("FROM PERSONAS WHERE NUMERO_DOCUMENTO")) return List.of(55L);
            if (s.contains("FROM VISITANTES WHERE ID_PERSONA")) {
                return Long.valueOf(55L).equals(asLong(p.get("p"))) ? List.of(66L) : List.of(88L);
            }
            if (s.contains("FROM UNIDADES WHERE ID_UNIDAD")) return List.of(OWN_UNIT);
            if (s.contains("FROM USUARIOS WHERE ID_USUARIO")) return List.of(42L);
            return List.of();
        };
        when(jdbc.getJdbcOperations()).thenReturn(mock(org.springframework.jdbc.core.JdbcOperations.class));
        Map<String, Object> visitante = new HashMap<>();
        visitante.put("numeroDocumento", "CC-55");
        visitante.put("nombres", "Luis");
        visitante.put("idPersona", 999L);
        visitante.put("idVisitante", 888L);
        Map<String, Object> body = visitBody(OWN_UNIT, null);
        body.put("visitante", visitante);

        controller.programarVisita(body);

        ArgumentCaptor<VisitaRequestDTO> captor = ArgumentCaptor.forClass(VisitaRequestDTO.class);
        verify(porteriaService).programarVisita(captor.capture());
        assertEquals(66L, captor.getValue().visitanteId());
        for (Map<String, ?> params : paramsSeen) {
            assertNotEquals(999L, asLong(params.get("p")), "client idPersona must never reach a query");
        }
        assertEquals("RESIDENTE", SaedContextHolder.getContext().getRoleCode(), "caller context restored");
    }
}
