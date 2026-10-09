package com.saed.backend.authorization.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.saed.backend.audit.AuditService;
import com.saed.backend.authorization.dto.PropertyDTO;
import com.saed.backend.authorization.dto.PropertyDeletionDTOs;
import com.saed.backend.authorization.exception.PropertyHasHistoryException;
import com.saed.backend.authorization.repository.PropertyRepository;
import com.saed.backend.common.service.EmailService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.identity.repository.TokenActivacionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PropertyDeletionServiceTest {

    private static final Long ORG_ID = 5L;
    private static final Long PROPERTY_ID = 77L;
    private static final Long USER_ID = 9L;
    private static final String RAW_OTP = "493817";

    @Mock private PropertyRepository propertyRepository;
    @Mock private PropertyDeletionChallengeService challengeService;
    @Mock private EmailService emailService;
    @Mock private AuditService auditService;
    @Mock private TokenActivacionRepository tokenActivacionRepository;
    @Mock private PropertyStatusService propertyStatusService;

    @InjectMocks
    private PropertyDeletionService service;

    @BeforeEach
    void setUp() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(USER_ID).roleCode("ADMIN_ORGANIZACION").roleScope("ORGANIZACION")
                .organizationId(ORG_ID).build());
        PropertyDTO property = new PropertyDTO();
        property.setId(PROPERTY_ID);
        property.setIdOrganizacion(ORG_ID);
        property.setNombre("Torres del Parque");
        property.setEstado("ACTIVA");
        when(propertyRepository.findById(PROPERTY_ID)).thenReturn(Optional.of(property));
    }

    @AfterEach
    void tearDown() {
        SaedContextHolder.clearContext();
    }

    private PropertyDeletionDTOs.ConfirmResponse confirm() {
        return service.confirmAndExecuteDeletion(PROPERTY_ID,
                new PropertyDeletionDTOs.ConfirmRequest("challenge-1", true), "127.0.0.1", "JUnit");
    }

    @Test
    void confirmDeletion_withFinancialHistory_isRefusedAsConflict_andNothingIsDeleted() {
        when(propertyRepository.findProtectedHistory(PROPERTY_ID)).thenReturn(List.of("CUOTAS", "PAGOS"));

        PropertyHasHistoryException ex = assertThrows(PropertyHasHistoryException.class, this::confirm);

        // Mapped to 409 CONFLICT by GlobalExceptionHandler (IllegalStateException handler)
        assertTrue(ex instanceof IllegalStateException);
        assertTrue(ex.getMessage().contains("INACTIVA"), "Message must point to deactivation as the supported path");
        verify(propertyRepository, never()).deletePropertyCascade(anyLong(), anyLong());
        verify(challengeService, never()).consumeChallengeForFinalExecution(anyString(), anyLong(), anyLong(), anyLong());
    }

    @Test
    void requestDeletion_withLegalHistory_isRefusedBeforeAnyOtpIsIssued() throws Exception {
        when(propertyRepository.findProtectedHistory(PROPERTY_ID)).thenReturn(List.of("CONTRATOS"));

        assertThrows(PropertyHasHistoryException.class,
                () -> service.requestDeletion(PROPERTY_ID, "127.0.0.1", "JUnit"));

        verify(challengeService, never()).createChallenge(anyLong(), anyLong(), anyLong(), anyString(), anyString());
        verify(emailService, never()).enviarCodigoOtpEliminacion(anyString(), anyString(), anyString(), anyInt());
    }

    @Test
    void confirmDeletion_withoutHistory_deletesTheProperty() {
        when(propertyRepository.findProtectedHistory(PROPERTY_ID)).thenReturn(List.of());
        when(propertyRepository.deletePropertyCascade(PROPERTY_ID, ORG_ID)).thenReturn(true);

        PropertyDeletionDTOs.ConfirmResponse response = confirm();

        assertTrue(response.success());
        verify(challengeService).consumeChallengeForFinalExecution("challenge-1", PROPERTY_ID, ORG_ID, USER_ID);
        verify(propertyRepository).deletePropertyCascade(PROPERTY_ID, ORG_ID);
    }

    @Test
    void confirmDeletion_whenCascadeFails_propagatesTheError() {
        when(propertyRepository.findProtectedHistory(PROPERTY_ID)).thenReturn(List.of());
        when(propertyRepository.deletePropertyCascade(PROPERTY_ID, ORG_ID))
                .thenThrow(new DataIntegrityViolationException("ORA-02292: child record found"));

        assertThrows(DataIntegrityViolationException.class, this::confirm);
        verify(propertyStatusService, never()).updateCache(eq(PROPERTY_ID), anyString());
    }

    @Test
    void confirmDeletion_isTransactionalAndRollsBackOnAnyException() throws Exception {
        Method method = PropertyDeletionService.class.getMethod("confirmAndExecuteDeletion",
                Long.class, PropertyDeletionDTOs.ConfirmRequest.class, String.class, String.class);
        Transactional tx = method.getAnnotation(Transactional.class);
        assertNotNull(tx, "History check and cascade delete must run in one transaction");
        assertEquals(List.of(Exception.class), List.of(tx.rollbackFor()));
    }

    @Test
    void requestDeletion_whenEmailFails_neverLogsTheOtp() throws Exception {
        when(propertyRepository.findProtectedHistory(PROPERTY_ID)).thenReturn(List.of());
        when(tokenActivacionRepository.obtenerUsuarioInfo(USER_ID)).thenReturn(Optional.of(
                new TokenActivacionRepository.UsuarioInfo(USER_ID, "admin", "admin@saed.test", "Ana", "ACTIVO")));
        when(challengeService.createChallenge(any(), any(), any(), any(), any())).thenReturn(
                new PropertyDeletionChallengeService.GeneratedChallenge("challenge-1", RAW_OTP, 300, null));
        doThrow(new RuntimeException("Brevo down")).when(emailService)
                .enviarCodigoOtpEliminacion(anyString(), anyString(), anyString(), anyInt());

        Logger logger = (Logger) LoggerFactory.getLogger(PropertyDeletionService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            service.requestDeletion(PROPERTY_ID, "127.0.0.1", "JUnit");
        } finally {
            logger.detachAppender(appender);
        }

        assertFalse(appender.list.isEmpty(), "Email failure should still be logged");
        for (ILoggingEvent event : appender.list) {
            assertFalse(event.getFormattedMessage().contains(RAW_OTP), "OTP must never be logged");
        }
    }
}
