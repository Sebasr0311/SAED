package com.saed.backend.common.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** F9-09: evidence links shown to other users must be plain http(s) links or an inline image. */
class EvidenceUrlPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "javascript:alert(1)", "JaVaScRiPt:alert(1)", "data:text/html;base64,PHNjcmlwdD4=", "file:///etc/passwd",
            "vbscript:msgbox", "//evil.example.invalid/x", "ftp://host/file", "https://user:pw@host.example/x",
            "https://", "not a url", "http://ok.example/a javascript:alert(1)", "https://ok.example/\u0000"})
    void unsafeValuesAreRejected(String value) {
        assertThrows(IllegalArgumentException.class, () -> EvidenceUrlPolicy.validate(value));
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://cdn.example.com/a.jpg", "http://host.example/x?y=1#z",
            "https://a.example/1, https://b.example/2", "https://a.example/1\nhttps://b.example/2"})
    void httpLinksAreAccepted(String value) {
        assertEquals(value.trim(), EvidenceUrlPolicy.validate(value));
    }

    @Test
    void blankMeansNoEvidence() {
        assertNull(EvidenceUrlPolicy.validate(null));
        assertNull(EvidenceUrlPolicy.validate("   "));
    }

    @Test
    void inlineImagesAreAcceptedButNotInlineDocuments() {
        assertEquals("data:image/png;base64,iVBORw0KGgo=", EvidenceUrlPolicy.validate("data:image/png;base64,iVBORw0KGgo="));
        assertThrows(IllegalArgumentException.class, () -> EvidenceUrlPolicy.validate("data:image/svg+xml;base64,PHN2Zz4="));
    }

    @Test
    void tooManyOrTooLongLinksAreRejected() {
        StringBuilder many = new StringBuilder();
        for (int i = 0; i <= EvidenceUrlPolicy.MAX_URLS; i++) {
            many.append("https://a.example/").append(i).append(' ');
        }
        assertThrows(IllegalArgumentException.class, () -> EvidenceUrlPolicy.validate(many.toString()));
        assertFalse(EvidenceUrlPolicy.isSafeHttpUrl("https://a.example/" + "x".repeat(EvidenceUrlPolicy.MAX_URL_LENGTH)));
        assertTrue(EvidenceUrlPolicy.isSafeHttpUrl("https://a.example/x"));
    }
}
