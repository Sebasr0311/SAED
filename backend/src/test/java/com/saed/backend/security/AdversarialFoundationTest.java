package com.saed.backend.security;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class AdversarialFoundationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    public void cleanup() {
        SaedContextHolder.clearContext();
    }

    @Test
    public void whenNoContext_thenInsertShouldBeBlockedByRLS() {
        // Attempt to insert without setting context
        DataAccessException exception = assertThrows(DataAccessException.class, () -> {
            jdbcTemplate.update(
                "INSERT INTO ORGANIZACIONES (nombre, tipo_organizacion, nit, correo_contacto, telefono_contacto) VALUES (?, ?, ?, ?, ?)",
                "Hack Corp", "CONJUNTO_RESIDENCIAL", "1234", "test@test.com", "123"
            );
        });

        // It should throw ORA-28115 policy with check option violation (or similar VPD block because context is missing)
        // Wait, if no context is set, the proxy passes null to SET_CONTEXT.
        // PKG_SAED_SESSION will throw ORA-20082 if userId is null, or fail early.
        assertTrue(exception.getCause() != null && exception.getCause().getMessage().contains("ORA-20082"), 
            "Should be blocked before reaching table with ORA-20082 due to missing user in SET_CONTEXT");
    }

    @Test
    public void whenTamperedRole_thenContextShouldRejectSpoofing() {
        SaedContext spoofedContext = SaedContext.builder()
                .userId(1L) // Assuming user 1 exists, but maybe isn't SUPERADMIN
                .organizationId(1L)
                .propertyId(1L)
                .roleCode("SUPERADMIN") // Forging role
                .build();
        SaedContextHolder.setContext(spoofedContext);

        DataAccessException exception = assertThrows(DataAccessException.class, () -> {
            jdbcTemplate.queryForObject("SELECT count(*) FROM ORGANIZACIONES", Integer.class);
        });
        
        // Oracle should validate if user 1 really has SUPERADMIN in ADMINISTRADORES_SAED
        assertTrue(exception.getCause() != null && exception.getCause().getMessage().contains("ORA-2008"), "Should detect role spoofing");
    }

    @Test
    public void testConcurrencyAndContextBleed() throws InterruptedException {
        int threads = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger failures = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    // Try to execute a query WITHOUT context on a thread.
                    // This relies on the fact that connections are reused.
                    // If context bled from another test/thread, this might succeed and see data.
                    // But it should fail or see 0 rows.
                    try {
                        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM PROPIEDADES", Integer.class);
                        // If it doesn't throw, it must return 0 because RLS isolates it.
                        if (count != null && count > 0) {
                            System.err.println("CRITICAL: Context Bleed Detected! Saw " + count + " rows without context.");
                            failures.incrementAndGet();
                        }
                    } catch (DataAccessException e) {
                        // Expected if SET_CONTEXT throws ORA-20082 due to no context.
                        if (e.getCause() == null || !e.getCause().getMessage().contains("ORA-20082")) {
                             System.err.println("Unexpected error: " + e.getMessage());
                             failures.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdownNow();
        assertEquals(0, failures.get(), "Concurrency context bleed or unexpected errors detected");
    }
}
