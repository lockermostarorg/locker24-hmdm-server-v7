package com.hmdm.plugins.locker24health.job;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.hmdm.plugin.service.PluginStatusCache;
import com.hmdm.plugins.locker24health.HealthPluginConfigurationImpl;
import com.hmdm.plugins.locker24health.alert.AlertDecision;
import com.hmdm.plugins.locker24health.alert.AlertDispatcher;
import com.hmdm.plugins.locker24health.alert.AlertEvent;
import com.hmdm.plugins.locker24health.alert.AlertPolicy;
import com.hmdm.plugins.locker24health.alert.OpenAlert;
import com.hmdm.plugins.locker24health.persistence.HealthDAO;
import com.hmdm.plugins.locker24health.persistence.domain.EvalRow;
import com.hmdm.plugins.locker24health.persistence.domain.SettingsRow;
import com.hmdm.plugins.locker24health.persistence.mapper.HealthMapper;
import com.hmdm.plugins.locker24health.status.Condition;
import com.hmdm.plugins.locker24health.status.ConfiguredKiosk;
import com.hmdm.plugins.locker24health.status.DeviceHealth;
import com.hmdm.plugins.locker24health.status.EvalContext;
import com.hmdm.plugins.locker24health.status.Evaluation;
import com.hmdm.plugins.locker24health.status.StatusEvaluator;
import com.hmdm.security.SecurityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.LongSupplier;

@Singleton
public class EvaluationJob {

    private static final Logger logger = LoggerFactory.getLogger(EvaluationJob.class);

    private final HealthDAO dao;
    private final Function<Integer, ConfiguredKiosk> kioskResolver;
    private final AlertDispatcher dispatcher;
    private final IntPredicate customerEnabled;
    private final LongSupplier clock;
    private final ThrottledLog failures = new ThrottledLog(logger);
    private volatile long serverStartedAt;

    @Inject
    public EvaluationJob(HealthDAO dao, ConfiguredKioskResolver kioskResolver, AlertDispatcher dispatcher,
                         PluginStatusCache pluginStatusCache) {
        this(dao, kioskResolver::resolve, dispatcher, id -> pluginEnabled(pluginStatusCache, id), System::currentTimeMillis);
    }

    EvaluationJob(HealthDAO dao, Function<Integer, ConfiguredKiosk> kioskResolver, AlertDispatcher dispatcher,
                  IntPredicate customerEnabled, LongSupplier clock) {
        this.dao = dao;
        this.kioskResolver = kioskResolver;
        this.dispatcher = dispatcher;
        this.customerEnabled = customerEnabled;
        this.clock = clock;
        this.serverStartedAt = clock.getAsLong();
    }

    public void setServerStartedAt(long serverStartedAt) {
        this.serverStartedAt = serverStartedAt;
    }

    public void runOnce() {
        long now = clock.getAsLong();
        List<EvalRow> rows;
        Map<Integer, Map<Condition, OpenAlert>> open;
        Map<Integer, SettingsRow> settings;
        try {
            rows = dao.loadEvaluationRows();
            if (rows.isEmpty()) {
                return;
            }
            open = dao.loadOpenAlerts();
            settings = dao.loadSettings();
        } catch (RuntimeException e) {
            if (DbTimeout.isTimeout(e)) {
                failures.warn("timeout", "Health evaluation skipped: a database statement timed out after "
                        + HealthMapper.TIMEOUT_SECONDS + " s", null);
                return;
            }
            throw e;
        }
        Map<Integer, Boolean> enabled = new HashMap<>();
        Map<Integer, ZoneId> zones = new HashMap<>();
        Map<Integer, Optional<ConfiguredKiosk>> kiosks = new HashMap<>();
        Set<Integer> unresolved = new HashSet<>();
        List<AlertEvent> events = new ArrayList<>();
        int failed = 0;
        for (EvalRow row : rows) {
            try {
                int customerId = row.getCustomerId();
                if (!enabled.computeIfAbsent(customerId, customerEnabled::test)) {
                    continue;
                }
                ConfiguredKiosk kiosk = kiosk(row.getConfigurationId(), kiosks, unresolved);
                ZoneId zone = zones.computeIfAbsent(customerId, id -> zone(settings.get(id)));
                events.addAll(evaluate(row, now, kiosk, zone, open.getOrDefault(row.getDeviceId(), Map.of())));
            } catch (RuntimeException e) {
                failed++;
                if (DbTimeout.isTimeout(e)) {
                    failures.warn("timeout", "Health evaluation of device " + row.getDeviceId()
                            + ": a database statement timed out after " + HealthMapper.TIMEOUT_SECONDS + " s", null);
                } else {
                    failures.warn("device", "Health evaluation failed for device " + row.getDeviceId(), e);
                }
            }
        }
        if (failed > 0) {
            failures.warn("cycle", "Health evaluation: " + failed + " of " + rows.size() + " devices failed this cycle", null);
        }
        if (!events.isEmpty()) {
            dispatcher.dispatch(events, webhookUrls(settings), now);
        }
    }

    private List<AlertEvent> evaluate(EvalRow row, long now, ConfiguredKiosk kiosk, ZoneId zone,
                                      Map<Condition, OpenAlert> openForDevice) {
        EvalContext context = new EvalContext(now, serverStartedAt, kiosk, zone, row.getVersionMismatchSince());
        Evaluation evaluation = StatusEvaluator.evaluate(DeviceHealth.from(row), context);
        Long nonGreenSince = row.getNonGreenSince();
        Long lastReceivedAt = row.getReceivedAt();
        AlertDecision decision = AlertPolicy.decide(now, evaluation.status(), nonGreenSince, evaluation.findings(),
                openForDevice, lastReceivedAt);
        return dao.applyEvaluation(row, evaluation, decision, openForDevice, now);
    }

    private ConfiguredKiosk kiosk(Integer configurationId, Map<Integer, Optional<ConfiguredKiosk>> cache,
                                  Set<Integer> unresolved) {
        if (configurationId == null) {
            return null;
        }
        if (unresolved.contains(configurationId)) {
            throw new IllegalStateException("configuration " + configurationId + " could not be resolved this cycle");
        }
        Optional<ConfiguredKiosk> cached = cache.get(configurationId);
        if (cached != null) {
            return cached.orElse(null);
        }
        try {
            ConfiguredKiosk k = kioskResolver.apply(configurationId);
            cache.put(configurationId, Optional.ofNullable(k));
            return k;
        } catch (RuntimeException e) {
            unresolved.add(configurationId);
            throw e;
        }
    }

    static ZoneId zone(SettingsRow settings) {
        if (settings == null || settings.getTimeZone() == null || settings.getTimeZone().isBlank()) {
            return HealthPluginConfigurationImpl.DEFAULT_ZONE;
        }
        try {
            return ZoneId.of(settings.getTimeZone().trim());
        } catch (DateTimeException e) {
            return HealthPluginConfigurationImpl.DEFAULT_ZONE;
        }
    }

    private static Map<Integer, String> webhookUrls(Map<Integer, SettingsRow> settings) {
        Map<Integer, String> out = new HashMap<>();
        for (SettingsRow s : settings.values()) {
            if (s.getWebhookUrl() != null && !s.getWebhookUrl().isBlank()) {
                out.put(s.getCustomerId(), s.getWebhookUrl());
            }
        }
        return out;
    }

    private static boolean pluginEnabled(PluginStatusCache cache, int customerId) {
        SecurityContext.init(customerId);
        try {
            return !cache.isPluginDisabled(HealthPluginConfigurationImpl.PLUGIN_ID);
        } finally {
            SecurityContext.release();
        }
    }
}
