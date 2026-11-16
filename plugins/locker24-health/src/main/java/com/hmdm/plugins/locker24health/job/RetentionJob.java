package com.hmdm.plugins.locker24health.job;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.hmdm.plugins.locker24health.persistence.HealthDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.IntUnaryOperator;
import java.util.function.LongSupplier;

@Singleton
public class RetentionJob {

    public static final long RETENTION_MS = 2_592_000_000L;
    public static final int BATCH = 5_000;
    public static final int MAX_BATCHES = 2_000;

    private static final Logger logger = LoggerFactory.getLogger(RetentionJob.class);

    private final HealthDAO dao;
    private final LongSupplier clock;

    @Inject
    public RetentionJob(HealthDAO dao) {
        this(dao, System::currentTimeMillis);
    }

    RetentionJob(HealthDAO dao, LongSupplier clock) {
        this.dao = dao;
        this.clock = clock;
    }

    public void purge() {
        long cutoff = clock.getAsLong() - RETENTION_MS;
        int history = drain(limit -> dao.purgeHistoryBatch(cutoff, limit));
        int alerts = drain(limit -> dao.purgeClearedAlertsBatch(cutoff, limit));
        logger.info("Health retention: removed {} history rows and {} cleared alerts older than 30 days", history, alerts);
    }

    static int drain(IntUnaryOperator batch) {
        int total = 0;
        for (int i = 0; i < MAX_BATCHES; i++) {
            int n = batch.applyAsInt(BATCH);
            total += n;
            if (n < BATCH) {
                break;
            }
        }
        return total;
    }
}
