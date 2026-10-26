package com.saed.backend.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * A success audit joins the audited business transaction: it commits atomically with the change and never asks
 * the pool for a second connection while the first one holds row locks (five concurrent payment approvals on a
 * small pool stalled for the full connection timeout). A failure audit keeps its own transaction, because the
 * business transaction is about to roll back.
 */
class AuditTransactionPropagationTest {

    @Test
    void successAuditJoinsTheBusinessTransaction() {
        assertThat(propagationOf("recordSuccess")).isEqualTo(Propagation.REQUIRED);
    }

    @Test
    void failureAuditSurvivesTheRollback() {
        assertThat(propagationOf("recordFailure")).isEqualTo(Propagation.REQUIRES_NEW);
    }

    private static Propagation propagationOf(String name) {
        Method method = Arrays.stream(AuditServiceImpl.class.getDeclaredMethods())
                .filter(m -> m.getName().equals(name))
                .findFirst()
                .orElseThrow();
        return method.getAnnotation(Transactional.class).propagation();
    }
}
