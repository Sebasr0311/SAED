package com.saed.backend.finanzas.billing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** F06-02 / DEC-08: golden coefficient, rounding, discount and interest examples. Pure arithmetic. */
class FeeCalculatorTest {

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private static BillingSettings settings(String fixedFee, String discountPct, int discountDays, String ratePct,
                                            String capPct, int grace, int dueDay) {
        return new BillingSettings(1L, fixedFee == null ? null : bd(fixedFee), bd(discountPct), discountDays,
                bd(ratePct), capPct == null ? null : bd(capPct), grace, dueDay);
    }

    @Test
    void defaultsAreDisabled() {
        BillingSettings s = BillingSettings.disabled(7L);
        assertFalse(s.discountEnabled());
        assertFalse(s.interestEnabled());
        assertNull(s.cuotaFija());
        assertEquals(0, s.porcDescuentoProntoPago().signum());
        assertEquals(0, s.porcInteresMoraMensual().signum());
    }

    @Test
    void budgetIsSharedByCoefficient() {
        Map<Long, BigDecimal> fees = FeeCalculator.unitFees(BillingSettings.disabled(1L), bd("1200000.00"),
                List.of(new UnitShare(1L, bd("0.5")), new UnitShare(2L, bd("0.3")), new UnitShare(3L, bd("0.2"))));

        assertEquals(bd("50000.00"), fees.get(1L));
        assertEquals(bd("30000.00"), fees.get(2L));
        assertEquals(bd("20000.00"), fees.get(3L));
    }

    @Test
    void roundingResidueIsDistributedSoTheMonthlyTotalReconciles() {
        List<UnitShare> thirds = List.of(new UnitShare(1L, bd("0.333333")), new UnitShare(2L, bd("0.333333")),
                new UnitShare(3L, bd("0.333334")));
        Map<Long, BigDecimal> fees = FeeCalculator.unitFees(BillingSettings.disabled(1L), bd("1200.00"), thirds);

        assertEquals(bd("100.00"), fees.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Test
    void residueGoesToTheLargestRemaindersDeterministically() {
        List<UnitShare> thirds = List.of(new UnitShare(1L, bd("0.333333")), new UnitShare(2L, bd("0.333333")),
                new UnitShare(3L, bd("0.333334")));
        Map<Long, BigDecimal> a = FeeCalculator.unitFees(BillingSettings.disabled(1L), bd("1200.00"), thirds);
        Map<Long, BigDecimal> b = FeeCalculator.unitFees(BillingSettings.disabled(1L), bd("1200.00"), thirds);

        assertEquals(new LinkedHashMap<>(a), new LinkedHashMap<>(b));
        assertEquals(bd("33.33"), a.get(1L));
        assertEquals(bd("33.33"), a.get(2L));
        assertEquals(bd("33.34"), a.get(3L));
    }

    @Test
    void incompleteCensusIsNotForcedToReconcile() {
        Map<Long, BigDecimal> fees = FeeCalculator.unitFees(BillingSettings.disabled(1L), bd("1200.00"),
                List.of(new UnitShare(1L, bd("0.25")), new UnitShare(2L, bd("0.25"))));

        assertEquals(bd("25.00"), fees.get(1L));
        assertEquals(bd("25.00"), fees.get(2L));
    }

    @Test
    void fixedFeeOverridesTheBudgetShare() {
        Map<Long, BigDecimal> fees = FeeCalculator.unitFees(settings("150000", "0", 0, "0", null, 0, 10), bd("999999.00"),
                List.of(new UnitShare(1L, bd("0.9")), new UnitShare(2L, null)));

        assertEquals(bd("150000.00"), fees.get(1L));
        assertEquals(bd("150000.00"), fees.get(2L));
    }

    @Test
    void unitsWithoutCoefficientGetNoBudgetShare() {
        Map<Long, BigDecimal> fees = FeeCalculator.unitFees(BillingSettings.disabled(1L), bd("1200.00"),
                List.of(new UnitShare(1L, bd("1.0")), new UnitShare(2L, null)));

        assertEquals(bd("100.00"), fees.get(1L));
        assertFalse(fees.containsKey(2L));
    }

    @Test
    void discountAndDeadlineAreComputedFromSettings() {
        assertEquals(bd("10000.00"), FeeCalculator.discount(bd("100000.00"), bd("10")));
        assertEquals(bd("0.00"), FeeCalculator.discount(bd("100000.00"), bd("0")));
        assertEquals(LocalDate.of(2026, 10, 5), FeeCalculator.discountDeadline(YearMonth.of(2026, 10), 5));
        assertNull(FeeCalculator.discountDeadline(YearMonth.of(2026, 10), 0));
    }

    @Test
    void dueDateUsesTheConfiguredDayOfThePeriod() {
        assertEquals(LocalDate.of(2026, 2, 10), FeeCalculator.dueDate(YearMonth.of(2026, 2), 10));
        assertEquals(LocalDate.of(2026, 2, 28), FeeCalculator.dueDate(YearMonth.of(2026, 2), 28));
    }

    @Test
    void monthsOverdueCountStartedMonthsAfterTheGracePeriod() {
        LocalDate due = LocalDate.of(2026, 10, 10);
        assertEquals(0, FeeCalculator.monthsOverdue(due, 0, LocalDate.of(2026, 10, 10)));
        assertEquals(1, FeeCalculator.monthsOverdue(due, 0, LocalDate.of(2026, 10, 11)));
        assertEquals(1, FeeCalculator.monthsOverdue(due, 0, LocalDate.of(2026, 11, 10)));
        assertEquals(2, FeeCalculator.monthsOverdue(due, 0, LocalDate.of(2026, 11, 11)));
        assertEquals(0, FeeCalculator.monthsOverdue(due, 5, LocalDate.of(2026, 10, 15)));
        assertEquals(1, FeeCalculator.monthsOverdue(due, 5, LocalDate.of(2026, 10, 16)));
    }

    @Test
    void interestIsSimpleMonthlyAndCappedByTheConfiguredCeiling() {
        assertEquals(bd("6000.00"), FeeCalculator.accruedInterest(bd("100000.00"), bd("2"), null, 3));
        assertEquals(bd("3000.00"), FeeCalculator.accruedInterest(bd("100000.00"), bd("2"), bd("1"), 3));
        assertEquals(bd("0.00"), FeeCalculator.accruedInterest(bd("100000.00"), bd("0"), null, 3));
        assertEquals(bd("0.00"), FeeCalculator.accruedInterest(bd("100000.00"), bd("2"), null, 0));
    }
}
