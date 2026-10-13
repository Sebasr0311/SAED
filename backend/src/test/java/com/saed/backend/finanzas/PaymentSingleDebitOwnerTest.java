package com.saed.backend.finanzas;

import com.saed.backend.common.service.EmailService;
import com.saed.backend.common.service.FileStorageService;
import com.saed.backend.common.service.PdfService;
import com.saed.backend.common.service.TemplateRenderService;
import com.saed.backend.common.service.VariableResolverService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.finanzas.dto.CuotaDTO;
import com.saed.backend.finanzas.dto.PagoRequestDTO;
import com.saed.backend.finanzas.dto.PagoResponseDTO;
import com.saed.backend.finanzas.repository.FinanzasRepository;
import com.saed.backend.finanzas.repository.impl.FinanzasRepositoryImpl;
import com.saed.backend.finanzas.service.impl.FinanzasServiceImpl;
import com.saed.backend.person.service.ConvivienteQuotaService;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.Invocation;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * F06-03: a payment must reduce a fee's balance exactly once. The single owner of the debit is the
 * V5.17 database trigger pair (TRG_APLICAR_PAGO_CUOTA on approved insert, TRG_PAGOS_APROBACION_ESTADO
 * on the PENDIENTE_APROBACION to APROBADO transition); Java code never debits CUOTAS.
 *
 * <p>Pure unit tests with mocks. They prove that Java stays out of the ledger and that approval is
 * guarded and single-shot; the trigger arithmetic itself is verified by the coordinator on Oracle.
 */
class PaymentSingleDebitOwnerTest {
    private static final long UNIT_ID = 1000L;
    private static final long FEE_ID = 55L;
    private static final long PAYMENT_ID = 900L;

    private NamedParameterJdbcTemplate jdbc;
    private FinanzasRepository repository;
    private EmailService email;
    private FinanzasServiceImpl service;
    private String paymentState;
    private String feeState;
    private BigDecimal feeBalance;
    private BigDecimal paymentAmount;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        repository = mock(FinanzasRepository.class);
        email = mock(EmailService.class);
        service = new FinanzasServiceImpl(repository, mock(PdfService.class), email,
                mock(TemplateRenderService.class), mock(VariableResolverService.class),
                mock(FileStorageService.class), jdbc, mock(ConvivienteQuotaService.class));
        paymentState = "PENDIENTE_APROBACION";
        feeState = "PENDIENTE";
        feeBalance = new BigDecimal("100.00");
        paymentAmount = new BigDecimal("30.00");

