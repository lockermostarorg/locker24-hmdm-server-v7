package com.hmdm.plugins.locker24health.persistence.mapper;

import com.hmdm.plugins.locker24health.persistence.domain.HistoryRow;
import com.hmdm.plugins.locker24health.persistence.domain.LatestRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface HealthMapper {

    @Insert("INSERT INTO locker24_device_health_history (" +
            "device_id, device_ts, received_at, measured_at, " +
            "battery_pct, charging, temperature_c, rssi_dbm, kiosk_running, kiosk_admin_unlocked" +
            ") VALUES (" +
            "#{deviceId}, #{deviceTs}, #{receivedAt}, #{measuredAt}, " +
            "#{batteryPct}, #{charging}, #{temperatureC}, #{rssiDbm}, #{kioskRunning}, #{kioskAdminUnlocked}" +
            ") ON CONFLICT (device_id, device_ts) DO NOTHING")
    int insertHistory(HistoryRow row);

    @Insert("INSERT INTO locker24_device_health_latest (" +
            "device_id, customer_id, received_at, measured_at, " +
            "battery_pct, charging, temperature_c, free_storage_mb, rssi_dbm, " +
            "kiosk_required, kiosk_running, kiosk_admin_unlocked, " +
            "kiosk_app_package, kiosk_app_version, agent_version, locker_short_uid, " +
            "manufacturer, model, report_json" +
            ") VALUES (" +
            "#{deviceId}, #{customerId}, #{receivedAt}, #{measuredAt}, " +
            "#{batteryPct}, #{charging}, #{temperatureC}, #{freeStorageMb}, #{rssiDbm}, " +
            "#{kioskRequired}, #{kioskRunning}, #{kioskAdminUnlocked}, " +
            "#{kioskAppPackage}, #{kioskAppVersion}, #{agentVersion}, #{lockerShortUid}, " +
            "#{manufacturer}, #{model}, #{reportJson}" +
            ") ON CONFLICT (device_id) DO UPDATE SET " +
            "customer_id = EXCLUDED.customer_id, " +
            "measured_at = EXCLUDED.measured_at, " +
            "battery_pct = EXCLUDED.battery_pct, " +
            "charging = EXCLUDED.charging, " +
            "temperature_c = EXCLUDED.temperature_c, " +
            "free_storage_mb = EXCLUDED.free_storage_mb, " +
            "rssi_dbm = EXCLUDED.rssi_dbm, " +
            "kiosk_required = EXCLUDED.kiosk_required, " +
            "kiosk_running = EXCLUDED.kiosk_running, " +
            "kiosk_admin_unlocked = EXCLUDED.kiosk_admin_unlocked, " +
            "kiosk_app_package = EXCLUDED.kiosk_app_package, " +
            "kiosk_app_version = EXCLUDED.kiosk_app_version, " +
            "agent_version = EXCLUDED.agent_version, " +
            "locker_short_uid = EXCLUDED.locker_short_uid, " +
            "manufacturer = EXCLUDED.manufacturer, " +
            "model = EXCLUDED.model, " +
            "report_json = EXCLUDED.report_json " +
            "WHERE locker24_device_health_latest.measured_at <= EXCLUDED.measured_at")
    int upsertLatest(LatestRow row);

    @Update("UPDATE locker24_device_health_latest " +
            "SET received_at = GREATEST(received_at, #{receivedAt}) " +
            "WHERE device_id = #{deviceId}")
    int touchReceived(@Param("deviceId") int deviceId, @Param("receivedAt") long receivedAt);
}
