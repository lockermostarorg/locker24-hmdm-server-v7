package com.hmdm.plugins.locker24health.guice.module;

import com.google.inject.Inject;
import com.hmdm.plugin.PluginTaskModule;
import com.hmdm.plugin.persistence.PluginDAO;
import com.hmdm.plugin.service.PluginVersionService;
import com.hmdm.plugins.locker24health.HealthPluginConfigurationImpl;
import com.hmdm.plugins.locker24health.job.EvaluationJob;
import com.hmdm.plugins.locker24health.job.RetentionJob;
import com.hmdm.plugins.locker24health.job.RetentionSchedule;
import com.hmdm.plugins.locker24health.job.ThrottledLog;
import com.hmdm.util.BackgroundTaskRunnerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZonedDateTime;
import java.util.concurrent.TimeUnit;

public class HealthTaskModule implements PluginTaskModule {

    private static final Logger logger = LoggerFactory.getLogger(HealthTaskModule.class);
    private static final ThrottledLog failures = new ThrottledLog(logger);

    private final PluginDAO pluginDAO;
    private final BackgroundTaskRunnerService taskRunner;
    private final EvaluationJob evaluationJob;
    private final RetentionJob retentionJob;

    @Inject
    public HealthTaskModule(PluginDAO pluginDAO, BackgroundTaskRunnerService taskRunner,
                            EvaluationJob evaluationJob, RetentionJob retentionJob) {
        this.pluginDAO = pluginDAO;
        this.taskRunner = taskRunner;
        this.evaluationJob = evaluationJob;
        this.retentionJob = retentionJob;
    }

    @Override
    public void init() {
        String pluginId = HealthPluginConfigurationImpl.PLUGIN_ID;
        try {
            pluginDAO.updatePluginVersion(pluginId, PluginVersionService.getVersion(this.getClass(), pluginId));
        } catch (RuntimeException e) {
            logger.warn("Could not record the {} plugin version", pluginId, e);
        }
        evaluationJob.setServerStartedAt(System.currentTimeMillis());
        taskRunner.submitRepeatableTask(guard("evaluation", evaluationJob::runOnce), 1, 1, TimeUnit.MINUTES);
        long retentionDelay = RetentionSchedule.initialDelayMinutes(
                ZonedDateTime.now(HealthPluginConfigurationImpl.DEFAULT_ZONE), RetentionSchedule.AT);
        taskRunner.submitRepeatableTask(guard("retention", retentionJob::purge), retentionDelay,
                RetentionSchedule.PERIOD_MINUTES, TimeUnit.MINUTES);
        logger.info("Health evaluation scheduled every minute; retention in {} min, then daily", retentionDelay);
    }

    static Runnable guard(String name, Runnable task) {
        return () -> {
            try {
                task.run();
            } catch (Throwable t) {
                try {
                    failures.warn(name, "Health " + name + " run failed", t);
                } catch (Throwable ignored) {
                }
            }
        };
    }
}
