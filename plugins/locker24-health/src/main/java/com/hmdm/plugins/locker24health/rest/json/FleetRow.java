package com.hmdm.plugins.locker24health.rest.json;

import java.util.List;

public class FleetRow {

    private int deviceId;
    private String number;
    private String description;
    private Integer configurationId;
    private String configurationName;
    private String groups;
    private String lockerShortUid;
    private Long lastSeen;
    private String status;
    private List<String> reasons;
    private Integer batteryPct;
    private Boolean charging;
    private Float temperatureC;
    private Integer freeStorageMb;
    private String kioskAppVersion;
    private String agentVersion;
    private Boolean kioskRequired;
    private Boolean kioskRunning;
    private Boolean kioskAdminUnlocked;
    private String model;

    public int getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(int deviceId) {
        this.deviceId = deviceId;
    }

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getConfigurationId() {
        return configurationId;
    }

    public void setConfigurationId(Integer configurationId) {
        this.configurationId = configurationId;
    }

    public String getConfigurationName() {
        return configurationName;
    }

    public void setConfigurationName(String configurationName) {
        this.configurationName = configurationName;
    }

    public String getGroups() {
        return groups;
    }

    public void setGroups(String groups) {
        this.groups = groups;
    }

    public String getLockerShortUid() {
        return lockerShortUid;
    }

    public void setLockerShortUid(String lockerShortUid) {
        this.lockerShortUid = lockerShortUid;
    }

    public Long getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(Long lastSeen) {
        this.lastSeen = lastSeen;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<String> getReasons() {
        return reasons;
    }

    public void setReasons(List<String> reasons) {
        this.reasons = reasons;
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

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }
}
