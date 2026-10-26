package com.saed.backend.config;

import com.saed.backend.platform.controller.PlatformDashboardController;
import com.saed.backend.dashboard.service.AnalyticsService;
import java.util.Arrays;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.verifyNoInteractions;

/** Offline regression checks: no Spring application or database connection is started. */
class StartupSafetyTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(JdbcTemplate.class, () -> jdbc)
            .withUserConfiguration(ProductionSchemaInitializer.class, DatabaseSeeder.class);

    @Test
    void ordinaryStartupDoesNotRunDatabaseMaintenanceOrDemoSeeding() {
        contextRunner.run(context -> {
            assertTrue(context.getBeansOfType(ProductionSchemaInitializer.class).isEmpty());
            assertTrue(context.getBeansOfType(DatabaseSeeder.class).isEmpty());
            context.getBeansOfType(org.springframework.boot.ApplicationRunner.class).values()
                    .forEach(runner -> {
                        try {
                            runner.run(new DefaultApplicationArguments());
                        } catch (Exception exception) {
                            throw new AssertionError("Offline startup runner failed", exception);
                        }
                    });
            assertFalse(hasExecutedSql("CREATE", "ALTER", "INSERT", "UPDATE", "DELETE", "BEGIN", "SELECT"),
                    "Default startup must not execute JDBC statements");
        });
    }

    @Test
    void explicitMaintenanceNeverPurgesBusinessDataOrProvisionsCredentials() {
        contextRunner.withPropertyValues("saed.database.maintenance.enabled=true").run(context -> {
            ProductionSchemaInitializer initializer = context.getBean(ProductionSchemaInitializer.class);
            initializer.run(new DefaultApplicationArguments());
            assertFalse(hasExecutedSql("DELETE FROM ORGANIZACIONES", "DELETE FROM PROPIEDADES",
                    "PASSWORD_HASH", "MERGE INTO USUARIOS", "UPDATE USUARIOS", "DISABLE ALL TRIGGERS"),
                    "Maintenance must preserve business data and privileged credentials");
            assertTrue(hasExecutedSql("CREATE"), "Legacy schema maintenance remains available explicitly");
        });
    }

    @Test
    void explicitMaintenancePreservesExistingPeopleUsersAndTenantTemplates() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(1);
        contextRunner.withPropertyValues("saed.database.maintenance.enabled=true").run(context -> {
            context.getBean(ProductionSchemaInitializer.class).run(new DefaultApplicationArguments());
            assertFalse(hasExecutedSql("DELETE FROM USUARIOS", "DELETE FROM PERSONAS",
                    "DELETE FROM PLANTILLAS_CONTRATOS"),
                    "Explicit legacy maintenance must not delete existing identities or tenant templates");
        });
    }

    @Test
    void explicitDemoSeedingNeverChangesAccountsOrDropsSecurityPolicies() {
        contextRunner.withPropertyValues("saed.database.demo-seeding.enabled=true").run(context -> {
            context.getBean(DatabaseSeeder.class).run(new DefaultApplicationArguments());
            assertFalse(hasExecutedSql("PASSWORD_HASH", "INSERT INTO USUARIOS", "UPDATE USUARIOS",
                    "DROP_POLICY", "DROP_GROUPED_POLICY", "INSERT INTO USUARIO_ASIGNACIONES"),
                    "Demo seeding must not change credentials, grant privileges or remove RLS");
        });
    }

    @Test
    void productionNeverActivatesDemoSeederEvenWhenRequested() {
        contextRunner.withPropertyValues("spring.profiles.active=prod,dev",
                "saed.database.demo-seeding.enabled=true").run(context ->
                assertTrue(context.getBeansOfType(DatabaseSeeder.class).isEmpty()));
    }

    @Test
    void productionDefaultDoesNotRegisterSchemaMaintenance() {
        contextRunner.withPropertyValues("spring.profiles.active=prod").run(context -> {
            assertTrue(context.getBeansOfType(ProductionSchemaInitializer.class).isEmpty());
            assertTrue(context.getBeansOfType(DatabaseSeeder.class).isEmpty());
        });
    }

    @Test
    void productionNeverRegistersLegacyMaintenanceEvenWithBothFlagsAndDevProfile() {
        contextRunner.withPropertyValues("spring.profiles.active=prod,dev",
                "saed.database.maintenance.enabled=true", "saed.database.demo-seeding.enabled=true")
                .run(context -> {
                    assertTrue(context.getBeansOfType(ProductionSchemaInitializer.class).isEmpty());
                    assertTrue(context.getBeansOfType(DatabaseSeeder.class).isEmpty());
                });
    }

    @Test
    void existingCatalogDoesNotTriggerAccountRecoveryOrPrivilegeChanges() {
        when(jdbc.queryForObject("SELECT COUNT(1) FROM ROLES WHERE CODIGO = 'RESIDENTE_CONVIVENCIA'", Integer.class))
                .thenReturn(1);
        contextRunner.withPropertyValues("saed.database.demo-seeding.enabled=true").run(context -> {
            context.getBean(DatabaseSeeder.class).run(new DefaultApplicationArguments());
            assertFalse(hasExecutedSql("USUARIOS", "PERSONAS", "USUARIO_ASIGNACIONES", "ADMINISTRADORES_SAED"),
                    "Existing users, identities and permissions must remain untouched");
        });
    }

    @Test
    void removedPurgeEndpointReturnsNotFound() throws Exception {
        PlatformDashboardController controller = new PlatformDashboardController(
                mock(NamedParameterJdbcTemplate.class), mock(AnalyticsService.class));
        MockMvcBuilders.standaloneSetup(controller).build()
                .perform(post("/api/v1/platform/dashboard/purge-demo-seed"))
                .andExpect(status().isNotFound());
    }

    @Test
    void purgeCannotBeInvokedThroughApplicationApiOrInitializer() {
        assertFalse(Arrays.stream(PlatformDashboardController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(PostMapping.class))
                .map(method -> method.getAnnotation(PostMapping.class))
                .flatMap(mapping -> Arrays.stream(mapping.value()))
                .anyMatch(path -> path.contains("purge")), "No purge API may remain reachable");
        assertFalse(Arrays.stream(ProductionSchemaInitializer.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().toLowerCase(Locale.ROOT).contains("purge")),
                "Destructive purge routines must be removed, rather than hidden from startup");
    }

    private boolean hasExecutedSql(String... fragments) {
        return mockingDetails(jdbc).getInvocations().stream()
                .flatMap(invocation -> Arrays.stream(invocation.getArguments()))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(sql -> sql.toUpperCase(Locale.ROOT))
                .anyMatch(sql -> Arrays.stream(fragments).anyMatch(sql::contains));
    }
}
