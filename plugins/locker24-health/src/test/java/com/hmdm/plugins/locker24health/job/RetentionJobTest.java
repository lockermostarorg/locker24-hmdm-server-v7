package com.hmdm.plugins.locker24health.job;

import com.hmdm.plugins.locker24health.persistence.HealthDAO;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RetentionJobTest {

    private static final long NOW = 1_763_290_060_000L;
    private static final long CUTOFF = NOW - 2_592_000_000L;

    @Test
    public void deletesInBatchesUntilAShortOne() {
        HealthDAO dao = mock(HealthDAO.class);
        when(dao.purgeHistoryBatch(CUTOFF, RetentionJob.BATCH)).thenReturn(RetentionJob.BATCH, RetentionJob.BATCH, 12);
        when(dao.purgeClearedAlertsBatch(CUTOFF, RetentionJob.BATCH)).thenReturn(3);
        new RetentionJob(dao, () -> NOW).purge();
        verify(dao, times(3)).purgeHistoryBatch(CUTOFF, RetentionJob.BATCH);
        verify(dao, times(1)).purgeClearedAlertsBatch(CUTOFF, RetentionJob.BATCH);
    }

    @Test
    public void drainIsBounded() {
        AtomicInteger calls = new AtomicInteger();
        int total = RetentionJob.drain(limit -> {
            calls.incrementAndGet();
            return limit;
        });
        assertEquals(RetentionJob.MAX_BATCHES, calls.get());
        assertEquals(RetentionJob.MAX_BATCHES * RetentionJob.BATCH, total);
    }

    @Test
    public void thirtyDays() {
        assertEquals(30L * 24 * 3600 * 1000, RetentionJob.RETENTION_MS);
    }
}
