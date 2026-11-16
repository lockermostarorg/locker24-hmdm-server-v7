package com.hmdm.plugins.locker24health.ingest;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ClockCorrectionTest {

    private static final long R = 1_763_290_000_000L;

    @Test
    public void skewRemoved() {
        long sent = R - 3_600_000;
        assertEquals(R - 600_000, ClockCorrection.measuredAt(R, sent, sent - 600_000));
    }

    @Test
    public void negativeAgeClamps() {
        long sent = R - 1_000;
        assertEquals(R, ClockCorrection.measuredAt(R, sent, sent + 5_000));
    }

    @Test
    public void ageCappedAt48h() {
        long sent = R - 1_000;
        long ts = sent - 50L * 3_600_000;
        assertEquals(R - 172_800_000L, ClockCorrection.measuredAt(R, sent, ts));
    }

    @Test
    public void unsetDeviceClock() {
        assertEquals(R - 300_000, ClockCorrection.measuredAt(R, 301_000, 1_000));
    }

    @Test
    public void nonPositiveTsSaturates() {
        assertEquals(R - 172_800_000L, ClockCorrection.measuredAt(R, R, Long.MIN_VALUE));
        assertEquals(R - 172_800_000L, ClockCorrection.measuredAt(R, R, -1));
    }

    @Test
    public void subtractionOverflowSaturates() {
        assertEquals(R - 172_800_000L, ClockCorrection.measuredAt(R, Long.MIN_VALUE, 5));
    }
}
