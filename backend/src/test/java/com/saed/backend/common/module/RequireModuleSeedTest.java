package com.saed.backend.common.module;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Every module code guarded by {@code @RequireModule} must be seeded by some migration; otherwise the endpoint
 * answers 403 to every role (INCIDENTES was missing from V5.13).
 */
class RequireModuleSeedTest {

    private static final Pattern ANNOTATION = Pattern.compile("@RequireModule[(]\\s*\"([A-Z_]+)\"\\s*[)]");

    @Test
    void everyRequiredModuleCodeIsSeededByAMigration() throws IOException {
        Set<String> required = new TreeSet<>();
        try (Stream<Path> files = Files.walk(Path.of("src", "main", "java"))) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                Matcher m = ANNOTATION.matcher(Files.readString(file, StandardCharsets.UTF_8));
                while (m.find()) {
                    required.add(m.group(1));
                }
            }
        }
        assertThat(required).as("@RequireModule codes found in main sources").isNotEmpty();

        StringBuilder migrations = new StringBuilder();
        try (Stream<Path> files = Files.list(Path.of("..", "database", "migrations"))) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".sql"))::iterator) {
                // Latin-1 never fails to decode; only ASCII module codes are matched.
                migrations.append(Files.readString(file, StandardCharsets.ISO_8859_1)).append('\n');
            }
        }
        String sql = migrations.toString();
        for (String code : required) {
            assertThat(sql).as("module %s must be seeded into MODULOS by a migration", code)
                    .contains("'" + code + "'");
        }
    }

    @Test
    void incidentesIsSeededWithPlanEntitlements() throws IOException {
        String sql = Files.readString(
                Path.of("..", "database", "migrations", "V5.90__seed_modulo_incidentes.sql"), StandardCharsets.UTF_8);
        assertThat(sql).contains("INSERT INTO MODULOS").contains("INSERT INTO PLAN_MODULOS").doesNotContain("MERGE ");
    }
}
