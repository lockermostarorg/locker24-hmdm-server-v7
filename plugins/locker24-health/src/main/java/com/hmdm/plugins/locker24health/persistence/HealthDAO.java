package com.hmdm.plugins.locker24health.persistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.hmdm.plugins.locker24health.alert.AlertDecision;
import com.hmdm.plugins.locker24health.alert.AlertEvent;
import com.hmdm.plugins.locker24health.alert.OpenAlert;
import com.hmdm.plugins.locker24health.persistence.domain.AlertRow;
import com.hmdm.plugins.locker24health.persistence.domain.EvalRow;
import com.hmdm.plugins.locker24health.persistence.domain.FleetRecord;
import com.hmdm.plugins.locker24health.persistence.domain.GapRow;
import com.hmdm.plugins.locker24health.persistence.domain.ReceivedBounds;
import com.hmdm.plugins.locker24health.persistence.domain.HistoryRow;
import com.hmdm.plugins.locker24health.persistence.domain.LatestRow;
import com.hmdm.plugins.locker24health.persistence.domain.SettingsRow;
import com.hmdm.plugins.locker24health.persistence.domain.SettingsView;
import com.hmdm.plugins.locker24health.rest.json.FleetRow;
import com.hmdm.plugins.locker24health.rest.json.HealthSettings;
import com.hmdm.plugins.locker24health.ingest.ClockCorrection;
import com.hmdm.plugins.locker24health.persistence.mapper.HealthMapper;
import com.hmdm.plugins.locker24health.status.Condition;
import com.hmdm.plugins.locker24health.status.Evaluation;
import com.hmdm.plugins.locker24health.status.Finding;
import com.hmdm.plugins.locker24health.status.Severity;
import org.mybatis.guice.transactional.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Singleton
public class HealthDAO {

    private final HealthMapper mapper;

