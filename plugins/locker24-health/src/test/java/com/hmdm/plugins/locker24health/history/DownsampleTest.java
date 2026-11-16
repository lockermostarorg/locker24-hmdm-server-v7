package com.hmdm.plugins.locker24health.history;

import com.hmdm.plugins.locker24health.persistence.domain.HistoryRow;
import com.hmdm.plugins.locker24health.rest.json.HistoryResponse;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

public class DownsampleTest {

    private static HistoryRow row(long t, Integer battery, Float temp, Integer rssi, Boolean charging, Boolean kiosk) {
        HistoryRow r = new HistoryRow();
        r.setMeasuredAt(t);
        r.setBatteryPct(battery);
        r.setTemperatureC(temp);
        r.setRssiDbm(rssi);
        r.setCharging(charging);
        r.setKioskRunning(kiosk);
        return r;
    }

    @Test
    public void noBucketWhenWithinTheLimit() {
        assertEquals(0L, Downsample.bucketMs(0, 1000, 100, 100));
    }

    @Test
    public void bucketCoversTheRangeInAtMostMaxPoints() {
        long b = Downsample.bucketMs(0, 1000, 500, 100);
        assertEquals(10L, b);
    }

    @Test
    public void zeroBucketKeepsEveryRow() {
        List<HistoryRow> rows = new ArrayList<>();
        rows.add(row(5, 50, 30f, -60, true, true));
        rows.add(row(6, 49, 31f, -61, true, true));
        List<HistoryResponse.Point> p = Downsample.apply(rows, 0, 0);
        assertEquals(2, p.size());
        assertEquals(5L, p.get(0).getT());
        assertEquals(Integer.valueOf(49), p.get(1).getBatteryPct());
    }

    @Test
    public void bucketKeepsTheWorstValuesAndTheLastTime() {
        List<HistoryRow> rows = new ArrayList<>();
        rows.add(row(1, 80, 30f, -50, true, true));
        rows.add(row(2, 70, 47f, -90, false, false));
        rows.add(row(3, 75, 31f, -60, true, true));
        rows.add(row(12, 60, 20f, null, null, null));
        List<HistoryResponse.Point> p = Downsample.apply(rows, 0, 10);
        assertEquals(2, p.size());
        HistoryResponse.Point first = p.get(0);
        assertEquals(3L, first.getT());
        assertEquals(Integer.valueOf(70), first.getBatteryPct());
        assertEquals(47f, first.getTemperatureC(), 0.001f);
        assertEquals(Integer.valueOf(-90), first.getRssiDbm());
        assertFalse(first.getCharging());
        assertFalse(first.getKioskRunning());
        HistoryResponse.Point second = p.get(1);
        assertEquals(12L, second.getT());
        assertNull(second.getRssiDbm());
        assertNull(second.getKioskRunning());
    }
}
