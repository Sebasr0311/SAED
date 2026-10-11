package com.saed.backend.finanzas.billing;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * F06-02 / DEC-08: monthly fee generation per property and period, idempotent, with disabled-by-default
 * discount and interest, and the OVERDUE transition. The same service method serves the manual endpoint
 * and the future scheduler. Mocked persistence; no database.
 */
class BillingServiceTest {
    private static final long PROP = 100L;
    private static final long CONCEPT = 77L;
    private static final YearMonth PERIOD = YearMonth.of(2026, 10);
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 1);

    private BillingRepository repo;
    private BillingService service;

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    @BeforeEach
    void setUp() {
        repo = mock(BillingRepository.class);
        service = new BillingService(repo);
        when(repo.findSettings(PROP)).thenReturn(Optional.empty());
        when(repo.ensureAdministrationConcept(PROP)).thenReturn(CONCEPT);
        when(repo.approvedAnnualExpenseBudget(PROP, 2026)).thenReturn(bd("1200000.00"));
        when(repo.findActiveUnits(PROP)).thenReturn(List.of(new UnitShare(1L, bd("0.5")),
                new UnitShare(2L, bd("0.3")), new UnitShare(3L, bd("0.2"))));
        when(repo.insertFeeIfAbsent(any(NewFee.class))).thenReturn(true);
        asAdmin(PROP);
    }

    @AfterEach
    void tearDown() {
        SaedContextHolder.clearContext();
    }

    private static void asAdmin(long property) {
        SaedContextHolder.setContext(SaedContext.builder().userId(9L).organizationId(10L).propertyId(property)
                .roleCode("ADMIN_PROPIEDAD").roleScope("PROPIEDAD").build());
    }

    private List<NewFee> insertedFees() {
        ArgumentCaptor<NewFee> captor = ArgumentCaptor.forClass(NewFee.class);
        verify(repo, atLeastOnce()).insertFeeIfAbsent(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void generatesOneFeePerUnitFromTheBudgetShare() {
        GenerationResult result = service.generarCuotasMensuales(PROP, PERIOD, TODAY);

        List<NewFee> fees = insertedFees();
        assertEquals(3, fees.size());
        assertEquals(bd("50000.00"), fees.get(0).valorBase());
        assertEquals(bd("30000.00"), fees.get(1).valorBase());
        assertEquals(bd("20000.00"), fees.get(2).valorBase());
        for (NewFee fee : fees) {
            assertEquals("2026-10", fee.periodo());
            assertEquals(CONCEPT, fee.idConcepto());
            assertEquals(LocalDate.of(2026, 10, 10), fee.fechaVencimiento());
            assertEquals(fee.valorBase(), fee.saldoPendiente());
            assertEquals(0, fee.valorDescuento().signum());
            assertNull(fee.fechaDescuentoProntoPago());
        }
        assertEquals(3, result.creadas());
        assertEquals(0, result.existentes());
        assertEquals(bd("100000.00"), result.totalMensual());
    }

    @Test
    void replayingThePeriodCreatesNothingNew() {
        when(repo.insertFeeIfAbsent(any(NewFee.class))).thenReturn(false);

        GenerationResult result = service.generarCuotasMensuales(PROP, PERIOD, TODAY);

        assertEquals(0, result.creadas());
        assertEquals(3, result.existentes());
    }

    @Test
    void aFixedFeeReplacesTheBudgetShare() {
        when(repo.findSettings(PROP)).thenReturn(Optional.of(new BillingSettings(PROP, bd("180000"), bd("0"), 0,
                bd("0"), null, 0, 15)));
        when(repo.approvedAnnualExpenseBudget(PROP, 2026)).thenReturn(BigDecimal.ZERO);

        service.generarCuotasMensuales(PROP, PERIOD, TODAY);

        for (NewFee fee : insertedFees()) {
            assertEquals(bd("180000.00"), fee.valorBase());
            assertEquals(LocalDate.of(2026, 10, 15), fee.fechaVencimiento());
        }
    }

    @Test
    void earlyPaymentDiscountReducesTheBalanceWhileTheWindowIsOpen() {
        when(repo.findSettings(PROP)).thenReturn(Optional.of(new BillingSettings(PROP, null, bd("10"), 5,
                bd("0"), null, 0, 10)));

        service.generarCuotasMensuales(PROP, PERIOD, TODAY);

        NewFee first = insertedFees().get(0);
        assertEquals(bd("50000.00"), first.valorBase());
        assertEquals(bd("5000.00"), first.valorDescuento());
        assertEquals(bd("45000.00"), first.saldoPendiente());
        assertEquals(LocalDate.of(2026, 10, 5), first.fechaDescuentoProntoPago());
    }

    @Test
    void noDiscountIsGrantedOnceTheWindowAlreadyClosed() {
        when(repo.findSettings(PROP)).thenReturn(Optional.of(new BillingSettings(PROP, null, bd("10"), 5,
                bd("0"), null, 0, 10)));

        service.generarCuotasMensuales(PROP, PERIOD, LocalDate.of(2026, 10, 8));

        NewFee first = insertedFees().get(0);
        assertEquals(0, first.valorDescuento().signum());
        assertEquals(first.valorBase(), first.saldoPendiente());
        assertNull(first.fechaDescuentoProntoPago());
    }

    @Test
    void unitsWithoutCoefficientAreSkippedAndReported() {
        when(repo.findActiveUnits(PROP)).thenReturn(List.of(new UnitShare(1L, bd("1.0")), new UnitShare(2L, null)));

        GenerationResult result = service.generarCuotasMensuales(PROP, PERIOD, TODAY);

        assertEquals(1, insertedFees().size());
        assertEquals(1, result.sinCoeficiente());
    }

    @Test
    void withoutBudgetOrFixedFeeNothingIsGenerated() {
        when(repo.approvedAnnualExpenseBudget(PROP, 2026)).thenReturn(BigDecimal.ZERO);

        assertThrows(IllegalStateException.class, () -> service.generarCuotasMensuales(PROP, PERIOD, TODAY));

        verify(repo, never()).insertFeeIfAbsent(any());
    }

    @Test
    void onlyTheAdministratorOfThatPropertyMayGenerate() {
        asAdmin(999L);
        assertThrows(AccessDeniedException.class, () -> service.generarCuotasMensuales(PROP, PERIOD, TODAY));

        SaedContextHolder.setContext(SaedContext.builder().userId(1L).organizationId(10L).propertyId(PROP)
                .roleCode("SUPERADMIN").roleScope("GLOBAL").build());
        assertThrows(AccessDeniedException.class, () -> service.generarCuotasMensuales(PROP, PERIOD, TODAY));

        SaedContextHolder.setContext(SaedContext.builder().userId(2L).organizationId(10L).propertyId(PROP)
                .roleCode("RESIDENTE").roleScope("UNIDAD").build());
        assertThrows(AccessDeniedException.class, () -> service.generarCuotasMensuales(PROP, PERIOD, TODAY));

        verify(repo, never()).insertFeeIfAbsent(any());
    }

    // ---- transitions

    private static OpenFee fee(long id, String state, String base, String discount, String interest, String balance,
                               LocalDate due, LocalDate discountDeadline) {
        return new OpenFee(id, state, bd(base), bd(discount), bd(interest), bd(balance), due, discountDeadline);
    }

    @Test
    void overdueTransitionIsDelegatedForTheProperty() {
        when(repo.markOverdue(PROP, TODAY)).thenReturn(4);
        when(repo.findOutstandingFees(PROP)).thenReturn(List.of());

        TransitionResult result = service.procesarVencimientos(PROP, TODAY);

        verify(repo).markOverdue(PROP, TODAY);
        assertEquals(4, result.vencidas());
    }

    @Test
    void expiredEarlyPaymentDiscountIsForfeitedExactlyOnce() {
        LocalDate now = LocalDate.of(2026, 10, 6);
        OpenFee withDiscount = fee(1, "PENDIENTE", "50000.00", "5000.00", "0.00", "45000.00",
                LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 5));
        when(repo.findOutstandingFees(PROP)).thenReturn(List.of(withDiscount));

        TransitionResult first = service.procesarVencimientos(PROP, now);

        verify(repo).applyAdjustment(1L, bd("50000.00"), bd("0.00"), bd("0.00"));
        assertEquals(1, first.descuentosVencidos());

        // replay: the stored fee no longer carries a discount
        OpenFee afterwards = fee(1, "PENDIENTE", "50000.00", "0.00", "0.00", "50000.00",
                LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 5));
        when(repo.findOutstandingFees(PROP)).thenReturn(List.of(afterwards));
        TransitionResult second = service.procesarVencimientos(PROP, now);

        verify(repo, times(1)).applyAdjustment(anyLong(), any(), any(), any());
        assertEquals(0, second.descuentosVencidos());
    }

    @Test
    void nothingIsAccruedWhileInterestIsDisabled() {
        OpenFee overdue = fee(2, "VENCIDA", "100000.00", "0.00", "0.00", "100000.00",
                LocalDate.of(2026, 7, 10), null);
        when(repo.findOutstandingFees(PROP)).thenReturn(List.of(overdue));

        TransitionResult result = service.procesarVencimientos(PROP, LocalDate.of(2026, 10, 15));

        verify(repo, never()).applyAdjustment(anyLong(), any(), any(), any());
        assertEquals(0, result.cuotasConInteres());
    }

    @Test
    void simpleMonthlyInterestAccruesOncePerStartedMonthAndNeverTwice() {
        when(repo.findSettings(PROP)).thenReturn(Optional.of(new BillingSettings(PROP, null, bd("0"), 0,
                bd("2"), null, 0, 10)));
        OpenFee overdue = fee(2, "VENCIDA", "100000.00", "0.00", "0.00", "100000.00",
                LocalDate.of(2026, 7, 10), null);
        when(repo.findOutstandingFees(PROP)).thenReturn(List.of(overdue));
        LocalDate now = LocalDate.of(2026, 10, 15);   // 4 started months after July 10

        TransitionResult first = service.procesarVencimientos(PROP, now);

        verify(repo).applyAdjustment(2L, bd("108000.00"), bd("0.00"), bd("8000.00"));
        assertEquals(1, first.cuotasConInteres());

        OpenFee accrued = fee(2, "VENCIDA", "100000.00", "0.00", "8000.00", "108000.00",
                LocalDate.of(2026, 7, 10), null);
        when(repo.findOutstandingFees(PROP)).thenReturn(List.of(accrued));
        service.procesarVencimientos(PROP, now);

        verify(repo, times(1)).applyAdjustment(anyLong(), any(), any(), any());
    }

    @Test
    void interestIsChargedOnTheUnpaidPrincipalOfPartiallyPaidFees() {
        when(repo.findSettings(PROP)).thenReturn(Optional.of(new BillingSettings(PROP, null, bd("0"), 0,
                bd("2"), null, 0, 10)));
        OpenFee partial = fee(3, "PAGADA_PARCIAL", "100000.00", "0.00", "0.00", "40000.00",
                LocalDate.of(2026, 9, 10), null);
        when(repo.findOutstandingFees(PROP)).thenReturn(List.of(partial));

        service.procesarVencimientos(PROP, LocalDate.of(2026, 10, 15));   // 2 started months on 40000

        verify(repo).applyAdjustment(3L, bd("41600.00"), bd("0.00"), bd("1600.00"));
    }

    @Test
    void transitionsRequireThePropertyAdministrator() {
        asAdmin(999L);

        assertThrows(AccessDeniedException.class, () -> service.procesarVencimientos(PROP, TODAY));

        verify(repo, never()).markOverdue(anyLong(), any());
    }

    @Test
    void settingsAreValidatedBeforeTheyAreSaved() {
        assertThrows(IllegalArgumentException.class, () -> service.guardarConfiguracion(PROP,
                new BillingSettings(PROP, null, bd("101"), 0, bd("0"), null, 0, 10)));
        assertThrows(IllegalArgumentException.class, () -> service.guardarConfiguracion(PROP,
                new BillingSettings(PROP, null, bd("0"), 0, bd("-1"), null, 0, 10)));
        assertThrows(IllegalArgumentException.class, () -> service.guardarConfiguracion(PROP,
                new BillingSettings(PROP, null, bd("0"), 0, bd("0"), null, 0, 31)));
        assertThrows(IllegalArgumentException.class, () -> service.guardarConfiguracion(PROP,
                new BillingSettings(PROP, bd("-5"), bd("0"), 0, bd("0"), null, 0, 10)));
        verify(repo, never()).saveSettings(any());

        service.guardarConfiguracion(PROP, new BillingSettings(PROP, null, bd("5"), 3, bd("1.5"), bd("2"), 2, 10));
        verify(repo).saveSettings(any(BillingSettings.class));
    }

    @Test
    void settingsDefaultToDisabledWhenNeverConfigured() {
        BillingSettings s = service.obtenerConfiguracion(PROP);

        assertFalse(s.discountEnabled());
        assertFalse(s.interestEnabled());
    }
}
