import { THealthSettingsUpdate } from '../types/settings.type';

export type TWebhookMode = 'keep' | 'replace' | 'clear';

export function timeZoneOptions(current: string | null): string[] {
  let zones: string[] = [];
  try {
    zones = Intl.supportedValuesOf('timeZone');
  } catch {
    zones = [];
  }
  if (!zones.includes('UTC')) {
    zones = [...zones, 'UTC'];
  }
  if (current && !zones.includes(current)) {
    zones = [...zones, current];
  }
  return [...zones].sort();
}

export function isHttpsUrl(value: string): boolean {
  const trimmed = value.trim();
  if (!trimmed || trimmed.length > 1024) {
    return false;
  }
  try {
    const url = new URL(trimmed);
    return url.protocol === 'https:' && !!url.hostname && !url.username && !url.password;
  } catch {
    return false;
  }
}

export function buildUpdate(
  timeZone: string | null,
  mode: TWebhookMode,
  webhookUrl: string,
): THealthSettingsUpdate {
  const body: THealthSettingsUpdate = {};
  if (timeZone) {
    body.timeZone = timeZone;
  }
  if (mode === 'replace') {
    body.webhookUrl = webhookUrl.trim();
  } else if (mode === 'clear') {
    body.webhookUrl = '';
  }
  return body;
}
