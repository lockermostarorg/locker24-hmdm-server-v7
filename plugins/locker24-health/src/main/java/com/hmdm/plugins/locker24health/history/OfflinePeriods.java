package com.hmdm.plugins.locker24health.history;

import com.hmdm.plugins.locker24health.persistence.domain.GapRow;
import com.hmdm.plugins.locker24health.rest.json.HistoryResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class OfflinePeriods {

    private OfflinePeriods() {
    }

    public static List<HistoryResponse.Period> assemble(List<GapRow> gaps, Long before, Long first, Long last,
                                                        Long latestReceived, long from, long to, long threshold) {
        List<HistoryResponse.Period> out = new ArrayList<>();
        if (first == null) {
            Long known = before != null ? before : latestReceived;
            if (known != null && to - known > threshold) {
                out.add(new HistoryResponse.Period(Math.max(known, from), to));
            }
            return out;
        }
        for (GapRow g : gaps) {
            out.add(new HistoryResponse.Period(Math.max(g.getFromMs(), from), Math.min(g.getToMs(), to)));
        }
        if (last != null && to - last > threshold) {
            out.add(new HistoryResponse.Period(Math.max(last, from), to));
        }
        Collections.sort(out, Comparator.comparingLong(HistoryResponse.Period::getFrom));
        return out;
    }
}
