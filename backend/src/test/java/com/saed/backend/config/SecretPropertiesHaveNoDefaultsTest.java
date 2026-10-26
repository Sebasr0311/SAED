package com.saed.backend.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.env.PropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The repository is public: no secret, key, password or token property may carry a usable value in
 * ANY profile of the packaged configuration. A secret must either be a bare placeholder
 * ({@code ${NAME}}) or a placeholder with an empty default ({@code ${NAME:}}). Offline: reads the
 * YAML files directly, no Spring context. Values are never echoed in failure messages.
 */
class SecretPropertiesHaveNoDefaultsTest {

    private static final Pattern SENSITIVE_KEY =
            Pattern.compile("(?i)(secret|password|passwd|token|api-?key|private-?key|public-?key|\\bkey\\b|-key$)");
    private static final Pattern PLACEHOLDER = Pattern.compile("^\\$\\{([A-Za-z0-9_.\\-]+)(:(.*))?}$");

    @Test
    void noSecretPropertyHasANonEmptyDefaultInAnyProfile() throws IOException {
        List<String> offenders = new ArrayList<>();
        List<Path> files = configFiles();
        assertFalse(files.isEmpty(), "no application*.yml found under src/main/resources");
        PropertySourceLoader loader = new YamlPropertySourceLoader();
        for (Path file : files) {
            List<PropertySource<?>> documents = loader.load(file.getFileName().toString(),
                    new FileSystemResource(file));
            for (PropertySource<?> document : documents) {
                if (!(document instanceof EnumerablePropertySource<?> enumerable)) {
                    continue;
                }
                for (String key : enumerable.getPropertyNames()) {
                    if (!SENSITIVE_KEY.matcher(key).find()) {
                        continue;
                    }
                    Object raw = enumerable.getProperty(key);
                    if (raw != null && hasUsableValue(String.valueOf(raw))) {
                        offenders.add(file.getFileName() + " -> " + key);
                    }
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                "secret properties must have no non-empty default (use ${NAME} or ${NAME:}): " + offenders);
    }

    @Test
    void theDetectorFlagsLiteralsAndNonEmptyDefaultsButNotBarePlaceholders() {
        assertTrue(hasUsableValue("literal-value"));
        assertTrue(hasUsableValue("${SOME_SECRET:fallback}"));
        assertFalse(hasUsableValue("${SOME_SECRET}"));
        assertFalse(hasUsableValue("${SOME_SECRET:}"));
        assertFalse(hasUsableValue(""));
    }

    private static boolean hasUsableValue(String raw) {
        String value = raw.trim();
        if (value.isEmpty()) {
            return false;
        }
        Matcher placeholder = PLACEHOLDER.matcher(value);
        if (placeholder.matches()) {
            String fallback = placeholder.group(3);
            return fallback != null && !fallback.isBlank();
        }
        return true;
    }

    private static List<Path> configFiles() throws IOException {
        try (Stream<Path> files = Files.list(Paths.get("src", "main", "resources"))) {
            return files.filter(p -> {
                String name = p.getFileName().toString();
                return name.startsWith("application") && (name.endsWith(".yml") || name.endsWith(".yaml"));
            }).toList();
        }
    }
}
