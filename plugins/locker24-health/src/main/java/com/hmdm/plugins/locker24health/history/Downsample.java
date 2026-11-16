package com.hmdm.plugins.locker24health.history;

import com.hmdm.plugins.locker24health.persistence.domain.HistoryRow;
import com.hmdm.plugins.locker24health.rest.json.HistoryResponse;

import java.util.ArrayList;
import java.util.List;

public final class Downsample {

    private Downsample() {
    }

    public static long bucketMs(long from, long to, int rows, int maxPoints) {
        if (rows <= maxPoints) {
            return 0L;
        }
        long range = Math.max(1L, to - from);
        return (range + maxPoints - 1) / maxPoints;
    }

    public static List<HistoryResponse.Point> apply(List<HistoryRow> rowsAscending, long from, long bucketMs) {
        List<HistoryResponse.Point> out = new ArrayList<>();
        long currentBucket = Long.MIN_VALUE;
        HistoryResponse.Point acc = null;
        for (HistoryRow r : rowsAscending) {
            long bucket = bucketMs <= 0 ? r.getMeasuredAt() : Math.floorDiv(r.getMeasuredAt() - from, bucketMs);
            if (acc == null || bucketMs <= 0 || bucket != currentBucket) {
                acc = new HistoryResponse.Point();
                acc.setT(r.getMeasuredAt());
                acc.setBatteryPct(r.getBatteryPct());
                acc.setTemperatureC(r.getTemperatureC());
                acc.setRssiDbm(r.getRssiDbm());
                acc.setCharging(r.getCharging());
                acc.setKioskRunning(r.getKioskRunning());
                out.add(acc);
                currentBucket = bucket;
                continue;
            }
            acc.setT(r.getMeasuredAt());
            acc.setBatteryPct(worst(acc.getBatteryPct(), r.getBatteryPct(), false));
            acc.setRssiDbm(worst(acc.getRssiDbm(), r.getRssiDbm(), false));
            acc.setTemperatureC(worst(acc.getTemperatureC(), r.getTemperatureC(), true));
            acc.setCharging(worst(acc.getCharging(), r.getCharging()));
            acc.setKioskRunning(worst(acc.getKioskRunning(), r.getKioskRunning()));
        }
        return out;
    }

    private static <T extends Comparable<T>> T worst(T a, T b, boolean higherIsWorse) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        int c = a.compareTo(b);
        return (c >= 0) == higherIsWorse ? a : b;
    }

    private static Boolean worst(Boolean a, Boolean b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a && b;
    }
}
