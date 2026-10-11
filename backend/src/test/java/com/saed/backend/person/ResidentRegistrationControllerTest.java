package com.saed.backend.person;

import com.saed.backend.person.controller.ResidentRegistrationController;
import com.saed.backend.person.dto.ResidentRegistrationResult;
import com.saed.backend.person.service.ResidentRegistrationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The integrated registration endpoint validates its body and answers 201 with the created identifiers. */
class ResidentRegistrationControllerTest {

    private final ResidentRegistrationService service = mock(ResidentRegistrationService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ResidentRegistrationController(service)).build();

    @Test
    void aValidRequestIsCreated() throws Exception {
        when(service.register(any())).thenReturn(new ResidentRegistrationResult(500L, null, null, 900L, null, null, null));

        mvc.perform(post("/api/v1/residentes/registro-integral").contentType(MediaType.APPLICATION_JSON).content(
                        "{\"unitId\":100,\"tipoRelacion\":\"FAMILIAR\",\"person\":{\"tipoDocumentoId\":1,"
                                + "\"numeroDocumento\":\"111\",\"tipoPersona\":\"NATURAL\",\"primerNombre\":\"Ana\"}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.personId").value(500))
                .andExpect(jsonPath("$.data.residentLinkId").value(900));
    }

    @Test
    void aBodyWithoutPersonOrUnitIsRejectedBeforeTheService() throws Exception {
        mvc.perform(post("/api/v1/residentes/registro-integral").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipoRelacion\":\"FAMILIAR\"}"))
                .andExpect(status().isBadRequest());
        verify(service, never()).register(any());
    }
}
