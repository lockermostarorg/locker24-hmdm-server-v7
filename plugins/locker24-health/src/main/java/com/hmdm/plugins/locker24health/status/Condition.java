package com.hmdm.plugins.locker24health.status;

public enum Condition {
    OFFLINE("offline"),
    KIOSK_NOT_RUNNING("kiosk_not_running"),
    KIOSK_ADMIN_OFF("kiosk_admin_off"),
    BATTERY_LOW("battery_low"),
    NOT_CHARGING("not_charging"),
    TEMPERATURE("temperature"),
    STORAGE_LOW("storage_low"),
    KIOSK_VERSION("kiosk_version");

    private final String code;

    Condition(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public boolean alerts() {
        return this != KIOSK_ADMIN_OFF;
    }
}
