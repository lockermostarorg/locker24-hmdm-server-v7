package com.hmdm.plugins.locker24health.persistence.domain;

public class EvalRow extends LatestRow {

    private String deviceNumber;
    private Integer configurationId;
    private String configurationName;
    private String status;
    private Long nonGreenSince;
    private Long versionMismatchSince;

    public String getDeviceNumber() {
        return deviceNumber;
    }

    public void setDeviceNumber(String deviceNumber) {
        this.deviceNumber = deviceNumber;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getNonGreenSince() {
        return nonGreenSince;
    }

    public void setNonGreenSince(Long nonGreenSince) {
        this.nonGreenSince = nonGreenSince;
    }

    public Long getVersionMismatchSince() {
        return versionMismatchSince;
    }

    public void setVersionMismatchSince(Long versionMismatchSince) {
        this.versionMismatchSince = versionMismatchSince;
    }
}
