package com.hmdm.plugins.locker24health.persistence.domain;

public class HistoryRow {

    private Integer deviceId;
    private long deviceTs;
    private long receivedAt;
    private long measuredAt;
    private Integer batteryPct;
    private Boolean charging;
    private Float temperatureC;
    private Integer rssiDbm;
    private Boolean kioskRunning;
    private Boolean kioskAdminUnlocked;
    private boolean kioskDown;

    public Integer getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(Integer deviceId) {
        this.deviceId = deviceId;
    }

    public long getDeviceTs() {
        return deviceTs;
    }

    public void setDeviceTs(long deviceTs) {
        this.deviceTs = deviceTs;
    }

    public long getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(long receivedAt) {
        this.receivedAt = receivedAt;
    }

    public long getMeasuredAt() {
        return measuredAt;
    }

    public void setMeasuredAt(long measuredAt) {
        this.measuredAt = measuredAt;
    }

    public Integer getBatteryPct() {
        return batteryPct;
    }

    public void setBatteryPct(Integer batteryPct) {
        this.batteryPct = batteryPct;
    }

    public Boolean getCharging() {
        return charging;
    }

    public void setCharging(Boolean charging) {
        this.charging = charging;
    }

    public Float getTemperatureC() {
        return temperatureC;
    }

    public void setTemperatureC(Float temperatureC) {
        this.temperatureC = temperatureC;
    }

    public Integer getRssiDbm() {
        return rssiDbm;
    }

    public void setRssiDbm(Integer rssiDbm) {
        this.rssiDbm = rssiDbm;
    }

    public Boolean getKioskRunning() {
        return kioskRunning;
    }

    public void setKioskRunning(Boolean kioskRunning) {
        this.kioskRunning = kioskRunning;
    }

    public Boolean getKioskAdminUnlocked() {
        return kioskAdminUnlocked;
    }

    public void setKioskAdminUnlocked(Boolean kioskAdminUnlocked) {
        this.kioskAdminUnlocked = kioskAdminUnlocked;
    }

    public boolean isKioskDown() {
        return kioskDown;
    }

    public void setKioskDown(boolean kioskDown) {
        this.kioskDown = kioskDown;
    }
}
