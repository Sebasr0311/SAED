package com.saed.backend.audit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CorrelationIdFilterTest {

    private CorrelationIdFilter filter;

    @BeforeEach
    void setUp() {
        filter = new CorrelationIdFilter();
        CorrelationIdHolder.clear();
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        CorrelationIdHolder.clear();
        MDC.clear();
    }

    @Test
    @DisplayName("Request without correlation ID generates UUID and cleans up ThreadLocal")
    void testRequestWithoutCorrelationIdGeneratesUuid() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> capturedInChain = new AtomicReference<>();
        AtomicReference<String> capturedMdc = new AtomicReference<>();

        FilterChain chain = (req, res) -> {
            capturedInChain.set(CorrelationIdHolder.get());
            capturedMdc.set(MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID_KEY));
        };

        filter.doFilter(request, response, chain);

        assertThat(capturedInChain.get()).isNotNull().isNotBlank();
        assertThat(capturedMdc.get()).isEqualTo(capturedInChain.get());
        assertThat(response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).isEqualTo(capturedInChain.get());

        // Must be cleanly wiped after request completes
        assertThat(CorrelationIdHolder.get()).isNull();
        assertThat(MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID_KEY)).isNull();
    }

    @Test
    @DisplayName("Request with valid correlation ID preserves value")
    void testRequestWithValidCorrelationIdPreservesValue() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "req-audit-12345");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> capturedInChain = new AtomicReference<>();

        FilterChain chain = (req, res) -> capturedInChain.set(CorrelationIdHolder.get());

        filter.doFilter(request, response, chain);

        assertThat(capturedInChain.get()).isEqualTo("req-audit-12345");
        assertThat(response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).isEqualTo("req-audit-12345");
        assertThat(CorrelationIdHolder.get()).isNull();
    }

    @Test
    @DisplayName("Request with invalid/dangerous correlation ID replaces with safe UUID")
    void testRequestWithDangerousCorrelationIdReplacesWithUuid() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "<script>alert(1)</script>; DROP TABLE--");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> capturedInChain = new AtomicReference<>();

        FilterChain chain = (req, res) -> capturedInChain.set(CorrelationIdHolder.get());

        filter.doFilter(request, response, chain);

        assertThat(capturedInChain.get()).isNotEqualTo("<script>alert(1)</script>; DROP TABLE--");
        assertThat(capturedInChain.get()).matches("^[a-f0-9\\-]{36}$");
        assertThat(CorrelationIdHolder.get()).isNull();
    }

    @Test
    @DisplayName("Exception during request execution still cleans up ThreadLocal and MDC")
    void testExceptionDuringRequestCleansUpThreadLocal() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (req, res) -> {
            throw new RuntimeException("Simulated failure in filter chain");
        };

        assertThrows(RuntimeException.class, () -> filter.doFilter(request, response, chain));

        assertThat(CorrelationIdHolder.get()).isNull();
        assertThat(MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID_KEY)).isNull();
    }
}
