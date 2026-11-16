package com.hmdm.rest.json;

public class ReleaseSummary {

    private final int configurations;
    private final int devices;
    private final String mode;

    public ReleaseSummary(int configurations, int devices, String mode) {
        this.configurations = configurations;
        this.devices = devices;
        this.mode = mode;
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
}
