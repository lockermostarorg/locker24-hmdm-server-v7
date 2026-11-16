package com.hmdm.plugins.locker24health.alert;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdm.persistence.domain.Alert;
import com.hmdm.plugins.locker24health.status.Condition;
import com.hmdm.plugins.locker24health.status.Severity;
import com.hmdm.service.AlertService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AlertDispatcherTest {

    private static final long T = 1_763_290_060_000L;
    private static final long MIN = 60_000L;
    private static final String URL = "https://hooks.example/token-abc";

    private AlertService alertService;
    private WebhookSender webhook;
    private List<Runnable> queued;
    private boolean runInline;
    private AlertDispatcher dispatcher;

    @Before
    public void setUp() throws Exception {
        alertService = mock(AlertService.class);
        when(alertService.sendAlert(any(Alert.class))).thenReturn(true);
        webhook = mock(WebhookSender.class);
        when(webhook.send(anyString(), anyString())).thenReturn(204);
        queued = new ArrayList<>();
        runInline = true;
        dispatcher = new AlertDispatcher(alertService, webhook, r -> {
            if (runInline) {
                r.run();
            } else {
                queued.add(r);
            }
        }, new PopupBudget(5, 10 * MIN));
    }

    private static AlertEvent raised(int customer, int device, Severity s) {
        return new AlertEvent(customer, device, String.format("M-%02d", device), "hpmo-qa", null, Condition.OFFLINE, s,
                false, null, T, null);
    }

    private static AlertEvent cleared(int customer, int device) {
        return new AlertEvent(customer, device, String.format("M-%02d", device), "hpmo-qa", null, Condition.NOT_CHARGING,
                Severity.YELLOW, true, null, T, null);
    }

    private static List<AlertEvent> raisedMany(int customer, int from, int count) {
        return IntStream.range(from, from + count).mapToObj(d -> raised(customer, d, Severity.RED)).collect(Collectors.toList());
    }

    private List<Alert> sent(int times) {
        ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
        verify(alertService, times(times)).sendAlert(captor.capture());
        return captor.getAllValues();
    }

    @Test
    public void capsEachCustomerAtFivePlusSummary() {
        dispatcher.dispatch(raisedMany(1, 1, 7), Map.of(), T);
        List<Alert> alerts = sent(6);
        for (int i = 0; i < 5; i++) {
            assertEquals(Alert.SEVERE, alerts.get(i).getLevel());
            assertEquals(Integer.valueOf(i + 1), alerts.get(i).getDeviceId());
            assertEquals(1, alerts.get(i).getCustomerId());
        }
        Alert summary = alerts.get(5);
        assertNull(summary.getDeviceId());
        assertEquals(Alert.SEVERE, summary.getLevel());
        assertEquals("2 more tablets need attention: offline 2", summary.getMessage());
    }

    @Test
    public void levelsFollowSeverity() {
        assertEquals(Alert.SEVERE, AlertDispatcher.level(raised(1, 1, Severity.RED)));
        assertEquals(Alert.WARNING, AlertDispatcher.level(raised(1, 1, Severity.YELLOW)));
        assertEquals(Alert.INFO, AlertDispatcher.level(cleared(1, 1)));
        Alert a = AlertDispatcher.toAlert(1, raised(1, 4, Severity.RED));
        assertEquals("M-04 (hpmo-qa): offline", a.getMessage());
        assertEquals("M-04", a.getDeviceNumber());
    }

    @Test
    public void clearedAreInfoAndCappedWithARecoveredSummary() {
        List<AlertEvent> events = IntStream.range(1, 9).mapToObj(d -> cleared(1, d)).collect(Collectors.toList());
        dispatcher.dispatch(events, Map.of(), T);
        List<Alert> alerts = sent(6);
        assertTrue(alerts.stream().allMatch(x -> x.getLevel() == Alert.INFO));
        for (int i = 0; i < 5; i++) {
            assertEquals(Integer.valueOf(i + 1), alerts.get(i).getDeviceId());
            assertTrue(alerts.get(i).getMessage().contains("cleared not_charging"));
        }
        assertNull(alerts.get(5).getDeviceId());
        assertEquals("3 more tablets recovered", alerts.get(5).getMessage());
    }

    @Test
    public void webhookStillGetsEveryClear() throws Exception {
        List<AlertEvent> events = IntStream.range(1, 9).mapToObj(d -> cleared(1, d)).collect(Collectors.toList());
        dispatcher.dispatch(events, Map.of(1, URL), T);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(webhook).send(eq(URL), body.capture());
        assertEquals(8, new ObjectMapper().readTree(body.getValue()).get("events").size());
    }

    @Test
    public void rollingCapSpansCycles() {
        dispatcher.dispatch(raisedMany(1, 1, 3), Map.of(), T);
        assertEquals(3, sent(3).size());
        reset(alertService);
        when(alertService.sendAlert(any(Alert.class))).thenReturn(true);

        dispatcher.dispatch(raisedMany(1, 4, 3), Map.of(), T + MIN);
        List<Alert> second = sent(3);
        assertEquals(Integer.valueOf(4), second.get(0).getDeviceId());
        assertEquals(Integer.valueOf(5), second.get(1).getDeviceId());
        assertEquals("1 more tablet needs attention: offline 1", second.get(2).getMessage());
        reset(alertService);
        when(alertService.sendAlert(any(Alert.class))).thenReturn(true);

        dispatcher.dispatch(raisedMany(1, 7, 1), Map.of(), T + 2 * MIN);
        List<Alert> third = sent(1);
        assertNull(third.get(0).getDeviceId());
        reset(alertService);
        when(alertService.sendAlert(any(Alert.class))).thenReturn(true);

        dispatcher.dispatch(raisedMany(1, 8, 1), Map.of(), T + 11 * MIN);
        assertEquals(Integer.valueOf(8), sent(1).get(0).getDeviceId());
    }

    @Test
    public void customersHaveSeparateCaps() {
        List<AlertEvent> events = new ArrayList<>(raisedMany(1, 1, 7));
        events.add(raised(2, 50, Severity.YELLOW));
        dispatcher.dispatch(events, Map.of(), T);
        List<Alert> alerts = sent(7);
        Alert other = alerts.get(6);
        assertEquals(2, other.getCustomerId());
        assertEquals(Integer.valueOf(50), other.getDeviceId());
        assertEquals(Alert.WARNING, other.getLevel());
    }

    @Test
    public void webhookGetsTheFullUncappedListPerCustomer() throws Exception {
        List<AlertEvent> events = new ArrayList<>(raisedMany(1, 1, 7));
        events.add(cleared(1, 20));
        events.add(raised(2, 50, Severity.RED));
        dispatcher.dispatch(events, Map.of(1, URL), T);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(webhook, times(1)).send(eq(URL), body.capture());
        var root = new ObjectMapper().readTree(body.getValue());
        assertEquals(8, root.get("events").size());
        assertEquals(T, root.get("sent_at").asLong());
    }

    @Test
    public void noWebhookWithoutUrl() throws Exception {
        dispatcher.dispatch(raisedMany(1, 1, 2), Map.of(2, URL), T);
        dispatcher.dispatch(raisedMany(1, 3, 1), Map.of(1, " "), T);
        verify(webhook, never()).send(anyString(), anyString());
    }

    @Test
    public void deliveryFailuresNeverEscape() throws Exception {
        when(webhook.send(anyString(), anyString())).thenThrow(new IOException("connect failed to " + URL));
        when(alertService.sendAlert(any(Alert.class))).thenThrow(new IllegalStateException("loopback down"));
        dispatcher.dispatch(raisedMany(1, 1, 2), Map.of(1, URL), T);
        doReturn(false).when(alertService).sendAlert(any(Alert.class));
        dispatcher.dispatch(raisedMany(1, 3, 1), Map.of(1, URL), T + MIN);
        verify(webhook, times(2)).send(eq(URL), anyString());
    }

    @Test
    public void stuckDeliveriesAreBounded() throws Exception {
        runInline = false;
        for (int i = 0; i < 6; i++) {
            dispatcher.dispatch(List.of(cleared(1, i)), Map.of(1, URL), T + i);
        }
        assertEquals(2 * AlertDispatcher.MAX_IN_FLIGHT, queued.size());
        queued.forEach(Runnable::run);
        queued.clear();
        dispatcher.dispatch(List.of(cleared(1, 9)), Map.of(1, URL), T + 10);
        assertEquals(2, queued.size());
        verify(alertService, times(AlertDispatcher.MAX_IN_FLIGHT)).sendAlert(any(Alert.class));
    }
}
