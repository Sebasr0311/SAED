package com.saed.backend.paquetes;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.saed.backend.audit.AuditService;
import com.saed.backend.common.service.FileStorageService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.paquetes.dto.PaqueteDTO;
import com.saed.backend.paquetes.dto.PaqueteEntregaDTO;
import com.saed.backend.paquetes.dto.PaqueteRequestDTO;
import com.saed.backend.paquetes.repository.PaquetesRepository;
import com.saed.backend.paquetes.service.impl.PaquetesServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.security.access.AccessDeniedException;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** getPaquetesByUnidad staff scope, DEC-02, and F08-02 custody (atomic PIN attempts, no PIN in logs, re-notify). */
class PackageScopeAndCustodyTest {

    private static final long PROP = 3L;
    private static final long UNIT = 101L;
    private static final long PKG = 55L;
    private static final String PIN = "4821";

    private PaquetesRepository repo;
    private NamedParameterJdbcTemplate jdbc;
    private PaquetesServiceImpl service;
    private final List<String> sqlSeen = new ArrayList<>();
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        repo = mock(PaquetesRepository.class);
        jdbc = mock(NamedParameterJdbcTemplate.class);
        service = new PaquetesServiceImpl(repo, jdbc, mock(FileStorageService.class), mock(AuditService.class));
        when(repo.getPaqueteById(PKG)).thenReturn(Optional.of(paquete(PROP, UNIT, "RECIBIDO")));
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Integer.class))).thenAnswer(inv -> {
            sqlSeen.add(inv.getArgument(0));
            return 0;
        });
        when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class))).thenAnswer(inv -> {
            sqlSeen.add(inv.getArgument(0));
            return new ArrayList<>(List.of(31L, 32L));
        });
        logs = new ListAppender<>();
        logs.start();
        ((Logger) LoggerFactory.getLogger(PaquetesServiceImpl.class)).addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger(PaquetesServiceImpl.class)).detachAppender(logs);
        SaedContextHolder.clearContext();
    }

    private static PaqueteDTO paquete(long prop, long unit, String estado) {
        return new PaqueteDTO(PKG, prop, 1L, unit, "101", 900L, "Laura", "Servientrega", "GUIA-1", "Caja",
            "MEDIANO", null, PIN, ZonedDateTime.now(), "Portero", null, null, null, null, null, estado);
    }

    private static void portero() {
        SaedContextHolder.setContext(SaedContext.builder().userId(7L).roleCode("PORTERO")
            .roleScope("PROPIEDAD").propertyId(PROP).build());
    }

    @Test
    void staffCannotReadPackagesOfAUnitOutsideTheirProperty() {
        portero(); // COUNT(1) of the unit in property 3 answers 0
        assertThrows(AccessDeniedException.class, () -> service.getPaquetesByUnidad(999L));
        verify(repo, never()).getPaquetesByUnidad(anyLong());
    }

    @Test
    @SuppressWarnings("unchecked")
    void staffReadsPackagesOfAUnitOfTheirProperty() {
        portero();
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Integer.class))).thenReturn(1);
        when(repo.getPaquetesByUnidad(UNIT)).thenReturn(List.of(paquete(PROP, UNIT, "RECIBIDO")));
        assertEquals(1, service.getPaquetesByUnidad(UNIT).size());
    }

    @Test
    void staffPackageListOnlyContainsTheirProperty() {
        portero();
        when(repo.getPaquetesList()).thenReturn(List.of(paquete(PROP, UNIT, "RECIBIDO"), paquete(8L, 808L, "RECIBIDO")));
        List<PaqueteDTO> list = service.getPaquetes();
        assertEquals(1, list.size());
        assertEquals(PROP, list.get(0).idPropiedad());
    }

    @Test
    void missingContextIsDenied() {
        assertThrows(AccessDeniedException.class, () -> service.getPaquetes());
        assertThrows(AccessDeniedException.class, () -> service.getPaquetesByUnidad(UNIT));
        assertThrows(AccessDeniedException.class, () -> service.registrarPaquete(
            new PaqueteRequestDTO(UNIT, null, "S", "G", "Caja", "MEDIANO", null, 1L)));
    }

    @Test
    void superadminDoesNotReadPackages() {
        SaedContextHolder.setContext(SaedContext.builder().userId(1L).roleCode("SUPERADMIN")
            .roleScope("GLOBAL").propertyId(PROP).build());
        assertThrows(AccessDeniedException.class, () -> service.getPaquetes());
        assertThrows(AccessDeniedException.class, () -> service.getPaqueteById(PKG));
    }

    @Test
    void lockedPackageRejectsTheAttemptBeforeComparingThePin() {
        portero();
        when(repo.reservarIntentoPin(PKG, 3)).thenReturn(false);
        assertThrows(SecurityException.class,
            () -> service.registrarEntrega(PKG, new PaqueteEntregaDTO(PIN, 900L, 1L, null)));
        verify(repo, never()).registrarEntrega(anyLong(), any(), anyLong());
    }

    @Test
    void everyAttemptIsReservedAtomicallyBeforeTheComparison() {
        portero();
        when(repo.reservarIntentoPin(PKG, 3)).thenReturn(true);
        assertThrows(IllegalArgumentException.class,
            () -> service.registrarEntrega(PKG, new PaqueteEntregaDTO("0000", 900L, 1L, null)));
        verify(repo).reservarIntentoPin(PKG, 3);
        verify(repo, never()).registrarEntrega(anyLong(), any(), anyLong());
    }

    @Test
    @SuppressWarnings("unchecked")
    void failedNotificationDoesNotLogThePinOrTheDatabaseMessage() {
        portero();
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Integer.class))).thenReturn(1);
        when(repo.registrarPaquete(any(), anyLong(), anyString(), anyLong())).thenReturn(paquete(PROP, UNIT, "RECIBIDO"));
        when(jdbc.update(anyString(), any(SqlParameterSource.class)))
            .thenThrow(new DataAccessResourceFailureException("ORA-28115 values " + PIN));

        service.registrarPaquete(new PaqueteRequestDTO(UNIT, null, "S", "G", "Caja", "MEDIANO", null, 1L));

        assertFalse(logs.list.isEmpty(), "a failed notification must be visible in the log");
        for (ILoggingEvent e : logs.list) {
            assertFalse(e.getFormattedMessage().contains(PIN), e.getFormattedMessage());
            assertFalse(e.getFormattedMessage().contains("ORA-"), e.getFormattedMessage());
        }
    }

    @Test
    void notificationNeverBorrowsACommunicationOfAnotherProperty() {
        portero();
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Integer.class))).thenReturn(1);
        when(repo.registrarPaquete(any(), anyLong(), anyString(), anyLong())).thenReturn(paquete(PROP, UNIT, "RECIBIDO"));
        service.registrarPaquete(new PaqueteRequestDTO(UNIT, null, "S", "G", "Caja", "MEDIANO", null, 1L));
        assertTrue(sqlSeen.stream().noneMatch(s -> s.replaceAll("\\s+", " ").trim()
            .equals("SELECT MIN(ID_COMUNICADO) FROM COMUNICADOS")), sqlSeen.toString());
    }

    @Test
    void staffCanResendAMissingNotificationForAnUndeliveredPackage() {
        portero();
        when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(1);
        assertEquals(2, service.reenviarNotificacion(PKG));
    }

    @Test
    void resendEndpointReportsHowManyResidentsWereNotified() {
        portero();
        when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(1);
        var res = new com.saed.backend.paquetes.controller.PaquetesController(service).reenviarNotificacion(PKG);
        assertEquals(2, res.getBody().get("notificados"));
    }

    @Test
    void resendIsRefusedForADeliveredPackageOrAnotherProperty() {
        portero();
        when(repo.getPaqueteById(PKG)).thenReturn(Optional.of(paquete(PROP, UNIT, "ENTREGADO")));
        assertThrows(IllegalStateException.class, () -> service.reenviarNotificacion(PKG));
        when(repo.getPaqueteById(PKG)).thenReturn(Optional.of(paquete(8L, UNIT, "RECIBIDO")));
        assertThrows(AccessDeniedException.class, () -> service.reenviarNotificacion(PKG));
        verify(jdbc, never()).update(anyString(), any(SqlParameterSource.class));
    }
}
