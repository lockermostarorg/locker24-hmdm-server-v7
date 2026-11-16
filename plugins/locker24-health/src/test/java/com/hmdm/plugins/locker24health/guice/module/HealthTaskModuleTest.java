package com.hmdm.plugins.locker24health.guice.module;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertTrue;

public class HealthTaskModuleTest {

    @Test
    public void guardSwallowsExceptionsAndErrors() {
        HealthTaskModule.guard("t", () -> {
            throw new IllegalStateException("boom");
        }).run();
        HealthTaskModule.guard("t", () -> {
            throw new AssertionError("boom");
        }).run();
    }

    @Test
    public void fixedRateScheduleSurvivesAFailingRun() throws Exception {
        ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor();
        try {
            AtomicInteger runs = new AtomicInteger();
            CountDownLatch three = new CountDownLatch(3);
            ses.scheduleAtFixedRate(HealthTaskModule.guard("t", () -> {
                three.countDown();
                if (runs.incrementAndGet() == 1) {
                    throw new IllegalStateException("first run fails");
                }
            }), 0, 10, TimeUnit.MILLISECONDS);
            assertTrue(three.await(5, TimeUnit.SECONDS));
        } finally {
            ses.shutdownNow();
        }
    }
}
