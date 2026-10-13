package com.saed.backend.identity.controller;

import com.saed.backend.identity.service.TokenActivacionService;
import com.saed.backend.platform.service.OnboardingRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** The anonymous activation-link resend endpoint is rate limited per client address and per target account. */
class ActivationResendRateLimitTest {

    private TokenActivacionService tokens;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        tokens = mock(TokenActivacionService.class);
        mvc = MockMvcBuilders.standaloneSetup(new ActivacionCuentaController(tokens, new OnboardingRateLimiter())).build();
    }

    private int resend(String ip, String identifier) throws Exception {
        return mvc.perform(post("/api/v1/auth/activar/solicitar-reenvio").contentType(MediaType.APPLICATION_JSON)
                        .with(request -> {
                            request.setRemoteAddr(ip);
                            return request;
                        })
                        .content("{\"identificador\":\"" + identifier + "\"}"))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void theSameTargetAccountIsLimitedAcrossAddressesAndCase() throws Exception {
        assertEquals(200, resend("203.0.113.1", "Carlos.Admin"));
        assertEquals(200, resend("203.0.113.2", "carlos.admin"));
        assertEquals(200, resend("203.0.113.3", " CARLOS.ADMIN "));
        assertEquals(429, resend("203.0.113.4", "carlos.admin"));
        verify(tokens, times(3)).solicitarReenvio(anyString(), anyString());
    }

    @Test
    void theSameClientAddressIsLimitedAcrossAccounts() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertEquals(200, resend("203.0.113.9", "user" + i));
        }
        assertEquals(429, resend("203.0.113.9", "user-late"));
        assertEquals(200, resend("203.0.113.10", "user-other"));
    }
}
