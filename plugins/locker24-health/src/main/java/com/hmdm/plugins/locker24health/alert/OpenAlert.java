package com.hmdm.plugins.locker24health.alert;

import com.hmdm.plugins.locker24health.status.Condition;
import com.hmdm.plugins.locker24health.status.Severity;

public record OpenAlert(long id, Condition condition, Severity severity, long raisedAt, long lastPresentAt) {
}
