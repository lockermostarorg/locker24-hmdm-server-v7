package com.hmdm.plugins.locker24health.persistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.hmdm.plugins.locker24health.persistence.domain.HistoryRow;
import com.hmdm.plugins.locker24health.persistence.domain.LatestRow;
import com.hmdm.plugins.locker24health.persistence.mapper.HealthMapper;
import org.mybatis.guice.transactional.Transactional;

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
        for (HistoryRow row : history) {
            inserted += mapper.insertHistory(row);
        }
        mapper.upsertLatest(latest);
        mapper.touchReceived(latest.getDeviceId(), latest.getReceivedAt());
        return inserted;
    }
}
