package com.hmdm.plugins.locker24health.persistence.mapper;

import com.hmdm.plugins.locker24health.persistence.domain.AlertRow;
import com.hmdm.plugins.locker24health.persistence.domain.EvalRow;
import com.hmdm.plugins.locker24health.persistence.domain.HistoryRow;
import com.hmdm.plugins.locker24health.persistence.domain.LatestRow;
import com.hmdm.plugins.locker24health.persistence.domain.SettingsRow;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

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
            "manufacturer, model, report_json, kiosk_down_since, kiosk_down_reports" +
            ") VALUES (" +
            "#{deviceId}, #{customerId}, #{receivedAt}, #{measuredAt}, " +
            "#{batteryPct}, #{charging}, #{temperatureC}, #{freeStorageMb}, #{rssiDbm}, " +
            "#{kioskRequired}, #{kioskRunning}, #{kioskAdminUnlocked}, " +
            "#{kioskAppPackage}, #{kioskAppVersion}, #{agentVersion}, #{lockerShortUid}, " +
            "#{manufacturer}, #{model}, #{reportJson}, " +
            "CASE WHEN #{freshReports} > 0 AND #{kioskDownRun} > 0 THEN #{kioskDownRunStart,jdbcType=BIGINT} END, " +
            "CASE WHEN #{freshReports} > 0 THEN #{kioskDownRun} ELSE 0 END" +
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
            "report_json = EXCLUDED.report_json, " +
            "kiosk_down_since = CASE " +
            "WHEN #{freshReports} = 0 THEN locker24_device_health_latest.kiosk_down_since " +
            "WHEN #{kioskDownRun} = 0 THEN NULL " +
            "WHEN #{kioskDownAll} THEN COALESCE(locker24_device_health_latest.kiosk_down_since, #{kioskDownRunStart,jdbcType=BIGINT}) " +
            "ELSE #{kioskDownRunStart,jdbcType=BIGINT} END, " +
            "kiosk_down_reports = CASE " +
            "WHEN #{freshReports} = 0 THEN locker24_device_health_latest.kiosk_down_reports " +
            "WHEN #{kioskDownRun} = 0 THEN 0 " +
            "WHEN #{kioskDownAll} THEN locker24_device_health_latest.kiosk_down_reports + #{kioskDownRun} " +
            "ELSE #{kioskDownRun} END " +
            "WHERE locker24_device_health_latest.measured_at <= EXCLUDED.measured_at")
    int upsertLatest(LatestRow row);

    @Update("UPDATE locker24_device_health_latest " +
            "SET received_at = GREATEST(received_at, #{receivedAt}) " +
            "WHERE device_id = #{deviceId}")
    int touchReceived(@Param("deviceId") int deviceId, @Param("receivedAt") long receivedAt);

    @Select("SELECT l.device_id AS deviceId, d.customerId AS customerId, " +
            "l.received_at AS receivedAt, l.measured_at AS measuredAt, " +
            "l.battery_pct AS batteryPct, l.charging AS charging, l.temperature_c AS temperatureC, " +
            "l.free_storage_mb AS freeStorageMb, " +
            "l.kiosk_required AS kioskRequired, l.kiosk_running AS kioskRunning, " +
            "l.kiosk_admin_unlocked AS kioskAdminUnlocked, " +
            "l.kiosk_app_package AS kioskAppPackage, l.kiosk_app_version AS kioskAppVersion, " +
            "l.locker_short_uid AS lockerShortUid, " +
            "l.kiosk_down_since AS kioskDownSince, l.kiosk_down_reports AS kioskDownReports, " +
            "l.status AS status, l.non_green_since AS nonGreenSince, " +
            "l.version_mismatch_since AS versionMismatchSince, " +
            "d.number AS deviceNumber, d.configurationId AS configurationId, c.name AS configurationName " +
            "FROM locker24_device_health_latest l " +
            "JOIN devices d ON d.id = l.device_id " +
            "LEFT JOIN configurations c ON c.id = d.configurationId " +
            "ORDER BY d.number, l.device_id")
    List<EvalRow> selectEvaluationRows();

    @Select("SELECT id, device_id AS deviceId, customer_id AS customerId, condition, severity, " +
            "raised_at AS raisedAt, last_present_at AS lastPresentAt " +
            "FROM locker24_device_health_alerts WHERE cleared_at IS NULL ORDER BY device_id, id")
    List<AlertRow> selectOpenAlerts();

    @Select("SELECT customer_id AS customerId, webhook_url AS webhookUrl, time_zone AS timeZone " +
            "FROM locker24_health_settings")
    List<SettingsRow> selectSettings();

    @Update("UPDATE locker24_device_health_latest SET " +
            "status = #{status}, " +
            "status_reasons = #{reasons,jdbcType=VARCHAR}, " +
            "non_green_since = #{nonGreenSince,jdbcType=BIGINT}, " +
            "version_mismatch_since = #{versionMismatchSince,jdbcType=BIGINT}, " +
            "evaluated_at = #{evaluatedAt} " +
            "WHERE device_id = #{deviceId}")
    int updateEvaluation(@Param("deviceId") int deviceId,
                         @Param("status") String status,
                         @Param("reasons") String reasons,
                         @Param("nonGreenSince") Long nonGreenSince,
                         @Param("versionMismatchSince") Long versionMismatchSince,
                         @Param("evaluatedAt") long evaluatedAt);

    @Insert("INSERT INTO locker24_device_health_alerts " +
            "(device_id, customer_id, condition, severity, raised_at, last_present_at) " +
            "VALUES (#{deviceId}, #{customerId}, #{condition}, #{severity}, #{now}, #{now}) " +
            "ON CONFLICT (device_id, condition) WHERE cleared_at IS NULL DO NOTHING")
    int insertAlert(@Param("deviceId") int deviceId,
                    @Param("customerId") int customerId,
                    @Param("condition") String condition,
                    @Param("severity") String severity,
                    @Param("now") long now);

    @Update("UPDATE locker24_device_health_alerts SET severity = 'red', last_present_at = #{now} " +
            "WHERE id = #{id} AND cleared_at IS NULL AND severity = 'yellow'")
    int escalateAlert(@Param("id") long id, @Param("now") long now);

    @Update("UPDATE locker24_device_health_alerts SET last_present_at = GREATEST(last_present_at, #{now}) " +
            "WHERE id = #{id} AND cleared_at IS NULL")
    int touchAlert(@Param("id") long id, @Param("now") long now);

    @Update("UPDATE locker24_device_health_alerts SET cleared_at = #{now} " +
            "WHERE id = #{id} AND cleared_at IS NULL")
    int clearAlert(@Param("id") long id, @Param("now") long now);

    @Delete("DELETE FROM locker24_device_health_history WHERE id IN (" +
            "SELECT id FROM locker24_device_health_history WHERE measured_at < #{cutoff} LIMIT #{limit})")
    int deleteHistoryBefore(@Param("cutoff") long cutoff, @Param("limit") int limit);

    @Delete("DELETE FROM locker24_device_health_alerts WHERE id IN (" +
            "SELECT id FROM locker24_device_health_alerts WHERE cleared_at < #{cutoff} LIMIT #{limit})")
    int deleteClearedAlertsBefore(@Param("cutoff") long cutoff, @Param("limit") int limit);
}
