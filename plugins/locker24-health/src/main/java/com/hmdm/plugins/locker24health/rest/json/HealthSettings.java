package com.hmdm.plugins.locker24health.rest.json;

public class HealthSettings {

    private String webhookUrl;
    private String timeZone;
    private Boolean webhookUrlSet;
    private Boolean editable;

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }

    public Boolean getWebhookUrlSet() {
        return webhookUrlSet;
    }

    public void setWebhookUrlSet(Boolean webhookUrlSet) {
        this.webhookUrlSet = webhookUrlSet;
    }

    public Boolean getEditable() {
        return editable;
    }

    public void setEditable(Boolean editable) {
        this.editable = editable;
    }
}
