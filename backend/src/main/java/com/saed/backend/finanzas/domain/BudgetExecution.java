package com.saed.backend.finanzas.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Accounting rule of the executed amount of a budget line (F06-05): an expense counts towards the
 * executed amount of its budget unless it is cancelled (ANULADO). Given the stored and the new version
 * of an expense this returns the net change per budget, so moving an expense between budgets, changing
 * its amount or cancelling it can be applied to every affected budget in one transaction.
 */
public final class BudgetExecution {

    /** Net change of one budget's executed amount. */
    public record Movement(Long idPresupuesto, BigDecimal delta) {
    }

    private BudgetExecution() {
    }

    public static boolean counts(String state) {
        return state == null || !"ANULADO".equalsIgnoreCase(state.trim());
    }

    /** Net movements (non-zero, ordered by budget id) between two versions of the same expense. */
    public static List<Movement> between(Long previousBudget, BigDecimal previousAmount, String previousState,
                                         Long newBudget, BigDecimal newAmount, String newState) {
        Map<Long, BigDecimal> net = new TreeMap<>();
        if (previousBudget != null && counts(previousState) && previousAmount != null) {
            net.merge(previousBudget, previousAmount.negate(), BigDecimal::add);
        }
        if (newBudget != null && counts(newState) && newAmount != null) {
            net.merge(newBudget, newAmount, BigDecimal::add);
        }
        List<Movement> movements = new ArrayList<>();
        net.forEach((budget, delta) -> {
            if (delta.signum() != 0) {
                movements.add(new Movement(budget, delta));
            }
        });
        return movements;
    }
}
