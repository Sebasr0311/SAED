package com.saed.backend.comunicacion;

import com.saed.backend.common.service.EmailService;
import com.saed.backend.comunicacion.controller.ComunicadosController;
import com.saed.backend.comunicacion.service.ComunicadoService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ComunicadosControllerScopeTest {

    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final ComunicadoService service = mock(ComunicadoService.class);
    private final ComunicadosController controller = new ComunicadosController(jdbc, mock(EmailService.class), service);

    @AfterEach
    void clear() {
        SaedContextHolder.clearContext();
    }

    @Test
    void noticeEndpointsDelegateToTheScopedService() {
        controller.getAvisos(70L);
        controller.postAviso(Map.of("titulo", "t"));
        controller.archivarAviso(9L);
        controller.marcarLeido(9L);
        controller.archivarParaMi(9L);
        controller.getAvisosArchivados();
        verify(service).listVisible(70L);
        verify(service).publish(Map.of("titulo", "t"));
        verify(service).archiveOfficial(9L);
        verify(service).markRead(9L);
        verify(service).archiveForUser(9L);
        verify(service).listArchivedForUser();
    }

    @Test
    @SuppressWarnings("unchecked")
    void noiseNoticeForAUnitOfAnotherPropertyIsDenied() {
        SaedContextHolder.setContext(SaedContext.builder().userId(30L).organizationId(1L).propertyId(70L)
            .roleCode("PORTERO").roleScope("PROPIEDAD").build());
        when(jdbc.query(anyString(), anyMap(), any(RowMapper.class))).thenReturn(new ArrayList<>(List.of()));

        assertThrows(AccessDeniedException.class,
            () -> controller.postAvisoRuido(Map.of("idApartamento", 999L)));
        verify(jdbc, never()).update(anyString(), anyMap());
    }

    @Test
    void noiseNoticeWithoutContextIsDenied() {
        assertThrows(AccessDeniedException.class, () -> controller.postAvisoRuido(Map.of("idApartamento", 1L)));
        verify(jdbc, never()).update(anyString(), anyMap());
    }
}
