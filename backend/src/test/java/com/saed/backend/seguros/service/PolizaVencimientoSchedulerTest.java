package com.saed.backend.seguros.service;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/** Insurance expiry job: per-tenant scope, failure isolation, enablement. Pure unit tests. */
class PolizaVencimientoSchedulerTest {

    @AfterEach
    void clearContext() {
        SaedContextHolder.clearContext();
    }

    @Test
    void everyTenantRunsWithItsOwnPropertyContextAndTheContextIsClearedAfterwards() {
        List<SaedContext> seen = new ArrayList<>();
        PolizaVencimientoService service = new PolizaVencimientoService(null, null) {
            @Override
            public int detectarVencimientos(Long idPropiedad, LocalDate hoy) {
                seen.add(SaedContextHolder.getContext());
                return 1;
            }
        };
        PolizaTenantSource source = () -> List.of(
                new PolizaTenantSource.TenantScope(40L, 30L, 60L),
                new PolizaTenantSource.TenantScope(41L, 31L, 61L));

        int total = new PolizaVencimientoScheduler(service, Optional.of(source)).verificarYAlertarVencimientos();

        assertEquals(2, total);
        assertEquals(2, seen.size());
        assertEquals(30L, seen.get(0).getPropertyId());
        assertEquals(40L, seen.get(0).getOrganizationId());
        assertEquals(60L, seen.get(0).getUserId());
        assertEquals("ADMIN_PROPIEDAD", seen.get(0).getRoleCode());
        assertEquals(31L, seen.get(1).getPropertyId());
        assertNull(SaedContextHolder.getContext());
    }

    @Test
    void aFailingTenantDoesNotStopTheOthersAndDoesNotLeakItsContext() {
        List<Long> attempted = new ArrayList<>();
        PolizaVencimientoService service = new PolizaVencimientoService(null, null) {
            @Override
            public int detectarVencimientos(Long idPropiedad, LocalDate hoy) {
                attempted.add(idPropiedad);
                if (idPropiedad == 30L) {
                    throw new IllegalStateException("tenant failure");
                }
                return 2;
            }
        };
        PolizaTenantSource source = () -> List.of(
                new PolizaTenantSource.TenantScope(40L, 30L, 60L),
                new PolizaTenantSource.TenantScope(41L, 31L, 61L));

        assertEquals(2, new PolizaVencimientoScheduler(service, Optional.of(source)).verificarYAlertarVencimientos());

        assertEquals(List.of(30L, 31L), attempted);
        assertNull(SaedContextHolder.getContext());
    }

    @Test
    void aFailingEnumerationIsContainedAndRunsNothing() {
        PolizaVencimientoService service = mock(PolizaVencimientoService.class);
        PolizaTenantSource source = () -> {
            throw new IllegalStateException("database unavailable");
        };

        assertEquals(0, new PolizaVencimientoScheduler(service, Optional.of(source)).verificarYAlertarVencimientos());

        assertNull(SaedContextHolder.getContext());
    }

    @Test
    void theJobIsEnabledByDefaultThroughAnExplicitProperty() throws NoSuchMethodException {
        ConditionalOnProperty condition = PolizaVencimientoScheduler.class.getAnnotation(ConditionalOnProperty.class);
        assertNotNull(condition, "the scheduler must be switchable by a property");
        assertEquals("saed.seguros.vencimiento-enabled", condition.name()[0]);
        assertTrue(condition.matchIfMissing(), "safe default: enabled when the property is absent");
        Method job = PolizaVencimientoScheduler.class.getMethod("verificarYAlertarVencimientos");
        assertNotNull(job.getAnnotation(Scheduled.class));
    }
}
