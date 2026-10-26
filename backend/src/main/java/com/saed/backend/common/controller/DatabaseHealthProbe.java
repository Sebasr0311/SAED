package com.saed.backend.common.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Cheap database reachability probe ({@code SELECT 1 FROM DUAL}) with a hard time bound that also covers the wait
 * for a pooled connection. It reports only up or down: failure causes, URLs and credentials are never surfaced.
 */
@Component
public class DatabaseHealthProbe {

    private static final Logger log = LoggerFactory.getLogger(DatabaseHealthProbe.class);
    private static final String PROBE_SQL = "SELECT 1 FROM DUAL";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(3);
    // Dedicated daemon threads: the probe must never borrow the common pool nor keep the JVM alive.
    private static final ExecutorService PROBE_EXECUTOR = Executors.newCachedThreadPool(task -> {
        Thread thread = new Thread(task, "saed-health-probe");
        thread.setDaemon(true);
        return thread;
    });

    private final JdbcTemplate jdbcTemplate;
    private final Duration timeout;

    @Autowired
    public DatabaseHealthProbe(DataSource dataSource) {
        this(probeTemplate(dataSource), DEFAULT_TIMEOUT);
    }

    DatabaseHealthProbe(JdbcTemplate jdbcTemplate, Duration timeout) {
        this.jdbcTemplate = jdbcTemplate;
        this.timeout = timeout;
    }

    private static JdbcTemplate probeTemplate(DataSource dataSource) {
        JdbcTemplate template = new JdbcTemplate(dataSource);
        template.setQueryTimeout((int) DEFAULT_TIMEOUT.toSeconds());
        return template;
    }

    /** Returns true only when the database answered the probe within the time bound. */
    public boolean isUp() {
        CompletableFuture<Integer> probe = CompletableFuture.supplyAsync(
                () -> jdbcTemplate.queryForObject(PROBE_SQL, Integer.class), PROBE_EXECUTOR);
        try {
            Integer answer = probe.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            return answer != null && answer == 1;
        } catch (TimeoutException timedOut) {
            probe.cancel(true);
            log.warn("[Health] Database probe timed out after {} ms", timeout.toMillis());
            return false;
        } catch (ExecutionException failed) {
            log.warn("[Health] Database probe failed ({})", failed.getCause().getClass().getSimpleName());
            return false;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
