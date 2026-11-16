package com.hmdm.plugins.locker24health.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdm.persistence.domain.User;
import com.hmdm.persistence.domain.UserRole;
import com.hmdm.persistence.domain.UserRolePermission;
import com.hmdm.plugins.locker24health.persistence.HealthDAO;
import com.hmdm.plugins.locker24health.persistence.domain.HistoryRow;
import com.hmdm.plugins.locker24health.persistence.domain.GapRow;
import com.hmdm.plugins.locker24health.persistence.domain.ReceivedBounds;
import com.hmdm.plugins.locker24health.persistence.mapper.HealthMapper;
import com.hmdm.plugins.locker24health.rest.json.FleetResponse;
import com.hmdm.plugins.locker24health.rest.json.FleetRow;
import com.hmdm.plugins.locker24health.rest.json.HealthSettings;
import com.hmdm.plugins.locker24health.rest.json.HistoryResponse;
import com.hmdm.rest.json.Response;
import com.hmdm.security.SecurityContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class HealthResourceTest {

    private static final long NOW = 1_763_290_000_000L;
    private static final long HOUR = 3_600_000L;
    private static final String PLUGIN = "plugin_locker24_health_access";

    private HealthDAO dao;
    private HealthResource resource;

    @Before
    public void setUp() {
        dao = mock(HealthDAO.class);
        resource = new HealthResource(dao, () -> NOW);
    }

    @After
    public void tearDown() {
        SecurityContext.release();
    }

    private static void login(boolean allDevices, String... permissions) {
        User u = new User();
        u.setId(7);
        u.setCustomerId(3);
        u.setAllDevicesAvailable(allDevices);
        UserRole role = new UserRole();
        List<UserRolePermission> list = new ArrayList<>();
        for (String p : permissions) {
            UserRolePermission perm = new UserRolePermission();
            perm.setName(p);
            list.add(perm);
        }
        role.setPermissions(list);
        u.setUserRole(role);
        SecurityContext.init(u);
    }

    private static void assertDenied(Response r) {
        assertEquals(Response.PERMISSION_DENIED().getMessage(), r.getMessage());
        assertEquals(Response.ResponseStatus.ERROR, r.getStatus());
    }

    @Test
    public void everyEndpointNeedsThePluginPermission() {
        login(true, "settings");
        assertDenied(resource.devices(null, null, null));
        assertDenied(resource.history(5, 24));
        assertDenied(resource.getSettings());
        HealthSettings put = new HealthSettings();
        put.setTimeZone("Europe/Sarajevo");
        assertDenied(resource.saveSettings(put));
        verifyZeroInteractions(dao);
    }

    @Test
    public void saveSettingsNeedsTheCoreSettingsPermissionToo() {
        login(true, PLUGIN);
        HealthSettings put = new HealthSettings();
        put.setWebhookUrl("https://hooks.slack.com/services/x");
        assertDenied(resource.saveSettings(put));
        verify(dao, never()).saveSettings(anyInt(), any(HealthSettings.class));
    }

    @Test
    public void saveSettingsStoresForTheCallersCustomer() {
        login(false, PLUGIN, "settings");
        HealthSettings put = new HealthSettings();
        put.setWebhookUrl("https://hooks.slack.com/services/x");
        put.setTimeZone("Europe/Sarajevo");
        Response r = resource.saveSettings(put);
        assertEquals(Response.ResponseStatus.OK, r.getStatus());
        verify(dao).saveSettings(3, put);
        assertNull(((HealthSettings) r.getData()).getWebhookUrl());
    }

    @Test
    public void invalidSettingsAreRejectedAndNotStored() {
        login(true, PLUGIN, "settings");
        HealthSettings put = new HealthSettings();
        put.setWebhookUrl("http://insecure");
        Response r = resource.saveSettings(put);
        assertEquals(Response.ResponseStatus.ERROR, r.getStatus());
        assertEquals("error.validation", r.getMessage());
        verify(dao, never()).saveSettings(anyInt(), any(HealthSettings.class));
    }

    @Test
    public void getSettingsNeverCarriesTheWebhookUrl() throws Exception {
        login(true, PLUGIN);
        HealthSettings stored = new HealthSettings();
        stored.setWebhookUrl("https://hooks.slack.com/services/SECRET");
        stored.setWebhookUrlSet(true);
        stored.setTimeZone("Europe/Sarajevo");
        when(dao.loadSettings(3)).thenReturn(stored);
        Response r = resource.getSettings();
        String json = new ObjectMapper().writeValueAsString(r);
        assertFalse(json, json.contains("SECRET"));
        assertFalse(json, json.contains("hooks.slack.com"));
        HealthSettings out = (HealthSettings) r.getData();
        assertNull(out.getWebhookUrl());
        assertEquals(Boolean.TRUE, out.getWebhookUrlSet());
        assertEquals("Europe/Sarajevo", out.getTimeZone());
        assertEquals(Boolean.FALSE, out.getEditable());
    }

    @Test
    public void getSettingsWithoutARowReturnsDefaults() {
        login(true, PLUGIN, "settings");
        when(dao.loadSettings(3)).thenReturn(null);
        HealthSettings out = (HealthSettings) resource.getSettings().getData();
        assertEquals(Boolean.FALSE, out.getWebhookUrlSet());
        assertEquals("Europe/Sarajevo", out.getTimeZone());
        assertEquals(Boolean.TRUE, out.getEditable());
    }

    @Test
    public void fleetIsScopedToTheCallersCustomerAndUser() {
        login(false, PLUGIN);
        List<FleetRow> rows = Collections.singletonList(new FleetRow());
        when(dao.loadFleet(3, 7, 9, "red", "Tab A9")).thenReturn(rows);
        Response r = resource.devices(9, "red", "Tab A9");
        assertEquals(rows, ((FleetResponse) r.getData()).getDevices());
        assertFalse(((FleetResponse) r.getData()).isTruncated());
    }

    @Test
    public void fleetOverTheCapIsTrimmedAndFlagged() {
        login(true, PLUGIN);
        List<FleetRow> rows = new ArrayList<>();
        for (int i = 0; i <= HealthMapper.FLEET_LIMIT; i++) {
            rows.add(new FleetRow());
        }
        when(dao.loadFleet(3, 7, null, null, null)).thenReturn(rows);
        FleetResponse out = (FleetResponse) resource.devices(null, null, null).getData();
        assertEquals(HealthMapper.FLEET_LIMIT, out.getDevices().size());
        assertTrue(out.isTruncated());
    }

    @Test
    public void blankFiltersAreTreatedAsAbsent() {
        login(true, PLUGIN);
        resource.devices(null, "", " ");
        verify(dao).loadFleet(3, 7, null, null, null);
    }

    @Test
    public void unknownStatusFilterIsRejected() {
        login(true, PLUGIN);
        Response r = resource.devices(null, "purple", null);
        assertEquals("error.validation", r.getMessage());
        verify(dao, never()).loadFleet(anyInt(), anyInt(), any(Integer.class), any(String.class), any(String.class));
    }

    @Test
    public void historyOfAnInaccessibleDeviceIsDeniedAndNothingIsRead() {
        login(false, PLUGIN);
        when(dao.canAccessDevice(3, 7, 5)).thenReturn(false);
        assertDenied(resource.history(5, 24));
        verify(dao, never()).historyPoints(anyInt(), anyLong(), anyLong(), anyLong());
        verify(dao, never()).offlineGaps(anyInt(), anyLong(), anyLong(), anyLong());
    }

    private void accessible() {
        when(dao.canAccessDevice(3, 7, 5)).thenReturn(true);
    }

    @Test
    public void hoursAreClampedToOneThroughSevenHundredTwenty() {
        login(true, PLUGIN);
        accessible();
        resource.history(5, 99999);
        verify(dao).historyPoints(5, NOW - 720 * HOUR, NOW, 2_592_000L);
        resource.history(5, 0);
        verify(dao).historyPoints(5, NOW - HOUR, NOW, 3_600L);
        resource.history(5, null);
        verify(dao).historyPoints(5, NOW - 24 * HOUR, NOW, 86_400L);
    }

    private static HistoryRow point(long t, int battery) {
        HistoryRow r = new HistoryRow();
        r.setMeasuredAt(t);
        r.setBatteryPct(battery);
        r.setCharging(true);
        r.setTemperatureC(31.5f);
        r.setRssiDbm(-60);
        r.setKioskRunning(true);
        return r;
    }

    private static ReceivedBounds bounds(Long first, Long last) {
        ReceivedBounds b = new ReceivedBounds();
        b.setFirst(first);
        b.setLast(last);
        return b;
    }

    private static GapRow gap(long from, long to) {
        GapRow g = new GapRow();
        g.setFromMs(from);
        g.setToMs(to);
        return g;
    }

    @Test
    public void historyReturnsTypedPointsAndOfflinePeriods() {
        login(true, PLUGIN);
        accessible();
        long from = NOW - 2 * HOUR;
        long bucket = 7_200L;
        when(dao.historyPoints(5, from, NOW, bucket)).thenReturn(Arrays.asList(point(from + 60_000, 50), point(from + 120_000, 49)));
        when(dao.offlineGaps(eq(5), eq(from), eq(NOW), anyLong())).thenReturn(Arrays.asList(gap(from - 60_000, from + 60_000)));
        when(dao.receivedBounds(5, from, NOW)).thenReturn(bounds(from + 60_000L, NOW - 60_000L));
        when(dao.lastReceivedBefore(5, from)).thenReturn(from - 60_000);
        HistoryResponse h = (HistoryResponse) resource.history(5, 2).getData();
        assertEquals(2, h.getPoints().size());
        assertEquals(from + 60_000, h.getPoints().get(0).getT());
        assertEquals(Integer.valueOf(50), h.getPoints().get(0).getBatteryPct());
        assertEquals(1, h.getOfflinePeriods().size());
        assertEquals(from, h.getOfflinePeriods().get(0).getFrom());
        assertEquals(from + 60_000, h.getOfflinePeriods().get(0).getTo());
        assertEquals(bucket, h.getBucketMs());
        assertFalse(h.isTruncated());
    }

    @Test
    public void deviceWithNoReportsEverHasNoOfflinePeriods() {
        login(true, PLUGIN);
        accessible();
        when(dao.lastReceivedBefore(anyInt(), anyLong())).thenReturn(null);
        when(dao.latestReceived(5)).thenReturn(null);
        HistoryResponse h = (HistoryResponse) resource.history(5, 24).getData();
        assertTrue(h.getOfflinePeriods().isEmpty());
        assertTrue(h.getPoints().isEmpty());
    }

    @Test
    public void deviceWhosHistoryWasPurgedFallsBackToItsLatestReport() {
        login(true, PLUGIN);
        accessible();
        when(dao.lastReceivedBefore(anyInt(), anyLong())).thenReturn(null);
        when(dao.latestReceived(5)).thenReturn(NOW - 40 * 24 * HOUR);
        HistoryResponse h = (HistoryResponse) resource.history(5, 24).getData();
        assertEquals(1, h.getOfflinePeriods().size());
        assertEquals(NOW - 24 * HOUR, h.getOfflinePeriods().get(0).getFrom());
        assertEquals(NOW, h.getOfflinePeriods().get(0).getTo());
    }

    @Test
    public void deviceThatReportedEarlierButNotInTheRangeIsOfflineForTheRange() {
        login(true, PLUGIN);
        accessible();
        when(dao.lastReceivedBefore(5, NOW - 24 * HOUR)).thenReturn(NOW - 30 * HOUR);
        HistoryResponse h = (HistoryResponse) resource.history(5, 24).getData();
        assertEquals(1, h.getOfflinePeriods().size());
        assertEquals(NOW - 24 * HOUR, h.getOfflinePeriods().get(0).getFrom());
        assertEquals(NOW, h.getOfflinePeriods().get(0).getTo());
    }

    @Test
    public void tooManyRowsOrGapsSetTruncated() {
        login(true, PLUGIN);
        accessible();
        List<HistoryRow> rows = new ArrayList<>();
        for (int i = 0; i <= HealthMapper.POINT_LIMIT; i++) {
            rows.add(point(NOW - 1000 + i, 50));
        }
        when(dao.historyPoints(5, NOW - 24 * HOUR, NOW, 86_400L)).thenReturn(rows);
        HistoryResponse h = (HistoryResponse) resource.history(5, 24).getData();
        assertTrue(h.isTruncated());
        assertEquals(HealthMapper.POINT_LIMIT, h.getPoints().size());
        assertEquals(NOW - 1000 + HealthMapper.POINT_LIMIT, h.getPoints().get(HealthMapper.POINT_LIMIT - 1).getT());

        List<GapRow> gaps = new ArrayList<>();
        for (int i = 0; i <= HealthMapper.GAP_LIMIT; i++) {
            gaps.add(gap(i, i + 1));
        }
        when(dao.historyPoints(5, NOW - 24 * HOUR, NOW, 86_400L)).thenReturn(new ArrayList<HistoryRow>());
        when(dao.offlineGaps(eq(5), anyLong(), anyLong(), anyLong())).thenReturn(gaps);
        when(dao.receivedBounds(5, NOW - 24 * HOUR, NOW)).thenReturn(bounds(1L, NOW));
        assertTrue(((HistoryResponse) resource.history(5, 24).getData()).isTruncated());
    }

    @Test
    public void bucketCountNeverExceedsTheLimit() {
        login(true, PLUGIN);
        accessible();
        for (int h = 1; h <= 720; h++) {
            long range = h * HOUR;
            long bucket = Math.max(1L, (range + HealthResource.MAX_POINTS - 1) / HealthResource.MAX_POINTS);
            assertTrue(h + "h", (range - 1) / bucket + 1 <= HealthResource.MAX_POINTS);
        }
    }
}
