package com.saed.backend.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C-03 / F00-03: production-like runtime configuration must fail closed. Offline: no Spring
 * application context, no database.
 */
class ProductionConfigurationSafetyTest {

    private static final String DEV_JWT_SECRET = "c2VjcmV0LWtleS1kZWJlLXNlci1sYXJnYS1wYXJhLWhtYWMtc2hhMjU2";
    private static final String STRONG_JWT_SECRET = "a-production-only-jwt-secret-with-more-than-32-bytes";

    private static MockEnvironment completeLiveProduction() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("jwt.secret", STRONG_JWT_SECRET);
        env.setProperty("spring.datasource.url", "jdbc:oracle:thin:@//db.example:1521/SAED");
        env.setProperty("spring.datasource.username", "saed_app");
        env.setProperty("spring.datasource.password", "not-a-real-password");
        env.setProperty("saed.wompi.environment", "LIVE");
        env.setProperty("saed.wompi.public-key", "pub_prod_abc");
        env.setProperty("saed.wompi.integrity-secret", "prod_integrity_abc");
        env.setProperty("saed.wompi.events-secret", "prod_events_abc");
        env.setProperty("saed.cors.allowed-origins", "https://app.example.com");
        env.setProperty("app.frontend.url", "https://app.example.com");
        return env;
    }

    private static String violationsOf(MockEnvironment env) {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> new ProductionConfigurationGuard(env));
        return failure.getMessage();
    }

    @Test
    void prodProfileWithoutSecretsFailsStartup() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        String message = violationsOf(env);

        assertTrue(message.contains("jwt.secret"), message);
        assertTrue(message.contains("spring.datasource.url"), message);
        assertTrue(message.contains("spring.datasource.username"), message);
        assertTrue(message.contains("spring.datasource.password"), message);
        assertTrue(message.contains("saed.wompi.environment"), message);
        assertTrue(message.contains("saed.wompi.public-key"), message);
        assertTrue(message.contains("saed.wompi.integrity-secret"), message);
        assertTrue(message.contains("saed.wompi.events-secret"), message);
        assertTrue(message.contains("saed.cors.allowed-origins"), message);
        assertTrue(message.contains("app.frontend.url"), message);
    }

    @Test
    void prodRejectsLocalhostActivationLinks() {
        MockEnvironment env = completeLiveProduction();
        env.setProperty("app.frontend.url", "http://localhost:5173");

        assertTrue(violationsOf(env).contains("app.frontend.url"));
    }

    @Test
    void unresolvablePlaceholdersCountAsMissingInsteadOfCrashingTheGuard() {
        MockEnvironment env = completeLiveProduction();
        env.setProperty("jwt.secret", "${JWT_SECRET}");

        String message = violationsOf(env);

        assertTrue(message.contains("jwt.secret"), message);
    }

    @Test
    void noActiveProfileIsTreatedAsProduction() {
        MockEnvironment env = new MockEnvironment();

        String message = violationsOf(env);

        assertTrue(message.contains("jwt.secret"), message);
    }

    @Test
    void prodRejectsTheRepositoryPublishedDevelopmentJwtSecret() {
        MockEnvironment env = completeLiveProduction();
        env.setProperty("jwt.secret", DEV_JWT_SECRET);

        assertTrue(violationsOf(env).contains("jwt.secret"));
    }

    @Test
    void prodRejectsWildcardCors() {
        MockEnvironment env = completeLiveProduction();
        env.setProperty("saed.cors.allowed-origins", "https://app.example.com, *");

        assertTrue(violationsOf(env).contains("saed.cors.allowed-origins"));
    }

    @Test
    void liveEnvironmentRejectsSandboxKeys() {
        MockEnvironment env = completeLiveProduction();
        env.setProperty("saed.wompi.public-key", "pub_test_abc");

        assertTrue(violationsOf(env).contains("saed.wompi.public-key"));
    }

    @Test
    void sandboxEnvironmentRejectsLiveKeys() {
        MockEnvironment env = completeLiveProduction();
        env.setProperty("saed.wompi.environment", "SANDBOX");
        env.setProperty("saed.wompi.public-key", "pub_test_abc");
        env.setProperty("saed.wompi.integrity-secret", "test_integrity_abc");
        env.setProperty("saed.wompi.events-secret", "prod_events_abc");

        assertTrue(violationsOf(env).contains("saed.wompi.events-secret"));
    }

    @Test
    void productionRejectsSandboxSecretsPublishedInTheRepository() {
        MockEnvironment env = completeLiveProduction();
        env.setProperty("saed.wompi.environment", "SANDBOX");
        env.setProperty("saed.wompi.public-key", "pub_test_IZg6dmwtip4WYXjPP8G7zYvWzCU5wRaH");
        env.setProperty("saed.wompi.integrity-secret", "test_integrity_CT3taBwOqVtSQITYMGUTcrJftHdoLoPQ");
        env.setProperty("saed.wompi.events-secret", "test_events_VgrGtdTPd9q6XKtBz3iRTgTHJSUuwBBy");

        String message = violationsOf(env);

        assertTrue(message.contains("saed.wompi.integrity-secret"), message);
        assertTrue(message.contains("saed.wompi.events-secret"), message);
    }

    @Test
    void explicitSandboxWithPrivateSandboxSecretsIsAccepted() {
        MockEnvironment env = completeLiveProduction();
        env.setProperty("saed.wompi.environment", "SANDBOX");
        env.setProperty("saed.wompi.public-key", "pub_test_private");
        env.setProperty("saed.wompi.integrity-secret", "test_integrity_private");
        env.setProperty("saed.wompi.events-secret", "test_events_private");

        assertDoesNotThrow(() -> new ProductionConfigurationGuard(env));
    }

    @Test
    void completeLiveProductionConfigurationStarts() {
        assertDoesNotThrow(() -> new ProductionConfigurationGuard(completeLiveProduction()));
    }

    @Test
    void violationMessagesNeverEchoSecretValues() {
        MockEnvironment env = completeLiveProduction();
        env.setProperty("saed.wompi.events-secret", "test_events_should_not_leak");

        String message = violationsOf(env);

        assertFalse(message.contains("should_not_leak"), message);
    }

    @Test
    void explicitDevelopmentAndTestProfilesKeepTheirDefaults() {
        for (String profile : List.of("dev", "test", "isolated")) {
            MockEnvironment env = new MockEnvironment();
            env.setActiveProfiles(profile);
            assertDoesNotThrow(() -> new ProductionConfigurationGuard(env), profile);
        }
    }

    @Test
    void prodWinsWhenCombinedWithADevelopmentProfile() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev", "prod");

        assertTrue(violationsOf(env).contains("jwt.secret"));
    }

    @Test
    void applicationYamlDefaultsToProdAndKeepsFallbacksOnlyInTheDevProfile() throws IOException {
        List<PropertySource<?>> documents = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"));

        String base = rawValue(documents, null, "spring.profiles.active");
        assertEquals("${SPRING_PROFILES_ACTIVE:prod}", base, "unset SPRING_PROFILES_ACTIVE must not select dev");

        assertEquals("${JWT_SECRET}", rawValue(documents, "prod", "jwt.secret"));
        assertEquals("${DB_URL}", rawValue(documents, "prod", "spring.datasource.url"));
        assertEquals("${DB_USERNAME}", rawValue(documents, "prod", "spring.datasource.username"));
        assertEquals("${DB_PASSWORD}", rawValue(documents, "prod", "spring.datasource.password"));
        assertFalse(hasValue(documents, "prod", "saed.wompi.events-secret"),
                "prod must not override the empty base default with a fallback");
        assertFalse(hasValue(documents, "prod", "saed.cors.allowed-origins"));

        assertEquals("", resolvedDefault(rawValue(documents, null, "saed.wompi.events-secret")),
                "outside dev the Wompi secrets have no fallback");
        assertEquals("", resolvedDefault(rawValue(documents, null, "saed.cors.allowed-origins")),
                "outside dev CORS allows no origin by default");
        assertTrue(rawValue(documents, "dev", "jwt.secret").contains(DEV_JWT_SECRET),
                "the published development JWT secret is confined to the dev profile");
        assertEquals("SANDBOX", resolvedDefault(rawValue(documents, "dev", "saed.wompi.environment")));
    }

    @Test
    void mainSourcesContainNoHardcodedWompiSecrets() throws IOException {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Paths.get("src", "main", "java"))) {
            // The guard is exempt: it names the key prefixes it validates (no key values).
            for (Path file : files.filter(p -> p.toString().endsWith(".java")
                    && !p.endsWith("ProductionConfigurationGuard.java")).toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                if (source.contains("pub_test_") || source.contains("test_integrity_")
                        || source.contains("test_events_") || source.contains("System.getenv(\"WOMPI_")) {
                    offenders.add(file.getFileName().toString());
                }
            }
        }
        assertTrue(offenders.isEmpty(), "Wompi keys must come only from configuration: " + offenders);
        String guard = Files.readString(Paths.get("src", "main", "java", "com", "saed", "backend", "config",
                "ProductionConfigurationGuard.java"), StandardCharsets.UTF_8);
        assertFalse(guard.contains("IZg6dmwtip4") || guard.contains("CT3taBwOq") || guard.contains("VgrGtdTPd9")
                || guard.contains(DEV_JWT_SECRET), "the guard stores digests, not published secrets");
    }

    @Test
    void corsWithoutConfiguredOriginsAllowsNoCrossOriginRequest() {
        SecurityConfig config = new SecurityConfig(null, null, null, "");
        CorsConfiguration cors = config.corsConfigurationSource()
                .getCorsConfiguration(new MockHttpServletRequest("GET", "/api/v1/ping"));

        assertTrue(cors.getAllowedOrigins() == null || cors.getAllowedOrigins().isEmpty());
        assertNull(cors.checkOrigin("https://evil.example"));
    }

    @Test
    void corsUsesOnlyConfiguredOrigins() {
        SecurityConfig config = new SecurityConfig(null, null, null, "https://a.example, https://b.example");
        CorsConfiguration cors = config.corsConfigurationSource()
                .getCorsConfiguration(new MockHttpServletRequest("GET", "/api/v1/ping"));

        assertEquals(List.of("https://a.example", "https://b.example"), cors.getAllowedOrigins());
        assertNull(cors.checkOrigin("https://evil.example"));
    }

    private static String rawValue(List<PropertySource<?>> documents, String profile, String key) {
        for (PropertySource<?> document : documents) {
            Object onProfile = document.getProperty("spring.config.activate.on-profile");
            boolean matches = profile == null ? onProfile == null : profile.equals(String.valueOf(onProfile));
            if (matches && document.containsProperty(key)) {
                return String.valueOf(document.getProperty(key));
            }
        }
        throw new AssertionError("application.yml has no " + key + " for profile " + profile);
    }

    private static boolean hasValue(List<PropertySource<?>> documents, String profile, String key) {
        try {
            rawValue(documents, profile, key);
            return true;
        } catch (AssertionError absent) {
            return false;
        }
    }

    private static String resolvedDefault(String placeholder) {
        if (!placeholder.startsWith("${")) {
            return placeholder;
        }
        int colon = placeholder.indexOf(':');
        return colon < 0 ? null : placeholder.substring(colon + 1, placeholder.length() - 1);
    }
}
