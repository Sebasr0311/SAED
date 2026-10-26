package com.saed.backend.finanzas.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure fee arithmetic (DEC-08). Amounts use scale 2 and HALF_UP. When the registered coefficients add up
 * to 1 (4-decimal tolerance, DEC-04) the rounding residue is handed out one cent at a time to the
 * largest remainders so the unit fees add up exactly to the monthly budget; with an incomplete census
 * every share is simply rounded.
 */
public final class FeeCalculator {
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);
    private static final BigDecimal COEFFICIENT_TOLERANCE = new BigDecimal("0.0001");
    private static final BigDecimal CENT = new BigDecimal("0.01");

    private FeeCalculator() {
    }

    public static Map<Long, BigDecimal> unitFees(BillingSettings settings, BigDecimal annualBudget, List<UnitShare> units) {
        Map<Long, BigDecimal> fees = new LinkedHashMap<>();
        if (settings.cuotaFija() != null) {
            BigDecimal fixed = settings.cuotaFija().setScale(2, RoundingMode.HALF_UP);
            for (UnitShare unit : units) {
                fees.put(unit.idUnidad(), fixed);
            }
            return fees;
        }
        BigDecimal monthly = (annualBudget == null ? BigDecimal.ZERO : annualBudget).divide(TWELVE, 2, RoundingMode.HALF_UP);
        List<UnitShare> withCoefficient = units.stream()
                .filter(u -> u.coeficiente() != null && u.coeficiente().signum() > 0).toList();
        BigDecimal coefficientSum = withCoefficient.stream().map(UnitShare::coeficiente)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean complete = coefficientSum.subtract(BigDecimal.ONE).abs().compareTo(COEFFICIENT_TOLERANCE) <= 0;

        if (!complete) {
            for (UnitShare unit : withCoefficient) {
                fees.put(unit.idUnidad(), monthly.multiply(unit.coeficiente()).setScale(2, RoundingMode.HALF_UP));
            }
            return fees;
        }

        Map<Long, BigDecimal> floors = new LinkedHashMap<>();
        Map<Long, BigDecimal> remainders = new LinkedHashMap<>();
        BigDecimal floorSum = BigDecimal.ZERO;
        for (UnitShare unit : withCoefficient) {
            BigDecimal exact = monthly.multiply(unit.coeficiente());
            BigDecimal floor = exact.setScale(2, RoundingMode.DOWN);
            floors.put(unit.idUnidad(), floor);
            remainders.put(unit.idUnidad(), exact.subtract(floor));
            floorSum = floorSum.add(floor);
        }
        int leftoverCents = monthly.subtract(floorSum).movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValue();
        List<Long> byRemainder = new ArrayList<>(floors.keySet());
        byRemainder.sort(Comparator.<Long, BigDecimal>comparing(remainders::get).reversed()
                .thenComparing(Comparator.naturalOrder()));
        int toHandOut = Math.min(Math.max(leftoverCents, 0), byRemainder.size());
        for (int i = 0; i < toHandOut; i++) {
            Long id = byRemainder.get(i);
            floors.put(id, floors.get(id).add(CENT));
        }
        fees.putAll(floors);
        return fees;
    }

    public static BigDecimal discount(BigDecimal base, BigDecimal percentage) {
        if (percentage == null || percentage.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return base.multiply(percentage).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    /** Last day (inclusive) of the early-payment window, counted from the first day of the period. */
    public static LocalDate discountDeadline(YearMonth period, int days) {
        return days <= 0 ? null : period.atDay(1).plusDays(days - 1L);
    }

    public static LocalDate dueDate(YearMonth period, int dueDay) {
        return period.atDay(Math.min(Math.max(dueDay, 1), period.lengthOfMonth()));
    }

    /** Number of started months overdue after the grace period (0 while not overdue). */
    public static int monthsOverdue(LocalDate dueDate, int graceDays, LocalDate today) {
        LocalDate start = dueDate.plusDays(Math.max(graceDays, 0));
        if (!today.isAfter(start)) {
            return 0;
        }
        return (int) ChronoUnit.MONTHS.between(start, today.minusDays(1)) + 1;
    }

    /** Simple monthly interest, capped by the optional ceiling: principal * min(rate, cap)% * months. */
    public static BigDecimal accruedInterest(BigDecimal principal, BigDecimal ratePercent, BigDecimal capPercent, int months) {
        if (months <= 0 || ratePercent == null || ratePercent.signum() <= 0 || principal == null || principal.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        BigDecimal effective = capPercent != null && capPercent.compareTo(ratePercent) < 0 ? capPercent : ratePercent;
        return principal.multiply(effective).multiply(BigDecimal.valueOf(months))
                .divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }
}
