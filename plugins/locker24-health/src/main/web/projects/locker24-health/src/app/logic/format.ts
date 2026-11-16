const MINUTE = 60_000;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;

export type TRelativeTime = {
  key: string;
  count?: number;
};

export function relativeTime(ms: number | null | undefined, now: number): TRelativeTime {
  if (ms === null || ms === undefined) {
    return { key: 'plugin.locker24-health.time.never' };
  }
  const age = Math.max(0, now - ms);
  if (age < MINUTE) {
    return { key: 'plugin.locker24-health.time.just_now' };
  }
  if (age < HOUR) {
    return { key: 'plugin.locker24-health.time.minutes_ago', count: Math.floor(age / MINUTE) };
  }
  if (age < DAY) {
    return { key: 'plugin.locker24-health.time.hours_ago', count: Math.floor(age / HOUR) };
  }
  return { key: 'plugin.locker24-health.time.days_ago', count: Math.floor(age / DAY) };
}

const formatters = new Map<string, Intl.DateTimeFormat>();

function formatter(timeZone: string | null | undefined, withSeconds: boolean): Intl.DateTimeFormat {
  const cacheKey = `${timeZone ?? ''}|${withSeconds}`;
  let result = formatters.get(cacheKey);
  if (!result) {
    const options: Intl.DateTimeFormatOptions = {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: withSeconds ? '2-digit' : undefined,
      hourCycle: 'h23',
      timeZoneName: 'short',
    };
    try {
      result = new Intl.DateTimeFormat('en-GB', { ...options, timeZone: timeZone || undefined });
    } catch {
      result = new Intl.DateTimeFormat('en-GB', options);
    }
    formatters.set(cacheKey, result);
  }
  return result;
}

export function absoluteTime(
  ms: number | null | undefined,
  timeZone?: string | null,
  withSeconds = true,
): string {
  if (ms === null || ms === undefined) {
    return '';
  }
  return formatter(timeZone, withSeconds).format(new Date(ms));
}

export function zoneOffsetMs(ms: number, timeZone: string | null | undefined): number {
  try {
    const parts = new Intl.DateTimeFormat('en-US', {
      timeZone: timeZone || undefined,
      hourCycle: 'h23',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    }).formatToParts(new Date(ms));
    const part = (type: Intl.DateTimeFormatPartTypes) =>
      Number(parts.find((p) => p.type === type)?.value);
    const wallClock = Date.UTC(
      part('year'),
      part('month') - 1,
      part('day'),
      part('hour'),
      part('minute'),
      part('second'),
    );
    return wallClock - Math.floor(ms / 1000) * 1000;
  } catch {
    return -new Date(ms).getTimezoneOffset() * MINUTE;
  }
}

export function tickStepMs(rangeMs: number): number {
  const hours = rangeMs / HOUR;
  if (hours <= 6) {
    return HOUR;
  }
  if (hours <= 24) {
    return 3 * HOUR;
  }
  if (hours <= 72) {
    return 12 * HOUR;
  }
  if (hours <= 8 * 24) {
    return DAY;
  }
  return 5 * DAY;
}

export function alignedTicks(
  from: number,
  to: number,
  timeZone: string | null | undefined,
): number[] {
  const step = tickStepMs(to - from);
  const offset = zoneOffsetMs(from, timeZone);
  const ticks: number[] = [];
  for (let t = Math.ceil((from + offset) / step) * step - offset; t <= to; t += step) {
    ticks.push(t);
  }
  return ticks;
}

export function tickLabel(ms: number, timeZone: string | null | undefined, stepMs: number): string {
  const options: Intl.DateTimeFormatOptions =
    stepMs < DAY
      ? { hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }
      : { day: '2-digit', month: '2-digit' };
  try {
    return new Intl.DateTimeFormat('en-GB', { ...options, timeZone: timeZone || undefined }).format(
      new Date(ms),
    );
  } catch {
    return new Intl.DateTimeFormat('en-GB', options).format(new Date(ms));
  }
}

export function formatDuration(ms: number): string {
  const total = Math.max(0, Math.round(ms / MINUTE));
  const days = Math.floor(total / (24 * 60));
  const hours = Math.floor((total % (24 * 60)) / 60);
  const minutes = total % 60;
  if (days > 0) {
    return hours > 0 ? `${days} d ${hours} h` : `${days} d`;
  }
  if (hours > 0) {
    return minutes > 0 ? `${hours} h ${minutes} min` : `${hours} h`;
  }
  return `${minutes} min`;
}

export function formatPercent(value: number | null | undefined): string {
  return value === null || value === undefined ? '' : `${Math.round(value)} %`;
}

export function formatTemperature(value: number | null | undefined): string {
  return value === null || value === undefined ? '' : `${value.toFixed(1)} °C`;
}

export function formatRssi(value: number | null | undefined): string {
  return value === null || value === undefined ? '' : `${value} dBm`;
}

export function formatStorage(mb: number | null | undefined): string {
  if (mb === null || mb === undefined) {
    return '';
  }
  return mb >= 1024 ? `${(mb / 1024).toFixed(1)} GB` : `${mb} MB`;
}
