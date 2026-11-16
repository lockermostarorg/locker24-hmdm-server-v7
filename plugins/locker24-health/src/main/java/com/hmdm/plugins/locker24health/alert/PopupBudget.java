package com.hmdm.plugins.locker24health.alert;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

public final class PopupBudget {

    public static final long WINDOW_MS = 600_000L;

    private final int max;
    private final long windowMs;
    private final Map<Integer, Deque<Long>> spent = new HashMap<>();

    public PopupBudget(int max, long windowMs) {
        this.max = max;
        this.windowMs = windowMs;
    }

    public synchronized int available(int customerId, long now) {
        Deque<Long> times = spent.get(customerId);
        if (times == null) {
            return max;
        }
        while (!times.isEmpty() && times.peekFirst() <= now - windowMs) {
            times.pollFirst();
        }
        if (times.isEmpty()) {
            spent.remove(customerId);
            return max;
        }
        return Math.max(0, max - times.size());
    }

    public synchronized void spend(int customerId, long now, int count) {
        if (count <= 0) {
            return;
        }
        Deque<Long> times = spent.computeIfAbsent(customerId, k -> new ArrayDeque<>());
        for (int i = 0; i < count && times.size() < max; i++) {
            times.addLast(now);
        }
    }
}
