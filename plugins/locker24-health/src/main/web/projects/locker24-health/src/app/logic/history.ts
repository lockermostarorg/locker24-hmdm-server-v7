import { THistoryPoint, TOfflinePeriod } from '../types/history.type';

export const RANGE_HOURS = [24, 168, 720] as const;
export const DEFAULT_HOURS = 24;

export type TSeriesKey = 'batteryPct' | 'temperatureC' | 'rssiDbm';

export type TXY = {
  x: number;
  y: number | null;
};

export function parseHours(value: string | null | undefined): number {
  const hours = Number(value);
  return Number.isInteger(hours) && hours >= 1 && hours <= 720 ? hours : DEFAULT_HOURS;
}

export function series(
  points: THistoryPoint[],
  key: TSeriesKey,
  offlinePeriods: TOfflinePeriod[],
): TXY[] {
  const values: TXY[] = points
    .filter((p) => p[key] !== null && p[key] !== undefined)
    .map((p) => ({ x: p.t, y: p[key] }));
  const breaks: TXY[] = offlinePeriods
    .filter(
      (period) => values.some((v) => v.x < period.from) && values.some((v) => v.x > period.from),
    )
    .map((period) => ({ x: period.from, y: null }));
  return [...values, ...breaks].sort((a, b) => a.x - b.x);
}

export function hasValues(points: THistoryPoint[], key: TSeriesKey): boolean {
  return points.some((p) => p[key] !== null && p[key] !== undefined);
}

export function newestFirst(periods: TOfflinePeriod[]): TOfflinePeriod[] {
  return [...periods].sort((a, b) => b.from - a.from);
}
