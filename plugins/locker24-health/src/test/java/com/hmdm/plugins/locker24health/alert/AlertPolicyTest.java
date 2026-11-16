package com.hmdm.plugins.locker24health.alert;

import com.hmdm.plugins.locker24health.status.Condition;
import com.hmdm.plugins.locker24health.status.Finding;
import com.hmdm.plugins.locker24health.status.Severity;
import com.hmdm.plugins.locker24health.status.Status;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AlertPolicyTest {

    private static final long NOW = 1_763_300_000_000L;

    private static Finding red(Condition c) {
        return new Finding(c, Severity.RED, "d");
    }

    private static Finding yellow(Condition c) {
        return new Finding(c, Severity.YELLOW, "d");
    }

    private static OpenAlert open(Condition c, long lastPresentAt) {
        return new OpenAlert(1, c, Severity.YELLOW, lastPresentAt - 1000, lastPresentAt);
    }

    private static AlertEvent event(Condition c, Severity s, int device) {
        return new AlertEvent(1, device, "D" + device, "cfg", "uid", c, s, false, "d", NOW, null);
    }

    @Test
    public void redRaisesImmediately() {
        AlertDecision d = AlertPolicy.decide(NOW, Status.RED, null, List.of(red(Condition.OFFLINE)), Map.of());
        assertEquals(List.of(red(Condition.OFFLINE)), d.raise());
        assertTrue(d.clear().isEmpty());
    }

    @Test
    public void yellowRaisesAfterThirtyMinutes() {
        List<Finding> f = List.of(yellow(Condition.BATTERY_LOW));
        assertTrue(AlertPolicy.decide(NOW, Status.YELLOW, NOW - 1_799_999, f, Map.of()).raise().isEmpty());
        assertEquals(f, AlertPolicy.decide(NOW, Status.YELLOW, NOW - 1_800_000, f, Map.of()).raise());
    }

    @Test
    public void oneOpenAlertPerCondition() {
        OpenAlert o = open(Condition.BATTERY_LOW, NOW - 60_000);
        AlertDecision d = AlertPolicy.decide(NOW, Status.RED, NOW - 10, List.of(red(Condition.BATTERY_LOW)),
                Map.of(Condition.BATTERY_LOW, o));
        assertTrue(d.raise().isEmpty());
        assertEquals(List.of(o), d.touch());
        assertTrue(d.clear().isEmpty());
    }

    @Test
    public void clearsAfterFiveMinutesAbsent() {
        OpenAlert fresh = open(Condition.TEMPERATURE, NOW - 299_999);
        AlertDecision d1 = AlertPolicy.decide(NOW, Status.GREEN, null, List.of(), Map.of(Condition.TEMPERATURE, fresh));
        assertTrue(d1.clear().isEmpty());
        OpenAlert old = open(Condition.TEMPERATURE, NOW - 300_000);
        AlertDecision d2 = AlertPolicy.decide(NOW, Status.GREEN, null, List.of(), Map.of(Condition.TEMPERATURE, old));
        assertEquals(List.of(old), d2.clear());
    }

    @Test
    public void offlineKeepsOtherAlertsOpen() {
        OpenAlert o = open(Condition.BATTERY_LOW, NOW - 1_200_000);
        AlertDecision d = AlertPolicy.decide(NOW, Status.RED, NOW - 5, List.of(red(Condition.OFFLINE)),
                Map.of(Condition.BATTERY_LOW, o));
        assertTrue(d.clear().isEmpty());
    }

    @Test
    public void adminKioskOffNeverAlerts() {
        AlertDecision d = AlertPolicy.decide(NOW, Status.YELLOW, NOW - 3_600_000,
                List.of(yellow(Condition.KIOSK_ADMIN_OFF)), Map.of());
        assertTrue(d.raise().isEmpty());
        assertNull(d.nonGreenSince());
    }

    @Test
    public void adminKioskOffDoesNotAgeOtherYellows() {
        AlertDecision d = AlertPolicy.decide(NOW, Status.YELLOW, null,
                List.of(yellow(Condition.KIOSK_ADMIN_OFF), yellow(Condition.BATTERY_LOW)), Map.of());
        assertEquals(Long.valueOf(NOW), d.nonGreenSince());
        assertTrue(d.raise().isEmpty());
    }

    @Test
    public void nonGreenSinceTracking() {
        assertNull(AlertPolicy.decide(NOW, Status.GREEN, 123L, List.of(), Map.of()).nonGreenSince());
        List<Finding> f = List.of(yellow(Condition.STORAGE_LOW));
        assertEquals(Long.valueOf(NOW), AlertPolicy.decide(NOW, Status.YELLOW, null, f, Map.of()).nonGreenSince());
        assertEquals(Long.valueOf(123L), AlertPolicy.decide(NOW, Status.YELLOW, 123L, f, Map.of()).nonGreenSince());
    }

    @Test
    public void stormCap() {
        List<AlertEvent> events = new ArrayList<>();
        events.add(event(Condition.BATTERY_LOW, Severity.YELLOW, 99));
        for (int i = 0; i < 11; i++) {
            events.add(event(Condition.OFFLINE, Severity.RED, i));
        }
        List<AlertEvent> out = AlertPolicy.limitPopups(events, 5);
        assertEquals(6, out.size());
        for (int i = 0; i < 5; i++) {
            assertEquals(Condition.OFFLINE, out.get(i).condition());
            assertEquals(Severity.RED, out.get(i).severity());
        }
        assertNull(out.get(5).deviceId());
        assertEquals("7 more tablets need attention: offline 6, battery_low 1", out.get(5).message());

        List<AlertEvent> five = events.subList(0, 5);
        assertEquals(five, AlertPolicy.limitPopups(five, 5));
    }

    @Test
    public void stormCapLeavesClearedUncapped() {
        List<AlertEvent> events = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            events.add(event(Condition.OFFLINE, Severity.RED, i));
        }
        for (int i = 0; i < 3; i++) {
            events.add(new AlertEvent(1, 50 + i, "C", "cfg", "uid", Condition.OFFLINE, Severity.YELLOW, true, "d", NOW, null));
        }
        List<AlertEvent> out = AlertPolicy.limitPopups(events, 5);
        assertEquals(9, out.size());
        assertEquals(3, out.stream().filter(AlertEvent::cleared).count());
        assertEquals("2 more tablets need attention: offline 2", out.get(5).message());
    }
}
