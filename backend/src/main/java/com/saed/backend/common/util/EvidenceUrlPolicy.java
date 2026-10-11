package com.saed.backend.common.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Validates the free-text "evidence" field that is later rendered as a link to other users (F9-09). Accepted:
 * one or more absolute http(s) URLs (separated by whitespace, commas or semicolons) or a single inline image
 * (data:image/png|jpeg|webp;base64). Anything else, in particular javascript:, data:text/html, file: or URLs with
 * embedded credentials, is rejected with an IllegalArgumentException (HTTP 400).
 */
public final class EvidenceUrlPolicy {

    static final int MAX_URLS = 10;
    static final int MAX_URL_LENGTH = 500;
    private static final Pattern SEPARATORS = Pattern.compile("[\\s,;]+");
    private static final Pattern INLINE_IMAGE = Pattern.compile("^data:image/(png|jpeg|jpg|webp);base64,[A-Za-z0-9+/=]+$");

    private EvidenceUrlPolicy() {
    }

    /** Returns the trimmed value (null when blank); throws when it contains anything but safe links. */
    public static String validate(String evidence) {
        if (evidence == null || evidence.isBlank()) {
            return null;
        }
        String trimmed = evidence.strip();
        if (INLINE_IMAGE.matcher(trimmed).matches()) {
            return trimmed;
        }
        String[] urls = SEPARATORS.split(trimmed);
        if (urls.length > MAX_URLS) {
            throw invalid();
        }
        for (String url : urls) {
            if (!isSafeHttpUrl(url)) {
                throw invalid();
            }
        }
        return trimmed;
    }

    /** Whether the value is an absolute http(s) URL with a host and no credentials. */
    public static boolean isSafeHttpUrl(String url) {
        if (url == null || url.isEmpty() || url.length() > MAX_URL_LENGTH) {
            return false;
        }
        for (int i = 0; i < url.length(); i++) {
            char c = url.charAt(i);
            if (c <= 0x20 || c == 0x7f) {
                return false;
            }
        }
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            return scheme != null
                    && ("http".equals(scheme.toLowerCase(Locale.ROOT)) || "https".equals(scheme.toLowerCase(Locale.ROOT)))
                    && uri.getHost() != null
                    && uri.getUserInfo() == null;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException(
                "Las evidencias deben ser enlaces http(s) válidos o una imagen en línea (PNG, JPEG o WEBP).");
    }
}
