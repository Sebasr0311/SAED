package com.saed.backend.common.exception;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A single-row lookup that finds nothing (absent or hidden by RLS) must answer 404 without disclosing which
 * (DEC-01 non-disclosure), not the generic 500 DATABASE_ERROR. A multi-row result stays a server error.
 */
class EmptyResultNotFoundTest {

    @RestController
    static class ProbeController {
        @GetMapping("/empty")
        String empty() {
            throw new EmptyResultDataAccessException("SELECT secret detail", 1);
        }

        @GetMapping("/many")
        String many() {
            throw new IncorrectResultSizeDataAccessException(1, 2);
        }
    }

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ProbeController())
            .setControllerAdvice(new GlobalExceptionHandler(mock(NamedParameterJdbcTemplate.class)))
            .build();

    @Test
    void emptySingleRowLookupIsNotFoundWithoutLeakingDetail() throws Exception {
        mvc.perform(get("/empty"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("El recurso solicitado no fue encontrado."));
    }

    @Test
    void ambiguousSingleRowLookupStaysServerError() throws Exception {
        mvc.perform(get("/many")).andExpect(status().isInternalServerError());
    }
}
