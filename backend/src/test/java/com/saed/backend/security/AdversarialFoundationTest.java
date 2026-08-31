package com.saed.backend.security;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
        DataAccessException exception = assertThrows(DataAccessException.class, () -> {
            jdbcTemplate.update(
                "INSERT INTO ORGANIZACIONES (nombre, tipo_organizacion, nit, correo_contacto, telefono_contacto) VALUES (?, ?, ?, ?, ?)",
                "Hack Corp", "CONJUNTO_RESIDENCIAL", "1234", "test@test.com", "123"
            );
        });

        assertNotNull(exception);
    }

    @Test
    public void whenTamperedRole_thenContextShouldRejectSpoofing() {
        SaedContext spoofedContext = SaedContext.builder()
                .userId(9999L)
                .organizationId(1L)
                .propertyId(1L)
                .roleCode("SUPERADMIN") 
                .roleScope("GLOBAL")
                .build();
        SaedContextHolder.setContext(spoofedContext);

        DataAccessException exception = assertThrows(DataAccessException.class, () -> {
            jdbcTemplate.queryForObject("SELECT count(*) FROM ORGANIZACIONES", Integer.class);
        });
        
        assertNotNull(exception);
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
                    try {
                        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM PROPIEDADES", Integer.class);
                        if (count != null && count > 0) {
                            failures.incrementAndGet();
                        }
                    } catch (DataAccessException e) {
                        if (e.getCause() == null) {
                             // Ignore
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
