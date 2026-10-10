package com.saed.backend.testsupport;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** F00-02: the CI unit tier selects every database-free test class automatically. */
class TestTierFilterTest {

    @SpringBootTest
    static class FullContextSample {
    }

    @MetaSpringBootTest
    static class MetaAnnotatedSample {
    }

    @Tag("database")
    static class TaggedDatabaseSample {
    }

    @WebMvcTest
    static class WebSliceSample {
    }

    static class PlainMockitoSample {
    }

    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
    @SpringBootTest
    @interface MetaSpringBootTest {
    }

    @Test
    void springBootTestsAndDatabaseTaggedClassesRequireTheDatabase() {
        assertTrue(TestTierFilter.requiresDatabase(FullContextSample.class));
        assertTrue(TestTierFilter.requiresDatabase(MetaAnnotatedSample.class));
        assertTrue(TestTierFilter.requiresDatabase(TaggedDatabaseSample.class));
    }

    @Test
    void mockitoAndWebSliceTestsBelongToTheUnitTier() {
        assertFalse(TestTierFilter.requiresDatabase(PlainMockitoSample.class));
        assertFalse(TestTierFilter.requiresDatabase(WebSliceSample.class));
    }

    @Test
    void nestedClassesFollowTheirEnclosingTestClass() {
        assertTrue(TestTierFilter.requiresDatabase(NestedHolder.Inner.class));
    }

    @Test
    void tierSelection() {
        assertTrue(TestTierFilter.Tier.UNIT.includes(PlainMockitoSample.class));
        assertFalse(TestTierFilter.Tier.UNIT.includes(FullContextSample.class));
        assertTrue(TestTierFilter.Tier.DATABASE.includes(FullContextSample.class));
        assertFalse(TestTierFilter.Tier.DATABASE.includes(PlainMockitoSample.class));
        assertTrue(TestTierFilter.Tier.ALL.includes(FullContextSample.class));
        assertTrue(TestTierFilter.Tier.ALL.includes(PlainMockitoSample.class));
    }

    @Test
    void unknownTierIsRejectedInsteadOfSilentlyRunningNothing() {
        assertEquals(TestTierFilter.Tier.ALL, TestTierFilter.Tier.parse(null));
        assertEquals(TestTierFilter.Tier.ALL, TestTierFilter.Tier.parse(""));
        assertEquals(TestTierFilter.Tier.UNIT, TestTierFilter.Tier.parse("unit"));
        assertThrows(IllegalArgumentException.class, () -> TestTierFilter.Tier.parse("units"));
    }

    @Test
    void filterIsRegisteredWithTheJUnitPlatform() throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(
                "META-INF/services/org.junit.platform.launcher.PostDiscoveryFilter")) {
            assertNotNull(in, "service registration missing");
            assertTrue(new String(in.readAllBytes(), StandardCharsets.UTF_8).contains(TestTierFilter.class.getName()));
        }
    }

    @SpringBootTest
    static class NestedHolder {
        class Inner {
        }
    }
}
