package com.saed.backend.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Tests run against a shared database and must never alter its schema: constraints, columns and identities are
 * owned by the versioned migrations. Fixtures use valid values instead of widening a constraint.
 */
class TestSchemaImmutabilityGuardTest {

    private static final Path TESTS = Path.of("src", "test", "java");
    private static final Pattern DDL_CALL = Pattern.compile(
            "\\.(execute|update|batchUpdate)\\(\\s*\"\\s*(ALTER|DROP|CREATE|TRUNCATE)\\s",
            Pattern.CASE_INSENSITIVE);

    @Test
    void testsNeverExecuteSchemaChanges() throws IOException {
        List<String> offenders;
        try (Stream<Path> files = Files.walk(TESTS)) {
            offenders = files.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> DDL_CALL.matcher(read(p)).find())
                    .map(p -> TESTS.relativize(p).toString())
                    .collect(Collectors.toList());
        }
        assertTrue(offenders.isEmpty(), "Tests must not execute DDL against the shared database: " + offenders);
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + path, e);
        }
    }
}
