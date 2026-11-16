package com.hmdm.plugins.locker24health.alert;

import com.hmdm.plugins.locker24health.status.Condition;
import com.hmdm.plugins.locker24health.status.ConfiguredKiosk;
import com.hmdm.plugins.locker24health.status.DeviceHealth;
import com.hmdm.plugins.locker24health.status.EvalContext;
import com.hmdm.plugins.locker24health.status.Finding;
import com.hmdm.plugins.locker24health.status.Severity;
import com.hmdm.plugins.locker24health.status.StatusEvaluator;
import org.junit.Test;

import java.time.ZoneId;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AlertTextTest {

    private static final long NOW = 1_763_290_060_000L;

    private static AlertEvent raised(String uid, Condition c, Severity s, String detail) {
        return new AlertEvent(1, 5, "M-01", "hpmo-qa", uid, c, s, false, detail, NOW, null);
    }

    private static AlertEvent cleared(Condition c, Severity s) {
        return new AlertEvent(1, 5, "M-01", "hpmo-qa", null, c, s, true, null, NOW, null);
    }

    private static Finding finding(DeviceHealth h) {
        EvalContext c = new EvalContext(NOW, NOW, (ConfiguredKiosk) null, ZoneId.of("Europe/Sarajevo"), null);
        return StatusEvaluator.evaluate(h, c).findings().get(0);
    }

    private static DeviceHealth health(Integer battery, Float temperature, Integer storage) {
        return new DeviceHealth(NOW, NOW, battery, true, temperature, storage, null, null, null, null, null, null, 0);
    }

    @Test
    public void batteryWithLockerAndSeverity() {
        Finding f = finding(health(9, null, null));
        assertEquals("M-01 (hpmo-qa, locker HP01): battery 9 % (red)",
                AlertText.message(raised("HP01", f.condition(), f.severity(), f.detail())));
    }

    @Test
    public void offlineWithoutShortUid() {
        assertEquals("M-01 (hpmo-qa): offline",
                AlertText.message(raised(null, Condition.OFFLINE, Severity.RED, "no report for 12 min")));
        assertEquals("M-01 (hpmo-qa): offline",
                AlertText.message(raised("  ", Condition.OFFLINE, Severity.RED, "no report for 12 min")));
    }

    @Test
    public void clearedUsesCode() {
        assertEquals("M-01 (hpmo-qa): cleared not_charging",
                AlertText.message(cleared(Condition.NOT_CHARGING, Severity.YELLOW)));
    }

    @Test
    public void severitySuffixOnlyForValues() {
        Finding t = finding(health(80, 46.0f, null));
        assertEquals("M-01 (hpmo-qa): temperature 46.0 C (yellow)",
                AlertText.message(raised(null, t.condition(), t.severity(), t.detail())));
        Finding s = finding(health(80, null, 512));
        assertEquals("M-01 (hpmo-qa): storage 512 MB free (yellow)",
                AlertText.message(raised(null, s.condition(), s.severity(), s.detail())));
        assertEquals("M-01 (hpmo-qa): not_charging",
                AlertText.message(raised(null, Condition.NOT_CHARGING, Severity.YELLOW, "not charging")));
        assertEquals("M-01 (hpmo-qa): kiosk_not_running",
                AlertText.message(raised(null, Condition.KIOSK_NOT_RUNNING, Severity.RED, "kiosk app not running")));
        assertEquals("M-01 (hpmo-qa): kiosk_version",
                AlertText.message(raised(null, Condition.KIOSK_VERSION, Severity.YELLOW, "installed 1, configured 2")));
    }

    @Test
    public void summaryReturnsItsMessage() {
        AlertEvent summary = new AlertEvent(1, null, null, null, null, null, Severity.RED, false, null, NOW,
                "7 more tablets need attention: offline 6, battery_low 1");
        assertEquals("7 more tablets need attention: offline 6, battery_low 1", AlertText.message(summary));
    }

    @Test
    public void missingConfigurationAndNumber() {
        AlertEvent e = new AlertEvent(1, 5, null, null, "HP01", Condition.OFFLINE, Severity.RED, false, null, NOW, null);
        assertEquals("device 5 (locker HP01): offline", AlertText.message(e));
    }

    @Test
    public void boundedAndNeverHeadwind() {
        String longNumber = "M".repeat(2000);
        List<AlertEvent> all = List.of(
                raised("HP01", Condition.BATTERY_LOW, Severity.RED, "battery 9 %"),
                raised(null, Condition.OFFLINE, Severity.RED, null),
                cleared(Condition.NOT_CHARGING, Severity.YELLOW),
                new AlertEvent(1, 5, longNumber, longNumber, longNumber, Condition.OFFLINE, Severity.RED, false, null, NOW, null),
                new AlertEvent(1, null, null, null, null, null, Severity.RED, false, null, NOW, "x".repeat(3000)));
        for (AlertEvent e : all) {
            String m = AlertText.message(e);
            assertTrue(m.length() <= AlertText.MAX_LENGTH);
            assertFalse(m.toLowerCase().contains("headwind"));
        }
    }

    @Test
    public void truncationKeepsSurrogatePairsWhole() {
        String number = "M".repeat(1023) + "😀";
        String m = AlertText.message(new AlertEvent(1, 5, number, null, null, Condition.OFFLINE, Severity.RED, false, null, NOW, null));
        assertEquals(1023, m.length());
        assertFalse(Character.isHighSurrogate(m.charAt(m.length() - 1)));
    }
}
