package com.hmdm.plugins.locker24health.alert;

import com.hmdm.plugins.locker24health.status.Condition;
import com.hmdm.plugins.locker24health.status.Severity;

public record AlertEvent(Integer customerId, Integer deviceId, String deviceNumber, String configuration,
                         String lockerShortUid, Condition condition, Severity severity, boolean cleared,
                         String detail, long at, String message) {
}
