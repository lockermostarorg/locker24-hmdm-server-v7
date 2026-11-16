package com.hmdm.plugins.locker24health.job;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZonedDateTime;

public final class RetentionSchedule {

    public static final LocalTime AT = LocalTime.of(3, 30);
    public static final long PERIOD_MINUTES = 1440;

    private RetentionSchedule() {
    }

    public static long initialDelayMinutes(ZonedDateTime now, LocalTime at) {
        ZonedDateTime next = now.with(at);
        if (!next.isAfter(now)) {
            next = now.plusDays(1).with(at);
        }
        long millis = Duration.between(now, next).toMillis();
        return Math.max(1, (millis + 59_999) / 60_000);
    }
}
