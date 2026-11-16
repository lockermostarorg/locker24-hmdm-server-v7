package com.hmdm.plugins.locker24health.ingest;

import com.hmdm.persistence.domain.Device;
import com.hmdm.plugins.locker24health.model.ParsedBatch;
import com.hmdm.plugins.locker24health.model.ParsedReport;
import com.hmdm.plugins.locker24health.persistence.HealthDAO;
import com.hmdm.plugins.locker24health.persistence.domain.HistoryRow;
import com.hmdm.plugins.locker24health.persistence.domain.LatestRow;
import com.hmdm.plugins.locker24health.persistence.mapper.HealthMapper;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class IngestServiceTest {

    private static final long R = 1_763_300_000_000L;
    private static final long SENT = 1_000_000L;
    private static final int DEVICE_ID = 7;
    private static final int CUSTOMER_ID = 3;

    private HealthMapper mapper;
    private IngestService service;
    private Device device;

    @Before
    public void setUp() {
        mapper = mock(HealthMapper.class);
        when(mapper.insertHistory(any(HistoryRow.class))).thenReturn(1);
        service = new IngestService(new HealthDAO(mapper));
        device = new Device();
        device.setId(DEVICE_ID);
        device.setCustomerId(CUSTOMER_ID);
    }

    private static ParsedReport report(long ts, int battery) {
        ParsedReport r = new ParsedReport();
        r.setTs(ts);
        r.setBatteryPct(battery);
        r.setJson("{\"ts\":" + ts + "}");
        return r;
    }

    private static ParsedBatch batch(ParsedReport... reports) {
        return new ParsedBatch(SENT, Arrays.asList(reports));
    }

    private List<HistoryRow> capturedHistory(int expected) {
        ArgumentCaptor<HistoryRow> captor = ArgumentCaptor.forClass(HistoryRow.class);
        verify(mapper, times(expected)).insertHistory(captor.capture());
        return captor.getAllValues();
    }

    private LatestRow capturedLatest() {
        ArgumentCaptor<LatestRow> captor = ArgumentCaptor.forClass(LatestRow.class);
        verify(mapper, times(1)).upsertLatest(captor.capture());
        return captor.getValue();
    }

    @Test
    public void historyInsertedOldestFirst() {
        service.store(device, batch(report(3, 30), report(1, 10), report(2, 20)), R);

        List<HistoryRow> rows = capturedHistory(3);
        assertEquals(1L, rows.get(0).getDeviceTs());
        assertEquals(2L, rows.get(1).getDeviceTs());
        assertEquals(3L, rows.get(2).getDeviceTs());
        assertEquals(Integer.valueOf(10), rows.get(0).getBatteryPct());
    }

    @Test
    public void onlyNewestOfBatchUpdatesLatest() {
        service.store(device, batch(report(3, 30), report(1, 10), report(2, 20)), R);

        LatestRow latest = capturedLatest();
        assertEquals(Integer.valueOf(30), latest.getBatteryPct());
        assertEquals("{\"ts\":3}", latest.getReportJson());
        assertEquals(ClockCorrection.measuredAt(R, SENT, 3), latest.getMeasuredAt());
    }

    @Test
    public void receivedAlwaysTouched() {
        service.store(device, batch(report(1, 10)), R);

        verify(mapper).touchReceived(DEVICE_ID, R);
    }

    @Test
    public void writesHappenInHistoryLatestTouchOrder() {
        service.store(device, batch(report(2, 20), report(1, 10)), R);

        InOrder order = inOrder(mapper);
        order.verify(mapper, times(2)).insertHistory(any(HistoryRow.class));
        order.verify(mapper).upsertLatest(any(LatestRow.class));
        order.verify(mapper).touchReceived(DEVICE_ID, R);
    }

    @Test
    public void rowsCarryDeviceTimesAndClockCorrection() {
        ParsedReport r = report(SENT - 300_000L, 55);
        r.setCharging(true);
        r.setTemperatureC(31.5f);
        r.setRssiDbm(-61);
        r.setKioskRunning(true);
        r.setKioskAdminUnlocked(true);
        service.store(device, batch(r), R);

        HistoryRow h = capturedHistory(1).get(0);
        assertEquals(Integer.valueOf(DEVICE_ID), h.getDeviceId());
        assertEquals(SENT - 300_000L, h.getDeviceTs());
        assertEquals(R, h.getReceivedAt());
        assertEquals(R - 300_000L, h.getMeasuredAt());
        assertEquals(Integer.valueOf(55), h.getBatteryPct());
        assertEquals(Boolean.TRUE, h.getCharging());
        assertEquals(Float.valueOf(31.5f), h.getTemperatureC());
        assertEquals(Integer.valueOf(-61), h.getRssiDbm());
        assertEquals(Boolean.TRUE, h.getKioskRunning());
        assertEquals(Boolean.TRUE, h.getKioskAdminUnlocked());
    }

    @Test
    public void latestCarriesEveryColumn() {
        ParsedReport r = report(5, 87);
        r.setCharging(false);
        r.setTemperatureC(40.0f);
        r.setFreeStorageMb(2048);
        r.setRssiDbm(-70);
        r.setKioskRequired(true);
        r.setKioskRunning(false);
        r.setKioskAdminUnlocked(true);
        r.setKioskAppPackage("com.hpmoqa.tablet_client");
        r.setKioskAppVersion("1.2.8");
        r.setAgentVersion("6.40-l24.5");
        r.setLockerShortUid("HP01");
        r.setManufacturer("samsung");
        r.setModel("SM-X110");
        service.store(device, batch(r), R);

        LatestRow l = capturedLatest();
        assertEquals(Integer.valueOf(DEVICE_ID), l.getDeviceId());
        assertEquals(Integer.valueOf(CUSTOMER_ID), l.getCustomerId());
        assertEquals(R, l.getReceivedAt());
        assertEquals(Integer.valueOf(87), l.getBatteryPct());
        assertEquals(Boolean.FALSE, l.getCharging());
        assertEquals(Float.valueOf(40.0f), l.getTemperatureC());
        assertEquals(Integer.valueOf(2048), l.getFreeStorageMb());
        assertEquals(Integer.valueOf(-70), l.getRssiDbm());
        assertEquals(Boolean.TRUE, l.getKioskRequired());
        assertEquals(Boolean.FALSE, l.getKioskRunning());
        assertEquals(Boolean.TRUE, l.getKioskAdminUnlocked());
        assertEquals("com.hpmoqa.tablet_client", l.getKioskAppPackage());
        assertEquals("1.2.8", l.getKioskAppVersion());
        assertEquals("6.40-l24.5", l.getAgentVersion());
        assertEquals("HP01", l.getLockerShortUid());
        assertEquals("samsung", l.getManufacturer());
        assertEquals("SM-X110", l.getModel());
        assertEquals("{\"ts\":5}", l.getReportJson());
    }

    @Test
    public void missingAppValuesLeaveAppColumnsNull() {
        ParsedBatch parsed = ReportParser.parse(
                "{\"sent_at\":1000,\"reports\":[{\"v\":1,\"ts\":900,\"power\":{\"battery_pct\":80}}]}");
        service.store(device, parsed, R);

        LatestRow l = capturedLatest();
        assertNull(l.getLockerShortUid());
        assertNull(l.getKioskAdminUnlocked());
        assertTrue(l.getReportJson().contains("\"app\":{"));
    }

    @Test
    public void equalTsKeepsBatchOrderAndLastWins() {
        service.store(device, batch(report(5, 10), report(5, 20)), R);

        List<HistoryRow> rows = capturedHistory(2);
        assertEquals(Integer.valueOf(10), rows.get(0).getBatteryPct());
        assertEquals(Integer.valueOf(20), rows.get(1).getBatteryPct());
        assertEquals(Integer.valueOf(20), capturedLatest().getBatteryPct());
    }

    @Test
    public void unsetClockReportNeverBecomesNewest() {
        long realTs = 1_763_290_000_000L;
        service.store(device, new ParsedBatch(60_000L,
                Arrays.asList(report(realTs, 50), report(45_000L, 40))), R);

        LatestRow l = capturedLatest();
        assertEquals(Integer.valueOf(50), l.getBatteryPct());
        assertEquals(R, l.getMeasuredAt());
    }

    @Test
    public void returnsNumberOfNewHistoryRows() {
        when(mapper.insertHistory(any(HistoryRow.class))).thenReturn(1, 0, 1);

        int stored = service.store(device, batch(report(1, 10), report(2, 20), report(3, 30)), R);

        assertEquals(2, stored);
    }

    @Test
    public void duplicateOnlyBatchStillTouchesAndOffersLatest() {
        when(mapper.insertHistory(any(HistoryRow.class))).thenReturn(0);

        int stored = service.store(device, batch(report(1, 10)), R);

        assertEquals(0, stored);
        verify(mapper).upsertLatest(any(LatestRow.class));
        verify(mapper).touchReceived(DEVICE_ID, R);
    }
}
