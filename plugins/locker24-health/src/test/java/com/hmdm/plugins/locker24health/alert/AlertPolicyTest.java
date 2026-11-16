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

    private static AlertDecision decide(long now, Status s, Long ng, List<Finding> f, Map<Condition, OpenAlert> o) {
        return AlertPolicy.decide(now, s, ng, f, o, now);
    }

    private static OpenAlert openRed(Condition c, long lastPresentAt) {
        return new OpenAlert(1, c, Severity.RED, lastPresentAt - 1000, lastPresentAt);
    }

    private static AlertEvent event(Condition c, Severity s, int device) {
        return new AlertEvent(1, device, "D" + device, "cfg", "uid", c, s, false, "d", NOW, null);
    }

    @Test
    public void redRaisesImmediately() {
        AlertDecision d = decide(NOW, Status.RED, null, List.of(red(Condition.OFFLINE)), Map.of());
        assertEquals(List.of(red(Condition.OFFLINE)), d.raise());
        assertTrue(d.clear().isEmpty());
    }

    @Test
    public void yellowRaisesAfterThirtyMinutes() {
        List<Finding> f = List.of(yellow(Condition.BATTERY_LOW));
        assertTrue(decide(NOW, Status.YELLOW, NOW - 1_799_999, f, Map.of()).raise().isEmpty());
        assertEquals(f, decide(NOW, Status.YELLOW, NOW - 1_800_000, f, Map.of()).raise());
    }

    @Test
    public void oneOpenAlertPerCondition() {
        OpenAlert o = openRed(Condition.BATTERY_LOW, NOW - 60_000);
        AlertDecision d = decide(NOW, Status.RED, NOW - 10, List.of(red(Condition.BATTERY_LOW)),
                Map.of(Condition.BATTERY_LOW, o));
        assertTrue(d.raise().isEmpty());
        assertEquals(List.of(o), d.touch());
        assertTrue(d.clear().isEmpty());
    }

    @Test
    public void yellowEscalatesToRedOnce() {
        OpenAlert y = open(Condition.BATTERY_LOW, NOW - 60_000);
        Finding r = red(Condition.BATTERY_LOW);
        AlertDecision d = decide(NOW, Status.RED, NOW - 10, List.of(r), Map.of(Condition.BATTERY_LOW, y));
        assertEquals(List.of(r), d.escalate());
        assertTrue(d.raise().isEmpty());
        OpenAlert nowRed = openRed(Condition.BATTERY_LOW, NOW);
        AlertDecision d2 = decide(NOW + 60_000, Status.RED, NOW - 10, List.of(r), Map.of(Condition.BATTERY_LOW, nowRed));
        assertTrue(d2.escalate().isEmpty());
        assertEquals(List.of(nowRed), d2.touch());
    }

    @Test
    public void redNeverDeescalates() {
        OpenAlert r = openRed(Condition.BATTERY_LOW, NOW - 60_000);
        AlertDecision d = decide(NOW, Status.YELLOW, NOW - 10, List.of(yellow(Condition.BATTERY_LOW)),
                Map.of(Condition.BATTERY_LOW, r));
        assertTrue(d.escalate().isEmpty());
        assertTrue(d.raise().isEmpty());
        assertEquals(List.of(r), d.touch());
    }

    @Test
    public void offlineAlertClearsOnlyOnRealReport() {
        OpenAlert o = openRed(Condition.OFFLINE, NOW - 600_000);
        Map<Condition, OpenAlert> m = Map.of(Condition.OFFLINE, o);
        assertTrue(AlertPolicy.decide(NOW, Status.GREEN, null, List.of(), m, NOW - 700_000).clear().isEmpty());
        assertTrue(AlertPolicy.decide(NOW, Status.GREEN, null, List.of(), m, o.lastPresentAt()).clear().isEmpty());
        assertTrue(AlertPolicy.decide(NOW, Status.GREEN, null, List.of(), m, null).clear().isEmpty());
        assertEquals(List.of(o),
                AlertPolicy.decide(NOW, Status.GREEN, null, List.of(), m, o.lastPresentAt() + 1).clear());
        OpenAlert recent = openRed(Condition.OFFLINE, NOW - 100_000);
        assertTrue(AlertPolicy.decide(NOW, Status.GREEN, null, List.of(), Map.of(Condition.OFFLINE, recent), NOW - 1)
                .clear().isEmpty());
    }

    @Test
    public void clearsAfterFiveMinutesAbsent() {
        OpenAlert fresh = open(Condition.TEMPERATURE, NOW - 299_999);
        AlertDecision d1 = decide(NOW, Status.GREEN, null, List.of(), Map.of(Condition.TEMPERATURE, fresh));
        assertTrue(d1.clear().isEmpty());
        OpenAlert old = open(Condition.TEMPERATURE, NOW - 300_000);
        AlertDecision d2 = decide(NOW, Status.GREEN, null, List.of(), Map.of(Condition.TEMPERATURE, old));
        assertEquals(List.of(old), d2.clear());
    }

    @Test
    public void offlineKeepsOtherAlertsOpen() {
        OpenAlert o = open(Condition.BATTERY_LOW, NOW - 1_200_000);
        AlertDecision d = decide(NOW, Status.RED, NOW - 5, List.of(red(Condition.OFFLINE)),
                Map.of(Condition.BATTERY_LOW, o));
        assertTrue(d.clear().isEmpty());
        assertEquals(List.of(o), d.touch());
    }

    @Test
    public void adminKioskOffNeverAlerts() {
        AlertDecision d = decide(NOW, Status.YELLOW, NOW - 3_600_000,
                List.of(yellow(Condition.KIOSK_ADMIN_OFF)), Map.of());
        assertTrue(d.raise().isEmpty());
        assertNull(d.nonGreenSince());
    }

    @Test
    public void adminKioskOffDoesNotAgeOtherYellows() {
        AlertDecision d = decide(NOW, Status.YELLOW, null,
                List.of(yellow(Condition.KIOSK_ADMIN_OFF), yellow(Condition.BATTERY_LOW)), Map.of());
        assertEquals(Long.valueOf(NOW), d.nonGreenSince());
        assertTrue(d.raise().isEmpty());
    }

    @Test
    public void nonGreenSinceTracking() {
        assertNull(decide(NOW, Status.GREEN, 123L, List.of(), Map.of()).nonGreenSince());
        List<Finding> f = List.of(yellow(Condition.STORAGE_LOW));
        assertEquals(Long.valueOf(NOW), decide(NOW, Status.YELLOW, null, f, Map.of()).nonGreenSince());
        assertEquals(Long.valueOf(123L), decide(NOW, Status.YELLOW, 123L, f, Map.of()).nonGreenSince());
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

        List<AlertEvent> five = new ArrayList<>(events.subList(1, 6));
        List<AlertEvent> sameFive = AlertPolicy.limitPopups(five, 5);
        assertEquals(five, sameFive);
        assertTrue(sameFive != five);
    }

    @Test
    public void stormCapSortsByDeviceNumberAndCountsDistinctTablets() {
        List<AlertEvent> events = new ArrayList<>();
        events.add(event(Condition.OFFLINE, Severity.RED, 3));
        events.add(event(Condition.BATTERY_LOW, Severity.RED, 3));
        events.add(event(Condition.OFFLINE, Severity.RED, 1));
        events.add(event(Condition.OFFLINE, Severity.RED, 2));
        List<AlertEvent> out = AlertPolicy.limitPopups(events, 2);
        assertEquals("D1", out.get(0).deviceNumber());
        assertEquals("D2", out.get(1).deviceNumber());
        assertEquals("1 more tablet needs attention: offline 1, battery_low 1", out.get(2).message());
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
