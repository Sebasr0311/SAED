package com.saed.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Makes the transaction advisor's position explicit instead of tying it with unordered aspects. It sits just
 * outside {@link com.saed.backend.audit.AuditAspect} (lowest precedence), so a success audit joins the audited
 * transaction, and inside {@link com.saed.backend.audit.AuditFailureAspect} (near-highest precedence), so a failure
 * audit runs only after the business transaction has ended and released its pooled connection. Class-based proxies
 * keep Spring Boot's default.
 */
@Configuration
@EnableTransactionManagement(proxyTargetClass = true, order = Ordered.LOWEST_PRECEDENCE - 1)
public class TransactionOrderConfig {
}
