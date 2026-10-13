package com.saed.backend.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The repository is public: packaged sources and resources must not embed real personal mailboxes.
 * Seeds and fixtures use the reserved {@code example.invalid} domain. Offline source scan; the
 * matched text is never echoed, only the file name.
 */
class MainSourcesContainNoPersonalDataTest {

    private static final Pattern PERSONAL_MAILBOX = Pattern.compile(
            "[A-Za-z0-9._%+-]+@(gmail|googlemail|hotmail|outlook|live|yahoo|icloud|protonmail)\\.[a-z.]+",
            Pattern.CASE_INSENSITIVE);

    @Test
    void mainJavaAndResourcesEmbedNoPersonalMailboxes() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path root : List.of(Paths.get("src", "main", "java"), Paths.get("src", "main", "resources"))) {
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.filter(Files::isRegularFile).toList()) {
                    String name = file.getFileName().toString();
                    if (!(name.endsWith(".java") || name.endsWith(".yml") || name.endsWith(".sql")
                            || name.endsWith(".html") || name.endsWith(".properties"))) {
                        continue;
                    }
                    String source = Files.readString(file, StandardCharsets.UTF_8);
                    if (PERSONAL_MAILBOX.matcher(source).find()) {
                        offenders.add(name);
                    }
                }
            }
        }
        assertTrue(offenders.isEmpty(), "personal mailboxes must not be embedded in main sources: " + offenders);
    }
}
