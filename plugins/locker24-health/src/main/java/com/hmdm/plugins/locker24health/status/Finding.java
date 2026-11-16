package com.hmdm.plugins.locker24health.status;

public record Finding(Condition condition, Severity severity, String detail) {
}
