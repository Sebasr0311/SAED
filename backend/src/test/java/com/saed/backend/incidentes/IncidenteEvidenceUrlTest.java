package com.saed.backend.incidentes;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.incidentes.dto.IncidenteDTO;
import com.saed.backend.incidentes.repository.IncidenteRepository;
import com.saed.backend.incidentes.service.impl.IncidenteServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/** F9-09: an incident cannot be stored with an evidence link that is not a plain http(s) link or inline image. */
class IncidenteEvidenceUrlTest {

    private final IncidenteRepository repository = mock(IncidenteRepository.class);
    private final IncidenteServiceImpl service = new IncidenteServiceImpl(repository, mock(NamedParameterJdbcTemplate.class));

    @AfterEach
    void clear() {
        SaedContextHolder.clearContext();
    }

    @Test
    void aJavascriptEvidenceLinkIsRejectedBeforeAnythingIsStored() {
        SaedContextHolder.setContext(SaedContext.builder().userId(15L).propertyId(2L).organizationId(1L)
                .roleCode("ADMIN_PROPIEDAD").roleScope("PROPIEDAD").build());
        IncidenteDTO request = new IncidenteDTO();
        request.setTitulo("Daño");
        request.setTipoIncidente("DANO");
        request.setDescripcionHechos("Hechos");
        request.setFechaHoraIncidente(ZonedDateTime.now());
        request.setEvidenciasUrls("javascript:alert(document.cookie)");

        assertThrows(IllegalArgumentException.class, () -> service.reportarIncidente(request));
        verifyNoInteractions(repository);
    }
}
