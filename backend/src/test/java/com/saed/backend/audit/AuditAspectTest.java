package com.saed.backend.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class AuditAspectTest {

    private AuditService auditService;
    private AuditSanitizer auditSanitizer;
    private AuditAspect aspect;

    @BeforeEach
    void setUp() {
        auditService = mock(AuditService.class);
        auditSanitizer = new AuditSanitizer(new ObjectMapper());
        aspect = new AuditAspect(auditService, auditSanitizer);

        SaedContext context = new SaedContext();
        context.setUserId(10L);
        context.setOrganizationId(1L);
        context.setPropertyId(2L);
        context.setRoleCode("ADMIN_PROPIEDAD");
        SaedContextHolder.setContext(context);
    }

    @AfterEach
    void tearDown() {
        SaedContextHolder.clearContext();
        CorrelationIdHolder.clear();
    }

    @Test
    @DisplayName("Successful method execution records audit log with EXITOSO")
    void testAuditMethodSuccess() throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        Auditable auditable = mock(Auditable.class);
        when(auditable.action()).thenReturn("CREATE");
        when(auditable.resource()).thenReturn("UNIDAD");
        when(auditable.includePayload()).thenReturn(true);

        when(joinPoint.getArgs()).thenReturn(new Object[]{Map.of("id", 101L, "numero", "A-101")});
        when(joinPoint.proceed()).thenReturn(Map.of("id", 101L, "status", "CREATED"));

        Object result = aspect.auditMethod(joinPoint, auditable);

        assertThat(result).isNotNull();
        verify(auditService, times(1)).recordSuccess(
                eq(10L), eq(1L), eq(2L),
                eq("CREATE"), eq("UNIDAD"), eq(101L),
                anyString(), any(),
                anyString(), anyString()
        );
        verify(auditService, never()).recordFailure(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Failed method execution records audit log with FALLIDO and rethrows original exception")
    void testAuditMethodFailureRethrowsException() throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        Auditable auditable = mock(Auditable.class);
        when(auditable.action()).thenReturn("DELETE");
        when(auditable.resource()).thenReturn("PERSONA");
        when(auditable.includePayload()).thenReturn(true);

        when(joinPoint.getArgs()).thenReturn(new Object[]{55L});
        when(joinPoint.proceed()).thenThrow(new IllegalArgumentException("Persona no encontrada"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> aspect.auditMethod(joinPoint, auditable));

        assertThat(ex.getMessage()).isEqualTo("Persona no encontrada");
        verify(auditService, times(1)).recordFailure(
                eq(10L), eq(1L), eq(2L),
                eq("DELETE"), eq("PERSONA"), eq(55L),
                anyString(), any(),
                anyString(), contains("Persona no encontrada")
        );
        verify(auditService, never()).recordSuccess(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }
}
