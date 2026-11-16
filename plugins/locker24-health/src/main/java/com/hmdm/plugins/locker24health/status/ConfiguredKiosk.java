package com.hmdm.plugins.locker24health.status;

import java.time.LocalTime;

public record ConfiguredKiosk(String pkg, String version, boolean scheduleAppUpdate, LocalTime from, LocalTime to) {
}
