package com.hmdm.plugins.locker24health.history;

import com.hmdm.plugins.locker24health.rest.json.HistoryResponse;

import java.util.ArrayList;
import java.util.List;

public final class OfflinePeriods {

    private OfflinePeriods() {
    }

    public static List<HistoryResponse.Period> compute(List<Long> receivedAtSorted, long from, long to, long threshold) {
        List<HistoryResponse.Period> out = new ArrayList<>();
        long prev = from;
        for (long s : receivedAtSorted) {
            if (s > to) {
                break;
            }
            if (s < from) {
                prev = s;
                continue;
            }
            if (s - prev > threshold) {
                out.add(new HistoryResponse.Period(Math.max(prev, from), s));
            }
            prev = s;
        }
        if (to - prev > threshold) {
            out.add(new HistoryResponse.Period(Math.max(prev, from), to));
        }
        return out;
    }
}
