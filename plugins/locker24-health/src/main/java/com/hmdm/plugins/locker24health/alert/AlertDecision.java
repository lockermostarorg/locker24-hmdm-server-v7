package com.hmdm.plugins.locker24health.alert;

import com.hmdm.plugins.locker24health.status.Finding;

import java.util.List;

public record AlertDecision(List<Finding> raise, List<Finding> escalate, List<OpenAlert> touch, List<OpenAlert> clear, Long nonGreenSince) {
}