    @Inject
    public HealthDAO(HealthMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional
    public int saveBatch(List<HistoryRow> history, LatestRow latest) {
        int inserted = 0;
        List<HistoryRow> fresh = new ArrayList<>();
        for (HistoryRow row : history) {
            int n = mapper.insertHistory(row);
            inserted += n;
            if (n > 0) {
                fresh.add(row);
            }
        }
        applyStreak(latest, fresh);
        mapper.upsertLatest(latest);
        mapper.touchReceived(latest.getDeviceId(), latest.getReceivedAt());
        return inserted;
    }

    static void applyStreak(LatestRow latest, List<HistoryRow> fresh) {
        int run = 0;
        Long runStart = null;
        boolean allDown = true;
        for (HistoryRow r : fresh) {
            if (r.isKioskDown()) {
                if (run == 0) {
                    runStart = r.getMeasuredAt();
                }
                run++;
            } else {
                run = 0;
                runStart = null;
                allDown = false;
            }
        }
        latest.setFreshReports(fresh.size());
        latest.setKioskDownRun(run);
        latest.setKioskDownRunStart(runStart);
        latest.setKioskDownAll(allDown);
    }

    public List<FleetRow> loadFleet(int customerId, int userId, Integer configurationId, String status, String model) {
        List<FleetRow> out = new ArrayList<>();
        for (FleetRecord r : mapper.selectFleet(customerId, userId, configurationId, status, model)) {
            out.add(fleetRow(r));
        }
        return out;
    }

    public boolean canAccessDevice(int customerId, int userId, int deviceId) {
        return mapper.selectDeviceAccess(customerId, userId, deviceId) != null;
    }

    public List<HistoryRow> historyPoints(int deviceId, long from, long to, long bucketMs) {
        List<HistoryRow> rows = new ArrayList<>(
                mapper.selectHistoryPoints(deviceId, from, to, bucketMs, ClockCorrection.MAX_AGE_MS));
        Collections.reverse(rows);
        return rows;
    }

    public List<GapRow> offlineGaps(int deviceId, long from, long to, long threshold) {
        return mapper.selectOfflineGaps(deviceId, from, to, threshold);
    }

    public ReceivedBounds receivedBounds(int deviceId, long from, long to) {
        return mapper.selectReceivedBounds(deviceId, from, to);
    }

    public Long lastReceivedBefore(int deviceId, long before) {
        return mapper.selectLastReceivedBefore(deviceId, before);
    }

    public Long latestReceived(int deviceId) {
        return mapper.selectLatestReceived(deviceId);
    }

    public HealthSettings loadSettings(int customerId) {
        SettingsView v = mapper.selectCustomerSettings(customerId);
        if (v == null) {
            return null;
        }
        HealthSettings s = new HealthSettings();
        s.setWebhookUrlSet(v.isWebhookUrlSet());
        s.setTimeZone(v.getTimeZone());
        return s;
    }

    public void saveSettings(int customerId, HealthSettings s) {
        mapper.upsertCustomerSettings(customerId, s.getWebhookUrl() == null || s.getWebhookUrl().isEmpty() ? null : s.getWebhookUrl(),
                s.getTimeZone(), s.getWebhookUrl() != null, s.getTimeZone() != null);
    }

    static FleetRow fleetRow(FleetRecord r) {
        FleetRow f = new FleetRow();
        f.setDeviceId(r.getDeviceId());
        f.setNumber(r.getNumber());
        f.setDescription(r.getDescription());
        f.setConfigurationId(r.getConfigurationId());
        f.setConfigurationName(r.getConfigurationName());
        f.setGroups(r.getGroups());
        f.setLockerShortUid(r.getLockerShortUid());
        f.setLastSeen(r.getLastSeen());
        f.setStatus(r.getStatus());
        List<String> reasons = new ArrayList<>();
        if (r.getReasons() != null) {
            for (String c : r.getReasons().split(",")) {
                if (!c.isEmpty()) {
                    reasons.add(c);
                }
            }
        }
        f.setReasons(reasons);
        f.setBatteryPct(r.getBatteryPct());
        f.setCharging(r.getCharging());
        f.setTemperatureC(r.getTemperatureC());
        f.setFreeStorageMb(r.getFreeStorageMb());
        f.setKioskAppVersion(r.getKioskAppVersion());
        f.setAgentVersion(r.getAgentVersion());
        f.setKioskRequired(r.getKioskRequired());
        f.setKioskRunning(r.getKioskRunning());
        f.setKioskAdminUnlocked(r.getKioskAdminUnlocked());
        f.setModel(r.getModel());
        return f;
    }

    public static final int MAX_REASONS_LENGTH = 256;

    public List<EvalRow> loadEvaluationRows() {
        return mapper.selectEvaluationRows();
    }

    public Map<Integer, Map<Condition, OpenAlert>> loadOpenAlerts() {
        Map<Integer, Map<Condition, OpenAlert>> out = new HashMap<>();
        for (AlertRow r : mapper.selectOpenAlerts()) {
            Condition c = condition(r.getCondition());
            Severity s = severity(r.getSeverity());
            if (c == null || s == null) {
                continue;
            }
            out.computeIfAbsent(r.getDeviceId(), k -> new EnumMap<>(Condition.class))
                    .putIfAbsent(c, new OpenAlert(r.getId(), c, s, r.getRaisedAt(), r.getLastPresentAt()));
        }
        return out;
    }

    public Map<Integer, SettingsRow> loadSettings() {
        Map<Integer, SettingsRow> out = new HashMap<>();
        for (SettingsRow r : mapper.selectSettings()) {
            out.put(r.getCustomerId(), r);
        }
        return out;
    }

    @Transactional
    public List<AlertEvent> applyEvaluation(EvalRow row, Evaluation evaluation, AlertDecision decision,
                                            Map<Condition, OpenAlert> open, long now) {
        int deviceId = row.getDeviceId();
        mapper.updateEvaluation(deviceId, code(evaluation.status().name()), reasons(evaluation.findings()),
                decision.nonGreenSince(), evaluation.versionMismatchSince(), now);
        List<AlertEvent> events = new ArrayList<>();
        for (Finding f : decision.raise()) {
            if (mapper.insertAlert(deviceId, row.getCustomerId(), f.condition().code(), code(f.severity().name()), now) > 0) {
                events.add(event(row, f.condition(), f.severity(), false, f.detail(), now));
            }
        }
        for (Finding f : decision.escalate()) {
            OpenAlert existing = open.get(f.condition());
            if (existing != null && mapper.escalateAlert(existing.id(), now) > 0) {
                events.add(event(row, f.condition(), Severity.RED, false, f.detail(), now));
            }
        }
        for (OpenAlert a : decision.touch()) {
            mapper.touchAlert(a.id(), now);
        }
        for (OpenAlert a : decision.clear()) {
            if (mapper.clearAlert(a.id(), now) > 0) {
                events.add(event(row, a.condition(), a.severity(), true, null, now));
            }
        }
        return events;
    }

    public int purgeHistoryBatch(long cutoff, int limit) {
        return mapper.deleteHistoryBefore(cutoff, limit);
    }

    public int purgeClearedAlertsBatch(long cutoff, int limit) {
        return mapper.deleteClearedAlertsBefore(cutoff, limit);
    }

    static String reasons(List<Finding> findings) {
        Set<String> codes = new LinkedHashSet<>();
        for (Finding f : findings) {
            codes.add(f.condition().code());
        }
        if (codes.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (String c : codes) {
            int extra = (sb.length() == 0 ? 0 : 1) + c.length();
            if (sb.length() + extra > MAX_REASONS_LENGTH) {
                break;
            }
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static AlertEvent event(EvalRow row, Condition c, Severity s, boolean cleared, String detail, long now) {
        return new AlertEvent(row.getCustomerId(), row.getDeviceId(), row.getDeviceNumber(), row.getConfigurationName(),
                row.getLockerShortUid(), c, s, cleared, detail, now, null);
    }

    private static String code(String enumName) {
        return enumName.toLowerCase(Locale.ROOT);
    }

    private static Condition condition(String code) {
        for (Condition c : Condition.values()) {
            if (c.code().equals(code)) {
                return c;
            }
        }
        return null;
    }

    private static Severity severity(String code) {
        for (Severity s : Severity.values()) {
            if (code(s.name()).equals(code)) {
                return s;
            }
        }
        return null;
    }
}
