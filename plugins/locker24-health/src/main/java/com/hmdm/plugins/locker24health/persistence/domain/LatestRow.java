package com.hmdm.plugins.locker24health.persistence.domain;

public class LatestRow {

    private Integer deviceId;
    private Integer customerId;
    private long receivedAt;
    private long measuredAt;
    private Integer batteryPct;
    private Boolean charging;
    private Float temperatureC;
    private Integer freeStorageMb;
    private Integer rssiDbm;
    private Boolean kioskRequired;
    private Boolean kioskRunning;
    private Boolean kioskAdminUnlocked;
    private String kioskAppPackage;
    private String kioskAppVersion;
    private String agentVersion;
    private String lockerShortUid;
    private String manufacturer;
    private String model;
    private String reportJson;

    public Integer getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(Integer deviceId) {
        this.deviceId = deviceId;
    }

    public Integer getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Integer customerId) {
        this.customerId = customerId;
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

    public Integer getFreeStorageMb() {
        return freeStorageMb;
    }

    public void setFreeStorageMb(Integer freeStorageMb) {
        this.freeStorageMb = freeStorageMb;
    }

    public Integer getRssiDbm() {
        return rssiDbm;
    }

    public void setRssiDbm(Integer rssiDbm) {
        this.rssiDbm = rssiDbm;
    }

    public Boolean getKioskRequired() {
        return kioskRequired;
    }

    public void setKioskRequired(Boolean kioskRequired) {
        this.kioskRequired = kioskRequired;
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

    public String getKioskAppPackage() {
        return kioskAppPackage;
    }

    public void setKioskAppPackage(String kioskAppPackage) {
        this.kioskAppPackage = kioskAppPackage;
    }

    public String getKioskAppVersion() {
        return kioskAppVersion;
    }

    public void setKioskAppVersion(String kioskAppVersion) {
        this.kioskAppVersion = kioskAppVersion;
    }

    public String getAgentVersion() {
        return agentVersion;
    }

    public void setAgentVersion(String agentVersion) {
        this.agentVersion = agentVersion;
    }

    public String getLockerShortUid() {
        return lockerShortUid;
    }

    public void setLockerShortUid(String lockerShortUid) {
        this.lockerShortUid = lockerShortUid;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getReportJson() {
        return reportJson;
    }

    public void setReportJson(String reportJson) {
        this.reportJson = reportJson;
    }
}
