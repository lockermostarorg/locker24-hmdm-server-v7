package com.hmdm.plugins.locker24health.alert;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.hmdm.persistence.domain.Alert;
import com.hmdm.plugins.locker24health.job.ThrottledLog;
import com.hmdm.plugins.locker24health.status.Severity;
import com.hmdm.service.AlertService;
import com.hmdm.util.BackgroundTaskRunnerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

@Singleton
public class AlertDispatcher {

    public static final int MAX_IN_FLIGHT = 3;

    private static final Logger logger = LoggerFactory.getLogger(AlertDispatcher.class);

    private final AlertService alertService;
    private final WebhookSender webhookSender;
    private final Executor executor;
    private final PopupBudget budget;
    private final AtomicInteger popupTasks = new AtomicInteger();
    private final AtomicInteger webhookTasks = new AtomicInteger();
    private final ThrottledLog warnings = new ThrottledLog(logger);

    @Inject
    public AlertDispatcher(AlertService alertService, WebhookSender webhookSender, BackgroundTaskRunnerService taskRunner) {
        this(alertService, webhookSender, taskRunner::submitTask,
                new PopupBudget(AlertPolicy.MAX_POPUPS_PER_CYCLE, PopupBudget.WINDOW_MS));
    }

    AlertDispatcher(AlertService alertService, WebhookSender webhookSender, Executor executor, PopupBudget budget) {
        this.alertService = alertService;
        this.webhookSender = webhookSender;
        this.executor = executor;
        this.budget = budget;
    }

    public void dispatch(List<AlertEvent> events, Map<Integer, String> webhookUrls, long now) {
        Map<Integer, List<AlertEvent>> byCustomer = new LinkedHashMap<>();
        for (AlertEvent e : events) {
            if (e.customerId() != null) {
                byCustomer.computeIfAbsent(e.customerId(), k -> new ArrayList<>()).add(e);
            }
        }
        for (Map.Entry<Integer, List<AlertEvent>> entry : byCustomer.entrySet()) {
            int customerId = entry.getKey();
            List<AlertEvent> all = entry.getValue();
            try {
                schedulePopups(customerId, all, now);
                String url = webhookUrls.get(customerId);
                if (url != null && !url.isBlank()) {
                    String body = WebhookPayload.build(all, now);
                    submit(webhookTasks, "webhook", customerId, () -> deliverWebhook(customerId, url, body));
                }
            } catch (RuntimeException e) {
                warnings.warn("dispatch", "Health alert dispatch failed for customer " + customerId, e);
            }
        }
    }

    private void schedulePopups(int customerId, List<AlertEvent> all, long now) {
        List<AlertEvent> popups = AlertPolicy.limitPopups(all, budget.available(customerId, now));
        int deviceRaises = (int) popups.stream().filter(e -> !e.cleared() && e.deviceId() != null).count();
        budget.spend(customerId, now, deviceRaises);
        List<Alert> alerts = new ArrayList<>(popups.size());
        for (AlertEvent e : popups) {
            alerts.add(toAlert(customerId, e));
        }
        if (!alerts.isEmpty()) {
            submit(popupTasks, "pop-up", customerId, () -> deliverPopups(customerId, alerts));
        }
    }

    static Alert toAlert(int customerId, AlertEvent e) {
        Alert a = new Alert();
        a.setCustomerId(customerId);
        a.setDeviceId(e.deviceId());
        a.setDeviceNumber(e.deviceNumber());
        a.setLevel(level(e));
        a.setMessage(AlertText.message(e));
        return a;
    }

    static int level(AlertEvent e) {
        if (e.cleared()) {
            return Alert.INFO;
        }
        return e.severity() == Severity.RED ? Alert.SEVERE : Alert.WARNING;
    }

    private void submit(AtomicInteger inFlight, String kind, int customerId, Runnable task) {
        if (inFlight.incrementAndGet() > MAX_IN_FLIGHT) {
            inFlight.decrementAndGet();
            warnings.warn(kind + "-busy", "Health " + kind + " delivery skipped for customer " + customerId
                    + ": earlier deliveries are still running", null);
            return;
        }
        try {
            executor.execute(() -> {
                try {
                    task.run();
                } catch (Throwable t) {
                    warnings.warn(kind + "-failed", "Health " + kind + " delivery failed for customer " + customerId
                            + ": " + t.getClass().getSimpleName(), null);
                } finally {
                    inFlight.decrementAndGet();
                }
            });
        } catch (RuntimeException e) {
            inFlight.decrementAndGet();
            throw e;
        }
    }

    private void deliverPopups(int customerId, List<Alert> alerts) {
        int failed = 0;
        for (Alert a : alerts) {
            if (!alertService.sendAlert(a)) {
                failed++;
            }
        }
        if (failed > 0) {
            warnings.warn("pop-up-rejected", "Health pop-ups for customer " + customerId + ": " + failed + " of "
                    + alerts.size() + " not delivered", null);
        }
    }

    private void deliverWebhook(int customerId, String url, String body) {
        try {
            int status = webhookSender.send(url, body);
            if (status >= 200 && status < 300) {
                logger.info("Health webhook for customer {}: HTTP {}", customerId, status);
            } else {
                warnings.warn("webhook-status", "Health webhook for customer " + customerId + ": HTTP " + status, null);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            warnings.warn("webhook-error", "Health webhook for customer " + customerId + " failed: "
                    + e.getClass().getSimpleName(), null);
        }
    }
}
