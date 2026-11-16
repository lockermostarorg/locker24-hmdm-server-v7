package com.hmdm.plugins.locker24health.persistence.domain;

public class AlertRow {

    private long id;
    private int deviceId;
    private int customerId;
    private String condition;
    private String severity;
    private long raisedAt;
    private long lastPresentAt;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public int getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(int deviceId) {
        this.deviceId = deviceId;
    }

    public int getCustomerId() {
        return customerId;
    }

    public void setCustomerId(int customerId) {
        this.customerId = customerId;
    }

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public long getRaisedAt() {
        return raisedAt;
    }

    public void setRaisedAt(long raisedAt) {
        this.raisedAt = raisedAt;
    }

    public long getLastPresentAt() {
        return lastPresentAt;
    }

    public void setLastPresentAt(long lastPresentAt) {
        this.lastPresentAt = lastPresentAt;
    }
}