        when(jdbc.queryForList(anyString(), any(SqlParameterSource.class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("FROM PAGOS WHERE ID_PAGO")) {
                return List.of(Map.of("ID_PAGO", PAYMENT_ID, "ID_UNIDAD", UNIT_ID,
                        "MONTO_TOTAL", paymentAmount, "ESTADO", paymentState));
            }
            if (sql.contains("FROM PAGO_DETALLE")) {
                return List.of(Map.of("ID_CUOTA", FEE_ID, "MONTO_APLICADO", paymentAmount));
            }
            if (sql.contains("FROM CUOTAS") && sql.contains("FOR UPDATE")) {
                return List.of(Map.of("ID_CUOTA", FEE_ID, "SALDO_PENDIENTE", feeBalance, "ESTADO", feeState));
            }
            if (sql.contains("FROM PERSONAS P")) {
                return List.of(Map.of("EMAIL", "resident@example.invalid"));
            }
            return List.of();
        });
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Integer.class))).thenReturn(1);
        CuotaDTO fee = new CuotaDTO(FEE_ID, UNIT_ID, "101", "Synthetic Resident", 7L, "ADMINISTRACION",
                "2099-01", new BigDecimal("100.00"), new BigDecimal("100.00"), new BigDecimal("100.00"),
                LocalDate.of(2099, 1, 31), "PENDIENTE");
        doReturn(List.of(fee)).when(jdbc).query(anyString(), any(SqlParameterSource.class), any(RowMapper.class));
        when(repository.getPagoById(PAYMENT_ID)).thenReturn(approvedPayment());
        when(repository.registrarPago(any(PagoRequestDTO.class), eq(UNIT_ID), anyString(), any(), any()))
                .thenReturn(PAYMENT_ID);
        SaedContextHolder.setContext(SaedContext.builder().userId(20L).organizationId(10L)
                .propertyId(100L).roleCode("ADMIN_PROPIEDAD").roleScope("PROPIEDAD").build());
    }

    @AfterEach
    void tearDown() {
        SaedContextHolder.clearContext();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private PagoResponseDTO approvedPayment() {
        return new PagoResponseDTO(PAYMENT_ID, UNIT_ID, "101", "Synthetic Resident", FEE_ID, "ADMINISTRACION",
                "2099-01", paymentAmount, "TRANSFERENCIA", "REF-1", null, null, null,
                LocalDate.of(2099, 1, 10), "APROBADO", 20L, "admin", null, null, null, null, null);
    }

    private long invocationsNamed(Object mock, String methodName) {
        return mockingDetails(mock).getInvocations().stream()
                .map(Invocation::getMethod).filter(m -> m.getName().equals(methodName)).count();
    }

    @Test
    void approvalNeverDebitsTheFeeFromJava() {
        service.aprobarPago(PAYMENT_ID);

        assertEquals(0, invocationsNamed(repository, "actualizarSaldoCuota"),
                "the database trigger is the single owner of the debit");
        verify(jdbc, never()).update(contains("UPDATE CUOTAS"), any(SqlParameterSource.class));
        verify(repository, times(1)).aprobarPago(eq(PAYMENT_ID), eq(20L));
        verify(repository).recalcularCarteraUnidad(UNIT_ID);
    }

    @Test
    void gatewaySettlementNeverDebitsAndReturnsThePaymentIdForLinkage() {
        Long id = service.registrarPagoPasarelaVerificado(
                new PagoRequestDTO(FEE_ID, LocalDate.of(2099, 1, 10), paymentAmount, "PASARELA_WOMPI", "REF-W"));

        assertEquals(PAYMENT_ID, id);
        assertEquals(0, invocationsNamed(repository, "actualizarSaldoCuota"));
        verify(repository).registrarPago(any(PagoRequestDTO.class), eq(UNIT_ID), eq("APROBADO"), isNull(), isNull());
        verify(repository).recalcularCarteraUnidad(UNIT_ID);
    }

    @Test
    void gatewaySettlementSendsNoReceiptItself() throws Exception {
        service.registrarPagoPasarelaVerificado(
                new PagoRequestDTO(FEE_ID, LocalDate.of(2099, 1, 10), paymentAmount, "PASARELA_WOMPI", "REF-W"));

        verify(email, never()).enviarReciboPago(any(), any(), any(), any(), any());
    }

    @Test
    void pendingAndRejectedPaymentsLeaveTheDebtUntouched() {
        service.registrarPago(new PagoRequestDTO(FEE_ID, LocalDate.of(2099, 1, 10), paymentAmount, "TRANSFERENCIA", "R"));
        service.rechazarPago(PAYMENT_ID, "illegible receipt");

        assertEquals(0, invocationsNamed(repository, "actualizarSaldoCuota"));
        verify(jdbc, never()).update(contains("UPDATE CUOTAS"), any(SqlParameterSource.class));
        verify(repository, never()).aprobarPago(anyLong(), anyLong());
        verify(repository).rechazarPago(PAYMENT_ID, 20L, "illegible receipt");
    }

    @Test
    void approvingAnAlreadyApprovedPaymentChangesNothing() {
        paymentState = "APROBADO";

        assertThrows(IllegalStateException.class, () -> service.aprobarPago(PAYMENT_ID));

        verify(repository, never()).aprobarPago(anyLong(), anyLong());
        verify(repository, never()).recalcularCarteraUnidad(anyLong());
    }

    @Test
    void approvalOfMoreThanTheOutstandingBalanceIsRefused() {
        paymentAmount = new BigDecimal("80.00");
        feeBalance = new BigDecimal("50.00");
        feeState = "PAGADA_PARCIAL";

        assertThrows(IllegalStateException.class, () -> service.aprobarPago(PAYMENT_ID));

        verify(repository, never()).aprobarPago(anyLong(), anyLong());
    }

    @Test
    void approvalOfAFeeThatIsAlreadyPaidIsRefused() {
        feeState = "PAGADA";
        feeBalance = BigDecimal.ZERO;

        assertThrows(IllegalStateException.class, () -> service.aprobarPago(PAYMENT_ID));

        verify(repository, never()).aprobarPago(anyLong(), anyLong());
    }

    @Test
    void approvalRequiresAnAuthenticatedApprover() {
        SaedContextHolder.setContext(SaedContext.builder().organizationId(10L).propertyId(100L)
                .roleCode("ADMIN_PROPIEDAD").roleScope("PROPIEDAD").build());

        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.aprobarPago(PAYMENT_ID));

        verify(repository, never()).aprobarPago(anyLong(), anyLong());
    }

    @Test
    void approvalReceiptIsSentOnlyAfterCommit() throws Exception {
        TransactionSynchronizationManager.initSynchronization();

        service.aprobarPago(PAYMENT_ID);

        verify(email, never()).enviarReciboPago(any(), any(), any(), any(), any());
        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCommit();
        }
        verify(email, times(1)).enviarReciboPago(eq("resident@example.invalid"), any(), any(), any(), any());
    }

    @Test
    void approvalReceiptIsDroppedWhenTheTransactionRollsBack() throws Exception {
        TransactionSynchronizationManager.initSynchronization();

        service.aprobarPago(PAYMENT_ID);
        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }

        verify(email, never()).enviarReciboPago(any(), any(), any(), any(), any());
    }

    @Test
    void repositoryDoesNotExposeAnyFeeDebitOperation() {
        for (Method m : FinanzasRepository.class.getMethods()) {
            assertNotEquals("actualizarSaldoCuota", m.getName(), "Java must not debit fees");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void repositoryApprovalIsAConditionalSingleShotTransition() {
        NamedParameterJdbcTemplate repoJdbc = mock(NamedParameterJdbcTemplate.class);
        when(repoJdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(1, 0);
        FinanzasRepositoryImpl repo = new FinanzasRepositoryImpl(repoJdbc);

        repo.aprobarPago(PAYMENT_ID, 20L);
        assertThrows(IllegalStateException.class, () -> repo.aprobarPago(PAYMENT_ID, 20L),
                "a repeated or concurrent approval must not succeed twice");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(repoJdbc, times(2)).update(sql.capture(), any(SqlParameterSource.class));
        for (String s : sql.getAllValues()) {
            assertTrue(s.contains("ESTADO = 'PENDIENTE_APROBACION'"), s);
            assertFalse(s.contains("CUOTAS"), s);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void repositoryInsertNeverTouchesFees() {
        NamedParameterJdbcTemplate repoJdbc = mock(NamedParameterJdbcTemplate.class);
        doAnswer(invocation -> {
            ((org.springframework.jdbc.support.GeneratedKeyHolder) invocation.getArgument(2))
                    .getKeyList().add(Map.of("ID_PAGO", 501L));
            return 1;
        }).when(repoJdbc).update(anyString(), any(SqlParameterSource.class),
                any(org.springframework.jdbc.support.KeyHolder.class), any(String[].class));
        FinanzasRepositoryImpl repo = new FinanzasRepositoryImpl(repoJdbc);

        repo.registrarPago(new PagoRequestDTO(FEE_ID, LocalDate.of(2099, 1, 10), paymentAmount, "TRANSFERENCIA", "R"),
                UNIT_ID, "PENDIENTE_APROBACION", null, null);

        verify(repoJdbc, never()).update(contains("UPDATE CUOTAS"), any(SqlParameterSource.class));
    }

    @Test
    void triggerMigrationIsTheDocumentedSingleOwner() throws Exception {
        Path migrations = Path.of("..", "database", "migrations");
        Path file;
        try (var files = Files.list(migrations)) {
            file = files.filter(p -> p.getFileName().toString().startsWith("V5.50__")).findFirst().orElseThrow();
        }
        String sql = Files.readString(file);
        assertTrue(sql.contains("CREATE OR REPLACE TRIGGER TRG_APLICAR_PAGO_CUOTA"));
        assertTrue(sql.contains("CREATE OR REPLACE TRIGGER TRG_PAGOS_APROBACION_ESTADO"));
        assertTrue(sql.contains("WHEN (NEW.ESTADO = 'APROBADO' AND OLD.ESTADO = 'PENDIENTE_APROBACION')"));
        assertTrue(sql.contains("ALTER TRIGGER TRG_APLICAR_PAGO_CUOTA ENABLE"));
        assertTrue(sql.contains("ALTER TRIGGER TRG_PAGOS_APROBACION_ESTADO ENABLE"));
        assertFalse(sql.contains("WHEN OTHERS THEN NULL"));
        assertTrue(Arrays.stream(sql.split("\n")).noneMatch(l -> l.contains("&")));
    }
}
