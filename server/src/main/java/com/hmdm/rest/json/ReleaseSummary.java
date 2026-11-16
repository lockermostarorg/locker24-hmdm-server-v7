package com.hmdm.rest.json;

public class ReleaseSummary {

    private final int configurations;
    private final int devices;
    private final String mode;
    private final int pushFailed;
    private final boolean notLatest;

    public ReleaseSummary(int configurations, int devices, String mode, int pushFailed, boolean notLatest) {
        this.configurations = configurations;
        this.devices = devices;
        this.mode = mode;
        this.pushFailed = pushFailed;
        this.notLatest = notLatest;
    }

    public int getConfigurations() {
        return configurations;
    }

    public int getDevices() {
        return devices;
    }

    public String getMode() {
        return mode;
    }

    public int getPushFailed() {
        return pushFailed;
    }

    public boolean isNotLatest() {
        return notLatest;
    }
}
