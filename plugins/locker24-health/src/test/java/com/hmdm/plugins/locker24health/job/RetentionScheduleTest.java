package com.hmdm.plugins.locker24health.job;

import org.junit.Test;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.Assert.assertEquals;

public class RetentionScheduleTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Sarajevo");
    private static final LocalTime AT = LocalTime.of(3, 30);

    private static ZonedDateTime at(int month, int day, int h, int m, int s) {
        return ZonedDateTime.of(2026, month, day, h, m, s, 0, ZONE);
    }

    @Test
    public void oneMinuteBefore() {
        assertEquals(1, RetentionSchedule.initialDelayMinutes(at(11, 16, 3, 29, 0), AT));
    }

    @Test
    public void exactlyAtRunsTomorrow() {
        assertEquals(1440, RetentionSchedule.initialDelayMinutes(at(11, 16, 3, 30, 0), AT));
    }

    @Test
    public void lateEvening() {
        assertEquals(270, RetentionSchedule.initialDelayMinutes(at(11, 16, 23, 0, 0), AT));
    }

    @Test
    public void partialMinuteRoundsUp() {
        assertEquals(1, RetentionSchedule.initialDelayMinutes(at(11, 16, 3, 29, 30), AT));
    }

    @Test
    public void springForwardNightIsShorter() {
        assertEquals(90, RetentionSchedule.initialDelayMinutes(at(3, 29, 1, 0, 0), AT));
    }

    @Test
    public void defaultTimeIsHalfPastThree() {
        assertEquals(AT, RetentionSchedule.AT);
    }
}
