package com.saed.backend.finanzas.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Runs a side effect (e-mail, notification) only after the surrounding transaction has committed
 * (F06-04). When the transaction rolls back, or the provider retries a webhook whose first attempt
 * rolled back, the effect is never executed, so receipts cannot be duplicated or sent for a payment
 * that was not stored. Outside a transaction the effect runs immediately.
 *
 * <p>Failures of the effect are logged and never propagate: after the commit there is nothing left
 * to roll back and the caller must not turn a stored payment into an error response.
 */
public final class AfterCommit {
    private static final Logger log = LoggerFactory.getLogger(AfterCommit.class);

    /** Side effect that may fail with a checked exception (mail transports do). */
    @FunctionalInterface
    public interface Effect {
        void run() throws Exception;
    }

    private AfterCommit() {
    }

    public static void run(String description, Effect effect) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    execute(description, effect);
                }
            });
        } else {
            execute(description, effect);
        }
    }

    private static void execute(String description, Effect effect) {
        try {
            effect.run();
        } catch (Exception e) {
            log.error("After-commit effect '{}' failed", description, e);
        }
    }
}
