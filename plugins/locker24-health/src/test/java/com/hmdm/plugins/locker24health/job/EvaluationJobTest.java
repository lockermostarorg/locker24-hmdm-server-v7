package com.hmdm.plugins.locker24health.job;

import com.hmdm.plugins.locker24health.HealthPluginConfigurationImpl;
import com.hmdm.plugins.locker24health.alert.AlertDecision;
import com.hmdm.plugins.locker24health.alert.AlertDispatcher;
import com.hmdm.plugins.locker24health.alert.AlertEvent;
import com.hmdm.plugins.locker24health.alert.OpenAlert;
import com.hmdm.plugins.locker24health.persistence.HealthDAO;
import com.hmdm.plugins.locker24health.persistence.domain.EvalRow;
import com.hmdm.plugins.locker24health.persistence.domain.SettingsRow;
import com.hmdm.plugins.locker24health.status.Condition;
import com.hmdm.plugins.locker24health.status.ConfiguredKiosk;
import com.hmdm.plugins.locker24health.status.Evaluation;
import com.hmdm.plugins.locker24health.status.Severity;
import com.hmdm.plugins.locker24health.status.Status;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyMapOf;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class EvaluationJobTest {

    private static final long NOW = 1_763_290_060_000L;

    private HealthDAO dao;
    private AlertDispatcher dispatcher;
    private EvalRow a;
    private EvalRow b;
    private EvalRow c;
    private Map<Integer, Map<Condition, OpenAlert>> open;
    private List<SettingsRow> settings;

    private static EvalRow row(int device, int customer, int configuration, long receivedAt) {
        EvalRow r = new EvalRow();
        r.setDeviceId(device);
        r.setCustomerId(customer);
        r.setConfigurationId(configuration);
        r.setDeviceNumber("M-0" + device);
        r.setReceivedAt(receivedAt);
        r.setMeasuredAt(receivedAt);
        return r;
    }

    private static AlertEvent event(int device) {
        return new AlertEvent(1, device, "M-0" + device, "cfg", null, Condition.OFFLINE, Severity.RED, false, null, NOW, null);
    }

    @Before
    public void setUp() {
        dao = mock(HealthDAO.class);
        dispatcher = mock(AlertDispatcher.class);
        a = row(1, 1, 10, NOW - 60_000);
        b = row(2, 1, 10, NOW - 60_000);
        c = row(3, 2, 20, NOW - 60_000);
        open = new java.util.HashMap<>();
        settings = new ArrayList<>();
        when(dao.loadEvaluationRows()).thenReturn(List.of(a, b, c));
        when(dao.loadOpenAlerts()).thenReturn(open);
        when(dao.loadSettings()).thenAnswer(inv -> {
            Map<Integer, SettingsRow> m = new java.util.HashMap<>();
            settings.forEach(s -> m.put(s.getCustomerId(), s));
            return m;
        });
    }

    private EvaluationJob job(Function<Integer, ConfiguredKiosk> resolver, java.util.function.IntPredicate enabled) {
        return new EvaluationJob(dao, resolver, dispatcher, enabled, () -> NOW);
    }

    private EvaluationJob job() {
        return job(id -> null, id -> true);
    }

    @SuppressWarnings("unchecked")
    private void apply(EvalRow row, List<AlertEvent> events) {
        when(dao.applyEvaluation(same(row), any(Evaluation.class), any(AlertDecision.class),
                anyMapOf(Condition.class, OpenAlert.class), anyLong())).thenReturn(events);
    }

    @SuppressWarnings("unchecked")
    private void verifyApplied(EvalRow row, boolean expected) {
        if (expected) {
            verify(dao).applyEvaluation(same(row), any(Evaluation.class), any(AlertDecision.class),
                    anyMapOf(Condition.class, OpenAlert.class), eq(NOW));
        } else {
            verify(dao, never()).applyEvaluation(same(row), any(Evaluation.class), any(AlertDecision.class),
                    anyMapOf(Condition.class, OpenAlert.class), anyLong());
        }
    }

    @SuppressWarnings("unchecked")
    private List<AlertEvent> dispatched() {
        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(dispatcher).dispatch(captor.capture(), anyMapOf(Integer.class, String.class), eq(NOW));
        return captor.getValue();
    }

    @Test
    public void oneFailingDeviceDoesNotStopTheCycle() {
        when(dao.applyEvaluation(same(a), any(Evaluation.class), any(AlertDecision.class),
                anyMapOf(Condition.class, OpenAlert.class), anyLong())).thenThrow(new IllegalStateException("db"));
        apply(b, List.of(event(2)));
        apply(c, List.of(event(3)));
        job().runOnce();
        assertEquals(List.of(event(2), event(3)), dispatched());
    }

    @Test
    public void disabledCustomerIsSkipped() {
        apply(a, List.of());
        apply(b, List.of());
        job(id -> null, id -> id != 2).runOnce();
        verifyApplied(c, false);
        verifyApplied(a, true);
    }

    @Test
    public void kioskResolvedOncePerConfigurationPerRun() {
        AtomicInteger calls = new AtomicInteger();
        apply(a, List.of());
        apply(b, List.of());
        apply(c, List.of());
        job(id -> {
            calls.incrementAndGet();
            return new ConfiguredKiosk("p", "1", false, null, null);
        }, id -> true).runOnce();
        assertEquals(2, calls.get());
    }

    @Test
    public void unresolvableConfigurationSkipsOnlyItsDevices() {
        AtomicInteger calls = new AtomicInteger();
        apply(c, List.of(event(3)));
        job(id -> {
            calls.incrementAndGet();
            if (id == 10) {
                throw new IllegalStateException("config lookup failed");
            }
            return null;
        }, id -> true).runOnce();
        verifyApplied(a, false);
        verifyApplied(b, false);
        verifyApplied(c, true);
        assertEquals(2, calls.get());
        assertEquals(List.of(event(3)), dispatched());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void latestReceivedAtClearsAStaleOfflineAlert() {
        OpenAlert offline = new OpenAlert(7, Condition.OFFLINE, Severity.RED, NOW - 3_600_000, NOW - 360_000);
        open.put(1, Map.of(Condition.OFFLINE, offline));
        when(dao.loadEvaluationRows()).thenReturn(List.of(a));
        job().runOnce();
        ArgumentCaptor<AlertDecision> decision = ArgumentCaptor.forClass(AlertDecision.class);
        ArgumentCaptor<Map> openArg = ArgumentCaptor.forClass(Map.class);
        verify(dao).applyEvaluation(same(a), any(Evaluation.class), decision.capture(), openArg.capture(), eq(NOW));
        assertEquals(List.of(offline), decision.getValue().clear());
        assertEquals(Map.of(Condition.OFFLINE, offline), openArg.getValue());
    }

    @Test
    public void restartGraceKeepsLongSilentDevicesOnline() {
        EvalRow silent = row(9, 1, 10, NOW - 7_200_000);
        when(dao.loadEvaluationRows()).thenReturn(List.of(silent));
        EvaluationJob job = job();
        job.setServerStartedAt(NOW - 120_000);
        job.runOnce();
        ArgumentCaptor<Evaluation> evaluation = ArgumentCaptor.forClass(Evaluation.class);
        verify(dao).applyEvaluation(same(silent), evaluation.capture(), any(AlertDecision.class),
                anyMapOf(Condition.class, OpenAlert.class), eq(NOW));
        assertEquals(Status.GREEN, evaluation.getValue().status());

        job.setServerStartedAt(NOW - 660_000);
        job.runOnce();
        verify(dao, org.mockito.Mockito.times(2)).applyEvaluation(same(silent), evaluation.capture(),
                any(AlertDecision.class), anyMapOf(Condition.class, OpenAlert.class), eq(NOW));
        assertEquals(Status.RED, evaluation.getValue().status());
        assertEquals(Condition.OFFLINE, evaluation.getValue().findings().get(0).condition());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void noEventsMeansNoDispatch() {
        apply(a, List.of());
        apply(b, List.of());
        apply(c, List.of());
        job().runOnce();
        verify(dispatcher, never()).dispatch(anyListOf(AlertEvent.class), anyMapOf(Integer.class, String.class), anyLong());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void webhookUrlsComeFromSettings() {
        settings.add(settingsRow(1, "https://hooks.example/abc", "Bad/Zone"));
        settings.add(settingsRow(2, "  ", null));
        apply(a, List.of(event(1)));
        job().runOnce();
        ArgumentCaptor<Map> urls = ArgumentCaptor.forClass(Map.class);
        verify(dispatcher).dispatch(anyListOf(AlertEvent.class), urls.capture(), eq(NOW));
        assertEquals(Map.of(1, "https://hooks.example/abc"), urls.getValue());
    }

    @Test
    public void zoneFallsBackToSarajevo() {
        assertEquals(HealthPluginConfigurationImpl.DEFAULT_ZONE, EvaluationJob.zone(null));
        assertEquals(HealthPluginConfigurationImpl.DEFAULT_ZONE, EvaluationJob.zone(settingsRow(1, null, "Bad/Zone")));
        assertEquals(HealthPluginConfigurationImpl.DEFAULT_ZONE, EvaluationJob.zone(settingsRow(1, null, " ")));
        assertEquals(ZoneId.of("Europe/Berlin"), EvaluationJob.zone(settingsRow(1, null, "Europe/Berlin")));
        assertEquals(ZoneId.of("Europe/Sarajevo"), HealthPluginConfigurationImpl.DEFAULT_ZONE);
    }

    @Test
    public void emptyFleetLoadsNothingElse() {
        when(dao.loadEvaluationRows()).thenReturn(List.of());
        job().runOnce();
        verify(dao, never()).loadOpenAlerts();
        verify(dao, never()).loadSettings();
    }

    private static SettingsRow settingsRow(int customer, String url, String zone) {
        SettingsRow s = new SettingsRow();
        s.setCustomerId(customer);
        s.setWebhookUrl(url);
        s.setTimeZone(zone);
        return s;
    }

    @Test
    public void loadTimeoutSkipsTheCycleQuietly() {
        when(dao.loadEvaluationRows()).thenThrow(new org.apache.ibatis.exceptions.PersistenceException(
                new java.sql.SQLException("canceling statement due to user request", "57014")));
        job().runOnce();
        verify(dao, never()).loadOpenAlerts();
    }

    @Test(expected = IllegalStateException.class)
    public void otherLoadFailuresStillReachTheGuard() {
        when(dao.loadEvaluationRows()).thenThrow(new IllegalStateException("db down"));
        job().runOnce();
    }

    @Test
    public void deviceTimeoutDoesNotStopTheCycle() {
        when(dao.applyEvaluation(same(a), any(Evaluation.class), any(AlertDecision.class),
                anyMapOf(Condition.class, OpenAlert.class), anyLong())).thenThrow(
                new org.apache.ibatis.exceptions.PersistenceException(new java.sql.SQLTimeoutException("t")));
        apply(b, List.of(event(2)));
        apply(c, List.of());
        job().runOnce();
        assertEquals(List.of(event(2)), dispatched());
    }
}
