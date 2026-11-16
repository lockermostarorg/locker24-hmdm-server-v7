package com.hmdm.plugins.locker24health.alert;

import com.hmdm.plugins.locker24health.status.Condition;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class AlertText {

    public static final int MAX_LENGTH = 1024;

    private static final Set<Condition> VALUED = EnumSet.of(Condition.BATTERY_LOW, Condition.TEMPERATURE, Condition.STORAGE_LOW);

    private AlertText() {
    }

    public static String message(AlertEvent e) {
        String text = e.deviceId() == null
                ? (e.message() != null ? e.message() : "")
                : subject(e) + ": " + label(e);
        return bounded(text);
    }

    private static String subject(AlertEvent e) {
        String number = present(e.deviceNumber()) ? e.deviceNumber() : "device " + e.deviceId();
        List<String> parts = new ArrayList<>(2);
        if (present(e.configuration())) {
            parts.add(e.configuration());
        }
        if (present(e.lockerShortUid())) {
            parts.add("locker " + e.lockerShortUid());
        }
        return parts.isEmpty() ? number : number + " (" + String.join(", ", parts) + ")";
    }

    private static String label(AlertEvent e) {
        String code = e.condition() != null ? e.condition().code() : "alert";
        if (e.cleared()) {
            return "cleared " + code;
        }
        if (!VALUED.contains(e.condition())) {
            return code;
        }
        String value = present(e.detail()) ? e.detail() : code;
        return e.severity() == null ? value : value + " (" + e.severity().name().toLowerCase(Locale.ROOT) + ")";
    }

    private static boolean present(String s) {
        return s != null && !s.isBlank();
    }

    private static String bounded(String s) {
        if (s.length() <= MAX_LENGTH) {
            return s;
        }
        int end = MAX_LENGTH;
        if (Character.isHighSurrogate(s.charAt(end - 1))) {
            end--;
        }
        return s.substring(0, end);
    }
}
