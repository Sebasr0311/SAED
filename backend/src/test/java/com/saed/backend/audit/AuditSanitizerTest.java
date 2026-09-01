package com.saed.backend.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuditSanitizerTest {

    private AuditSanitizer sanitizer;

    @BeforeEach
    void setUp() {
        sanitizer = new AuditSanitizer(new ObjectMapper());
    }

    @Test
    @DisplayName("Sanitizes password, token, and secret fields in nested maps")
    void testSanitizesSensitiveFieldsInNestedMap() {
        Map<String, Object> input = Map.of(
                "username", "admin_global",
                "password", "SuperSecret123!",
                "metadata", Map.of(
                        "jwt_token", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.dummy",
                        "wompi_signature", "sha256-checksum-12345",
                        "safeField", "This is public info"
                )
        );

        String json = sanitizer.sanitizeToJson(input);

        assertThat(json).contains("\"username\":\"admin_global\"");
        assertThat(json).contains("\"password\":\"[PROTECTED]\"");
        assertThat(json).contains("\"jwt_token\":\"[PROTECTED]\"");
        assertThat(json).contains("\"wompi_signature\":\"[PROTECTED]\"");
        assertThat(json).contains("\"safeField\":\"This is public info\"");

        assertThat(json).doesNotContain("SuperSecret123!");
        assertThat(json).doesNotContain("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9");
        assertThat(json).doesNotContain("sha256-checksum-12345");
    }

    @Test
    @DisplayName("Sanitizes sensitive fields inside collections and arrays")
    void testSanitizesSensitiveFieldsInCollections() {
        List<Map<String, Object>> input = List.of(
                Map.of("id", 1, "apiKey", "sk_live_98765"),
                Map.of("id", 2, "cvv", "123", "cardHolder", "Juan Perez")
        );

        String json = sanitizer.sanitizeToJson(input);

        assertThat(json).contains("\"apiKey\":\"[PROTECTED]\"");
        assertThat(json).contains("\"cvv\":\"[PROTECTED]\"");
        assertThat(json).contains("\"cardHolder\":\"Juan Perez\"");
        assertThat(json).doesNotContain("sk_live_98765");
        assertThat(json).doesNotContain("123\"");
    }

    @Test
    @DisplayName("Sanitizes JSON string inputs containing credentials")
    void testSanitizesJsonString() {
        String rawJson = "{\"email\":\"user@example.com\",\"contrasena\":\"MiClaveSecreta\",\"secretKey\":\"xyz\"}";

        String sanitized = sanitizer.sanitizeToJson(rawJson);

        assertThat(sanitized).contains("\"email\":\"user@example.com\"");
        assertThat(sanitized).contains("\"contrasena\":\"[PROTECTED]\"");
        assertThat(sanitized).contains("\"secretKey\":\"[PROTECTED]\"");
        assertThat(sanitized).doesNotContain("MiClaveSecreta");
    }

    @Test
    @DisplayName("Converts plain text and exception messages to valid JSON object")
    void testPlainTextConvertedToJsonObject() {
        String plain = "DuplicateKeyException: Unique constraint violated";
        String json = sanitizer.sanitizeToJson(plain);

        assertThat(json).isEqualTo("{\"message\":\"DuplicateKeyException: Unique constraint violated\"}");
    }

    @Test
    @DisplayName("Already valid JSON object is not double wrapped")
    void testValidJsonObjectNotDoubleWrapped() {
        String rawJson = "{\"status\":\"ACTIVE\",\"code\":200}";
        String json = sanitizer.sanitizeToJson(rawJson);

        assertThat(json).isEqualTo("{\"status\":\"ACTIVE\",\"code\":200}");
    }

    @Test
    @DisplayName("Null payload returns null")
    void testNullPayloadReturnsNull() {
        String json = sanitizer.sanitizeToJson(null);
        assertThat(json).isNull();
    }
}
