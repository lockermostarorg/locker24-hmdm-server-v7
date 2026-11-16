package com.hmdm.plugins.locker24health.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdm.persistence.domain.User;
import com.hmdm.persistence.domain.UserRole;
import com.hmdm.persistence.domain.UserRolePermission;
import com.hmdm.plugins.locker24health.persistence.HealthDAO;
import com.hmdm.plugins.locker24health.persistence.domain.HistoryRow;
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
        assertEquals(rows, r.getData());
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
        verify(dao, never()).historyPoints(anyInt(), anyLong(), anyLong());
        verify(dao, never()).receivedTimes(anyInt(), anyLong(), anyLong());
    }

    private void accessible() {
        when(dao.canAccessDevice(3, 7, 5)).thenReturn(true);
    }

    @Test
    public void hoursAreClampedToOneThroughSevenHundredTwenty() {
        login(true, PLUGIN);
        accessible();
        resource.history(5, 99999);
        verify(dao).historyPoints(5, NOW - 720 * HOUR, NOW);
        resource.history(5, 0);
        verify(dao).historyPoints(5, NOW - HOUR, NOW);
        resource.history(5, null);
        verify(dao).historyPoints(5, NOW - 24 * HOUR, NOW);
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

    @Test
    public void historyReturnsTypedPointsAndOfflinePeriods() {
        login(true, PLUGIN);
        accessible();
        long from = NOW - 2 * HOUR;
        when(dao.historyPoints(5, from, NOW)).thenReturn(Arrays.asList(point(from + 60_000, 50), point(from + 120_000, 49)));
        when(dao.receivedTimes(5, from, NOW)).thenReturn(Arrays.asList(from + 60_000, from + 120_000));
        when(dao.lastReceivedBefore(5, from)).thenReturn(from - 60_000);
        HistoryResponse h = (HistoryResponse) resource.history(5, 2).getData();
        assertEquals(2, h.getPoints().size());
        assertEquals(from + 60_000, h.getPoints().get(0).getT());
        assertEquals(Integer.valueOf(50), h.getPoints().get(0).getBatteryPct());
        assertEquals(1, h.getOfflinePeriods().size());
        assertEquals(from + 120_000, h.getOfflinePeriods().get(0).getFrom());
        assertEquals(NOW, h.getOfflinePeriods().get(0).getTo());
        assertEquals(0L, h.getBucketMs());
    }

    @Test
    public void deviceWithNoReportsEverHasNoOfflinePeriods() {
        login(true, PLUGIN);
        accessible();
        when(dao.receivedTimes(5, NOW - 24 * HOUR, NOW)).thenReturn(Collections.<Long>emptyList());
        when(dao.lastReceivedBefore(5, NOW - 24 * HOUR)).thenReturn(null);
        HistoryResponse h = (HistoryResponse) resource.history(5, 24).getData();
        assertTrue(h.getOfflinePeriods().isEmpty());
        assertTrue(h.getPoints().isEmpty());
    }

    @Test
    public void deviceThatReportedEarlierButNotInTheRangeIsOfflineForTheRange() {
        login(true, PLUGIN);
        accessible();
        when(dao.receivedTimes(5, NOW - 24 * HOUR, NOW)).thenReturn(Collections.<Long>emptyList());
        when(dao.lastReceivedBefore(5, NOW - 24 * HOUR)).thenReturn(NOW - 30 * HOUR);
        HistoryResponse h = (HistoryResponse) resource.history(5, 24).getData();
        assertEquals(1, h.getOfflinePeriods().size());
        assertEquals(NOW - 24 * HOUR, h.getOfflinePeriods().get(0).getFrom());
        assertEquals(NOW, h.getOfflinePeriods().get(0).getTo());
    }

    @Test
    public void firstEverReportDoesNotInventAnOfflineStretchBeforeIt() {
        login(true, PLUGIN);
        accessible();
        long from = NOW - 24 * HOUR;
        List<Long> received = new ArrayList<>();
        for (int i = 12; i >= 1; i--) {
            received.add(NOW - i * 300_000L);
        }
        when(dao.receivedTimes(5, from, NOW)).thenReturn(received);
        when(dao.lastReceivedBefore(5, from)).thenReturn(null);
        HistoryResponse h = (HistoryResponse) resource.history(5, 24).getData();
        assertTrue(h.getOfflinePeriods().isEmpty());
    }

    @Test
    public void largeHistoryIsDownsampled() {
        login(true, PLUGIN);
        accessible();
        long from = NOW - 720 * HOUR;
        List<HistoryRow> rows = new ArrayList<>();
        for (int i = 0; i < 5000; i++) {
            rows.add(point(from + i * 300_000L, 50));
        }
        when(dao.historyPoints(5, from, NOW)).thenReturn(rows);
        when(dao.receivedTimes(5, from, NOW)).thenReturn(Collections.<Long>emptyList());
        HistoryResponse h = (HistoryResponse) resource.history(5, 720).getData();
        assertTrue(h.getPoints().size() <= HealthResource.MAX_POINTS);
        assertTrue(h.getBucketMs() > 0);
    }
}
