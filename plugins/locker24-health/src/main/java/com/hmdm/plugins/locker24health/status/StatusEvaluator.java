package com.hmdm.plugins.locker24health.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public final class StatusEvaluator {

    public static final long OFFLINE_AFTER_MS = 600_000L;
    public static final long VERSION_GRACE_MS = 7_200_000L;
    public static final int LOW_STORAGE_MB = 1024;
    public static final int KIOSK_DOWN_MIN_REPORTS = 2;
    public static final long KIOSK_DOWN_MIN_MS = 120_000L;

    private StatusEvaluator() {
    }

    public static Evaluation evaluate(DeviceHealth h, EvalContext c) {
        long lastSeen = Math.max(h.receivedAt(), c.serverStartedAt());
        if (c.now() - lastSeen > OFFLINE_AFTER_MS) {
            List<Finding> only = List.of(new Finding(Condition.OFFLINE, Severity.RED,
                    "no report for " + (c.now() - lastSeen) / 60_000 + " min"));
            return new Evaluation(Status.RED, only, c.versionMismatchSince());
        }

        List<Finding> findings = new ArrayList<>();
        kiosk(h, findings);
        battery(h, findings);
        if (Boolean.FALSE.equals(h.charging())) {
            findings.add(new Finding(Condition.NOT_CHARGING, Severity.YELLOW, "not charging"));
        }
        temperature(h, findings);
        if (h.freeStorageMb() != null && h.freeStorageMb() < LOW_STORAGE_MB) {
            findings.add(new Finding(Condition.STORAGE_LOW, Severity.YELLOW, "storage " + h.freeStorageMb() + " MB free"));
        }
        Long since = version(h, c, findings);

        Status status = Status.GREEN;
        for (Finding f : findings) {
            if (f.severity() == Severity.RED) {
                status = Status.RED;
            } else if (status == Status.GREEN) {
                status = Status.YELLOW;
            }
        }
        return new Evaluation(status, findings, since);
    }

    private static void kiosk(DeviceHealth h, List<Finding> out) {
        if (!Boolean.TRUE.equals(h.kioskRequired()) || !Boolean.FALSE.equals(h.kioskRunning())) {
            return;
        }
        if (Boolean.TRUE.equals(h.kioskAdminUnlocked())) {
            out.add(new Finding(Condition.KIOSK_ADMIN_OFF, Severity.YELLOW, "kiosk off (admin)"));
            return;
        }
        boolean consecutive = h.kioskDownReports() >= KIOSK_DOWN_MIN_REPORTS;
        boolean lasting = h.kioskDownSince() != null && h.measuredAt() - h.kioskDownSince() >= KIOSK_DOWN_MIN_MS;
        if (consecutive || lasting) {
            out.add(new Finding(Condition.KIOSK_NOT_RUNNING, Severity.RED, "kiosk app not running"));
        }
    }

    private static void battery(DeviceHealth h, List<Finding> out) {
        Integer b = h.batteryPct();
        if (b == null || b >= 20) {
            return;
        }
        out.add(new Finding(Condition.BATTERY_LOW, b < 10 ? Severity.RED : Severity.YELLOW, "battery " + b + " %"));
    }

    private static void temperature(DeviceHealth h, List<Finding> out) {
        Float t = h.temperatureC();
        if (t == null || t <= 45.0f) {
            return;
        }
        out.add(new Finding(Condition.TEMPERATURE, t > 50.0f ? Severity.RED : Severity.YELLOW, "temperature " + t + " C"));
    }

    private static Long version(DeviceHealth h, EvalContext c, List<Finding> out) {
        ConfiguredKiosk k = c.configured();
        boolean mismatch = k != null
                && k.pkg() != null && k.pkg().equals(h.kioskAppPackage())
                && k.version() != null && h.kioskAppVersion() != null
                && !k.version().equals(h.kioskAppVersion());
        if (!mismatch) {
            return null;
        }
        long since = c.versionMismatchSince() != null ? c.versionMismatchSince() : c.now();
        if (overdue(k, c, since)) {
            out.add(new Finding(Condition.KIOSK_VERSION, Severity.YELLOW,
                    "installed " + h.kioskAppVersion() + ", configured " + k.version()));
        }
        return since;
    }

    private static boolean windowConfigured(ConfiguredKiosk k) {
        return k.scheduleAppUpdate() && k.from() != null && k.to() != null;
    }

    private static boolean overdue(ConfiguredKiosk k, EvalContext c, long since) {
        if (!windowConfigured(k) || k.from().equals(k.to())) {
            return c.now() - since > VERSION_GRACE_MS;
        }
        if (inWindow(k, c.now(), c.zone())) {
            return false;
        }
        return c.now() >= firstWindowEnd(k, since, c.zone());
    }

    private static boolean inWindow(ConfiguredKiosk k, long now, ZoneId zone) {
        LocalTime t = Instant.ofEpochMilli(now).atZone(zone).toLocalTime().truncatedTo(ChronoUnit.MINUTES);
        LocalTime from = k.from().truncatedTo(ChronoUnit.MINUTES);
        LocalTime to = k.to().truncatedTo(ChronoUnit.MINUTES);
        if (!from.isAfter(to)) {
            return !t.isBefore(from) && !t.isAfter(to);
        }
        return !t.isBefore(from) || !t.isAfter(to);
    }

    private static long firstWindowEnd(ConfiguredKiosk k, long since, ZoneId zone) {
        LocalTime from = k.from().truncatedTo(ChronoUnit.MINUTES);
        LocalTime to = k.to().truncatedTo(ChronoUnit.MINUTES);
        LocalDate day = Instant.ofEpochMilli(since).atZone(zone).toLocalDate();
        while (true) {
            ZonedDateTime start = ZonedDateTime.of(day, from, zone);
            boolean shownOnClock = start.toLocalTime().equals(from);
            if (shownOnClock && start.toInstant().toEpochMilli() >= since) {
                break;
            }
            day = day.plusDays(1);
        }
        LocalDate endDay = from.isAfter(to) ? day.plusDays(1) : day;
        return ZonedDateTime.of(endDay, to, zone).plusMinutes(1).toInstant().toEpochMilli();
    }
}
