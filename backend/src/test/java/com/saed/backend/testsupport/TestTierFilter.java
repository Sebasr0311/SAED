package com.saed.backend.testsupport;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.Tag;
import org.junit.platform.engine.FilterResult;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.PostDiscoveryFilter;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.annotation.AnnotatedElementUtils;

/**
 * Splits the suite into tiers without a hand-maintained class list (F00-02).
 *
 * <p>{@code -Dsaed.test.tier=unit} keeps only database-free classes (Mockito, WebMvc slices,
 * source-scanning guards); {@code -Dsaed.test.tier=database} keeps only classes that need the
 * Oracle schema: any {@link SpringBootTest} (direct or meta-annotated) or any class tagged
 * {@code @Tag("database")}. Without the property every class runs. Registered through
 * {@code META-INF/services/org.junit.platform.launcher.PostDiscoveryFilter}.
 */
public class TestTierFilter implements PostDiscoveryFilter {

    public static final String PROPERTY = "saed.test.tier";
    public static final String DATABASE_TAG = "database";

    /** Test tiers selectable from the build. */
    public enum Tier {
        ALL, UNIT, DATABASE;

        public static Tier parse(String value) {
            if (value == null || value.isBlank()) {
                return ALL;
            }
            return Tier.valueOf(value.trim().toUpperCase(Locale.ROOT));
        }

        public boolean includes(Class<?> testClass) {
            return switch (this) {
                case ALL -> true;
                case UNIT -> !requiresDatabase(testClass);
                case DATABASE -> requiresDatabase(testClass);
            };
        }
    }

    private final Tier tier = Tier.parse(System.getProperty(PROPERTY));

    @Override
    public FilterResult apply(TestDescriptor descriptor) {
        Optional<Class<?>> testClass = testClassOf(descriptor);
        if (testClass.isEmpty() || tier.includes(testClass.get())) {
            return FilterResult.included("tier " + tier);
        }
        return FilterResult.excluded("not in test tier " + tier);
    }

    public static boolean requiresDatabase(Class<?> testClass) {
        for (Class<?> c = testClass; c != null; c = c.getEnclosingClass()) {
            if (AnnotatedElementUtils.hasAnnotation(c, SpringBootTest.class)) {
                return true;
            }
            if (Arrays.stream(c.getAnnotationsByType(Tag.class)).anyMatch(t -> DATABASE_TAG.equals(t.value()))) {
                return true;
            }
        }
        return false;
    }

    private static Optional<Class<?>> testClassOf(TestDescriptor descriptor) {
        return descriptor.getSource().flatMap(source -> {
            if (source instanceof ClassSource classSource) {
                return Optional.of(classSource.getJavaClass());
            }
            if (source instanceof MethodSource methodSource) {
                return Optional.of(methodSource.getJavaClass());
            }
            return Optional.empty();
        });
    }
}
