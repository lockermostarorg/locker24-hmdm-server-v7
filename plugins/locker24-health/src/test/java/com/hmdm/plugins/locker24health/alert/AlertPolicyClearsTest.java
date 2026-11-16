package com.hmdm.plugins.locker24health.alert;

import com.hmdm.plugins.locker24health.status.Condition;
import com.hmdm.plugins.locker24health.status.Severity;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AlertPolicyClearsTest {

    private static final long NOW = 1_763_290_060_000L;

    private static AlertEvent cleared(int device, long at) {
        return new AlertEvent(1, device, String.format("M-%02d", device), "cfg", null, Condition.NOT_CHARGING,
                Severity.YELLOW, true, null, at, null);
    }

    private static AlertEvent raised(int device) {
        return new AlertEvent(1, device, String.format("M-%02d", device), "cfg", null, Condition.OFFLINE,
                Severity.RED, false, null, NOW, null);
    }

    @Test
    public void upToTheCapNothingChanges() {
        List<AlertEvent> in = List.of(raised(9), cleared(3, NOW), cleared(1, NOW));
        assertEquals(in, AlertPolicy.limitClears(in, 5));
    }

    @Test
    public void overTheCapFiveThenOneRecoveredSummary() {
        List<AlertEvent> in = new ArrayList<>();
        in.add(raised(99));
        for (int d = 8; d >= 1; d--) {
            in.add(cleared(d, NOW + d));
        }
        List<AlertEvent> out = AlertPolicy.limitClears(in, 5);
        assertEquals(7, out.size());
        assertEquals(raised(99), out.get(0));
        for (int i = 0; i < 5; i++) {
            assertEquals(Integer.valueOf(i + 1), out.get(i + 1).deviceId());
        }
        AlertEvent summary = out.get(6);
        assertTrue(summary.cleared());
        assertNull(summary.deviceId());
        assertNull(summary.condition());
        assertEquals("3 more tablets recovered", summary.message());
        assertEquals("3 more tablets recovered", AlertText.message(summary));
        assertEquals(NOW + 8, summary.at());
    }

    @Test
    public void singularWording() {
        List<AlertEvent> in = new ArrayList<>();
        for (int d = 1; d <= 6; d++) {
            in.add(cleared(d, NOW));
        }
        assertEquals("1 more tablet recovered", AlertPolicy.limitClears(in, 5).get(5).message());
    }
}
