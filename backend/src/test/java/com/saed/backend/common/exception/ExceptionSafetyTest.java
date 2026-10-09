package com.saed.backend.common.exception;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.saed.backend.audit.CorrelationIdFilter;
import com.saed.backend.audit.CorrelationIdHolder;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Synthetic errors only; no database, Spring application, credentials or production target. */
class ExceptionSafetyTest {

    private static final String SENSITIVE_MARKER = "synthetic-sensitive-value";
    private static final Set<String> CONTRACT_KEYS = Set.of("success", "status", "code", "message", "traceId", "timestamp");
    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(jdbc);

    @AfterEach
    void clearCorrelation() {
        CorrelationIdHolder.clear();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ORA-28112", "ORA-28112 ORA-28115"})
    void policyFunctionFailureDoesNotQueryRepairOrDropPolicies(String oracleError) {
        var response = handler.handleDatabaseException(new DataAccessResourceFailureException(
                oracleError + " SELECT USER_ERRORS USER_OBJECTS USER_POLICIES dbUser predicate=" + SENSITIVE_MARKER));
        assertEquals(500, response.getStatusCode().value());
        assertEquals("DATABASE_ERROR", response.getBody().get("code"));
        assertEquals(CONTRACT_KEYS, response.getBody().keySet());
        assertFalse(response.getBody().toString().contains(SENSITIVE_MARKER));
        assertFalse(response.getBody().toString().contains("ORA-"));
        assertFalse(mockingDetails(jdbc).getInvocations().iterator().hasNext(), "Error handling must perform no JDBC calls");
    }

    @Test
    void unknownDatabaseErrorNeverLeaksNestedSqlOrIdentities() {
        var response = handler.handleDatabaseException(new DataAccessResourceFailureException(
                "query failed", new SQLException("SELECT secret_column FROM PRIVATE_TABLE " + SENSITIVE_MARKER)));
        assertEquals(500, response.getStatusCode().value());
        assertEquals("DATABASE_ERROR", response.getBody().get("code"));
        assertEquals(CONTRACT_KEYS, response.getBody().keySet());
        assertFalse(response.getBody().toString().contains(SENSITIVE_MARKER));
        assertFalse(response.getBody().toString().contains("PRIVATE_TABLE"));
        assertNotNull(Instant.parse(response.getBody().get("timestamp").toString()));
    }

    @Test
    void nullDatabaseMessageStillProducesCanonicalError() {
        var response = handler.handleDatabaseException(new DataAccessResourceFailureException(null));
        assertEquals(500, response.getStatusCode().value());
        assertEquals(CONTRACT_KEYS, response.getBody().keySet());
        assertNotNull(response.getBody().get("traceId"));
    }

    @Test
    void transactionWrappedPolicyFailureDoesNotAttemptAuditOrRecovery() {
        var response = handler.handleTransactionException(new CannotCreateTransactionException(
                "transaction failed", new SQLException("ORA-28112 ORA-20083 " + SENSITIVE_MARKER)));
        assertEquals(500, response.getStatusCode().value());
        assertEquals("INTERNAL_SERVER_ERROR", response.getBody().get("code"));
        assertFalse(response.getBody().toString().contains(SENSITIVE_MARKER));
        assertFalse(mockingDetails(jdbc).getInvocations().iterator().hasNext());
    }

    @Test
    void intermediatePolicyFailureTakesPriorityOverSpoofingAudit() {
        var failure = new CannotCreateTransactionException("transaction failed",
                new RuntimeException("ORA-20083", new SQLException("ORA-28112 " + SENSITIVE_MARKER)));
        var response = handler.handleTransactionException(failure);
        assertEquals(500, response.getStatusCode().value());
        assertFalse(mockingDetails(jdbc).getInvocations().iterator().hasNext(), "Policy failure must not trigger audit JDBC");
        assertFalse(response.getBody().toString().contains(SENSITIVE_MARKER));
    }

    @Test
    void cyclicCauseChainFailsClosedWithoutAuditing() {
        RuntimeException cyclic = mock(RuntimeException.class);
        when(cyclic.getMessage()).thenReturn("ORA-20083");
        when(cyclic.getCause()).thenReturn(cyclic);
        var response = handler.handleTransactionException(new CannotCreateTransactionException("transaction failed", cyclic));
        assertEquals(500, response.getStatusCode().value());
        assertFalse(mockingDetails(jdbc).getInvocations().iterator().hasNext(), "Uninspectable cause chain must not trigger JDBC");
    }

    @Test
    void excessivelyDeepCauseChainFailsClosedWithoutAuditing() {
        Throwable cause = new SQLException("ORA-28112");
        for (int depth = 0; depth < 40; depth++) {
            cause = new RuntimeException("ORA-20083", cause);
        }
        var response = handler.handleTransactionException(new CannotCreateTransactionException("transaction failed", cause));
        assertEquals(500, response.getStatusCode().value());
        assertFalse(mockingDetails(jdbc).getInvocations().iterator().hasNext());
    }

    @Test
    void nestedDatabasePolicyFailureTakesPriorityOverTopLevelDenial() {
        var response = handler.handleDatabaseException(new DataAccessResourceFailureException("ORA-28115",
                new RuntimeException("wrapper", new SQLException("ORA-28112"))));
        assertEquals(500, response.getStatusCode().value());
        assertEquals("DATABASE_ERROR", response.getBody().get("code"));
        assertFalse(mockingDetails(jdbc).getInvocations().iterator().hasNext());
    }

    @Test
    void unsafeCorrelationIsReplacedWithBoundedIdentifier() {
        CorrelationIdHolder.set("unsafe\n" + SENSITIVE_MARKER);
        var response = handler.handleDatabaseException(new DataAccessResourceFailureException("ORA-28112"));
        String traceId = response.getBody().get("traceId").toString();
        assertFalse(traceId.contains(SENSITIVE_MARKER));
        assertEquals(36, traceId.length());
    }

    @Test
    void httpFailurePreservesCorrelationAndCanonicalContractWithoutMetadata() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new FailingController())
                .setControllerAdvice(handler).addFilters(new CorrelationIdFilter()).build();
        mvc.perform(get("/test/database-failure").header("X-Correlation-Id", "safe-trace-123"))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string("X-Correlation-Id", "safe-trace-123"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("DATABASE_ERROR"))
                .andExpect(jsonPath("$.traceId").value("safe-trace-123"))
                .andExpect(jsonPath("$.plsqlErrors").doesNotExist())
                .andExpect(jsonPath("$.objectStatus").doesNotExist())
                .andExpect(jsonPath("$.userPolicies").doesNotExist())
                .andExpect(jsonPath("$.dbUser").doesNotExist())
                .andExpect(jsonPath("$.testPredUnidad").doesNotExist())
                .andExpect(jsonPath("$.dropPersonaPolicy").doesNotExist());
        assertFalse(mockingDetails(jdbc).getInvocations().iterator().hasNext());
    }

    @Test
    void persistenceAndFallbackLoggingDoesNotIncludeRawExceptionOrStackTrace() {
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            CorrelationIdHolder.set("safe-log-trace");
            handler.handleDatabaseException(new DataAccessResourceFailureException("ORA-28112 " + SENSITIVE_MARKER));
            handler.handleDatabaseException(new DataAccessResourceFailureException("ORA-00001 " + SENSITIVE_MARKER));
            handler.handleDatabaseException(new DataAccessResourceFailureException(SENSITIVE_MARKER));
            handler.handleTransactionException(new CannotCreateTransactionException(SENSITIVE_MARKER));
            handler.handleGenericException(new IllegalStateException(SENSITIVE_MARKER));
            assertFalse(appender.list.isEmpty());
            for (ILoggingEvent event : appender.list) {
                assertFalse(event.getFormattedMessage().contains(SENSITIVE_MARKER), "Raw error details must not be logged");
                assertNull(event.getThrowableProxy(), "Sensitive nested stack traces must not be logged");
            }
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void unknownDatabaseFailureLogsBoundedDiagnosticWithoutRawDetails() {
        // A 500 must stay diagnosable server-side: exception types and vendor codes only, never text/SQL/stack.
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            CorrelationIdHolder.set("diag-trace");
            SQLException vendor = new SQLException("ORA-00904: \"" + SENSITIVE_MARKER + "\": invalid identifier", "42000", 904);
            handler.handleDatabaseException(new org.springframework.jdbc.BadSqlGrammarException("query", "SELECT " + SENSITIVE_MARKER, vendor));
            String logged = appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (a, b) -> a + "\n" + b);
            assertTrue(logged.contains("ORA-00904"), "vendor code must be logged: " + logged);
            assertTrue(logged.contains("BadSqlGrammarException"), "exception type must be logged: " + logged);
            assertTrue(logged.contains("diag-trace"), "diagnostic must carry the trace id: " + logged);
            assertFalse(logged.contains(SENSITIVE_MARKER), "raw message/SQL must not be logged");
            appender.list.forEach(event -> assertNull(event.getThrowableProxy()));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void unexpectedFailureLogsExceptionTypesWithoutRawDetails() {
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            CorrelationIdHolder.set("generic-trace");
            handler.handleGenericException(new IllegalStateException(SENSITIVE_MARKER, new NullPointerException(SENSITIVE_MARKER)));
            String logged = appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (a, b) -> a + "\n" + b);
            assertTrue(logged.contains("IllegalStateException>NullPointerException"), "exception types must be logged: " + logged);
            assertTrue(logged.contains("generic-trace"), logged);
            assertFalse(logged.contains(SENSITIVE_MARKER), "raw messages must not be logged");
            appender.list.forEach(event -> assertNull(event.getThrowableProxy()));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"ORA-00001", "ORA-02292"})
    void conflictResponsesKeepExistingStatusAndNeverLeakRawErrors(String oracleError) {
        Map<String, Object> body = handler.handleDatabaseException(
                new DataAccessResourceFailureException(oracleError + " " + SENSITIVE_MARKER)).getBody();
        assertEquals(409, body.get("status"));
        assertEquals(CONTRACT_KEYS, body.keySet());
        assertFalse(body.toString().contains(SENSITIVE_MARKER));
    }

    @RestController
    static class FailingController {
        @GetMapping("/test/database-failure")
        public void fail() {
            throw new DataAccessResourceFailureException("ORA-28112 predicate=" + SENSITIVE_MARKER);
        }
    }
}
