package com.hmdm.plugins.locker24health.model;

public class ParsedReport {

    private long ts;
    private String trigger;
    private Integer batteryPct;
    private Boolean charging;
    private String chargeSource;
    private Float temperatureC;
    private String batteryHealth;
    private String manufacturer;
    private String model;
    private String androidBuild;
    private String androidVersion;
    private Integer sdkInt;
    private String securityPatch;
    private Integer freeStorageMb;
    private Integer freeRamMb;
    private Long uptimeS;
    private String wifiSsid;
    private Integer rssiDbm;
    private String ip;
    private Boolean mqttConnected;
    private Long lastConfigSyncAt;
    private Boolean kioskRequired;
    private Boolean kioskRunning;
    private Boolean kioskAdminUnlocked;
    private String foregroundPackage;
    private String kioskAppPackage;
    private String kioskAppVersion;
    private Integer kioskAppVersionCode;
    private String agentVersion;
    private Integer agentVersionCode;
    private Integer watchdogRestarts24h;
    private Long appPublishedAt;
    private String lockerShortUid;
    private Long lastApiOkAt;
    private Long lastApiErrorAt;
    private String lastApiError;
    private Integer appErrors24h;
    private String json;

    public long getTs() {
        return ts;
    }

    public void setTs(long ts) {
        this.ts = ts;
    }

    public String getTrigger() {
        return trigger;
    }

    public void setTrigger(String trigger) {
        this.trigger = trigger;
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

    public String getChargeSource() {
        return chargeSource;
    }

    public void setChargeSource(String chargeSource) {
        this.chargeSource = chargeSource;
    }

    public Float getTemperatureC() {
        return temperatureC;
    }

    public void setTemperatureC(Float temperatureC) {
        this.temperatureC = temperatureC;
    }

    public String getBatteryHealth() {
        return batteryHealth;
    }

    public void setBatteryHealth(String batteryHealth) {
        this.batteryHealth = batteryHealth;
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

    public String getAndroidBuild() {
        return androidBuild;
    }

    public void setAndroidBuild(String androidBuild) {
        this.androidBuild = androidBuild;
    }

    public String getAndroidVersion() {
        return androidVersion;
    }

    public void setAndroidVersion(String androidVersion) {
        this.androidVersion = androidVersion;
    }

    public Integer getSdkInt() {
        return sdkInt;
    }

    public void setSdkInt(Integer sdkInt) {
        this.sdkInt = sdkInt;
    }

    public String getSecurityPatch() {
        return securityPatch;
    }

    public void setSecurityPatch(String securityPatch) {
        this.securityPatch = securityPatch;
    }

    public Integer getFreeStorageMb() {
        return freeStorageMb;
    }

    public void setFreeStorageMb(Integer freeStorageMb) {
        this.freeStorageMb = freeStorageMb;
    }

    public Integer getFreeRamMb() {
        return freeRamMb;
    }

    public void setFreeRamMb(Integer freeRamMb) {
        this.freeRamMb = freeRamMb;
    }

    public Long getUptimeS() {
        return uptimeS;
    }

    public void setUptimeS(Long uptimeS) {
        this.uptimeS = uptimeS;
    }

    public String getWifiSsid() {
        return wifiSsid;
    }

    public void setWifiSsid(String wifiSsid) {
        this.wifiSsid = wifiSsid;
    }

    public Integer getRssiDbm() {
        return rssiDbm;
    }

    public void setRssiDbm(Integer rssiDbm) {
        this.rssiDbm = rssiDbm;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public Boolean getMqttConnected() {
        return mqttConnected;
    }

    public void setMqttConnected(Boolean mqttConnected) {
        this.mqttConnected = mqttConnected;
    }

    public Long getLastConfigSyncAt() {
        return lastConfigSyncAt;
    }

    public void setLastConfigSyncAt(Long lastConfigSyncAt) {
        this.lastConfigSyncAt = lastConfigSyncAt;
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

    public String getForegroundPackage() {
        return foregroundPackage;
    }

    public void setForegroundPackage(String foregroundPackage) {
        this.foregroundPackage = foregroundPackage;
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

    public Integer getKioskAppVersionCode() {
        return kioskAppVersionCode;
    }

    public void setKioskAppVersionCode(Integer kioskAppVersionCode) {
        this.kioskAppVersionCode = kioskAppVersionCode;
    }

    public String getAgentVersion() {
        return agentVersion;
    }

    public void setAgentVersion(String agentVersion) {
        this.agentVersion = agentVersion;
    }

    public Integer getAgentVersionCode() {
        return agentVersionCode;
    }

    public void setAgentVersionCode(Integer agentVersionCode) {
        this.agentVersionCode = agentVersionCode;
    }

    public Integer getWatchdogRestarts24h() {
        return watchdogRestarts24h;
    }

    public void setWatchdogRestarts24h(Integer watchdogRestarts24h) {
        this.watchdogRestarts24h = watchdogRestarts24h;
    }

    public Long getAppPublishedAt() {
        return appPublishedAt;
    }

    public void setAppPublishedAt(Long appPublishedAt) {
        this.appPublishedAt = appPublishedAt;
    }

    public String getLockerShortUid() {
        return lockerShortUid;
    }

    public void setLockerShortUid(String lockerShortUid) {
        this.lockerShortUid = lockerShortUid;
    }

    public Long getLastApiOkAt() {
        return lastApiOkAt;
    }

    public void setLastApiOkAt(Long lastApiOkAt) {
        this.lastApiOkAt = lastApiOkAt;
    }

    public Long getLastApiErrorAt() {
        return lastApiErrorAt;
    }

    public void setLastApiErrorAt(Long lastApiErrorAt) {
        this.lastApiErrorAt = lastApiErrorAt;
    }

    public String getLastApiError() {
        return lastApiError;
    }

    public void setLastApiError(String lastApiError) {
        this.lastApiError = lastApiError;
    }

    public Integer getAppErrors24h() {
        return appErrors24h;
    }

    public void setAppErrors24h(Integer appErrors24h) {
        this.appErrors24h = appErrors24h;
    }

    public String getJson() {
        return json;
    }

    public void setJson(String json) {
        this.json = json;
    }
}
