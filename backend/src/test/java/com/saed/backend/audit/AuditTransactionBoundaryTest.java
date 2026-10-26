package com.saed.backend.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.saed.backend.config.TransactionOrderConfig;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Five concurrent approvals of one payment on a small pool stalled for the full connection timeout: each losing
 * request still held its business connection while its failure audit (REQUIRES_NEW) asked the pool for another one,
 * and the timed-out audit then replaced the 409 with a 503. The success audit must join the business transaction;
 * the failure audit must run only after the business transaction has ended, and must never replace the original
 * exception.
 */
@SpringJUnitConfig(AuditTransactionBoundaryTest.Config.class)
class AuditTransactionBoundaryTest {

    static final AtomicReference<Boolean> successSawTransaction = new AtomicReference<>();
    static final AtomicReference<Boolean> failureSawTransaction = new AtomicReference<>();

    @Configuration
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    @Import({TransactionOrderConfig.class, AuditAspect.class, AuditFailureAspect.class})
    static class Config {

        @Bean
        PlatformTransactionManager transactionManager() {
            return new NoResourceTransactionManager();
        }

        @Bean
        AuditSanitizer auditSanitizer() {
            return mock(AuditSanitizer.class);
        }

        @Bean
        AuditService auditService() {
            AuditService service = mock(AuditService.class);
            doAnswer(inv -> {
                successSawTransaction.set(TransactionSynchronizationManager.isActualTransactionActive());
                return null;
            }).when(service).recordSuccess(any(), any(), any(), anyString(), anyString(), any(), any(), any(), any(), any());
            doAnswer(inv -> {
                failureSawTransaction.set(TransactionSynchronizationManager.isActualTransactionActive());
                throw new IllegalStateException("audit store unavailable");
            }).when(service).recordFailure(any(), any(), any(), anyString(), anyString(), any(), any(), any(), any(), any());
            return service;
        }

        @Bean
        AuditedOperation auditedOperation() {
            return new AuditedOperation();
        }
    }

    static class AuditedOperation {

        @Transactional
        @Auditable(action = "UPDATE", resource = "PAGO")
        public Long succeed(Long id) {
            return id;
        }

        @Transactional
        @Auditable(action = "UPDATE", resource = "PAGO")
        public Long fail(Long id) {
            throw new IllegalArgumentException("payment already approved");
        }
    }

    /** Transaction manager without a real resource: enough to drive transaction synchronization. */
    static class NoResourceTransactionManager extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }

    @Autowired
    private AuditedOperation operation;

    @Test
    void successAuditRunsInsideTheBusinessTransaction() {
        operation.succeed(7L);

        assertThat(successSawTransaction.get()).isTrue();
    }

    @Test
    void failureAuditRunsAfterTheBusinessTransactionEnded() {
        assertThatThrownBy(() -> operation.fail(7L)).isInstanceOf(IllegalArgumentException.class);

        assertThat(failureSawTransaction.get()).isFalse();
    }

    @Test
    void failingFailureAuditNeverReplacesTheBusinessException() {
        assertThatThrownBy(() -> operation.fail(7L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("payment already approved");
    }
}
