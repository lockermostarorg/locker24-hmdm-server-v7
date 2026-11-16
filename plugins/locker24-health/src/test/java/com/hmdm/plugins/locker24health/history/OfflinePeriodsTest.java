package com.hmdm.plugins.locker24health.history;

import com.hmdm.plugins.locker24health.persistence.domain.GapRow;
import com.hmdm.plugins.locker24health.rest.json.HistoryResponse;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class OfflinePeriodsTest {

    private static final long MIN = 60_000L;
    private static final long T = 10 * MIN;

    private static GapRow gap(long from, long to) {
        GapRow g = new GapRow();
        g.setFromMs(from * MIN);
        g.setToMs(to * MIN);
        return g;
    }

    private static Long m(Integer v) {
        return v == null ? null : v * MIN;
    }

    private static List<String> run(List<GapRow> gaps, Integer before, Integer first, Integer last, Integer latest,
                                    long from, long to) {
        List<String> out = new ArrayList<>();
        for (HistoryResponse.Period p : OfflinePeriods.assemble(gaps, m(before), m(first), m(last), m(latest),
                from * MIN, to * MIN, T)) {
            out.add(p.getFrom() / MIN + "-" + p.getTo() / MIN);
        }
        return out;
    }

    @Test
    public void gapsAreKeptAndTrailingGapAdded() {
        assertEquals(Arrays.asList("5-25", "30-60"),
                run(Arrays.asList(gap(5, 25)), null, 0, 30, null, 0, 60));
    }

    @Test
    public void unsortedGapsComeOutAscending() {
        assertEquals(Arrays.asList("5-25", "40-50"),
                run(Arrays.asList(gap(40, 50), gap(5, 25)), null, 0, 50, null, 0, 55));
    }

    @Test
    public void exactlyThresholdTrailingIsNotOffline() {
        assertTrue(run(Collections.<GapRow>emptyList(), null, 0, 10, null, 0, 20).isEmpty());
    }

    @Test
    public void gapStartingBeforeTheRangeIsClipped() {
        assertEquals(Arrays.asList("0-30"), run(Arrays.asList(gap(-5, 30)), -5, 30, 33, null, 0, 35));
    }

    @Test
    public void noLeadingGapBeforeTheFirstEverReport() {
        assertTrue(run(Collections.<GapRow>emptyList(), null, 15, 20, null, 0, 22).isEmpty());
    }

    @Test
    public void nothingInRangeButEarlierHistoryMeansWholeRangeOffline() {
        assertEquals(Arrays.asList("0-20"), run(Collections.<GapRow>emptyList(), -30, null, null, null, 0, 20));
    }

    @Test
    public void nothingInRangeButEarlierSampleStillRecent() {
        assertTrue(run(Collections.<GapRow>emptyList(), -5, null, null, null, 0, 4).isEmpty());
    }

    @Test
    public void noHistoryFallsBackToLatestReceivedAt() {
        assertEquals(Arrays.asList("0-20"), run(Collections.<GapRow>emptyList(), null, null, null, -60, 0, 20));
        assertEquals(Arrays.asList("20-30"), run(Collections.<GapRow>emptyList(), null, null, null, 5, 20, 30));
    }

    @Test
    public void noHistoryAndNoLatestMeansNothing() {
        assertTrue(run(Collections.<GapRow>emptyList(), null, null, null, null, 0, 20).isEmpty());
    }
}
