package com.hmdm.plugins.locker24health.status;

import java.time.LocalTime;
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
            findings.add(new Finding(Condition.STORAGE_LOW, Severity.YELLOW, h.freeStorageMb() + " MB free"));
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
        out.add(new Finding(Condition.BATTERY_LOW, b < 10 ? Severity.RED : Severity.YELLOW, "battery " + b + "%"));
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
        if (c.now() - since > VERSION_GRACE_MS && !inWindow(k, c)) {
            out.add(new Finding(Condition.KIOSK_VERSION, Severity.YELLOW,
                    "installed " + h.kioskAppVersion() + ", configured " + k.version()));
        }
        return since;
    }

    private static boolean inWindow(ConfiguredKiosk k, EvalContext c) {
        if (!k.scheduleAppUpdate() || k.from() == null || k.to() == null) {
            return false;
        }
        LocalTime t = java.time.Instant.ofEpochMilli(c.now()).atZone(c.zone()).toLocalTime();
        if (k.from().equals(k.to())) {
            return false;
        }
        if (k.from().isBefore(k.to())) {
            return !t.isBefore(k.from()) && t.isBefore(k.to());
        }
        return !t.isBefore(k.from()) || t.isBefore(k.to());
    }
}
