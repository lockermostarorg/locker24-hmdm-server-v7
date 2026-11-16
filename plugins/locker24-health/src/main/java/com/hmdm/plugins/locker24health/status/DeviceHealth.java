package com.hmdm.plugins.locker24health.status;

import com.hmdm.plugins.locker24health.persistence.domain.LatestRow;

public record DeviceHealth(
        long receivedAt,
        long measuredAt,
        Integer batteryPct,
        Boolean charging,
        Float temperatureC,
        Integer freeStorageMb,
        Boolean kioskRequired,
        Boolean kioskRunning,
        Boolean kioskAdminUnlocked,
        String kioskAppPackage,
        String kioskAppVersion,
        Long kioskDownSince,
        int kioskDownReports) {

    public static DeviceHealth from(LatestRow r) {
        return new DeviceHealth(
                r.getReceivedAt(), r.getMeasuredAt(),
                r.getBatteryPct(), r.getCharging(), r.getTemperatureC(), r.getFreeStorageMb(),
                r.getKioskRequired(), r.getKioskRunning(), r.getKioskAdminUnlocked(),
                r.getKioskAppPackage(), r.getKioskAppVersion(),
                r.getKioskDownSince(), r.getKioskDownReports());
    }
}
