package com.saed.backend.finanzas;

import com.saed.backend.common.service.EmailService;
import com.saed.backend.common.service.FileStorageService;
import com.saed.backend.common.service.PdfService;
import com.saed.backend.common.service.TemplateRenderService;
import com.saed.backend.common.service.VariableResolverService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.finanzas.dto.ContratoRequestDTO;
import com.saed.backend.finanzas.dto.CuotaDTO;
import com.saed.backend.finanzas.dto.PagoRequestDTO;
import com.saed.backend.finanzas.repository.FinanzasRepository;
import com.saed.backend.finanzas.service.impl.FinanzasServiceImpl;
import com.saed.backend.person.service.ConvivienteQuotaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * F6 / F05: contract creation runs entirely in the caller's database context (no SUPERADMIN bootstrap), derives
 * its scope from the server context and lets failures propagate. Excess payments are rejected. No database.
 */
class ContractCreationScopeTest {
    private static final long UNIT = 1000L;
    private static final long PROPERTY = 100L;
    private static final long ORG = 10L;

    private NamedParameterJdbcTemplate jdbc;
    private JdbcOperations ops;
    private FinanzasRepository repository;
    private FinanzasServiceImpl service;
    private final List<String> rolesSeenByQueries = new ArrayList<>();

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        ops = mock(JdbcOperations.class);
        when(jdbc.getJdbcOperations()).thenReturn(ops);
        repository = mock(FinanzasRepository.class);
        service = new FinanzasServiceImpl(repository, mock(PdfService.class), mock(EmailService.class),
                mock(TemplateRenderService.class), mock(VariableResolverService.class),
                mock(FileStorageService.class), jdbc, mock(ConvivienteQuotaService.class));
        SaedContextHolder.setContext(SaedContext.builder().userId(20L).organizationId(ORG)
                .propertyId(PROPERTY).roleCode("ADMIN_PROPIEDAD").roleScope("PROPIEDAD").build());
    }

    @AfterEach
    void tearDown() {
        SaedContextHolder.clearContext();
    }

    private static ContratoRequestDTO request() {
        return new ContratoRequestDTO(UNIT, 55L, LocalDate.of(2099, 1, 1), null, "ARRENDAMIENTO",
                new BigDecimal("1000000"));
    }

    private void unitIn(long property, long org) {
        when(jdbc.queryForList(contains("FROM UNIDADES"), any(SqlParameterSource.class))).thenAnswer(inv -> {
            rolesSeenByQueries.add(SaedContextHolder.getContext().getRoleCode());
            return List.<Map<String, Object>>of(Map.of("ID_PROPIEDAD", property, "ID_ORGANIZACION", org));
        });
    }

    @Test
    void creationNeverElevatesTheDatabaseContext() {
        unitIn(PROPERTY, ORG);
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), org.mockito.ArgumentMatchers.<Class<Integer>>any()))
                .thenAnswer(inv -> {
                    rolesSeenByQueries.add(SaedContextHolder.getContext().getRoleCode());
                    return 0;
                });

        // no contract template configured and the person does not exist: the call stops with a 404
        assertThrows(NoSuchElementException.class, () -> service.createContrato(request()));

        assertTrue(rolesSeenByQueries.stream().allMatch("ADMIN_PROPIEDAD"::equals), rolesSeenByQueries.toString());
        assertEquals("ADMIN_PROPIEDAD", SaedContextHolder.getContext().getRoleCode());
        verify(ops, never()).execute(anyString());
    }

    @Test
    void aUnitOfAnotherPropertyIsIndistinguishableFromAMissingUnit() {
        unitIn(PROPERTY + 1, ORG);
        assertThrows(NoSuchElementException.class, () -> service.createContrato(request()));
        verifyNoInteractions(repository);
    }

    @Test
    void aUnitOfAnotherOrganizationIsNotFoundForAnOrganizationAdmin() {
        SaedContextHolder.setContext(SaedContext.builder().userId(20L).organizationId(ORG)
                .roleCode("ADMIN_ORGANIZACION").roleScope("ORGANIZACION").build());
        unitIn(PROPERTY, ORG + 1);
        assertThrows(NoSuchElementException.class, () -> service.createContrato(request()));
        verifyNoInteractions(repository);
    }

    @Test
    void aCallerWithoutContextCannotCreateContracts() {
        SaedContextHolder.clearContext();
        assertThrows(AccessDeniedException.class, () -> service.createContrato(request()));
        verifyNoInteractions(repository);
    }

    @Test
    void aDatabaseFailureWhileResolvingTheUnitPropagates() {
        when(jdbc.queryForList(contains("FROM UNIDADES"), any(SqlParameterSource.class)))
                .thenThrow(new DataAccessResourceFailureException("db down"));
        assertThrows(DataAccessResourceFailureException.class, () -> service.createContrato(request()));
        verifyNoInteractions(repository);
    }

    @Test
    @SuppressWarnings("unchecked")
    void aPaymentLargerThanTheBalanceIsRejectedAndNothingIsWritten() {
        SaedContextHolder.setContext(SaedContext.builder().userId(20L).organizationId(ORG)
                .propertyId(PROPERTY).unitId(UNIT).roleCode("RESIDENTE").roleScope("UNIDAD").build());
        CuotaDTO fee = new CuotaDTO(55L, UNIT, "101", "Synthetic", 7L, "ADMINISTRACION", "2099-01",
                new BigDecimal("100.00"), new BigDecimal("100.00"), new BigDecimal("100.00"),
                LocalDate.of(2099, 1, 31), "PENDIENTE");
        doReturn(List.of(fee)).when(jdbc).query(anyString(), any(SqlParameterSource.class), any(RowMapper.class));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.registrarPago(
                new PagoRequestDTO(55L, LocalDate.of(2099, 1, 10), new BigDecimal("100.01"), "TRANSFERENCIA", "R")));
        assertTrue(ex.getMessage().toLowerCase().contains("saldo"));
        verifyNoInteractions(repository);
    }

    @Test
    @SuppressWarnings("unchecked")
    void aFeeWithoutAKnownBalanceRejectsPaymentsInsteadOfAcceptingAnyAmount() {
        SaedContextHolder.setContext(SaedContext.builder().userId(20L).organizationId(ORG)
                .propertyId(PROPERTY).unitId(UNIT).roleCode("RESIDENTE").roleScope("UNIDAD").build());
        CuotaDTO fee = new CuotaDTO(55L, UNIT, "101", "Synthetic", 7L, "ADMINISTRACION", "2099-01",
                new BigDecimal("100.00"), new BigDecimal("100.00"), null,
                LocalDate.of(2099, 1, 31), "PENDIENTE");
        doReturn(List.of(fee)).when(jdbc).query(anyString(), any(SqlParameterSource.class), any(RowMapper.class));

        assertThrows(IllegalStateException.class, () -> service.registrarPago(
                new PagoRequestDTO(55L, LocalDate.of(2099, 1, 10), new BigDecimal("500.00"), "TRANSFERENCIA", "R")));
        verifyNoInteractions(repository);
    }
}
