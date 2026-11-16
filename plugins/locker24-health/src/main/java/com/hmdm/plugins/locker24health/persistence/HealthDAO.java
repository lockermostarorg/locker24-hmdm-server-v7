package com.hmdm.plugins.locker24health.persistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.hmdm.plugins.locker24health.persistence.domain.HistoryRow;
import com.hmdm.plugins.locker24health.persistence.domain.LatestRow;
import com.hmdm.plugins.locker24health.persistence.mapper.HealthMapper;
import org.mybatis.guice.transactional.Transactional;

import java.util.ArrayList;
import java.util.List;

@Singleton
public class HealthDAO {

    private final HealthMapper mapper;

    @Inject
    public HealthDAO(HealthMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional
    public int saveBatch(List<HistoryRow> history, LatestRow latest) {
        int inserted = 0;
        List<HistoryRow> fresh = new ArrayList<>();
        for (HistoryRow row : history) {
            int n = mapper.insertHistory(row);
            inserted += n;
            if (n > 0) {
                fresh.add(row);
            }
        }
        applyStreak(latest, fresh);
        mapper.upsertLatest(latest);
        mapper.touchReceived(latest.getDeviceId(), latest.getReceivedAt());
        return inserted;
    }

    static void applyStreak(LatestRow latest, List<HistoryRow> fresh) {
        int run = 0;
        Long runStart = null;
        boolean allDown = true;
        for (HistoryRow r : fresh) {
            if (r.isKioskDown()) {
                if (run == 0) {
                    runStart = r.getMeasuredAt();
                }
                run++;
            } else {
                run = 0;
                runStart = null;
                allDown = false;
            }
        }
        latest.setFreshReports(fresh.size());
        latest.setKioskDownRun(run);
        latest.setKioskDownRunStart(runStart);
        latest.setKioskDownAll(allDown);
    }
}
