package com.hmdm.plugins.locker24health.model;

import java.util.List;

public class ParsedBatch {

    private final long sentAt;
    private final List<ParsedReport> reports;

    public ParsedBatch(long sentAt, List<ParsedReport> reports) {
        this.sentAt = sentAt;
        this.reports = reports;
    }

    public long getSentAt() {
        return sentAt;
    }

    public List<ParsedReport> getReports() {
        return reports;
    }
}
