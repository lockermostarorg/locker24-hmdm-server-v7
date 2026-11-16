package com.hmdm.plugins.locker24health.status;

import java.time.ZoneId;

public record EvalContext(long now, long serverStartedAt, ConfiguredKiosk configured, ZoneId zone, Long versionMismatchSince) {
}
