package com.hmdm.plugins.locker24health.rest.json;

import java.util.ArrayList;
import java.util.List;

public class FleetResponse {

    private List<FleetRow> devices = new ArrayList<>();
    private boolean truncated;

    public List<FleetRow> getDevices() {
        return devices;
    }

    public void setDevices(List<FleetRow> devices) {
        this.devices = devices;
    }

    public boolean isTruncated() {
        return truncated;
    }

    public void setTruncated(boolean truncated) {
        this.truncated = truncated;
    }
}
