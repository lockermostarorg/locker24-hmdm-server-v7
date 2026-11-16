package com.hmdm.plugins.locker24health.ingest;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdm.plugins.locker24health.model.ParsedBatch;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hmdm.plugins.locker24health.model.ParsedReport;

import java.util.ArrayList;
import java.util.List;

public final class ReportParser {

    public static final int MAX_BODY_CHARS = 262_144;
    public static final int MAX_REPORTS = 50;

    private static final int STR_DEFAULT = 128;
    private static final int STR_SHORT = 64;
    private static final int STR_ERROR = 200;

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private ReportParser() {
    }

    public static ParsedBatch parse(String body) {
        if (body == null || body.length() > MAX_BODY_CHARS) {
            throw new ValidationException();
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(body);
        } catch (Exception e) {
            throw new ValidationException();
        }
        if (root == null || !root.isObject()) {
            throw new ValidationException();
        }
        JsonNode sentAt = root.get("sent_at");
        JsonNode reports = root.get("reports");
        if (sentAt == null || !sentAt.isIntegralNumber() || !sentAt.canConvertToLong()
                || reports == null || !reports.isArray()
                || reports.size() < 1 || reports.size() > MAX_REPORTS) {
            throw new ValidationException();
        }
        List<ParsedReport> parsed = new ArrayList<>(reports.size());
        for (JsonNode report : reports) {
            parsed.add(parseReport(report));
        }
        return new ParsedBatch(sentAt.asLong(), parsed);
    }

    private static ParsedReport parseReport(JsonNode n) {
        if (!n.isObject()) {
            throw new ValidationException();
        }
        JsonNode v = n.get("v");
        JsonNode ts = n.get("ts");
        if (v == null || !v.isIntegralNumber() || !v.canConvertToInt() || v.asInt() != 1
                || ts == null || !ts.isIntegralNumber() || !ts.canConvertToLong()) {
            throw new ValidationException();
        }
        JsonNode power = n.path("power");
        JsonNode system = n.path("system");
        JsonNode network = n.path("network");
        JsonNode kiosk = n.path("kiosk");
        JsonNode app = n.path("app");

        ParsedReport r = new ParsedReport();
        r.setTs(ts.asLong());
        r.setTrigger(str(n, "trigger", STR_SHORT));

        r.setBatteryPct(intIn(power, "battery_pct", 0, 100));
        r.setCharging(bool(power, "charging"));
        r.setChargeSource(str(power, "charge_source", STR_SHORT));
        r.setTemperatureC(floatIn(power, "temperature_c", -40, 100));
        r.setBatteryHealth(str(power, "battery_health", STR_SHORT));

        r.setManufacturer(str(system, "manufacturer", STR_SHORT));
        r.setModel(str(system, "model", STR_SHORT));
        r.setAndroidBuild(str(system, "android_build", STR_DEFAULT));
        r.setAndroidVersion(str(system, "android_version", STR_SHORT));
        r.setSdkInt(intIn(system, "sdk_int", 0, 1000));
        r.setSecurityPatch(str(system, "security_patch", STR_SHORT));
        r.setFreeStorageMb(intIn(system, "free_storage_mb", 0, Integer.MAX_VALUE));
        r.setFreeRamMb(intIn(system, "free_ram_mb", 0, Integer.MAX_VALUE));
        r.setUptimeS(longIn(system, "uptime_s", 0));

        r.setWifiSsid(str(network, "wifi_ssid", STR_DEFAULT));
        r.setRssiDbm(intIn(network, "rssi_dbm", -127, 0));
        r.setIp(str(network, "ip", STR_SHORT));
        r.setMqttConnected(bool(network, "mqtt_connected"));
        r.setLastConfigSyncAt(longIn(network, "last_config_sync_at", 0));

        r.setKioskRequired(bool(kiosk, "required"));
        r.setKioskRunning(bool(kiosk, "running"));
        r.setKioskAdminUnlocked(bool(kiosk, "admin_unlocked"));
        r.setForegroundPackage(str(kiosk, "foreground_package", STR_DEFAULT));
        r.setKioskAppPackage(str(kiosk, "app_package", STR_DEFAULT));
        r.setKioskAppVersion(str(kiosk, "app_version_name", STR_SHORT));
        r.setKioskAppVersionCode(intIn(kiosk, "app_version_code", 0, Integer.MAX_VALUE));
        r.setAgentVersion(str(kiosk, "agent_version_name", STR_SHORT));
        r.setAgentVersionCode(intIn(kiosk, "agent_version_code", 0, Integer.MAX_VALUE));
        r.setWatchdogRestarts24h(intIn(kiosk, "watchdog_restarts_24h", 0, Integer.MAX_VALUE));

        r.setAppPublishedAt(longIn(app, "published_at", 0));
        r.setLockerShortUid(str(app, "locker_short_uid", STR_SHORT));
        r.setLastApiOkAt(longIn(app, "last_api_ok_at", 0));
        r.setLastApiErrorAt(longIn(app, "last_api_error_at", 0));
        r.setLastApiError(str(app, "last_api_error", STR_ERROR));
        r.setAppErrors24h(intIn(app, "app_errors_24h", 0, Integer.MAX_VALUE));

        r.setJson(buildJson(r).toString());
        return r;
    }

