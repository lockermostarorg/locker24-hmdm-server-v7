package com.hmdm.plugins.locker24health.alert;

import com.hmdm.plugins.locker24health.status.Condition;
import com.hmdm.plugins.locker24health.status.Finding;
import com.hmdm.plugins.locker24health.status.Status;

import com.hmdm.plugins.locker24health.status.Severity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class AlertPolicy {

    public static final long YELLOW_ALERT_AFTER_MS = 1_800_000L;
    public static final long CLEAR_AFTER_ABSENT_MS = 300_000L;
    public static final int MAX_POPUPS_PER_CYCLE = 5;

    private AlertPolicy() {
    }

    public static AlertDecision decide(long now, Status status, Long nonGreenSince, List<Finding> findings,
                                       Map<Condition, OpenAlert> open, Long lastReceivedAt) {
        List<Finding> alertable = findings.stream().filter(f -> f.condition().alerts()).collect(Collectors.toList());
        Long newNonGreenSince = alertable.isEmpty() ? null : (nonGreenSince != null ? nonGreenSince : Long.valueOf(now));

        boolean yellowDue = newNonGreenSince != null && now - newNonGreenSince >= YELLOW_ALERT_AFTER_MS;
        Set<Condition> present = EnumSet.noneOf(Condition.class);
        List<Finding> raise = new ArrayList<>();
        List<Finding> escalate = new ArrayList<>();
        List<OpenAlert> touch = new ArrayList<>();
        for (Finding f : alertable) {
            if (!present.add(f.condition())) {
                continue;
            }
            OpenAlert existing = open.get(f.condition());
            if (existing != null) {
                if (existing.severity() == Severity.YELLOW && f.severity() == Severity.RED) {
                    escalate.add(f);
                } else {
                    touch.add(existing);
                }
            } else if (f.severity() == Severity.RED || yellowDue) {
                raise.add(f);
            }
        }

        boolean offline = present.contains(Condition.OFFLINE);
        List<OpenAlert> clear = new ArrayList<>();
        for (Map.Entry<Condition, OpenAlert> e : open.entrySet()) {
            if (present.contains(e.getKey())) {
                continue;
            }
            if (offline) {
                touch.add(e.getValue());
                continue;
            }
            if (e.getKey() == Condition.OFFLINE
                    && (lastReceivedAt == null || lastReceivedAt <= e.getValue().lastPresentAt())) {
                continue;
            }
            if (now - e.getValue().lastPresentAt() >= CLEAR_AFTER_ABSENT_MS) {
                clear.add(e.getValue());
            }
        }
        return new AlertDecision(raise, escalate, touch, clear, newNonGreenSince);
    }

    private static final Comparator<AlertEvent> RED_FIRST_THEN_DEVICE = Comparator
            .comparing((AlertEvent e) -> e.severity() == Severity.RED ? 0 : 1)
            .thenComparing(AlertEvent::deviceNumber, Comparator.nullsLast(Comparator.naturalOrder()));

    public static List<AlertEvent> limitPopups(List<AlertEvent> events, int max) {
        List<AlertEvent> raised = events.stream().filter(e -> !e.cleared()).sorted(RED_FIRST_THEN_DEVICE)
                .collect(Collectors.toList());
        List<AlertEvent> out = new ArrayList<>(raised.subList(0, Math.min(max, raised.size())));
        if (raised.size() > max) {
            List<AlertEvent> rest = raised.subList(max, raised.size());
            Map<Condition, Integer> counts = new LinkedHashMap<>();
            for (AlertEvent e : rest) {
                counts.merge(e.condition(), 1, Integer::sum);
            }
            String breakdown = counts.entrySet().stream()
                    .map(e -> e.getKey().code() + " " + e.getValue())
                    .collect(Collectors.joining(", "));
            long tablets = rest.stream().map(AlertEvent::deviceId).distinct().count();
            String text = tablets + (tablets == 1 ? " more tablet needs" : " more tablets need")
                    + " attention: " + breakdown;
            boolean anyRed = rest.stream().anyMatch(e -> e.severity() == Severity.RED);
            out.add(new AlertEvent(rest.get(0).customerId(), null, null, null, null, null,
                    anyRed ? Severity.RED : Severity.YELLOW, false, null,
                    rest.stream().mapToLong(AlertEvent::at).max().getAsLong(), text));
        }
        events.stream().filter(AlertEvent::cleared).forEach(out::add);
        return out;
    }
}
