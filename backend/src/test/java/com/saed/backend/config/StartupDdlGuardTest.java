package com.saed.backend.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * F01-01: schema is owned by versioned migrations (V5.31/V5.32 version the former startup-only objects).
 * Application startup hooks must never execute DDL; only the legacy maintenance/demo runners may contain it,
 * and those must stay disabled in production and off by default.
 */
class StartupDdlGuardTest {

    private static final Path MAIN = Path.of("src", "main", "java");
    private static final Pattern STARTUP_HOOK = Pattern.compile(
            "@PostConstruct|implements\\s+(ApplicationRunner|CommandLineRunner)|ApplicationReadyEvent");
    private static final Pattern DDL = Pattern.compile(
            "CREATE\\s+(TABLE|INDEX|UNIQUE\\s+INDEX|OR\\s+REPLACE)|ALTER\\s+TABLE|DROP\\s+(TABLE|CONSTRAINT|INDEX)|DBMS_RLS",
            Pattern.CASE_INSENSITIVE);
    private static final Set<String> GATED_LEGACY_RUNNERS = Set.of(
            "ProductionSchemaInitializer.java", "DatabaseSeeder.java");

    @Test
    void startupHooksNeverExecuteDdl() throws IOException {
        List<String> offenders;
        try (Stream<Path> files = Files.walk(MAIN)) {
            offenders = files.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !GATED_LEGACY_RUNNERS.contains(p.getFileName().toString()))
                    .filter(p -> {
                        String source = read(p);
                        return STARTUP_HOOK.matcher(source).find() && DDL.matcher(source).find();
                    })
                    .map(p -> MAIN.relativize(p).toString())
                    .collect(Collectors.toList());
        }
        assertTrue(offenders.isEmpty(), "Startup hooks must not contain DDL: " + offenders);
    }

    @Test
    void legacyDdlRunnersStayDisabledInProductionAndByDefault() {
        for (String name : GATED_LEGACY_RUNNERS) {
            String source = read(MAIN.resolve(Path.of("com", "saed", "backend", "config", name)));
            assertTrue(source.contains("@Profile(\"!prod\")"), name + " must be excluded from the prod profile");
            assertTrue(source.contains("matchIfMissing = false"), name + " must be off unless explicitly enabled");
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(path.toString(), e);
        }
    }
}