    private static String str(JsonNode group, String key, int max) {
        JsonNode x = group.get(key);
        if (x == null || !x.isTextual()) {
            return null;
        }
        String s = stripControl(x.asText());
        if (s.length() > max) {
            int end = max;
            if (Character.isHighSurrogate(s.charAt(end - 1))) {
                end--;
            }
            s = s.substring(0, end);
        }
        return s;
    }

    private static String stripControl(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c > 0x1F && c != 0x7F) {
                b.append(c);
            }
        }
        return b.toString();
    }

    private static ObjectNode buildJson(ParsedReport r) {
        ObjectNode o = MAPPER.createObjectNode();
        o.put("v", 1);
        o.put("ts", r.getTs());
        o.put("trigger", r.getTrigger());
        ObjectNode power = o.putObject("power");
        power.put("battery_pct", r.getBatteryPct());
        power.put("charging", r.getCharging());
        power.put("charge_source", r.getChargeSource());
        power.put("temperature_c", r.getTemperatureC());
        power.put("battery_health", r.getBatteryHealth());
        ObjectNode system = o.putObject("system");
        system.put("manufacturer", r.getManufacturer());
        system.put("model", r.getModel());
        system.put("android_build", r.getAndroidBuild());
        system.put("android_version", r.getAndroidVersion());
        system.put("sdk_int", r.getSdkInt());
        system.put("security_patch", r.getSecurityPatch());
        system.put("free_storage_mb", r.getFreeStorageMb());
        system.put("free_ram_mb", r.getFreeRamMb());
        system.put("uptime_s", r.getUptimeS());
        ObjectNode network = o.putObject("network");
        network.put("wifi_ssid", r.getWifiSsid());
        network.put("rssi_dbm", r.getRssiDbm());
        network.put("ip", r.getIp());
        network.put("mqtt_connected", r.getMqttConnected());
        network.put("last_config_sync_at", r.getLastConfigSyncAt());
        ObjectNode kiosk = o.putObject("kiosk");
        kiosk.put("required", r.getKioskRequired());
        kiosk.put("running", r.getKioskRunning());
        kiosk.put("admin_unlocked", r.getKioskAdminUnlocked());
        kiosk.put("foreground_package", r.getForegroundPackage());
        kiosk.put("app_package", r.getKioskAppPackage());
        kiosk.put("app_version_name", r.getKioskAppVersion());
        kiosk.put("app_version_code", r.getKioskAppVersionCode());
        kiosk.put("agent_version_name", r.getAgentVersion());
        kiosk.put("agent_version_code", r.getAgentVersionCode());
        kiosk.put("watchdog_restarts_24h", r.getWatchdogRestarts24h());
        ObjectNode app = o.putObject("app");
        app.put("published_at", r.getAppPublishedAt());
        app.put("locker_short_uid", r.getLockerShortUid());
        app.put("last_api_ok_at", r.getLastApiOkAt());
        app.put("last_api_error_at", r.getLastApiErrorAt());
        app.put("last_api_error", r.getLastApiError());
        app.put("app_errors_24h", r.getAppErrors24h());
        return o;
    }

    private static Boolean bool(JsonNode group, String key) {
        JsonNode x = group.get(key);
        return x != null && x.isBoolean() ? x.asBoolean() : null;
    }

    private static Integer intIn(JsonNode group, String key, int min, int max) {
        JsonNode x = group.get(key);
        if (x == null || !x.isIntegralNumber() || !x.canConvertToInt()) {
            return null;
        }
        int i = x.asInt();
        return i < min || i > max ? null : i;
    }

    private static Long longIn(JsonNode group, String key, long min) {
        JsonNode x = group.get(key);
        if (x == null || !x.isIntegralNumber() || !x.canConvertToLong()) {
            return null;
        }
        long l = x.asLong();
        return l < min ? null : l;
    }

    private static Float floatIn(JsonNode group, String key, double min, double max) {
        JsonNode x = group.get(key);
        if (x == null || !x.isNumber()) {
            return null;
        }
        double d = x.asDouble();
        return Double.isNaN(d) || d < min || d > max ? null : (float) d;
    }
}
