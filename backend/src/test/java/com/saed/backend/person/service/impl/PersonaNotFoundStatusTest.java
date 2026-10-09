package com.saed.backend.person.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.saed.backend.common.exception.GlobalExceptionHandler;
import com.saed.backend.person.repository.PersonaRepository;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Isolated-Oracle matrix finding: a person outside the caller's scope (or nonexistent) produced HTTP 500.
 * Out-of-scope and missing persons must map to 404, identically, without leaking existence.
 */
class PersonaNotFoundStatusTest {

    private final PersonaRepository repository = mock(PersonaRepository.class);
    private final PersonaServiceImpl service = new PersonaServiceImpl(repository);
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(mock(NamedParameterJdbcTemplate.class));

    @Test
    void personaOutsideScopeIsNotFoundNotServerError() {
        when(repository.findById(92003L)).thenReturn(Optional.empty());

        NoSuchElementException ex = assertThrows(NoSuchElementException.class, () -> service.getPersonaById(92003L));
        ResponseEntity<Map<String, Object>> response = handler.handleNoSuchElement(ex);

        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    void personaForUnknownUserIsNotFound() {
        when(repository.findByUserId(5L)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> service.getPersonaByUserId(5L));
    }
}
