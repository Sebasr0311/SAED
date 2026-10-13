package com.saed.backend.platform.service;

import com.saed.backend.common.ratelimit.SlidingWindowRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Locale;
import java.util.function.LongSupplier;

/**
 * Rate limits for the unauthenticated onboarding endpoints (F3-04): registration attempts per client address and
 * per tax id / e-mail, and payment-status lookups per client address. Limits are small on purpose; a legitimate
 * organization registers once.
 */
@Component
public class OnboardingRateLimiter {

    static final int REGISTRATIONS_PER_IP = 10;
    static final int REGISTRATIONS_PER_IDENTITY = 3;
    static final int STATUS_LOOKUPS_PER_IP = 60;
    private static final Duration REGISTRATION_WINDOW = Duration.ofHours(1);
    private static final Duration STATUS_WINDOW = Duration.ofMinutes(10);
    private static final int MAX_KEYS = 10_000;

    private final SlidingWindowRateLimiter registrationsByIp;
    private final SlidingWindowRateLimiter registrationsByIdentity;
    private final SlidingWindowRateLimiter statusByIp;

    public OnboardingRateLimiter() {
        this(System::currentTimeMillis);
    }

    OnboardingRateLimiter(LongSupplier clock) {
        this.registrationsByIp = new SlidingWindowRateLimiter(REGISTRATIONS_PER_IP, REGISTRATION_WINDOW, MAX_KEYS, clock);
        this.registrationsByIdentity = new SlidingWindowRateLimiter(REGISTRATIONS_PER_IDENTITY, REGISTRATION_WINDOW, MAX_KEYS, clock);
        this.statusByIp = new SlidingWindowRateLimiter(STATUS_LOOKUPS_PER_IP, STATUS_WINDOW, MAX_KEYS, clock);
    }

    /** Throws 429 when the client address, the tax id or the e-mail exceeded its registration allowance. */
    public void checkRegistration(String clientIp, String taxId, String email) {
        boolean allowed = registrationsByIp.tryAcquire("ip:" + clientIp);
        // Both identity keys are always recorded so one cannot be used to probe the other.
        allowed &= registrationsByIdentity.tryAcquire("tax:" + normalizeTaxId(taxId));
        allowed &= registrationsByIdentity.tryAcquire("mail:" + normalizeEmail(email));
        if (!allowed) {
            throw tooMany();
        }
    }

    public void checkStatusLookup(String clientIp) {
        if (!statusByIp.tryAcquire("ip:" + clientIp)) {
            throw tooMany();
        }
    }

    private static ResponseStatusException tooMany() {
        return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                "Demasiados intentos. Espere un momento antes de volver a intentarlo.");
    }

    static String normalizeTaxId(String taxId) {
        return taxId == null ? "" : taxId.replaceAll("[^0-9A-Za-z]", "").toUpperCase(Locale.ROOT);
    }

    static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Client address. Behind a trusted reverse proxy (the direct peer has a loopback or private address) the entry
     * appended by that proxy, the last one of X-Forwarded-For, is used; entries before it are client-controlled.
     */
    public static String clientIp(HttpServletRequest request) {
        return com.saed.backend.common.util.ClientRequestInfo.clientIp(request);
    }
}
