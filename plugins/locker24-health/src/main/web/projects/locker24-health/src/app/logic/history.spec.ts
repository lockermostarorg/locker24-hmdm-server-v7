import { THistoryPoint } from '../types/history.type';
import { hasValues, newestFirst, parseHours, series } from './history';

function point(t: number, batteryPct: number | null): THistoryPoint {
  return { t, batteryPct, temperatureC: null, rssiDbm: null, charging: true, kioskRunning: true };
}

describe('history', () => {
  it('accepts hours 1..720 and falls back to 24', () => {
    expect(parseHours('168')).toBe(168);
    expect(parseHours('720')).toBe(720);
    expect(parseHours('1')).toBe(1);
    expect(parseHours('0')).toBe(24);
    expect(parseHours('721')).toBe(24);
    expect(parseHours('1.5')).toBe(24);
    expect(parseHours(null)).toBe(24);
  });

  it('drops missing values and breaks the line inside offline periods', () => {
    const points = [point(1000, 50), point(2000, null), point(9000, 40)];
    expect(series(points, 'batteryPct', [{ from: 3000, to: 8000 }])).toEqual([
      { x: 1000, y: 50 },
      { x: 3000, y: null },
      { x: 9000, y: 40 },
    ]);
  });

  it('adds no break for an offline period at the edge of the data', () => {
    const points = [point(1000, 50), point(2000, 40)];
    expect(series(points, 'batteryPct', [{ from: 3000, to: 8000 }])).toEqual([
      { x: 1000, y: 50 },
      { x: 2000, y: 40 },
    ]);
  });

  it('reports whether a series has values', () => {
    expect(hasValues([point(1, null)], 'batteryPct')).toBeFalse();
    expect(hasValues([point(1, 3)], 'batteryPct')).toBeTrue();
    expect(hasValues([point(1, 3)], 'rssiDbm')).toBeFalse();
  });

  it('lists offline periods newest first without mutating the input', () => {
    const periods = [
      { from: 1, to: 2 },
      { from: 5, to: 6 },
    ];
    expect(newestFirst(periods).map((p) => p.from)).toEqual([5, 1]);
    expect(periods[0].from).toBe(1);
  });
});
