package com.saed.backend.common.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The health endpoint reflects database reachability and never exposes connection details. */
class HealthControllerTest {

    private static final String PROBE_SQL = "SELECT 1 FROM DUAL";

    @Test
    void reportsUpWhenTheDatabaseAnswers() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(PROBE_SQL, Integer.class)).thenReturn(1);

        ResponseEntity<Map<String, Object>> response = controller(jdbc, Duration.ofSeconds(2)).checkHealth();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("UP", response.getBody().get("status"));
        assertEquals("UP", response.getBody().get("database"));
    }

    @Test
    void reportsServiceUnavailableWithoutDetailsWhenTheDatabaseIsDown() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(PROBE_SQL, Integer.class)).thenThrow(new CannotGetJdbcConnectionException(
                "Failed to obtain connection jdbc:oracle:thin:@db.internal:1521/SAED user=SAED_APP pwd=hunter2"));

        ResponseEntity<Map<String, Object>> response = controller(jdbc, Duration.ofSeconds(2)).checkHealth();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("DOWN", response.getBody().get("status"));
        assertEquals("DOWN", response.getBody().get("database"));
        String body = response.getBody().toString();
        assertFalse(body.contains("jdbc"), body);
        assertFalse(body.contains("hunter2"), body);
        assertFalse(body.contains("SAED_APP"), body);
    }

    @Test
    void reportsServiceUnavailableWhenTheProbeReturnsNothingUseful() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(PROBE_SQL, Integer.class)).thenReturn(null);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, controller(jdbc, Duration.ofSeconds(2)).checkHealth().getStatusCode());
    }

    @Test
    void reportsServiceUnavailableWhenTheDatabaseDoesNotAnswerInTime() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(PROBE_SQL, Integer.class)).thenAnswer(invocation -> {
            Thread.sleep(2_000);
            return 1;
        });

        long started = System.nanoTime();
        ResponseEntity<Map<String, Object>> response = controller(jdbc, Duration.ofMillis(150)).checkHealth();
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertTrue(elapsedMillis < 1_500, "the probe must be bounded, took " + elapsedMillis + " ms");
    }

    @Test
    void pingStaysACheapLivenessAnswerThatDoesNotTouchTheDatabase() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);

        ResponseEntity<Map<String, Object>> response = controller(jdbc, Duration.ofSeconds(2)).ping();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("UP", response.getBody().get("status"));
        org.mockito.Mockito.verifyNoInteractions(jdbc);
    }

    private static HealthController controller(JdbcTemplate jdbc, Duration timeout) {
        return new HealthController(new DatabaseHealthProbe(jdbc, timeout));
    }
}
