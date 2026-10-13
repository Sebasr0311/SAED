package com.saed.backend.common.exception;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * A request without a required query parameter is a client error. It used to fall through to the generic handler
 * and answer 500 (eight GET endpoints, e.g. /pagos/wompi/estado and /reglamentos/vigente).
 */
class MissingRequestParameterTest {

    @RestController
    static class ProbeController {
        @GetMapping("/probe")
        String probe(@RequestParam("referencia") String referencia) {
            return referencia;
        }
    }

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ProbeController())
            .setControllerAdvice(new GlobalExceptionHandler(mock(NamedParameterJdbcTemplate.class)))
            .build();

    @Test
    void missingRequiredParameterIsBadRequest() throws Exception {
        mvc.perform(get("/probe"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_PARAMETER"));
    }
}
