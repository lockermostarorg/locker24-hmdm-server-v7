package com.hmdm.plugins.locker24health.job;

import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

public final class ThrottledLog {

    public static final long INTERVAL_MS = 900_000L;

    private final Logger logger;
    private final long intervalMs;
    private final LongSupplier clock;
    private final Map<String, Long> lastAt = new HashMap<>();
    private final Map<String, Integer> suppressed = new HashMap<>();

    public ThrottledLog(Logger logger) {
        this(logger, INTERVAL_MS, System::currentTimeMillis);
    }

    public ThrottledLog(Logger logger, long intervalMs, LongSupplier clock) {
        this.logger = logger;
        this.intervalMs = intervalMs;
        this.clock = clock;
    }

    public void warn(String key, String message, Throwable t) {
        int skipped;
        synchronized (this) {
            long now = clock.getAsLong();
            Long last = lastAt.get(key);
            if (last != null && now - last < intervalMs) {
                suppressed.merge(key, 1, Integer::sum);
                return;
            }
            lastAt.put(key, now);
            Integer s = suppressed.remove(key);
            skipped = s == null ? 0 : s;
        }
        String text = skipped > 0 ? message + " (" + skipped + " similar suppressed)" : message;
        if (t != null) {
            logger.warn(text, t);
        } else {
            logger.warn(text);
        }
    }
}
