import {
  absoluteTime,
  alignedTicks,
  tickLabel,
  tickStepMs,
  zoneOffsetMs,
  formatDuration,
  formatPercent,
  formatStorage,
  formatTemperature,
  relativeTime,
} from './format';

describe('format', () => {
  const now = Date.UTC(2026, 10, 16, 12, 0, 0);

  it('formats relative times', () => {
    expect(relativeTime(null, now).key).toBe('plugin.locker24-health.time.never');
    expect(relativeTime(now - 30_000, now).key).toBe('plugin.locker24-health.time.just_now');
    expect(relativeTime(now + 30_000, now).key).toBe('plugin.locker24-health.time.just_now');
    expect(relativeTime(now - 5 * 60_000, now)).toEqual({
      key: 'plugin.locker24-health.time.minutes_ago',
      count: 5,
    });
    expect(relativeTime(now - 3 * 3_600_000 - 1, now)).toEqual({
      key: 'plugin.locker24-health.time.hours_ago',
      count: 3,
    });
    expect(relativeTime(now - 2 * 86_400_000, now)).toEqual({
      key: 'plugin.locker24-health.time.days_ago',
      count: 2,
    });
  });

  it('formats absolute times in the given zone', () => {
    expect(absoluteTime(now, 'Europe/Sarajevo')).toContain('16/11/2026, 13:00:00');
    expect(absoluteTime(now, 'UTC')).toContain('12:00:00');
    expect(absoluteTime(null, 'UTC')).toBe('');
  });

  it('falls back to the browser zone for an invalid zone', () => {
    expect(absoluteTime(now, 'Not/AZone')).toContain('2026');
  });

  it('formats durations', () => {
    expect(formatDuration(0)).toBe('0 min');
    expect(formatDuration(12 * 60_000)).toBe('12 min');
    expect(formatDuration(2 * 3_600_000 + 5 * 60_000)).toBe('2 h 5 min');
    expect(formatDuration(3 * 3_600_000)).toBe('3 h');
    expect(formatDuration(3 * 86_400_000 + 4 * 3_600_000 + 59_000)).toBe('3 d 4 h');
  });

  it('formats metric values and leaves missing ones empty', () => {
    expect(formatPercent(55)).toBe('55 %');
    expect(formatPercent(null)).toBe('');
    expect(formatTemperature(41.25)).toBe('41.3 °C');
    expect(formatTemperature(undefined)).toBe('');
    expect(formatStorage(900)).toBe('900 MB');
    expect(formatStorage(3277)).toBe('3.2 GB');
  });

  it('measures the zone offset including summer time', () => {
    expect(zoneOffsetMs(Date.UTC(2026, 0, 15), 'Europe/Sarajevo')).toBe(3_600_000);
    expect(zoneOffsetMs(Date.UTC(2026, 6, 15), 'Europe/Sarajevo')).toBe(7_200_000);
    expect(zoneOffsetMs(Date.UTC(2026, 6, 15), 'UTC')).toBe(0);
  });

  it('picks a tick step per range', () => {
    expect(tickStepMs(24 * 3_600_000)).toBe(3 * 3_600_000);
    expect(tickStepMs(168 * 3_600_000)).toBe(86_400_000);
    expect(tickStepMs(720 * 3_600_000)).toBe(5 * 86_400_000);
  });

  it('aligns ticks to whole hours and local midnights of the zone', () => {
    const to = Date.UTC(2026, 10, 16, 11, 17);
    const day = alignedTicks(to - 24 * 3_600_000, to, 'Europe/Sarajevo');
    expect(day.length).toBe(8);
    expect(day.map((t) => tickLabel(t, 'Europe/Sarajevo', 3 * 3_600_000))[0]).toBe('15:00');
    const week = alignedTicks(to - 168 * 3_600_000, to, 'Europe/Sarajevo');
    expect(week.map((t) => tickLabel(t, 'Europe/Sarajevo', 3 * 3_600_000))).toEqual(
      Array(7).fill('00:00'),
    );
    expect(tickLabel(week[0], 'Europe/Sarajevo', 86_400_000)).toBe('10/11');
  });
});
