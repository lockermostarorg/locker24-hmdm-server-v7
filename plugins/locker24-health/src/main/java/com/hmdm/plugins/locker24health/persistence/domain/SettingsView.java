package com.hmdm.plugins.locker24health.persistence.domain;

public class SettingsView {

    private boolean webhookUrlSet;
    private String timeZone;

    public boolean isWebhookUrlSet() {
        return webhookUrlSet;
    }

    public void setWebhookUrlSet(boolean webhookUrlSet) {
        this.webhookUrlSet = webhookUrlSet;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }
}
