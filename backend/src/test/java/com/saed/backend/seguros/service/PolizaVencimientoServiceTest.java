package com.saed.backend.seguros.service;

import com.saed.backend.common.service.EmailService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.seguros.dto.PolizaSeguroDTO;
import com.saed.backend.seguros.repository.PolizaSeguroRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tenant-scoped, idempotent insurance expiry detection (audit F10-08; plan F10-04). Pure Mockito.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PolizaVencimientoServiceTest {

    private static final Long ORG_ID = 40L;
    private static final Long PROPERTY_ID = 30L;
    private static final Long ADMIN_USER = 60L;
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 9);

    @Mock
    private PolizaSeguroRepository repository;
    @Mock
    private EmailService emailService;

    private PolizaVencimientoService service;

    @BeforeEach
    void setUp() {
        service = new PolizaVencimientoService(repository, emailService);
        when(repository.findEmailsAdministradores(PROPERTY_ID)).thenReturn(List.of("admin@example.test"));
    }

    @AfterEach
    void tearDown() {
        SaedContextHolder.clearContext();
    }

    @Test
    void detectionWithoutTenantContextIsRefused() {
        assertThrows(IllegalStateException.class, () -> service.detectarVencimientos(PROPERTY_ID, TODAY));

        verifyNoInteractions(repository);
    }

    @Test
    void detectionForAPropertyOtherThanTheContextIsRefused() {
        loginForProperty(PROPERTY_ID + 1);

        assertThrows(IllegalStateException.class, () -> service.detectarVencimientos(PROPERTY_ID, TODAY));

        verifyNoInteractions(repository);
    }

    @Test
    void oneAlertIsRecordedPerPolicyAndExpiryPeriod() {
        loginForProperty(PROPERTY_ID);
        when(repository.findPorVencerOVencidasByPropiedad(PROPERTY_ID))
                .thenReturn(List.of(policy(1L, TODAY.plusDays(10)), policy(2L, TODAY.minusDays(2))));
        when(repository.registrarAlertaVencimiento(eq(PROPERTY_ID), anyLong(), anyString(), anyString())).thenReturn(true);

        assertEquals(2, service.detectarVencimientos(PROPERTY_ID, TODAY));

        verify(repository).actualizarEstadosVencimiento(PROPERTY_ID);
        verify(repository).registrarAlertaVencimiento(eq(PROPERTY_ID), eq(1L), eq("2026-10-19"), anyString());
        verify(repository).registrarAlertaVencimiento(eq(PROPERTY_ID), eq(2L), eq("2026-10-07"), anyString());
    }

    @Test
    void repeatedRunForTheSamePeriodRecordsAndSendsNothing() {
        loginForProperty(PROPERTY_ID);
        when(repository.findPorVencerOVencidasByPropiedad(PROPERTY_ID)).thenReturn(List.of(policy(1L, TODAY.plusDays(10))));
        when(repository.registrarAlertaVencimiento(eq(PROPERTY_ID), anyLong(), anyString(), anyString())).thenReturn(false);

        assertEquals(0, service.detectarVencimientos(PROPERTY_ID, TODAY));

        verifyNoInteractions(emailService);
    }

    @Test
    void schedulerRunsEachTenantInItsOwnScopeAndClearsTheContext() {
        List<Long> seenProperties = new ArrayList<>();
        PolizaVencimientoService recording = new PolizaVencimientoService(repository, emailService) {
            @Override
            public int detectarVencimientos(Long idPropiedad, LocalDate hoy) {
                SaedContext ctx = SaedContextHolder.getContext();
                assertEquals(idPropiedad, ctx.getPropertyId());
                seenProperties.add(idPropiedad);
                if (idPropiedad.equals(31L)) {
                    throw new IllegalStateException("simulated failure for one tenant");
                }
                return 1;
            }
        };
        PolizaTenantSource source = () -> List.of(
                new PolizaTenantSource.TenantScope(ORG_ID, 31L, ADMIN_USER),
                new PolizaTenantSource.TenantScope(ORG_ID, PROPERTY_ID, ADMIN_USER));
        PolizaVencimientoScheduler scheduler = new PolizaVencimientoScheduler(recording, Optional.of(source));

        assertEquals(1, scheduler.verificarYAlertarVencimientos());

        assertEquals(List.of(31L, PROPERTY_ID), seenProperties);
        assertNull(SaedContextHolder.getContext());
    }

    @Test
    void schedulerWithoutTenantSourceDoesNotRunGlobally() {
        PolizaVencimientoScheduler scheduler = new PolizaVencimientoScheduler(service, Optional.empty());

        assertEquals(0, scheduler.verificarYAlertarVencimientos());

        verifyNoInteractions(repository);
    }

    @Test
    void emailFailureDoesNotUndoTheRecordedAlert() throws Exception {
        loginForProperty(PROPERTY_ID);
        when(repository.findPorVencerOVencidasByPropiedad(PROPERTY_ID)).thenReturn(List.of(policy(1L, TODAY.plusDays(3))));
        when(repository.registrarAlertaVencimiento(eq(PROPERTY_ID), anyLong(), anyString(), anyString())).thenReturn(true);
        doThrow(new IllegalStateException("smtp down")).when(emailService).enviarHtmlPublico(anyString(), anyString(), anyString());

        assertEquals(1, service.detectarVencimientos(PROPERTY_ID, TODAY));
        verify(repository, never()).findPorVencerOVencidasByPropiedad(PROPERTY_ID + 1);
        verify(emailService).enviarHtmlPublico(eq("admin@example.test"), anyString(), any());
    }

    private void loginForProperty(Long propertyId) {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(ADMIN_USER)
                .organizationId(ORG_ID)
                .propertyId(propertyId)
                .roleCode("ADMIN_PROPIEDAD")
                .build());
    }

    private PolizaSeguroDTO policy(Long id, LocalDate end) {
        PolizaSeguroDTO dto = new PolizaSeguroDTO();
        dto.setIdPoliza(id);
        dto.setIdPropiedad(PROPERTY_ID);
        dto.setNumeroPoliza("POL-" + id);
        dto.setCompaniaAseguradora("Insurer");
        dto.setRamoCobertura("TODO_RIESGO");
        dto.setFechaFin(end);
        return dto;
    }
}
