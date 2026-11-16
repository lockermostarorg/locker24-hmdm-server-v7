package com.hmdm.plugins.locker24health.persistence;

import com.hmdm.plugins.locker24health.alert.AlertDecision;
import com.hmdm.plugins.locker24health.alert.AlertEvent;
import com.hmdm.plugins.locker24health.alert.OpenAlert;
import com.hmdm.plugins.locker24health.persistence.domain.AlertRow;
import com.hmdm.plugins.locker24health.persistence.domain.EvalRow;
import com.hmdm.plugins.locker24health.persistence.mapper.HealthMapper;
import com.hmdm.plugins.locker24health.status.Condition;
import com.hmdm.plugins.locker24health.status.Evaluation;
import com.hmdm.plugins.locker24health.status.Finding;
import com.hmdm.plugins.locker24health.status.Severity;
import com.hmdm.plugins.locker24health.status.Status;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class HealthDAOAlertTest {

    private static final long NOW = 1_763_290_060_000L;

    private HealthMapper mapper;
    private HealthDAO dao;
    private EvalRow row;

    @Before
    public void setUp() {
        mapper = mock(HealthMapper.class);
        dao = new HealthDAO(mapper);
        row = new EvalRow();
        row.setDeviceId(5);
        row.setCustomerId(1);
        row.setDeviceNumber("M-01");
        row.setConfigurationName("hpmo-qa");
        row.setLockerShortUid("HP01");
    }

    private static Evaluation eval(Status s, Finding... f) {
        return new Evaluation(s, List.of(f), 777L);
    }

    private static AlertDecision decision(List<Finding> raise, List<Finding> escalate, List<OpenAlert> touch,
                                          List<OpenAlert> clear) {
        return new AlertDecision(raise, escalate, touch, clear, 555L);
    }

    @Test
    public void raiseInsertsAndEmitsAfterWritingTheEvaluation() {
        Finding offline = new Finding(Condition.OFFLINE, Severity.RED, "no report for 11 min");
        when(mapper.insertAlert(5, 1, "offline", "red", NOW)).thenReturn(1);
        List<AlertEvent> events = dao.applyEvaluation(row, eval(Status.RED, offline),
                decision(List.of(offline), List.of(), List.of(), List.of()), Map.of(), NOW);
        verify(mapper).updateEvaluation(5, "red", "offline", 555L, 777L, NOW);
        assertEquals(List.of(new AlertEvent(1, 5, "M-01", "hpmo-qa", "HP01", Condition.OFFLINE, Severity.RED, false,
                "no report for 11 min", NOW, null)), events);
    }

    @Test
    public void insertConflictMeansAnotherRunWon() {
        Finding offline = new Finding(Condition.OFFLINE, Severity.RED, "x");
        when(mapper.insertAlert(5, 1, "offline", "red", NOW)).thenReturn(0);
        assertTrue(dao.applyEvaluation(row, eval(Status.RED, offline),
                decision(List.of(offline), List.of(), List.of(), List.of()), Map.of(), NOW).isEmpty());
    }

    @Test
    public void escalateUpdatesTheOpenRowAndEmitsARedRaise() {
        Finding red = new Finding(Condition.BATTERY_LOW, Severity.RED, "battery 9 %");
        OpenAlert open = new OpenAlert(11, Condition.BATTERY_LOW, Severity.YELLOW, NOW - 3_600_000, NOW - 60_000);
        when(mapper.escalateAlert(11, NOW)).thenReturn(1);
        List<AlertEvent> events = dao.applyEvaluation(row, eval(Status.RED, red),
                decision(List.of(), List.of(red), List.of(), List.of()), Map.of(Condition.BATTERY_LOW, open), NOW);
        verify(mapper).escalateAlert(11, NOW);
        assertEquals(1, events.size());
        assertEquals(Severity.RED, events.get(0).severity());
        assertFalse(events.get(0).cleared());
        assertEquals("battery 9 %", events.get(0).detail());
    }

    @Test
    public void escalateThatLostTheRaceEmitsNothing() {
        Finding red = new Finding(Condition.BATTERY_LOW, Severity.RED, "battery 9 %");
        OpenAlert open = new OpenAlert(11, Condition.BATTERY_LOW, Severity.YELLOW, NOW - 3_600_000, NOW - 60_000);
        when(mapper.escalateAlert(11, NOW)).thenReturn(0);
        assertTrue(dao.applyEvaluation(row, eval(Status.RED, red),
                decision(List.of(), List.of(red), List.of(), List.of()), Map.of(Condition.BATTERY_LOW, open), NOW).isEmpty());
    }

    @Test
    public void touchSetsLastPresentToTheTick() {
        OpenAlert open = new OpenAlert(12, Condition.NOT_CHARGING, Severity.YELLOW, NOW - 3_600_000, NOW - 60_000);
        Finding f = new Finding(Condition.NOT_CHARGING, Severity.YELLOW, "not charging");
        assertTrue(dao.applyEvaluation(row, eval(Status.YELLOW, f),
                decision(List.of(), List.of(), List.of(open), List.of()), Map.of(Condition.NOT_CHARGING, open), NOW).isEmpty());
        verify(mapper).touchAlert(12, NOW);
    }

    @Test
    public void clearEmitsOnlyWhenTheRowWasStillOpen() {
        OpenAlert open = new OpenAlert(13, Condition.NOT_CHARGING, Severity.YELLOW, NOW - 3_600_000, NOW - 600_000);
        when(mapper.clearAlert(13, NOW)).thenReturn(1);
        List<AlertEvent> events = dao.applyEvaluation(row, eval(Status.GREEN),
                decision(List.of(), List.of(), List.of(), List.of(open)), Map.of(Condition.NOT_CHARGING, open), NOW);
        verify(mapper).updateEvaluation(5, "green", null, 555L, 777L, NOW);
        assertEquals(1, events.size());
        assertTrue(events.get(0).cleared());
        assertEquals(Condition.NOT_CHARGING, events.get(0).condition());
        assertEquals(Severity.YELLOW, events.get(0).severity());

        when(mapper.clearAlert(13, NOW)).thenReturn(0);
        assertTrue(dao.applyEvaluation(row, eval(Status.GREEN),
                decision(List.of(), List.of(), List.of(), List.of(open)), Map.of(Condition.NOT_CHARGING, open), NOW).isEmpty());
    }

    @Test
    public void reasonsListEveryCodeOnceAndStayBounded() {
        assertNull(HealthDAO.reasons(List.of()));
        assertEquals("kiosk_admin_off,not_charging", HealthDAO.reasons(List.of(
                new Finding(Condition.KIOSK_ADMIN_OFF, Severity.YELLOW, "a"),
                new Finding(Condition.NOT_CHARGING, Severity.YELLOW, "b"),
                new Finding(Condition.NOT_CHARGING, Severity.YELLOW, "b"))));
        Finding[] all = Arrays.stream(Condition.values()).map(c -> new Finding(c, Severity.YELLOW, "")).toArray(Finding[]::new);
        assertTrue(HealthDAO.reasons(List.of(all)).length() <= HealthDAO.MAX_REASONS_LENGTH);
    }

    @Test
    public void openAlertsAreKeyedByDeviceAndCondition() {
        when(mapper.selectOpenAlerts()).thenReturn(List.of(
                alertRow(1, 5, "offline", "red"),
                alertRow(2, 5, "battery_low", "yellow"),
                alertRow(3, 6, "something_new", "red"),
                alertRow(4, 6, "not_charging", "purple")));
        Map<Integer, Map<Condition, OpenAlert>> open = dao.loadOpenAlerts();
        assertEquals(1, open.size());
        assertEquals(Severity.RED, open.get(5).get(Condition.OFFLINE).severity());
        assertEquals(2L, open.get(5).get(Condition.BATTERY_LOW).id());
        assertEquals(1_000L, open.get(5).get(Condition.BATTERY_LOW).raisedAt());
        assertEquals(2_000L, open.get(5).get(Condition.BATTERY_LOW).lastPresentAt());
    }

    private static AlertRow alertRow(long id, int device, String condition, String severity) {
        AlertRow r = new AlertRow();
        r.setId(id);
        r.setDeviceId(device);
        r.setCustomerId(1);
        r.setCondition(condition);
        r.setSeverity(severity);
        r.setRaisedAt(1_000L);
        r.setLastPresentAt(2_000L);
        return r;
    }
}
