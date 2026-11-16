package com.hmdm.plugins.locker24health.ingest;

public final class ClockCorrection {

    public static final long MAX_AGE_MS = 172_800_000L;

    private ClockCorrection() {
    }

    public static long measuredAt(long receivedAt, long sentAt, long ts) {
        long age = Math.max(0L, Math.min(MAX_AGE_MS, sentAt - ts));
        return receivedAt - age;
    }
}
