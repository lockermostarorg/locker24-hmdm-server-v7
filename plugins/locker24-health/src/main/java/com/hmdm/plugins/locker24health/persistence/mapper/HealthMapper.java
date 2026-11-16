package com.hmdm.plugins.locker24health.persistence.mapper;

import com.hmdm.plugins.locker24health.persistence.domain.AlertRow;
import com.hmdm.plugins.locker24health.persistence.domain.EvalRow;
import com.hmdm.plugins.locker24health.persistence.domain.FleetRecord;
import com.hmdm.plugins.locker24health.persistence.domain.HistoryRow;
import com.hmdm.plugins.locker24health.persistence.domain.LatestRow;
import com.hmdm.plugins.locker24health.persistence.domain.SettingsRow;
import com.hmdm.plugins.locker24health.persistence.domain.SettingsView;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface HealthMapper {

    int TIMEOUT_SECONDS = 30;

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
    @Options(timeout = TIMEOUT_SECONDS)
    List<EvalRow> selectEvaluationRows();

    @Select("SELECT id, device_id AS deviceId, customer_id AS customerId, condition, severity, " +
            "raised_at AS raisedAt, last_present_at AS lastPresentAt " +
            "FROM locker24_device_health_alerts WHERE cleared_at IS NULL ORDER BY device_id, id")
    @Options(timeout = TIMEOUT_SECONDS)
    List<AlertRow> selectOpenAlerts();

    @Select("SELECT customer_id AS customerId, webhook_url AS webhookUrl, time_zone AS timeZone " +
            "FROM locker24_health_settings")
    @Options(timeout = TIMEOUT_SECONDS)
    List<SettingsRow> selectSettings();

    @Update("UPDATE locker24_device_health_latest SET " +
            "status = #{status}, " +
            "status_reasons = #{reasons,jdbcType=VARCHAR}, " +
            "non_green_since = #{nonGreenSince,jdbcType=BIGINT}, " +
            "version_mismatch_since = #{versionMismatchSince,jdbcType=BIGINT}, " +
            "evaluated_at = #{evaluatedAt} " +
            "WHERE device_id = #{deviceId}")
    @Options(timeout = TIMEOUT_SECONDS)
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
    @Options(timeout = TIMEOUT_SECONDS)
    int insertAlert(@Param("deviceId") int deviceId,
                    @Param("customerId") int customerId,
                    @Param("condition") String condition,
                    @Param("severity") String severity,
                    @Param("now") long now);

    @Update("UPDATE locker24_device_health_alerts SET severity = 'red', last_present_at = #{now} " +
            "WHERE id = #{id} AND cleared_at IS NULL AND severity = 'yellow'")
    @Options(timeout = TIMEOUT_SECONDS)
    int escalateAlert(@Param("id") long id, @Param("now") long now);

    @Update("UPDATE locker24_device_health_alerts SET last_present_at = GREATEST(last_present_at, #{now}) " +
            "WHERE id = #{id} AND cleared_at IS NULL")
    @Options(timeout = TIMEOUT_SECONDS)
    int touchAlert(@Param("id") long id, @Param("now") long now);

    @Update("UPDATE locker24_device_health_alerts SET cleared_at = #{now} " +
            "WHERE id = #{id} AND cleared_at IS NULL")
    @Options(timeout = TIMEOUT_SECONDS)
    int clearAlert(@Param("id") long id, @Param("now") long now);

    @Delete("DELETE FROM locker24_device_health_history WHERE id IN (" +
            "SELECT id FROM locker24_device_health_history WHERE measured_at < #{cutoff} LIMIT #{limit})")
    @Options(timeout = TIMEOUT_SECONDS)
    int deleteHistoryBefore(@Param("cutoff") long cutoff, @Param("limit") int limit);

    @Delete("DELETE FROM locker24_device_health_alerts WHERE id IN (" +
            "SELECT id FROM locker24_device_health_alerts WHERE cleared_at < #{cutoff} LIMIT #{limit})")
    @Options(timeout = TIMEOUT_SECONDS)
    int deleteClearedAlertsBefore(@Param("cutoff") long cutoff, @Param("limit") int limit);

    String ACCESS_FILTER = "AND (u.allDevicesAvailable = TRUE OR EXISTS (" +
            "SELECT 1 FROM deviceGroups dg " +
            "INNER JOIN userDeviceGroupsAccess a ON a.groupId = dg.groupId AND a.userId = u.id " +
            "WHERE dg.deviceId = d.id)) ";

    int FLEET_LIMIT = 5000;
    int HISTORY_ROW_LIMIT = 20000;

    @Select("SELECT d.id AS deviceId, d.number AS number, d.description AS description, " +
            "d.configurationId AS configurationId, c.name AS configurationName, " +
            "(SELECT string_agg(g.name, ', ' ORDER BY g.name) FROM deviceGroups dg " +
            "INNER JOIN groups g ON g.id = dg.groupId WHERE dg.deviceId = d.id) AS groups, " +
            "l.locker_short_uid AS lockerShortUid, l.received_at AS lastSeen, " +
            "CASE WHEN l.device_id IS NULL OR l.evaluated_at IS NULL THEN 'unknown' ELSE l.status END AS status, " +
            "l.status_reasons AS reasons, l.battery_pct AS batteryPct, l.charging AS charging, " +
            "l.temperature_c AS temperatureC, l.free_storage_mb AS freeStorageMb, " +
            "l.kiosk_app_version AS kioskAppVersion, l.agent_version AS agentVersion, " +
            "l.kiosk_required AS kioskRequired, l.kiosk_running AS kioskRunning, " +
            "l.kiosk_admin_unlocked AS kioskAdminUnlocked, l.model AS model " +
            "FROM devices d " +
            "INNER JOIN users u ON u.id = #{userId} AND u.customerId = d.customerId " +
            "LEFT JOIN configurations c ON c.id = d.configurationId " +
            "LEFT JOIN locker24_device_health_latest l ON l.device_id = d.id " +
            "WHERE d.customerId = #{customerId} " +
            ACCESS_FILTER +
            "AND (CAST(#{configurationId,jdbcType=INTEGER} AS INTEGER) IS NULL " +
            "OR d.configurationId = CAST(#{configurationId,jdbcType=INTEGER} AS INTEGER)) " +
            "AND (CAST(#{model,jdbcType=VARCHAR} AS VARCHAR) IS NULL " +
            "OR l.model = CAST(#{model,jdbcType=VARCHAR} AS VARCHAR)) " +
            "AND (CAST(#{status,jdbcType=VARCHAR} AS VARCHAR) IS NULL " +
            "OR CASE WHEN l.device_id IS NULL OR l.evaluated_at IS NULL THEN 'unknown' ELSE l.status END " +
            "= CAST(#{status,jdbcType=VARCHAR} AS VARCHAR)) " +
            "ORDER BY d.number, d.id LIMIT " + FLEET_LIMIT)
    @Options(timeout = TIMEOUT_SECONDS)
    List<FleetRecord> selectFleet(@Param("customerId") int customerId,
                                  @Param("userId") int userId,
                                  @Param("configurationId") Integer configurationId,
                                  @Param("status") String status,
                                  @Param("model") String model);

    @Select("SELECT d.id FROM devices d " +
            "INNER JOIN users u ON u.id = #{userId} AND u.customerId = d.customerId " +
            "WHERE d.id = #{deviceId} AND d.customerId = #{customerId} " +
            ACCESS_FILTER)
    @Options(timeout = TIMEOUT_SECONDS)
    Integer selectDeviceAccess(@Param("customerId") int customerId,
                               @Param("userId") int userId,
                               @Param("deviceId") int deviceId);

    @Select("SELECT measured_at AS measuredAt, battery_pct AS batteryPct, charging AS charging, " +
            "temperature_c AS temperatureC, rssi_dbm AS rssiDbm, kiosk_running AS kioskRunning " +
            "FROM locker24_device_health_history " +
            "WHERE device_id = #{deviceId} AND measured_at >= #{from} AND measured_at <= #{to} " +
            "AND received_at - measured_at < #{maxAgeMs} " +
            "ORDER BY measured_at DESC LIMIT " + HISTORY_ROW_LIMIT)
    @Options(timeout = TIMEOUT_SECONDS)
    List<HistoryRow> selectHistoryPoints(@Param("deviceId") int deviceId,
                                         @Param("from") long from,
                                         @Param("to") long to,
                                         @Param("maxAgeMs") long maxAgeMs);

    @Select("SELECT DISTINCT received_at FROM locker24_device_health_history " +
            "WHERE device_id = #{deviceId} AND received_at >= #{from} AND received_at <= #{to} " +
            "ORDER BY received_at LIMIT " + HISTORY_ROW_LIMIT)
    @Options(timeout = TIMEOUT_SECONDS)
    List<Long> selectReceivedTimes(@Param("deviceId") int deviceId,
                                   @Param("from") long from,
                                   @Param("to") long to);

    @Select("SELECT MAX(received_at) FROM locker24_device_health_history " +
            "WHERE device_id = #{deviceId} AND received_at < #{before}")
    @Options(timeout = TIMEOUT_SECONDS)
    Long selectLastReceivedBefore(@Param("deviceId") int deviceId, @Param("before") long before);

    @Select("SELECT COALESCE(webhook_url <> '', FALSE) AS webhookUrlSet, time_zone AS timeZone " +
            "FROM locker24_health_settings WHERE customer_id = #{customerId}")
    @Options(timeout = TIMEOUT_SECONDS)
    SettingsView selectCustomerSettings(@Param("customerId") int customerId);

    @Insert("INSERT INTO locker24_health_settings AS s (customer_id, webhook_url, time_zone) " +
            "VALUES (#{customerId}, #{webhookUrl,jdbcType=VARCHAR}, COALESCE(CAST(#{timeZone,jdbcType=VARCHAR} AS VARCHAR), 'Europe/Sarajevo')) " +
            "ON CONFLICT (customer_id) DO UPDATE SET " +
            "webhook_url = CASE WHEN #{setWebhook} THEN EXCLUDED.webhook_url ELSE s.webhook_url END, " +
            "time_zone = CASE WHEN #{setTimeZone} THEN EXCLUDED.time_zone ELSE s.time_zone END")
    @Options(timeout = TIMEOUT_SECONDS)
    int upsertCustomerSettings(@Param("customerId") int customerId,
                               @Param("webhookUrl") String webhookUrl,
                               @Param("timeZone") String timeZone,
                               @Param("setWebhook") boolean setWebhook,
                               @Param("setTimeZone") boolean setTimeZone);
}
