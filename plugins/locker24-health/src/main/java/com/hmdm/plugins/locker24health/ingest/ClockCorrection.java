package com.hmdm.plugins.locker24health.ingest;

public final class ClockCorrection {

    public static final long MAX_AGE_MS = 172_800_000L;

    private ClockCorrection() {
    }

    public static long measuredAt(long receivedAt, long sentAt, long ts) {
        long age;
        if (ts <= 0) {
            age = MAX_AGE_MS;
        } else {
            try {
                age = Math.max(0L, Math.min(MAX_AGE_MS, Math.subtractExact(sentAt, ts)));
            } catch (ArithmeticException e) {
                age = MAX_AGE_MS;
            }
        }
        return receivedAt - age;
    }
}
