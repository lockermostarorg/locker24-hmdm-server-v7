package com.hmdm.plugins.locker24health.ingest;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.hmdm.persistence.domain.Device;
import com.hmdm.plugins.locker24health.model.ParsedBatch;
import com.hmdm.plugins.locker24health.model.ParsedReport;
import com.hmdm.plugins.locker24health.persistence.HealthDAO;
import com.hmdm.plugins.locker24health.persistence.domain.HistoryRow;
import com.hmdm.plugins.locker24health.persistence.domain.LatestRow;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Singleton
public class IngestService {

    private final HealthDAO dao;

    @Inject
    public IngestService(HealthDAO dao) {
        this.dao = dao;
    }

    public int store(Device device, ParsedBatch batch, long receivedAt) {
        List<ParsedReport> reports = new ArrayList<>(batch.getReports());
        if (reports.isEmpty()) {
            return 0;
        }
        reports.sort(Comparator.comparingLong(ParsedReport::getTs));

        List<HistoryRow> history = new ArrayList<>(reports.size());
        for (ParsedReport r : reports) {
            history.add(historyRow(device, r, receivedAt, measuredAt(batch, r, receivedAt)));
        }
        ParsedReport newest = reports.get(reports.size() - 1);
        LatestRow latest = latestRow(device, newest, receivedAt, measuredAt(batch, newest, receivedAt));
        return dao.saveBatch(history, latest);
    }

    private static long measuredAt(ParsedBatch batch, ParsedReport r, long receivedAt) {
        return ClockCorrection.measuredAt(receivedAt, batch.getSentAt(), r.getTs());
    }

    private static HistoryRow historyRow(Device device, ParsedReport r, long receivedAt, long measuredAt) {
        HistoryRow h = new HistoryRow();
        h.setDeviceId(device.getId());
        h.setDeviceTs(r.getTs());
        h.setReceivedAt(receivedAt);
        h.setMeasuredAt(measuredAt);
        h.setBatteryPct(r.getBatteryPct());
        h.setCharging(r.getCharging());
        h.setTemperatureC(r.getTemperatureC());
        h.setRssiDbm(r.getRssiDbm());
        h.setKioskRunning(r.getKioskRunning());
        h.setKioskAdminUnlocked(r.getKioskAdminUnlocked());
        h.setKioskDown(Boolean.TRUE.equals(r.getKioskRequired())
                && Boolean.FALSE.equals(r.getKioskRunning())
                && !Boolean.TRUE.equals(r.getKioskAdminUnlocked()));
        return h;
    }

    private static LatestRow latestRow(Device device, ParsedReport r, long receivedAt, long measuredAt) {
        LatestRow l = new LatestRow();
        l.setDeviceId(device.getId());
        l.setCustomerId(device.getCustomerId());
        l.setReceivedAt(receivedAt);
        l.setMeasuredAt(measuredAt);
        l.setBatteryPct(r.getBatteryPct());
        l.setCharging(r.getCharging());
        l.setTemperatureC(r.getTemperatureC());
        l.setFreeStorageMb(r.getFreeStorageMb());
        l.setRssiDbm(r.getRssiDbm());
        l.setKioskRequired(r.getKioskRequired());
        l.setKioskRunning(r.getKioskRunning());
        l.setKioskAdminUnlocked(r.getKioskAdminUnlocked());
        l.setKioskAppPackage(r.getKioskAppPackage());
        l.setKioskAppVersion(r.getKioskAppVersion());
        l.setAgentVersion(r.getAgentVersion());
        l.setLockerShortUid(r.getLockerShortUid());
        l.setManufacturer(r.getManufacturer());
        l.setModel(r.getModel());
        l.setReportJson(r.getJson());
        return l;
    }
}
