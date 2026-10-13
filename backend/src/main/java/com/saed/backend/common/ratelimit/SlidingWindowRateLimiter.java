package com.saed.backend.common.ratelimit;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * In-memory sliding-window limiter with a bounded number of keys and a time-to-live per key.
 *
 * <p>Each key may succeed at most {@code maxPerWindow} times within {@code window}. Entries whose events are all
 * older than the window are dropped, and when more than {@code maxKeys} keys are tracked the oldest-inserted keys
 * are evicted, so memory stays bounded under a flood of distinct keys. State is per instance: behind several
 * instances the effective limit is multiplied by their number, which is acceptable as a first defence for public
 * endpoints (a shared store is the follow-up for hard guarantees).</p>
 */
public final class SlidingWindowRateLimiter {

    private final int maxPerWindow;
    private final long windowMillis;
    private final int maxKeys;
    private final LongSupplier clockMillis;
    private final Map<String, Deque<Long>> events = new LinkedHashMap<>();

    public SlidingWindowRateLimiter(int maxPerWindow, Duration window, int maxKeys, LongSupplier clockMillis) {
        if (maxPerWindow < 1 || maxKeys < 1 || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("Invalid rate limiter configuration");
        }
        this.maxPerWindow = maxPerWindow;
        this.windowMillis = window.toMillis();
        this.maxKeys = maxKeys;
        this.clockMillis = clockMillis;
    }

    public SlidingWindowRateLimiter(int maxPerWindow, Duration window, int maxKeys) {
        this(maxPerWindow, window, maxKeys, System::currentTimeMillis);
    }

    /** Records an attempt for the key and returns whether it is allowed. A blank key is never allowed to bypass. */
    public synchronized boolean tryAcquire(String key) {
        String safeKey = key == null || key.isBlank() ? "<blank>" : key;
        long now = clockMillis.getAsLong();
        Deque<Long> window = events.get(safeKey);
        if (window == null) {
            purgeExpired(now);
            while (events.size() >= maxKeys) {
                Iterator<String> eldest = events.keySet().iterator();
                eldest.next();
                eldest.remove();
            }
            window = new ArrayDeque<>();
            events.put(safeKey, window);
        }
        while (!window.isEmpty() && now - window.peekFirst() >= windowMillis) {
            window.pollFirst();
        }
        if (window.size() >= maxPerWindow) {
            return false;
        }
        window.addLast(now);
        return true;
    }

    /** Number of tracked keys (for tests and diagnostics). */
    public synchronized int trackedKeys() {
        return events.size();
    }

    private void purgeExpired(long now) {
        events.values().removeIf(window -> window.isEmpty() || now - window.peekLast() >= windowMillis);
    }
}
