package com.saed.backend.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Isolated-Oracle finding (recovery-13): column defaults use SYSDATE (database server clock, UTC in cloud
 * databases) while some queries compared against CURRENT_DATE (session clock, America/Bogota from the JVM).
 * Between 19:00 and 24:00 Colombia time a row created "today" (UTC date) looked like it started tomorrow, so new
 * assignments were invisible until midnight. Application SQL must use the database clock (SYSDATE) consistently.
 */
class DatabaseClockConsistencyTest {

    private static final Path MAIN = Path.of("src", "main", "java");
    private static final Set<String> GATED_LEGACY_RUNNERS = Set.of(
            "ProductionSchemaInitializer.java", "DatabaseSeeder.java");

    @Test
    void applicationSqlUsesTheDatabaseClockOnly() throws IOException {
        List<String> offenders;
        try (Stream<Path> files = Files.walk(MAIN)) {
            offenders = files.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !GATED_LEGACY_RUNNERS.contains(p.getFileName().toString()))
                    .filter(p -> read(p).contains("CURRENT_DATE"))
                    .map(p -> MAIN.relativize(p).toString())
                    .collect(Collectors.toList());
        }
        assertTrue(offenders.isEmpty(), "Use SYSDATE instead of CURRENT_DATE in application SQL: " + offenders);
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(path.toString(), e);
        }
    }
}
