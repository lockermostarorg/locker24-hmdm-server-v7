package com.hmdm.plugins.locker24health.history;

import com.hmdm.plugins.locker24health.rest.json.HistoryResponse;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class OfflinePeriodsTest {

    private static final long MIN = 60_000L;

    private static List<Long> minutes(long... m) {
        List<Long> out = new ArrayList<>();
        for (long v : m) {
            out.add(v * MIN);
        }
        return out;
    }

    private static List<String> asMinutes(List<HistoryResponse.Period> periods) {
        List<String> out = new ArrayList<>();
        for (HistoryResponse.Period p : periods) {
            out.add(p.getFrom() / MIN + "-" + p.getTo() / MIN);
        }
        return out;
    }

    @Test
    public void gapsOverThreshold() {
        List<HistoryResponse.Period> r = OfflinePeriods.compute(minutes(0, 5, 25, 30), 0, 60 * MIN, 10 * MIN);
        assertEquals(Arrays.asList("5-25", "30-60"), asMinutes(r));
    }

    @Test
    public void exactlyThresholdIsNotOffline() {
        assertTrue(OfflinePeriods.compute(minutes(0, 10), 0, 10 * MIN, 10 * MIN).isEmpty());
    }

    @Test
    public void leadingGap() {
        assertEquals(Arrays.asList("0-15"), asMinutes(OfflinePeriods.compute(minutes(15), 0, 20 * MIN, 10 * MIN)));
    }

    @Test
    public void noSamples() {
        assertEquals(Arrays.asList("0-20"), asMinutes(OfflinePeriods.compute(minutes(), 0, 20 * MIN, 10 * MIN)));
    }

    @Test
    public void sampleBeforeTheRangeStartsTheFirstGapButIsClipped() {
        assertEquals(Arrays.asList("0-30"), asMinutes(OfflinePeriods.compute(minutes(-5, 30), 0, 35 * MIN, 10 * MIN)));
    }

    @Test
    public void sampleBeforeTheRangeCloseEnoughMeansNoLeadingGap() {
        assertTrue(OfflinePeriods.compute(minutes(-5, 4), 0, 10 * MIN, 10 * MIN).isEmpty());
    }

    @Test
    public void samplesAfterTheRangeAreIgnored() {
        assertEquals(Arrays.asList("5-20"), asMinutes(OfflinePeriods.compute(minutes(5, 50), 5 * MIN, 20 * MIN, 10 * MIN)));
    }
}
