package com.hmdm.plugins.locker24health.status;

import com.hmdm.plugins.locker24health.persistence.domain.LatestRow;
import org.junit.Test;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class StatusEvaluatorTest {

    private static final long NOW = 1_763_300_000_000L;
    private static final ZoneId ZONE = ZoneId.of("Europe/Sarajevo");
    private static final String PKG = "com.hpmo.tablet_client";

    private static class H {
        long receivedAt = NOW;
        long measuredAt = NOW;
        Integer battery = 80;
        Boolean charging = true;
        Float temp = 30f;
        Integer storage = 5000;
        Boolean required = true;
        Boolean running = true;
        Boolean admin = false;
        String pkg = PKG;
        String version = "1.2.8";
        Long downSince = null;
        int downReports = 0;

        DeviceHealth build() {
            return new DeviceHealth(receivedAt, measuredAt, battery, charging, temp, storage,
                    required, running, admin, pkg, version, downSince, downReports);
        }
    }

    private static EvalContext ctx(long now) {
        return new EvalContext(now, 0L, null, ZONE, null);
    }

    private static Evaluation eval(H h) {
        return StatusEvaluator.evaluate(h.build(), ctx(NOW));
    }

    private static List<Condition> conditions(Evaluation e) {
        return e.findings().stream().map(Finding::condition).collect(Collectors.toList());
    }

    private static void assertOnly(Status status, Evaluation e, Condition... expected) {
        assertEquals(status, e.status());
        assertEquals(List.of(expected), conditions(e));
    }

    @Test
    public void healthyIsGreen() {
        assertOnly(Status.GREEN, eval(new H()));
    }

    @Test
    public void offlineUsesServerReceiveTimeOnly() {
        H h = new H();
        h.receivedAt = NOW - 600_000;
        assertOnly(Status.GREEN, eval(h));
        h.receivedAt = NOW - 600_001;
        assertOnly(Status.RED, eval(h), Condition.OFFLINE);
    }

    @Test
    public void offlineMeasuredFromServerStart() {
        long start = NOW;
        H h = new H();
        h.receivedAt = start - 3_600_000;
        assertOnly(Status.GREEN, StatusEvaluator.evaluate(h.build(), new EvalContext(start + 600_000, start, null, ZONE, null)));
        assertOnly(Status.RED, StatusEvaluator.evaluate(h.build(), new EvalContext(start + 600_001, start, null, ZONE, null)),
                Condition.OFFLINE);
    }

    @Test
    public void offlineSuppressesStaleFindings() {
        H h = new H();
        h.receivedAt = NOW - 700_000;
        h.battery = 5;
        h.charging = false;
        assertOnly(Status.RED, eval(h), Condition.OFFLINE);
    }

    @Test
    public void singleKioskDownReportDoesNotAlert() {
        H h = new H();
        h.running = false;
        h.downSince = NOW;
        h.downReports = 1;
        assertOnly(Status.GREEN, eval(h));
    }

    @Test
    public void kioskDownWithoutStreakStateIsGreen() {
        H h = new H();
        h.running = false;
        assertOnly(Status.GREEN, eval(h));
    }

    @Test
    public void twoConsecutiveKioskDownReportsAreRed() {
        H h = new H();
        h.running = false;
        h.downSince = NOW - 60_000;
        h.downReports = 2;
        assertOnly(Status.RED, eval(h), Condition.KIOSK_NOT_RUNNING);
    }

    @Test
    public void kioskDownTwoMinutesIsRed() {
        H h = new H();
        h.running = false;
        h.downReports = 1;
        h.downSince = NOW - 119_999;
        assertOnly(Status.GREEN, eval(h));
        h.downSince = NOW - 120_000;
        assertOnly(Status.RED, eval(h), Condition.KIOSK_NOT_RUNNING);
    }

    @Test
    public void kioskAdminExitIsYellowRegardlessOfStreak() {
        H h = new H();
        h.running = false;
        h.admin = true;
        assertOnly(Status.YELLOW, eval(h), Condition.KIOSK_ADMIN_OFF);
        h.downReports = 5;
        h.downSince = NOW - 3_600_000;
        assertOnly(Status.YELLOW, eval(h), Condition.KIOSK_ADMIN_OFF);
        assertFalse(Condition.KIOSK_ADMIN_OFF.alerts());
        assertTrue(Condition.KIOSK_NOT_RUNNING.alerts());
    }

    @Test
    public void kioskNotRequiredOrUnknownIsGreen() {
        H h = new H();
        h.running = false;
        h.downReports = 9;
        h.downSince = NOW - 3_600_000;
        h.required = false;
        assertOnly(Status.GREEN, eval(h));
        h.required = null;
        assertOnly(Status.GREEN, eval(h));
    }

    @Test
    public void kioskRunningIsGreenEvenWithOldStreakColumns() {
        H h = new H();
        h.downReports = 3;
        h.downSince = NOW - 3_600_000;
        assertOnly(Status.GREEN, eval(h));
    }

    @Test
    public void batteryBoundaries() {
        H h = new H();
        h.battery = 20;
        assertOnly(Status.GREEN, eval(h));
        h.battery = 19;
        assertOnly(Status.YELLOW, eval(h), Condition.BATTERY_LOW);
        h.battery = 10;
        assertOnly(Status.YELLOW, eval(h), Condition.BATTERY_LOW);
        h.battery = 9;
        assertOnly(Status.RED, eval(h), Condition.BATTERY_LOW);
        h.battery = null;
        assertOnly(Status.GREEN, eval(h));
    }

    @Test
    public void notCharging() {
        H h = new H();
        h.charging = false;
        assertOnly(Status.YELLOW, eval(h), Condition.NOT_CHARGING);
        h.charging = null;
        assertOnly(Status.GREEN, eval(h));
    }

    @Test
    public void temperatureBoundaries() {
        H h = new H();
        h.temp = 45.0f;
        assertOnly(Status.GREEN, eval(h));
        h.temp = 45.1f;
        assertOnly(Status.YELLOW, eval(h), Condition.TEMPERATURE);
        h.temp = 50.0f;
        assertOnly(Status.YELLOW, eval(h), Condition.TEMPERATURE);
        h.temp = 50.1f;
        assertOnly(Status.RED, eval(h), Condition.TEMPERATURE);
        h.temp = null;
        assertOnly(Status.GREEN, eval(h));
    }

    @Test
    public void storageBoundary() {
        H h = new H();
        h.storage = 1024;
        assertOnly(Status.GREEN, eval(h));
        h.storage = 1023;
        assertOnly(Status.YELLOW, eval(h), Condition.STORAGE_LOW);
        h.storage = null;
        assertOnly(Status.GREEN, eval(h));
    }

    private static ConfiguredKiosk configured(boolean window, String from, String to) {
        return new ConfiguredKiosk(PKG, "1.2.8", window, LocalTime.parse(from), LocalTime.parse(to));
    }

    private static H outdated() {
        H h = new H();
        h.version = "1.2.7";
        return h;
    }

    private static long at(String localTime) {
        return atDay(16, localTime);
    }

    private static long atDay(int day, String localTime) {
        return ZonedDateTime.of(2026, 11, day, Integer.parseInt(localTime.substring(0, 2)),
                Integer.parseInt(localTime.substring(3, 5)), 0, 0, ZONE).toInstant().toEpochMilli();
    }

    private static Evaluation versionEval(H h, ConfiguredKiosk k, long now, Long since) {
        h.receivedAt = now;
        h.measuredAt = now;
        return StatusEvaluator.evaluate(h.build(), new EvalContext(now, 0L, k, ZONE, since));
    }

    @Test
    public void kioskVersionNeedsTwoHoursOutsideWindow() {
        ConfiguredKiosk off = configured(false, "02:00", "05:00");
        Evaluation first = versionEval(outdated(), off, NOW, null);
        assertOnly(Status.GREEN, first);
        assertEquals(Long.valueOf(NOW), first.versionMismatchSince());

        Evaluation edge = versionEval(outdated(), off, NOW, NOW - 7_200_000);
        assertOnly(Status.GREEN, edge);
        assertEquals(Long.valueOf(NOW - 7_200_000), edge.versionMismatchSince());

        assertOnly(Status.YELLOW, versionEval(outdated(), off, NOW, NOW - 7_200_001), Condition.KIOSK_VERSION);
    }

    @Test
    public void kioskVersionWindowSuppresses() {
        ConfiguredKiosk win = configured(true, "02:00", "05:00");
        long old = 10_000L;
        assertOnly(Status.GREEN, versionEval(outdated(), win, at("03:00"), old));
        assertOnly(Status.GREEN, versionEval(outdated(), win, at("05:00"), old));
        assertOnly(Status.YELLOW, versionEval(outdated(), win, at("05:01"), old), Condition.KIOSK_VERSION);
        assertOnly(Status.GREEN, versionEval(outdated(), win, at("02:00"), old));
        assertOnly(Status.YELLOW, versionEval(outdated(), win, at("01:59"), old), Condition.KIOSK_VERSION);
    }

    @Test
    public void windowAcrossMidnight() {
        ConfiguredKiosk win = configured(true, "22:00", "02:00");
        long old = 10_000L;
        assertOnly(Status.GREEN, versionEval(outdated(), win, at("23:30"), old));
        assertOnly(Status.GREEN, versionEval(outdated(), win, at("01:59"), old));
        assertOnly(Status.GREEN, versionEval(outdated(), win, at("02:00"), old));
        assertOnly(Status.YELLOW, versionEval(outdated(), win, at("02:01"), old), Condition.KIOSK_VERSION);
    }

    @Test
    public void rolloutAssignedMidMorningWaitsForTheNextFullWindow() {
        ConfiguredKiosk win = configured(true, "02:00", "05:00");
        long since = at("10:00");
        assertOnly(Status.GREEN, versionEval(outdated(), win, at("12:00"), since));
        assertOnly(Status.GREEN, versionEval(outdated(), win, at("23:00"), since));
        assertOnly(Status.GREEN, versionEval(outdated(), win, atDay(17, "03:00"), since));
        assertOnly(Status.GREEN, versionEval(outdated(), win, atDay(17, "05:00"), since));
        assertOnly(Status.YELLOW, versionEval(outdated(), win, atDay(17, "05:01"), since), Condition.KIOSK_VERSION);
        assertOnly(Status.YELLOW, versionEval(outdated(), win, atDay(18, "12:00"), since), Condition.KIOSK_VERSION);
    }

    @Test
    public void windowThatWasAlreadyRunningAtAssignmentDoesNotCount() {
        ConfiguredKiosk win = configured(true, "02:00", "05:00");
        long since = at("03:00");
        assertOnly(Status.GREEN, versionEval(outdated(), win, at("06:00"), since));
        assertOnly(Status.GREEN, versionEval(outdated(), win, atDay(17, "05:00"), since));
        assertOnly(Status.YELLOW, versionEval(outdated(), win, atDay(17, "05:01"), since), Condition.KIOSK_VERSION);
    }

    @Test
    public void rolloutAcrossMidnightWindow() {
        ConfiguredKiosk win = configured(true, "22:00", "02:00");
        long since = at("10:00");
        assertOnly(Status.GREEN, versionEval(outdated(), win, at("21:59"), since));
        assertOnly(Status.GREEN, versionEval(outdated(), win, atDay(17, "02:00"), since));
        assertOnly(Status.YELLOW, versionEval(outdated(), win, atDay(17, "02:01"), since), Condition.KIOSK_VERSION);
    }

    @Test
    public void equalFromAndToMeansAlwaysAllowed() {
        ConfiguredKiosk win = configured(true, "03:00", "03:00");
        assertOnly(Status.GREEN, versionEval(outdated(), win, NOW, 10_000L));
    }

    @Test
    public void windowOffKeepsTheTwoHourRule() {
        ConfiguredKiosk off = configured(false, "02:00", "05:00");
        assertOnly(Status.GREEN, versionEval(outdated(), off, at("12:00"), at("10:00")));
        assertOnly(Status.YELLOW, versionEval(outdated(), off, at("12:01"), at("10:00") - 1), Condition.KIOSK_VERSION);
    }

    @Test
    public void equalVersionsOrNoConfigurationClearSince() {
        ConfiguredKiosk k = configured(false, "02:00", "05:00");
        Evaluation equal = versionEval(new H(), k, NOW, 5L);
        assertOnly(Status.GREEN, equal);
        assertNull(equal.versionMismatchSince());

        Evaluation none = versionEval(outdated(), null, NOW, 5L);
        assertOnly(Status.GREEN, none);
        assertNull(none.versionMismatchSince());
    }

    @Test
    public void foreignPackageOrMissingVersionIsNotAMismatch() {
        ConfiguredKiosk k = configured(false, "02:00", "05:00");
        H other = outdated();
        other.pkg = "com.other";
        assertNull(versionEval(other, k, NOW, 5L).versionMismatchSince());
        H noVersion = outdated();
        noVersion.version = null;
        assertNull(versionEval(noVersion, k, NOW, 5L).versionMismatchSince());
    }

    @Test
    public void worstSeverityWins() {
        H h = new H();
        h.battery = 15;
        h.temp = 51f;
        Evaluation e = eval(h);
        assertEquals(Status.RED, e.status());
        assertEquals(List.of(Condition.BATTERY_LOW, Condition.TEMPERATURE), conditions(e));
    }

    @Test
    public void conditionCodes() {
        assertEquals("kiosk_not_running", Condition.KIOSK_NOT_RUNNING.code());
        assertEquals("kiosk_admin_off", Condition.KIOSK_ADMIN_OFF.code());
        assertEquals("offline", Condition.OFFLINE.code());
    }

    @Test
    public void fromLatestRowMapsEveryInput() {
        LatestRow r = new LatestRow();
        r.setReceivedAt(11L);
        r.setMeasuredAt(22L);
        r.setBatteryPct(33);
        r.setCharging(true);
        r.setTemperatureC(44.5f);
        r.setFreeStorageMb(55);
        r.setKioskRequired(true);
        r.setKioskRunning(false);
        r.setKioskAdminUnlocked(true);
        r.setKioskAppPackage("pkg.x");
        r.setKioskAppVersion("9.9");
        r.setKioskDownSince(66L);
        r.setKioskDownReports(77);
        DeviceHealth h = DeviceHealth.from(r);
        assertEquals(11L, h.receivedAt());
        assertEquals(22L, h.measuredAt());
        assertEquals(Integer.valueOf(33), h.batteryPct());
        assertEquals(Boolean.TRUE, h.charging());
        assertEquals(Float.valueOf(44.5f), h.temperatureC());
        assertEquals(Integer.valueOf(55), h.freeStorageMb());
        assertEquals(Boolean.TRUE, h.kioskRequired());
        assertEquals(Boolean.FALSE, h.kioskRunning());
        assertEquals(Boolean.TRUE, h.kioskAdminUnlocked());
        assertEquals("pkg.x", h.kioskAppPackage());
        assertEquals("9.9", h.kioskAppVersion());
        assertEquals(Long.valueOf(66L), h.kioskDownSince());
        assertEquals(77, h.kioskDownReports());
    }
}
