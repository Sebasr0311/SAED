package com.saed.backend.person;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * DEC-01 guard: PERSONAS has no row-level policy, so tenant isolation depends on PERSONAS.ID_ORGANIZACION being set at
 * every creation point. Each INSERT INTO PERSONAS in the application and in the security-procedure migrations must
 * list the column.
 */
class PersonasInsertOrganizationGuardTest {

    private static final Pattern INSERT = Pattern.compile("INSERT\\s+INTO\\s+PERSONAS\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern GUARDED_MIGRATION = Pattern.compile("V5\\.(66|67|68)__sec_.*\\.sql|V5\\.69__.*\\.sql");
    /** Platform administrators are not tenant persons: they have no organization by design. */
    private static final Set<String> PLATFORM_LEVEL = Set.of("PlatformAdminsController.java");

    @Test
    void everyPersonasInsertInTheApplicationBindsTheOrganization() throws IOException {
        List<String> bad = offenders(Path.of("src", "main", "java"), false);
        assertTrue(bad.isEmpty(), () -> "INSERT INTO PERSONAS without ID_ORGANIZACION: " + bad);
    }

    @Test
    void everyPersonasInsertInTheSecurityMigrationsBindsTheOrganization() throws IOException {
        Path migrations = Path.of("..", "database", "migrations");
        assertTrue(Files.isDirectory(migrations));
        List<String> bad = offenders(migrations, true);
        assertTrue(bad.isEmpty(), () -> "INSERT INTO PERSONAS without ID_ORGANIZACION: " + bad);
    }

    @Test
    void theGuardRecognisesAMissingColumn() {
        String missing = "INSERT INTO PERSONAS (ID_TIPO_DOCUMENTO, ESTADO) VALUES (1, 'A')";
        String present = "INSERT INTO PERSONAS (ID_TIPO_DOCUMENTO, ID_ORGANIZACION) VALUES (1, 2)";
        assertFalse(columnListHasOrganization(missing, 0));
        assertTrue(columnListHasOrganization(present, 0));
    }

    private static List<String> offenders(Path root, boolean migrations) throws IOException {
        List<String> found = new ArrayList<>();
        List<Path> files;
        try (Stream<Path> walk = Files.walk(root)) {
            files = walk.filter(Files::isRegularFile).toList();
        }
        for (Path file : files) {
            String name = file.getFileName().toString();
            boolean candidate = migrations ? GUARDED_MIGRATION.matcher(name).matches() : name.endsWith(".java");
            if (!candidate || PLATFORM_LEVEL.contains(name)) {
                continue;
            }
            String text = Files.readString(file, StandardCharsets.UTF_8);
            Matcher m = INSERT.matcher(text);
            while (m.find()) {
                if (!columnListHasOrganization(text, m.start())) {
                    found.add(name + "@" + m.start());
                }
            }
        }
        return found;
    }

    private static boolean columnListHasOrganization(String text, int from) {
        String upper = text.toUpperCase();
        int values = upper.indexOf("VALUES", from);
        int select = upper.indexOf("SELECT", from);
        int end = values < 0 ? select : (select < 0 ? values : Math.min(values, select));
        return end > from && upper.substring(from, end).contains("ID_ORGANIZACION");
    }
}
