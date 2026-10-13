package com.saed.backend.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Fails application startup when a production-like profile is missing required secrets or still
 * uses values that are published in this repository (C-03 / F00-03).
 *
 * <p>A profile set is production-like when {@code prod} is active, or when none of the explicit
 * development/verification profiles ({@code dev}, {@code test}, {@code isolated}) is active. An
 * unset profile therefore never inherits development fallbacks. Violation messages name the
 * property only and never echo its value.
 */
@Component
public class ProductionConfigurationGuard {

    static final Set<String> NON_PRODUCTION_PROFILES = Set.of("dev", "test", "isolated");

    /**
     * SHA-256 digests of values committed to this repository (the development JWT secret and the
     * public Wompi sandbox keys); they must never protect a production-like runtime. Stored as
     * digests so that main sources contain no usable secret.
     */
    private static final Set<String> PUBLISHED_SECRET_DIGESTS = Set.of(
            "fc3a994d2f823741899e29fa4b15aa7d067fc5245606b0f723113366ce987973",
            "1d6636526a407374d35bece49131e1adab5f8e17039f1a09169eb7a81c7dc921",
            "a9a2b88b778dcf91b4b6b26a248a970d6386b11a1d1f52b3df51ac0af79797bd",
            "2c3439f6960ade4be6ae63234b448c6a862e19ba86ca82f4c5d66e5653c4eb02");

    private static final int MIN_JWT_SECRET_BYTES = 32;

    public ProductionConfigurationGuard(Environment environment) {
        if (!isProductionLike(environment.getActiveProfiles())) {
            return;
        }
        List<String> violations = violations(environment);
        if (!violations.isEmpty()) {
            throw new IllegalStateException("Production configuration is incomplete or unsafe; refusing to start. "
                    + String.join("; ", violations));
        }
    }

    static boolean isProductionLike(String[] activeProfiles) {
        List<String> profiles = Arrays.asList(activeProfiles);
        if (profiles.contains("prod")) {
            return true;
        }
        return profiles.stream().noneMatch(NON_PRODUCTION_PROFILES::contains);
    }

    private static List<String> violations(Environment env) {
        List<String> violations = new ArrayList<>();

        String jwt = read(env, "jwt.secret");
        if (jwt == null) {
            violations.add("jwt.secret (JWT_SECRET) is required");
        } else if (isPublished(jwt)) {
            violations.add("jwt.secret uses the development secret published in the repository");
        } else if (jwt.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < MIN_JWT_SECRET_BYTES) {
            violations.add("jwt.secret must be at least " + MIN_JWT_SECRET_BYTES + " bytes");
        }

        requirePresent(env, violations, "spring.datasource.url", "DB_URL");
        requirePresent(env, violations, "spring.datasource.username", "DB_USERNAME");
        requirePresent(env, violations, "spring.datasource.password", "DB_PASSWORD");

        validateWompi(env, violations);

        String cors = read(env, "saed.cors.allowed-origins");
        if (cors == null) {
            violations.add("saed.cors.allowed-origins (CORS_ALLOWED_ORIGINS) is required");
        } else if (Arrays.stream(cors.split(",")).map(String::trim).anyMatch(o -> o.contains("*"))) {
            violations.add("saed.cors.allowed-origins must list explicit origins, not a wildcard");
        }

        // Activation and password-reset links are built from this URL; a localhost fallback would
        // send users unusable links.
        String frontend = read(env, "app.frontend.url");
        if (frontend == null) {
            violations.add("app.frontend.url (APP_FRONTEND_URL) is required");
        } else if (frontend.contains("localhost") || frontend.contains("127.0.0.1")) {
            violations.add("app.frontend.url must be the public frontend URL, not a local address");
        }
        return violations;
    }

    private static void validateWompi(Environment env, List<String> violations) {
        String mode = read(env, "saed.wompi.environment");
        WompiMode wompiMode = WompiMode.parse(mode);
        if (wompiMode == null) {
            violations.add("saed.wompi.environment (WOMPI_ENVIRONMENT) must be SANDBOX or LIVE");
        }
        checkWompiKey(env, violations, wompiMode, "saed.wompi.public-key", "WOMPI_PUBLIC_KEY", "pub_test_", "pub_prod_");
        checkWompiKey(env, violations, wompiMode, "saed.wompi.integrity-secret", "WOMPI_INTEGRITY_SECRET",
                "test_integrity_", "prod_integrity_");
        checkWompiKey(env, violations, wompiMode, "saed.wompi.events-secret", "WOMPI_EVENTS_SECRET",
                "test_events_", "prod_events_");
    }

    private static void checkWompiKey(Environment env, List<String> violations, WompiMode mode,
                                      String property, String variable, String sandboxPrefix, String livePrefix) {
        String value = read(env, property);
        if (value == null) {
            violations.add(property + " (" + variable + ") is required");
            return;
        }
        if (isPublished(value)) {
            violations.add(property + " uses the sandbox key published in the repository");
            return;
        }
        if (mode == WompiMode.LIVE && !value.startsWith(livePrefix)) {
            violations.add(property + " must be a live key (" + livePrefix + "...) when saed.wompi.environment=LIVE");
        } else if (mode == WompiMode.SANDBOX && !value.startsWith(sandboxPrefix)) {
            violations.add(property + " must be a sandbox key (" + sandboxPrefix + "...) when saed.wompi.environment=SANDBOX");
        }
    }

    private static void requirePresent(Environment env, List<String> violations, String property, String variable) {
        if (read(env, property) == null) {
            violations.add(property + " (" + variable + ") is required");
        }
    }

    private static boolean isPublished(String value) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return PUBLISHED_SECRET_DIGESTS.contains(java.util.HexFormat.of().formatHex(digest));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    /** Returns the trimmed value, or null when absent, blank or an unresolvable placeholder. */
    private static String read(Environment env, String property) {
        String value;
        try {
            value = env.getProperty(property);
        } catch (IllegalArgumentException unresolvablePlaceholder) {
            return null;
        }
        if (value == null || value.isBlank() || value.contains("${")) {
            return null;
        }
        return value.trim();
    }

    /** Explicit separation between Wompi sandbox and live credentials. */
    public enum WompiMode {
        SANDBOX, LIVE;

        static WompiMode parse(String value) {
            if (value == null) {
                return null;
            }
            try {
                return WompiMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException unknownMode) {
                return null;
            }
        }
    }
}
