package com.hmdm.plugins.locker24health.status;

import java.util.List;

public record Evaluation(Status status, List<Finding> findings, Long versionMismatchSince) {
}
