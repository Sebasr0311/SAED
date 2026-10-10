package com.saed.backend.comunicacion;

import com.saed.backend.common.service.EmailService;
import com.saed.backend.comunicacion.service.ComunicadoService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** F08-03: communications are segmented and tenant-scoped; read/archive state persists per user. */
class ComunicadoSegmentationTest {

    private static final long ORG = 1L;
    private static final long PROP = 70L;
    private static final long UNIT = 701L;

    private NamedParameterJdbcTemplate jdbc;
    private EmailService email;
    private ComunicadoService service;
    private final List<String> sql = new ArrayList<>();
    private final List<Map<String, ?>> params = new ArrayList<>();
    private final List<String> updates = new ArrayList<>();
    private final List<Map<String, ?>> updateParams = new ArrayList<>();
    /** Answer for id lookups (org properties, block ownership, visibility counts). */
    private List<Long> idAnswer = List.of();
    private int updateResult = 1;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        email = mock(EmailService.class);
        service = new ComunicadoService(jdbc, email);
        when(jdbc.queryForList(anyString(), anyMap())).thenAnswer(inv -> {
            sql.add(inv.getArgument(0));
            params.add(inv.getArgument(1));
            return new ArrayList<Map<String, Object>>();
        });
        when(jdbc.query(anyString(), anyMap(), any(RowMapper.class))).thenAnswer(inv -> {
            sql.add(inv.getArgument(0));
            params.add(inv.getArgument(1));
            return new ArrayList<>(idAnswer);
        });
        when(jdbc.update(anyString(), anyMap())).thenAnswer(inv -> {
            updates.add(inv.getArgument(0));
            updateParams.add(inv.getArgument(1));
            return updateResult;
        });
    }

    @AfterEach
    void clear() {
        SaedContextHolder.clearContext();
    }

    private static void ctx(String role, String scope, Long prop, Long unit) {
        SaedContextHolder.setContext(SaedContext.builder().userId(40L).organizationId(ORG).propertyId(prop)
            .unitId(unit).roleCode(role).roleScope(scope).build());
    }

    // --- reading ---

    @Test
    void residentListIsScopedToPropertyAndSegments() {
        ctx("RESIDENTE", "UNIDAD", PROP, UNIT);
        service.listVisible(null);
        String q = sql.get(sql.size() - 1);
        assertTrue(q.contains("TIPO_SEGMENTACION = 'TORRE_BLOQUE'"), q);
        assertTrue(q.contains("SOLO_PROPIETARIOS") && q.contains("SOLO_ARRENDATARIOS"), q);
        assertTrue(q.contains("FECHA_EXPIRACION"), q);
        assertTrue(q.contains("ARCHIVADO_EN IS NOT NULL"), "per-user archive must hide the notice");
        assertEquals(List.of(PROP), params.get(params.size() - 1).get("props"));
    }

    @Test
    void residentCannotAskForAnotherProperty() {
        ctx("RESIDENTE", "UNIDAD", PROP, UNIT);
        assertThrows(AccessDeniedException.class, () -> service.listVisible(80L));
    }

    @Test
    void staffCannotAskForAnotherProperty() {
        ctx("ADMIN_PROPIEDAD", "PROPIEDAD", PROP, null);
        assertThrows(AccessDeniedException.class, () -> service.listVisible(80L));
    }

    @Test
    void orgAdminCannotAskForAPropertyOutsideTheOrganization() {
        ctx("ADMIN_ORGANIZACION", "ORGANIZACION", null, null);
        idAnswer = List.of(70L, 71L);
        assertThrows(AccessDeniedException.class, () -> service.listVisible(80L));
    }

    @Test
    void superadminAndMissingContextAreDenied() {
        assertThrows(AccessDeniedException.class, () -> service.listVisible(null));
        ctx("SUPERADMIN", "GLOBAL", PROP, null);
        assertThrows(AccessDeniedException.class, () -> service.listVisible(null));
    }

    // --- per-user read / archive ---

    @Test
    void markReadPersistsPerUserAndInsertsWhenMissing() {
        ctx("RESIDENTE", "UNIDAD", PROP, UNIT);
        idAnswer = List.of(9L); // the notice is visible to the caller
        updateResult = 0; // no previous read row

        service.markRead(9L);

        assertTrue(updates.get(0).startsWith("UPDATE COMUNICADOS_LECTURAS"), updates.get(0));
        assertTrue(updates.get(1).startsWith("INSERT INTO COMUNICADOS_LECTURAS"), updates.get(1));
        assertEquals(40L, updateParams.get(1).get("usuario"));
        assertEquals(PROP, updateParams.get(1).get("propId"));
    }

    @Test
    void invisibleNoticeCannotBeMarked() {
        ctx("RESIDENTE", "UNIDAD", PROP, UNIT);
        idAnswer = List.of();
        assertThrows(NoSuchElementException.class, () -> service.markRead(9L));
        assertThrows(NoSuchElementException.class, () -> service.archiveForUser(9L));
        assertTrue(updates.isEmpty());
    }

    // --- publishing ---

    private Map<String, Object> aviso(String seg) {
        Map<String, Object> p = new HashMap<>();
        p.put("titulo", "Corte <script>alert(1)</script>");
        p.put("mensaje", "Agua & luz");
        p.put("tipoSegmentacion", seg);
        p.put("enviarEmail", true);
        return p;
    }

    @Test
    void legacySegmentValuesAreNormalizedAndPublisherRecorded() {
        ctx("ADMIN_PROPIEDAD", "PROPIEDAD", PROP, null);
        service.publish(aviso("PROPIETARIOS"));
        Map<String, ?> insert = updateParams.get(0);
        assertEquals("SOLO_PROPIETARIOS", insert.get("segmentacion"));
        assertEquals(40L, insert.get("publicadoPor"));
        assertEquals(PROP, insert.get("propId"));
    }

    @Test
    void unknownSegmentIsRejected() {
        ctx("ADMIN_PROPIEDAD", "PROPIEDAD", PROP, null);
        assertThrows(IllegalArgumentException.class, () -> service.publish(aviso("VECINOS")));
        assertTrue(updates.isEmpty());
    }

    @Test
    void blockSegmentRequiresABlockOfTheProperty() {
        ctx("ADMIN_PROPIEDAD", "PROPIEDAD", PROP, null);
        assertThrows(IllegalArgumentException.class, () -> service.publish(aviso("TORRE_BLOQUE")));
        Map<String, Object> p = aviso("TORRE_BLOQUE");
        p.put("idBloque", 99L);
        idAnswer = List.of(); // block 99 is not in property 70
        assertThrows(AccessDeniedException.class, () -> service.publish(p));
        assertTrue(updates.isEmpty());
    }

    @Test
    void propertyAdminCannotPublishToAnotherProperty() {
        ctx("ADMIN_PROPIEDAD", "PROPIEDAD", PROP, null);
        Map<String, Object> p = aviso("TODOS");
        p.put("idPropiedad", 80L);
        assertThrows(AccessDeniedException.class, () -> service.publish(p));
        assertTrue(updates.isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void emailHtmlIsEscapedAndSentOnlyToSegmentRecipients() throws Exception {
        ctx("ADMIN_PROPIEDAD", "PROPIEDAD", PROP, null);
        when(jdbc.queryForList(anyString(), anyMap())).thenAnswer(inv -> {
            sql.add(inv.getArgument(0));
            return new ArrayList<>(List.of(Map.<String, Object>of("EMAIL", "a@x.co")));
        });

        service.publish(aviso("SOLO_ARRENDATARIOS"));

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(email).enviarHtmlPublico(eq("a@x.co"), anyString(), html.capture());
        assertFalse(html.getValue().contains("<script>"), html.getValue());
        assertTrue(html.getValue().contains("&amp;"), html.getValue());
        assertTrue(sql.get(sql.size() - 1).contains("ARRENDATARIO"), "recipients follow the segment");
    }

    @Test
    void archivingANoticeOfAnotherPropertyLooksLikeNotFound() throws Exception {
        ctx("ADMIN_PROPIEDAD", "PROPIEDAD", PROP, null);
        updateResult = 0;
        assertThrows(NoSuchElementException.class, () -> service.archiveOfficial(9L));
        assertTrue(updates.get(0).contains("ID_PROPIEDAD IN (:props)"), updates.get(0));
        verify(email, never()).enviarHtmlPublico(anyString(), anyString(), anyString());
    }
}
