package com.saed.backend.platform;

import com.saed.backend.common.ratelimit.SlidingWindowRateLimiter;
import com.saed.backend.platform.controller.PublicOnboardingController;
import com.saed.backend.platform.service.OnboardingRateLimiter;
import com.saed.backend.platform.service.OnboardingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** F3-04: the public onboarding endpoints are rate limited per client address and per tax id / e-mail. */
class OnboardingRateLimitTest {

    private OnboardingService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(OnboardingService.class);
        when(service.registrar(any())).thenReturn(Map.of("ok", true));
        when(service.consultarEstadoPago(anyString())).thenReturn(Map.of());
        mvc = MockMvcBuilders.standaloneSetup(new PublicOnboardingController(service, new OnboardingRateLimiter())).build();
    }

    private static String body(String taxId, String email) {
        return "{\"nombreOrganizacion\":\"Torres\",\"identificacionFiscal\":\"" + taxId + "\",\"emailOrganizacion\":\"" + email
                + "\",\"primerNombre\":\"Ana\",\"primerApellido\":\"Paz\",\"numeroDocumento\":\"123\",\"emailAdmin\":\"a@b.co\",\"idPlan\":1}";
    }

    private int register(String ip, String taxId, String email) throws Exception {
        return mvc.perform(post("/api/v1/auth/onboarding/registro").contentType(MediaType.APPLICATION_JSON)
                        .with(request -> {
                            request.setRemoteAddr(ip);
                            return request;
                        })
                        .content(body(taxId, email)))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void theSameClientAddressIsLimited() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertEquals(201, register("203.0.113.5", "9000000" + i + "-1", "o" + i + "@x.co"));
        }
        assertEquals(429, register("203.0.113.5", "900999999-1", "late@x.co"));
        assertEquals(201, register("203.0.113.6", "900999998-1", "other@x.co"));
    }

    @Test
    void theSameTaxIdIsLimitedAcrossAddressesAndFormats() throws Exception {
        assertEquals(201, register("203.0.113.1", "900.123.456-7", "a1@x.co"));
        assertEquals(201, register("203.0.113.2", "9001234567", "a2@x.co"));
        assertEquals(201, register("203.0.113.3", "900123456-7", "a3@x.co"));
        assertEquals(429, register("203.0.113.4", "900-123-456-7", "a4@x.co"));
        verify(service, org.mockito.Mockito.times(3)).registrar(any());
    }

    @Test
    void theSameEmailIsLimitedCaseInsensitively() throws Exception {
        assertEquals(201, register("203.0.113.1", "1111111-1", "Same@X.co"));
        assertEquals(201, register("203.0.113.2", "2222222-1", "same@x.CO"));
        assertEquals(201, register("203.0.113.3", "3333333-1", "sAme@x.co"));
        assertEquals(429, register("203.0.113.4", "4444444-1", "SAME@x.co"));
    }

    @Test
    void aRejectedRegistrationNeverReachesTheService() throws Exception {
        for (int i = 0; i < 3; i++) {
            register("203.0.113.9", "555-5", "z@x.co");
        }
        assertEquals(429, register("203.0.113.9", "555-5", "z@x.co"));
        verify(service, org.mockito.Mockito.times(3)).registrar(any());
        verify(service, never()).consultarEstadoPago(anyString());
    }

    @Test
    void paymentStatusLookupsAreLimitedPerAddress() throws Exception {
        for (int i = 0; i < 60; i++) {
            mvc.perform(get("/api/v1/auth/onboarding/estado-pago").param("referencia", "R" + i)
                    .with(r -> {
                        r.setRemoteAddr("198.51.100.7");
                        return r;
                    })).andExpect(status().isOk());
        }
        mvc.perform(get("/api/v1/auth/onboarding/estado-pago").param("referencia", "R61")
                .with(r -> {
                    r.setRemoteAddr("198.51.100.7");
                    return r;
                })).andExpect(status().isTooManyRequests());
    }

    @Test
    void behindAProxyTheAddressAppendedByTheProxyIsUsed() throws Exception {
        // The client-controlled left part of X-Forwarded-For cannot be used to dodge the per-address limit.
        for (int i = 0; i < 10; i++) {
            int code = mvc.perform(post("/api/v1/auth/onboarding/registro").contentType(MediaType.APPLICATION_JSON)
                            .header("X-Forwarded-For", "10.9.9." + i + ", 203.0.113.77")
                            .with(r -> {
                                r.setRemoteAddr("10.0.0.2");
                                return r;
                            })
                            .content(body("77700" + i + "-1", "p" + i + "@x.co")))
                    .andReturn().getResponse().getStatus();
            assertEquals(201, code);
        }
        int blocked = mvc.perform(post("/api/v1/auth/onboarding/registro").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", "1.2.3.4, 203.0.113.77")
                        .with(r -> {
                            r.setRemoteAddr("10.0.0.2");
                            return r;
                        })
                        .content(body("7779999-1", "pz@x.co")))
                .andReturn().getResponse().getStatus();
        assertEquals(429, blocked);
    }

    // ---- limiter ----

    @Test
    void entriesExpireWithTheirWindow() {
        AtomicLong now = new AtomicLong(1_000);
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(2, Duration.ofSeconds(10), 100, now::get);
        assertTrue(limiter.tryAcquire("k"));
        assertTrue(limiter.tryAcquire("k"));
        assertFalse(limiter.tryAcquire("k"));
        now.addAndGet(10_000);
        assertTrue(limiter.tryAcquire("k"));
    }

    @Test
    void memoryStaysBoundedUnderADistinctKeyFlood() {
        AtomicLong now = new AtomicLong(1_000);
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(1, Duration.ofHours(1), 50, now::get);
        for (int i = 0; i < 5_000; i++) {
            limiter.tryAcquire("flood-" + i);
        }
        assertTrue(limiter.trackedKeys() <= 50, "tracked " + limiter.trackedKeys());
    }

    @Test
    void expiredKeysAreDroppedBeforeLiveOnesAreEvicted() {
        AtomicLong now = new AtomicLong(1_000);
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(1, Duration.ofSeconds(10), 3, now::get);
        limiter.tryAcquire("a");
        limiter.tryAcquire("b");
        now.addAndGet(11_000);
        limiter.tryAcquire("live");
        assertEquals(1, limiter.trackedKeys());
        assertFalse(limiter.tryAcquire("live"));
    }
}
