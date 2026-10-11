package com.saed.backend.common.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** F10-19: audit entries carry the real client address and user agent, never a fixed loopback. */
class ClientRequestInfoTest {

    @AfterEach
    void clear() {
        RequestContextHolder.resetRequestAttributes();
    }

    private static void request(String remote, String forwarded, String agent) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remote);
        if (forwarded != null) {
            request.addHeader("X-Forwarded-For", forwarded);
        }
        if (agent != null) {
            request.addHeader("User-Agent", agent);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    void theDirectPeerAddressIsReported() {
        request("203.0.113.9", "198.51.100.1", "JUnit/5");
        assertEquals("203.0.113.9", ClientRequestInfo.ip());
        assertEquals("JUnit/5", ClientRequestInfo.userAgent());
    }

    @Test
    void behindAProxyTheEntryAppendedByTheProxyIsUsed() {
        request("10.0.0.2", "6.6.6.6, 203.0.113.77", null);
        assertEquals("203.0.113.77", ClientRequestInfo.ip());
        assertEquals("SAED-Core", ClientRequestInfo.userAgent());
    }

    @Test
    void aGarbageForwardedEntryFallsBackToThePeer() {
        request("10.0.0.2", "<script>", null);
        assertEquals("10.0.0.2", ClientRequestInfo.ip());
    }

    @Test
    void outsideARequestTheInternalMarkersAreUsed() {
        assertEquals("127.0.0.1", ClientRequestInfo.ip());
        assertEquals("SAED-Core", ClientRequestInfo.userAgent());
    }

    @Test
    void aVeryLongUserAgentIsTruncated() {
        request("203.0.113.9", null, "x".repeat(1000));
        assertEquals(255, ClientRequestInfo.userAgent().length());
    }
}
